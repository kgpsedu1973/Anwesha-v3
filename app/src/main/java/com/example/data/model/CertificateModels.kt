package com.example.data.model

import com.example.util.BanglaUtils

/**
 * Data structures for Certificate / Testimonial Maker (প্রত্যয়নপত্র ও প্রশংসাপত্র মেকার)
 */
data class CertificateStudent(
    val id: String,
    val name: String,
    val studentClass: String,
    val rollNumber: String,
    val fatherName: String = "",
    val motherName: String = "",
    val birthDate: String = "",
    val academicYear: String = "২০২৬",
    val gender: String = "ছাত্র",
    val customSerial: String? = null
) {
    /**
     * Formats Date of Birth into clean Bangla digits (e.g. ১০/০৩/২০১২)
     */
    fun getFormattedDobBangla(): String {
        if (birthDate.isBlank()) return "—"
        val clean = birthDate.trim()
        if (clean.contains("-")) {
            val parts = clean.split("-")
            if (parts.size == 3) {
                return if (parts[0].length == 4) {
                    // yyyy-MM-dd
                    "${BanglaUtils.toBanglaDigits(parts[2])}/${BanglaUtils.toBanglaDigits(parts[1])}/${BanglaUtils.toBanglaDigits(parts[0])}"
                } else {
                    // dd-MM-yyyy
                    "${BanglaUtils.toBanglaDigits(parts[0])}/${BanglaUtils.toBanglaDigits(parts[1])}/${BanglaUtils.toBanglaDigits(parts[2])}"
                }
            }
        }
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size == 3) {
                return "${BanglaUtils.toBanglaDigits(parts[0])}/${BanglaUtils.toBanglaDigits(parts[1])}/${BanglaUtils.toBanglaDigits(parts[2])}"
            }
        }
        return BanglaUtils.toBanglaDigits(clean)
    }

    /**
     * Clean class name for sentence flow (e.g. "পঞ্চম", "৫ম", etc.)
     */
    fun getCleanClassForSentence(): String {
        val c = studentClass.trim()
        if (c.isBlank() || c.equals("Hold", ignoreCase = true) || c.equals("null", ignoreCase = true)) {
            return "৫ম"
        }
        return when {
            c.contains("প্রাক-প্রাথমিক ৪+") || c.contains("প্রাক-প্রাথমিক 4+") -> "প্রাক-প্রাথমিক (৪+)"
            c.contains("প্রাক-প্রাথমিক ৫+") || c.contains("প্রাক-প্রাথমিক 5+") -> "প্রাক-প্রাথমিক (৫+)"
            c.contains("প্রাক-প্রাথমিক") || c.contains("শিশু") -> "প্রাক-প্রাথমিক"
            c.contains("প্রথম") || c.contains("১ম") || c.contains("1st") -> "১ম"
            c.contains("দ্বিতীয়") || c.contains("দ্বিতীয়") || c.contains("২য়") || c.contains("2nd") -> "২য়"
            c.contains("তৃতীয়") || c.contains("তৃতীয়") || c.contains("৩য়") || c.contains("3rd") -> "৩য়"
            c.contains("চতুর্থ") || c.contains("৪র্থ") || c.contains("4th") -> "৪র্থ"
            c.contains("পঞ্চম") || c.contains("৫ম") || c.contains("5th") -> "৫ম"
            c.contains("ষষ্ঠ") || c.contains("৬ষ্ঠ") -> "৬ষ্ঠ"
            c.contains("সপ্তম") || c.contains("৭ম") -> "৭ম"
            c.contains("অষ্টম") || c.contains("৮ম") -> "৮ম"
            c.endsWith("শ্রেণি") -> c.removeSuffix("শ্রেণি").trim()
            else -> c
        }
    }
}

