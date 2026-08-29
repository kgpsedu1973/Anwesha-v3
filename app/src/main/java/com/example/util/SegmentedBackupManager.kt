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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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

        // 2. Users / Staff
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

        // 3. Students by Class (Granular partition)
        val students = repository.allStudents.firstOrNull() ?: emptyList()
        val studentsByClass = students.groupBy { it.studentClass.trim() }

        // All students consolidated file
        val allStudentsArray = JSONArray()
        students.forEach { s -> allStudentsArray.put(studentToJson(s)) }
        segments.add(
            BackupSegmentItem(
                segmentKey = "students_all",
                fileName = "students_all.json",
                titleBn = "সকল শিক্ষার্থী (একত্রে)",
                recordCount = students.size,
                jsonContent = allStudentsArray.toString(2)
            )
        )

        // Individual class-wise files
        studentsByClass.forEach { (className, classStudents) ->
            val safeClassSlug = sanitizeClassName(className)
            val classArray = JSONArray()
            classStudents.forEach { s -> classArray.put(studentToJson(s)) }

            val fileKey = "students_class_$safeClassSlug"
            val fileName = "students_class_$safeClassSlug.json"
            val classTitle = if (className.isNotBlank()) "$className (শিক্ষার্থী)" else "অনির্দিষ্ট শ্রেণি"

            segments.add(
                BackupSegmentItem(
                    segmentKey = fileKey,
                    fileName = fileName,
                    titleBn = classTitle,
                    recordCount = classStudents.size,
                    jsonContent = classArray.toString(2)
                )
            )
        }

        // 4. Attendance Records
        val attendance = repository.allAttendance.firstOrNull() ?: emptyList()
        val attArray = JSONArray()
        attendance.forEach { a ->
            attArray.put(JSONObject().apply {
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
                titleBn = "দৈনিক হাজিরা রেকর্ড",
                recordCount = attendance.size,
                jsonContent = attArray.toString(2)
            )
        )

        // 5. Exam Results
        val examResults = repository.allExamResults.firstOrNull() ?: emptyList()
        val resultsArray = JSONArray()
        examResults.forEach { r ->
            resultsArray.put(JSONObject().apply {
                put("id", r.id)
                put("examName", r.examName)
                put("className", r.className)
                put("section", r.section)
                put("rollNumber", r.rollNumber)
                put("studentId", r.studentId ?: "")
                put("studentName", r.studentName)
                put("subject", r.subject)
                put("marks", r.marks)
                put("grade", r.grade)
                put("gpa", r.gpa)
                put("date", r.date)
                put("createdAt", r.createdAt)
                put("updatedAt", r.updatedAt)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "exam_results",
                fileName = "exam_results.json",
                titleBn = "পরীক্ষার ফলাফল ও নম্বরপত্র",
                recordCount = examResults.size,
                jsonContent = resultsArray.toString(2)
            )
        )

        // 6. Fees Records
        val fees = repository.allFees.firstOrNull() ?: emptyList()
        val feesArray = JSONArray()
        fees.forEach { f ->
            feesArray.put(JSONObject().apply {
                put("id", f.id)
                put("studentId", f.studentId)
                put("studentName", f.studentName)
                put("studentClass", f.studentClass)
                put("rollNumber", f.rollNumber)
                put("month", f.month)
                put("feeType", f.feeType)
                put("amount", f.amount)
                put("paidAmount", f.paidAmount)
                put("dueAmount", f.dueAmount)
                put("status", f.status)
                put("paymentDate", f.paymentDate)
                put("receiptNo", f.receiptNo)
                put("notes", f.notes ?: "")
                put("createdAt", f.createdAt)
                put("updatedAt", f.updatedAt)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "fees_records",
                fileName = "fees_records.json",
                titleBn = "ফি ও পেমেন্ট রেকর্ড",
                recordCount = fees.size,
                jsonContent = feesArray.toString(2)
            )
        )

        // 7. Notices
        val notices = repository.allNotices.firstOrNull() ?: emptyList()
        val noticesArray = JSONArray()
        notices.forEach { n ->
            noticesArray.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("publishedDate", n.publishedDate)
                put("targetAudience", n.targetAudience)
                put("priority", n.priority)
                put("authorName", n.authorName)
                put("createdAt", n.createdAt)
                put("updatedAt", n.updatedAt)
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "notices_records",
                fileName = "notices_records.json",
                titleBn = "বিদ্যালয়ের নোটিশ",
                recordCount = notices.size,
                jsonContent = noticesArray.toString(2)
            )
        )

        // 8. Routine Items
        val routines = repository.allRoutineItems.firstOrNull() ?: emptyList()
        val routineArray = JSONArray()
        routines.forEach { r ->
            routineArray.put(JSONObject().apply {
                put("id", r.id)
                put("routineType", r.routineType)
                put("className", r.className)
                put("subject", r.subject)
                put("teacher", r.teacher)
                put("day", r.day)
                put("startTime", r.startTime)
                put("endTime", r.endTime)
                put("periodName", r.periodName)
                put("roomNo", r.roomNo ?: "")
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

        // 9. Document Templates
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

        // 10. Surveys
        val surveys = repository.allSurveys.firstOrNull() ?: emptyList()
        val surveysArray = JSONArray()
        surveys.forEach { s ->
            surveysArray.put(JSONObject().apply {
                put("id", s.id)
                put("studentId", s.studentId ?: "")
                put("surveyYear", s.surveyYear)
                put("age", s.age)
                put("educationStatus", s.educationStatus)
                put("schoolName", s.schoolName)
                put("className", s.className)
                put("gender", s.gender)
                put("isSpecialNeeds", s.isSpecialNeeds)
                put("notes", s.notes ?: "")
            })
        }
        segments.add(
            BackupSegmentItem(
                segmentKey = "surveys_records",
                fileName = "surveys_records.json",
                titleBn = "সার্ভে ও জরিপ তথ্য",
                recordCount = surveys.size,
                jsonContent = surveysArray.toString(2)
            )
        )

        // 11. Custom Fields & Formulas
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

            // Step 1: Generate all fresh segments from Room DB
            val segments = generateAllSegments(repository)
            val total = segments.size + 1 // +1 for manifest

            // Step 2: Fetch existing files in the Drive folder
            val existingFilesMap = fetchFolderFilesMap(accessToken, folderId)
            AppErrorLogger.logInfo("SegmentedSync", "ড্রাইভ ফোল্ডারে বিদ্যমান ফাইল সংখ্যা: ${existingFilesMap.size}")

            var uploadedCount = 0
            var skippedCount = 0
            var errorCount = 0
            val detailsList = mutableListOf<String>()

            val manifestEntries = mutableMapOf<String, SegmentManifestEntry>()

            // Step 3: Iterate and upload changed segments only
            segments.forEachIndexed { index, seg ->
                val currentStep = index + 1
                val storedHash = getStoredSegmentHash(seg.segmentKey)
                val isFileOnDrive = existingFilesMap.containsKey(seg.fileName)
                val isUnchanged = (storedHash == seg.contentHash) && isFileOnDrive

                if (isUnchanged) {
                    // Skip upload!
                    skippedCount++
                    val msg = "${seg.titleBn} (${seg.fileName}) [অপরিবর্তিত - স্কিপ করা হয়েছে]"
                    detailsList.add(msg)
                    onProgress(currentStep, total, seg, true, msg)
                    Log.d(TAG, "SEGMENT_SKIPPED: ${seg.fileName} (Hash matches & file exists)")
                } else {
                    // Upload / Update file on Drive
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

            // Step 4: Upload Manifest File
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
            AppErrorLogger.logError("SegmentedSync", "সিঙ্ক প্রক্রিয়ায় সাধারণ ত্রুটি: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // 4. RESTORE SEGMENTS FROM GOOGLE DRIVE
    // ==========================================

    data class RestoreResult(
        val restoredFilesCount: Int,
        val totalRecordsImported: Int,
        val summary: String
    )

    suspend fun restoreSegmentsFromDrive(
        accessToken: String,
        folderId: String,
        repository: SchoolRepository,
        onProgress: (current: Int, total: Int, fileName: String, message: String) -> Unit = { _, _, _, _ -> }
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            AppErrorLogger.logInfo("SegmentedRestore", "Google Drive থেকে রিস্টোর শুরু হচ্ছে... FolderId: $folderId")
            val existingFilesMap = fetchFolderFilesMap(accessToken, folderId)

            if (existingFilesMap.isEmpty()) {
                return@withContext Result.failure(Exception("ড্রাইভ ফোল্ডারে কোনো ব্যাকআপ ফাইল পাওয়া যায়নি"))
            }

            var importedFiles = 0
            var totalRecords = 0
            val totalFiles = existingFilesMap.size

            // Prioritize Order: school_profile, school_users, custom_fields_and_formulas, students_*, attendance, exam_results, etc.
            val sortedFiles = existingFilesMap.toList().sortedBy { (fileName, _) ->
                when {
                    fileName.startsWith("school_profile") -> 1
                    fileName.startsWith("school_users") -> 2
                    fileName.startsWith("custom_fields") -> 3
                    fileName.startsWith("students_") -> 4
                    fileName.startsWith("attendance_") -> 5
                    fileName.startsWith("exam_results") -> 6
                    else -> 10
                }
            }

            for ((index, entry) in sortedFiles.withIndex()) {
                val (fileName, fileId) = entry
                if (fileName == "backup_manifest.json" || fileName == "school_system_info.json") continue

                onProgress(index + 1, totalFiles, fileName, "ডাউনলোড ও ইম্পোর্ট হচ্ছে: $fileName...")
                val content = downloadFileContent(accessToken, fileId)

                if (content.isNotBlank()) {
                    val count = importSingleSegmentContent(fileName, content, repository)
                    totalRecords += count
                    importedFiles++
                }
            }

            val summary = "সফলভাবে $importedFiles টি সেগমেন্ট এবং $totalRecords টি রেকর্ড রিস্টোর সম্পন্ন হয়েছে।"
            AppErrorLogger.logInfo("SegmentedRestore", summary)
            Result.success(RestoreResult(importedFiles, totalRecords, summary))
        } catch (e: Exception) {
            AppErrorLogger.logError("SegmentedRestore", "রিস্টোর ব্যর্থ: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private suspend fun importSingleSegmentContent(
        fileName: String,
        jsonContent: String,
        repository: SchoolRepository
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
                    val students = mutableListOf<StudentEntity>()
                    for (i in 0 until array.length()) {
                        val s = array.getJSONObject(i)
                        students.add(
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
                    if (students.isNotEmpty()) repository.insertAllStudents(students)
                    students.size
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
                fileName == "exam_results.json" -> {
                    val array = JSONArray(jsonContent)
                    val list = mutableListOf<ExamResultEntity>()
                    for (i in 0 until array.length()) {
                        val r = array.getJSONObject(i)
                        list.add(
                            ExamResultEntity(
                                id = r.optString("id", UUID.randomUUID().toString()),
                                examName = r.optString("examName", ""),
                                className = r.optString("className", ""),
                                section = r.optString("section", "ক"),
                                rollNumber = r.optInt("rollNumber", 1),
                                studentId = if (r.optString("studentId").isNotBlank()) r.optString("studentId") else null,
                                studentName = r.optString("studentName", ""),
                                subject = r.optString("subject", ""),
                                marks = r.optDouble("marks", 0.0),
                                grade = r.optString("grade", "A+"),
                                gpa = r.optDouble("gpa", 5.0),
                                date = r.optString("date", ""),
                                createdAt = r.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = r.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (list.isNotEmpty()) repository.insertAllExamResults(list)
                    list.size
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
            // Write segments
            segments.forEach { seg ->
                val entry = ZipEntry(seg.fileName)
                zos.putNextEntry(entry)
                zos.write(seg.jsonContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // Write Manifest
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
            putExtra(Intent.EXTRA_TEXT, "বিদ্যালয়ের সকল ডাটাবেস সেগমেন্ট ফাইলসহ সম্পূর্ণ ব্যাকআপ আর্কাইভ।")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Intent.createChooser(shareIntent, "সেগমেন্টেড ব্যাকআপ জিপ ফাইল শেয়ার করুন").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun restoreFromZipUri(uri: Uri, repository: SchoolRepository): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var restoredCount = 0
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("ফাইল খোলা যায়নি"))

            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val fileName = entry.name
                    if (!entry.isDirectory && fileName.endsWith(".json") && fileName != "backup_manifest.json") {
                        val content = zis.bufferedReader(Charsets.UTF_8).readText()
                        restoredCount += importSingleSegmentContent(fileName, content, repository)
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

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val resp = httpClient.newCall(req).execute()
        val body = resp.body?.string() ?: ""
        if (resp.isSuccessful) {
            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.optString("name")
                    val id = f.optString("id")
                    if (name.isNotBlank() && id.isNotBlank()) {
                        map[name] = id
                    }
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
            // Update existing file content
            val url = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                throw Exception("Drive update failed for $fileName: HTTP ${resp.code}")
            }
        } else {
            // Create file metadata first
            val metaUrl = "https://www.googleapis.com/drive/v3/files"
            val metaPayload = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().apply { put(folderId) })
                put("mimeType", "application/json")
            }

            val metaReq = Request.Builder()
                .url(metaUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metaPayload.toString().toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val metaResp = httpClient.newCall(metaReq).execute()
            val metaBody = metaResp.body?.string() ?: ""
            if (!metaResp.isSuccessful) {
                throw Exception("Drive metadata creation failed for $fileName: HTTP ${metaResp.code}")
            }

            val newId = JSONObject(metaBody).getString("id")

            // Upload content
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$newId?uploadType=media"
            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()

            val uploadResp = httpClient.newCall(uploadReq).execute()
            if (!uploadResp.isSuccessful) {
                throw Exception("Drive upload failed for $fileName: HTTP ${uploadResp.code}")
            }
        }
    }

    private fun downloadFileContent(accessToken: String, fileId: String): String {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val resp = httpClient.newCall(req).execute()
        return resp.body?.string() ?: ""
    }
}
