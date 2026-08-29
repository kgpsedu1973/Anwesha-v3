package com.example.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SchoolRepository(
    private val db: AppDatabase
) {

    // School Info
    val schoolInfo: Flow<SchoolInfoEntity?> = db.schoolInfoDao().getSchoolInfo()
    suspend fun saveSchoolInfo(info: SchoolInfoEntity) {
        val updated = info.copy(
            updatedAt = System.currentTimeMillis(),
            version = info.version + 1
        )
        db.schoolInfoDao().insertOrUpdateSchoolInfo(updated)
    }

    // Users
    val allUsers: Flow<List<UserEntity>> = db.userDao().getAllUsers()
    suspend fun getUserById(userId: String): UserEntity? = db.userDao().getUserById(userId)
    suspend fun getAdminUser(): UserEntity? = db.userDao().getAdminUser()
    suspend fun insertUser(user: UserEntity) = db.userDao().insertUser(user)
    suspend fun insertAllUsers(users: List<UserEntity>) = db.userDao().insertAllUsers(users)
    suspend fun deleteUser(user: UserEntity) = db.userDao().deleteUser(user)
    suspend fun deleteUserById(userId: String) = db.userDao().deleteUserById(userId)

    // Students
    val allStudents: Flow<List<StudentEntity>> = db.studentDao().getAllStudents()
    fun searchStudents(query: String): Flow<List<StudentEntity>> = db.studentDao().searchStudents(query)
    suspend fun getStudentById(id: String): StudentEntity? = db.studentDao().getStudentById(id)

    suspend fun insertStudent(student: StudentEntity) {
        val entity = student.copy(
            createdAt = if (student.createdAt == 0L) System.currentTimeMillis() else student.createdAt,
            updatedAt = System.currentTimeMillis(),
            version = if (student.version <= 0) 1 else student.version,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        db.studentDao().insertStudent(entity)
    }

    suspend fun insertAllStudents(students: List<StudentEntity>) {
        val mapped = students.map { s ->
            s.copy(
                createdAt = if (s.createdAt == 0L) System.currentTimeMillis() else s.createdAt,
                updatedAt = System.currentTimeMillis(),
                version = if (s.version <= 0) 1 else s.version,
                isDeleted = false,
                syncStatus = "SYNCED"
            )
        }
        db.studentDao().insertAllStudents(mapped)
    }

    suspend fun updateStudent(student: StudentEntity) {
        val entity = student.copy(
            updatedAt = System.currentTimeMillis(),
            version = student.version + 1,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        db.studentDao().updateStudent(entity)
    }

    suspend fun deleteStudent(student: StudentEntity) {
        val softDeleted = student.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        db.studentDao().insertStudent(softDeleted)
    }

    suspend fun deleteStudentById(id: String) {
        val student = getStudentById(id)
        if (student != null) {
            deleteStudent(student)
        } else {
            db.studentDao().softDeleteStudent(id, System.currentTimeMillis())
        }
    }

    // Fees
    val allFees: Flow<List<FeeEntity>> = db.feeDao().getAllFees()
    fun getFeesForStudent(studentId: String): Flow<List<FeeEntity>> = db.feeDao().getFeesForStudent(studentId)
    suspend fun insertFee(fee: FeeEntity) {
        val entity = fee.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED",
            isDeleted = false
        )
        db.feeDao().insertFee(entity)
    }
    suspend fun deleteFee(fee: FeeEntity) {
        val soft = fee.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        db.feeDao().insertFee(soft)
    }

    // Notices
    val allNotices: Flow<List<NoticeEntity>> = db.noticeDao().getAllNotices()
    suspend fun insertNotice(notice: NoticeEntity) {
        val entity = notice.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED",
            isDeleted = false
        )
        db.noticeDao().insertNotice(entity)
    }
    suspend fun deleteNotice(notice: NoticeEntity) {
        val soft = notice.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        db.noticeDao().insertNotice(soft)
    }

    // Custom Fields & Formulas
    val customFields: Flow<List<CustomFieldEntity>> = db.customFieldDao().getAllFields()
    suspend fun insertCustomField(field: CustomFieldEntity) = db.customFieldDao().insertField(field)
    suspend fun deleteCustomField(field: CustomFieldEntity) = db.customFieldDao().deleteField(field)

    val formulaRules: Flow<List<FormulaRuleEntity>> = db.formulaRuleDao().getAllRules()
    suspend fun insertFormulaRule(rule: FormulaRuleEntity) = db.formulaRuleDao().insertRule(rule)
    suspend fun deleteFormulaRule(rule: FormulaRuleEntity) = db.formulaRuleDao().deleteRule(rule)

    // Attendance
    val allAttendance: Flow<List<AttendanceEntity>> = db.attendanceDao().getAllAttendance()

    suspend fun insertAttendance(attendance: AttendanceEntity) {
        val entity = attendance.copy(
            updatedAt = System.currentTimeMillis(),
            version = attendance.version + 1,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        db.attendanceDao().insertAttendance(entity)
    }

    suspend fun insertAllAttendance(records: List<AttendanceEntity>) {
        records.forEach { insertAttendance(it) }
    }

    suspend fun deleteAttendance(attendance: AttendanceEntity) {
        val softDeleted = attendance.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        db.attendanceDao().insertAttendance(softDeleted)
    }

    suspend fun deleteAttendanceForDate(date: String) = db.attendanceDao().deleteAttendanceForDate(date)

    // Routine
    val allRoutineItems: Flow<List<RoutineItemEntity>> = db.routineDao().getAllRoutineItems()
    suspend fun insertRoutineItem(item: RoutineItemEntity) = db.routineDao().insertRoutineItem(item)
    suspend fun deleteRoutineItem(item: RoutineItemEntity) = db.routineDao().deleteRoutineItem(item)

    // Document Templates
    val allDocumentTemplates: Flow<List<DocumentTemplateEntity>> = db.documentTemplateDao().getAllTemplates()
    suspend fun insertDocumentTemplate(template: DocumentTemplateEntity) = db.documentTemplateDao().insertTemplate(template)
    suspend fun deleteDocumentTemplate(template: DocumentTemplateEntity) = db.documentTemplateDao().deleteTemplate(template)

    // Surveys
    val allSurveys: Flow<List<SurveyEntity>> = db.surveyDao().getAllSurveys()
    suspend fun insertSurvey(survey: SurveyEntity) = db.surveyDao().insertSurvey(survey)
    suspend fun deleteSurvey(survey: SurveyEntity) = db.surveyDao().deleteSurvey(survey)

    // Exam Results
    val allExamResults: Flow<List<ExamResultEntity>> = db.examResultDao().getAllResults()
    fun getResultsByClassAndExam(className: String, examName: String): Flow<List<ExamResultEntity>> =
        db.examResultDao().getResultsByClassAndExam(className, examName)

    suspend fun insertExamResult(result: ExamResultEntity) {
        val entity = result.copy(
            updatedAt = System.currentTimeMillis(),
            version = result.version + 1,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        db.examResultDao().insertResult(entity)
    }

    suspend fun insertAllExamResults(results: List<ExamResultEntity>) {
        results.forEach { insertExamResult(it) }
    }

    suspend fun deleteExamResult(result: ExamResultEntity) {
        val softDeleted = result.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        db.examResultDao().insertResult(softDeleted)
    }

    /**
     * Converts current Room Database into the serializable Master School Database Model.
     */
    suspend fun exportToMasterModel(): SchoolDatabaseModel {
        val currentInfo = schoolInfo.firstOrNull() ?: SchoolInfoEntity(
            schoolName = "অন্বেষা বিদ্যালয়",
            eiinCode = "123456",
            adminName = "প্রধান শিক্ষক"
        )
        val students = allStudents.firstOrNull() ?: emptyList()
        val users = allUsers.firstOrNull() ?: emptyList()
        val attendance = allAttendance.firstOrNull() ?: emptyList()
        val examResults = allExamResults.firstOrNull() ?: emptyList()

        val schoolInfoModel = SchoolInfoModel(
            schoolName = currentInfo.schoolName,
            eiinCode = currentInfo.eiinCode,
            adminName = currentInfo.adminName.ifEmpty { currentInfo.headTeacherName },
            adminEmail = currentInfo.adminEmail,
            adminPhone = currentInfo.adminPhone.ifEmpty { currentInfo.phone },
            createdDate = currentInfo.createdDate,
            address = currentInfo.address,
            tagline = currentInfo.tagline,
            logoUri = currentInfo.logoUri,
            headTeacherName = currentInfo.headTeacherName,
            internalVillages = currentInfo.internalVillages
        )

        val usersModelList = users.map { u ->
            UserModel(
                userId = u.userId,
                name = u.name,
                email = u.email,
                phone = u.phone,
                role = u.role,
                status = u.status,
                securityPinHash = u.securityPinHash,
                createdDate = u.createdDate
            )
        }

        val studentsModelList = students.map { s ->
            StudentModel(
                studentId = s.id,
                studentClass = s.studentClass,
                section = s.section,
                rollNumber = s.rollNumber,
                name = s.name,
                parentContact = s.parentContact.ifEmpty { s.mobile },
                fatherName = s.fatherName,
                motherName = s.motherName,
                gender = s.gender,
                village = s.village,
                birthDate = s.birthDate,
                birthRegNumber = s.birthRegNumber,
                address = s.address,
                academicYear = s.academicYear,
                isSpecialNeeds = s.isSpecialNeeds,
                status = s.status,
                photoUri = s.photoUri,
                customValuesJson = s.customValuesJson
            )
        }

        val attendanceModelList = attendance.map { a ->
            AttendanceRecordModel(
                id = a.id,
                date = a.date,
                className = a.className,
                rollNumber = null,
                studentId = null,
                status = "Present",
                remarks = a.notes,
                presentBoys = a.presentBoys,
                presentGirls = a.presentGirls,
                absentBoys = a.absentBoys,
                absentGirls = a.absentGirls
            )
        }

        val examResultsModelList = examResults.map { e ->
            ExamResultModel(
                id = e.id,
                examName = e.examName,
                className = e.className,
                section = e.section,
                rollNumber = e.rollNumber,
                studentId = e.studentId,
                studentName = e.studentName,
                subject = e.subject,
                marks = e.marks,
                grade = e.grade,
                gpa = e.gpa,
                date = e.date
            )
        }

        return SchoolDatabaseModel(
            schemaVersion = 1,
            lastUpdated = System.currentTimeMillis(),
            schoolInfo = schoolInfoModel,
            usersList = usersModelList,
            studentsList = studentsModelList,
            attendanceList = attendanceModelList,
            examResultsList = examResultsModelList
        )
    }

    /**
     * Imports a Master School Database Model into Room SQLite Database.
     */
    suspend fun importMasterModel(model: SchoolDatabaseModel) {
        // Save School Info
        val infoEntity = SchoolInfoEntity(
            id = 1,
            schoolName = model.schoolInfo.schoolName,
            address = model.schoolInfo.address,
            eiinCode = model.schoolInfo.eiinCode,
            logoUri = model.schoolInfo.logoUri,
            phone = model.schoolInfo.adminPhone,
            headTeacherName = model.schoolInfo.headTeacherName.ifEmpty { model.schoolInfo.adminName },
            adminName = model.schoolInfo.adminName,
            adminEmail = model.schoolInfo.adminEmail,
            adminPhone = model.schoolInfo.adminPhone,
            createdDate = model.schoolInfo.createdDate,
            tagline = model.schoolInfo.tagline,
            internalVillages = model.schoolInfo.internalVillages
        )
        saveSchoolInfo(infoEntity)

        // Save Users
        if (model.usersList.isNotEmpty()) {
            val userEntities = model.usersList.map { u ->
                UserEntity(
                    userId = u.userId,
                    name = u.name,
                    email = u.email,
                    phone = u.phone,
                    role = u.role,
                    status = u.status,
                    securityPinHash = u.securityPinHash,
                    createdDate = u.createdDate
                )
            }
            db.userDao().insertAllUsers(userEntities)
        }

        // Save Students
        if (model.studentsList.isNotEmpty()) {
            val studentEntities = model.studentsList.map { s ->
                StudentEntity(
                    id = s.studentId,
                    studentClass = s.studentClass,
                    section = s.section,
                    rollNumber = s.rollNumber,
                    name = s.name,
                    parentContact = s.parentContact,
                    fatherName = s.fatherName,
                    motherName = s.motherName,
                    birthDate = s.birthDate,
                    mobile = s.parentContact,
                    village = s.village,
                    academicYear = s.academicYear,
                    address = s.address,
                    birthRegNumber = s.birthRegNumber,
                    gender = s.gender,
                    isSpecialNeeds = s.isSpecialNeeds,
                    status = s.status,
                    photoUri = s.photoUri,
                    customValuesJson = s.customValuesJson
                )
            }
            db.studentDao().insertAllStudents(studentEntities)
        }

        // Save Exam Results
        if (model.examResultsList.isNotEmpty()) {
            val examEntities = model.examResultsList.map { e ->
                ExamResultEntity(
                    id = e.id,
                    examName = e.examName,
                    className = e.className,
                    section = e.section,
                    rollNumber = e.rollNumber,
                    studentId = e.studentId,
                    studentName = e.studentName,
                    subject = e.subject,
                    marks = e.marks,
                    grade = e.grade,
                    gpa = e.gpa,
                    date = e.date
                )
            }
            db.examResultDao().insertAllResults(examEntities)
        }
    }

    // Data Backup / Export to JSON
    suspend fun exportAllDataToJson(students: List<StudentEntity>): String {
        return exportToMasterModel().toJson(true)
    }

    // Data Import from JSON
    suspend fun importDataFromJson(jsonString: String): Int {
        val master = SchoolDatabaseModel.fromJson(jsonString)
        return if (master != null) {
            importMasterModel(master)
            master.studentsList.size
        } else {
            0
        }
    }

    suspend fun clearAllLocalData() {
        db.studentDao().deleteAllStudents()
        db.attendanceDao().deleteAllAttendance()
        db.examResultDao().deleteAllResults()
    }
}

