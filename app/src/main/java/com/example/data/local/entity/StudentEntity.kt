package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String, // e.g. "STU-2026-001"
    val studentClass: String, // e.g. "প্রাক-প্রাথমিক ৪+", "১ম শ্রেণি", "২য় শ্রেণি", etc.
    val section: String = "ক",
    val rollNumber: Int,
    val name: String, // শিক্ষার্থী নাম
    val parentContact: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val birthDate: String = "", // YYYY-MM-DD or DD/MM/YYYY
    val mobile: String = "",
    val village: String = "",
    val academicYear: String = "২০২৬", // e.g. "২০২৬"
    val address: String = "",
    val birthRegNumber: String = "",
    val gender: String = "ছাত্র", // "ছাত্র" / "ছাত্রী"
    val isSpecialNeeds: Boolean = false,
    val status: String = "Current", // "Current", "Former", "Transferred", "Inactive"
    val photoUri: String? = null,
    val customValuesJson: String = "{}", // JSON string of key-value custom field attributes
    val admissionDate: String = "", // ভর্তির তারিখ (YYYY-MM-DD)
    val lastModifiedDate: String = "", // সর্বশেষ পরিবর্তনের তারিখ (YYYY-MM-DD)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false
)
