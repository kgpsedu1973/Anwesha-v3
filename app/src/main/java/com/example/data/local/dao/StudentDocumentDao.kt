package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.StudentDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDocumentDao {

    @Query("SELECT * FROM student_documents WHERE studentId = :studentId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getDocumentsForStudent(studentId: String): Flow<List<StudentDocumentEntity>>

    @Query("SELECT * FROM student_documents WHERE studentId = :studentId AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getDocumentsForStudentList(studentId: String): List<StudentDocumentEntity>

    @Query("SELECT * FROM student_documents WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<StudentDocumentEntity>>

    @Query("SELECT * FROM student_documents WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllActiveDocumentsList(): List<StudentDocumentEntity>

    @Query("SELECT * FROM student_documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsList(): List<StudentDocumentEntity>

    @Query("SELECT * FROM student_documents ORDER BY createdAt DESC")
    fun getAllDocumentsIncludingDeleted(): Flow<List<StudentDocumentEntity>>

    @Query("SELECT * FROM student_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): StudentDocumentEntity?

    @Query("SELECT * FROM student_documents WHERE syncStatus = 'PENDING'")
    suspend fun getPendingDocuments(): List<StudentDocumentEntity>

    @Query("SELECT * FROM student_documents WHERE updatedAt > :timestamp")
    suspend fun getDocumentsModifiedSince(timestamp: Long): List<StudentDocumentEntity>

    @Query("UPDATE student_documents SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE student_documents SET isDeleted = 1, deletedAt = :timestamp, updatedAt = :timestamp, updatedBy = :userEmail, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun softDeleteDocument(id: String, timestamp: Long = System.currentTimeMillis(), userEmail: String = "")

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: StudentDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDocuments(docs: List<StudentDocumentEntity>)

    @Update
    suspend fun updateDocument(doc: StudentDocumentEntity)

    @Delete
    suspend fun deleteDocument(doc: StudentDocumentEntity)

    @Query("DELETE FROM student_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM student_documents WHERE studentId = :studentId")
    suspend fun deleteDocumentsForStudent(studentId: String)

    @Query("DELETE FROM student_documents")
    suspend fun deleteAllDocuments()
}
