package com.example.util

import android.content.Context
import com.example.data.local.entity.StudentEntity

enum class ClassPreset(val id: Int, val titleBn: String, val classNames: List<String>) {
    PRESET_DIGIT(
        id = 1,
        titleBn = "প্রাক-প্রাথমিক ৪+, প্রাক-প্রাথমিক ৫+, ১ম, ২য়, ৩য়, ৪র্থ, ৫ম",
        classNames = listOf(
            "প্রাক-প্রাথমিক ৪+",
            "প্রাক-প্রাথমিক ৫+",
            "১ম",
            "২য়",
            "৩য়",
            "৪র্থ",
            "৫ম"
        )
    ),
    PRESET_WORD(
        id = 2,
        titleBn = "প্রাক-প্রাথমিক ৪+, প্রাক-প্রাথমিক ৫+, প্রথম, দ্বিতীয়, তৃতীয়, চতুর্থ, পঞ্চম",
        classNames = listOf(
            "প্রাক-প্রাথমিক ৪+",
            "প্রাক-প্রাথমিক ৫+",
            "প্রথম",
            "দ্বিতীয়",
            "তৃতীয়",
            "চতুর্থ",
            "পঞ্চম"
        )
    );

    companion object {
        private const val PREFS_NAME = "anwesha_class_preset_prefs"
        private const val KEY_PRESET_ID = "selected_class_preset_id"

        fun getSavedPreset(context: Context): ClassPreset {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getInt(KEY_PRESET_ID, 1)
            return values().find { it.id == id } ?: PRESET_DIGIT
        }

        fun savePreset(context: Context, preset: ClassPreset) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_PRESET_ID, preset.id).apply()
        }

        fun convertClassName(currentName: String, targetPreset: ClassPreset): String {
            val trimmed = currentName.trim()
            if (trimmed.startsWith("প্রাক-প্রাথমিক ৪")) return "প্রাক-প্রাথমিক ৪+"
            if (trimmed.startsWith("প্রাক-প্রাথমিক ৫")) return "প্রাক-প্রাথমিক ৫+"
            if (trimmed.contains("প্রাক-প্রাথমিক") || trimmed.contains("শিশু")) {
                return if (trimmed.contains("৪")) "প্রাক-প্রাথমিক ৪+" else "প্রাক-প্রাথমিক ৫+"
            }

            if (targetPreset == PRESET_DIGIT) {
                return when {
                    trimmed.contains("প্রথম") || trimmed.contains("১ম") || trimmed == "1" -> "১ম"
                    trimmed.contains("দ্বিতীয়") || trimmed.contains("দ্বিতীয়") || trimmed.contains("২য়") || trimmed.contains("২য়") || trimmed == "2" -> "২য়"
                    trimmed.contains("তৃতীয়") || trimmed.contains("তৃতীয়") || trimmed.contains("৩য়") || trimmed.contains("৩য়") || trimmed == "3" -> "৩য়"
                    trimmed.contains("চতুর্থ") || trimmed.contains("৪র্থ") || trimmed == "4" -> "৪র্থ"
                    trimmed.contains("পঞ্চম") || trimmed.contains("৫ম") || trimmed == "5" -> "৫ম"
                    else -> currentName
                }
            } else {
                return when {
                    trimmed.contains("১ম") || trimmed.contains("প্রথম") || trimmed == "1" -> "প্রথম"
                    trimmed.contains("২য়") || trimmed.contains("২য়") || trimmed.contains("দ্বিতীয়") || trimmed.contains("দ্বিতীয়") || trimmed == "2" -> "দ্বিতীয়"
                    trimmed.contains("৩য়") || trimmed.contains("৩য়") || trimmed.contains("তৃতীয়") || trimmed.contains("তৃতীয়") || trimmed == "3" -> "তৃতীয়"
                    trimmed.contains("৪র্থ") || trimmed.contains("চতুর্থ") || trimmed == "4" -> "চতুর্থ"
                    trimmed.contains("৫ম") || trimmed.contains("পঞ্চম") || trimmed == "5" -> "পঞ্চম"
                    else -> currentName
                }
            }
        }
    }
}
