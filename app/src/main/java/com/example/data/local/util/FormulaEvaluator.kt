package com.example.data.local.util

import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.StudentEntity
import com.example.util.BanglaUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FormulaEvaluator {

    /**
     * Extracts or calculates the string value for any field key on a StudentEntity.
     * Supports:
     * - Standard fields: "name", "studentClass", "rollNumber", "id", "gender", "fatherName", "motherName",
     *   "mobile", "village", "address", "academicYear", "birthDate", "age", "birthRegNumber", "isSpecialNeeds",
     *   "category", "status"
     * - Custom fields (direct or calculated)
     * - Formula rules evaluation
     */
    fun getFieldValue(
        student: StudentEntity,
        fieldKey: String,
        customFields: List<CustomFieldEntity> = emptyList(),
        formulaRules: List<FormulaRuleEntity> = emptyList(),
        schoolInternalVillages: List<String> = listOf("পশ্চিম রামপুর", "আমতলী", "কৃষ্ণপুর")
    ): String {
        return when (fieldKey.lowercase(Locale.ROOT)) {
            "name", "নাম", "শিক্ষার্থীর নাম" -> student.name
            "studentclass", "class", "শ্রেণি", "শ্রেণী" -> student.studentClass
            "rollnumber", "roll", "রোল", "রোল নম্বর" -> BanglaUtils.toBanglaDigits(student.rollNumber)
            "id", "student_id", "শিক্ষার্থী আইডি", "আইডি" -> student.id
            "gender", "লিঙ্গ" -> student.gender
            "fathername", "father", "পিতার নাম", "পিতা" -> student.fatherName
            "mothername", "mother", "মাতার নাম", "মাতা" -> student.motherName
            "mobile", "phone", "মোবাইল", "ফোন" -> student.mobile
            "village", "গ্রাম" -> student.village
            "address", "ঠিকানা" -> student.address
            "academicyear", "year", "শিক্ষাবর্ষ", "বছর" -> student.academicYear
            "birthdate", "dob", "জন্মতারিখ" -> student.birthDate
            "age", "বয়স", "বয়স" -> calculateAge(student.birthDate).toString()
            "birthregnumber", "birth_reg", "জন্ম নিবন্ধন নম্বর" -> student.birthRegNumber
            "isspecialneeds", "বিশেষ চাহিদা" -> if (student.isSpecialNeeds) "হ্যাঁ" else "না"
            "status", "স্ট্যাটাস" -> when (student.status) {
                "Current" -> "বর্তমান"
                "Former" -> "সাবেক"
                "Transferred" -> "বদলীকৃত"
                "Inactive" -> "নিষ্ক্রিয়"
                else -> student.status
            }
            "category", "ধরণ", "শিক্ষার্থীর ধরণ" -> {
                val rule = formulaRules.firstOrNull { it.targetFieldName == "শিক্ষার্থীর ধরণ" || it.sourceField == "village" }
                if (rule != null) {
                    evaluateRule(student, rule, customFields)
                } else {
                    if (schoolInternalVillages.any { it.equals(student.village, ignoreCase = true) }) "অভ্যন্তরীণ" else "বহিরাগত"
                }
            }
            else -> {
                // Check if it's a custom field ID (or prefixed with cf_)
                val cleanKey = fieldKey.removePrefix("cf_")
                val matchedCustomField = customFields.find { it.id == cleanKey || it.name.equals(fieldKey, ignoreCase = true) }
                
                if (matchedCustomField != null) {
                    if (matchedCustomField.isCalculated) {
                        // Evaluate associated formula rule or expression
                        val rule = formulaRules.find { it.id == matchedCustomField.formulaRuleId || it.targetFieldName.equals(matchedCustomField.name, ignoreCase = true) }
                        if (rule != null) {
                            return evaluateRule(student, rule, customFields)
                        }
                        // Check if value already computed in customValuesJson
                        val raw = extractCustomValue(student.customValuesJson, matchedCustomField.id)
                        return raw ?: ""
                    } else {
                        return extractCustomValue(student.customValuesJson, matchedCustomField.id) ?: ""
                    }
                }

                // Check general formula rules matching fieldKey
                val generalRule = formulaRules.find { it.targetFieldName.equals(fieldKey, ignoreCase = true) || it.targetFieldName.equals(cleanKey, ignoreCase = true) }
                if (generalRule != null) {
                    return evaluateRule(student, generalRule, customFields)
                }

                // Fallback: extract directly from custom JSON
                extractCustomValue(student.customValuesJson, cleanKey) ?: ""
            }
        }
    }

    fun evaluateRule(
        student: StudentEntity,
        rule: FormulaRuleEntity,
        customFields: List<CustomFieldEntity> = emptyList()
    ): String {
        val sourceValue = getFieldValue(student, rule.sourceField, customFields)
        val conditionMet = checkCondition(sourceValue, rule.operator, rule.conditionValue)
        return if (conditionMet) {
            evaluateResultExpression(rule.resultIfTrue, student, customFields)
        } else {
            evaluateResultExpression(rule.resultIfFalse, student, customFields)
        }
    }

    private fun evaluateResultExpression(
        templateOrVal: String,
        student: StudentEntity,
        customFields: List<CustomFieldEntity>
    ): String {
        var output = templateOrVal
        // Support dynamic placeholders like {age}, {studentClass}, {rollNumber}, {name}
        if (output.contains("{") && output.contains("}")) {
            val placeholderRegex = Regex("\\{([^}]+)\\}")
            output = placeholderRegex.replace(output) { matchResult ->
                val field = matchResult.groupValues[1]
                getFieldValue(student, field, customFields)
            }
        }
        return output
    }

    fun calculateAge(birthDateStr: String): Int {
        if (birthDateStr.isBlank()) return 0
        return try {
            val date: Date = parseFlexibleDate(birthDateStr) ?: return 0
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

    /**
     * Calculates detailed age with a configurable base date option:
     * - TODAY: calculates relative to today
     * - CUSTOM: e.g. "2026-08-31" (or specific base date)
     * - YEAR_START: January 1 of the student's academic year
     * - YEAR_END: December 31 of the student's academic year
     */
    fun calculateAgeDetailed(
        birthDateStr: String,
        baseDateOption: String = "TODAY",
        customBaseDateStr: String? = null,
        academicYear: String? = null,
        outputFormat: String = "FULL" // "FULL", "YEARS_MONTHS", "YEARS_ONLY", "DIGIT_ONLY"
    ): String {
        if (birthDateStr.isBlank()) return ""
        try {
            val birthDate = parseFlexibleDate(birthDateStr) ?: return ""
            val dob = Calendar.getInstance().apply { time = birthDate }

            val targetCal = Calendar.getInstance()
            when (baseDateOption.uppercase(Locale.ROOT)) {
                "TODAY" -> {
                    targetCal.time = Date()
                }
                "CUSTOM" -> {
                    val customDate = if (!customBaseDateStr.isNullOrBlank()) parseFlexibleDate(customBaseDateStr) else null
                    if (customDate != null) {
                        targetCal.time = customDate
                    }
                }
                "YEAR_START" -> {
                    val yr = academicYear?.filter { it.isDigit() }?.toIntOrNull() ?: targetCal.get(Calendar.YEAR)
                    targetCal.set(yr, Calendar.JANUARY, 1)
                }
                "YEAR_END" -> {
                    val yr = academicYear?.filter { it.isDigit() }?.toIntOrNull() ?: targetCal.get(Calendar.YEAR)
                    targetCal.set(yr, Calendar.DECEMBER, 31)
                }
                else -> {
                    // Try parsing baseDateOption directly as a date
                    val parsed = parseFlexibleDate(baseDateOption)
                    if (parsed != null) {
                        targetCal.time = parsed
                    }
                }
            }

            if (targetCal.before(dob)) {
                return "০ দিন"
            }

            var years = targetCal.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            var months = targetCal.get(Calendar.MONTH) - dob.get(Calendar.MONTH)
            var days = targetCal.get(Calendar.DAY_OF_MONTH) - dob.get(Calendar.DAY_OF_MONTH)

            if (days < 0) {
                months--
                val prevMonthCal = (targetCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                days += prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            }

            if (months < 0) {
                years--
                months += 12
            }

            if (years < 0) {
                years = 0
                months = 0
                days = 0
            }

            val yBn = BanglaUtils.toBanglaDigits(years)
            val mBn = BanglaUtils.toBanglaDigits(months)
            val dBn = BanglaUtils.toBanglaDigits(days)

            return when (outputFormat.uppercase(Locale.ROOT)) {
                "DIGIT_ONLY" -> yBn
                "YEARS_ONLY" -> "$yBn বছর"
                "YEARS_MONTHS" -> {
                    if (months > 0) "$yBn বছর $mBn মাস" else "$yBn বছর"
                }
                "FULL" -> {
                    buildString {
                        append("$yBn বছর")
                        if (months > 0) append(" $mBn মাস")
                        if (days > 0 || (years == 0 && months == 0)) append(" $dBn দিন")
                    }
                }
                else -> "$yBn বছর $mBn মাস $dBn দিন"
            }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun parseFlexibleDate(dateStr: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("yyyy/MM/dd", Locale.US),
            SimpleDateFormat("d MMMM yyyy", Locale.US),
            SimpleDateFormat("d MMM yyyy", Locale.US)
        )
        for (fmt in formats) {
            try {
                fmt.isLenient = false
                val parsed = fmt.parse(dateStr.trim())
                if (parsed != null) return parsed
            } catch (e: Exception) {
                // try next
            }
        }
        return null
    }

    fun checkCondition(sourceValue: String, operator: String, conditionValue: String): Boolean {
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
            "STARTS_WITH" -> src.startsWith(cond, ignoreCase = true)
            "ENDS_WITH" -> src.endsWith(cond, ignoreCase = true)
            "IS_EMPTY" -> src.isEmpty()
            "IS_NOT_EMPTY" -> src.isNotEmpty()
            else -> false
        }
    }

    private fun extractCustomValue(jsonStr: String, fieldId: String): String? {
        if (jsonStr.isBlank() || jsonStr == "{}") return null
        return try {
            val keyPattern = "\"${Regex.escape(fieldId)}\"\\s*:\\s*\"([^\"]*)\""
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
