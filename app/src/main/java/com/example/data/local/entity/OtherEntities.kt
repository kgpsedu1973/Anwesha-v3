package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "attendance_records")
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val date: String, // YYYY-MM-DD
    val className: String, // e.g., "১ম শ্রেণি" or "ALL"
    val presentBoys: Int,
    val presentGirls: Int,
    val absentBoys: Int,
    val absentGirls: Int,
    val totalBoys: Int,
    val totalGirls: Int,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "routine_items")
data class RoutineItemEntity(
    @PrimaryKey val id: String,
    val routineType: String, // "Class Routine", "Exam Routine"
    val className: String,
    val subject: String,
    val teacher: String,
    val day: String, // e.g. "শনিবার", "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার"
    val startTime: String, // e.g. "09:00 AM"
    val endTime: String, // e.g. "09:45 AM"
    val periodName: String, // e.g. "১ম পিরিয়ড"
    val roomNo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "document_templates")
data class DocumentTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val contentTemplate: String, // template text containing placeholders like {শিক্ষার্থীর নাম}, {রোল}, {শ্রেণি}, etc.
    val createdDate: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "surveys")
data class SurveyEntity(
    @PrimaryKey val id: String,
    val studentId: String?,
    val surveyYear: String,
    val age: Int,
    val educationStatus: String, // e.g. "অধ্যায়নরত", "ঝরে পড়া", "ভর্তি হয়নি"
    val schoolName: String,
    val className: String,
    val gender: String,
    val isSpecialNeeds: Boolean,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "school_info")
data class SchoolInfoEntity(
    @PrimaryKey val id: Int = 1,
    val schoolName: String,
    val address: String = "",
    val eiinCode: String = "123456",
    val logoUri: String? = null,
    val phone: String = "",
    val email: String = "",
    val headTeacherName: String = "",
    val adminName: String = "",
    val adminEmail: String = "",
    val adminPhone: String = "",
    val createdDate: String = "",
    val tagline: String = "জ্ঞান, মনন ও স্বপ্নের সোপান",
    val internalVillages: String = "রামপুর,কৃষ্ণপুর,আমতলী",
    val customSchoolInfoJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1
) {
    val emisCode: String get() = eiinCode
}

data class SchoolCustomInfoItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String
)

object SchoolCustomInfoHelper {
    fun parse(jsonStr: String?): List<SchoolCustomInfoItem> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<SchoolCustomInfoItem>()
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SchoolCustomInfoItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        key = obj.optString("key", ""),
                        value = obj.optString("value", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toJson(items: List<SchoolCustomInfoItem>): String {
        val array = org.json.JSONArray()
        items.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("key", item.key)
            obj.put("value", item.value)
            array.put(obj)
        }
        return array.toString()
    }
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val role: String = "Admin", // "Admin", "Teacher", "Parent"
    val status: String = "Active", // "Active", "Pending"
    val securityPinHash: String = "",
    val createdDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "exam_results")
data class ExamResultEntity(
    @PrimaryKey val id: String,
    val examName: String,
    val className: String,
    val section: String = "ক",
    val rollNumber: Int,
    val studentId: String? = null,
    val studentName: String = "",
    val subject: String,
    val marks: Double,
    val grade: String = "A+",
    val gpa: Double = 5.0,
    val date: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: String = "SYNCED"
)
