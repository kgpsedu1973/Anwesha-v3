package com.example.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SchoolRepository(private val db: AppDatabase) {

    // School Info
    val schoolInfo: Flow<SchoolInfoEntity?> = db.schoolInfoDao().getSchoolInfo()
    suspend fun saveSchoolInfo(info: SchoolInfoEntity) = db.schoolInfoDao().insertOrUpdateSchoolInfo(info)

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
    suspend fun insertStudent(student: StudentEntity) = db.studentDao().insertStudent(student)
    suspend fun insertAllStudents(students: List<StudentEntity>) = db.studentDao().insertAllStudents(students)
    suspend fun updateStudent(student: StudentEntity) = db.studentDao().updateStudent(student)
    suspend fun deleteStudent(student: StudentEntity) = db.studentDao().deleteStudent(student)
    suspend fun deleteStudentById(id: String) = db.studentDao().deleteStudentById(id)

    // Custom Fields & Formulas
    val customFields: Flow<List<CustomFieldEntity>> = db.customFieldDao().getAllFields()
    suspend fun insertCustomField(field: CustomFieldEntity) = db.customFieldDao().insertField(field)
    suspend fun deleteCustomField(field: CustomFieldEntity) = db.customFieldDao().deleteField(field)

    val formulaRules: Flow<List<FormulaRuleEntity>> = db.formulaRuleDao().getAllRules()
    suspend fun insertFormulaRule(rule: FormulaRuleEntity) = db.formulaRuleDao().insertRule(rule)
    suspend fun deleteFormulaRule(rule: FormulaRuleEntity) = db.formulaRuleDao().deleteRule(rule)

    // Attendance
    val allAttendance: Flow<List<AttendanceEntity>> = db.attendanceDao().getAllAttendance()
    suspend fun insertAttendance(attendance: AttendanceEntity) = db.attendanceDao().insertAttendance(attendance)
    suspend fun insertAllAttendance(records: List<AttendanceEntity>) = db.attendanceDao().insertAllAttendance(records)
    suspend fun deleteAttendance(attendance: AttendanceEntity) = db.attendanceDao().deleteAttendance(attendance)
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
    suspend fun insertExamResult(result: ExamResultEntity) = db.examResultDao().insertResult(result)
    suspend fun insertAllExamResults(results: List<ExamResultEntity>) = db.examResultDao().insertAllResults(results)
    suspend fun deleteExamResult(result: ExamResultEntity) = db.examResultDao().deleteResult(result)

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

