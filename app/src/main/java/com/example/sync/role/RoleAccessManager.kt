package com.example.sync.role

import com.example.data.local.entity.UserAccountEntity

enum class UserRole(val label: String, val banglaLabel: String) {
    ADMIN("Admin", "অ্যাডমিন (সর্বোচ্চ নিয়ন্ত্রণ)"),
    TEACHER("Teacher", "শিক্ষক (উপস্থিতি ও ফলাফল এন্ট্রি)"),
    STAFF("Staff", "স্টাফ (ফি ও নোটিশ পরিচালনা)"),
    VIEW_ONLY("ViewOnly", "দর্শক (শুধুমাত্র দেখার অনুমতি)");

    companion object {
        fun fromString(role: String?): UserRole {
            return when (role?.trim()?.lowercase()) {
                "admin", "school admin" -> ADMIN
                "teacher" -> TEACHER
                "staff" -> STAFF
                "viewonly", "viewer", "parent" -> VIEW_ONLY
                else -> TEACHER
            }
        }
    }
}

data class RolePermissions(
    val canManageUsers: Boolean,
    val canEditSchoolInfo: Boolean,
    val canEditStudents: Boolean,
    val canDeleteStudents: Boolean,
    val canTakeAttendance: Boolean,
    val canManageFees: Boolean,
    val canPublishNotices: Boolean,
    val canRunBackupRestore: Boolean,
    val canReviewConflicts: Boolean
)

object RoleAccessManager {

    fun getPermissions(role: UserRole): RolePermissions {
        return when (role) {
            UserRole.ADMIN -> RolePermissions(
                canManageUsers = true,
                canEditSchoolInfo = true,
                canEditStudents = true,
                canDeleteStudents = true,
                canTakeAttendance = true,
                canManageFees = true,
                canPublishNotices = true,
                canRunBackupRestore = true,
                canReviewConflicts = true
            )
            UserRole.TEACHER -> RolePermissions(
                canManageUsers = false,
                canEditSchoolInfo = false,
                canEditStudents = true,
                canDeleteStudents = false,
                canTakeAttendance = true,
                canManageFees = false,
                canPublishNotices = true,
                canRunBackupRestore = false,
                canReviewConflicts = false
            )
            UserRole.STAFF -> RolePermissions(
                canManageUsers = false,
                canEditSchoolInfo = false,
                canEditStudents = false,
                canDeleteStudents = false,
                canTakeAttendance = false,
                canManageFees = true,
                canPublishNotices = true,
                canRunBackupRestore = false,
                canReviewConflicts = false
            )
            UserRole.VIEW_ONLY -> RolePermissions(
                canManageUsers = false,
                canEditSchoolInfo = false,
                canEditStudents = false,
                canDeleteStudents = false,
                canTakeAttendance = false,
                canManageFees = false,
                canPublishNotices = false,
                canRunBackupRestore = false,
                canReviewConflicts = false
            )
        }
    }

    fun isAuthorized(user: UserAccountEntity?): Boolean {
        if (user == null) return false
        return !user.isDeleted && user.status.equals("Active", ignoreCase = true)
    }
}
