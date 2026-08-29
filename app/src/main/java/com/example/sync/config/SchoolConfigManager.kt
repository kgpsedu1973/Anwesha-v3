package com.example.sync.config

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SchoolConfig
import com.example.data.local.entity.SchoolInfoEntity
import com.example.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SchoolConfigManager manages local persistence, first-launch configuration checks,
 * and room code generation for school environments.
 */
class SchoolConfigManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("school_config_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_IS_CONFIGURED = "is_school_configured"
        private const val PREF_SCHOOL_NAME = "configured_school_name"
        private const val PREF_ROOM_CODE = "configured_room_code"
        private const val PREF_ADMIN_EMAIL = "configured_admin_email"
        private const val PREF_CREATED_DATE = "configured_created_date"
        private const val PREF_JOIN_METHOD = "configured_join_method" // "CREATE", "JOIN_CODE", "DEMO"

        @Volatile
        private var INSTANCE: SchoolConfigManager? = null

        fun getInstance(context: Context, database: AppDatabase): SchoolConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SchoolConfigManager(context.applicationContext, database).also { INSTANCE = it }
            }
        }
    }

    private val _isConfigured = MutableStateFlow(prefs.getBoolean(PREF_IS_CONFIGURED, false))
    val isConfigured = _isConfigured.asStateFlow()

    private val _currentConfig = MutableStateFlow<SchoolConfig?>(loadStoredConfig())
    val currentConfig = _currentConfig.asStateFlow()

    fun isSchoolConfigured(): Boolean {
        return prefs.getBoolean(PREF_IS_CONFIGURED, false)
    }

    fun getRoomCode(): String {
        return prefs.getString(PREF_ROOM_CODE, "") ?: ""
    }

    fun getSchoolName(): String {
        return prefs.getString(PREF_SCHOOL_NAME, "") ?: ""
    }

    fun getAdminEmail(): String {
        return prefs.getString(PREF_ADMIN_EMAIL, "") ?: ""
    }

    private fun loadStoredConfig(): SchoolConfig? {
        val configured = prefs.getBoolean(PREF_IS_CONFIGURED, false)
        if (!configured) return null
        return SchoolConfig(
            schoolName = prefs.getString(PREF_SCHOOL_NAME, "আমার বিদ্যালয়") ?: "আমার বিদ্যালয়",
            roomCode = prefs.getString(PREF_ROOM_CODE, "ROOM-2026-01") ?: "ROOM-2026-01",
            createdDate = prefs.getString(PREF_CREATED_DATE, "") ?: "",
            schemaVersion = 1,
            adminEmail = prefs.getString(PREF_ADMIN_EMAIL, "") ?: ""
        )
    }

    suspend fun saveSchoolConfig(config: SchoolConfig, method: String = "CREATE"): Boolean = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(PREF_IS_CONFIGURED, true)
            .putString(PREF_SCHOOL_NAME, config.schoolName)
            .putString(PREF_ROOM_CODE, config.roomCode)
            .putString(PREF_ADMIN_EMAIL, config.adminEmail)
            .putString(PREF_CREATED_DATE, config.createdDate)
            .putString(PREF_JOIN_METHOD, method)
            .apply()

        // Also persist/update Room database school_info
        val currentInfo = database.schoolInfoDao().getSchoolInfo()
        val schoolInfo = SchoolInfoEntity(
            id = 1,
            schoolName = config.schoolName,
            adminEmail = config.adminEmail,
            createdDate = config.createdDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
            updatedAt = System.currentTimeMillis()
        )
        database.schoolInfoDao().insertOrUpdateSchoolInfo(schoolInfo)

        // Update metadata for sync
        database.syncMetadataDao().insertOrUpdate(
            SyncMetadataEntity(
                fileKey = "config",
                driveFileId = null,
                localLastSyncTime = System.currentTimeMillis(),
                driveRevisionEtag = null,
                lastModifiedTime = System.currentTimeMillis()
            )
        )

        _isConfigured.value = true
        _currentConfig.value = config
        true
    }

    fun generateUniqueRoomCode(): String {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val randomNum = (1000..9999).random()
        return "ROOM-$year-$randomNum"
    }

    suspend fun resetSchoolConfig(): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        _isConfigured.value = false
        _currentConfig.value = null
        true
    }
}
