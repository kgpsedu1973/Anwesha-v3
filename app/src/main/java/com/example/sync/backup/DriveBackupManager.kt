package com.example.sync.backup

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BackupHistoryEntity
import com.example.sync.drive.DriveDirectApiHelper
import com.example.sync.drive.SchoolDriveStructure
import com.example.sync.drive.SchoolFolderTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * DriveBackupManager manages generational multi-tier backups on Google Drive:
 * - Daily snapshots in Backups/daily/ (Strict retention of last 7 days)
 * - Weekly snapshots in Backups/weekly/ (Strict retention of last 4-8 weeks)
 * - Automatic pruning of older backups to prevent Drive clutter
 * - Local audit trail logged to Room backup_history table
 */
class DriveBackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val driveApi: DriveDirectApiHelper = DriveDirectApiHelper(context),
    private val driveStructure: SchoolDriveStructure = SchoolDriveStructure(driveApi)
) {

    private val TAG = "DriveBackupManager"

    suspend fun performDailyBackup(accountEmail: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = driveApi.getAccessToken(accountEmail) ?: return@withContext false
            val tree = driveStructure.resolveOrCreateFolderTree(token) ?: return@withContext false

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val backupFileName = "backup_daily_$dateStr.json"

            // 1. Generate full snapshot JSON
            val snapshotJson = createFullSnapshotJson(accountEmail)
            val upload = driveApi.uploadOrUpdateFile(
                token = token,
                fileId = null,
                fileName = backupFileName,
                parentFolderId = tree.dailyBackupFolderId,
                content = snapshotJson
            )

            if (upload != null) {
                // Log to local backup history
                database.backupHistoryDao().insertBackup(
                    BackupHistoryEntity(
                        backupId = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        createdByEmail = accountEmail,
                        recordCount = database.studentDao().getAllActiveStudentsList().size,
                        fileSize = snapshotJson.toByteArray().size.toLong(),
                        driveFileId = upload.first,
                        backupType = "DAILY",
                        note = "Daily automated backup for $dateStr"
                    )
                )

                // 2. Enforce Daily Retention (Keep last 7)
                pruneBackups(token, tree.dailyBackupFolderId, keepMaxCount = 7)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Daily backup error: ${e.localizedMessage}")
        }
        false
    }

    suspend fun performWeeklyBackup(accountEmail: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = driveApi.getAccessToken(accountEmail) ?: return@withContext false
            val tree = driveStructure.resolveOrCreateFolderTree(token) ?: return@withContext false

            val weekStr = SimpleDateFormat("yyyy_'week'_ww", Locale.US).format(Date())
            val backupFileName = "backup_weekly_$weekStr.json"

            val snapshotJson = createFullSnapshotJson(accountEmail)
            val upload = driveApi.uploadOrUpdateFile(
                token = token,
                fileId = null,
                fileName = backupFileName,
                parentFolderId = tree.weeklyBackupFolderId,
                content = snapshotJson
            )

            if (upload != null) {
                database.backupHistoryDao().insertBackup(
                    BackupHistoryEntity(
                        backupId = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        createdByEmail = accountEmail,
                        recordCount = database.studentDao().getAllActiveStudentsList().size,
                        fileSize = snapshotJson.toByteArray().size.toLong(),
                        driveFileId = upload.first,
                        backupType = "WEEKLY",
                        note = "Weekly archive backup for $weekStr"
                    )
                )

                // Enforce Weekly Retention (Keep last 8)
                pruneBackups(token, tree.weeklyBackupFolderId, keepMaxCount = 8)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Weekly backup error: ${e.localizedMessage}")
        }
        false
    }

    private suspend fun pruneBackups(token: String, folderId: String, keepMaxCount: Int) {
        val files = driveApi.listFilesInFolder(token, folderId)
        if (files.size > keepMaxCount) {
            // Sort files by name (which has YYYY-MM-DD or YYYY_week_WW timestamp) ascending (oldest first)
            val sorted = files.sortedBy { it.name }
            val excessCount = files.size - keepMaxCount
            for (i in 0 until excessCount) {
                val oldFile = sorted[i]
                Log.d(TAG, "Pruning old backup file: ${oldFile.name} (id=${oldFile.id})")
                driveApi.deleteFile(token, oldFile.id)
            }
        }
    }

    private suspend fun createFullSnapshotJson(accountEmail: String): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())
        root.put("createdBy", accountEmail)

        // Students
        val students = database.studentDao().getAllActiveStudentsList()
        val sArr = JSONArray()
        for (s in students) {
            sArr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("class", s.studentClass)
                put("roll", s.rollNumber)
                put("village", s.village)
                put("mobile", s.mobile)
                put("father", s.fatherName)
                put("mother", s.motherName)
                put("academicYear", s.academicYear)
            })
        }
        root.put("students", sArr)
        return root.toString(2)
    }
}
