package com.example.util

import android.content.Context
import android.content.SharedPreferences

enum class AppSecurityScope(val key: String, val titleBn: String, val descBn: String) {
    ATTENDANCE_DELETE("protect_attendance_delete", "হাজিরা রেকর্ড মুছে ফেলা", "দৈনিক ও তারিখভিত্তিক হাজিরা রেকর্ড মুছতে পাসওয়ার্ড চাইবে"),
    STUDENT_DELETE("protect_student_delete", "শিক্ষার্থী তথ্য মুছে ফেলা", "শিক্ষার্থীর প্রোফাইল বা তালিকা থেকে মুছতে পাসওয়ার্ড চাইবে"),
    EXAM_DELETE("protect_exam_delete", "পরীক্ষা ও ফলাফল মুছে ফেলা", "পরীক্ষা ও নম্বরের রেকর্ড মুছতে পাসওয়ার্ড চাইবে"),
    DATABASE_RESET("protect_db_reset", "সম্পূর্ণ ডেটা রিসেট", "ডাটাবেস সম্পূর্ণ রিসেট বা ফ্যাক্টরি মুছতে পাসওয়ার্ড চাইবে")
}

object AppSecurityManager {
    private const val PREFS_NAME = "school_app_security_prefs"
    private const val KEY_SECURITY_PASSWORD = "security_master_password"
    private const val KEY_MASTER_ENABLED = "security_master_protection_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPasswordSet(context: Context): Boolean {
        return getPrefs(context).getString(KEY_SECURITY_PASSWORD, "")?.isNotBlank() == true
    }

    fun getSavedPassword(context: Context): String {
        return getPrefs(context).getString(KEY_SECURITY_PASSWORD, "") ?: ""
    }

    fun setPassword(context: Context, newPass: String) {
        getPrefs(context).edit()
            .putString(KEY_SECURITY_PASSWORD, newPass.trim())
            .putBoolean(KEY_MASTER_ENABLED, newPass.trim().isNotBlank())
            .apply()
    }

    fun isMasterProtectionEnabled(context: Context): Boolean {
        val hasPass = isPasswordSet(context)
        val isEnabled = getPrefs(context).getBoolean(KEY_MASTER_ENABLED, true)
        return hasPass && isEnabled
    }

    fun setMasterProtectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun isScopeProtected(context: Context, scope: AppSecurityScope): Boolean {
        if (!isMasterProtectionEnabled(context)) return false
        return getPrefs(context).getBoolean(scope.key, true) // Default is protected
    }

    fun setScopeProtected(context: Context, scope: AppSecurityScope, protected: Boolean) {
        getPrefs(context).edit().putBoolean(scope.key, protected).apply()
    }

    fun verifyPassword(context: Context, input: String): Boolean {
        val saved = getSavedPassword(context)
        if (saved.isBlank()) return true // No password set
        return saved.trim() == input.trim()
    }
}
