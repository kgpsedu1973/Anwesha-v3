package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "student_documents",
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["documentType"]),
        Index(value = ["updatedAt"])
    ]
)
data class StudentDocumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val title: String,
    val documentType: String = "জন্ম নিবন্ধন সনদ",
    val fileUri: String,
    val fileType: String = "image/jpeg",
    val extractedText: String = "",
    val pageCount: Int = 1,
    val notes: String = "",
    val scanDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
) {
    companion object {
        val DOCUMENT_TYPES = listOf(
            "জন্ম নিবন্ধন সনদ",
            "ভর্তি ফরম",
            "জাতীয় পরিচয়পত্র (NID)",
            "প্রত্যয়ন / প্রশংসাপত্র",
            "টিকা কার্ড / স্বাস্থ্য সনদ",
            "অন্যান্য নথি"
        )
    }
}
