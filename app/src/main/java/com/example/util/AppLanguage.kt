package com.example.util

import android.content.Context
import android.content.SharedPreferences

enum class Language(val code: String, val displayName: String) {
    BANGLA("bn", "বাংলা"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String): Language {
            return if (code.equals("en", ignoreCase = true) || code.equals("english", ignoreCase = true)) {
                ENGLISH
            } else {
                BANGLA
            }
        }
    }
}

object AppLanguage {
    private const val PREFS_NAME = "anwesha_app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedLanguage(context: Context): Language {
        val code = getPrefs(context).getString(KEY_LANGUAGE, "bn") ?: "bn"
        return Language.fromCode(code)
    }

    fun saveLanguage(context: Context, language: Language) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    // Comprehensive Bilingual Translations Map
    private val translations = mapOf(
        // Navigation & Top Bar
        "app_title" to Pair("অন্বেষা", "ANWESHA"),
        "nav_dashboard" to Pair("ড্যাশবোর্ড", "Dashboard"),
        "nav_students" to Pair("শিক্ষার্থী", "Students"),
        "nav_custom_fields" to Pair("ফিল্ড ও সূত্র", "Fields & Rules"),
        "nav_settings" to Pair("বিদ্যালয় ও সেটিংস", "School & Settings"),

        // Dashboard
        "dash_total_students" to Pair("মোট শিক্ষার্থী", "Total Students"),
        "dash_boys" to Pair("ছাত্র (বালক)", "Boys"),
        "dash_girls" to Pair("ছাত্রী (বালিকা)", "Girls"),
        "dash_current_active" to Pair("অধ্যয়নরত শিক্ষার্থী", "Currently Enrolled"),
        "dash_class_wise" to Pair("শ্রেণিভিত্তিক পরিসংখ্যান", "Class-wise Statistics"),
        "dash_gender_ratio" to Pair("ছাত্র-ছাত্রী অনুপাত", "Gender Ratio"),
        "dash_quick_actions" to Pair("দ্রুত কার্যক্রম", "Quick Actions"),
        "dash_add_student" to Pair("নতুন শিক্ষার্থী যোগ", "Add Student"),
        "dash_print_summary" to Pair("সামারি প্রিন্ট", "Print Summary"),
        "dash_backup" to Pair("ডাটাবেস ব্যাকআপ", "Database Backup"),
        "dash_manage_fields" to Pair("ফিল্ড ও সূত্র সেটিংস", "Fields & Formula Settings"),

        // Student Screen
        "student_list_title" to Pair("শিক্ষার্থী তালিকা", "Student Directory"),
        "student_search_placeholder" to Pair("নাম, রোল, আইডি বা গ্রাম দিয়ে খুঁজুন...", "Search by name, roll, ID or village..."),
        "student_filter_all" to Pair("সকল", "All"),
        "student_filter_class" to Pair("শ্রেণি", "Class"),
        "student_filter_gender" to Pair("লিঙ্গ", "Gender"),
        "student_filter_status" to Pair("অবস্থা", "Status"),
        "student_add_new" to Pair("নতুন শিক্ষার্থী যোগ", "Add Student"),
        "student_export_excel" to Pair("Excel এক্সপোর্ট", "Export Excel"),
        "student_import_excel" to Pair("Excel ইমপোর্ট", "Import Excel"),
        "student_print_id_cards" to Pair("আইডি কার্ড প্রিন্ট", "Print ID Cards"),
        "student_print_certificates" to Pair("প্রত্যয়নপত্র প্রিন্ট", "Print Certificates"),
        "student_no_data" to Pair("কোনো শিক্ষার্থী পাওয়া যায়নি", "No students found"),

        // Student Form Fields
        "field_name" to Pair("শিক্ষার্থীর নাম", "Student Name"),
        "field_roll" to Pair("রোল নম্বর", "Roll Number"),
        "field_class" to Pair("শ্রেণি", "Class"),
        "field_gender" to Pair("লিঙ্গ", "Gender"),
        "field_father" to Pair("পিতার নাম", "Father's Name"),
        "field_mother" to Pair("মাতার নাম", "Mother's Name"),
        "field_birth_date" to Pair("জন্ম তারিখ", "Date of Birth"),
        "field_mobile" to Pair("মোবাইল নম্বর", "Mobile Number"),
        "field_village" to Pair("গ্রাম / ঠিকানা", "Village / Address"),
        "field_status" to Pair("ভর্তি অবস্থা", "Enrollment Status"),
        "field_photo" to Pair("শিক্ষার্থীর ছবি", "Student Photo"),
        "field_take_photo" to Pair("ক্যামেরা / ছবি নির্বাচন", "Take / Choose Photo"),
        "field_status_current" to Pair("অধ্যয়নরত (Current)", "Enrolled (Current)"),
        "field_status_passed" to Pair("উত্তীর্ণ (Passed)", "Passed Out"),
        "field_status_transferred" to Pair("ছাড়পত্র প্রাপ্ত (TC)", "Transferred (TC)"),

        // Groups
        "group_basic" to Pair("মৌলিক তথ্য", "Basic Information"),
        "group_parents" to Pair("অভিভাবকের তথ্য", "Parent Information"),
        "group_address" to Pair("ঠিকানা ও যোগাযোগ", "Address & Contact"),
        "group_custom" to Pair("কাস্টম তথ্য", "Custom Information"),
        "group_academic" to Pair("একাডেমিক তথ্য", "Academic Information"),
        "group_health" to Pair("স্বাস্থ্য তথ্য", "Health Information"),

        // Form Layout Settings
        "form_layout_title" to Pair("ফিল্ড বিন্যাস ও গ্রুপ সেটিংস", "Field Layout & Group Settings"),
        "form_layout_subtitle" to Pair("গ্রুপ ও ফিল্ড সমূহের ক্রম, শিরোনাম এবং দৃশ্যমানতা পরিবর্তন করুন", "Reorder, rename or configure groups and fields"),
        "form_layout_add_group" to Pair("নতুন গ্রুপ তৈরি", "Add New Group"),
        "form_layout_group_name" to Pair("গ্রুপের নাম", "Group Name"),
        "form_layout_save" to Pair("বিন্যাস সংরক্ষণ করুন", "Save Layout"),
        "form_layout_reset" to Pair("ডিফল্ট বিন্যাসে ফিরুন", "Reset to Default"),
        "form_layout_tune_btn" to Pair("ফিল্ড ও গ্রুপ সেটিংস", "Field & Group Settings"),

        // Custom Fields
        "custom_fields_title" to Pair("কাস্টম ফিল্ড ও সূত্র ব্যবস্থাপনা", "Custom Fields & Formula Rules"),
        "custom_fields_add_field" to Pair("নতুন ফিল্ড যোগ", "Add Custom Field"),
        "custom_fields_add_rule" to Pair("নতুন সূত্র যোগ", "Add Formula Rule"),
        "custom_fields_field_name" to Pair("ফিল্ডের নাম (Label)", "Field Name (Label)"),
        "custom_fields_field_type" to Pair("ফিল্ডের ধরন", "Field Type"),
        "custom_fields_group_name" to Pair("গ্রুপের নাম (Group)", "Group Name (Group)"),
        "custom_fields_existing_groups" to Pair("বিদ্যমান গ্রুপসমূহ:", "Existing Groups:"),
        "custom_fields_new_group_option" to Pair("+ নতুন গ্রুপ", "+ New Group"),

        // Settings Screen
        "settings_title" to Pair("বিদ্যালয় তথ্য ও সেটিংস", "School Info & Settings"),
        "settings_school_profile" to Pair("বিদ্যালয়ের মৌলিক তথ্য", "School Profile & Details"),
        "settings_drive_sync" to Pair("Google Drive ক্লাউড ব্যাকআপ ও সিঙ্ক", "Google Drive Cloud Backup & Sync"),
        "settings_users" to Pair("ব্যবহারকারী ও অ্যাক্সেস নিয়ন্ত্রণ", "Users & Access Control"),
        "settings_data_mgmt" to Pair("ডাটাবেস ব্যাকআপ ও ডেটা রিসেট", "Database Backup & Data Reset"),
        "settings_preferences" to Pair("অ্যাপ পছন্দসমূহ ও ভাষা", "App Preferences & Language"),
        "settings_language" to Pair("ভাষা (Language)", "Language"),
        "settings_theme" to Pair("থিম (Theme)", "Theme"),

        // Common Buttons & Actions
        "btn_save" to Pair("সংরক্ষণ করুন", "Save"),
        "btn_cancel" to Pair("বাতিল", "Cancel"),
        "btn_edit" to Pair("সম্পাদনা", "Edit"),
        "btn_delete" to Pair("মুছে ফেলুন", "Delete"),
        "btn_confirm" to Pair("নিশ্চিত করুন", "Confirm"),
        "btn_close" to Pair("বন্ধ করুন", "Close"),
        "btn_update" to Pair("হালনাগাদ করুন", "Update"),
        "btn_reset" to Pair("রিসেট", "Reset"),
        "btn_done" to Pair("সম্পন্ন", "Done")
    )

    fun t(key: String, language: Language = Language.BANGLA): String {
        val pair = translations[key] ?: return key
        return if (language == Language.BANGLA) pair.first else pair.second
    }

    fun formatNumber(number: Any?, language: Language = Language.BANGLA): String {
        if (number == null) return ""
        return if (language == Language.BANGLA) {
            BanglaUtils.toBanglaDigits(number)
        } else {
            BanglaUtils.toEnglishDigits(number.toString())
        }
    }
}
