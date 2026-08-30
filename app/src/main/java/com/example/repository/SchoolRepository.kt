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

    // Student Documents
    val allStudentDocuments: Flow<List<StudentDocumentEntity>> = db.studentDocumentDao().getAllDocuments()
    fun getDocumentsForStudent(studentId: String): Flow<List<StudentDocumentEntity>> = db.studentDocumentDao().getDocumentsForStudent(studentId)
    suspend fun getDocumentsForStudentList(studentId: String): List<StudentDocumentEntity> = db.studentDocumentDao().getDocumentsForStudentList(studentId)
    suspend fun getDocumentById(id: String): StudentDocumentEntity? = db.studentDocumentDao().getDocumentById(id)

    suspend fun insertStudentDocument(doc: StudentDocumentEntity) {
        val entity = doc.copy(
            createdAt = if (doc.createdAt == 0L) System.currentTimeMillis() else doc.createdAt,
            updatedAt = System.currentTimeMillis(),
            version = if (doc.version <= 0) 1 else doc.version,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        db.studentDocumentDao().insertDocument(entity)
    }

    suspend fun insertAllStudentDocuments(docs: List<StudentDocumentEntity>) {
        val mapped = docs.map { d ->
            d.copy(
                createdAt = if (d.createdAt == 0L) System.currentTimeMillis() else d.createdAt,
                updatedAt = System.currentTimeMillis(),
                version = if (d.version <= 0) 1 else d.version,
                isDeleted = false,
                syncStatus = "SYNCED"
            )
        }
        db.studentDocumentDao().insertAllDocuments(mapped)
    }

    suspend fun updateStudentDocument(doc: StudentDocumentEntity) {
        val entity = doc.copy(
            updatedAt = System.currentTimeMillis(),
            version = doc.version + 1,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        db.studentDocumentDao().updateDocument(entity)
    }

    suspend fun deleteStudentDocument(doc: StudentDocumentEntity) {
        val softDeleted = doc.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        db.studentDocumentDao().insertDocument(softDeleted)
    }

    suspend fun deleteStudentDocumentById(id: String) {
        val doc = getDocumentById(id)
        if (doc != null) {
            deleteStudentDocument(doc)
        } else {
            db.studentDocumentDao().softDeleteDocument(id, System.currentTimeMillis())
        }
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
        val documents = allStudentDocuments.firstOrNull() ?: emptyList()

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

        val documentsModelList = documents.map { d ->
            StudentDocumentModel(
                id = d.id,
                studentId = d.studentId,
                title = d.title,
                documentType = d.documentType,
                fileUri = d.fileUri,
                fileType = d.fileType,
                extractedText = d.extractedText,
                pageCount = d.pageCount,
                notes = d.notes,
                scanDate = d.scanDate,
                createdAt = d.createdAt,
                updatedAt = d.updatedAt
            )
        }

        return SchoolDatabaseModel(
            schemaVersion = 1,
            lastUpdated = System.currentTimeMillis(),
            schoolInfo = schoolInfoModel,
            usersList = usersModelList,
            studentsList = studentsModelList,
            attendanceList = attendanceModelList,
            documentsList = documentsModelList
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

        // Save Documents
        if (model.documentsList.isNotEmpty()) {
            val docEntities = model.documentsList.map { d ->
                StudentDocumentEntity(
                    id = d.id,
                    studentId = d.studentId,
                    title = d.title,
                    documentType = d.documentType,
                    fileUri = d.fileUri,
                    fileType = d.fileType,
                    extractedText = d.extractedText,
                    pageCount = d.pageCount,
                    notes = d.notes,
                    scanDate = d.scanDate,
                    createdAt = d.createdAt,
                    updatedAt = d.updatedAt,
                    isDeleted = false,
                    syncStatus = "SYNCED"
                )
            }
            db.studentDocumentDao().insertAllDocuments(docEntities)
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
        db.studentDocumentDao().deleteAllDocuments()
    }

    suspend fun clearAllDatabaseTables() {
        db.studentDao().deleteAllStudents()
        db.attendanceDao().deleteAllAttendance()
        db.routineDao().deleteAllRoutineItems()
        db.documentTemplateDao().deleteAllTemplates()
        db.customFieldDao().deleteAllFields()
        db.formulaRuleDao().deleteAllRules()
        db.studentDocumentDao().deleteAllDocuments()
    }
}

