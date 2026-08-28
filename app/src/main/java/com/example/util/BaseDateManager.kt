package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

data class BaseDateConfig(
    val baseDate: String = "2026-12-31",
    val startDate: String = "2026-01-01",
    val endDate: String = "2026-12-31",
    val presetType: String = "YEAR_END", // "YEAR_END", "YEAR_START", "TODAY", "MONTH_END", "MONTH_START", "CUSTOM"
    val targetYear: Int = Calendar.getInstance().get(Calendar.YEAR)
)

object BaseDateManager {
    private const val PREFS_NAME = "anwesha_base_date_prefs"
    private const val KEY_BASE_DATE = "key_base_date"
    private const val KEY_START_DATE = "key_base_start_date"
    private const val KEY_END_DATE = "key_base_end_date"
    private const val KEY_PRESET_TYPE = "key_base_preset_type"
    private const val KEY_TARGET_YEAR = "key_base_target_year"

    private val banglaMonthNames = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private val banglaDayNames = arrayOf(
        "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"
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
        val m = (month1Indexed ?: (cal.get(Calendar.MONTH) + 1)) - 1
        val tempCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, maxDay)
    }

    fun computePresetDate(preset: String, targetYear: Int): String {
        val cal = Calendar.getInstance()
        return when (preset.uppercase(Locale.ROOT)) {
            "TODAY" -> getTodayStr()
            "YEAR_START" -> getYearStartStr(targetYear)
            "YEAR_END" -> getYearEndStr(targetYear)
            "MONTH_START" -> getMonthStartStr(targetYear, cal.get(Calendar.MONTH) + 1)
            "MONTH_END" -> getMonthEndStr(targetYear, cal.get(Calendar.MONTH) + 1)
            else -> getYearEndStr(targetYear)
        }
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

    fun getDayOfWeekBengali(dateStr: String?): String {
        val d = parseDate(dateStr) ?: return ""
        val cal = Calendar.getInstance().apply { time = d }
        val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sunday
        return if (dayIndex in 0..6) banglaDayNames[dayIndex] else ""
    }

    fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
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
                val d = fmt.parse(dateStr.trim())
                if (d != null) return d
            } catch (e: Exception) { }
        }
        return null
    }

    /**
     * Calculates the age in years as an integer relative to baseDate.
     */
    fun calculateAgeYearsInt(birthDateStr: String?, baseDateStr: String?): Int {
        if (birthDateStr.isNullOrBlank()) return 0
        return try {
            val birthDate = parseDate(birthDateStr) ?: return 0
            val dob = Calendar.getInstance().apply { time = birthDate }
            val targetCal = Calendar.getInstance()
            val targetDate = if (!baseDateStr.isNullOrBlank()) parseDate(baseDateStr) else null
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

    /**
     * Returns student age string formatted as ONLY YEARS (e.g. "৭ বছর" or "১০ বছর")
     * as requested for student profile and throughout the student database.
     */
    fun getStudentAgeYearsFormatted(birthDateStr: String?, baseDateStr: String?): String {
        if (birthDateStr.isNullOrBlank()) return ""
        val years = calculateAgeYearsInt(birthDateStr, baseDateStr)
        return "${BanglaUtils.toBanglaDigits(years)} বছর"
    }

    /**
     * Detailed Age calculation with Day Inclusion support for Age Calculator tool.
     */
    data class DetailedAgeResult(
        val years: Int,
        val months: Int,
        val days: Int,
        val totalDays: Long,
        val totalWeeks: Long,
        val remainingDaysInWeek: Long,
        val totalMonthsApprox: Long,
        val remainingDaysInMonth: Long,
        val totalHoursApprox: Long,
        val nextBirthdayDateStr: String,
        val nextBirthdayBengali: String,
        val nextBirthdayDayOfWeek: String,
        val daysUntilNextBirthday: Long,
        val isValid: Boolean = true
    )

    fun calculateFullAgeWithInclusion(
        startDateStr: String?,
        endDateStr: String?,
        includeStartDay: Boolean = false,
        includeEndDay: Boolean = false
    ): DetailedAgeResult {
        val start = parseDate(startDateStr)
        val end = parseDate(endDateStr)

        if (start == null || end == null) {
            return DetailedAgeResult(0, 0, 0, 0, 0, 0, 0, 0, 0, "", "", "", 0, isValid = false)
        }

        val startCal = Calendar.getInstance().apply { time = start }
        val endCal = Calendar.getInstance().apply { time = end }

        if (endCal.before(startCal)) {
            return DetailedAgeResult(0, 0, 0, 0, 0, 0, 0, 0, 0, "", "", "", 0, isValid = false)
        }

        // Days adjustment based on inclusion options
        var extraDays = 0
        if (includeStartDay) extraDays += 1
        if (includeEndDay) extraDays += 0 // Normal diff already counts up to end date, extraDay if both

        val diffMillis = endCal.timeInMillis - startCal.timeInMillis
        var totalDays = (diffMillis / (1000L * 60 * 60 * 24))
        if (includeStartDay) totalDays += 1
        if (includeEndDay && !includeStartDay) totalDays += 1 // if user wants boundary inclusive

        var years = endCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
        var months = endCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)
        var days = endCal.get(Calendar.DAY_OF_MONTH) - startCal.get(Calendar.DAY_OF_MONTH)

        if (includeStartDay) {
            days += 1
        }

        if (days < 0) {
            months--
            val prevMonthCal = (endCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }

        val tempMaxDays = endCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (days >= tempMaxDays) {
            days -= tempMaxDays
            months++
        }

        if (months < 0) {
            years--
            months += 12
        }

        if (months >= 12) {
            years += (months / 12)
            months %= 12
        }

        if (years < 0) {
            years = 0
            months = 0
            days = 0
        }

        val totalWeeks = totalDays / 7
        val remDaysWeek = totalDays % 7
        val totalMonthsApprox = (years * 12L) + months
        val totalHoursApprox = totalDays * 24

        // Next Birthday calculation
        val todayCal = Calendar.getInstance()
        val nextBdayCal = Calendar.getInstance().apply {
            time = start
            set(Calendar.YEAR, endCal.get(Calendar.YEAR))
        }
        if (nextBdayCal.before(endCal) || nextBdayCal.equals(endCal)) {
            nextBdayCal.add(Calendar.YEAR, 1)
        }
        val diffBdayMillis = nextBdayCal.timeInMillis - endCal.timeInMillis
        val daysUntilNextBirthday = (diffBdayMillis / (1000L * 60 * 60 * 24)).coerceAtLeast(0)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val nextBdayStr = sdf.format(nextBdayCal.time)
        val nextBdayBn = formatDateBengali(nextBdayStr)
        val nextBdayDayOfWeek = getDayOfWeekBengali(nextBdayStr)

        return DetailedAgeResult(
            years = years,
            months = months,
            days = days,
            totalDays = totalDays,
            totalWeeks = totalWeeks,
            remainingDaysInWeek = remDaysWeek,
            totalMonthsApprox = totalMonthsApprox,
            remainingDaysInMonth = days.toLong(),
            totalHoursApprox = totalHoursApprox,
            nextBirthdayDateStr = nextBdayStr,
            nextBirthdayBengali = nextBdayBn,
            nextBirthdayDayOfWeek = nextBdayDayOfWeek,
            daysUntilNextBirthday = daysUntilNextBirthday,
            isValid = true
        )
    }

    /**
     * Legacy helper used in some components
     */
    fun calculateAgeDetailed(
        birthDateStr: String?,
        baseEndDateStr: String?,
        outputFormat: String = "YEARS_ONLY" // Defaults to YEARS_ONLY as per user directive
    ): String {
        if (birthDateStr.isNullOrBlank()) return ""
        val years = calculateAgeYearsInt(birthDateStr, baseEndDateStr)
        val yBn = BanglaUtils.toBanglaDigits(years)
        return when (outputFormat.uppercase(Locale.ROOT)) {
            "DIGIT_ONLY" -> yBn
            "YEARS_ONLY" -> "$yBn বছর"
            else -> "$yBn বছর"
        }
    }

    fun saveConfig(context: Context, config: BaseDateConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val finalBase = if (config.baseDate.isNotBlank()) config.baseDate else config.endDate
        prefs.edit()
            .putString(KEY_BASE_DATE, finalBase)
            .putString(KEY_START_DATE, config.startDate)
            .putString(KEY_END_DATE, finalBase)
            .putString(KEY_PRESET_TYPE, config.presetType)
            .putInt(KEY_TARGET_YEAR, config.targetYear)
            .apply()
    }

    fun getConfig(context: Context): BaseDateConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultYear = Calendar.getInstance().get(Calendar.YEAR)
        val end = prefs.getString(KEY_BASE_DATE, null)
            ?: prefs.getString(KEY_END_DATE, "$defaultYear-12-31")
            ?: "$defaultYear-12-31"
        val start = prefs.getString(KEY_START_DATE, "$defaultYear-01-01") ?: "$defaultYear-01-01"
        val preset = prefs.getString(KEY_PRESET_TYPE, "YEAR_END") ?: "YEAR_END"
        val targetYear = prefs.getInt(KEY_TARGET_YEAR, defaultYear)
        return BaseDateConfig(
            baseDate = end,
            startDate = start,
            endDate = end,
            presetType = preset,
            targetYear = targetYear
        )
    }
}
