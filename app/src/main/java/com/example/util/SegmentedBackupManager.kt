package com.example.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.model.*
import com.example.repository.SchoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Robust, Segmented, Granular & Differential Backup Manager for ANWESHA School Management.
 * Splitting database into modular JSON files so only modified segments are uploaded/updated.
 * Supports Settings Backup, Selective/Merge/Clean Restore Modes, and Differential Sync.
 */
class SegmentedBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "SegmentedBackupManager"
        private const val PREFS_NAME = "segmented_backup_prefs"
        private const val PREF_KEY_HASH_PREFIX = "hash_"
        private const val PREF_KEY_LAST_SYNC_TIME = "last_sync_timestamp"
        private const val PREF_KEY_LAST_SYNC_RESULT = "last_sync_result"

        val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // ==========================================
    // 1. GENERATE ALL SEGMENTS FROM LOCAL DB
    // ==========================================

    suspend fun generateAllSegments(repository: SchoolRepository): List<BackupSegmentItem> = withContext(Dispatchers.IO) {
        val segments = mutableListOf<BackupSegmentItem>()

        // 1. School Profile
        val info = repository.schoolInfo.firstOrNull() ?: SchoolInfoEntity(
            schoolName = "অন্বেষা বিদ্যালয়",
            eiinCode = "123456",
            adminName = "প্রধান শিক্ষক"
        )
        val infoJson = JSONObject().apply {
            put("schoolName", info.schoolName)
            put("eiinCode", info.eiinCode)
            put("address", info.address)
            put("tagline", info.tagline)
            put("phone", info.phone)
            put("email", info.email)
            put("headTeacherName", info.headTeacherName)
            put("adminName", info.adminName)
            put("adminEmail", info.adminEmail)
            put("adminPhone", info.adminPhone)
            put("logoUri", info.logoUri ?: "")
            put("internalVillages", info.internalVillages)
            put("customSchoolInfoJson", info.customSchoolInfoJson)
            put("createdDate", info.createdDate)
            put("updatedAt", info.updatedAt)
            put("version", info.version)
        }.toString(2)

        segments.add(
            BackupSegmentItem(
                segmentKey = "school_profile",
                fileName = "school_profile.json",
                titleBn = "বিদ্যালয় প্রোফাইল ও তথ্য",
                recordCount = 1,
                jsonContent = infoJson
            )
        )

        // 2. Settings and Preferences Backup
        val settingsJson = generateSettingsJson()
        segments.add(
            BackupSegmentItem(
                segmentKey = "settings_and_preferences",
                fileName = "settings_and_preferences.json",
                titleBn = "অ্যাপ সেটিংস ও নিরাপত্তা কনফিগারেশন",
                recordCount = 1,
                jsonContent = settingsJson
            )
        )

        // 3. Users / Staff
        val users = repository.allUsers.firstOrNull() ?: emptyList()
        val usersArray = JSONArray()
        users.forEach { u ->
            usersArray.put(JSONObject().apply {
                put("userId", u.userId)
                put("name", u.name)
                put("email", u.email)
                put("phone", u.phone)
                put("role", u.role)
                put("status", u.status)
                put("securityPinHash", u.securityPinHash)
                put("createdDate", u.createdDate)
                put("createdAt", u.createdAt)
                put("updatedAt", u.updatedAt)
                put("version", u.version)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "school_users",
                fileName = "school_users.json",
                titleBn = "শিক্ষক ও ব্যবহারকারী তালিকা",
                recordCount = users.size,
                jsonContent = usersArray.toString(2)
            )
        )

        // 4. Students (Split Class-Wise for granular sync)
        val allStudents = repository.allStudents.firstOrNull() ?: emptyList()
        val studentsByClass = allStudents.groupBy { it.studentClass.ifBlank { "Unassigned" } }

        if (studentsByClass.isEmpty()) {
            val emptyArray = JSONArray()
            segments.add(
                BackupSegmentItem(
                    segmentKey = "students_all",
                    fileName = "students_all.json",
                    titleBn = "শিক্ষার্থীদের সম্পূর্ণ তথ্য",
                    recordCount = 0,
                    jsonContent = emptyArray.toString(2)
                )
            )
        } else {
            studentsByClass.forEach { (className, classStudents) ->
                val safeClassName = sanitizeClassName(className)
                val classArray = JSONArray()
                classStudents.forEach { s ->
                    classArray.put(studentToJson(s))
                }
                segments.add(
                    BackupSegmentItem(
                        segmentKey = "students_class_$safeClassName",
                        fileName = "students_class_$safeClassName.json",
                        titleBn = "$className শিক্ষার্থী তালিকা",
                        recordCount = classStudents.size,
                        jsonContent = classArray.toString(2)
                    )
                )
            }
        }

        // 5. Attendance Records
        val attendance = repository.allAttendance.firstOrNull() ?: emptyList()
        val attendanceArray = JSONArray()
        attendance.forEach { a ->
            attendanceArray.put(JSONObject().apply {
                put("id", a.id)
                put("date", a.date)
                put("className", a.className)
                put("presentBoys", a.presentBoys)
                put("presentGirls", a.presentGirls)
                put("absentBoys", a.absentBoys)
                put("absentGirls", a.absentGirls)
                put("totalBoys", a.totalBoys)
                put("totalGirls", a.totalGirls)
                put("notes", a.notes ?: "")
                put("createdAt", a.createdAt)
                put("updatedAt", a.updatedAt)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "attendance_records",
                fileName = "attendance_records.json",
                titleBn = "উপস্থিতি ও হাজিরা তথ্য",
                recordCount = attendance.size,
                jsonContent = attendanceArray.toString(2)
            )
        )

        // 6. Routines
        val routines = repository.allRoutineItems.firstOrNull() ?: emptyList()
        val routineArray = JSONArray()
        routines.forEach { rt ->
            routineArray.put(JSONObject().apply {
                put("id", rt.id)
                put("routineType", rt.routineType)
                put("className", rt.className)
                put("subject", rt.subject)
                put("teacher", rt.teacher)
                put("day", rt.day)
                put("startTime", rt.startTime)
                put("endTime", rt.endTime)
                put("periodName", rt.periodName)
                put("roomNo", rt.roomNo)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "routine_items",
                fileName = "routine_items.json",
                titleBn = "ক্লাস ও পরীক্ষার রুটিন",
                recordCount = routines.size,
                jsonContent = routineArray.toString(2)
            )
        )

        // 10. Document Templates
        val templates = repository.allDocumentTemplates.firstOrNull() ?: emptyList()
        val templatesArray = JSONArray()
        templates.forEach { t ->
            templatesArray.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("contentTemplate", t.contentTemplate)
                put("createdDate", t.createdDate)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "document_templates",
                fileName = "document_templates.json",
                titleBn = "প্রত্যয়ন ও ডকুমেন্ট টেমপ্লেট",
                recordCount = templates.size,
                jsonContent = templatesArray.toString(2)
            )
        )

        // 11. Student Attached Documents
        val studentDocs = repository.allStudentDocuments.firstOrNull() ?: emptyList()
        val studentDocsArray = JSONArray()
        studentDocs.forEach { d ->
            studentDocsArray.put(JSONObject().apply {
                put("id", d.id)
                put("studentId", d.studentId)
                put("title", d.title)
                put("documentType", d.documentType)
                put("fileUri", d.fileUri)
                put("fileType", d.fileType)
                put("extractedText", d.extractedText)
                put("pageCount", d.pageCount)
                put("notes", d.notes)
                put("scanDate", d.scanDate)
                put("createdAt", d.createdAt)
                put("updatedAt", d.updatedAt)
                put("version", d.version)
                put("isDeleted", d.isDeleted)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "student_documents",
                fileName = "student_documents.json",
                titleBn = "শিক্ষার্থীদের সংযুক্ত নথিপত্র (Scanned Docs)",
                recordCount = studentDocs.size,
                jsonContent = studentDocsArray.toString(2)
            )
        )

        // 8. Custom Fields & Formulas
        val customFields = repository.customFields.firstOrNull() ?: emptyList()
        val formulas = repository.formulaRules.firstOrNull() ?: emptyList()
        val customObj = JSONObject().apply {
            val fieldsArray = JSONArray()
            customFields.forEach { cf ->
                fieldsArray.put(JSONObject().apply {
                    put("id", cf.id)
                    put("name", cf.name)
                    put("fieldType", cf.fieldType)
                    put("optionsJson", cf.optionsJson ?: "")
                    put("isCalculated", cf.isCalculated)
                    put("formulaRuleId", cf.formulaRuleId ?: "")
                    put("groupName", cf.groupName)
                    put("orderIndex", cf.orderIndex)
                })
            }
            put("customFields", fieldsArray)

            val rulesArray = JSONArray()
            formulas.forEach { fr ->
                rulesArray.put(JSONObject().apply {
                    put("id", fr.id)
                    put("ruleName", fr.ruleName)
                    put("targetFieldName", fr.targetFieldName)
                    put("sourceField", fr.sourceField)
                    put("operator", fr.operator)
                    put("conditionValue", fr.conditionValue)
                    put("resultIfTrue", fr.resultIfTrue)
                    put("resultIfFalse", fr.resultIfFalse)
                })
            }
            put("formulaRules", rulesArray)
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "custom_fields_and_formulas",
                fileName = "custom_fields_and_formulas.json",
                titleBn = "কাস্টম ফিল্ড ও ফর্মুলা রুলস",
                recordCount = customFields.size + formulas.size,
                jsonContent = customObj.toString(2)
            )
        )

        val driveSetup = GoogleDriveSetupManager(context)

        // 9. Media & Images (Student Photos & School Logo)
        if (driveSetup.isSyncImagesEnabled()) {
            val mediaObj = JSONObject()
            val photosArray = JSONArray()
            var photoCount = 0
            allStudents.filter { !it.photoUri.isNullOrBlank() }.forEach { st ->
                photosArray.put(JSONObject().apply {
                    put("studentId", st.id)
                    put("name", st.name)
                    put("photoUri", st.photoUri)
                })
                photoCount++
            }
            mediaObj.put("studentPhotos", photosArray)
            val schoolInfoLogo = repository.schoolInfo.firstOrNull()?.logoUri
            mediaObj.put("schoolLogoUri", schoolInfoLogo ?: "")
            if (schoolInfoLogo != null && schoolInfoLogo.isNotBlank()) photoCount++

            segments.add(
                BackupSegmentItem(
                    segmentKey = "media_and_images",
                    fileName = "media_and_images.json",
                    titleBn = "শিক্ষার্থী ও বিদ্যালয়ের ছবি/লোগো (Images)",
                    recordCount = photoCount,
                    jsonContent = mediaObj.toString(2)
                )
            )
        }

        // 10. PDF, Admit Card & Seat Plan Templates
        if (driveSetup.isSyncPdfsEnabled()) {
            val pdfDocsObj = JSONObject()
            val admitPrefs = context.getSharedPreferences("admit_card_prefs", Context.MODE_PRIVATE)
            val admitConfig = JSONObject()
            admitPrefs.all.forEach { (k, v) ->
                admitConfig.put(k, v)
            }
            pdfDocsObj.put("admitCardPreferences", admitConfig)

            val seatPrefs = context.getSharedPreferences("seat_plan_prefs", Context.MODE_PRIVATE)
            val seatConfig = JSONObject()
            seatPrefs.all.forEach { (k, v) ->
                seatConfig.put(k, v)
            }
            pdfDocsObj.put("seatPlanPreferences", seatConfig)

            val certPrefs = context.getSharedPreferences("certificate_prefs", Context.MODE_PRIVATE)
            val certConfig = JSONObject()
            certPrefs.all.forEach { (k, v) ->
                certConfig.put(k, v)
            }
            pdfDocsObj.put("certificatePreferences", certConfig)

            segments.add(
                BackupSegmentItem(
                    segmentKey = "pdf_and_documents_config",
                    fileName = "pdf_and_documents_config.json",
                    titleBn = "এডমিট কার্ড, সিটপ্ল্যান ও ডকুমেন্ট টেমপ্লেট (PDFs)",
                    recordCount = templates.size + 3,
                    jsonContent = pdfDocsObj.toString(2)
                )
            )
        }

        // Attach statuses based on cached hashes
        segments.map { seg ->
            val cachedHash = getStoredSegmentHash(seg.segmentKey)
            val status = when {
                cachedHash == null -> SegmentSyncStatus.NEW_PENDING
                cachedHash != seg.contentHash -> SegmentSyncStatus.MODIFIED_LOCALLY
                else -> SegmentSyncStatus.SYNCED
            }
            seg.copy(status = status)
        }
    }

    private fun generateSettingsJson(): String {
        val root = JSONObject()
        try {
            // Security Prefs
            val secPrefs = context.getSharedPreferences("school_app_security_prefs", Context.MODE_PRIVATE)
            val secObj = JSONObject()
            secPrefs.all.forEach { (k, v) ->
                secObj.put(k, v)
            }
            root.put("security_preferences", secObj)

            // General & Display Settings
            val appPrefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
            val appObj = JSONObject()
            appPrefs.all.forEach { (k, v) ->
                appObj.put(k, v)
            }
            root.put("app_settings", appObj)

            root.put("backup_version", 2)
            root.put("timestamp", System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Error generating settings json", e)
        }
        return root.toString(2)
    }

    private fun restoreSettingsFromJson(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            if (root.has("security_preferences")) {
                val secObj = root.getJSONObject("security_preferences")
                val secPrefs = context.getSharedPreferences("school_app_security_prefs", Context.MODE_PRIVATE)
                val editor = secPrefs.edit()
                val keys = secObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = secObj.get(k)
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v.toFloat())
                        is String -> editor.putString(k, v)
                    }
                }
                editor.apply()
            }

            if (root.has("app_settings")) {
                val appObj = root.getJSONObject("app_settings")
                val appPrefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
                val editor = appPrefs.edit()
                val keys = appObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = appObj.get(k)
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v.toFloat())
                        is String -> editor.putString(k, v)
                    }
                }
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring settings from json: ${e.message}")
        }
    }

    private fun studentToJson(s: StudentEntity): JSONObject {
        return JSONObject().apply {
            put("id", s.id)
            put("studentClass", s.studentClass)
            put("section", s.section)
            put("rollNumber", s.rollNumber)
            put("name", s.name)
            put("parentContact", s.parentContact.ifEmpty { s.mobile })
            put("mobile", s.mobile)
            put("fatherName", s.fatherName)
            put("motherName", s.motherName)
            put("gender", s.gender)
            put("village", s.village)
            put("birthDate", s.birthDate)
            put("birthRegNumber", s.birthRegNumber)
            put("address", s.address)
            put("academicYear", s.academicYear)
            put("isSpecialNeeds", s.isSpecialNeeds)
            put("status", s.status)
            put("photoUri", s.photoUri ?: "")
            put("customValuesJson", s.customValuesJson)
            put("createdAt", s.createdAt)
            put("updatedAt", s.updatedAt)
            put("version", s.version)
        }
    }

    private fun sanitizeClassName(className: String): String {
        return when (className.trim()) {
            "১ম শ্রেণি", "1st", "Class 1", "১ম" -> "1"
            "২য় শ্রেণি", "২য় শ্রেণি", "2nd", "Class 2", "২য়", "২য়" -> "2"
            "৩য় শ্রেণি", "৩য় শ্রেণি", "3rd", "Class 3", "৩য়", "৩য়" -> "3"
            "৪র্থ শ্রেণি", "4th", "Class 4", "৪র্থ" -> "4"
            "৫ম শ্রেণি", "5th", "Class 5", "৫ম" -> "5"
            "প্রাক-প্রাথমিক", "Play", "প্লে" -> "play"
            "শিশু", "Nursery", "নার্সারি" -> "nursery"
            else -> className.replace(Regex("[^a-zA-Z0-9\u0980-\u09FF]"), "_").take(20)
        }
    }

    // ==========================================
    // 2. HASH STORAGE & DIFFERENTIAL CHECK
    // ==========================================

    fun getStoredSegmentHash(segmentKey: String): String? {
        return prefs.getString(PREF_KEY_HASH_PREFIX + segmentKey, null)
    }

    fun saveStoredSegmentHash(segmentKey: String, hash: String) {
        prefs.edit().putString(PREF_KEY_HASH_PREFIX + segmentKey, hash).apply()
    }

    fun clearAllStoredHashes() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREF_KEY_HASH_PREFIX) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(PREF_KEY_LAST_SYNC_TIME, 0L)
    }

    fun setLastSyncTimestamp(ts: Long, summary: String = "") {
        prefs.edit()
            .putLong(PREF_KEY_LAST_SYNC_TIME, ts)
            .putString(PREF_KEY_LAST_SYNC_RESULT, summary)
            .apply()
    }

    // ==========================================
    // 3. CLOUD DIFFERENTIAL SYNC (GOOGLE DRIVE)
    // ==========================================

    data class SyncResult(
        val totalSegments: Int,
        val uploadedCount: Int,
        val skippedCount: Int,
        val errorCount: Int,
        val manifestUpdated: Boolean,
        val details: List<String>
    )

    suspend fun syncSegmentsToDrive(
        accessToken: String,
        folderId: String,
        repository: SchoolRepository,
        onProgress: (current: Int, total: Int, segment: BackupSegmentItem, isSkipped: Boolean, message: String) -> Unit = { _, _, _, _, _ -> }
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            AppErrorLogger.logInfo("SegmentedSync", "সেগমেন্টেড ড্রাইভ সিঙ্ক শুরু হচ্ছে... FolderId: $folderId")

            val segments = generateAllSegments(repository)
            val total = segments.size + 1 // +1 for manifest

            val existingFilesMap = fetchFolderFilesMap(accessToken, folderId)
            AppErrorLogger.logInfo("SegmentedSync", "ড্রাইভ ফোল্ডারে বিদ্যমান ফাইল সংখ্যা: ${existingFilesMap.size}")

            var uploadedCount = 0
            var skippedCount = 0
            var errorCount = 0
            val detailsList = mutableListOf<String>()

            val manifestEntries = mutableMapOf<String, SegmentManifestEntry>()

            segments.forEachIndexed { index, seg ->
                val currentStep = index + 1
                val storedHash = getStoredSegmentHash(seg.segmentKey)
                val isFileOnDrive = existingFilesMap.containsKey(seg.fileName)
                val isUnchanged = (storedHash == seg.contentHash) && isFileOnDrive

                if (isUnchanged) {
                    skippedCount++
                    val msg = "${seg.titleBn} (${seg.fileName}) [অপরিবর্তিত - স্কিপ করা হয়েছে]"
                    detailsList.add(msg)
                    onProgress(currentStep, total, seg, true, msg)
                } else {
                    onProgress(currentStep, total, seg, false, "${seg.titleBn} আপলোড করা হচ্ছে...")
                    try {
                        val existingFileId = existingFilesMap[seg.fileName]
                        uploadOrUpdateJsonFile(accessToken, folderId, seg.fileName, seg.jsonContent, existingFileId)
                        saveStoredSegmentHash(seg.segmentKey, seg.contentHash)
                        uploadedCount++
                        val msg = "${seg.titleBn} (${seg.fileName}) [সফলভাবে আপলোড সম্পন্ন]"
                        detailsList.add(msg)
                        AppErrorLogger.logInfo("SegmentedSync", msg)
                        onProgress(currentStep, total, seg.copy(status = SegmentSyncStatus.SYNCED), false, msg)
                    } catch (e: Exception) {
                        errorCount++
                        val errMsg = "${seg.titleBn} (${seg.fileName}) [ত্রুটি: ${e.localizedMessage}]"
                        detailsList.add(errMsg)
                        AppErrorLogger.logError("SegmentedSync", errMsg, e)
                        onProgress(currentStep, total, seg.copy(status = SegmentSyncStatus.ERROR), false, errMsg)
                    }
                }

                manifestEntries[seg.segmentKey] = SegmentManifestEntry(
                    segmentKey = seg.segmentKey,
                    fileName = seg.fileName,
                    titleBn = seg.titleBn,
                    recordCount = seg.recordCount,
                    contentHash = seg.contentHash,
                    lastUpdated = System.currentTimeMillis()
                )
            }

            // Manifest
            val schoolInfo = repository.schoolInfo.firstOrNull()
            val schoolName = schoolInfo?.schoolName ?: "অন্বেষা বিদ্যালয়"
            val totalRecords = segments.sumOf { it.recordCount }
            val timeFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val manifest = BackupManifestModel(
                schoolName = schoolName,
                eiinCode = schoolInfo?.eiinCode ?: "123456",
                backupTimestamp = System.currentTimeMillis(),
                backupDateFormatted = timeFormatted,
                totalRecords = totalRecords,
                segments = manifestEntries
            )

            val manifestExistingId = existingFilesMap["backup_manifest.json"]
            uploadOrUpdateJsonFile(accessToken, folderId, "backup_manifest.json", manifest.toJson(), manifestExistingId)

            val summary = "সিঙ্ক সম্পন্ন: $uploadedCount টি ফাইল হালনাগাদ, $skippedCount টি অপরিবর্তিত।"
            setLastSyncTimestamp(System.currentTimeMillis(), summary)
            AppErrorLogger.logInfo("SegmentedSync", summary)

            Result.success(
                SyncResult(
                    totalSegments = segments.size,
                    uploadedCount = uploadedCount,
                    skippedCount = skippedCount,
                    errorCount = errorCount,
                    manifestUpdated = true,
                    details = detailsList
                )
            )
        } catch (e: Exception) {
            AppErrorLogger.logError("SegmentedSync", "সিঙ্ক প্রক্রিয়ায় ত্রুটি: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // 4. RESTORE SEGMENTS WITH ADVANCED MODES
    // ==========================================

    data class RestoreResult(
        val restoredFilesCount: Int,
        val totalRecordsImported: Int,
        val mode: DriveRestoreMode,
        val summary: String
    )

    suspend fun restoreSegmentsFromDrive(
        accessToken: String,
        folderId: String,
        repository: SchoolRepository,
        mode: DriveRestoreMode = DriveRestoreMode.MERGE,
        onProgress: (current: Int, total: Int, fileName: String, message: String) -> Unit = { _, _, _, _ -> }
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            AppErrorLogger.logInfo("SegmentedRestore", "Google Drive থেকে রিস্টোর শুরু হচ্ছে (মোড: ${mode.name})... FolderId: $folderId")
            val existingFilesMap = mutableMapOf<String, String>()

            // 1. Fetch files in the designated folder if folderId is provided
            if (folderId.isNotBlank()) {
                val folderMap = fetchFolderFilesMap(accessToken, folderId)
                existingFilesMap.putAll(folderMap)
            }

            // 2. Also search candidate folders if needed
            val candidateFolders = fetchSchoolCandidateFolders(accessToken)
            for (candId in candidateFolders) {
                val candMap = fetchFolderFilesMap(accessToken, candId)
                candMap.forEach { (name, id) ->
                    if (!existingFilesMap.containsKey(name)) {
                        existingFilesMap[name] = id
                    }
                }
            }

            // 3. Search Google Drive globally for backup files and segments
            val globalFiles = searchAllBackupFilesGlobally(accessToken)
            globalFiles.forEach { (name, id) ->
                if (!existingFilesMap.containsKey(name)) {
                    existingFilesMap[name] = id
                }
            }

            if (existingFilesMap.isEmpty()) {
                return@withContext Result.failure(Exception("ড্রাইভ ফোল্ডারে কোনো ব্যাকআপ ফাইল পাওয়া যায়নি"))
            }

            // Check if backup_manifest.json exists and read it for exact segment list
            val manifestFileId = existingFilesMap["backup_manifest.json"]
            if (manifestFileId != null) {
                try {
                    val manifestContent = downloadFileContent(accessToken, manifestFileId)
                    if (manifestContent.isNotBlank()) {
                        val mJson = JSONObject(manifestContent)
                        val segmentsObj = mJson.optJSONObject("segments")
                        if (segmentsObj != null) {
                            val keys = segmentsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val seg = segmentsObj.optJSONObject(k)
                                val fn = seg?.optString("fileName")
                                if (!fn.isNullOrBlank() && !existingFilesMap.containsKey(fn)) {
                                    // Search specifically for this missing segment file in Drive
                                    val fId = searchSingleFileByName(accessToken, fn)
                                    if (fId != null) {
                                        existingFilesMap[fn] = fId
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read backup manifest: ${e.message}")
                }
            }

            // Check if .db file exists in the files
            val dbFileEntry = existingFilesMap.entries.firstOrNull { it.key.endsWith(".db") || it.key.contains("anwesha_school_db") }

            // Filter valid JSON segments & full backups
            val jsonFiles = existingFilesMap.filter { (name, _) ->
                name.endsWith(".json") && name != "backup_manifest.json" && name != "school_system_info.json" && name != "school_system_info_secondary.json"
            }

            var importedFiles = 0
            var totalRecords = 0

            if (jsonFiles.isNotEmpty()) {
                // If EXCLUDE_OFFLINE mode, clear tables before importing JSON segments
                if (mode == DriveRestoreMode.EXCLUDE_OFFLINE) {
                    onProgress(0, jsonFiles.size, "clean_reset", "ক্লিন রিস্টোর: বর্তমান অফলাইন রেকর্ড মুছে ফেলা হচ্ছে...")
                    repository.clearAllDatabaseTables()
                }

                val sortedFiles = jsonFiles.toList().sortedBy { (fileName, _) ->
                    when {
                        fileName.contains("school_profile", ignoreCase = true) || fileName.contains("school_info", ignoreCase = true) -> 1
                        fileName.contains("setting", ignoreCase = true) || fileName.contains("preference", ignoreCase = true) -> 2
                        fileName.contains("custom_field", ignoreCase = true) || fileName.contains("formula", ignoreCase = true) -> 3
                        fileName.contains("user", ignoreCase = true) || fileName.contains("teacher", ignoreCase = true) -> 4
                        fileName.contains("student", ignoreCase = true) && !fileName.contains("document", ignoreCase = true) -> 5
                        fileName.contains("attendance", ignoreCase = true) -> 6
                        fileName.contains("routine", ignoreCase = true) -> 7
                        fileName.contains("template", ignoreCase = true) -> 8
                        fileName.contains("document", ignoreCase = true) -> 9
                        fileName.contains("media", ignoreCase = true) || fileName.contains("image", ignoreCase = true) -> 10
                        fileName.contains("pdf", ignoreCase = true) || fileName.contains("config", ignoreCase = true) -> 11
                        else -> 20
                    }
                }

                for ((index, entry) in sortedFiles.withIndex()) {
                    val (fileName, fileId) = entry
                    onProgress(index + 1, sortedFiles.size, fileName, "ডাউনলোড ও ইম্পোর্ট হচ্ছে: $fileName...")
                    val content = downloadFileContent(accessToken, fileId)

                    if (content.isNotBlank()) {
                        val count = importSingleSegmentContent(fileName, content, repository, mode)
                        if (count > 0) {
                            totalRecords += count
                            importedFiles++
                        }
                    }
                }
            }

            // Fallback: If 0 records were imported from JSON but a .db file exists in Drive, restore from .db
            if (totalRecords == 0 && dbFileEntry != null) {
                onProgress(1, 1, dbFileEntry.key, "সরাসরি SQLite .db ফাইল থেকে ডাটাবেস রিস্টোর হচ্ছে...")
                AppErrorLogger.logInfo("SegmentedRestore", "JSON সেগমেন্টের বদলে .db ফাইল (${dbFileEntry.key}) থেকে রিস্টোর করা হচ্ছে...")
                
                val driveSetupManager = GoogleDriveSetupManager(context)
                val directDbResult = driveSetupManager.downloadAndRestoreDirectDatabase(
                    isSecondary = false,
                    targetFileId = dbFileEntry.value,
                    onProgress = { msg -> onProgress(1, 1, dbFileEntry.key, msg) }
                )

                if (directDbResult.isSuccess) {
                    val db = AppDatabase.getDatabase(context)
                    val studentCount = db.studentDao().getStudentCountSync()
                    val school = db.schoolInfoDao().getSchoolInfoSync()
                    totalRecords = studentCount + if (school != null) 1 else 0
                    importedFiles = 1
                }
            }

            if (importedFiles == 0 && totalRecords == 0) {
                return@withContext Result.failure(Exception("ড্রাইভ ফোল্ডারে কোনো রিস্টোরযোগ্য রেকর্ড পাওয়া যায়নি"))
            }

            // Update persistent internal vault snapshot
            try {
                val db = AppDatabase.getDatabase(context)
                InternalAutoBackupManager.getInstance(context).saveInternalSnapshot(db)
            } catch (e: Exception) {
                Log.w(TAG, "Vault update warning: ${e.message}")
            }

            val summary = "সফলভাবে $importedFiles টি সেগমেন্ট এবং $totalRecords টি রেকর্ড (${mode.titleBn}) রিস্টোর সম্পন্ন হয়েছে।"
            AppErrorLogger.logInfo("SegmentedRestore", summary)
            Result.success(RestoreResult(importedFiles, totalRecords, mode, summary))
        } catch (e: Exception) {
            AppErrorLogger.logError("SegmentedRestore", "রিস্টোর ব্যর্থ: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun importSingleSegmentContent(
        fileName: String,
        jsonContent: String,
        repository: SchoolRepository,
        mode: DriveRestoreMode
    ): Int {
        return try {
            val trimmed = jsonContent.trim()
            if (trimmed.isEmpty()) return 0
            val lowerName = File(fileName).name.lowercase(Locale.ROOT)
            
            // 1. Check if this is a master / full backup JSON
            if (trimmed.contains("\"studentsList\"") || (trimmed.contains("\"students\"") && (trimmed.contains("\"schoolInfo\"") || trimmed.contains("\"school_info\"")))) {
                val count = repository.importDataFromJson(trimmed)
                if (count > 0) {
                    return count
                }
            }

            when {
                // Priority 1: Users / Teachers (must be checked BEFORE generic school / user)
                lowerName.contains("school_users") || lowerName.contains("teachers") || (lowerName.contains("user") && !lowerName.contains("custom") && !lowerName.contains("profile") && !lowerName.contains("info")) -> {
                    val array = extractJsonArrayFromContent(trimmed, "users", "usersList", "school_users", "teachers", "data")
                    val users = mutableListOf<UserEntity>()
                    for (i in 0 until array.length()) {
                        val u = array.getJSONObject(i)
                        users.add(
                            UserEntity(
                                userId = u.optString("userId", u.optString("id", "USR-$i")),
                                name = u.optString("name", "User $i"),
                                email = u.optString("email", ""),
                                phone = u.optString("phone", ""),
                                role = u.optString("role", "Teacher"),
                                status = u.optString("status", "Active"),
                                securityPinHash = u.optString("securityPinHash", ""),
                                createdDate = u.optString("createdDate", ""),
                                createdAt = u.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = u.optLong("updatedAt", System.currentTimeMillis()),
                                version = u.optInt("version", 1)
                            )
                        )
                    }
                    if (users.isNotEmpty()) repository.insertAllUsers(users)
                    users.size
                }

                // Priority 2: Student Documents (must be checked BEFORE generic student / class)
                lowerName.contains("student_document") || (lowerName.contains("document") && !lowerName.contains("template") && !lowerName.contains("pdf")) -> {
                    val array = extractJsonArrayFromContent(trimmed, "studentDocuments", "documents", "documentsList", "data")
                    val incomingDocs = mutableListOf<StudentDocumentEntity>()
                    for (i in 0 until array.length()) {
                        val d = array.getJSONObject(i)
                        incomingDocs.add(
                            StudentDocumentEntity(
                                id = d.optString("id", UUID.randomUUID().toString()),
                                studentId = d.optString("studentId", ""),
                                title = d.optString("title", "ডকুমেন্ট"),
                                documentType = d.optString("documentType", "জন্ম নিবন্ধন সনদ"),
                                fileUri = d.optString("fileUri", ""),
                                fileType = d.optString("fileType", "image/jpeg"),
                                extractedText = d.optString("extractedText", ""),
                                pageCount = d.optInt("pageCount", 1),
                                notes = d.optString("notes", ""),
                                scanDate = d.optString("scanDate", ""),
                                createdAt = d.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = d.optLong("updatedAt", System.currentTimeMillis()),
                                version = d.optInt("version", 1),
                                isDeleted = d.optBoolean("isDeleted", false),
                                syncStatus = "SYNCED"
                            )
                        )
                    }
                    if (incomingDocs.isNotEmpty()) {
                        repository.insertAllStudentDocuments(incomingDocs)
                    }
                    incomingDocs.size
                }

                // Priority 3: Document Templates
                lowerName.contains("document_template") || lowerName.contains("template") -> {
                    val array = extractJsonArrayFromContent(trimmed, "documentTemplates", "templates", "data")
                    for (i in 0 until array.length()) {
                        val t = array.getJSONObject(i)
                        repository.insertDocumentTemplate(
                            DocumentTemplateEntity(
                                id = t.optString("id", UUID.randomUUID().toString()),
                                title = t.optString("title", ""),
                                contentTemplate = t.optString("contentTemplate", ""),
                                createdDate = t.optString("createdDate", "")
                            )
                        )
                    }
                    array.length()
                }

                // Priority 4: School Profile / School Info
                lowerName.contains("school_profile") || lowerName.contains("school_info") || lowerName == "school.json" -> {
                    val obj = if (trimmed.startsWith("{")) {
                        val root = JSONObject(trimmed)
                        if (root.has("schoolInfo")) root.getJSONObject("schoolInfo")
                        else if (root.has("school_info")) root.getJSONObject("school_info")
                        else root
                    } else if (trimmed.startsWith("[")) {
                        val arr = JSONArray(trimmed)
                        if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
                    } else {
                        JSONObject()
                    }

                    if (obj.length() > 0) {
                        val info = SchoolInfoEntity(
                            id = 1,
                            schoolName = obj.optString("schoolName", obj.optString("name", "অন্বেষা বিদ্যালয়")),
                            eiinCode = obj.optString("eiinCode", obj.optString("emisCode", obj.optString("eiin", "123456"))),
                            address = obj.optString("address", ""),
                            tagline = obj.optString("tagline", "জ্ঞান, মনন ও স্বপ্নের সোপান"),
                            phone = obj.optString("phone", obj.optString("adminPhone", "")),
                            email = obj.optString("email", obj.optString("adminEmail", "")),
                            headTeacherName = obj.optString("headTeacherName", obj.optString("headTeacher", "")),
                            adminName = obj.optString("adminName", ""),
                            adminEmail = obj.optString("adminEmail", ""),
                            adminPhone = obj.optString("adminPhone", ""),
                            logoUri = if (obj.optString("logoUri").isNotBlank()) obj.optString("logoUri") else null,
                            internalVillages = obj.optString("internalVillages", "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর"),
                            customSchoolInfoJson = obj.optString("customSchoolInfoJson", "[]"),
                            createdDate = obj.optString("createdDate", ""),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            version = obj.optInt("version", 1)
                        )
                        repository.saveSchoolInfo(info)
                        1
                    } else 0
                }

                // Priority 5: Settings & Preferences
                lowerName.contains("setting") || lowerName.contains("preference") -> {
                    restoreSettingsFromJson(trimmed)
                    1
                }

                // Priority 6: Custom Fields & Formulas
                lowerName.contains("custom_field") || lowerName.contains("formula") -> {
                    var count = 0
                    if (trimmed.startsWith("{")) {
                        val obj = JSONObject(trimmed)
                        val fieldsArray = obj.optJSONArray("customFields") ?: obj.optJSONArray("fields")
                        if (fieldsArray != null) {
                            for (i in 0 until fieldsArray.length()) {
                                val cf = fieldsArray.getJSONObject(i)
                                repository.insertCustomField(
                                    CustomFieldEntity(
                                        id = cf.optString("id", UUID.randomUUID().toString()),
                                        name = cf.optString("name", ""),
                                        fieldType = cf.optString("fieldType", "Text"),
                                        optionsJson = cf.optString("optionsJson", null),
                                        isCalculated = cf.optBoolean("isCalculated", false),
                                        formulaRuleId = cf.optString("formulaRuleId", null),
                                        groupName = cf.optString("groupName", "কাস্টম তথ্য"),
                                        orderIndex = cf.optInt("orderIndex", 0)
                                    )
                                )
                                count++
                            }
                        }
                        val rulesArray = obj.optJSONArray("formulaRules") ?: obj.optJSONArray("rules")
                        if (rulesArray != null) {
                            for (i in 0 until rulesArray.length()) {
                                val fr = rulesArray.getJSONObject(i)
                                repository.insertFormulaRule(
                                    FormulaRuleEntity(
                                        id = fr.optString("id", UUID.randomUUID().toString()),
                                        ruleName = fr.optString("ruleName", ""),
                                        targetFieldName = fr.optString("targetFieldName", ""),
                                        sourceField = fr.optString("sourceField", ""),
                                        operator = fr.optString("operator", "EQUALS"),
                                        conditionValue = fr.optString("conditionValue", ""),
                                        resultIfTrue = fr.optString("resultIfTrue", ""),
                                        resultIfFalse = fr.optString("resultIfFalse", "")
                                    )
                                )
                                count++
                            }
                        }
                    } else if (trimmed.startsWith("[")) {
                        val array = JSONArray(trimmed)
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            if (item.has("fieldType") || item.has("optionsJson")) {
                                repository.insertCustomField(
                                    CustomFieldEntity(
                                        id = item.optString("id", UUID.randomUUID().toString()),
                                        name = item.optString("name", ""),
                                        fieldType = item.optString("fieldType", "Text"),
                                        optionsJson = item.optString("optionsJson", null),
                                        isCalculated = item.optBoolean("isCalculated", false),
                                        formulaRuleId = item.optString("formulaRuleId", null),
                                        groupName = item.optString("groupName", "কাস্টম তথ্য"),
                                        orderIndex = item.optInt("orderIndex", 0)
                                    )
                                )
                                count++
                            } else if (item.has("ruleName") || item.has("targetFieldName")) {
                                repository.insertFormulaRule(
                                    FormulaRuleEntity(
                                        id = item.optString("id", UUID.randomUUID().toString()),
                                        ruleName = item.optString("ruleName", ""),
                                        targetFieldName = item.optString("targetFieldName", ""),
                                        sourceField = item.optString("sourceField", ""),
                                        operator = item.optString("operator", "EQUALS"),
                                        conditionValue = item.optString("conditionValue", ""),
                                        resultIfTrue = item.optString("resultIfTrue", ""),
                                        resultIfFalse = item.optString("resultIfFalse", "")
                                    )
                                )
                                count++
                            }
                        }
                    }
                    count
                }

                // Priority 7: Attendance
                lowerName.contains("attendance") -> {
                    val array = extractJsonArrayFromContent(trimmed, "attendance", "attendanceList", "attendanceRecords", "data")
                    val list = mutableListOf<AttendanceEntity>()
                    for (i in 0 until array.length()) {
                        val a = array.getJSONObject(i)
                        list.add(
                            AttendanceEntity(
                                id = a.optString("id", UUID.randomUUID().toString()),
                                date = a.optString("date", ""),
                                className = a.optString("className", a.optString("class", "১ম শ্রেণি")),
                                presentBoys = a.optInt("presentBoys", 0),
                                presentGirls = a.optInt("presentGirls", 0),
                                absentBoys = a.optInt("absentBoys", 0),
                                absentGirls = a.optInt("absentGirls", 0),
                                totalBoys = a.optInt("totalBoys", 0),
                                totalGirls = a.optInt("totalGirls", 0),
                                notes = if (a.has("notes") && !a.isNull("notes")) a.optString("notes") else null,
                                createdAt = a.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = a.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (list.isNotEmpty()) repository.insertAllAttendance(list)
                    list.size
                }

                // Priority 8: Routines
                lowerName.contains("routine") -> {
                    val array = extractJsonArrayFromContent(trimmed, "routineItems", "routines", "routineList", "data")
                    for (i in 0 until array.length()) {
                        val rt = array.getJSONObject(i)
                        repository.insertRoutineItem(
                            RoutineItemEntity(
                                id = rt.optString("id", UUID.randomUUID().toString()),
                                routineType = rt.optString("routineType", "Class Routine"),
                                className = rt.optString("className", ""),
                                subject = rt.optString("subject", ""),
                                teacher = rt.optString("teacher", ""),
                                day = rt.optString("day", "রবিবার"),
                                startTime = rt.optString("startTime", "09:00 AM"),
                                endTime = rt.optString("endTime", "09:45 AM"),
                                periodName = rt.optString("periodName", "১ম পিরিয়ড"),
                                roomNo = if (rt.has("roomNo") && !rt.isNull("roomNo")) rt.optString("roomNo") else null
                            )
                        )
                    }
                    array.length()
                }

                // Priority 9: Media & Images
                lowerName.contains("media") || lowerName.contains("image") -> {
                    val mediaObj = JSONObject(trimmed)
                    var restoredCount = 0
                    val photosArray = mediaObj.optJSONArray("studentPhotos")
                    if (photosArray != null) {
                        for (i in 0 until photosArray.length()) {
                            val item = photosArray.getJSONObject(i)
                            val studentId = item.optString("studentId")
                            val photoUri = item.optString("photoUri")
                            if (studentId.isNotBlank() && photoUri.isNotBlank()) {
                                val currentStudent = repository.getStudentById(studentId)
                                if (currentStudent != null) {
                                    repository.updateStudent(currentStudent.copy(photoUri = photoUri))
                                    restoredCount++
                                }
                            }
                        }
                    }
                    val schoolLogoUri = mediaObj.optString("schoolLogoUri")
                    if (schoolLogoUri.isNotBlank()) {
                        val currentInfo = repository.schoolInfo.firstOrNull()
                        if (currentInfo != null) {
                            repository.saveSchoolInfo(currentInfo.copy(logoUri = schoolLogoUri))
                            restoredCount++
                        }
                    }
                    restoredCount
                }

                // Priority 10: PDF & Documents Preferences
                lowerName.contains("pdf") || lowerName.contains("config") -> {
                    val pdfDocsObj = JSONObject(trimmed)
                    val admitConfig = pdfDocsObj.optJSONObject("admitCardPreferences")
                    if (admitConfig != null) {
                        val edit = context.getSharedPreferences("admit_card_prefs", Context.MODE_PRIVATE).edit()
                        val keys = admitConfig.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            edit.putString(k, admitConfig.optString(k))
                        }
                        edit.apply()
                    }
                    val seatConfig = pdfDocsObj.optJSONObject("seatPlanPreferences")
                    if (seatConfig != null) {
                        val edit = context.getSharedPreferences("seat_plan_prefs", Context.MODE_PRIVATE).edit()
                        val keys = seatConfig.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            edit.putString(k, seatConfig.optString(k))
                        }
                        edit.apply()
                    }
                    val certConfig = pdfDocsObj.optJSONObject("certificatePreferences")
                    if (certConfig != null) {
                        val edit = context.getSharedPreferences("certificate_prefs", Context.MODE_PRIVATE).edit()
                        val keys = certConfig.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            edit.putString(k, certConfig.optString(k))
                        }
                        edit.apply()
                    }
                    1
                }

                // Priority 11: Students (handles class segments, students_all, students.json, or any student list)
                lowerName.contains("student") || lowerName.contains("class") || lowerName.endsWith(".json") -> {
                    val array = extractJsonArrayFromContent(trimmed, "students", "studentsList", "studentList", "records", "data")
                    val incomingStudents = mutableListOf<StudentEntity>()
                    for (i in 0 until array.length()) {
                        val s = array.getJSONObject(i)
                        incomingStudents.add(
                            StudentEntity(
                                id = s.optString("id", s.optString("studentId", "STU-$i")),
                                studentClass = s.optString("studentClass", s.optString("className", s.optString("class", "১ম শ্রেণি"))),
                                section = s.optString("section", s.optString("sec", "ক")),
                                rollNumber = s.optInt("rollNumber", s.optInt("roll", s.optInt("roll_no", i + 1))),
                                name = s.optString("name", s.optString("studentName", "")),
                                parentContact = s.optString("parentContact", s.optString("mobile", s.optString("phone", ""))),
                                mobile = s.optString("mobile", s.optString("parentContact", s.optString("phone", ""))),
                                fatherName = s.optString("fatherName", s.optString("father_name", "")),
                                motherName = s.optString("motherName", s.optString("mother_name", "")),
                                gender = s.optString("gender", "ছাত্র"),
                                village = s.optString("village", ""),
                                birthDate = s.optString("birthDate", s.optString("dob", "")),
                                birthRegNumber = s.optString("birthRegNumber", s.optString("birth_reg_number", s.optString("birthRegNo", ""))),
                                address = s.optString("address", ""),
                                academicYear = s.optString("academicYear", "২০২৬"),
                                isSpecialNeeds = s.optBoolean("isSpecialNeeds", false),
                                status = s.optString("status", "Current"),
                                photoUri = if (s.optString("photoUri").isNotBlank()) s.optString("photoUri") else null,
                                customValuesJson = s.optString("customValuesJson", "{}"),
                                createdAt = s.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = s.optLong("updatedAt", System.currentTimeMillis()),
                                version = s.optInt("version", 1)
                            )
                        )
                    }

                    if (incomingStudents.isNotEmpty()) {
                        when (mode) {
                            DriveRestoreMode.EXCLUDE_OFFLINE, DriveRestoreMode.MERGE -> {
                                repository.insertAllStudents(incomingStudents)
                            }
                            DriveRestoreMode.INCLUDE_OFFLINE -> {
                                val localStudents = repository.allStudents.firstOrNull() ?: emptyList()
                                val localMap = localStudents.associateBy { it.id }
                                val mergedList = incomingStudents.map { incoming ->
                                    val local = localMap[incoming.id]
                                    if (local != null && local.updatedAt > incoming.updatedAt) local else incoming
                                }
                                if (mergedList.isNotEmpty()) repository.insertAllStudents(mergedList)
                            }
                        }
                    }
                    incomingStudents.size
                }
                else -> 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing segment $fileName: ${e.message}")
            0
        }
    }

    private fun extractJsonArrayFromContent(trimmed: String, vararg possibleKeys: String): JSONArray {
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            for (key in possibleKeys) {
                val arr = root.optJSONArray(key)
                if (arr != null) return arr
            }
            // If no matched array key found, check any array in root
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val candidate = root.optJSONArray(k)
                if (candidate != null) return candidate
            }
        }
        return JSONArray()
    }

    private fun fetchSchoolCandidateFolders(accessToken: String): List<String> {
        val folderIds = mutableListOf<String>()
        try {
            val query = Uri.encode(
                "mimeType = 'application/vnd.google-apps.folder' and trashed = false and (" +
                "name contains 'School' or " +
                "name contains 'Data_Storage' or " +
                "name contains 'Anwesha' or " +
                "name contains 'Backup' or " +
                "name contains 'বিদ্যালয়' or " +
                "name contains 'স্কুল' or " +
                "name contains 'প্রাথমিক'" +
                ")"
            )
            val fields = Uri.encode("files(id, name)")
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=50"
            val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val filesArr = json.optJSONArray("files")
                if (filesArr != null) {
                    for (i in 0 until filesArr.length()) {
                        folderIds.add(filesArr.getJSONObject(i).getString("id"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch candidate folders: ${e.message}")
        }
        return folderIds
    }

    private fun searchAllBackupFilesGlobally(accessToken: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val query = Uri.encode(
                "trashed = false and (" +
                "name contains '.json' or " +
                "name contains '.db' or " +
                "name contains '.zip'" +
                ") and (" +
                "name contains 'school' or " +
                "name contains 'student' or " +
                "name contains 'routine' or " +
                "name contains 'attendance' or " +
                "name contains 'user' or " +
                "name contains 'teacher' or " +
                "name contains 'custom' or " +
                "name contains 'manifest' or " +
                "name contains 'setting' or " +
                "name contains 'template' or " +
                "name contains 'media' or " +
                "name contains 'anwesha' or " +
                "name contains 'backup' or " +
                "name contains 'doc' or " +
                "name contains 'class'" +
                ")"
            )
            val fields = Uri.encode("files(id, name, parents)")
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=100"
            val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val filesArr = json.optJSONArray("files")
                if (filesArr != null) {
                    for (i in 0 until filesArr.length()) {
                        val fileObj = filesArr.getJSONObject(i)
                        val id = fileObj.getString("id")
                        val name = fileObj.getString("name")
                        map[name] = id
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search backup files globally: ${e.message}")
        }
        return map
    }

    private fun searchSingleFileByName(accessToken: String, targetFileName: String): String? {
        try {
            val query = Uri.encode("name = '$targetFileName' and trashed = false")
            val fields = Uri.encode("files(id, name)")
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=1"
            val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val filesArr = json.optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    return filesArr.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search file $targetFileName: ${e.message}")
        }
        return null
    }

    // ==========================================
    // 5. ZIP EXPORT / IMPORT LOCAL ENGINE
    // ==========================================

    suspend fun exportAllSegmentsAsZipFile(repository: SchoolRepository): File = withContext(Dispatchers.IO) {
        val segments = generateAllSegments(repository)
        val backupDir = File(context.cacheDir, "segmented_backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val zipFile = File(backupDir, "school_backup_segmented_$timestamp.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            segments.forEach { seg ->
                val entry = ZipEntry(seg.fileName)
                zos.putNextEntry(entry)
                zos.write(seg.jsonContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val manifest = BackupManifestModel(
                schoolName = repository.schoolInfo.firstOrNull()?.schoolName ?: "অন্বেষা বিদ্যালয়",
                eiinCode = repository.schoolInfo.firstOrNull()?.eiinCode ?: "123456",
                backupDateFormatted = timestamp,
                totalRecords = segments.sumOf { it.recordCount },
                segments = segments.associate { it.segmentKey to SegmentManifestEntry(it.segmentKey, it.fileName, it.titleBn, it.recordCount, it.contentHash, System.currentTimeMillis()) }
            )
            val manifestEntry = ZipEntry("backup_manifest.json")
            zos.putNextEntry(manifestEntry)
            zos.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        zipFile
    }

    suspend fun createZipShareIntent(repository: SchoolRepository): Intent = withContext(Dispatchers.IO) {
        val zipFile = exportAllSegmentsAsZipFile(repository)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "অন্বেষা বিদ্যালয় - সেগমেন্টেড ব্যাকআপ জিপ (${zipFile.name})")
            putExtra(Intent.EXTRA_TEXT, "বিদ্যালয়ের সেটিংস ও সকল ডাটাবেস সেগমেন্ট ফাইলসহ সম্পূর্ণ ব্যাকআপ আর্কাইভ।")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Intent.createChooser(shareIntent, "সেগমেন্টেড ব্যাকআপ জিপ ফাইল শেয়ার করুন").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun restoreFromZipUri(
        uri: Uri,
        repository: SchoolRepository,
        mode: DriveRestoreMode = DriveRestoreMode.MERGE
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (mode == DriveRestoreMode.EXCLUDE_OFFLINE) {
                repository.clearAllDatabaseTables()
            }

            var restoredCount = 0
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("ফাইল খোলা যায়নি"))

            val entriesMap = mutableMapOf<String, String>()
            var dbTempFile: File? = null

            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val simpleName = File(entry.name).name
                    if (!entry.isDirectory) {
                        if (simpleName.endsWith(".db")) {
                            val tempDir = File(context.cacheDir, "zip_db_restore")
                            if (!tempDir.exists()) tempDir.mkdirs()
                            val temp = File(tempDir, "extracted_db.db")
                            FileOutputStream(temp).use { out -> zis.copyTo(out) }
                            dbTempFile = temp
                        } else if (simpleName.endsWith(".json") && simpleName != "backup_manifest.json") {
                            val content = zis.bufferedReader(Charsets.UTF_8).readText()
                            entriesMap[simpleName] = content
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // If .db file was found in ZIP and no JSON segments were present (or along with db)
            if (dbTempFile != null && dbTempFile!!.exists()) {
                val driveSetupManager = GoogleDriveSetupManager(context)
                val replaceResult = driveSetupManager.replaceLocalDatabaseWithFile(dbTempFile!!)
                dbTempFile!!.delete()
                if (replaceResult.isSuccess) {
                    val db = AppDatabase.getDatabase(context)
                    val studentCount = db.studentDao().getStudentCountSync()
                    val school = db.schoolInfoDao().getSchoolInfoSync()
                    return@withContext Result.success(studentCount + if (school != null) 1 else 0)
                }
            }

            if (entriesMap.isNotEmpty()) {
                val sortedEntries = entriesMap.toList().sortedBy { (fileName, _) ->
                    val lower = fileName.lowercase(Locale.ROOT)
                    when {
                        lower.contains("school_profile") || lower.contains("school_info") -> 1
                        lower.contains("setting") || lower.contains("preference") -> 2
                        lower.contains("custom_field") || lower.contains("formula") -> 3
                        lower.contains("user") || lower.contains("teacher") -> 4
                        lower.contains("student") && !lower.contains("document") -> 5
                        lower.contains("attendance") -> 6
                        lower.contains("routine") -> 7
                        lower.contains("template") -> 8
                        lower.contains("document") -> 9
                        lower.contains("media") || lower.contains("image") -> 10
                        lower.contains("pdf") || lower.contains("config") -> 11
                        else -> 20
                    }
                }

                for ((fileName, content) in sortedEntries) {
                    restoredCount += importSingleSegmentContent(fileName, content, repository, mode)
                }
            }

            // Update persistent internal vault snapshot
            try {
                val db = AppDatabase.getDatabase(context)
                InternalAutoBackupManager.getInstance(context).saveInternalSnapshot(db)
            } catch (e: Exception) {
                Log.w(TAG, "Vault update warning: ${e.message}")
            }

            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 6. LOW-LEVEL DRIVE API HTTP CALLS
    // ==========================================

    private fun fetchFolderFilesMap(accessToken: String, folderId: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val query = Uri.encode("'$folderId' in parents and trashed = false")
        val fields = Uri.encode("files(id, name)")
        val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=100"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (response.isSuccessful) {
            val json = JSONObject(body)
            val filesArr = json.optJSONArray("files")
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    val fileObj = filesArr.getJSONObject(i)
                    val id = fileObj.getString("id")
                    val name = fileObj.getString("name")
                    map[name] = id
                }
            }
        }
        return map
    }

    private fun uploadOrUpdateJsonFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        jsonContent: String,
        existingFileId: String?
    ) {
        val requestBody = jsonContent.toRequestBody(MEDIA_TYPE_JSON)

        if (existingFileId != null) {
            val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
            val updateReq = Request.Builder()
                .url(updateUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()

            val resp = httpClient.newCall(updateReq).execute()
            if (!resp.isSuccessful) {
                throw Exception("ফাইল আপডেট ব্যর্থ ($fileName): HTTP ${resp.code}")
            }
        } else {
            val metaJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().apply { put(folderId) })
                put("mimeType", "application/json")
            }

            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val createReq = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metaJson.toString().toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val createResp = httpClient.newCall(createReq).execute()
            val createBody = createResp.body?.string() ?: ""

            if (!createResp.isSuccessful) {
                throw Exception("ফাইল তৈরি ব্যর্থ ($fileName): HTTP ${createResp.code} - $createBody")
            }

            val createdId = JSONObject(createBody).getString("id")
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdId?uploadType=media"
            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()

            val uploadResp = httpClient.newCall(uploadReq).execute()
            if (!uploadResp.isSuccessful) {
                throw Exception("ফাইল কনটেন্ট আপলোড ব্যর্থ ($fileName): HTTP ${uploadResp.code}")
            }
        }
    }

    private fun downloadFileContent(accessToken: String, fileId: String): String {
        val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(downloadUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        return response.body?.string() ?: ""
    }
}
