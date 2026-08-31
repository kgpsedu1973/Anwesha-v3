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

    @Query("SELECT * FROM students ORDER BY studentClass ASC, rollNumber ASC")
    suspend fun getAllStudentsList(): List<StudentEntity>

    @Query("SELECT COUNT(*) FROM students WHERE isDeleted = 0")
    suspend fun getStudentCountSync(): Int

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

    @Query("SELECT * FROM custom_fields ORDER BY name ASC")
    suspend fun getAllFieldsList(): List<CustomFieldEntity>

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

    @Query("SELECT * FROM formula_rules ORDER BY ruleName ASC")
    suspend fun getAllRulesList(): List<FormulaRuleEntity>

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

    @Query("SELECT * FROM attendance_records WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllAttendanceList(): List<AttendanceEntity>

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

    @Query("SELECT * FROM routine_items ORDER BY className ASC, day ASC, startTime ASC")
    suspend fun getAllRoutinesList(): List<RoutineItemEntity>

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

    @Query("SELECT * FROM document_templates ORDER BY createdDate DESC")
    suspend fun getAllTemplatesList(): List<DocumentTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: DocumentTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: DocumentTemplateEntity)

    @Query("DELETE FROM document_templates")
    suspend fun deleteAllTemplates()
}

@Dao
interface SchoolInfoDao {
    @Query("SELECT * FROM school_info WHERE id = 1 LIMIT 1")
    fun getSchoolInfo(): Flow<SchoolInfoEntity?>

    @Query("SELECT * FROM school_info WHERE id = 1 LIMIT 1")
    suspend fun getSchoolInfoSync(): SchoolInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchoolInfo(schoolInfo: SchoolInfoEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsersList(): List<UserEntity>

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

