package com.example.data.local.util

import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.StudentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FormulaEvaluator {

    fun evaluateRule(student: StudentEntity, rule: FormulaRuleEntity): String {
        val sourceValue = getSourceFieldValue(student, rule.sourceField)
        val conditionMet = checkCondition(sourceValue, rule.operator, rule.conditionValue)
        return if (conditionMet) rule.resultIfTrue else rule.resultIfFalse
    }

    private fun getSourceFieldValue(student: StudentEntity, sourceField: String): String {
        return when (sourceField.lowercase(Locale.ROOT)) {
            "village", "গ্রাম" -> student.village
            "gender", "লিঙ্গ" -> student.gender
            "studentclass", "class", "শ্রেণি" -> student.studentClass
            "status", "স্ট্যাটাস" -> student.status
            "isspecialneeds", "বিশেষ চাহিদা" -> if (student.isSpecialNeeds) "হ্যাঁ" else "না"
            "birthdate", "জন্মতারিখ" -> student.birthDate
            "age", "বয়স" -> calculateAge(student.birthDate).toString()
            else -> {
                // Check if in custom values JSON
                extractCustomValue(student.customValuesJson, sourceField) ?: ""
            }
        }
    }

    fun calculateAge(birthDateStr: String): Int {
        if (birthDateStr.isBlank()) return 0
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date: Date = format.parse(birthDateStr) ?: return 0
            val dob = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            if (age < 0) 0 else age
        } catch (e: Exception) {
            0
        }
    }

    private fun checkCondition(sourceValue: String, operator: String, conditionValue: String): Boolean {
        val src = sourceValue.trim()
        val cond = conditionValue.trim()
        return when (operator.uppercase(Locale.ROOT)) {
            "EQUALS" -> src.equals(cond, ignoreCase = true)
            "NOT_EQUALS" -> !src.equals(cond, ignoreCase = true)
            "CONTAINS" -> src.contains(cond, ignoreCase = true)
            "IN_LIST" -> {
                val list = cond.split(",").map { it.trim() }
                list.any { it.equals(src, ignoreCase = true) }
            }
            "GREATER_THAN" -> {
                val numSrc = src.toDoubleOrNull()
                val numCond = cond.toDoubleOrNull()
                if (numSrc != null && numCond != null) numSrc > numCond else src > cond
            }
            "LESS_THAN" -> {
                val numSrc = src.toDoubleOrNull()
                val numCond = cond.toDoubleOrNull()
                if (numSrc != null && numCond != null) numSrc < numCond else src < cond
            }
            else -> false
        }
    }

    private fun extractCustomValue(jsonStr: String, fieldId: String): String? {
        // Simple regex/substring JSON parser for {"key":"val"} to avoid complex dependency setup
        return try {
            val keyPattern = "\"${fieldId}\"\\s*:\\s*\"([^\"]+)\""
            val matcher = Regex(keyPattern).find(jsonStr)
            matcher?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    fun buildCustomValuesJson(map: Map<String, String>): String {
        val entries = map.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }
        return "{$entries}"
    }

    fun parseCustomValuesJson(jsonStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (jsonStr.isBlank() || jsonStr == "{}") return result
        try {
            val pattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
            pattern.findAll(jsonStr).forEach { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                result[key] = value
            }
        } catch (e: Exception) {
            // fallback
        }
        return result
    }
}
