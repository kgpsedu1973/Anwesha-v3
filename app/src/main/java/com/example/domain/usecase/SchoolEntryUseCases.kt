package com.example.domain.usecase

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuthorizedUserEntity
import com.example.data.local.entity.SchoolConfig
import com.example.data.local.entity.UserAccountEntity
import com.example.sync.config.SchoolConfigManager
import com.example.sync.drive.DriveDirectApiHelper
import com.example.sync.role.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result states for AuthenticateUserUseCase
 */
sealed class AuthCheckResult {
    object SchoolNotConfigured : AuthCheckResult()
    data class UserApproved(val user: UserAccountEntity) : AuthCheckResult()
    data class UserPendingApproval(val email: String, val schoolName: String, val roomCode: String) : AuthCheckResult()
    data class UserUnauthorized(val email: String, val schoolName: String) : AuthCheckResult()
    data class Error(val message: String) : AuthCheckResult()
}

/**
 * UseCase 1: Create New School (Admin First Launch Setup)
 * - Generates unique Room Code
 * - Initializes SchoolConfig
 * - Registers current Google user as Primary School Admin
 */
class CreateSchoolUseCase(
    private val context: Context,
    private val database: AppDatabase,
    private val configManager: SchoolConfigManager
) {
    suspend fun execute(
        schoolName: String,
        adminEmail: String,
        adminName: String = "",
        eiinCode: String = "",
        headTeacherName: String = ""
    ): Result<SchoolConfig> = withContext(Dispatchers.IO) {
        if (schoolName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("বিদ্যালয়ের নাম ফাঁকা রাখা যাবে না"))
        }
        val cleanEmail = adminEmail.trim().lowercase()
        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("অ্যাডমিনের ইমেইল আবশ্যক"))
        }

        val roomCode = configManager.generateUniqueRoomCode()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val config = SchoolConfig(
            schoolName = schoolName.trim(),
            roomCode = roomCode,
            createdDate = todayStr,
            schemaVersion = 1,
            adminEmail = cleanEmail,
            updatedAt = System.currentTimeMillis(),
            updatedBy = cleanEmail
        )

        // 1. Save Config
        configManager.saveSchoolConfig(config, method = "CREATE")

        // 2. Register Admin in Local User Tables
        val adminUser = UserAccountEntity(
            email = cleanEmail,
            name = adminName.ifBlank { cleanEmail.substringBefore("@") },
            role = "Admin",
            addedDate = todayStr,
            status = "Active",
            updatedAt = System.currentTimeMillis(),
            updatedBy = cleanEmail
        )
        database.userAccountDao().insertUser(adminUser)

        val authAdmin = AuthorizedUserEntity(
            email = cleanEmail,
            displayName = adminName.ifBlank { "প্রধান শিক্ষক (Admin)" },
            role = "Admin",
            status = "Active",
            canViewStudents = true,
            canEditStudents = true,
            canDeleteStudents = true,
            canViewAttendance = true,
            canEditAttendance = true,
            canViewExamResults = true,
            canEditExamResults = true,
            canManageSettings = true,
            canManageUsers = true,
            canBackupRestore = true,
            addedBy = "system"
        )
        database.authorizedUserDao().insertAuthorizedUser(authAdmin)

        Result.success(config)
    }
}

/**
 * UseCase 2: Join Existing School
 * - Validates Room Code
 * - Binds local app to target school
 * - Verifies user credentials in that school
 */
class JoinSchoolUseCase(
    private val context: Context,
    private val database: AppDatabase,
    private val configManager: SchoolConfigManager
) {
    suspend fun execute(
        roomCode: String,
        userEmail: String,
        userName: String = "",
        schoolNameHint: String = "কেজিপিএস বিদ্যালয়"
    ): Result<SchoolConfig> = withContext(Dispatchers.IO) {
        val cleanRoomCode = roomCode.trim().uppercase()
        if (cleanRoomCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("অনুগ্রহ করে সঠিক রুম কোড (Room Code) লিখুন"))
        }

        val cleanEmail = userEmail.trim().lowercase()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val config = SchoolConfig(
            schoolName = schoolNameHint,
            roomCode = cleanRoomCode,
            createdDate = todayStr,
            schemaVersion = 1,
            adminEmail = "",
            updatedAt = System.currentTimeMillis(),
            updatedBy = cleanEmail
        )

        // Save local binding
        configManager.saveSchoolConfig(config, method = "JOIN_CODE")

        // Register user as pending/member until sync verifies from users.json
        val existing = database.userAccountDao().getUserByEmail(cleanEmail)
        if (existing == null) {
            val user = UserAccountEntity(
                email = cleanEmail,
                name = userName.ifBlank { cleanEmail.substringBefore("@") },
                role = "Teacher",
                addedDate = todayStr,
                status = "Pending", // Needs Admin confirmation from Drive
                updatedAt = System.currentTimeMillis(),
                updatedBy = cleanEmail
            )
            database.userAccountDao().insertUser(user)
        }

        Result.success(config)
    }
}

/**
 * UseCase 3: Authenticate User Against School Context
 * - Ensures Phase 1 (School Exists) is satisfied
 * - Then verifies Phase 2 (User exists and is approved in this school)
 */
class AuthenticateUserUseCase(
    private val database: AppDatabase,
    private val configManager: SchoolConfigManager
) {
    suspend fun execute(userEmail: String?): AuthCheckResult = withContext(Dispatchers.IO) {
        // Phase 1 Check: Does School Config Exist?
        if (!configManager.isSchoolConfigured()) {
            return@withContext AuthCheckResult.SchoolNotConfigured
        }

        val cleanEmail = userEmail?.trim()?.lowercase()
        if (cleanEmail.isNullOrBlank()) {
            return@withContext AuthCheckResult.Error("ইমেইল পাওয়া যায়নি")
        }

        val schoolConfig = configManager.currentConfig.value
        val schoolName = schoolConfig?.schoolName ?: "বিদ্যালয়"
        val roomCode = schoolConfig?.roomCode ?: ""

        // If user is designated Admin in SchoolConfig
        if (schoolConfig?.adminEmail.equals(cleanEmail, ignoreCase = true)) {
            val adminAccount = database.userAccountDao().getUserByEmail(cleanEmail)
                ?: UserAccountEntity(
                    email = cleanEmail,
                    name = cleanEmail.substringBefore("@"),
                    role = "Admin",
                    status = "Active"
                )
            return@withContext AuthCheckResult.UserApproved(adminAccount)
        }

        // Phase 2 Check: User in school's users directory
        val userAccount = database.userAccountDao().getUserByEmail(cleanEmail)
        if (userAccount == null) {
            return@withContext AuthCheckResult.UserUnauthorized(cleanEmail, schoolName)
        }

        if (userAccount.isDeleted || userAccount.status.equals("Inactive", ignoreCase = true)) {
            return@withContext AuthCheckResult.UserUnauthorized(cleanEmail, schoolName)
        }

        if (userAccount.status.equals("Pending", ignoreCase = true)) {
            return@withContext AuthCheckResult.UserPendingApproval(cleanEmail, schoolName, roomCode)
        }

        AuthCheckResult.UserApproved(userAccount)
    }
}
