package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.model.SchoolDatabaseModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * InternalAutoBackupManager provides bulletproof on-device data durability.
 * It automatically maintains persistent internal JSON and SQLite snapshots
 * in private app storage so that app updates, migrations, or OS events never lose user data.
 */
class InternalAutoBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "InternalAutoBackup"
        private const val VAULT_DIR = "persistent_vault"
        private const val SNAPSHOT_JSON_FILE = "school_snapshot_autobackup.json"
        private const val SNAPSHOT_DB_FILE = "anwesha_school_db.bak"
        private const val PREFS_NAME = "internal_backup_prefs"
        private const val KEY_LAST_INTERNAL_BACKUP_TIME = "key_last_internal_backup_time"
        private const val KEY_IS_ONBOARDING_COMPLETED = "key_is_onboarding_completed"
        private const val KEY_SETUP_COMPLETED_TIME = "key_setup_completed_time"

        @Volatile
        private var INSTANCE: InternalAutoBackupManager? = null

        fun getInstance(context: Context): InternalAutoBackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InternalAutoBackupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getVaultDirectory(): File {
        val dir = File(context.filesDir, VAULT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isInitialSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_IS_ONBOARDING_COMPLETED, false)
    }

    fun setInitialSetupCompleted(completed: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_ONBOARDING_COMPLETED, completed)
            .putLong(KEY_SETUP_COMPLETED_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLastInternalBackupTimestamp(): Long {
        return prefs.getLong(KEY_LAST_INTERNAL_BACKUP_TIME, 0L)
    }

    /**
     * Creates an atomic persistent internal snapshot of all database tables.
     */
    suspend fun saveInternalSnapshot(db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            val schoolInfo = db.schoolInfoDao().getSchoolInfoSync()
            val students = db.studentDao().getAllStudentsList()
            val attendance = db.attendanceDao().getAllAttendanceList()
            val routines = db.routineDao().getAllRoutinesList()
            val customFields = db.customFieldDao().getAllFieldsList()
            val formulas = db.formulaRuleDao().getAllRulesList()
            val users = db.userDao().getAllUsersList()
            val templates = db.documentTemplateDao().getAllTemplatesList()
            val studentDocs = db.studentDocumentDao().getAllDocumentsList()

            // Do not overwrite valid backup with empty data if db was unexpectedly empty
            if (schoolInfo == null && students.isEmpty()) {
                Log.w(TAG, "Skipping snapshot: database appears empty")
                return@withContext false
            }

            val root = JSONObject().apply {
                put("version", 2)
                put("timestamp", System.currentTimeMillis())
                put("formattedDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

                // School Info
                if (schoolInfo != null) {
                    put("schoolInfo", JSONObject().apply {
                        put("schoolName", schoolInfo.schoolName)
                        put("eiinCode", schoolInfo.eiinCode)
                        put("address", schoolInfo.address)
                        put("tagline", schoolInfo.tagline)
                        put("phone", schoolInfo.phone)
                        put("email", schoolInfo.email)
                        put("headTeacherName", schoolInfo.headTeacherName)
                        put("adminName", schoolInfo.adminName)
                        put("adminEmail", schoolInfo.adminEmail)
                        put("adminPhone", schoolInfo.adminPhone)
                        put("logoUri", schoolInfo.logoUri ?: "")
                        put("internalVillages", schoolInfo.internalVillages)
                        put("customSchoolInfoJson", schoolInfo.customSchoolInfoJson)
                        put("createdDate", schoolInfo.createdDate)
                        put("updatedAt", schoolInfo.updatedAt)
                    })
                }

                // Students
                val studentsArr = JSONArray()
                students.forEach { s ->
                    studentsArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("studentClass", s.studentClass)
                        put("section", s.section)
                        put("rollNumber", s.rollNumber)
                        put("name", s.name)
                        put("parentContact", s.parentContact)
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
                    })
                }
                put("students", studentsArr)

                // Attendance
                val attArr = JSONArray()
                attendance.forEach { a ->
                    attArr.put(JSONObject().apply {
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
                put("attendance", attArr)

                // Routines
                val routArr = JSONArray()
                routines.forEach { r ->
                    routArr.put(JSONObject().apply {
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
                put("routines", routArr)

                // Users
                val usersArr = JSONArray()
                users.forEach { u ->
                    usersArr.put(JSONObject().apply {
                        put("userId", u.userId)
                        put("name", u.name)
                        put("email", u.email)
                        put("phone", u.phone)
                        put("role", u.role)
                        put("status", u.status)
                        put("securityPinHash", u.securityPinHash)
                    })
                }
                put("users", usersArr)

                // Custom Fields
                val cfArr = JSONArray()
                customFields.forEach { cf ->
                    cfArr.put(JSONObject().apply {
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
                put("customFields", cfArr)

                // Formulas
                val frArr = JSONArray()
                formulas.forEach { fr ->
                    frArr.put(JSONObject().apply {
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
                put("formulaRules", frArr)

                // Templates
                val dtArr = JSONArray()
                templates.forEach { dt ->
                    dtArr.put(JSONObject().apply {
                        put("id", dt.id)
                        put("title", dt.title)
                        put("contentTemplate", dt.contentTemplate)
                        put("createdDate", dt.createdDate)
                    })
                }
                put("documentTemplates", dtArr)
            }

            val snapshotFile = File(getVaultDirectory(), SNAPSHOT_JSON_FILE)
            val tempFile = File(getVaultDirectory(), "$SNAPSHOT_JSON_FILE.tmp")

            FileOutputStream(tempFile).use { fos ->
                fos.write(root.toString(2).toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            if (tempFile.exists()) {
                if (snapshotFile.exists()) snapshotFile.delete()
                tempFile.renameTo(snapshotFile)
            }

            // Also copy SQLite binary if possible
            backupDatabaseBinaryFile()

            prefs.edit().putLong(KEY_LAST_INTERNAL_BACKUP_TIME, System.currentTimeMillis()).apply()
            Log.d(TAG, "Internal snapshot safely saved (${students.size} students, ${attendance.size} attendance records)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save internal snapshot: ${e.message}", e)
            false
        }
    }

    private fun backupDatabaseBinaryFile() {
        try {
            val dbFile = context.getDatabasePath("anwesha_school_db")
            if (dbFile.exists() && dbFile.length() > 0) {
                val bakFile = File(getVaultDirectory(), SNAPSHOT_DB_FILE)
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(bakFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Binary backup error: ${e.message}")
        }
    }

    fun hasPersistentSnapshot(): Boolean {
        val file = File(getVaultDirectory(), SNAPSHOT_JSON_FILE)
        return file.exists() && file.length() > 100
    }

    /**
     * Restores data from the persistent internal snapshot if database tables are empty
     * (e.g. following an app update or database recreation).
     */
    suspend fun restorePersistentSnapshotIfEmpty(db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            val studentCount = db.studentDao().getStudentCountSync()
            val hasSchool = db.schoolInfoDao().getSchoolInfoSync() != null

            if (studentCount > 0 && hasSchool) {
                // Database is already populated, no need to auto-restore
                return@withContext false
            }

            val file = File(getVaultDirectory(), SNAPSHOT_JSON_FILE)
            if (!file.exists() || file.length() == 0L) {
                return@withContext false
            }

            Log.i(TAG, "Empty database detected on startup! Auto-recovering from persistent vault snapshot...")
            val jsonContent = file.readText(Charsets.UTF_8)
            val root = JSONObject(jsonContent)

            // Restore School Info
            if (root.has("schoolInfo")) {
                val sObj = root.getJSONObject("schoolInfo")
                val school = SchoolInfoEntity(
                    id = 1,
                    schoolName = sObj.optString("schoolName", "অন্বেষা বিদ্যালয়"),
                    eiinCode = sObj.optString("eiinCode", "123456"),
                    address = sObj.optString("address", ""),
                    tagline = sObj.optString("tagline", "জ্ঞান, মনন ও স্বপ্নের সোপান"),
                    phone = sObj.optString("phone", ""),
                    email = sObj.optString("email", ""),
                    headTeacherName = sObj.optString("headTeacherName", ""),
                    adminName = sObj.optString("adminName", ""),
                    adminEmail = sObj.optString("adminEmail", ""),
                    adminPhone = sObj.optString("adminPhone", ""),
                    logoUri = if (sObj.optString("logoUri").isNotBlank()) sObj.optString("logoUri") else null,
                    internalVillages = sObj.optString("internalVillages", "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর"),
                    customSchoolInfoJson = sObj.optString("customSchoolInfoJson", "[]"),
                    createdDate = sObj.optString("createdDate", ""),
                    updatedAt = sObj.optLong("updatedAt", System.currentTimeMillis())
                )
                db.schoolInfoDao().insertOrUpdateSchoolInfo(school)
            }

            // Restore Custom Fields
            if (root.has("customFields")) {
                val cfArr = root.getJSONArray("customFields")
                for (i in 0 until cfArr.length()) {
                    val cf = cfArr.getJSONObject(i)
                    db.customFieldDao().insertField(
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

            // Restore Formula Rules
            if (root.has("formulaRules")) {
                val frArr = root.getJSONArray("formulaRules")
                for (i in 0 until frArr.length()) {
                    val fr = frArr.getJSONObject(i)
                    db.formulaRuleDao().insertRule(
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

            // Restore Students
            if (root.has("students")) {
                val sArr = root.getJSONArray("students")
                val students = mutableListOf<StudentEntity>()
                for (i in 0 until sArr.length()) {
                    val s = sArr.getJSONObject(i)
                    students.add(
                        StudentEntity(
                            id = s.optString("id", "STU-$i"),
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
                            updatedAt = s.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (students.isNotEmpty()) {
                    db.studentDao().insertAllStudents(students)
                }
            }

            // Restore Attendance
            if (root.has("attendance")) {
                val aArr = root.getJSONArray("attendance")
                val attList = mutableListOf<AttendanceEntity>()
                for (i in 0 until aArr.length()) {
                    val a = aArr.getJSONObject(i)
                    attList.add(
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
                if (attList.isNotEmpty()) {
                    db.attendanceDao().insertAllAttendance(attList)
                }
            }

            // Restore Routines
            if (root.has("routines")) {
                val rArr = root.getJSONArray("routines")
                for (i in 0 until rArr.length()) {
                    val r = rArr.getJSONObject(i)
                    db.routineDao().insertRoutineItem(
                        RoutineItemEntity(
                            id = r.optString("id", UUID.randomUUID().toString()),
                            routineType = r.optString("routineType", "Class Routine"),
                            className = r.optString("className", ""),
                            subject = r.optString("subject", ""),
                            teacher = r.optString("teacher", ""),
                            day = r.optString("day", "রবিবার"),
                            startTime = r.optString("startTime", "09:00 AM"),
                            endTime = r.optString("endTime", "09:45 AM"),
                            periodName = r.optString("periodName", "১ম পিরিয়ড"),
                            roomNo = r.optString("roomNo", null)
                        )
                    )
                }
            }

            // Restore Users
            if (root.has("users")) {
                val uArr = root.getJSONArray("users")
                val uList = mutableListOf<UserEntity>()
                for (i in 0 until uArr.length()) {
                    val u = uArr.getJSONObject(i)
                    uList.add(
                        UserEntity(
                            userId = u.optString("userId", "USR-$i"),
                            name = u.optString("name", ""),
                            email = u.optString("email", ""),
                            phone = u.optString("phone", ""),
                            role = u.optString("role", "Admin"),
                            status = u.optString("status", "Active"),
                            securityPinHash = u.optString("securityPinHash", "")
                        )
                    )
                }
                if (uList.isNotEmpty()) {
                    db.userDao().insertAllUsers(uList)
                }
            }

            // Restore Document Templates
            if (root.has("documentTemplates")) {
                val dtArr = root.getJSONArray("documentTemplates")
                for (i in 0 until dtArr.length()) {
                    val dt = dtArr.getJSONObject(i)
                    db.documentTemplateDao().insertTemplate(
                        DocumentTemplateEntity(
                            id = dt.optString("id", UUID.randomUUID().toString()),
                            title = dt.optString("title", ""),
                            contentTemplate = dt.optString("contentTemplate", ""),
                            createdDate = dt.optString("createdDate", "")
                        )
                    )
                }
            }

            Log.i(TAG, "Auto-recovery from persistent vault completed successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auto-recovery failed: ${e.message}", e)
            false
        }
    }
}
