package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_fields")
data class CustomFieldEntity(
    @PrimaryKey val id: String,
    val name: String, // Field label in Bangla or English
    val fieldType: String, // "Text", "Number", "Date", "Phone", "Dropdown", "Yes/No", "Multiple choice", "Long text", "Image", "Calculated"
    val optionsJson: String? = null, // Comma separated options if Dropdown/Multiple choice
    val isCalculated: Boolean = false,
    val formulaRuleId: String? = null,
    val groupName: String = "কাস্টম তথ্য",
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false
) {
    val optionsList: List<String>
        get() = if (optionsJson.isNullOrBlank()) emptyList()
        else optionsJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "formula_rules")
data class FormulaRuleEntity(
    @PrimaryKey val id: String,
    val ruleName: String,
    val targetFieldName: String,
    val sourceField: String, // e.g. "village", "birthDate", "gender"
    val operator: String, // "EQUALS", "NOT_EQUALS", "CONTAINS", "IN_LIST", "GREATER_THAN", "LESS_THAN"
    val conditionValue: String, // e.g. "পশ্চিম রামপুর,আমতলী" or "2018"
    val resultIfTrue: String,
    val resultIfFalse: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = "",
    val version: Int = 1,
    val isDeleted: Boolean = false
)
