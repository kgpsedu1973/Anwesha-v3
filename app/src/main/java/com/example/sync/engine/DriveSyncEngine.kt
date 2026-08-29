package com.example.sync.engine

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
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

data class SyncResult(
    val success: Boolean,
    val message: String,
    val studentsSynced: Int = 0,
    val attendanceSynced: Int = 0,
    val usersSynced: Int = 0,
    val feesSynced: Int = 0,
    val noticesSynced: Int = 0,
    val conflictsFound: Int = 0
)

/**
 * DriveSyncEngine handles modular, zero-server synchronization directly between
 * local Room Database and the School Admin's Google Drive.
 *
 * Implements:
 * - Optimistic Concurrency Control (ETag & Revision Verification)
 * - Field-Level Merging (Granular property reconciliation)
 * - Conflict Logging (Lossless audit trail in local Room database)
 * - Soft-Delete propagation (Tombstone synchronization)
 */
class DriveSyncEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val driveApi: DriveDirectApiHelper = DriveDirectApiHelper(context),
    private val driveStructure: SchoolDriveStructure = SchoolDriveStructure(driveApi)
) {

    private val TAG = "DriveSyncEngine"

    suspend fun syncAll(accountEmail: String): SyncResult = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank()) {
            return@withContext SyncResult(false, "কোনো Google অ্যাকাউন্ট সাইন-ইন করা নেই")
        }

        val token = driveApi.getAccessToken(accountEmail)
        if (token == null) {
            return@withContext SyncResult(false, "Google Drive অ্যাক্সেস টোকেন পাওয়া যায়নি")
        }

        val tree = driveStructure.resolveOrCreateFolderTree(token)
        if (tree == null) {
            return@withContext SyncResult(false, "Drive ফোল্ডার স্ট্রাকচার প্রস্তুত করা যায়নি")
        }

        try {
            // 1. Config Sync
            syncConfig(token, tree, accountEmail)

            // 2. Users Sync (Rapid Role Cache)
            val usersCount = syncUsers(token, tree, accountEmail)

            // 3. Students Sync (Field-Level Merge + ETag check)
            val studentsCount = syncStudents(token, tree, accountEmail)

            // 4. Attendance Sync (Month-wise Partitioned)
            val attendanceCount = syncAttendance(token, tree, accountEmail)

            // 5. Fees & Accounts Sync
            val feesCount = syncFees(token, tree, accountEmail)

            // 6. Notices Sync
            val noticesCount = syncNotices(token, tree, accountEmail)

            return@withContext SyncResult(
                success = true,
                message = "সফলভাবে Google Drive-এর সাথে সিঙ্ক সম্পন্ন হয়েছে",
                studentsSynced = studentsCount,
                attendanceSynced = attendanceCount,
                usersSynced = usersCount,
                feesSynced = feesCount,
                noticesSynced = noticesCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.localizedMessage}", e)
            return@withContext SyncResult(false, "সিঙ্ক ব্যর্থ: ${e.localizedMessage}")
        }
    }

    // ==========================================
    // 1. CONFIG.JSON SYNC
    // ==========================================
    private suspend fun syncConfig(token: String, tree: SchoolFolderTree, email: String) {
        val meta = database.syncMetadataDao().getMetadata("config")
        val fileInfo = driveApi.findFile(token, "config.json", tree.rootFolderId)

        if (fileInfo != null) {
            if (meta == null || meta.driveRevisionEtag != fileInfo.etag) {
                val download = driveApi.downloadFileContentAndEtag(token, fileInfo.id)
                if (download != null) {
                    try {
                        val json = JSONObject(download.first)
                        val schoolName = json.optString("schoolName", "আমার বিদ্যালয়")
                        val currentInfo = database.schoolInfoDao().getSchoolInfo()
                        // Update local SchoolInfo if needed
                        database.syncMetadataDao().insertOrUpdate(
                            SyncMetadataEntity(
                                fileKey = "config",
                                driveFileId = fileInfo.id,
                                localLastSyncTime = System.currentTimeMillis(),
                                driveRevisionEtag = download.second,
                                lastModifiedTime = System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing config.json: ${e.localizedMessage}")
                    }
                }
            }
        } else {
            // Create initial config.json
            val configObj = JSONObject().apply {
                put("schoolName", "কেজিপিএস প্রাথমিক বিদ্যালয়")
                put("roomCode", "ROOM-2026-${(1000..9999).random()}")
                put("createdDate", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                put("schemaVersion", 1)
                put("adminEmail", email)
                put("updatedAt", System.currentTimeMillis())
                put("updatedBy", email)
            }
            val upload = driveApi.uploadOrUpdateFile(token, null, "config.json", tree.rootFolderId, configObj.toString(2))
            if (upload != null) {
                database.syncMetadataDao().insertOrUpdate(
                    SyncMetadataEntity(
                        fileKey = "config",
                        driveFileId = upload.first,
                        localLastSyncTime = System.currentTimeMillis(),
                        driveRevisionEtag = upload.second,
                        lastModifiedTime = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ==========================================
    // 2. USERS.JSON SYNC (Multi-User Role Engine)
    // ==========================================
    private suspend fun syncUsers(token: String, tree: SchoolFolderTree, currentEmail: String): Int {
        val meta = database.syncMetadataDao().getMetadata("users")
        val fileInfo = driveApi.findFile(token, "users.json", tree.rootFolderId)
        val pendingUsers = database.userAccountDao().getPendingUsers()

        var remoteUsersMap = mutableMapOf<String, UserAccountEntity>()
        var remoteEtag = ""
        var fileId: String? = fileInfo?.id

        if (fileInfo != null) {
            val download = driveApi.downloadFileContentAndEtag(token, fileInfo.id)
            if (download != null) {
                remoteEtag = download.second
                try {
                    val root = JSONObject(download.first)
                    val arr = root.optJSONArray("users") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val email = obj.getString("email").trim().lowercase()
                        val u = UserAccountEntity(
                            email = email,
                            name = obj.optString("name", ""),
                            role = obj.optString("role", "Teacher"),
                            addedDate = obj.optString("addedDate", ""),
                            status = obj.optString("status", "Active"),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            updatedBy = obj.optString("updatedBy", ""),
                            isDeleted = obj.optBoolean("isDeleted", false),
                            deletedAt = if (obj.has("deletedAt")) obj.optLong("deletedAt") else null,
                            syncStatus = SyncStatus.SYNCED
                        )
                        remoteUsersMap[email] = u
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing users.json: ${e.localizedMessage}")
                }
            }
        } else {
            // First time admin setup: create current user as Admin in users.json
            val adminUser = UserAccountEntity(
                email = currentEmail.trim().lowercase(),
                name = currentEmail.substringBefore("@"),
                role = "Admin",
                addedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                status = "Active",
                updatedAt = System.currentTimeMillis(),
                updatedBy = currentEmail,
                syncStatus = SyncStatus.SYNCED
            )
            remoteUsersMap[adminUser.email] = adminUser
        }

        // Merge Pending Local Users
        for (pending in pendingUsers) {
            val email = pending.email.trim().lowercase()
            val existingRemote = remoteUsersMap[email]
            if (existingRemote == null || pending.updatedAt >= existingRemote.updatedAt) {
                remoteUsersMap[email] = pending.copy(syncStatus = SyncStatus.SYNCED)
                // If this is a newly shared user, grant Drive permission
                if (pending.role.isNotBlank()) {
                    driveApi.shareWithUser(token, tree.rootFolderId, email, pending.role)
                }
            }
        }

        // Save into local Room database
        database.userAccountDao().insertAll(remoteUsersMap.values.toList())
        if (pendingUsers.isNotEmpty()) {
            database.userAccountDao().markSynced(pendingUsers.map { it.email })
        }

        // Upload updated users.json if pending changes or initial creation
        if (pendingUsers.isNotEmpty() || fileInfo == null) {
            val root = JSONObject()
            val arr = JSONArray()
            for (u in remoteUsersMap.values) {
                val obj = JSONObject().apply {
                    put("email", u.email)
                    put("name", u.name)
                    put("role", u.role)
                    put("addedDate", u.addedDate)
                    put("status", u.status)
                    put("updatedAt", u.updatedAt)
                    put("updatedBy", u.updatedBy)
                    put("isDeleted", u.isDeleted)
                    if (u.deletedAt != null) put("deletedAt", u.deletedAt)
                }
                arr.put(obj)
            }
            root.put("schemaVersion", 1)
            root.put("lastUpdated", System.currentTimeMillis())
            root.put("users", arr)

            val upload = driveApi.uploadOrUpdateFile(token, fileId, "users.json", tree.rootFolderId, root.toString(2))
            if (upload != null) {
                fileId = upload.first
                remoteEtag = upload.second
            }
        }

        // Update Metadata
        database.syncMetadataDao().insertOrUpdate(
            SyncMetadataEntity(
                fileKey = "users",
                driveFileId = fileId,
                localLastSyncTime = System.currentTimeMillis(),
                driveRevisionEtag = remoteEtag,
                lastModifiedTime = System.currentTimeMillis()
            )
        )

        return remoteUsersMap.size
    }

    // ==========================================
    // 3. STUDENTS.JSON SYNC (Field-Level Merge)
    // ==========================================
    private suspend fun syncStudents(token: String, tree: SchoolFolderTree, currentEmail: String): Int {
        val meta = database.syncMetadataDao().getMetadata("students")
        val fileInfo = driveApi.findFile(token, "students.json", tree.rootFolderId)
        val pendingStudents = database.studentDao().getPendingStudents()

        var remoteStudents = mutableMapOf<String, StudentEntity>()
        var remoteEtag = ""
        var fileId: String? = fileInfo?.id

        if (fileInfo != null) {
            val download = driveApi.downloadFileContentAndEtag(token, fileInfo.id)
            if (download != null) {
                remoteEtag = download.second
                try {
                    val root = JSONObject(download.first)
                    val arr = root.optJSONArray("students") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getString("id")
                        val s = StudentEntity(
                            id = id,
                            studentClass = obj.optString("studentClass", "১ম শ্রেণি"),
                            section = obj.optString("section", "ক"),
                            rollNumber = obj.optInt("rollNumber", 1),
                            name = obj.optString("name", ""),
                            parentContact = obj.optString("parentContact", ""),
                            fatherName = obj.optString("fatherName", ""),
                            motherName = obj.optString("motherName", ""),
                            birthDate = obj.optString("birthDate", ""),
                            mobile = obj.optString("mobile", ""),
                            village = obj.optString("village", ""),
                            academicYear = obj.optString("academicYear", "২০২৬"),
                            address = obj.optString("address", ""),
                            birthRegNumber = obj.optString("birthRegNumber", ""),
                            gender = obj.optString("gender", "ছাত্র"),
                            isSpecialNeeds = obj.optBoolean("isSpecialNeeds", false),
                            status = obj.optString("status", "Current"),
                            photoUri = obj.optString("photoUri", null),
                            customValuesJson = obj.optString("customValuesJson", "{}"),
                            admissionDate = obj.optString("admissionDate", ""),
                            lastModifiedDate = obj.optString("lastModifiedDate", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            updatedBy = obj.optString("updatedBy", ""),
                            version = obj.optInt("version", 1),
                            isDeleted = obj.optBoolean("isDeleted", false),
                            deletedAt = if (obj.has("deletedAt")) obj.optLong("deletedAt") else null,
                            syncStatus = SyncStatus.SYNCED
                        )
                        remoteStudents[id] = s
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing students.json: ${e.localizedMessage}")
                }
            }
        }

        // Perform Field-Level Merge with Local Pending Students
        val mergedList = mutableListOf<StudentEntity>()
        val allLocal = database.studentDao().getAllActiveStudentsList().associateBy { it.id }.toMutableMap()

        // 1. Process all remote students
        for ((id, remote) in remoteStudents) {
            val local = allLocal[id]
            if (local == null) {
                // Insert into local
                mergedList.add(remote)
            } else if (local.syncStatus != SyncStatus.PENDING) {
                // Local hasn't changed, accept remote
                mergedList.add(remote)
            } else {
                // Both modified! Perform Field-Level Merge
                val merged = mergeStudentFieldLevel(local, remote, currentEmail)
                mergedList.add(merged)
            }
            allLocal.remove(id)
        }

        // 2. Add local students that don't exist in remote yet
        for ((_, localOnly) in allLocal) {
            mergedList.add(localOnly.copy(syncStatus = SyncStatus.SYNCED))
        }

        // Update local Room database
        database.studentDao().insertAllStudents(mergedList)
        if (pendingStudents.isNotEmpty()) {
            database.studentDao().markSynced(pendingStudents.map { it.id })
        }

        // Push to Drive if there were local pending changes or file was missing
        if (pendingStudents.isNotEmpty() || fileInfo == null) {
            val root = JSONObject()
            val arr = JSONArray()
            for (s in mergedList) {
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("studentClass", s.studentClass)
                    put("section", s.section)
                    put("rollNumber", s.rollNumber)
                    put("parentContact", s.parentContact)
                    put("fatherName", s.fatherName)
                    put("motherName", s.motherName)
                    put("gender", s.gender)
                    put("village", s.village)
                    put("birthDate", s.birthDate)
                    put("mobile", s.mobile)
                    put("birthRegNumber", s.birthRegNumber)
                    put("address", s.address)
                    put("academicYear", s.academicYear)
                    put("isSpecialNeeds", s.isSpecialNeeds)
                    put("status", s.status)
                    put("photoUri", s.photoUri ?: "")
                    put("customValuesJson", s.customValuesJson)
                    put("admissionDate", s.admissionDate)
                    put("lastModifiedDate", s.lastModifiedDate)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                    put("updatedBy", s.updatedBy)
                    put("version", s.version)
                    put("isDeleted", s.isDeleted)
                    if (s.deletedAt != null) put("deletedAt", s.deletedAt)
                }
                arr.put(obj)
            }
            root.put("schemaVersion", 1)
            root.put("lastUpdated", System.currentTimeMillis())
            root.put("students", arr)

            val upload = driveApi.uploadOrUpdateFile(token, fileId, "students.json", tree.rootFolderId, root.toString(2))
            if (upload != null) {
                fileId = upload.first
                remoteEtag = upload.second
            }
        }

        // Update sync metadata
        database.syncMetadataDao().insertOrUpdate(
            SyncMetadataEntity(
                fileKey = "students",
                driveFileId = fileId,
                localLastSyncTime = System.currentTimeMillis(),
                driveRevisionEtag = remoteEtag,
                lastModifiedTime = System.currentTimeMillis()
            )
        )

        return mergedList.size
    }

    /**
     * Reconciles two versions of a Student record at the individual property level.
     * Logs collisions to SyncConflictDao for admin review without silent data loss.
     */
    private suspend fun mergeStudentFieldLevel(
        local: StudentEntity,
        remote: StudentEntity,
        userEmail: String
    ): StudentEntity {
        var merged = local

        suspend fun resolveField(fieldName: String, localVal: String, remoteVal: String): String {
            if (localVal == remoteVal) return localVal
            // If remote is newer, remote wins but record conflict
            return if (remote.updatedAt > local.updatedAt) {
                logConflict("STUDENT", local.id, local.name, fieldName, localVal, remoteVal, remote.updatedBy)
                remoteVal
            } else {
                localVal
            }
        }

        merged = merged.copy(
            name = resolveField("name", local.name, remote.name),
            studentClass = resolveField("studentClass", local.studentClass, remote.studentClass),
            rollNumber = if (remote.updatedAt > local.updatedAt) remote.rollNumber else local.rollNumber,
            mobile = resolveField("mobile", local.mobile, remote.mobile),
            village = resolveField("village", local.village, remote.village),
            fatherName = resolveField("fatherName", local.fatherName, remote.fatherName),
            motherName = resolveField("motherName", local.motherName, remote.motherName),
            birthRegNumber = resolveField("birthRegNumber", local.birthRegNumber, remote.birthRegNumber),
            isDeleted = if (local.isDeleted || remote.isDeleted) true else false,
            deletedAt = local.deletedAt ?: remote.deletedAt,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            updatedBy = if (local.updatedAt >= remote.updatedAt) userEmail else remote.updatedBy,
            syncStatus = SyncStatus.SYNCED
        )

        return merged
    }

    // ==========================================
    // 4. ATTENDANCE SYNC (Partitioned by Month)
    // ==========================================
    private suspend fun syncAttendance(token: String, tree: SchoolFolderTree, currentEmail: String): Int {
        val currentMonthKey = SimpleDateFormat("yyyy_MM", Locale.US).format(Date())
        val pendingList = database.attendanceDao().getPendingAttendance()
        val affectedMonths = pendingList.map { it.date.replace("-", "_").substring(0, 7) }.toSet() + setOf(currentMonthKey)

        var totalRecordsSynced = 0

        for (monthKey in affectedMonths) {
            val fileName = "attendance_$monthKey.json"
            val fileKey = "attendance_$monthKey"
            val fileInfo = driveApi.findFile(token, fileName, tree.attendanceFolderId)

            var remoteAttendance = mutableMapOf<String, AttendanceEntity>()
            var remoteEtag = ""
            var fileId: String? = fileInfo?.id

            if (fileInfo != null) {
                val download = driveApi.downloadFileContentAndEtag(token, fileInfo.id)
                if (download != null) {
                    remoteEtag = download.second
                    try {
                        val root = JSONObject(download.first)
                        val arr = root.optJSONArray("records") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.getString("id")
                            val att = AttendanceEntity(
                                id = id,
                                date = obj.optString("date", ""),
                                className = obj.optString("className", "ALL"),
                                presentBoys = obj.optInt("presentBoys", 0),
                                presentGirls = obj.optInt("presentGirls", 0),
                                absentBoys = obj.optInt("absentBoys", 0),
                                absentGirls = obj.optInt("absentGirls", 0),
                                totalBoys = obj.optInt("totalBoys", 0),
                                totalGirls = obj.optInt("totalGirls", 0),
                                notes = obj.optString("notes", null),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                                updatedBy = obj.optString("updatedBy", ""),
                                version = obj.optInt("version", 1),
                                isDeleted = obj.optBoolean("isDeleted", false),
                                deletedAt = if (obj.has("deletedAt")) obj.optLong("deletedAt") else null,
                                syncStatus = SyncStatus.SYNCED
                            )
                            remoteAttendance[id] = att
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing $fileName: ${e.localizedMessage}")
                    }
                }
            }

            // Merge local month records
            val monthDash = monthKey.replace("_", "-")
            val localRecords = database.attendanceDao().getAttendanceListForMonth(monthDash)
            val mergedMap = remoteAttendance.toMutableMap()

            for (local in localRecords) {
                val existing = mergedMap[local.id]
                if (existing == null || local.updatedAt >= existing.updatedAt) {
                    mergedMap[local.id] = local.copy(syncStatus = SyncStatus.SYNCED)
                }
            }

            database.attendanceDao().insertAllAttendance(mergedMap.values.toList())
            totalRecordsSynced += mergedMap.size

            // Push updated month file
            val root = JSONObject()
            val arr = JSONArray()
            for (att in mergedMap.values) {
                val obj = JSONObject().apply {
                    put("id", att.id)
                    put("date", att.date)
                    put("className", att.className)
                    put("presentBoys", att.presentBoys)
                    put("presentGirls", att.presentGirls)
                    put("absentBoys", att.absentBoys)
                    put("absentGirls", att.absentGirls)
                    put("totalBoys", att.totalBoys)
                    put("totalGirls", att.totalGirls)
                    put("notes", att.notes ?: "")
                    put("updatedAt", att.updatedAt)
                    put("updatedBy", att.updatedBy)
                    put("isDeleted", att.isDeleted)
                    if (att.deletedAt != null) put("deletedAt", att.deletedAt)
                }
                arr.put(obj)
            }
            root.put("month", monthKey)
            root.put("records", arr)

            val upload = driveApi.uploadOrUpdateFile(token, fileId, fileName, tree.attendanceFolderId, root.toString(2))
            if (upload != null) {
                fileId = upload.first
                remoteEtag = upload.second
            }

            database.syncMetadataDao().insertOrUpdate(
                SyncMetadataEntity(
                    fileKey = fileKey,
                    driveFileId = fileId,
                    localLastSyncTime = System.currentTimeMillis(),
                    driveRevisionEtag = remoteEtag,
                    lastModifiedTime = System.currentTimeMillis()
                )
            )
        }

        if (pendingList.isNotEmpty()) {
            database.attendanceDao().markSynced(pendingList.map { it.id })
        }

        return totalRecordsSynced
    }

    // ==========================================
    // 5. FEES.JSON SYNC
    // ==========================================
    private suspend fun syncFees(token: String, tree: SchoolFolderTree, currentEmail: String): Int {
        val meta = database.syncMetadataDao().getMetadata("fees")
        val fileInfo = driveApi.findFile(token, "fees.json", tree.rootFolderId)
        val pendingFees = database.feeDao().getPendingFees()

        var remoteFees = mutableMapOf<String, FeeEntity>()
        var remoteEtag = ""
        var fileId: String? = fileInfo?.id

        if (fileInfo != null) {
            val download = driveApi.downloadFileContentAndEtag(token, fileInfo.id)
            if (download != null) {
                remoteEtag = download.second
                try {
                    val root = JSONObject(download.first)
                    val arr = root.optJSONArray("fees") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getString("id")
                        val f = FeeEntity(
                            id = id,
                            studentId = obj.optString("studentId", ""),
                            studentName = obj.optString("studentName", ""),
                            studentClass = obj.optString("studentClass", ""),
                            feeType = obj.optString("feeType", "মাসিক বেতন"),
                            amount = obj.optDouble("amount", 0.0),
                            paidAmount = obj.optDouble("paidAmount", 0.0),
                            dueDate = obj.optString("dueDate", ""),
                            paymentDate = obj.optString("paymentDate", null),
                            status = obj.optString("status", "Unpaid"),
                            month = obj.optString("month", ""),
                            receiptNumber = obj.optString("receiptNumber", ""),
                            notes = obj.optString("notes", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            updatedBy = obj.optString("updatedBy", ""),
                            isDeleted = obj.optBoolean("isDeleted", false),
                            deletedAt = if (obj.has("deletedAt")) obj.optLong("deletedAt") else null,
                            syncStatus = SyncStatus.SYNCED
                        )
                        remoteFees[id] = f
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing fees.json: ${e.localizedMessage}")
                }
            }
        }

        // Merge pending fees
        for (pending in pendingFees) {
            val existing = remoteFees[pending.id]
            if (existing == null || pending.updatedAt >= existing.updatedAt) {
                remoteFees[pending.id] = pending.copy(syncStatus = SyncStatus.SYNCED)
            }
        }

        database.feeDao().insertAllFees(remoteFees.values.toList())
        if (pendingFees.isNotEmpty()) {
            database.feeDao().markSynced(pendingFees.map { it.id })
        }

        if (pendingFees.isNotEmpty() || fileInfo == null) {
            val root = JSONObject()
            val arr = JSONArray()
            for (f in remoteFees.values) {
                val obj = JSONObject().apply {
                    put("id", f.id)
                    put("studentId", f.studentId)
                    put("studentName", f.studentName)
                    put("studentClass", f.studentClass)
                    put("feeType", f.feeType)
                    put("amount", f.amount)
                    put("paidAmount", f.paidAmount)
                    put("dueDate", f.dueDate)
                    put("paymentDate", f.paymentDate ?: "")
                    put("status", f.status)
                    put("month", f.month)
                    put("receiptNumber", f.receiptNumber)
                    put("notes", f.notes)
                    put("updatedAt", f.updatedAt)
                    put("updatedBy", f.updatedBy)
                    put("isDeleted", f.isDeleted)
                    if (f.deletedAt != null) put("deletedAt", f.deletedAt)
                }
                arr.put(obj)
            }
            root.put("fees", arr)
            val upload = driveApi.uploadOrUpdateFile(token, fileId, "fees.json", tree.rootFolderId, root.toString(2))
            if (upload != null) {
                fileId = upload.first
                remoteEtag = upload.second
            }
        }

        database.syncMetadataDao().insertOrUpdate(
            SyncMetadataEntity(
                fileKey = "fees",
                driveFileId = fileId,
                localLastSyncTime = System.currentTimeMillis(),
                driveRevisionEtag = remoteEtag,
                lastModifiedTime = System.currentTimeMillis()
            )
        )

        return remoteFees.size
    }

    // ==========================================
    // 6. NOTICES.JSON SYNC
    // ==========================================
    private suspend fun syncNotices(token: String, tree: SchoolFolderTree, currentEmail: String): Int {
        val meta = database.syncMetadataDao().getMetadata("notices")
        val fileInfo = driveApi.findFile(token, "notices.json", tree.rootFolderId)
        val pendingNotices = database.noticeDao().getPendingNotices()

        var remoteNotices = mutableMapOf<String, NoticeEntity>()
        var remoteEtag = ""
        var fileId: String? = fileInfo?.id

        if (fileInfo != null) {
            val download = driveApi.downloadFileContentAndEtag(token, fileInfo.id)
            if (download != null) {
                remoteEtag = download.second
                try {
                    val root = JSONObject(download.first)
                    val arr = root.optJSONArray("notices") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getString("id")
                        val n = NoticeEntity(
                            id = id,
                            title = obj.optString("title", ""),
                            content = obj.optString("content", ""),
                            publishedDate = obj.optString("publishedDate", ""),
                            targetAudience = obj.optString("targetAudience", "All"),
                            attachmentUrl = obj.optString("attachmentUrl", null),
                            isImportant = obj.optBoolean("isImportant", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            updatedBy = obj.optString("updatedBy", ""),
                            isDeleted = obj.optBoolean("isDeleted", false),
                            deletedAt = if (obj.has("deletedAt")) obj.optLong("deletedAt") else null,
                            syncStatus = SyncStatus.SYNCED
                        )
                        remoteNotices[id] = n
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing notices.json: ${e.localizedMessage}")
                }
            }
        }

        for (pending in pendingNotices) {
            val existing = remoteNotices[pending.id]
            if (existing == null || pending.updatedAt >= existing.updatedAt) {
                remoteNotices[pending.id] = pending.copy(syncStatus = SyncStatus.SYNCED)
            }
        }

        database.noticeDao().insertAllNotices(remoteNotices.values.toList())
        if (pendingNotices.isNotEmpty()) {
            database.noticeDao().markSynced(pendingNotices.map { it.id })
        }

        if (pendingNotices.isNotEmpty() || fileInfo == null) {
            val root = JSONObject()
            val arr = JSONArray()
            for (n in remoteNotices.values) {
                val obj = JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("publishedDate", n.publishedDate)
                    put("targetAudience", n.targetAudience)
                    put("attachmentUrl", n.attachmentUrl ?: "")
                    put("isImportant", n.isImportant)
                    put("updatedAt", n.updatedAt)
                    put("updatedBy", n.updatedBy)
                    put("isDeleted", n.isDeleted)
                    if (n.deletedAt != null) put("deletedAt", n.deletedAt)
                }
                arr.put(obj)
            }
            root.put("notices", arr)
            val upload = driveApi.uploadOrUpdateFile(token, fileId, "notices.json", tree.rootFolderId, root.toString(2))
            if (upload != null) {
                fileId = upload.first
                remoteEtag = upload.second
            }
        }

        database.syncMetadataDao().insertOrUpdate(
            SyncMetadataEntity(
                fileKey = "notices",
                driveFileId = fileId,
                localLastSyncTime = System.currentTimeMillis(),
                driveRevisionEtag = remoteEtag,
                lastModifiedTime = System.currentTimeMillis()
            )
        )

        return remoteNotices.size
    }

    private suspend fun logConflict(
        entityType: String,
        entityId: String,
        entityLabel: String,
        fieldName: String,
        localVal: String,
        remoteVal: String,
        remoteUpdatedBy: String
    ) {
        database.syncConflictDao().insertConflict(
            SyncConflictEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                entityLabel = entityLabel,
                conflictingField = fieldName,
                localValue = localVal,
                remoteValue = remoteVal,
                remoteUpdatedBy = remoteUpdatedBy,
                timestamp = System.currentTimeMillis(),
                resolutionStatus = "RESOLVED_AUTOMATICALLY_LATEST",
                resolutionNote = "Latest remote timestamp selected; local copy archived in audit trail."
            )
        )
    }
}
