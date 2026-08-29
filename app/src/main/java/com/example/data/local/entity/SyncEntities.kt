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
 * Sync status constants for Optimistic Concurrency & Field-level synchronization.
 */
object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING = "PENDING"
    const val CONFLICT = "CONFLICT"
}

/**
 * Tracks modular Google Drive file synchronization metadata (fileId, ETag/Revision, timestamps).
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val fileKey: String, // "config", "students", "attendance_2026_08", "users", "fees", "notices"
    val driveFileId: String? = null,
    val localLastSyncTime: Long = 0L,
    val driveRevisionEtag: String? = null,
    val lastModifiedTime: Long = 0L
)

/**
 * Local cache of users from users.json on Google Drive for rapid role and UI gating checks.
 */
@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val email: String, // Lowercase Google email
    val name: String = "",
    val role: String = "Teacher", // "Admin", "Teacher", "Staff", "ViewOnly"
    val addedDate: String = "",
    val status: String = "Active", // "Active", "Pending", "Inactive"
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.SYNCED
)

/**
 * Modular Fees & Accounts Entity.
 */
@Entity(tableName = "fees")
data class FeeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String = "",
    val studentName: String = "",
    val studentClass: String = "",
    val feeType: String = "মাসিক বেতন",
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueDate: String = "",
    val paymentDate: String? = null,
    val status: String = "Unpaid", // "Paid", "Partial", "Unpaid"
    val month: String = "", // e.g. "2026-08"
    val receiptNumber: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.SYNCED
)

/**
 * Modular Notices Entity.
 */
@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val publishedDate: String = "",
    val targetAudience: String = "All", // "All", "Teachers", "Students"
    val attachmentUrl: String? = null,
    val isImportant: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.SYNCED
)

/**
 * School Global Configuration (config.json)
 */
data class SchoolConfig(
    val schoolName: String = "আমার বিদ্যালয়",
    val roomCode: String = "ROOM-2026-01",
    val createdDate: String = "",
    val schemaVersion: Int = 1,
    val adminEmail: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
)

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
    val backupType: String = "MANUAL", // "MANUAL", "DAILY", "WEEKLY", "PRE_RESTORE_SAFETY_SNAPSHOT"
    val note: String = ""
)
