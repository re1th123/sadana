package com.example.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Categories Queries
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    // Exercises Queries
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    // Shortcuts Queries
    @Query("SELECT * FROM shortcuts ORDER BY `order` ASC, id ASC")
    fun getAllShortcuts(): Flow<List<Shortcut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: Shortcut)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcuts(shortcuts: List<Shortcut>)

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun deleteShortcut(id: String)

    // PracticeLogs Queries
    @Query("SELECT * FROM practice_logs ORDER BY timestamp DESC")
    fun getAllPracticeLogs(): Flow<List<PracticeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPracticeLog(practiceLog: PracticeLog)

    @Query("DELETE FROM practice_logs")
    suspend fun clearPracticeLogs()

    // UserProfile Queries
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(userProfile: UserProfile)

    // WaterLog Queries
    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC")
    fun getAllWaterLogs(): Flow<List<WaterLog>>

    @Query("SELECT * FROM water_logs WHERE dateString = :dateString ORDER BY timestamp DESC")
    fun getWaterLogsForDate(dateString: String): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLog(waterLog: WaterLog)

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteWaterLog(id: Long)

    @Query("DELETE FROM water_logs")
    suspend fun clearWaterLogs()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM exercises")
    suspend fun clearExercises()

    @Query("DELETE FROM shortcuts")
    suspend fun clearShortcuts()

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfile()
}

class Converters {
    @TypeConverter
    fun fromExerciseMode(mode: ExerciseMode?): String {
        return mode?.name ?: ExerciseMode.YOGA.name
    }

    @TypeConverter
    fun toExerciseMode(value: String?): ExerciseMode {
        if (value.isNullOrBlank()) return ExerciseMode.YOGA
        return try {
            ExerciseMode.valueOf(value)
        } catch (e: Exception) {
            ExerciseMode.YOGA
        }
    }

    @TypeConverter
    fun fromPurposeTagsList(list: List<PurposeTag>?): String {
        return list?.joinToString(",") { it.name } ?: ""
    }

    @TypeConverter
    fun toPurposeTagsList(value: String?): List<PurposeTag> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").filter { it.isNotBlank() }.map { PurposeTag.valueOf(it) }
    }

    @TypeConverter
    fun fromExerciseMedia(media: ExerciseMedia?): String {
        return media?.let { "${it.mediaType.name}|${it.mediaUri}" } ?: ""
    }

    @TypeConverter
    fun toExerciseMedia(value: String?): ExerciseMedia {
        if (value.isNullOrBlank()) return ExerciseMedia(MediaType.IMAGE, "")
        val parts = value.split("|", limit = 2)
        val mediaType = MediaType.fromString(parts[0])
        val mediaUri = parts.getOrElse(1) { "" }
        return ExerciseMedia(mediaType, mediaUri)
    }

    @TypeConverter
    fun fromShortcutExerciseList(list: List<ShortcutExercise>?): String {
        return list?.joinToString(",") { "${it.exerciseId}:${it.customDurationSeconds}:${it.repeatCount}:${it.nestedShortcutId ?: ""}" } ?: ""
    }

    @TypeConverter
    fun toShortcutExerciseList(value: String?): List<ShortcutExercise> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").filter { it.isNotBlank() }.map {
            val parts = it.split(":")
            val exerciseId = parts[0]
            val customDurationSeconds = parts.getOrNull(1)?.toIntOrNull() ?: 10
            val repeatCount = parts.getOrNull(2)?.toIntOrNull() ?: 1
            val nestedShortcutId = if (parts.size > 4) {
                parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            } else {
                val p3 = parts.getOrNull(3)
                if (p3 == "1" || p3 == "0") {
                    null
                } else {
                    p3?.takeIf { it.isNotBlank() }
                }
            }
            ShortcutExercise(
                exerciseId = exerciseId,
                customDurationSeconds = customDurationSeconds,
                repeatCount = repeatCount,
                nestedShortcutId = nestedShortcutId
            )
        }
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String {
        if (map == null) return ""
        return map.entries.joinToString("[##]") { "${it.key}:::${it.value}" }
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        value.split("[##]").forEach { pair ->
            val parts = pair.split(":::", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = parts[1]
            }
        }
        return map
    }
}

@Database(entities = [Category::class, Exercise::class, Shortcut::class, PracticeLog::class, UserProfile::class, WaterLog::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val appDao: AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE practice_logs ADD COLUMN shortcutNameSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE practice_logs ADD COLUMN actualDurationSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE practice_logs ADD COLUMN categoriesSnapshot TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN mode TEXT NOT NULL DEFAULT 'YOGA'")
                db.execSQL("ALTER TABLE shortcuts ADD COLUMN mode TEXT NOT NULL DEFAULT 'YOGA'")
                db.execSQL("UPDATE shortcuts SET mode = 'GYM' WHERE id = 'gym_cooldown'")
                db.execSQL("UPDATE exercises SET mode = 'GYM' WHERE id IN ('triceps_stretch', 'standing_quad', 'hamstring_fold', 'shoulder_shrugs')")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yoga_xt_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
