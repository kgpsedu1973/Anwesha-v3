package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

data class BaseDateConfig(
    val startDate: String = "2026-01-01",
    val endDate: String = "2026-12-31",
    val presetType: String = "YEAR_END", // "TODAY", "YEAR_START", "YEAR_END", "MONTH_START", "MONTH_END", "CUSTOM"
    val targetYear: Int = Calendar.getInstance().get(Calendar.YEAR)
)

object BaseDateManager {
    private const val PREFS_NAME = "anwesha_base_date_prefs"
    private const val KEY_START_DATE = "key_base_start_date"
    private const val KEY_END_DATE = "key_base_end_date"
    private const val KEY_PRESET_TYPE = "key_base_preset_type"
    private const val KEY_TARGET_YEAR = "key_base_target_year"

    private val banglaMonthNames = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    fun getTodayStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getYearStartStr(year: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
        return "$year-01-01"
    }

    fun getYearEndStr(year: Int = Calendar.getInstance().get(Calendar.YEAR)): String {
        return "$year-12-31"
    }

    fun getMonthStartStr(year: Int? = null, month1Indexed: Int? = null): String {
        val cal = Calendar.getInstance()
        val y = year ?: cal.get(Calendar.YEAR)
        val m = month1Indexed ?: (cal.get(Calendar.MONTH) + 1)
        return String.format(Locale.US, "%04d-%02d-01", y, m)
    }

    fun getMonthEndStr(year: Int? = null, month1Indexed: Int? = null): String {
        val cal = Calendar.getInstance()
        val y = year ?: cal.get(Calendar.YEAR)
        val m = (month1Indexed ?: (cal.get(Calendar.MONTH) + 1)) - 1 // 0-indexed for calendar
        val tempCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, maxDay)
    }

    fun computePresetDates(preset: String, targetYear: Int): Pair<String, String> {
        val cal = Calendar.getInstance()
        return when (preset.uppercase(Locale.ROOT)) {
            "TODAY" -> {
                val today = getTodayStr()
                Pair(today, today)
            }
            "YEAR_START" -> {
                val start = getYearStartStr(targetYear)
                Pair(start, start)
            }
            "YEAR_END" -> {
                val start = getYearStartStr(targetYear)
                val end = getYearEndStr(targetYear)
                Pair(start, end)
            }
            "MONTH_START" -> {
                val start = getMonthStartStr(targetYear, cal.get(Calendar.MONTH) + 1)
                Pair(start, start)
            }
            "MONTH_END" -> {
                val start = getMonthStartStr(targetYear, cal.get(Calendar.MONTH) + 1)
                val end = getMonthEndStr(targetYear, cal.get(Calendar.MONTH) + 1)
                Pair(start, end)
            }
            else -> {
                Pair(getYearStartStr(targetYear), getYearEndStr(targetYear))
            }
        }
    }

    fun formatDateBengali(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            val parts = dateStr.trim().split("-", "/", ".")
            if (parts.size == 3) {
                val (y, m, d) = if (parts[0].length == 4) {
                    Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                } else {
                    Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                }
                val dBn = BanglaUtils.toBanglaDigits(d)
                val mName = if (m in 1..12) banglaMonthNames[m - 1] else m.toString()
                val yBn = BanglaUtils.toBanglaDigits(y)
                "$dBn $mName $yBn"
            } else {
                BanglaUtils.toBanglaDigits(dateStr)
            }
        } catch (e: Exception) {
            BanglaUtils.toBanglaDigits(dateStr)
        }
    }

    fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("yyyy/MM/dd", Locale.US)
        )
        for (fmt in formats) {
            try {
                fmt.isLenient = false
                val d = fmt.parse(dateStr.trim())
                if (d != null) return d
            } catch (e: Exception) { }
        }
        return null
    }

    /**
     * Calculates the age of a person relative to the configured baseEndDate.
     */
    fun calculateAgeDetailed(
        birthDateStr: String?,
        baseEndDateStr: String?,
        outputFormat: String = "FULL" // "FULL", "YEARS_MONTHS", "YEARS_ONLY", "DIGIT_ONLY"
    ): String {
        if (birthDateStr.isNullOrBlank()) return ""
        try {
            val birthDate = parseDate(birthDateStr) ?: return ""
            val dob = Calendar.getInstance().apply { time = birthDate }

            val targetCal = Calendar.getInstance()
            val targetDate = if (!baseEndDateStr.isNullOrBlank()) parseDate(baseEndDateStr) else null
            if (targetDate != null) {
                targetCal.time = targetDate
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

    fun calculateAgeYearsInt(birthDateStr: String?, baseEndDateStr: String?): Int {
        if (birthDateStr.isNullOrBlank()) return 0
        return try {
            val birthDate = parseDate(birthDateStr) ?: return 0
            val dob = Calendar.getInstance().apply { time = birthDate }
            val targetCal = Calendar.getInstance()
            val targetDate = if (!baseEndDateStr.isNullOrBlank()) parseDate(baseEndDateStr) else null
            if (targetDate != null) {
                targetCal.time = targetDate
            }
            var age = targetCal.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (targetCal.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            if (age < 0) 0 else age
        } catch (e: Exception) {
            0
        }
    }

    fun saveConfig(context: Context, config: BaseDateConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_START_DATE, config.startDate)
            .putString(KEY_END_DATE, config.endDate)
            .putString(KEY_PRESET_TYPE, config.presetType)
            .putInt(KEY_TARGET_YEAR, config.targetYear)
            .apply()
    }

    fun getConfig(context: Context): BaseDateConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultYear = Calendar.getInstance().get(Calendar.YEAR)
        val start = prefs.getString(KEY_START_DATE, "$defaultYear-01-01") ?: "$defaultYear-01-01"
        val end = prefs.getString(KEY_END_DATE, "$defaultYear-12-31") ?: "$defaultYear-12-31"
        val preset = prefs.getString(KEY_PRESET_TYPE, "YEAR_END") ?: "YEAR_END"
        val targetYear = prefs.getInt(KEY_TARGET_YEAR, defaultYear)
        return BaseDateConfig(
            startDate = start,
            endDate = end,
            presetType = preset,
            targetYear = targetYear
        )
    }
}
