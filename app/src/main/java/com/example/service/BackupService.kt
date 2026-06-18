package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.database.AppDao
import com.example.model.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupService(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(BackupPayload::class.java)

    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    suspend fun executeBackup(appDao: AppDao, account: GoogleSignInAccount): Boolean {
        if (!isInternetAvailable()) {
            throw IOException("No internet connection available. Please connect to the internet and try again.")
        }

        // 1. Gather all local data
        val categories = appDao.getAllCategories().first()
        val exercises = appDao.getAllExercises().first()
        val shortcuts = appDao.getAllShortcuts().first()
        val practiceLogs = appDao.getAllPracticeLogs().first()
        val waterLogs = appDao.getAllWaterLogs().first()
        val userProfile = appDao.getUserProfile().first()

        val prefs = context.getSharedPreferences("sadana_yoga_prefs", Context.MODE_PRIVATE)
        val settingsMap = mutableMapOf<String, String>()
        val keys = listOf(
            "reminder_enabled", "reminder_hour", "reminder_minute",
            "bg_sound_enabled", "bg_sound_type", "keep_screen_awake", "countdown_length"
        )
        for (k in keys) {
            if (prefs.contains(k)) {
                settingsMap[k] = prefs.all[k]?.toString() ?: ""
            }
        }

        val payload = BackupPayload(
            categories = categories,
            exercises = exercises,
            shortcuts = shortcuts,
            practiceLogs = practiceLogs,
            waterLogs = waterLogs,
            userProfile = userProfile,
            settings = settingsMap
        )

        val jsonString = backupAdapter.toJson(payload)
        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)

        // 2. Drive API setup
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf("https://www.googleapis.com/auth/drive.appdata")
        )
        credential.selectedAccount = account.account

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Sadana").build()

        // 3. Search for existing file
        val files = driveService.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id, name)")
            .execute()

        val backupFile = files.files?.firstOrNull { it.name == "sadana_yoga_backup.json" }

        val metadata = File().apply {
            name = "sadana_yoga_backup.json"
            if (backupFile == null) {
                parents = listOf("appDataFolder")
            }
        }

        val contentStream = InputStreamContent(
            "application/json",
            ByteArrayInputStream(jsonBytes)
        )

        val completedFile = if (backupFile != null) {
            driveService.files().update(backupFile.id, metadata, contentStream).execute()
        } else {
            driveService.files().create(metadata, contentStream).execute()
        }

        if (completedFile != null) {
            // Update last backed up time
            val now = System.currentTimeMillis()
            prefs.edit().putLong("drive_last_backup_time", now).apply()
            return true
        }

        return false
    }

    suspend fun executeRestore(appDao: AppDao, account: GoogleSignInAccount): Boolean {
        if (!isInternetAvailable()) {
            throw IOException("No internet connection available. Please connect to the internet and try again.")
        }

        // 1. Setup Drive Service
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf("https://www.googleapis.com/auth/drive.appdata")
        )
        credential.selectedAccount = account.account

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Sadana").build()

        // 2. Fetch the backup file list
        val files = driveService.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id, name)")
            .execute()

        val backupFile = files.files?.firstOrNull { it.name == "sadana_yoga_backup.json" }
            ?: throw IllegalStateException("No previous backup found in your Google Drive.")

        // 3. Download the file
        val outputStream = ByteArrayOutputStream()
        driveService.files().get(backupFile.id).executeMediaAndDownloadTo(outputStream)
        val jsonString = outputStream.toString("UTF-8")

        // 4. Parse the backup file
        val payload = backupAdapter.fromJson(jsonString)
            ?: throw IOException("Failed to parse the backup file data.")

        // 5. Restore database and settings in local context
        // Fully clear local Room tables and insert standard
        appDao.clearCategories()
        appDao.clearExercises()
        appDao.clearShortcuts()
        appDao.clearPracticeLogs()
        appDao.clearWaterLogs()
        appDao.clearUserProfile()

        if (payload.categories.isNotEmpty()) {
            appDao.insertCategories(payload.categories)
        }
        if (payload.exercises.isNotEmpty()) {
            appDao.insertExercises(payload.exercises)
        }
        if (payload.shortcuts.isNotEmpty()) {
            appDao.insertShortcuts(payload.shortcuts)
        }
        payload.practiceLogs.forEach { appDao.insertPracticeLog(it) }
        payload.waterLogs.forEach { appDao.insertWaterLog(it) }
        payload.userProfile?.let { appDao.insertUserProfile(it) }

        // Restore settings/preferences
        val prefs = context.getSharedPreferences("sadana_yoga_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        payload.settings.forEach { (key, value) ->
            when (key) {
                "reminder_enabled", "bg_sound_enabled", "keep_screen_awake" -> {
                    editor.putBoolean(key, value.toBoolean())
                }
                "reminder_hour", "reminder_minute", "countdown_length" -> {
                    val intVal = value.toIntOrNull() ?: 0
                    editor.putInt(key, intVal)
                }
                "bg_sound_type" -> {
                    editor.putString(key, value)
                }
            }
        }
        editor.apply()

        return true
    }
}
