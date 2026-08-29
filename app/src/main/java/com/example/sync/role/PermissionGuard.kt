package com.example.sync.role

import android.util.Log
import com.example.data.local.entity.UserAccountEntity

/**
 * PermissionGuard enforces a strict "FAIL-CLOSED" Role-Based Access Control (RBAC) model.
 *
 * Fail-Closed Security Rules:
 * 1. If role is null, blank, or unverified: ALL sensitive permissions evaluate strictly to FALSE.
 * 2. Sensitive Actions (Backup, Restore, User Management, Database Reset/Delete, Settings Edit)
 *    require explicit, verified ADMIN authorization.
 * 3. Operational Actions (Student Edit, Attendance, Fees, Notices) require verified role assignments.
 * 4. Offline users or guest sessions are restricted to safe read-only or draft-only operations.
 */
object PermissionGuard {

    private const val TAG = "PermissionGuard"

    /**
     * Determines if a user can trigger Google Drive / Local Database Backups.
     * Strict Fail-Closed: Only verified School Admin can backup.
     */
    fun canPerformBackup(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Determines if a user can restore database snapshots (Destructive operation).
     * Strict Fail-Closed: Only verified School Admin can restore.
     */
    fun canPerformRestore(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Determines if a user can add, edit, or remove authorized school staff/teachers.
     * Strict Fail-Closed: Only verified School Admin can manage users.
     */
    fun canManageUsers(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Determines if a user can edit school profile, EIIN code, villages, and basic settings.
     * Strict Fail-Closed: Only verified School Admin can edit school settings.
     */
    fun canEditSchoolSettings(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Determines if a user can delete student records or wipe data.
     * Strict Fail-Closed: Only verified School Admin can delete.
     */
    fun canDeleteStudentRecord(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Determines if a user can wipe/reset the entire database.
     * Strict Fail-Closed: Only verified School Admin can wipe.
     */
    fun canWipeDatabase(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Determines if a user can add or edit student profiles.
     * Allowed for verified Admin and Teachers.
     */
    fun canEditStudents(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN || role == UserRole.TEACHER
    }

    /**
     * Determines if a user can take or edit daily attendance.
     * Allowed for verified Admin and Teachers.
     */
    fun canTakeAttendance(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN || role == UserRole.TEACHER
    }

    /**
     * Determines if a user can collect or manage student fees & accounts.
     * Allowed for verified Admin and Staff.
     */
    fun canManageFees(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN || role == UserRole.STAFF
    }

    /**
     * Determines if a user can publish or delete school notices.
     * Allowed for verified Admin, Teachers, and Staff.
     */
    fun canPublishNotices(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN || role == UserRole.TEACHER || role == UserRole.STAFF
    }

    /**
     * Determines if a user can review or resolve multi-user sync conflicts.
     * Allowed for verified Admin.
     */
    fun canReviewConflicts(role: UserRole?, isVerified: Boolean): Boolean {
        if (!isVerified || role == null) return false
        return role == UserRole.ADMIN
    }

    /**
     * Convenience string-based permission check.
     */
    fun isActionPermitted(action: String, roleString: String?, isVerified: Boolean): Boolean {
        val role = if (roleString.isNullOrBlank()) null else UserRole.fromString(roleString)
        return when (action.trim().lowercase()) {
            "backup" -> canPerformBackup(role, isVerified)
            "restore" -> canPerformRestore(role, isVerified)
            "users", "manage_users" -> canManageUsers(role, isVerified)
            "school_settings", "settings" -> canEditSchoolSettings(role, isVerified)
            "delete_student", "delete" -> canDeleteStudentRecord(role, isVerified)
            "wipe_data", "reset" -> canWipeDatabase(role, isVerified)
            "edit_student", "student" -> canEditStudents(role, isVerified)
            "attendance" -> canTakeAttendance(role, isVerified)
            "fees" -> canManageFees(role, isVerified)
            "notices" -> canPublishNotices(role, isVerified)
            "conflicts" -> canReviewConflicts(role, isVerified)
            else -> {
                Log.w(TAG, "Unknown action '$action' denied by default fail-closed policy.")
                false
            }
        }
    }
}
