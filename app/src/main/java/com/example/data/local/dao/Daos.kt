package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY studentClass ASC, rollNumber ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY studentClass ASC, rollNumber ASC")
    fun getAllStudentsIncludingDeleted(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY studentClass ASC, rollNumber ASC")
    suspend fun getAllActiveStudentsList(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE syncStatus = 'PENDING'")
    suspend fun getPendingStudents(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE updatedAt > :timestamp")
    suspend fun getStudentsModifiedSince(timestamp: Long): List<StudentEntity>

    @Query("UPDATE students SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE students SET isDeleted = 1, deletedAt = :timestamp, updatedAt = :timestamp, updatedBy = :userEmail, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun softDeleteStudent(id: String, timestamp: Long = System.currentTimeMillis(), userEmail: String = "")

    @Query("""
        SELECT * FROM students 
        WHERE isDeleted = 0 AND (
            name LIKE '%' || :query || '%' 
            OR fatherName LIKE '%' || :query || '%'
            OR motherName LIKE '%' || :query || '%'
            OR village LIKE '%' || :query || '%'
            OR studentClass LIKE '%' || :query || '%'
            OR id LIKE '%' || :query || '%'
            OR mobile LIKE '%' || :query || '%'
        )
        ORDER BY name ASC
    """)
    fun searchStudents(query: String): Flow<List<StudentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStudents(students: List<StudentEntity>)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: String)

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()
}

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields ORDER BY name ASC")
    fun getAllFields(): Flow<List<CustomFieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: CustomFieldEntity)

    @Delete
    suspend fun deleteField(field: CustomFieldEntity)

    @Query("DELETE FROM custom_fields")
    suspend fun deleteAllFields()
}

@Dao
interface FormulaRuleDao {
    @Query("SELECT * FROM formula_rules ORDER BY ruleName ASC")
    fun getAllRules(): Flow<List<FormulaRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FormulaRuleEntity)

    @Delete
    suspend fun deleteRule(rule: FormulaRuleEntity)

    @Query("DELETE FROM formula_rules")
    suspend fun deleteAllRules()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records WHERE date = :date AND isDeleted = 0")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records WHERE date LIKE :yearMonth || '%' AND isDeleted = 0 ORDER BY date ASC")
    fun getAttendanceForMonth(yearMonth: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records WHERE date LIKE :yearMonth || '%'")
    suspend fun getAttendanceListForMonth(yearMonth: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_records WHERE syncStatus = 'PENDING'")
    suspend fun getPendingAttendance(): List<AttendanceEntity>

    @Query("UPDATE attendance_records SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAttendance(records: List<AttendanceEntity>)

    @Delete
    suspend fun deleteAttendance(attendance: AttendanceEntity)

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteAttendanceById(id: String)

    @Query("DELETE FROM attendance_records WHERE date = :date")
    suspend fun deleteAttendanceForDate(date: String)

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAllAttendance()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_items ORDER BY className ASC, day ASC, startTime ASC")
    fun getAllRoutineItems(): Flow<List<RoutineItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineItem(item: RoutineItemEntity)

    @Delete
    suspend fun deleteRoutineItem(item: RoutineItemEntity)

    @Query("DELETE FROM routine_items")
    suspend fun deleteAllRoutineItems()
}

@Dao
interface DocumentTemplateDao {
    @Query("SELECT * FROM document_templates ORDER BY createdDate DESC")
    fun getAllTemplates(): Flow<List<DocumentTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: DocumentTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: DocumentTemplateEntity)

    @Query("DELETE FROM document_templates")
    suspend fun deleteAllTemplates()
}

@Dao
interface SurveyDao {
    @Query("SELECT * FROM surveys ORDER BY surveyYear DESC")
    fun getAllSurveys(): Flow<List<SurveyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: SurveyEntity)

    @Delete
    suspend fun deleteSurvey(survey: SurveyEntity)

    @Query("DELETE FROM surveys")
    suspend fun deleteAllSurveys()
}

@Dao
interface SchoolInfoDao {
    @Query("SELECT * FROM school_info WHERE id = 1 LIMIT 1")
    fun getSchoolInfo(): Flow<SchoolInfoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchoolInfo(schoolInfo: SchoolInfoEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE role = 'Admin' LIMIT 1")
    suspend fun getAdminUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllUsers(users: List<UserEntity>)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

@Dao
interface ExamResultDao {
    @Query("SELECT * FROM exam_results WHERE isDeleted = 0 ORDER BY className ASC, rollNumber ASC")
    fun getAllResults(): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_results WHERE className = :className AND examName = :examName AND isDeleted = 0 ORDER BY rollNumber ASC")
    fun getResultsByClassAndExam(className: String, examName: String): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_results WHERE syncStatus = 'PENDING'")
    suspend fun getPendingExamResults(): List<ExamResultEntity>

    @Query("UPDATE exam_results SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ExamResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllResults(results: List<ExamResultEntity>)

    @Delete
    suspend fun deleteResult(result: ExamResultEntity)

    @Query("DELETE FROM exam_results WHERE id = :id")
    suspend fun deleteResultById(id: String)

    @Query("DELETE FROM exam_results")
    suspend fun deleteAllResults()
}

@Dao
interface FeeDao {
    @Query("SELECT * FROM fees WHERE isDeleted = 0 ORDER BY month DESC, studentClass ASC")
    fun getAllFees(): Flow<List<FeeEntity>>

    @Query("SELECT * FROM fees WHERE studentId = :studentId AND isDeleted = 0 ORDER BY month DESC")
    fun getFeesForStudent(studentId: String): Flow<List<FeeEntity>>

    @Query("SELECT * FROM fees WHERE month = :month AND isDeleted = 0")
    fun getFeesForMonth(month: String): Flow<List<FeeEntity>>

    @Query("SELECT * FROM fees WHERE id = :id LIMIT 1")
    suspend fun getFeeById(id: String): FeeEntity?

    @Query("SELECT * FROM fees WHERE syncStatus = 'PENDING'")
    suspend fun getPendingFees(): List<FeeEntity>

    @Query("UPDATE fees SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFee(fee: FeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFees(fees: List<FeeEntity>)

    @Update
    suspend fun updateFee(fee: FeeEntity)

    @Delete
    suspend fun deleteFee(fee: FeeEntity)

    @Query("DELETE FROM fees WHERE id = :id")
    suspend fun deleteFeeById(id: String)

    @Query("DELETE FROM fees")
    suspend fun deleteAllFees()
}

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices WHERE isDeleted = 0 ORDER BY publishedDate DESC, createdAt DESC")
    fun getAllNotices(): Flow<List<NoticeEntity>>

    @Query("SELECT * FROM notices WHERE id = :id LIMIT 1")
    suspend fun getNoticeById(id: String): NoticeEntity?

    @Query("SELECT * FROM notices WHERE syncStatus = 'PENDING'")
    suspend fun getPendingNotices(): List<NoticeEntity>

    @Query("UPDATE notices SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: NoticeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNotices(notices: List<NoticeEntity>)

    @Update
    suspend fun updateNotice(notice: NoticeEntity)

    @Delete
    suspend fun deleteNotice(notice: NoticeEntity)

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteNoticeById(id: String)

    @Query("DELETE FROM notices")
    suspend fun deleteAllNotices()
}
