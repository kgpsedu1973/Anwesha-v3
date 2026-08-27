package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.repository.SchoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoBackupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val driveManager = GoogleDriveManager(appContext)
        if (!driveManager.isAutoBackupEnabled()) {
            Log.d("DRIVE_BACKUP", "AutoBackupWorker: Skipped (Auto backup disabled in settings)")
            return@withContext Result.success()
        }

        if (!driveManager.isSignedIn()) {
            Log.d("DRIVE_BACKUP", "AutoBackupWorker: Skipped (No Google account connected)")
            return@withContext Result.success()
        }

        try {
            Log.d("DRIVE_BACKUP", "AutoBackupWorker: Starting automatic background backup...")
            val db = AppDatabase.getDatabase(appContext)
            val repo = SchoolRepository(db)
            val masterModel = repo.exportToMasterModel()
            val jsonString = masterModel.toJson(indent = false)
            val totalRecords = masterModel.studentsList.size + masterModel.usersList.size + masterModel.attendanceList.size + masterModel.examResultsList.size

            val uploadResult = driveManager.uploadDatabase(jsonString, totalRecords)
            if (uploadResult is DriveOperationResult.Success) {
                Log.d("DRIVE_BACKUP", "AutoBackupWorker: DRIVE_BACKUP: BACKUP_COMPLETE = PASS (Auto)")
                Result.success()
            } else {
                Log.w("DRIVE_BACKUP", "AutoBackupWorker: Backup failed, will retry later")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("DRIVE_BACKUP", "AutoBackupWorker: Error during auto backup: ${e.localizedMessage}")
            Result.retry()
        }
    }
}
