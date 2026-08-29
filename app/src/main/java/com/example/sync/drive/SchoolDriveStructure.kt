package com.example.sync.drive

import android.content.Context
import android.util.Log

data class SchoolFolderTree(
    val rootFolderId: String,
    val attendanceFolderId: String,
    val backupsFolderId: String,
    val dailyBackupFolderId: String,
    val weeklyBackupFolderId: String,
    val attachmentsFolderId: String
)

/**
 * Ensures and manages the exact Google Drive directory structure:
 *
 * SchoolApp_Data/
 *   ├── config.json
 *   ├── students.json
 *   ├── attendance/
 *   │    ├── attendance_YYYY_MM.json
 *   ├── users.json
 *   ├── fees.json
 *   ├── notices.json
 *   ├── Backups/
 *   │    ├── daily/
 *   │    └── weekly/
 *   └── attachments/
 */
class SchoolDriveStructure(private val driveApi: DriveDirectApiHelper) {

    private val TAG = "SchoolDriveStructure"

    suspend fun resolveOrCreateFolderTree(token: String): SchoolFolderTree? {
        try {
            // 1. Root Folder: "SchoolApp_Data"
            val rootFolder = driveApi.findFolder(token, "SchoolApp_Data")
            val rootId = rootFolder?.id ?: driveApi.createFolder(token, "SchoolApp_Data")
            if (rootId == null) {
                Log.e(TAG, "Failed to resolve root folder SchoolApp_Data")
                return null
            }

            // 2. Attendance Folder: "attendance"
            val attendanceFolder = driveApi.findFolder(token, "attendance", rootId)
            val attendanceId = attendanceFolder?.id ?: driveApi.createFolder(token, "attendance", rootId)
                ?: rootId

            // 3. Backups Folder: "Backups"
            val backupsFolder = driveApi.findFolder(token, "Backups", rootId)
            val backupsId = backupsFolder?.id ?: driveApi.createFolder(token, "Backups", rootId)
                ?: rootId

            // 4. Daily Backups Folder: "daily"
            val dailyFolder = driveApi.findFolder(token, "daily", backupsId)
            val dailyId = dailyFolder?.id ?: driveApi.createFolder(token, "daily", backupsId)
                ?: backupsId

            // 5. Weekly Backups Folder: "weekly"
            val weeklyFolder = driveApi.findFolder(token, "weekly", backupsId)
            val weeklyId = weeklyFolder?.id ?: driveApi.createFolder(token, "weekly", backupsId)
                ?: backupsId

            // 6. Attachments Folder: "attachments"
            val attachmentsFolder = driveApi.findFolder(token, "attachments", rootId)
            val attachmentsId = attachmentsFolder?.id ?: driveApi.createFolder(token, "attachments", rootId)
                ?: rootId

            return SchoolFolderTree(
                rootFolderId = rootId,
                attendanceFolderId = attendanceId,
                backupsFolderId = backupsId,
                dailyBackupFolderId = dailyId,
                weeklyBackupFolderId = weeklyId,
                attachmentsFolderId = attachmentsId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving folder tree: ${e.localizedMessage}")
            return null
        }
    }
}
