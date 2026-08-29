package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.model.SchoolDatabaseModel
import com.example.repository.SchoolRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class SyncState {
    SYNCED,
    SYNCING,
    PENDING_CHANGES,
    OFFLINE,
    ERROR
}

data class SyncSummary(
    val state: SyncState,
    val pendingCount: Int,
    val isOnline: Boolean,
    val lastSyncTime: Long,
    val message: String
)

data class RemoteBackupItem(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long,
    val lastUpdated: Long
)

/**
 * MultiUserSyncManager manages local-first offline storage, real-time sync queue,
 * conflict resolution, role-based access control (RBAC), and Google Drive backup/restore.
 */
class MultiUserSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val repository: SchoolRepository,
    private val googleDriveManager: GoogleDriveManager
) {
    private val TAG = "SCHOOL_SYNC"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs: SharedPreferences =
        context.getSharedPreferences("school_multiuser_sync_prefs", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    // Preferences keys
    companion object {
        private const val PREF_SCRIPT_URL = "apps_script_url"
        private const val PREF_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val PREF_GLOBAL_VERSION = "global_database_version"
        private const val PREF_AUTO_SYNC = "auto_sync_enabled"
        private const val PREF_CURRENT_USER_ROLE = "current_user_role"

        @Volatile
        private var INSTANCE: MultiUserSyncManager? = null

        fun getInstance(
            context: Context,
            db: AppDatabase,
            repository: SchoolRepository,
            googleDriveManager: GoogleDriveManager
        ): MultiUserSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MultiUserSyncManager(
                    context.applicationContext,
                    db,
                    repository,
                    googleDriveManager
                ).also { INSTANCE = it }
            }
        }
    }

    // State Flows
    private val _isOnline = MutableStateFlow(checkInitialNetworkState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs.getLong(PREF_LAST_SYNC_TIMESTAMP, 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _statusMessage = MutableStateFlow("সিঙ্ক প্রস্তুত")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _scriptUrl = MutableStateFlow(prefs.getString(PREF_SCRIPT_URL, "") ?: "")
    val scriptUrl: StateFlow<String> = _scriptUrl.asStateFlow()

    private val _currentUserRole = MutableStateFlow(prefs.getString(PREF_CURRENT_USER_ROLE, "Admin") ?: "Admin")
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    val pendingCount: Flow<Int> = db.syncQueueDao().getPendingCount()
    val allAuthorizedUsers: Flow<List<AuthorizedUserEntity>> = db.authorizedUserDao().getAllAuthorizedUsers()
    val allConflicts: Flow<List<SyncConflictEntity>> = db.syncConflictDao().getAllConflicts()
    val backupHistory: Flow<List<BackupHistoryEntity>> = db.backupHistoryDao().getAllBackups()

    init {
        registerNetworkCallback()
        observeQueueChanges()
        seedDefaultAdminIfEmpty()
    }

    private fun checkInitialNetworkState(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(builder, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    Log.d(TAG, "NETWORK_ONLINE: Auto-triggering pending queue sync")
                    scope.launch {
                        delay(1500)
                        triggerSync()
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                    _syncState.value = SyncState.OFFLINE
                    _statusMessage.value = "ইন্টারনেট বিচ্ছিন্ন (অফলাইন মোড)"
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.localizedMessage}")
        }
    }

    private fun observeQueueChanges() {
        scope.launch {
            db.syncQueueDao().getPendingCount().collect { count ->
                if (count > 0 && _syncState.value != SyncState.SYNCING) {
                    _syncState.value = SyncState.PENDING_CHANGES
                    _statusMessage.value = "$count টি পরিবর্তন ক্লাউডে আপলোডের অপেক্ষায়"
                } else if (count == 0 && _syncState.value == SyncState.PENDING_CHANGES) {
                    _syncState.value = SyncState.SYNCED
                    _statusMessage.value = "সমস্ত ডেটা সিঙ্কড আছে"
                }
            }
        }
    }

    private fun seedDefaultAdminIfEmpty() {
        scope.launch {
            val count = db.authorizedUserDao().getUserByEmail("admin")
            if (count == null) {
                val currentEmail = googleDriveManager.getAccountEmail() ?: "admin@school.edu"
                val defaultAdmin = AuthorizedUserEntity(
                    email = currentEmail.lowercase(),
                    displayName = googleDriveManager.getAccountName() ?: "প্রধান শিক্ষক (Admin)",
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
                db.authorizedUserDao().insertAuthorizedUser(defaultAdmin)
            }
        }
    }

    // -------------------------------------------------------------------------
    // LOCAL QUEUE MUTATIONS (Called on CRUD)
    // -------------------------------------------------------------------------

    suspend fun enqueueStudentChange(student: StudentEntity, action: String) {
        val userEmail = googleDriveManager.getAccountEmail() ?: "admin"
        val payloadJson = JSONObject().apply {
            put("studentId", student.id)
            put("studentClass", student.studentClass)
            put("section", student.section)
            put("rollNumber", student.rollNumber)
            put("name", student.name)
            put("parentContact", student.parentContact.ifEmpty { student.mobile })
            put("fatherName", student.fatherName)
            put("motherName", student.motherName)
            put("gender", student.gender)
            put("village", student.village)
            put("birthDate", student.birthDate)
            put("birthRegNumber", student.birthRegNumber)
            put("address", student.address)
            put("academicYear", student.academicYear)
            put("isSpecialNeeds", student.isSpecialNeeds)
            put("status", student.status)
            put("photoUri", student.photoUri ?: "")
            put("customValuesJson", student.customValuesJson)
            put("updatedBy", userEmail)
            put("updatedAt", System.currentTimeMillis())
            put("version", student.version)
            put("isDeleted", action == "DELETE")
        }.toString()

        val queueItem = SyncQueueEntity(
            entityType = "STUDENT",
            entityId = student.id,
            action = action,
            payloadJson = payloadJson,
            clientTimestamp = System.currentTimeMillis(),
            updatedBy = userEmail,
            clientVersion = student.version
        )
        db.syncQueueDao().enqueue(queueItem)
        checkAndTriggerAutoSync()
    }

    suspend fun enqueueAttendanceChange(attendance: AttendanceEntity, action: String) {
        val userEmail = googleDriveManager.getAccountEmail() ?: "admin"
        val payloadJson = JSONObject().apply {
            put("id", attendance.id)
            put("date", attendance.date)
            put("className", attendance.className)
            put("presentBoys", attendance.presentBoys)
            put("presentGirls", attendance.presentGirls)
            put("absentBoys", attendance.absentBoys)
            put("absentGirls", attendance.absentGirls)
            put("totalBoys", attendance.totalBoys)
            put("totalGirls", attendance.totalGirls)
            put("notes", attendance.notes ?: "")
            put("updatedBy", userEmail)
            put("updatedAt", System.currentTimeMillis())
            put("version", attendance.version)
            put("isDeleted", action == "DELETE")
        }.toString()

        val queueItem = SyncQueueEntity(
            entityType = "ATTENDANCE",
            entityId = attendance.id,
            action = action,
            payloadJson = payloadJson,
            clientTimestamp = System.currentTimeMillis(),
            updatedBy = userEmail,
            clientVersion = attendance.version
        )
        db.syncQueueDao().enqueue(queueItem)
        checkAndTriggerAutoSync()
    }

    suspend fun enqueueExamResultChange(result: ExamResultEntity, action: String) {
        val userEmail = googleDriveManager.getAccountEmail() ?: "admin"
        val payloadJson = JSONObject().apply {
            put("id", result.id)
            put("examName", result.examName)
            put("className", result.className)
            put("section", result.section)
            put("rollNumber", result.rollNumber)
            put("studentId", result.studentId ?: "")
            put("studentName", result.studentName)
            put("subject", result.subject)
            put("marks", result.marks)
            put("grade", result.grade)
            put("gpa", result.gpa)
            put("date", result.date)
            put("updatedBy", userEmail)
            put("updatedAt", System.currentTimeMillis())
            put("version", result.version)
            put("isDeleted", action == "DELETE")
        }.toString()

        val queueItem = SyncQueueEntity(
            entityType = "EXAM_RESULT",
            entityId = result.id,
            action = action,
            payloadJson = payloadJson,
            clientTimestamp = System.currentTimeMillis(),
            updatedBy = userEmail,
            clientVersion = result.version
        )
        db.syncQueueDao().enqueue(queueItem)
        checkAndTriggerAutoSync()
    }

    suspend fun enqueueSchoolInfoChange(info: SchoolInfoEntity) {
        val userEmail = googleDriveManager.getAccountEmail() ?: "admin"
        val payloadJson = JSONObject().apply {
            put("schoolName", info.schoolName)
            put("eiinCode", info.eiinCode)
            put("adminName", info.adminName)
            put("adminEmail", info.adminEmail)
            put("adminPhone", info.adminPhone)
            put("address", info.address)
            put("tagline", info.tagline)
            put("headTeacherName", info.headTeacherName)
            put("internalVillages", info.internalVillages)
            put("customSchoolInfoJson", info.customSchoolInfoJson)
            put("updatedBy", userEmail)
            put("updatedAt", System.currentTimeMillis())
        }.toString()

        val queueItem = SyncQueueEntity(
            entityType = "SCHOOL_INFO",
            entityId = info.id.toString(),
            action = "UPDATE",
            payloadJson = payloadJson,
            clientTimestamp = System.currentTimeMillis(),
            updatedBy = userEmail
        )
        db.syncQueueDao().enqueue(queueItem)
        checkAndTriggerAutoSync()
    }

    private fun checkAndTriggerAutoSync() {
        if (_isOnline.value && isAutoSyncEnabled()) {
            scope.launch {
                delay(800)
                triggerSync()
            }
        }
    }

    // -------------------------------------------------------------------------
    // CORE SYNC ENGINE: Push Queue + Pull Remote Deltas
    // -------------------------------------------------------------------------

    suspend fun triggerSync(): Boolean = withContext(Dispatchers.IO) {
        if (!_isOnline.value) {
            _syncState.value = SyncState.OFFLINE
            _statusMessage.value = "ইন্টারনেট সংযোগ নেই"
            return@withContext false
        }

        val userEmail = googleDriveManager.getAccountEmail()?.lowercase() ?: ""
        if (userEmail.isBlank()) {
            _syncState.value = SyncState.ERROR
            _statusMessage.value = "কোনো Google অ্যাকাউন্ট যুক্ত নেই"
            return@withContext false
        }

        _syncState.value = SyncState.SYNCING
        _statusMessage.value = "ক্লাউডের সাথে সিঙ্ক করা হচ্ছে..."

        try {
            val url = getScriptUrl()
            if (url.isNotBlank()) {
                return@withContext syncViaAppsScript(url, userEmail)
            } else {
                return@withContext syncViaGoogleDriveAppData(userEmail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.localizedMessage}", e)
            _syncState.value = SyncState.ERROR
            _statusMessage.value = "সিঙ্ক ত্রুটি: ${e.localizedMessage}"
            return@withContext false
        }
    }

    private suspend fun syncViaAppsScript(url: String, userEmail: String): Boolean {
        // 1. Check user permission & fetch status
        val statusRequest = JSONObject().apply {
            put("action", "get_status")
            put("userEmail", userEmail)
        }
        val statusResp = postJson(url, statusRequest)
        if (statusResp == null) {
            _syncState.value = SyncState.ERROR
            _statusMessage.value = "সার্ভার থেকে কোনো প্রতিক্রিয়া পাওয়া যায়নি"
            return false
        }

        if (statusResp.optString("status") == "unauthorized") {
            _syncState.value = SyncState.ERROR
            _statusMessage.value = statusResp.optString("message", "অননুমোদিত ইমেইল অ্যাকাউন্ট")
            return false
        }

        val role = statusResp.optString("role", "Teacher")
        _currentUserRole.value = role
        prefs.edit().putString(PREF_CURRENT_USER_ROLE, role).apply()

        // 2. Push Pending Queue Changes
        val pendingQueue = db.syncQueueDao().getPendingQueueList()
        if (pendingQueue.isNotEmpty()) {
            _statusMessage.value = "${pendingQueue.size}টি রেকর্ড পুশ করা হচ্ছে..."
            val changesArray = JSONArray()
            pendingQueue.forEach { q ->
                val cObj = JSONObject().apply {
                    put("queueId", q.queueId)
                    put("entityType", q.entityType)
                    put("entityId", q.entityId)
                    put("action", q.action)
                    put("payloadJson", q.payloadJson)
                    put("clientTimestamp", q.clientTimestamp)
                }
                changesArray.put(cObj)
            }

            val pushReq = JSONObject().apply {
                put("action", "sync_push")
                put("userEmail", userEmail)
                put("changes", changesArray)
            }

            val pushResp = postJson(url, pushReq)
            if (pushResp != null && pushResp.optString("status") == "success") {
                pendingQueue.forEach { q ->
                    db.syncQueueDao().delete(q.queueId)
                }
                Log.d(TAG, "SYNC_PUSH_SUCCESS: Cleared ${pendingQueue.size} queue items")
            }
        }

        // 3. Pull Delta Changes
        val lastSync = getLastSyncTimestamp()
        val clientGlobalVersion = getGlobalDatabaseVersion()
        val pullReq = JSONObject().apply {
            put("action", "sync_pull")
            put("userEmail", userEmail)
            put("sinceTimestamp", lastSync)
            put("clientGlobalVersion", clientGlobalVersion)
        }

        val pullResp = postJson(url, pullReq)
        if (pullResp != null) {
            val pullStatus = pullResp.optString("status")
            if (pullStatus == "full_refresh_required") {
                val masterJson = pullResp.optJSONObject("masterDatabase")
                if (masterJson != null) {
                    val masterModel = SchoolDatabaseModel.fromJson(masterJson.toString())
                    if (masterModel != null) {
                        repository.importMasterModel(masterModel)
                        val newVer = pullResp.optLong("globalDatabaseVersion", 1L)
                        setGlobalDatabaseVersion(newVer)
                    }
                }
            } else if (pullStatus == "success") {
                applyPullDeltas(pullResp)
                val newVer = pullResp.optLong("globalDatabaseVersion", clientGlobalVersion)
                setGlobalDatabaseVersion(newVer)
            }
        }

        val now = System.currentTimeMillis()
        setLastSyncTimestamp(now)
        _syncState.value = SyncState.SYNCED
        _statusMessage.value = "সফলভাবে সিঙ্ক সম্পন্ন হয়েছে!"
        return true
    }

    private suspend fun applyPullDeltas(json: JSONObject) {
        // Students
        val studentsArr = json.optJSONArray("students")
        if (studentsArr != null && studentsArr.length() > 0) {
            for (i in 0 until studentsArr.length()) {
                val obj = studentsArr.getJSONObject(i)
                val sId = obj.optString("studentId", obj.optString("id", ""))
                if (sId.isBlank()) continue

                val isDeleted = obj.optBoolean("isDeleted", false)
                if (isDeleted) {
                    db.studentDao().deleteStudentById(sId)
                    continue
                }

                val existing = db.studentDao().getStudentById(sId)
                val remoteVersion = obj.optInt("version", 1)
                val remoteUpdatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                val remoteUpdatedBy = obj.optString("updatedBy", "")

                val incoming = StudentEntity(
                    id = sId,
                    studentClass = obj.optString("studentClass", "১ম শ্রেণি"),
                    section = obj.optString("section", "ক"),
                    rollNumber = obj.optInt("rollNumber", 1),
                    name = obj.optString("name", "শিক্ষার্থী"),
                    parentContact = obj.optString("parentContact", obj.optString("mobile", "")),
                    fatherName = obj.optString("fatherName", ""),
                    motherName = obj.optString("motherName", ""),
                    gender = obj.optString("gender", "ছাত্র"),
                    village = obj.optString("village", ""),
                    birthDate = obj.optString("birthDate", ""),
                    birthRegNumber = obj.optString("birthRegNumber", ""),
                    address = obj.optString("address", ""),
                    academicYear = obj.optString("academicYear", "২০২৬"),
                    isSpecialNeeds = obj.optBoolean("isSpecialNeeds", false),
                    status = obj.optString("status", "Current"),
                    photoUri = if (obj.optString("photoUri").isNotBlank()) obj.optString("photoUri") else null,
                    customValuesJson = obj.optString("customValuesJson", "{}"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = remoteUpdatedAt,
                    updatedBy = remoteUpdatedBy,
                    version = remoteVersion,
                    isDeleted = false
                )

                if (existing != null) {
                    // Smart 3-Way Field Merger & Conflict Detector
                    if (existing.updatedAt > remoteUpdatedAt && existing.version >= remoteVersion) {
                        // Local has more recent concurrent edits: log conflict & keep local
                        db.syncConflictDao().insertConflict(
                            SyncConflictEntity(
                                entityType = "STUDENT",
                                entityId = sId,
                                entityLabel = "${existing.name} (${existing.studentClass})",
                                conflictingField = "All Fields",
                                localValue = "v${existing.version} @ ${existing.updatedAt}",
                                remoteValue = "v$remoteVersion @ $remoteUpdatedAt",
                                remoteUpdatedBy = remoteUpdatedBy,
                                resolutionStatus = "RESOLVED_AUTOMATICALLY_LATEST",
                                resolutionNote = "স্থানীয় পরিবর্তন সাম্প্রতিক হওয়ায় বহাল রাখা হয়েছে"
                            )
                        )
                    } else {
                        db.studentDao().updateStudent(incoming)
                    }
                } else {
                    db.studentDao().insertStudent(incoming)
                }
            }
        }

        // Attendance
        val attendanceArr = json.optJSONArray("attendance")
        if (attendanceArr != null && attendanceArr.length() > 0) {
            for (i in 0 until attendanceArr.length()) {
                val obj = attendanceArr.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                if (obj.optBoolean("isDeleted", false)) {
                    db.attendanceDao().deleteAttendanceById(id)
                    continue
                }
                val record = AttendanceEntity(
                    id = id,
                    date = obj.optString("date", ""),
                    className = obj.optString("className", "১ম শ্রেণি"),
                    presentBoys = obj.optInt("presentBoys", 0),
                    presentGirls = obj.optInt("presentGirls", 0),
                    absentBoys = obj.optInt("absentBoys", 0),
                    absentGirls = obj.optInt("absentGirls", 0),
                    totalBoys = obj.optInt("totalBoys", 0),
                    totalGirls = obj.optInt("totalGirls", 0),
                    notes = obj.optString("notes", null),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    updatedBy = obj.optString("updatedBy", ""),
                    version = obj.optInt("version", 1),
                    isDeleted = false
                )
                db.attendanceDao().insertAttendance(record)
            }
        }

        // Exam Results
        val resultsArr = json.optJSONArray("examResults")
        if (resultsArr != null && resultsArr.length() > 0) {
            for (i in 0 until resultsArr.length()) {
                val obj = resultsArr.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                if (obj.optBoolean("isDeleted", false)) {
                    db.examResultDao().deleteResultById(id)
                    continue
                }
                val res = ExamResultEntity(
                    id = id,
                    examName = obj.optString("examName", "১ম সাময়িক"),
                    className = obj.optString("className", "১ম শ্রেণি"),
                    section = obj.optString("section", "ক"),
                    rollNumber = obj.optInt("rollNumber", 1),
                    studentId = obj.optString("studentId", null),
                    studentName = obj.optString("studentName", ""),
                    subject = obj.optString("subject", "বাংলা"),
                    marks = obj.optDouble("marks", 0.0),
                    grade = obj.optString("grade", "A+"),
                    gpa = obj.optDouble("gpa", 5.0),
                    date = obj.optString("date", ""),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    updatedBy = obj.optString("updatedBy", ""),
                    version = obj.optInt("version", 1),
                    isDeleted = false
                )
                db.examResultDao().insertResult(res)
            }
        }
    }

    private suspend fun syncViaGoogleDriveAppData(userEmail: String): Boolean {
        // Fallback: Direct Hidden Google Drive AppData Sync
        val pendingCount = db.syncQueueDao().getPendingQueueList().size
        if (pendingCount > 0) {
            val masterModel = repository.exportToMasterModel()
            val jsonString = masterModel.toJson(true)
            val res = googleDriveManager.uploadDatabase(jsonString, masterModel.studentsList.size)
            if (res is DriveOperationResult.Success) {
                db.syncQueueDao().clearQueue()
                val now = System.currentTimeMillis()
                setLastSyncTimestamp(now)
                _syncState.value = SyncState.SYNCED
                _statusMessage.value = "Google Drive এ ব্যাকআপ সফল হয়েছে!"
                return true
            } else if (res is DriveOperationResult.Error) {
                _syncState.value = SyncState.ERROR
                _statusMessage.value = res.message
                return false
            }
        }
        _syncState.value = SyncState.SYNCED
        _statusMessage.value = "ড্রাইভ সিঙ্ক সম্পন্ন"
        return true
    }

    private fun postJson(url: String, jsonBody: JSONObject): JSONObject? {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                JSONObject(respStr)
            } else {
                Log.e(TAG, "HTTP Error ${response.code}: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed: ${e.localizedMessage}")
            null
        }
    }

    // -------------------------------------------------------------------------
    // BACKUP & RESTORE ACTIONS
    // -------------------------------------------------------------------------

    suspend fun createManualBackup(note: String = "ম্যানুয়াল ব্যাকআপ"): Result<String> = withContext(Dispatchers.IO) {
        val userEmail = googleDriveManager.getAccountEmail() ?: "admin"
        val masterModel = repository.exportToMasterModel()
        val jsonStr = masterModel.toJson(true)
        val size = jsonStr.toByteArray(Charsets.UTF_8).size.toLong()
        val records = masterModel.studentsList.size + masterModel.attendanceList.size + masterModel.examResultsList.size

        val uploadRes = googleDriveManager.uploadDatabase(jsonStr, records)
        if (uploadRes is DriveOperationResult.Success) {
            val history = BackupHistoryEntity(
                backupId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                globalVersion = getGlobalDatabaseVersion(),
                createdByEmail = userEmail,
                recordCount = records,
                fileSize = size,
                driveFileId = uploadRes.data.id,
                backupType = "MANUAL",
                note = note
            )
            db.backupHistoryDao().insertBackup(history)
            Result.success("Google Drive এ ব্যাকআপ সফল হয়েছে! মোট $records টি রেকর্ড সংরক্ষিত।")
        } else {
            Result.failure(Exception("ড্রাইভ ব্যাকআপ ব্যর্থ হয়েছে"))
        }
    }

    suspend fun restoreBackupWithSafetySnapshot(targetBackupId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        // 1. Create Pre-Restore Safety Snapshot
        createManualBackup("রিস্টোর পূর্ববর্তী অটোমেটিক সেফটি স্ন্যাপশট")

        // 2. Download and apply remote backup
        val downloadRes = googleDriveManager.downloadDatabase(targetBackupId, repository)
        if (downloadRes is DriveOperationResult.Success) {
            val currentGlobalVer = getGlobalDatabaseVersion() + 1
            setGlobalDatabaseVersion(currentGlobalVer)
            setLastSyncTimestamp(System.currentTimeMillis())
            _syncState.value = SyncState.SYNCED
            _statusMessage.value = "সফলভাবে ডাটাবেস রিস্টোর সম্পন্ন হয়েছে!"
            Result.success("ডাটাবেস পুনরুদ্ধার সম্পন্ন হয়েছে (${downloadRes.data.studentsList.size} জন শিক্ষার্থী)")
        } else {
            Result.failure(Exception("রিস্টোর সম্পন্ন করা যায়নি"))
        }
    }

    // -------------------------------------------------------------------------
    // USER MANAGEMENT & RBAC
    // -------------------------------------------------------------------------

    suspend fun saveAuthorizedUser(user: AuthorizedUserEntity): Boolean = withContext(Dispatchers.IO) {
        db.authorizedUserDao().insertAuthorizedUser(user)
        val url = getScriptUrl()
        if (url.isNotBlank() && _isOnline.value) {
            val adminEmail = googleDriveManager.getAccountEmail() ?: "admin"
            val req = JSONObject().apply {
                put("action", "user_save")
                put("userEmail", adminEmail)
                put("user", JSONObject().apply {
                    put("email", user.email.lowercase())
                    put("displayName", user.displayName)
                    put("role", user.role)
                    put("status", user.status)
                    put("canViewStudents", user.canViewStudents)
                    put("canEditStudents", user.canEditStudents)
                    put("canDeleteStudents", user.canDeleteStudents)
                    put("canViewAttendance", user.canViewAttendance)
                    put("canEditAttendance", user.canEditAttendance)
                    put("canViewExamResults", user.canViewExamResults)
                    put("canEditExamResults", user.canEditExamResults)
                    put("canManageSettings", user.canManageSettings)
                    put("canManageUsers", user.canManageUsers)
                    put("canBackupRestore", user.canBackupRestore)
                })
            }
            postJson(url, req)
        }
        true
    }

    suspend fun deleteAuthorizedUser(email: String): Boolean = withContext(Dispatchers.IO) {
        db.authorizedUserDao().deleteByEmail(email)
        val url = getScriptUrl()
        if (url.isNotBlank() && _isOnline.value) {
            val adminEmail = googleDriveManager.getAccountEmail() ?: "admin"
            val req = JSONObject().apply {
                put("action", "user_delete")
                put("userEmail", adminEmail)
                put("targetEmail", email.lowercase())
            }
            postJson(url, req)
        }
        true
    }

    suspend fun syncAuthorizedUsersFromBackend(): Boolean = withContext(Dispatchers.IO) {
        val url = getScriptUrl()
        if (url.isBlank() || !_isOnline.value) return@withContext false
        val adminEmail = googleDriveManager.getAccountEmail() ?: "admin"
        val req = JSONObject().apply {
            put("action", "user_list")
            put("userEmail", adminEmail)
        }
        val resp = postJson(url, req) ?: return@withContext false
        val usersArr = resp.optJSONArray("users") ?: return@withContext false

        val list = mutableListOf<AuthorizedUserEntity>()
        for (i in 0 until usersArr.length()) {
            val u = usersArr.getJSONObject(i)
            list.add(
                AuthorizedUserEntity(
                    email = u.optString("email", "").lowercase(),
                    displayName = u.optString("displayName", ""),
                    role = u.optString("role", "Teacher"),
                    status = u.optString("status", "Active"),
                    canViewStudents = u.optBoolean("canViewStudents", true),
                    canEditStudents = u.optBoolean("canEditStudents", true),
                    canDeleteStudents = u.optBoolean("canDeleteStudents", false),
                    canViewAttendance = u.optBoolean("canViewAttendance", true),
                    canEditAttendance = u.optBoolean("canEditAttendance", true),
                    canViewExamResults = u.optBoolean("canViewExamResults", true),
                    canEditExamResults = u.optBoolean("canEditExamResults", true),
                    canManageSettings = u.optBoolean("canManageSettings", false),
                    canManageUsers = u.optBoolean("canManageUsers", false),
                    canBackupRestore = u.optBoolean("canBackupRestore", false),
                    addedBy = u.optString("addedBy", "system"),
                    addedAt = u.optLong("addedAt", System.currentTimeMillis())
                )
            )
        }
        if (list.isNotEmpty()) {
            db.authorizedUserDao().insertAllAuthorizedUsers(list)
        }
        true
    }

    // -------------------------------------------------------------------------
    // GETTERS / SETTERS
    // -------------------------------------------------------------------------

    fun getScriptUrl(): String = prefs.getString(PREF_SCRIPT_URL, "") ?: ""

    fun setScriptUrl(url: String) {
        prefs.edit().putString(PREF_SCRIPT_URL, url.trim()).apply()
        _scriptUrl.value = url.trim()
        if (url.isNotBlank() && _isOnline.value) {
            scope.launch {
                triggerSync()
                syncAuthorizedUsersFromBackend()
            }
        }
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong(PREF_LAST_SYNC_TIMESTAMP, 0L)

    private fun setLastSyncTimestamp(ts: Long) {
        prefs.edit().putLong(PREF_LAST_SYNC_TIMESTAMP, ts).apply()
        _lastSyncTime.value = ts
    }

    fun getGlobalDatabaseVersion(): Long = prefs.getLong(PREF_GLOBAL_VERSION, 1L)

    private fun setGlobalDatabaseVersion(ver: Long) {
        prefs.edit().putLong(PREF_GLOBAL_VERSION, ver).apply()
    }

    fun isAutoSyncEnabled(): Boolean = prefs.getBoolean(PREF_AUTO_SYNC, true)

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_SYNC, enabled).apply()
    }

    fun getSampleBackendScript(): String {
        return try {
            context.assets.open("google_apps_script/SchoolSyncBackend.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "// Backend script asset not found"
        }
    }
}
