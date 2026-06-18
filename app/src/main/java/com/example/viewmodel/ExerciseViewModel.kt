package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.model.Category
import com.example.model.Exercise
import com.example.model.ExerciseMode
import com.example.model.ExerciseRepository
import com.example.model.PurposeTag
import com.example.model.Shortcut
import com.example.model.ShortcutExercise
import com.example.model.PracticeLog
import com.example.model.UserProfile
import com.example.model.WaterLog
import com.example.WaterReminderReceiver
import com.example.ReminderReceiver
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val appDao = db.appDao

    // --- User Profile & Water Settings / Goals ---
    val userProfile: StateFlow<UserProfile> = appDao.getUserProfile()
        .map { profile ->
            profile ?: UserProfile(
                id = 1,
                name = "Self Practitioner",
                isEmoji = true,
                avatarValue = "🧘",
                waterTrackerEnabled = false,
                waterGoalMl = 2000,
                waterReminderEnabled = false,
                waterReminderType = "INTERVAL",
                waterReminderIntervalHours = 2,
                waterReminderCustomTimes = "08:00,12:00,16:00,20:00",
                languageCode = "en"
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile(
                id = 1,
                name = "Self Practitioner",
                isEmoji = true,
                avatarValue = "🧘",
                waterTrackerEnabled = false,
                waterGoalMl = 2000,
                waterReminderEnabled = false,
                waterReminderType = "INTERVAL",
                waterReminderIntervalHours = 2,
                waterReminderCustomTimes = "08:00,12:00,16:00,20:00",
                languageCode = "en"
            )
        )

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            appDao.insertUserProfile(profile)
            WaterReminderReceiver.rescheduleWaterReminders(getApplication())
        }
    }

    // --- Water Logs & Hydration Tracker ---
    val allWaterLogs: StateFlow<List<WaterLog>> = appDao.getAllWaterLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val todayWaterIntake: StateFlow<Int> = allWaterLogs
        .map { logs ->
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            logs.filter { it.dateString == todayStr }.sumOf { it.amountMl }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun logWaterIntake(amountMl: Int) {
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val log = WaterLog(
                timestamp = System.currentTimeMillis(),
                amountMl = amountMl,
                dateString = todayStr
            )
            appDao.insertWaterLog(log)
        }
    }

    fun deleteWaterLog(id: Long) {
        viewModelScope.launch {
            appDao.deleteWaterLog(id)
        }
    }

    fun clearWaterLogs() {
        viewModelScope.launch {
            appDao.clearWaterLogs()
        }
    }

    // --- Practice Session Completion & Streak Tracking ---
    val practiceLogs: StateFlow<List<PracticeLog>> = appDao.getAllPracticeLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentStreak: StateFlow<Int> = practiceLogs
        .map { calculateStreak(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun logPracticeSession(
        shortcutId: String,
        shortcutNameSnapshot: String,
        actualDurationSeconds: Int,
        categoriesSnapshot: String
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateString = sdf.format(Date())
            val log = PracticeLog(
                timestamp = System.currentTimeMillis(),
                dateString = dateString,
                shortcutId = shortcutId,
                shortcutNameSnapshot = shortcutNameSnapshot,
                actualDurationSeconds = actualDurationSeconds,
                categoriesSnapshot = categoriesSnapshot
            )
            appDao.insertPracticeLog(log)
        }
    }

    private fun calculateStreak(logs: List<PracticeLog>): Int {
        if (logs.isEmpty()) return 0
        val uniqueDates = logs.map { it.dateString }.toSet()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        val startStr = when {
            uniqueDates.contains(todayStr) -> todayStr
            uniqueDates.contains(yesterdayStr) -> yesterdayStr
            else -> return 0
        }

        var streakCount = 0
        val checkCal = Calendar.getInstance()
        val startDate = sdf.parse(startStr) ?: return 0
        checkCal.time = startDate

        while (true) {
            val dateToCheck = sdf.format(checkCal.time)
            if (uniqueDates.contains(dateToCheck)) {
                streakCount++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streakCount
    }

    // --- Daily Reminder Notification Settings Persistence ---
    private val prefs = application.getSharedPreferences("sadana_yoga_prefs", Context.MODE_PRIVATE)

    private val _activeMode = MutableStateFlow(ExerciseMode.valueOf(prefs.getString("active_exercise_mode", "YOGA") ?: "YOGA"))
    val activeMode: StateFlow<ExerciseMode> = _activeMode

    fun setActiveMode(mode: ExerciseMode) {
        _activeMode.value = mode
        prefs.edit().putString("active_exercise_mode", mode.name).apply()
    }

    private val _isReminderEnabled = MutableStateFlow(prefs.getBoolean("reminder_enabled", false))
    val isReminderEnabled: StateFlow<Boolean> = _isReminderEnabled

    private val _reminderHour = MutableStateFlow(prefs.getInt("reminder_hour", 8))
    val reminderHour: StateFlow<Int> = _reminderHour

    private val _reminderMinute = MutableStateFlow(prefs.getInt("reminder_minute", 0))
    val reminderMinute: StateFlow<Int> = _reminderMinute

    // --- Practice Mode Refinements Persistence ---
    private val _bgSoundEnabled = MutableStateFlow(prefs.getBoolean("bg_sound_enabled", false))
    val bgSoundEnabled: StateFlow<Boolean> = _bgSoundEnabled

    private val _bgSoundType = MutableStateFlow(prefs.getString("bg_sound_type", "Calm Meditation") ?: "Calm Meditation")
    val bgSoundType: StateFlow<String> = _bgSoundType

    private val _keepScreenAwake = MutableStateFlow(prefs.getBoolean("keep_screen_awake", true))
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake

    private val _countdownLength = MutableStateFlow(prefs.getInt("countdown_length", 5))
    val countdownLength: StateFlow<Int> = _countdownLength

    // --- Google Drive Backup & Restore Persistence ---
    private val _driveLastBackupTime = MutableStateFlow(prefs.getLong("drive_last_backup_time", 0L))
    val driveLastBackupTime: StateFlow<Long> = _driveLastBackupTime

    private val _driveAutoBackupEnabled = MutableStateFlow(prefs.getBoolean("drive_auto_backup_enabled", false))
    val driveAutoBackupEnabled: StateFlow<Boolean> = _driveAutoBackupEnabled

    fun updateLastBackupTime(time: Long) {
        prefs.edit().putLong("drive_last_backup_time", time).apply()
        _driveLastBackupTime.value = time
    }

    fun updateAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("drive_auto_backup_enabled", enabled).apply()
        _driveAutoBackupEnabled.value = enabled
        if (enabled) {
            com.example.service.scheduleDailyBackup(getApplication())
        } else {
            com.example.service.cancelDailyBackup(getApplication())
        }
    }

    fun updateBgSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("bg_sound_enabled", enabled).apply()
        _bgSoundEnabled.value = enabled
    }

    fun updateBgSoundType(type: String) {
        prefs.edit().putString("bg_sound_type", type).apply()
        _bgSoundType.value = type
    }

    fun updateKeepScreenAwake(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_awake", enabled).apply()
        _keepScreenAwake.value = enabled
    }

    fun updateCountdownLength(length: Int) {
        prefs.edit().putInt("countdown_length", length).apply()
        _countdownLength.value = length
    }

    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit()
            .putBoolean("reminder_enabled", enabled)
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()

        _isReminderEnabled.value = enabled
        _reminderHour.value = hour
        _reminderMinute.value = minute

        scheduleReminderAlarm()
    }

    fun scheduleReminderAlarm() {
        val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val intent = android.content.Intent(getApplication(), ReminderReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            getApplication(),
            1001,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        if (!_isReminderEnabled.value) {
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, _reminderHour.value)
            set(Calendar.MINUTE, _reminderMinute.value)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    // Inputs/Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory

    private val _selectedTag = MutableStateFlow<PurposeTag?>(null)
    val selectedTag: StateFlow<PurposeTag?> = _selectedTag

    // Dynamic Categories State
    val categories: StateFlow<List<Category>> = appDao.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dynamic Shortcuts State
    val shortcuts: StateFlow<List<Shortcut>> = appDao.getAllShortcuts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Raw/Mutable Exercises
    val exercises: StateFlow<List<Exercise>> = appDao.getAllExercises()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val currentCategories = appDao.getAllCategories().first()
            if (currentCategories.isEmpty()) {
                appDao.insertCategories(ExerciseRepository.defaultCategories)
            }
            
            val currentExercises = appDao.getAllExercises().first()
            if (currentExercises.isEmpty()) {
                appDao.insertExercises(ExerciseRepository.exercises)
            }
            
            val currentShortcuts = appDao.getAllShortcuts().first()
            if (currentShortcuts.isEmpty()) {
                appDao.insertShortcuts(ExerciseRepository.defaultShortcuts)
            }
        }
    }

    // Derived filtered list combining all search & filter parameters
    val filteredExercises: StateFlow<List<Exercise>> = combine(
        exercises,
        _searchQuery,
        _selectedCategory,
        _selectedTag,
        activeMode
    ) { exercisesList, query, category, tag, mode ->
        exercisesList.filter { exercise ->
            val matchesMode = exercise.mode == mode
            val matchesQuery = query.isBlank() || exercise.name.contains(query, ignoreCase = true)
            // Match category by ID representation
            val matchesCategory = category == null || exercise.categoryId == category.id
            val matchesTag = tag == null || exercise.purposeTags.contains(tag)
            matchesMode && matchesQuery && matchesCategory && matchesTag
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: Category?) {
        _selectedCategory.value = category
    }

    fun selectTag(tag: PurposeTag?) {
        _selectedTag.value = tag
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _selectedTag.value = null
    }

    // --- Category Management API ---
    fun addCategory(category: Category) {
        viewModelScope.launch {
            appDao.insertCategory(category)
        }
    }

    fun updateCategory(updatedCategory: Category) {
        viewModelScope.launch {
            appDao.insertCategory(updatedCategory)
        }
        // Force refresh UI if active category was updated
        if (_selectedCategory.value?.id == updatedCategory.id) {
            _selectedCategory.value = updatedCategory
        }
    }

    fun deleteCategory(categoryId: String, reassignToUncategorized: Boolean) {
        viewModelScope.launch {
            if (reassignToUncategorized) {
                // Ensure Uncategorized exists first
                val categoriesList = appDao.getAllCategories().first()
                val hasUncategorized = categoriesList.any { it.id == "uncategorized" }
                if (!hasUncategorized) {
                    val uncategorized = Category(
                        id = "uncategorized",
                        name = "Uncategorized",
                        isEmoji = true,
                        iconValue = "📦"
                    )
                    appDao.insertCategory(uncategorized)
                }
                // Move exercises referencing deleted category to "uncategorized"
                val exercisesList = appDao.getAllExercises().first()
                exercisesList.forEach {
                    if (it.categoryId == categoryId) {
                        appDao.insertExercise(it.copy(categoryId = "uncategorized"))
                    }
                }
            } else {
                val categoriesList = appDao.getAllCategories().first()
                val hasUncategorized = categoriesList.any { it.id == "uncategorized" }
                if (!hasUncategorized) {
                    appDao.insertCategory(Category(
                        id = "uncategorized",
                        name = "Uncategorized",
                        isEmoji = true,
                        iconValue = "📦"
                    ))
                }
                val exercisesList = appDao.getAllExercises().first()
                exercisesList.forEach {
                    if (it.categoryId == categoryId) {
                        appDao.insertExercise(it.copy(categoryId = "uncategorized"))
                    }
                }
            }

            // Delete the category
            appDao.deleteCategory(categoryId)
            
            // Reset filter selection if it was deleted
            if (_selectedCategory.value?.id == categoryId) {
                _selectedCategory.value = null
            }
        }
    }

    // --- Shortcut Management API ---
    fun addShortcut(shortcut: Shortcut) {
        viewModelScope.launch {
            appDao.insertShortcut(shortcut)
        }
    }

    fun updateShortcut(updatedShortcut: Shortcut) {
        viewModelScope.launch {
            appDao.insertShortcut(updatedShortcut)
        }
    }

    fun deleteShortcut(shortcutId: String) {
        viewModelScope.launch {
            appDao.deleteShortcut(shortcutId)
        }
    }

    fun updateShortcutsOrder(orderedShortcuts: List<Shortcut>) {
        viewModelScope.launch {
            val updated = orderedShortcuts.mapIndexed { index, shortcut ->
                shortcut.copy(order = index)
            }
            appDao.insertShortcuts(updated)
        }
    }

    // --- Exercise Management API ---
    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            appDao.insertExercise(exercise)
        }
    }

    fun updateExercise(updatedExercise: Exercise) {
        viewModelScope.launch {
            appDao.insertExercise(updatedExercise)
        }
    }

    fun deleteExercise(exerciseId: String) {
        viewModelScope.launch {
            appDao.deleteExercise(exerciseId)
            
            // Dynamic cascaded update for all shortcuts referencing this exercise
            val currentShortcuts = appDao.getAllShortcuts().first()
            currentShortcuts.forEach { shortcut ->
                val remainingExercises = shortcut.exercises.filter { it.exerciseId != exerciseId }
                if (remainingExercises.size != shortcut.exercises.size) {
                    val updatedShortcut = shortcut.copy(exercises = remainingExercises)
                    appDao.insertShortcut(updatedShortcut)
                }
            }
        }
    }
}
