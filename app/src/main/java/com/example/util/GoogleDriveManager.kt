package com.example.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.SchoolDatabaseModel
import com.example.repository.SchoolRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveFileInfo(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val size: Long = 0L,
    val webViewLink: String? = null
)

sealed class DriveOperationResult<out T> {
    data class Success<T>(val data: T, val message: String = "") : DriveOperationResult<T>()
    data class Error(val exception: Throwable? = null, val message: String) : DriveOperationResult<Nothing>()
    data class ConsentRequired(val consentIntent: Intent, val message: String = "Google Drive অনুমতি প্রয়োজন") : DriveOperationResult<Nothing>()
    data class NotFound(val message: String = "কোনো ব্যাকআপ পাওয়া যায়নি") : DriveOperationResult<Nothing>()
    data class Progress(val status: String, val percentage: Float = 0f) : DriveOperationResult<Nothing>()
}

/**
 * GoogleDriveManager manages zero-server, multi-school data isolation via
 * hidden Google Drive AppData Folder (`https://www.googleapis.com/auth/drive.appdata`).
 *
 * Security Guarantee:
 * - Backups are stored strictly within the user's private Google Drive `appDataFolder`.
 * - Zero developer server, zero central database, zero token logging.
 */
class GoogleDriveManager(private val context: Context) {

    private val TAG = "DRIVE_BACKUP"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anwesha_drive_sync_prefs", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var lastConsentIntent: Intent? = null

