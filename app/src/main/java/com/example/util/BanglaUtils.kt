package com.example.util

import java.text.SimpleDateFormat
import java.util.*

object BanglaUtils {

    private val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    private val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

    fun toBanglaDigits(number: Any?): String {
        if (number == null) return ""
        val str = number.toString()
        val sb = StringBuilder()
        for (ch in str) {
            if (ch in '0'..'9') {
                sb.append(banglaDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toEnglishDigits(str: String?): String {
        if (str == null) return ""
        val sb = StringBuilder()
        for (ch in str) {
            val idx = banglaDigits.indexOf(ch)
            if (idx != -1) {
                sb.append(englishDigits[idx])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatBanglaDate(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance()
            cal.time = date
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH)
            val year = cal.get(Calendar.YEAR)

            val monthNames = arrayOf(
                "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
                "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
            )
            "${toBanglaDigits(day)} ${monthNames[month]}, ${toBanglaDigits(year)}"
        } catch (e: Exception) {
            dateStr
        }
    }

    fun getDayOfWeekBangla(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return "রবিবার"
            val cal = Calendar.getInstance()
            cal.time = date
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> "শনিবার"
                Calendar.SUNDAY -> "রবিবার"
                Calendar.MONDAY -> "সোমবার"
                Calendar.TUESDAY -> "মঙ্গলবার"
                Calendar.WEDNESDAY -> "বুধবার"
                Calendar.THURSDAY -> "বৃহস্পতিবার"
                Calendar.FRIDAY -> "শুক্রবার"
                else -> "রবিবার"
            }
        } catch (e: Exception) {
            "রবিবার"
        }
    }

    fun getBanglaDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatBanglaDate(sdf.format(Date()))
    }
}