data class CertificateMakerState(
    // School information
    val schoolName: String = "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়",
    val upazila: String = "আলফাডাঙ্গা",
    val district: String = "ফরিদপুর",
    val estYear: String = "১৯৭৩",

    // Government and Ministry Headers
    val govtHeader1: String = "গণপ্রজাতন্ত্রী বাংলাদেশ সরকার",
    val govtHeader2: String = "প্রাথমিক শিক্ষা অধিদপ্তর",
    val govtHeader3: String = "প্রাথমিক ও গণশিক্ষা মন্ত্রণালয়",

    // Certificate Title & Type
    val certificateTitle: String = "প্রত্যয়নপত্র", // "প্রত্যয়নপত্র", "প্রশংসাপত্র", "চারিত্রিক সনদপত্র"

    // Issue Date & Academic Session
    val issueDate: String = "", // e.g. "2026-08-29"
    val sessionYear: String = "২০২৬",

    // Serial Number Generation Format
    // "YEAR_CLASS_ROLL" (e.g. 20260512 -> ২০২৬০৫১২)
    // "CUSTOM_PREFIX_ROLL" (e.g. PR-2026-0012)
    // "AUTO_INCREMENT" (e.g. 001, 002)
    // "MANUAL"
    val serialFormatMode: String = "YEAR_CLASS_ROLL",
    val customSerialPrefix: String = "PR-2026-",
    val autoIncrementStart: Int = 1,
    val manualSerialMap: Map<String, String> = emptyMap(),

    // Certificate Body Text Options
    val studyTense: String = "PAST", // "PAST" -> "অধ্যয়ন করেছে", "PRESENT" -> "অধ্যয়ন করছে"
    val characterRemark: String = "তার স্বভাব চরিত্র ভালো।",
    val wishRemark: String = "আমি তার সর্বাঙ্গীণ সাফল্য কামনা করি।",

    // Signatures and designations
    val headTeacherTitle: String = "(প্রধান শিক্ষক)",
    val showHeadTeacherSignature: Boolean = true,
    val headTeacherSignatureBase64: String = "",
    val showStudentSignatureBox: Boolean = true,
    val showIssuerSignatureBox: Boolean = true,

    // Layout, Styling & Page Settings
    val pageSize: String = "Legal", // "Legal" (Default), "A4", "Letter"
    val orientation: String = "landscape", // "landscape" (Default), "portrait"
    val marginLeftInch: Float = 0.25f,
    val marginRightInch: Float = 0.25f,
    val marginTopInch: Float = 0.25f,
    val marginBottomInch: Float = 0.25f,
    val borderStyle: String = "ornate", // "ornate", "double", "classic", "solid"
    val showWatermark: Boolean = true,
    val showGovtEmblems: Boolean = true,
    val showCounterfoil: Boolean = true, // Left Office Stub
    val fontStyle: String = "serif", // "serif", "sans"

    // Student Scope and Filter Selection
    val scope: String = "all", // "all", "class", "student"
    val selectedClasses: List<String> = emptyList(),
    val selectedStudentIds: List<String> = emptyList()
) {
    /**
     * Compute exact Serial Number for a given student according to configured format
     */
    fun computeSerialForStudent(student: CertificateStudent, index: Int = 0): String {
        val manual = manualSerialMap[student.id] ?: student.customSerial
        if (!manual.isNullOrBlank()) {
            return manual
        }

        return when (serialFormatMode) {
            "YEAR_CLASS_ROLL" -> {
                // Year 4 digits
                val yEng = BanglaUtils.toEnglishDigits(sessionYear).filter { it.isDigit() }.takeLast(4).ifBlank { "2026" }
                // Class 2 digits (e.g. Class 5 -> 05, Class 1 -> 01, Pre-primary -> 00)
                val cCode = getClassTwoDigitCode(student.studentClass)
                // Roll 2 digits
                val rNum = BanglaUtils.toEnglishDigits(student.rollNumber).filter { it.isDigit() }.toIntOrNull() ?: (index + 1)
                val rCode = String.format("%02d", rNum)
                val combined = "$yEng$cCode$rCode"
                BanglaUtils.toBanglaDigits(combined)
            }
            "CUSTOM_PREFIX_ROLL" -> {
                val rNum = BanglaUtils.toEnglishDigits(student.rollNumber).filter { it.isDigit() }.toIntOrNull() ?: (index + 1)
                val rCode = String.format("%02d", rNum)
                val prefix = customSerialPrefix.ifBlank { "PR-" }
                "$prefix$rCode"
            }
            "AUTO_INCREMENT" -> {
                val seq = autoIncrementStart + index
                val formatted = String.format("%03d", seq)
                BanglaUtils.toBanglaDigits(formatted)
            }
            else -> {
                student.rollNumber.ifBlank { BanglaUtils.toBanglaDigits(index + 1) }
            }
        }
    }

    companion object {
        fun getClassTwoDigitCode(className: String): String {
            val c = className.trim()
            return when {
                c.contains("প্রাক-প্রাথমিক ৪+") || c.contains("প্রাক-প্রাথমিক 4+") || c.contains("প্রাক ৪+") -> "00"
                c.contains("প্রাক-প্রাথমিক ৫+") || c.contains("প্রাক-প্রাথমিক 5+") || c.contains("প্রাক ৫+") -> "00"
                c.contains("প্রাক-প্রাথমিক") || c.contains("শিশু") -> "00"
                c.contains("প্রথম") || c.contains("১ম") || c.contains("1st") -> "01"
                c.contains("দ্বিতীয়") || c.contains("দ্বিতীয়") || c.contains("২য়") || c.contains("2nd") -> "02"
                c.contains("তৃতীয়") || c.contains("তৃতীয়") || c.contains("৩য়") || c.contains("3rd") -> "03"
                c.contains("চতুর্থ") || c.contains("৪র্থ") || c.contains("4th") -> "04"
                c.contains("পঞ্চম") || c.contains("৫ম") || c.contains("5th") -> "05"
                c.contains("ষষ্ঠ") || c.contains("৬ষ্ঠ") -> "06"
                c.contains("সপ্তম") || c.contains("৭ম") -> "07"
                c.contains("অষ্টম") || c.contains("৮ম") -> "08"
                else -> {
                    val d = BanglaUtils.toEnglishDigits(c).filter { it.isDigit() }
                    if (d.isNotBlank()) String.format("%02d", d.toInt().coerceIn(0, 99)) else "01"
                }
            }
        }
    }
}
