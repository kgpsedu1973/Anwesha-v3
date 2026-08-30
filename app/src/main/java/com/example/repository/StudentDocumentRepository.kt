package com.example.repository

import com.example.data.local.dao.StudentDocumentDao
import com.example.data.local.entity.StudentDocumentEntity
import kotlinx.coroutines.flow.Flow

interface StudentDocumentRepository {
    fun getDocumentsForStudent(studentId: String): Flow<List<StudentDocumentEntity>>
    suspend fun getDocumentsForStudentList(studentId: String): List<StudentDocumentEntity>
    fun getAllDocuments(): Flow<List<StudentDocumentEntity>>
    suspend fun getDocumentById(id: String): StudentDocumentEntity?
    suspend fun insertDocument(doc: StudentDocumentEntity)
    suspend fun insertAllDocuments(docs: List<StudentDocumentEntity>)
    suspend fun updateDocument(doc: StudentDocumentEntity)
    suspend fun deleteDocument(doc: StudentDocumentEntity)
    suspend fun deleteDocumentById(id: String)
    suspend fun deleteDocumentsForStudent(studentId: String)
}

class StudentDocumentRepositoryImpl(
    private val dao: StudentDocumentDao
) : StudentDocumentRepository {

    override fun getDocumentsForStudent(studentId: String): Flow<List<StudentDocumentEntity>> {
        return dao.getDocumentsForStudent(studentId)
    }

    override suspend fun getDocumentsForStudentList(studentId: String): List<StudentDocumentEntity> {
        return dao.getDocumentsForStudentList(studentId)
    }

    override fun getAllDocuments(): Flow<List<StudentDocumentEntity>> {
        return dao.getAllDocuments()
    }

    override suspend fun getDocumentById(id: String): StudentDocumentEntity? {
        return dao.getDocumentById(id)
    }

    override suspend fun insertDocument(doc: StudentDocumentEntity) {
        val entity = doc.copy(
            createdAt = if (doc.createdAt == 0L) System.currentTimeMillis() else doc.createdAt,
            updatedAt = System.currentTimeMillis(),
            version = if (doc.version <= 0) 1 else doc.version,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        dao.insertDocument(entity)
    }

    override suspend fun insertAllDocuments(docs: List<StudentDocumentEntity>) {
        val entities = docs.map { doc ->
            doc.copy(
                createdAt = if (doc.createdAt == 0L) System.currentTimeMillis() else doc.createdAt,
                updatedAt = System.currentTimeMillis(),
                version = if (doc.version <= 0) 1 else doc.version,
                isDeleted = false,
                syncStatus = "SYNCED"
            )
        }
        dao.insertAllDocuments(entities)
    }

    override suspend fun updateDocument(doc: StudentDocumentEntity) {
        val entity = doc.copy(
            updatedAt = System.currentTimeMillis(),
            version = doc.version + 1,
            isDeleted = false,
            syncStatus = "SYNCED"
        )
        dao.updateDocument(entity)
    }

    override suspend fun deleteDocument(doc: StudentDocumentEntity) {
        val softDeleted = doc.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
        dao.insertDocument(softDeleted)
    }

    override suspend fun deleteDocumentById(id: String) {
        val doc = getDocumentById(id)
        if (doc != null) {
            deleteDocument(doc)
        } else {
            dao.softDeleteDocument(id, System.currentTimeMillis())
        }
    }

    override suspend fun deleteDocumentsForStudent(studentId: String) {
        val docs = dao.getDocumentsForStudentList(studentId)
        docs.forEach { deleteDocument(it) }
    }
}
