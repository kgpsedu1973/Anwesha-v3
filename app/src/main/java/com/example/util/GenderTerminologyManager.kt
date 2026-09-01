package com.example.util

import android.content.Context

enum class GenderTerminology(
    val id: Int,
    val titleBn: String,
    val titleEn: String,
    val boyLabel: String,
    val girlLabel: String,
    val descriptionBn: String
) {
    CHHATRA_CHHATRI(
        id = 1,
        titleBn = "ছাত্র / ছাত্রী",
        titleEn = "Chhatra / Chhatri",
        boyLabel = "ছাত্র",
        girlLabel = "ছাত্রী",
        descriptionBn = "ডিফল্ট প্রাতিষ্ঠানিক শব্দ (ছাত্র ও ছাত্রী)"
    ),
    BALOK_BALIKA(
        id = 2,
        titleBn = "বালক / বালিকা",
        titleEn = "Balok / Balika",
        boyLabel = "বালক",
        girlLabel = "বালিকা",
        descriptionBn = "বালক ও বালিকা হিসেবে ইমপোর্ট, প্রদর্শন ও সংরক্ষণ"
    ),
    CHHELE_MEYE(
        id = 3,
        titleBn = "ছেলে / মেয়ে",
        titleEn = "Chhele / Meye",
        boyLabel = "ছেলে",
        girlLabel = "মেয়ে",
        descriptionBn = "ছেলে ও মেয়ে টার্মিনোলজি ব্যবহার"
    ),
    FLEXIBLE_BOTH(
        id = 4,
        titleBn = "উভয়ই গ্রহণযোগ্য (ছাত্র/ছাত্রী বা বালক/বালিকা)",
        titleEn = "Flexible (Accept Both)",
        boyLabel = "ছাত্র / বালক",
        girlLabel = "ছাত্রী / বালিকা",
        descriptionBn = "এক্সেল/সিএসভি ইমপোর্টে ছাত্র-ছাত্রী বা বালক-বালিকা যেকোনোটি সরাসরি গৃহীত হবে"
    );

    companion object {
        private const val PREFS_NAME = "anwesha_gender_prefs"
        private const val KEY_TERMINOLOGY_ID = "selected_gender_terminology_id"

        fun getSavedTerminology(context: Context): GenderTerminology {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getInt(KEY_TERMINOLOGY_ID, 1)
            return values().find { it.id == id } ?: CHHATRA_CHHATRI
        }

        fun saveTerminology(context: Context, terminology: GenderTerminology) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_TERMINOLOGY_ID, terminology.id).apply()
        }
    }
}

object GenderUtils {

    /**
     * Determines if the given string represents female (ছাত্রী / বালিকা / মেয়ে / Female).
     */
    fun isGirl(raw: String?): Boolean {
        if (raw == null) return false
        val clean = raw.trim().lowercase()
        return clean.contains("ছাত্রী") ||
                clean.contains("বালিকা") ||
                clean.contains("মেয়ে") ||
                clean.contains("মেয়ে") ||
                clean.contains("নারী") ||
                clean.contains("মহিলা") ||
                clean.contains("কন্যা") ||
                clean == "female" ||
                clean == "girl" ||
                clean == "f" ||
                clean == "g"
    }

    /**
     * Determines if the given string represents male (ছাত্র / বালক / পালক / ছেলে / Male).
     */
    fun isBoy(raw: String?): Boolean {
        if (raw == null) return true
        if (isGirl(raw)) return false
        val clean = raw.trim().lowercase()
        return clean.contains("ছাত্র") ||
                clean.contains("বালক") ||
                clean.contains("পালক") || // Handles common typo mentioned by user
                clean.contains("ছেলে") ||
                clean.contains("পুরুষ") ||
                clean == "male" ||
                clean == "boy" ||
                clean == "m" ||
                clean == "b" ||
                clean.isNotBlank()
    }

    /**
     * Normalizes any input gender variant into the target terminology format.
     */
    fun normalizeGender(raw: String?, terminology: GenderTerminology = GenderTerminology.CHHATRA_CHHATRI): String {
        val girl = isGirl(raw)
        return when (terminology) {
            GenderTerminology.CHHATRA_CHHATRI -> if (girl) "ছাত্রী" else "ছাত্র"
            GenderTerminology.BALOK_BALIKA -> if (girl) "বালিকা" else "বালক"
            GenderTerminology.CHHELE_MEYE -> if (girl) "মেয়ে" else "ছেলে"
            GenderTerminology.FLEXIBLE_BOTH -> {
                val clean = (raw ?: "").trim()
                if (girl) {
                    if (clean.contains("বালিকা")) "বালিকা" else if (clean.contains("মেয়ে") || clean.contains("মেয়ে")) "মেয়ে" else "ছাত্রী"
                } else {
                    if (clean.contains("বালক") || clean.contains("পালক")) "বালক" else if (clean.contains("ছেলে")) "ছেলে" else "ছাত্র"
                }
            }
        }
    }

    /**
     * Returns canonical internal representation ("ছাত্র" or "ছাত্রী").
     */
    fun getCanonicalGender(raw: String?): String {
        return if (isGirl(raw)) "ছাত্রী" else "ছাত্র"
    }

    /**
     * Formats gender string for UI display according to user preference.
     */
    fun getDisplayLabel(gender: String?, terminology: GenderTerminology): String {
        if (terminology == GenderTerminology.FLEXIBLE_BOTH) {
            return gender?.ifBlank { "ছাত্র" } ?: "ছাত্র"
        }
        return if (isGirl(gender)) terminology.girlLabel else terminology.boyLabel
    }

    /**
     * Checks if a student's gender matches a filter value (handles both Chhatra/Chhatri and Balok/Balika interoperably).
     */
    fun matchesFilter(studentGender: String?, filterGender: String?): Boolean {
        if (filterGender.isNullOrBlank() || filterGender == "ALL" || filterGender == "সকল") return true
        val studentIsGirl = isGirl(studentGender)
        val filterIsGirl = isGirl(filterGender)
        val filterIsBoy = isBoy(filterGender)

        if (filterIsGirl) return studentIsGirl
        if (filterIsBoy) return !studentIsGirl
        return studentGender.equals(filterGender, ignoreCase = true)
    }

    /**
     * Checks if a student's gender matches any in the selected filters set.
     */
    fun matchesAnyFilter(studentGender: String?, filterGenders: Set<String>): Boolean {
        if (filterGenders.isEmpty()) return true
        return filterGenders.any { matchesFilter(studentGender, it) }
    }

    /**
     * Returns selectable options for student forms / filter dropdowns.
     */
    fun getFormGenderOptions(terminology: GenderTerminology): List<String> {
        return when (terminology) {
            GenderTerminology.CHHATRA_CHHATRI -> listOf("ছাত্র", "ছাত্রী")
            GenderTerminology.BALOK_BALIKA -> listOf("বালক", "বালিকা")
            GenderTerminology.CHHELE_MEYE -> listOf("ছেলে", "মেয়ে")
            GenderTerminology.FLEXIBLE_BOTH -> listOf("ছাত্র", "ছাত্রী", "বালক", "বালিকা")
        }
    }
}
