package com.example.sync.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.sync.backup.DriveBackupManager
import com.example.sync.engine.DriveSyncEngine

/**
 * Background periodic sync worker running every 15-30 minutes when connected.
 */
class PeriodicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("anwesha_drive_sync_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("account_email", "") ?: ""
        if (email.isBlank()) {
            return Result.success()
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val syncEngine = DriveSyncEngine(applicationContext, db)
            val result = syncEngine.syncAll(email)
            Log.d("PeriodicSyncWorker", "Periodic sync finished: ${result.message}")
            Result.success()
        } catch (e: Exception) {
            Log.e("PeriodicSyncWorker", "Periodic sync failed: ${e.localizedMessage}")
            Result.retry()
        }
    }
}

/**
 * Expedited / One-time debounced sync worker triggered after local edits.
 */
class ImmediateSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("anwesha_drive_sync_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("account_email", "") ?: ""
        if (email.isBlank()) {
            return Result.success()
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val syncEngine = DriveSyncEngine(applicationContext, db)
            val result = syncEngine.syncAll(email)
            Log.d("ImmediateSyncWorker", "Immediate sync result: ${result.message}")
            Result.success()
        } catch (e: Exception) {
            Log.e("ImmediateSyncWorker", "Immediate sync failed: ${e.localizedMessage}")
            Result.retry()
        }
    }
}

/**
 * Generational Daily / Weekly Backup Worker.
 */
class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("anwesha_drive_sync_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("account_email", "") ?: ""
        if (email.isBlank()) {
            return Result.success()
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val backupManager = DriveBackupManager(applicationContext, db)
            backupManager.performDailyBackup(email)
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Backup failed: ${e.localizedMessage}")
            Result.retry()
        }
    }
}
