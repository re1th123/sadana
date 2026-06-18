package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.database.AppDatabase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val appDao = db.appDao
            val lastAccount = GoogleSignIn.getLastSignedInAccount(applicationContext)
            if (lastAccount != null) {
                val backupService = BackupService(applicationContext)
                val success = backupService.executeBackup(appDao, lastAccount)
                if (success) {
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
