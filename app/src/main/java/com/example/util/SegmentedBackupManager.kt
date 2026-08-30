package com.example.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
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
                titleBn = "শিক্ষার্থীদের সংযুক্ত নথিপত্র (OCR Scans)",
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
            val existingFilesMap = fetchFolderFilesMap(accessToken, folderId)

            if (existingFilesMap.isEmpty()) {
                return@withContext Result.failure(Exception("ড্রাইভ ফোল্ডারে কোনো ব্যাকআপ ফাইল পাওয়া যায়নি"))
            }

            // In EXCLUDE_OFFLINE mode, perform clean database reset first
            if (mode == DriveRestoreMode.EXCLUDE_OFFLINE) {
                onProgress(0, existingFilesMap.size, "clean_reset", "ক্লিন রিস্টোর: বর্তমান অফলাইন রেকর্ড মুছে ফেলা হচ্ছে...")
                repository.clearAllDatabaseTables()
            }

            var importedFiles = 0
            var totalRecords = 0
            val totalFiles = existingFilesMap.size

            val sortedFiles = existingFilesMap.toList().sortedBy { (fileName, _) ->
                when {
                    fileName.startsWith("school_profile") -> 1
                    fileName.startsWith("settings_and_preferences") -> 2
                    fileName.startsWith("school_users") -> 3
                    fileName.startsWith("custom_fields") -> 4
                    fileName.startsWith("students_") -> 5
                    fileName.startsWith("attendance_") -> 6
                    fileName.startsWith("routine_items") -> 7
                    fileName.startsWith("document_templates") -> 8
                    fileName.startsWith("custom_fields") -> 9
                    else -> 20
                }
            }

            for ((index, entry) in sortedFiles.withIndex()) {
                val (fileName, fileId) = entry
                if (fileName == "backup_manifest.json" || fileName == "school_system_info.json" || fileName == "school_system_info_secondary.json" || fileName.endsWith(".db")) continue

                onProgress(index + 1, totalFiles, fileName, "ডাউনলোড ও ইম্পোর্ট হচ্ছে: $fileName...")
                val content = downloadFileContent(accessToken, fileId)

                if (content.isNotBlank()) {
                    val count = importSingleSegmentContent(fileName, content, repository, mode)
                    totalRecords += count
                    importedFiles++
                }
            }

            val summary = "সফলভাবে $importedFiles টি সেগমেন্ট এবং $totalRecords টি রেকর্ড (${mode.titleBn}) রিস্টোর সম্পন্ন হয়েছে।"
            AppErrorLogger.logInfo("SegmentedRestore", summary)
            Result.success(RestoreResult(importedFiles, totalRecords, mode, summary))
        } catch (e: Exception) {
            AppErrorLogger.logError("SegmentedRestore", "রিস্টোর ব্যর্থ: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private suspend fun importSingleSegmentContent(
        fileName: String,
        jsonContent: String,
        repository: SchoolRepository,
        mode: DriveRestoreMode
    ): Int {
        return try {
            when {
                fileName == "school_profile.json" -> {
                    val obj = JSONObject(jsonContent)
                    val info = SchoolInfoEntity(
                        id = 1,
                        schoolName = obj.optString("schoolName", "অন্বেষা বিদ্যালয়"),
                        eiinCode = obj.optString("eiinCode", "123456"),
                        address = obj.optString("address", ""),
                        tagline = obj.optString("tagline", "জ্ঞান, মনন ও স্বপ্নের সোপান"),
                        phone = obj.optString("phone", ""),
                        email = obj.optString("email", ""),
                        headTeacherName = obj.optString("headTeacherName", ""),
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
                }
                fileName == "settings_and_preferences.json" -> {
                    restoreSettingsFromJson(jsonContent)
                    1
                }
                fileName == "school_users.json" -> {
                    val array = JSONArray(jsonContent)
                    val users = mutableListOf<UserEntity>()
                    for (i in 0 until array.length()) {
                        val u = array.getJSONObject(i)
                        users.add(
                            UserEntity(
                                userId = u.optString("userId", "USR-$i"),
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
                fileName.startsWith("students_") -> {
                    val array = JSONArray(jsonContent)
                    val incomingStudents = mutableListOf<StudentEntity>()
                    for (i in 0 until array.length()) {
                        val s = array.getJSONObject(i)
                        incomingStudents.add(
                            StudentEntity(
                                id = s.optString("id", s.optString("studentId", "STU-$i")),
                                studentClass = s.optString("studentClass", "১ম শ্রেণি"),
                                section = s.optString("section", "ক"),
                                rollNumber = s.optInt("rollNumber", i + 1),
                                name = s.optString("name", ""),
                                parentContact = s.optString("parentContact", s.optString("mobile", "")),
                                mobile = s.optString("mobile", s.optString("parentContact", "")),
                                fatherName = s.optString("fatherName", ""),
                                motherName = s.optString("motherName", ""),
                                gender = s.optString("gender", "ছাত্র"),
                                village = s.optString("village", ""),
                                birthDate = s.optString("birthDate", ""),
                                birthRegNumber = s.optString("birthRegNumber", ""),
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

                    when (mode) {
                        DriveRestoreMode.EXCLUDE_OFFLINE -> {
                            if (incomingStudents.isNotEmpty()) repository.insertAllStudents(incomingStudents)
                        }
                        DriveRestoreMode.MERGE -> {
                            if (incomingStudents.isNotEmpty()) repository.insertAllStudents(incomingStudents)
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
                    incomingStudents.size
                }
                fileName == "attendance_records.json" -> {
                    val array = JSONArray(jsonContent)
                    val list = mutableListOf<AttendanceEntity>()
                    for (i in 0 until array.length()) {
                        val a = array.getJSONObject(i)
                        list.add(
                            AttendanceEntity(
                                id = a.optString("id", UUID.randomUUID().toString()),
                                date = a.optString("date", ""),
                                className = a.optString("className", "১ম শ্রেণি"),
                                presentBoys = a.optInt("presentBoys", 0),
                                presentGirls = a.optInt("presentGirls", 0),
                                absentBoys = a.optInt("absentBoys", 0),
                                absentGirls = a.optInt("absentGirls", 0),
                                totalBoys = a.optInt("totalBoys", 0),
                                totalGirls = a.optInt("totalGirls", 0),
                                notes = a.optString("notes", null),
                                createdAt = a.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = a.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (list.isNotEmpty()) repository.insertAllAttendance(list)
                    list.size
                }
                fileName == "routine_items.json" -> {
                    val array = JSONArray(jsonContent)
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
                fileName == "document_templates.json" -> {
                    val array = JSONArray(jsonContent)
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
                fileName == "student_documents.json" -> {
                    val array = JSONArray(jsonContent)
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
                fileName == "custom_fields_and_formulas.json" -> {
                    val obj = JSONObject(jsonContent)
                    val fieldsArray = obj.optJSONArray("customFields")
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
                        }
                    }
                    val rulesArray = obj.optJSONArray("formulaRules")
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
                        }
                    }
                    (fieldsArray?.length() ?: 0) + (rulesArray?.length() ?: 0)
                }
                fileName == "media_and_images.json" -> {
                    val mediaObj = JSONObject(jsonContent)
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
                fileName == "pdf_and_documents_config.json" -> {
                    val pdfDocsObj = JSONObject(jsonContent)
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
                else -> 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing segment $fileName: ${e.message}")
            0
        }
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

            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val fileName = entry.name
                    if (!entry.isDirectory && fileName.endsWith(".json") && fileName != "backup_manifest.json") {
                        val content = zis.bufferedReader(Charsets.UTF_8).readText()
                        restoredCount += importSingleSegmentContent(fileName, content, repository, mode)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
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