    companion object {
        const val MASTER_DB_FILE_NAME = "school_database_master.json"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val RC_GOOGLE_SIGN_IN = 9001
        const val RC_RECOVERABLE_AUTH = 9002

        private const val PREF_ACCOUNT_EMAIL = "account_email"
        private const val PREF_ACCOUNT_NAME = "account_name"
        private const val PREF_ACCOUNT_PHOTO = "account_photo"
        private const val PREF_LAST_SYNC_TIME = "last_sync_time"
        private const val PREF_LAST_BACKUP_SIZE = "last_backup_size"
        private const val PREF_LAST_BACKUP_RECORDS = "last_backup_records"
        private const val PREF_LAST_BACKUP_STATUS = "last_backup_status"
        private const val PREF_DRIVE_FILE_ID = "drive_file_id"
        private const val PREF_AUTO_BACKUP = "auto_backup_enabled"
        private const val PREF_BACKUP_FREQ = "backup_frequency"
        private const val PREF_WIFI_ONLY = "backup_wifi_only"
    }

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Launches the native Android Google Account Picker.
     * Uses AccountManager.newChooseAccountIntent which lists all device Google accounts
     * and allows instant selection without developer error 10.
     */
    fun getSignInIntent(): Intent {
        Log.d(TAG, "ACCOUNT_PICKER_STARTED")
        return try {
            AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "AccountManager picker fallback to GoogleSignInClient: ${e.localizedMessage}")
            signInClient.signInIntent
        }
    }

    /**
     * Parses the result from rememberLauncherForActivityResult / onActivityResult.
     * Handles both AccountManager and GoogleSignIn results safely.
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        if (data == null) {
            Log.w(TAG, "ACCOUNT_SELECTION_CANCELLED")
            return Result.failure(Exception("Account selection cancelled"))
        }

        // 1. Check native AccountManager result (Account name/email)
        val selectedEmail = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!selectedEmail.isNullOrBlank()) {
            Log.d(TAG, "ACCOUNT_SELECTED: $selectedEmail")
            val displayName = selectedEmail.substringBefore("@")
            saveAccountState(
                email = selectedEmail,
                name = displayName,
                photoUrl = null
            )
            val mockAccount = GoogleSignInAccount.createDefault()
            return Result.success(mockAccount)
        }

        // 2. Check GoogleSignIn result
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrBlank()) {
                val email = account.email!!
                val name = account.displayName ?: (account.givenName ?: email.substringBefore("@"))
                Log.d(TAG, "ACCOUNT_SELECTED: $email")

                saveAccountState(
                    email = email,
                    name = name,
                    photoUrl = account.photoUrl?.toString()
                )
                Result.success(account)
            } else {
                Log.w(TAG, "ACCOUNT_SELECTED = FAIL (account was null)")
                Result.failure(Exception("Google Account selection returned null"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "ACCOUNT_SELECTED = FAIL (${e.localizedMessage})")
            Result.failure(e)
        }
    }

    fun isSignedIn(): Boolean {
        val email = getAccountEmail()
        return !email.isNullOrBlank()
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun getAccountEmail(): String? {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)?.email
        if (!lastAccount.isNullOrBlank()) return lastAccount
        return prefs.getString(PREF_ACCOUNT_EMAIL, null)
    }

    fun getAccountName(): String? {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)?.displayName
        if (!lastAccount.isNullOrBlank()) return lastAccount
        return prefs.getString(PREF_ACCOUNT_NAME, null)
    }

    fun getAccountPhotoUrl(): String? {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)?.photoUrl?.toString()
        if (!lastAccount.isNullOrBlank()) return lastAccount
        return prefs.getString(PREF_ACCOUNT_PHOTO, null)
    }

    fun saveAccountState(email: String, name: String, photoUrl: String? = null) {
        prefs.edit()
            .putString(PREF_ACCOUNT_EMAIL, email)
            .putString(PREF_ACCOUNT_NAME, name)
            .putString(PREF_ACCOUNT_PHOTO, photoUrl)
            .apply()
    }

    fun saveLastSync(timestamp: Long, fileId: String? = null, fileSize: Long = 0L, recordCount: Int = 0, status: String = "সফল") {
        val editor = prefs.edit()
            .putLong(PREF_LAST_SYNC_TIME, timestamp)
            .putString(PREF_LAST_BACKUP_STATUS, status)
        if (fileId != null) {
            editor.putString(PREF_DRIVE_FILE_ID, fileId)
        }
        if (fileSize > 0L) {
            editor.putLong(PREF_LAST_BACKUP_SIZE, fileSize)
        }
        if (recordCount > 0) {
            editor.putInt(PREF_LAST_BACKUP_RECORDS, recordCount)
        }
        editor.apply()
    }

    fun getLastSyncTime(): Long = prefs.getLong(PREF_LAST_SYNC_TIME, 0L)

    fun getLastBackupSize(): Long = prefs.getLong(PREF_LAST_BACKUP_SIZE, 0L)

    fun getLastBackupRecords(): Int = prefs.getInt(PREF_LAST_BACKUP_RECORDS, 0)

    fun getLastBackupStatus(): String = prefs.getString(PREF_LAST_BACKUP_STATUS, "অপেক্ষমান") ?: "অপেক্ষমান"

    fun getStoredDriveFileId(): String? = prefs.getString(PREF_DRIVE_FILE_ID, null)

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean(PREF_AUTO_BACKUP, true)

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_BACKUP, enabled).apply()
        AutoBackupScheduler.schedule(context, getBackupFrequency(), isWifiOnly(), enabled)
    }

    fun getBackupFrequency(): String = prefs.getString(PREF_BACKUP_FREQ, "দৈনিক") ?: "দৈনিক"

    fun setBackupFrequency(freq: String) {
        prefs.edit().putString(PREF_BACKUP_FREQ, freq).apply()
        AutoBackupScheduler.schedule(context, freq, isWifiOnly(), isAutoBackupEnabled())
    }

    fun isWifiOnly(): Boolean = prefs.getBoolean(PREF_WIFI_ONLY, true)

    fun setWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(PREF_WIFI_ONLY, wifiOnly).apply()
        AutoBackupScheduler.schedule(context, getBackupFrequency(), wifiOnly, isAutoBackupEnabled())
    }

    /**
     * Local Disconnect: Clears local cached account info without deleting
     * local SQLite database or remote Google Drive backup files.
     */
    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        try {
            try {
                signInClient.signOut()
            } catch (e: Exception) {
                Log.w(TAG, "GoogleSignIn signOut note: ${e.localizedMessage}")
            }
            prefs.edit()
                .remove(PREF_ACCOUNT_EMAIL)
                .remove(PREF_ACCOUNT_NAME)
                .remove(PREF_ACCOUNT_PHOTO)
                .apply()
            Log.d(TAG, "ACCOUNT_DISCONNECTED: Account signed out locally")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Obtains an OAuth2 bearer token for the DRIVE_APPDATA scope.
     * Uses the selected Google account email with GoogleAuthUtil.
     * Never logs tokens or credentials.
     */
    suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        val email = getAccountEmail()
        if (email.isNullOrBlank()) {
            Log.w(TAG, "DRIVE_AUTHORIZED = FAIL (No Google account connected)")
            return@withContext null
        }

        try {
            val account = Account(email, "com.google")
            val token = GoogleAuthUtil.getToken(
                context,
                account,
                "oauth2:$DRIVE_APPDATA_SCOPE"
            )
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "DRIVE_AUTHORIZED = PASS")
                return@withContext token
            }
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "DRIVE_AUTHORIZATION_REQUIRED: User consent required")
            lastConsentIntent = e.intent
        } catch (e: Exception) {
            Log.e(TAG, "DRIVE_AUTHORIZED = FAIL: ${e.localizedMessage}")
            if (lastConsentIntent == null) {
                lastConsentIntent = signInClient.signInIntent
            }
        }
        null
    }

    /**
     * Searches for 'school_database_master.json' inside the hidden AppDataFolder (`spaces=appDataFolder`).
     */
    suspend fun checkExistingDatabase(): DriveOperationResult<DriveFileInfo?> = withContext(Dispatchers.IO) {
        try {
            val email = getAccountEmail()
            if (email.isNullOrBlank()) {
                return@withContext DriveOperationResult.Error(null, "কোনো Google অ্যাকাউন্ট নির্বাচন করা নেই")
            }

            val token = getAuthToken()
            if (token == null) {
                if (lastConsentIntent != null) {
                    return@withContext DriveOperationResult.ConsentRequired(lastConsentIntent!!)
                }
                val cachedId = getStoredDriveFileId()
                if (cachedId != null) {
                    Log.d(TAG, "APPDATA_FILE_FOUND: id=$cachedId (Cached reference)")
                    return@withContext DriveOperationResult.Success(
                        DriveFileInfo(
                            id = cachedId,
                            name = MASTER_DB_FILE_NAME,
                            size = getLastBackupSize(),
                            modifiedTime = "Cached"
                        ),
                        "Found cached AppData file reference"
                    )
                }
                return@withContext DriveOperationResult.Success(null, "No remote database found")
            }

            val query = "'appDataFolder' in parents and name = '$MASTER_DB_FILE_NAME' and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=${URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime,size,webViewLink)&pageSize=1"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    val info = DriveFileInfo(
                        id = fileObj.getString("id"),
                        name = fileObj.getString("name"),
                        modifiedTime = fileObj.optString("modifiedTime", null),
                        size = fileObj.optLong("size", 0L),
                        webViewLink = fileObj.optString("webViewLink", null)
                    )
                    saveLastSync(System.currentTimeMillis(), info.id, info.size)
                    Log.d(TAG, "APPDATA_FILE_FOUND: id=${info.id}")
                    return@withContext DriveOperationResult.Success(info, "Existing database file found in AppDataFolder")
                } else {
                    Log.d(TAG, "APPDATA_FILE_NOT_FOUND: No existing file")
                    return@withContext DriveOperationResult.Success(null, "No database file exists in AppDataFolder")
                }
            } else {
                Log.e(TAG, "APPDATA_QUERY_FAILED: HTTP ${response.code}")
                return@withContext DriveOperationResult.Error(
                    Exception("HTTP ${response.code}: ${response.message}"),
                    "Google Drive AppData অ্যাক্সেস ব্যর্থ (HTTP ${response.code})"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "APPDATA_QUERY_ERROR: ${e.localizedMessage}")
            return@withContext DriveOperationResult.Error(e, e.localizedMessage ?: "Unknown network error")
        }
    }

    /**
     * Uploads or updates the master database JSON in Google Drive hidden appDataFolder.
     * Follows the complete sequence:
     * BACKUP_STARTED -> DATABASE_SERIALIZED -> DRIVE_AUTHORIZED -> APPDATA_FILE_FOUND/CREATED/UPDATED -> BACKUP_VERIFIED
     */
    suspend fun uploadDatabase(jsonContent: String, recordCount: Int = 0): DriveOperationResult<DriveFileInfo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "BACKUP_STARTED")
        try {
            val email = getAccountEmail()
            if (email.isNullOrBlank()) {
                Log.w(TAG, "BACKUP_FAILED: No Google account selected")
                return@withContext DriveOperationResult.Error(
                    null,
                    "কোনো Google অ্যাকাউন্ট নির্বাচন করা নেই। অনুগ্রহ করে প্রথমে Google অ্যাকাউন্ট নির্বাচন করুন।"
                )
            }

            val contentBytes = jsonContent.toByteArray(Charsets.UTF_8)
            val fileSize = contentBytes.size.toLong()
            Log.d(TAG, "DATABASE_SERIALIZED: $recordCount records, $fileSize bytes")

            val token = getAuthToken()
            if (token == null) {
                if (lastConsentIntent != null) {
                    Log.w(TAG, "BACKUP_PENDING: User consent required")
                    return@withContext DriveOperationResult.ConsentRequired(lastConsentIntent!!)
                }
                Log.e(TAG, "BACKUP_FAILED: Token acquisition failed")
                return@withContext DriveOperationResult.Error(
                    null,
                    "Google Drive অনুমোদন পাওয়া যায়নি। অ্যাকাউন্ট পুনরায় নির্বাচন করুন বা ইন্টারনেট সংযোগ পরীক্ষা করুন।"
                )
            }

            val mediaTypeJson = "application/json; charset=UTF-8".toMediaType()

            // Check if file already exists in AppData
            val existingCheck = checkExistingDatabase()
            val existingFile = if (existingCheck is DriveOperationResult.Success) existingCheck.data else null

            if (existingFile != null) {
                // Update existing file in appDataFolder via PATCH
                val patchUrl = "https://www.googleapis.com/upload/drive/v3/files/${existingFile.id}?uploadType=media"
                val requestBody = contentBytes.toRequestBody(mediaTypeJson)
                val request = Request.Builder()
                    .url(patchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .patch(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "APPDATA_FILE_UPDATED: id=${existingFile.id}")
                    Log.d(TAG, "BACKUP_VERIFIED: Backup update confirmed")
                    saveLastSync(System.currentTimeMillis(), existingFile.id, fileSize, recordCount, "সফল")
                    val updatedInfo = existingFile.copy(size = fileSize)
                    return@withContext DriveOperationResult.Success(updatedInfo, "Google Drive (AppData) এ সফলভাবে ব্যাকআপ আপডেট হয়েছে!")
                } else {
                    Log.e(TAG, "BACKUP_FAILED: Update failed HTTP ${response.code}")
                    saveLastSync(System.currentTimeMillis(), existingFile.id, fileSize, recordCount, "ব্যর্থ")
                    return@withContext DriveOperationResult.Error(
                        Exception("HTTP ${response.code}"),
                        "Google Drive ফাইল আপডেট ব্যর্থ হয়েছে (HTTP ${response.code})"
                    )
                }
            } else {
                // Create new file inside appDataFolder via Multipart POST
                val metadata = JSONObject().apply {
                    put("name", MASTER_DB_FILE_NAME)
                    put("parents", org.json.JSONArray().put("appDataFolder"))
                    put("mimeType", "application/json")
                }.toString()

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metadata.toRequestBody(mediaTypeJson))
                    .addFormDataPart("file", MASTER_DB_FILE_NAME, contentBytes.toRequestBody(mediaTypeJson))
                    .build()

                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: "{}"
                    val respJson = JSONObject(respBody)
                    val newId = respJson.optString("id", "appdata_file_id")
                    Log.d(TAG, "APPDATA_FILE_CREATED: id=$newId")
                    Log.d(TAG, "BACKUP_VERIFIED: New backup created")
                    saveLastSync(System.currentTimeMillis(), newId, fileSize, recordCount, "সফল")
                    val fileInfo = DriveFileInfo(
                        id = newId,
                        name = MASTER_DB_FILE_NAME,
                        size = fileSize
                    )
                    return@withContext DriveOperationResult.Success(fileInfo, "Google Drive (AppData) এ নতুন ব্যাকআপ তৈরি হয়েছে!")
                } else {
                    Log.e(TAG, "BACKUP_FAILED: Upload failed HTTP ${response.code}")
                    saveLastSync(System.currentTimeMillis(), null, fileSize, recordCount, "ব্যর্থ")
                    return@withContext DriveOperationResult.Error(
                        Exception("HTTP ${response.code}"),
                        "Google Drive এ নতুন ব্যাকআপ তৈরি ব্যর্থ (HTTP ${response.code})"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "BACKUP_FAILED: ${e.localizedMessage}")
            saveLastSync(System.currentTimeMillis(), null, 0L, recordCount, "ব্যর্থ")
            return@withContext DriveOperationResult.Error(e, e.localizedMessage ?: "ড্রাইভ ব্যাকআপ প্রক্রিয়ায় ত্রুটি হয়েছে")
        }
    }

    /**
     * Downloads and parses the master database JSON file from Google Drive AppData into Room.
     */
    suspend fun downloadDatabase(
        fileId: String? = null,
        repository: SchoolRepository
    ): DriveOperationResult<SchoolDatabaseModel> = withContext(Dispatchers.IO) {
        Log.d(TAG, "RESTORE_STARTED")
        try {
            val email = getAccountEmail()
            if (email.isNullOrBlank()) {
                return@withContext DriveOperationResult.Error(null, "কোনো Google অ্যাকাউন্ট নির্বাচন করা নেই")
            }

            val token = getAuthToken()
            if (token == null) {
                if (lastConsentIntent != null) {
                    return@withContext DriveOperationResult.ConsentRequired(lastConsentIntent!!)
                }
                return@withContext DriveOperationResult.Error(
                    null,
                    "Google Drive অনুমোদন পাওয়া যায়নি। অ্যাকাউন্ট পুনরায় সংযুক্ত করুন।"
                )
            }

            // Find file ID if not provided
            var targetFileId = fileId ?: getStoredDriveFileId()
            if (targetFileId == null) {
                val existingCheck = checkExistingDatabase()
                if (existingCheck is DriveOperationResult.Success && existingCheck.data != null) {
                    targetFileId = existingCheck.data.id
                }
            }

            if (targetFileId == null) {
                Log.d(TAG, "APPDATA_FILE_NOT_FOUND")
                return@withContext DriveOperationResult.NotFound("কোনো ব্যাকআপ পাওয়া যায়নি (No backup found in Google Drive)")
            }

            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$targetFileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonContent = response.body?.string()
                if (jsonContent.isNullOrBlank()) {
                    Log.e(TAG, "RESTORE_FAILED: Empty response from Drive")
                    return@withContext DriveOperationResult.Error(null, "ড্রাইভ থেকে কোনো ডেটা পাওয়া যায়নি")
                }

                val masterModel = SchoolDatabaseModel.fromJson(jsonContent)
                if (masterModel != null) {
                    repository.importMasterModel(masterModel)
                    val bytesSize = jsonContent.toByteArray(Charsets.UTF_8).size.toLong()
                    val totalRecords = masterModel.studentsList.size + masterModel.usersList.size + masterModel.attendanceList.size + masterModel.examResultsList.size
                    saveLastSync(System.currentTimeMillis(), targetFileId, bytesSize, totalRecords, "সফল")
                    Log.d(TAG, "BACKUP_VERIFIED: RESTORE_COMPLETE = PASS ($totalRecords records)")
                    return@withContext DriveOperationResult.Success(masterModel, "Google Drive থেকে সফলভাবে $totalRecords টি রেকর্ড পুনরুদ্ধার করা হয়েছে!")
                } else {
                    Log.e(TAG, "RESTORE_FAILED: Invalid JSON schema")
                    return@withContext DriveOperationResult.Error(Exception("JSON Parsing Failed"), "ব্যাকআপ ফাইলের ফরম্যাট সঠিক নয়")
                }
            } else if (response.code == 404) {
                Log.d(TAG, "APPDATA_FILE_NOT_FOUND: HTTP 404")
                return@withContext DriveOperationResult.NotFound("Google Drive এ ব্যাকআপ ফাইলটি পাওয়া যায়নি")
            } else {
                Log.e(TAG, "RESTORE_FAILED: HTTP ${response.code}")
                return@withContext DriveOperationResult.Error(Exception("HTTP ${response.code}"), "ড্রাইভ ফাইল ডাউনলোড ব্যর্থ (HTTP ${response.code})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "RESTORE_FAILED: ${e.localizedMessage}")
            return@withContext DriveOperationResult.Error(e, e.localizedMessage ?: "রিস্টোর প্রক্রিয়ায় নেটওয়ার্ক ত্রুটি")
        }
    }
}
