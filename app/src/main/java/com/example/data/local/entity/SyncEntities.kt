package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a queued local operation pending synchronization to the cloud.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val queueId: String = UUID.randomUUID().toString(),
    val entityType: String, // "STUDENT", "ATTENDANCE", "EXAM_RESULT", "ROUTINE", "SCHOOL_INFO", "CUSTOM_FIELD", "FORMULA_RULE", "USER"
    val entityId: String,
    val action: String, // "CREATE", "UPDATE", "DELETE"
    val payloadJson: String,
    val clientTimestamp: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val clientVersion: Int = 1,
    val status: String = "PENDING", // "PENDING", "SYNCING", "FAILED", "SYNCED"
    val retryCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * Logs record conflict information when concurrent multi-user edits occur on the same field.
 */
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val entityLabel: String = "",
    val conflictingField: String = "",
    val localValue: String = "",
    val remoteValue: String = "",
    val remoteUpdatedBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val resolutionStatus: String = "RESOLVED_AUTOMATICALLY_LATEST", // "RESOLVED_AUTOMATICALLY_LATEST", "PENDING_ADMIN_REVIEW", "RESOLVED_MANUALLY"
    val resolutionNote: String? = null
)

/**
 * Role-Based Access Control and Granular Permissions for 20+ school users.
 */
@Entity(tableName = "authorized_users")
data class AuthorizedUserEntity(
    @PrimaryKey val email: String, // Lowercase Google Email ID
    val displayName: String = "",
    val role: String = "Teacher", // "Admin", "Teacher", "Editor", "Viewer"
    val status: String = "Active", // "Active", "Inactive", "Pending"
    val canViewStudents: Boolean = true,
    val canEditStudents: Boolean = true,
    val canDeleteStudents: Boolean = false,
    val canViewAttendance: Boolean = true,
    val canEditAttendance: Boolean = true,
    val canViewExamResults: Boolean = true,
    val canEditExamResults: Boolean = true,
    val canManageSettings: Boolean = false,
    val canManageUsers: Boolean = false,
    val canBackupRestore: Boolean = false,
    val restrictedFieldsJson: String = "[]", // e.g. ["birthRegNumber", "mobile"]
    val addedBy: String = "admin",
    val addedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = 0L
) {
    fun hasPermission(permission: String): Boolean {
        if (role.equals("Admin", ignoreCase = true) || role.equals("School Admin", ignoreCase = true)) return true
        return when (permission) {
            "canViewStudents" -> canViewStudents
            "canEditStudents" -> canEditStudents
            "canDeleteStudents" -> canDeleteStudents
            "canViewAttendance" -> canViewAttendance
            "canEditAttendance" -> canEditAttendance
            "canViewExamResults" -> canViewExamResults
            "canEditExamResults" -> canEditExamResults
            "canManageSettings" -> canManageSettings
            "canManageUsers" -> canManageUsers
            "canBackupRestore" -> canBackupRestore
            else -> false
        }
    }
}

/**
 * Historical snapshot log for Google Drive database backups.
 */
@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey val backupId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val globalVersion: Long = 1L,
    val createdByEmail: String = "",
    val recordCount: Int = 0,
    val fileSize: Long = 0L,
    val driveFileId: String? = null,
    val backupType: String = "MANUAL", // "MANUAL", "AUTO_DAILY", "PRE_RESTORE_SAFETY_SNAPSHOT"
    val note: String = ""
)
