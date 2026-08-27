package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY studentClass ASC, rollNumber ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): StudentEntity?

    @Query("""
        SELECT * FROM students 
        WHERE name LIKE '%' || :query || '%' 
        OR fatherName LIKE '%' || :query || '%'
        OR motherName LIKE '%' || :query || '%'
        OR village LIKE '%' || :query || '%'
        OR studentClass LIKE '%' || :query || '%'
        OR id LIKE '%' || :query || '%'
        OR mobile LIKE '%' || :query || '%'
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
}

@Dao
interface FormulaRuleDao {
    @Query("SELECT * FROM formula_rules ORDER BY ruleName ASC")
    fun getAllRules(): Flow<List<FormulaRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FormulaRuleEntity)

    @Delete
    suspend fun deleteRule(rule: FormulaRuleEntity)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAttendance(records: List<AttendanceEntity>)

    @Delete
    suspend fun deleteAttendance(attendance: AttendanceEntity)

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
}

@Dao
interface DocumentTemplateDao {
    @Query("SELECT * FROM document_templates ORDER BY createdDate DESC")
    fun getAllTemplates(): Flow<List<DocumentTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: DocumentTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: DocumentTemplateEntity)
}

@Dao
interface SurveyDao {
    @Query("SELECT * FROM surveys ORDER BY surveyYear DESC")
    fun getAllSurveys(): Flow<List<SurveyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: SurveyEntity)

    @Delete
    suspend fun deleteSurvey(survey: SurveyEntity)
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
    @Query("SELECT * FROM exam_results ORDER BY className ASC, rollNumber ASC")
    fun getAllResults(): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_results WHERE className = :className AND examName = :examName ORDER BY rollNumber ASC")
    fun getResultsByClassAndExam(className: String, examName: String): Flow<List<ExamResultEntity>>

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
