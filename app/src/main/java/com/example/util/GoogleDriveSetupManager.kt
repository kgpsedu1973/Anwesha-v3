package com.example.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.data.local.AppDatabase
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class CloudBackupDiscoveryResult(
    val found: Boolean,
    val schoolName: String = "",
    val eiinCode: String = "",
    val studentCount: Int = 0,
    val backupTimestamp: Long = 0L,
    val backupDateFormatted: String = "",
    val folderId: String = "",
    val folderName: String = "",
    val dbFileId: String? = null,
    val dbFileName: String = "",
    val dbFileSizeFormatted: String = "",
    val hasDbBackup: Boolean = false,
    val hasJsonBackup: Boolean = false,
    val rawManifestJson: String? = null,
    val consentIntent: Intent? = null,
    val message: String = ""
)

data class DirectDbRestoreResult(
    val fileName: String,
    val fileSizeFormatted: String,
    val restoredTimestamp: Long,
    val success: Boolean = true,
    val message: String = ""
)

sealed class DriveSetupState {
    object Idle : DriveSetupState()
    data class Loading(val message: String) : DriveSetupState()
    data class NeedsUserConsent(
        val consentIntent: Intent,
        val email: String,
        val displayName: String,
        val isSecondary: Boolean = false
    ) : DriveSetupState()
    data class Success(
        val email: String,
        val displayName: String,
        val folderId: String,
        val folderName: String,
        val folderWebViewLink: String?,
        val message: String,
        val isSecondary: Boolean = false
    ) : DriveSetupState()
    data class Error(val errorMessage: String) : DriveSetupState()
}

data class ConnectedDriveAccountInfo(
    val email: String,
    val displayName: String,
    val folderId: String,
    val folderName: String,
    val folderWebViewLink: String?,
    val connectedAt: Long,
    val isSecondary: Boolean = false
)

data class DirectDbUploadResult(
    val fileId: String,
    val fileName: String,
    val fileSizeFormatted: String,
    val folderId: String,
    val webViewLink: String?,
    val uploadTimestamp: Long,
    val success: Boolean = true
) {
    val uploadedAtFormatted: String
        get() {
            return if (uploadTimestamp > 0) {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                BanglaUtils.toBanglaDigits(sdf.format(Date(uploadTimestamp)))
            } else ""
        }
}

class GoogleDriveSetupManager(private val context: Context) {

    companion object {
        const val WEB_CLIENT_ID = "653129644913-99d8fcsomo753eb9jr8nher3i3akcfr8.apps.googleusercontent.com"
        private const val TAG = "DriveSetupManager"
        private const val PREFS_NAME = "google_drive_school_prefs"

        // Primary Account Keys
        private const val KEY_IS_CONNECTED = "key_is_connected"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_FOLDER_ID = "key_folder_id"
        private const val KEY_FOLDER_NAME = "key_folder_name"
        private const val KEY_FOLDER_LINK = "key_folder_link"
        private const val KEY_CONNECTED_TIME = "key_connected_time"

        // Secondary Account Keys
        private const val KEY_SEC_IS_CONNECTED = "key_sec_is_connected"
        private const val KEY_SEC_EMAIL = "key_sec_email"
        private const val KEY_SEC_DISPLAY_NAME = "key_sec_display_name"
        private const val KEY_SEC_FOLDER_ID = "key_sec_folder_id"
        private const val KEY_SEC_FOLDER_NAME = "key_sec_folder_name"
        private const val KEY_SEC_FOLDER_LINK = "key_sec_folder_link"
        private const val KEY_SEC_CONNECTED_TIME = "key_sec_connected_time"

        // Last Direct DB Backup
        private const val KEY_LAST_DB_FILE_ID = "key_last_db_file_id"
        private const val KEY_LAST_DB_UPLOAD_TIME = "key_last_db_upload_time"
        private const val KEY_LAST_DB_SIZE = "key_last_db_size"

        // Auto Sync & Media Sync Keys
        const val KEY_AUTO_SYNC_MODE = "key_auto_sync_mode"
        const val KEY_SYNC_IMAGES = "key_sync_images"
        const val KEY_SYNC_PDFS = "key_sync_pdfs"
        const val KEY_LAST_AUTO_SYNC_TIME = "key_last_auto_sync_time"

        const val DRIVE_SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val DRIVE_SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_SCOPE_USER_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
        const val DRIVE_SCOPE_USER_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val _setupState = MutableStateFlow<DriveSetupState>(DriveSetupState.Idle)
    val setupState: StateFlow<DriveSetupState> = _setupState.asStateFlow()

    private val _primaryAccount = MutableStateFlow<ConnectedDriveAccountInfo?>(loadPrimaryAccount())
    val primaryAccount: StateFlow<ConnectedDriveAccountInfo?> = _primaryAccount.asStateFlow()

    private val _secondaryAccount = MutableStateFlow<ConnectedDriveAccountInfo?>(loadSecondaryAccount())
    val secondaryAccount: StateFlow<ConnectedDriveAccountInfo?> = _secondaryAccount.asStateFlow()

    private val _lastDbUploadInfo = MutableStateFlow<DirectDbUploadResult?>(loadLastDbUploadInfo())
    val lastDbUploadInfo: StateFlow<DirectDbUploadResult?> = _lastDbUploadInfo.asStateFlow()

    // Flag to know whether account picker was triggered for primary or secondary slot
    var isConnectingSecondary: Boolean = false

    private var pendingGoogleAccount: GoogleSignInAccount? = null
    private var pendingIsSecondary: Boolean = false

    private fun loadPrimaryAccount(): ConnectedDriveAccountInfo? {
        val isConnected = prefs.getBoolean(KEY_IS_CONNECTED, false)
        val email = prefs.getString(KEY_EMAIL, null)
        val folderId = prefs.getString(KEY_FOLDER_ID, null)

        return if (isConnected && !email.isNullOrBlank() && !folderId.isNullOrBlank()) {
            ConnectedDriveAccountInfo(
                email = email,
                displayName = prefs.getString(KEY_DISPLAY_NAME, email) ?: email,
                folderId = folderId,
                folderName = prefs.getString(KEY_FOLDER_NAME, "School_Data_Storage") ?: "School_Data_Storage",
                folderWebViewLink = prefs.getString(KEY_FOLDER_LINK, null),
                connectedAt = prefs.getLong(KEY_CONNECTED_TIME, System.currentTimeMillis()),
                isSecondary = false
            )
        } else null
    }

    private fun loadSecondaryAccount(): ConnectedDriveAccountInfo? {
        val isConnected = prefs.getBoolean(KEY_SEC_IS_CONNECTED, false)
        val email = prefs.getString(KEY_SEC_EMAIL, null)
        val folderId = prefs.getString(KEY_SEC_FOLDER_ID, null)

        return if (isConnected && !email.isNullOrBlank() && !folderId.isNullOrBlank()) {
            ConnectedDriveAccountInfo(
                email = email,
                displayName = prefs.getString(KEY_SEC_DISPLAY_NAME, email) ?: email,
                folderId = folderId,
                folderName = prefs.getString(KEY_SEC_FOLDER_NAME, "School_Data_Storage_Backup") ?: "School_Data_Storage_Backup",
                folderWebViewLink = prefs.getString(KEY_SEC_FOLDER_LINK, null),
                connectedAt = prefs.getLong(KEY_SEC_CONNECTED_TIME, System.currentTimeMillis()),
                isSecondary = true
            )
        } else null
    }

    private fun loadLastDbUploadInfo(): DirectDbUploadResult? {
        val fileId = prefs.getString(KEY_LAST_DB_FILE_ID, null) ?: return null
        val uploadTs = prefs.getLong(KEY_LAST_DB_UPLOAD_TIME, 0L)
        val sizeStr = prefs.getString(KEY_LAST_DB_SIZE, "") ?: ""
        return DirectDbUploadResult(
            fileId = fileId,
            fileName = "anwesha_school_db.db",
            fileSizeFormatted = sizeStr,
            folderId = "",
            webViewLink = "https://drive.google.com/file/d/$fileId/view",
            uploadTimestamp = uploadTs
        )
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .requestScopes(
                Scope(DRIVE_SCOPE_APPDATA),
                Scope(DRIVE_SCOPE_FILE),
                Scope("https://www.googleapis.com/auth/drive.readonly")
            )
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(forSecondary: Boolean = false): Intent {
        isConnectingSecondary = forSecondary
        val client = getGoogleSignInClient()
        client.signOut()
        return client.signInIntent
    }

    suspend fun retryPendingConsent(schoolName: String = "School_Data_Storage"): Result<ConnectedDriveAccountInfo> {
        val account = pendingGoogleAccount
        val isSec = pendingIsSecondary
        return if (account != null) {
            handleSignInAccount(account, schoolName, isSec)
        } else {
            val errorMsg = "অ্যাকাউন্ট পুনরায় পাওয়া যায়নি। অনুগ্রহ করে আবার অ্যাকাউন্ট নির্বাচন করুন।"
            _setupState.value = DriveSetupState.Error(errorMsg)
            Result.failure(Exception(errorMsg))
        }
    }

    suspend fun handleSignInAccount(
        account: GoogleSignInAccount,
        schoolName: String = "School_Data_Storage",
        isSecondary: Boolean = false,
        createFolderIfMissing: Boolean = true
    ): Result<ConnectedDriveAccountInfo> = withContext(Dispatchers.IO) {
        pendingGoogleAccount = account
        pendingIsSecondary = isSecondary
        val slotName = if (isSecondary) "দ্বিতীয় ড্রাইভ" else "মূল ড্রাইভ"
        try {
            _setupState.value = DriveSetupState.Loading("$slotName অ্যাকাউন্ট যাচাই করা হচ্ছে...")
            val email = account.email ?: "Unknown Email"
            val displayName = account.displayName ?: email
            val androidAccount: Account = account.account ?: Account(email, "com.google")

            _setupState.value = DriveSetupState.Loading("$slotName Google Drive অ্যাক্সেস টোকেন নেওয়া হচ্ছে...")
            val oauthScope = "oauth2:$DRIVE_SCOPE_FILE $DRIVE_SCOPE_USER_EMAIL $DRIVE_SCOPE_USER_PROFILE"

            val accessToken = try {
                AppErrorLogger.logInfo("DriveSetup", "OAuth Token অনুরোধ ($slotName): $email")
                GoogleAuthUtil.getToken(context, androidAccount, oauthScope)
            } catch (e: UserRecoverableAuthException) {
                AppErrorLogger.logWarning("DriveSetup", "OAuth সম্মতি স্ক্রিন প্রয়োজন ($slotName): ${e.message}")
                val consentIntent = e.intent
                if (consentIntent != null) {
                    _setupState.value = DriveSetupState.NeedsUserConsent(
                        consentIntent = consentIntent,
                        email = email,
                        displayName = displayName,
                        isSecondary = isSecondary
                    )
                    return@withContext Result.failure(e)
                } else throw e
            } catch (e: Exception) {
                AppErrorLogger.logError("DriveSetup", "OAuth Token গ্রহণ ব্যর্থ ($slotName): ${e.localizedMessage}", e)
                val errorMsg = "$slotName OAuth অনুমোদন পাওয়া যায়নি: ${e.localizedMessage}"
                _setupState.value = DriveSetupState.Error(errorMsg)
                return@withContext Result.failure(e)
            }

            val sanitizedSchoolFolderName = if (schoolName.isBlank() || schoolName == "School_Data_Storage") {
                if (isSecondary) "School_Data_Storage_Backup" else "School_Data_Storage"
            } else {
                val clean = schoolName.trim().replace(Regex("[^a-zA-Z0-9\u0980-\u09FF]"), "_")
                if (isSecondary) "${clean}_Backup_Storage" else "${clean}_Data_Storage"
            }

            _setupState.value = DriveSetupState.Loading("$slotName এ স্কুলের ফোল্ডার যাচাই করা হচ্ছে...")
            val folderResult = createOrFindSchoolFolder(accessToken, sanitizedSchoolFolderName, autoCreateIfNotFound = createFolderIfMissing)

            try {
                createOrUpdateSystemInfoFile(accessToken, folderResult.id, schoolName, email, isSecondary)
            } catch (e: Exception) {
                Log.w(TAG, "Could not write system_info file inside folder", e)
            }

            val info = ConnectedDriveAccountInfo(
                email = email,
                displayName = displayName,
                folderId = folderResult.id,
                folderName = folderResult.name,
                folderWebViewLink = folderResult.webViewLink,
                connectedAt = System.currentTimeMillis(),
                isSecondary = isSecondary
            )

            if (isSecondary) {
                saveSecondaryAccountInfo(info)
                _secondaryAccount.value = info
            } else {
                savePrimaryAccountInfo(info)
                _primaryAccount.value = info
            }

            _setupState.value = DriveSetupState.Success(
                email = email,
                displayName = displayName,
                folderId = folderResult.id,
                folderName = folderResult.name,
                folderWebViewLink = folderResult.webViewLink,
                message = if (folderResult.isNewlyCreated) {
                    "$slotName এ নতুন ফোল্ডার '${folderResult.name}' সফলভাবে তৈরি হয়েছে!"
                } else {
                    "$slotName এ '${folderResult.name}' ফোল্ডারের সাথে সফলভাবে সংযুক্ত হয়েছে!"
                },
                isSecondary = isSecondary
            )

            AppErrorLogger.logInfo("DriveSetup", "$slotName সফলভাবে সংযুক্ত: $email (Folder: ${folderResult.name})")
            Result.success(info)
        } catch (e: Exception) {
            AppErrorLogger.logError("DriveSetup", "$slotName সেটআপ ব্যর্থ: ${e.localizedMessage}", e)
            val error = e.localizedMessage ?: "অজানা ত্রুটি ঘটেছে।"
            _setupState.value = DriveSetupState.Error("ফোল্ডার তৈরিতে ব্যর্থতা: $error")
            Result.failure(e)
        }
    }

    private data class FolderQueryResult(
        val id: String,
        val name: String,
        val webViewLink: String?,
        val isNewlyCreated: Boolean
    )

    private fun createOrFindSchoolFolder(
        accessToken: String,
        targetFolderName: String,
        autoCreateIfNotFound: Boolean = true
    ): FolderQueryResult {
        // 1. Direct search by targetFolderName
        val queryUrl = "https://www.googleapis.com/drive/v3/files" +
                "?q=" + Uri.encode("mimeType = 'application/vnd.google-apps.folder' and name = '$targetFolderName' and trashed = false") +
                "&fields=" + Uri.encode("files(id, name, webViewLink)")

        val searchRequest = Request.Builder()
            .url(queryUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val searchResponse = httpClient.newCall(searchRequest).execute()
        val searchBody = searchResponse.body?.string() ?: ""

        if (searchResponse.isSuccessful) {
            val json = JSONObject(searchBody)
            val filesArray = json.optJSONArray("files")
            if (filesArray != null && filesArray.length() > 0) {
                val existing = filesArray.getJSONObject(0)
                val folderId = existing.getString("id")
                val name = existing.optString("name", targetFolderName)
                val webLink = existing.optString("webViewLink", "https://drive.google.com/drive/folders/$folderId")
                return FolderQueryResult(folderId, name, webLink, isNewlyCreated = false)
            }
        }

        // 2. Broad search for any existing school backup storage folder on the user's Drive
        try {
            val broadQueryUrl = "https://www.googleapis.com/drive/v3/files" +
                    "?q=" + Uri.encode("mimeType = 'application/vnd.google-apps.folder' and trashed = false and (name contains 'Data_Storage' or name contains 'School' or name contains 'বিদ্যালয়' or name contains 'বিদ্যালয়' or name contains 'প্রাথমিক' or name contains 'Storage' or name contains 'Backup')") +
                    "&orderBy=" + Uri.encode("modifiedTime desc") +
                    "&pageSize=20&fields=" + Uri.encode("files(id, name, webViewLink)")
            val broadReq = Request.Builder().url(broadQueryUrl).addHeader("Authorization", "Bearer $accessToken").get().build()
            val broadResp = httpClient.newCall(broadReq).execute()
            val broadBody = broadResp.body?.string() ?: ""
            if (broadResp.isSuccessful) {
                val json = JSONObject(broadBody)
                val filesArray = json.optJSONArray("files")
                if (filesArray != null && filesArray.length() > 0) {
                    val existing = filesArray.getJSONObject(0)
                    val folderId = existing.getString("id")
                    val name = existing.optString("name", targetFolderName)
                    val webLink = existing.optString("webViewLink", "https://drive.google.com/drive/folders/$folderId")
                    return FolderQueryResult(folderId, name, webLink, isNewlyCreated = false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Broad folder search warning: ${e.message}")
        }

        if (!autoCreateIfNotFound) {
            throw Exception("Google Drive-এ কোনো পূর্ববর্তী সংরক্ষিত ফোল্ডার পাওয়া যায়নি")
        }

        val createUrl = "https://www.googleapis.com/drive/v3/files?fields=id,name,webViewLink"
        val payload = JSONObject().apply {
            put("name", targetFolderName)
            put("mimeType", "application/vnd.google-apps.folder")
            put("description", "Dedicated storage folder for School Management Application data & backups")
        }

        val createRequest = Request.Builder()
            .url(createUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val createResponse = httpClient.newCall(createRequest).execute()
        val createBody = createResponse.body?.string() ?: ""

        if (!createResponse.isSuccessful) {
            throw Exception("Google Drive API Error (${createResponse.code}): $createBody")
        }

        val createdJson = JSONObject(createBody)
        val newFolderId = createdJson.getString("id")
        val folderName = createdJson.optString("name", targetFolderName)
        val webLink = createdJson.optString("webViewLink", "https://drive.google.com/drive/folders/$newFolderId")

        return FolderQueryResult(newFolderId, folderName, webLink, isNewlyCreated = true)
    }

    private fun createOrUpdateSystemInfoFile(
        accessToken: String,
        folderId: String,
        schoolName: String,
        email: String,
        isSecondary: Boolean
    ) {
        val fileName = if (isSecondary) "school_system_info_secondary.json" else "school_system_info.json"
        val queryUrl = "https://www.googleapis.com/drive/v3/files" +
                "?q=" + Uri.encode("'$folderId' in parents and name = '$fileName' and trashed = false") +
                "&fields=" + Uri.encode("files(id)")

        val searchReq = Request.Builder()
            .url(queryUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val searchResp = httpClient.newCall(searchReq).execute()
        val searchBody = searchResp.body?.string() ?: ""
        var existingFileId: String? = null

        if (searchResp.isSuccessful) {
            val json = JSONObject(searchBody)
            val filesArray = json.optJSONArray("files")
            if (filesArray != null && filesArray.length() > 0) {
                existingFileId = filesArray.getJSONObject(0).optString("id")
            }
        }

        val currentTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val contentJson = JSONObject().apply {
            put("application", "School Management System")
            put("schoolName", schoolName)
            put("connectedAccount", email)
            put("accountType", if (isSecondary) "SECONDARY_BACKUP" else "PRIMARY_MAIN")
            put("folderId", folderId)
            put("lastUpdated", currentTimeStr)
            put("status", "READY_FOR_BACKUP_AND_SYNC")
            put("note", "This folder contains school data and backups created by the School Management App.")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = contentJson.toString(2).toRequestBody(mediaType)

        if (existingFileId != null) {
            val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
            val updateReq = Request.Builder()
                .url(updateUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()
            httpClient.newCall(updateReq).execute()
        } else {
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val metaJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().apply { put(folderId) })
                put("mimeType", "application/json")
            }
            val createReq = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metaJson.toString().toRequestBody(mediaType))
                .build()
            val createResp = httpClient.newCall(createReq).execute()
            val createRespBody = createResp.body?.string() ?: ""
            if (createResp.isSuccessful) {
                val createdId = JSONObject(createRespBody).getString("id")
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$createdId?uploadType=media"
                val uploadReq = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .patch(requestBody)
                    .build()
                httpClient.newCall(uploadReq).execute()
            }
        }
    }

    // ==========================================
    // DIRECT .DB SQLITE SNAPSHOT & UPLOAD
    // ==========================================

    /**
     * Creates a solid SQLite snapshot of the active Room database and uploads `anwesha_school_db.db`
     * directly into the visible Google Drive folder (and updates if exists).
     */
    suspend fun uploadDirectDatabaseToDriveFolder(
        isSecondary: Boolean = false
    ): Result<DirectDbUploadResult> = withContext(Dispatchers.IO) {
        val targetAccount = if (isSecondary) _secondaryAccount.value else _primaryAccount.value
        if (targetAccount == null) {
            val msg = if (isSecondary) "দ্বিতীয় ড্রাইভ অ্যাকাউন্ট সংযুক্ত নেই" else "মূল ড্রাইভ অ্যাকাউন্ট সংযুক্ত নেই"
            return@withContext Result.failure(Exception(msg))
        }

        val token = getValidAccessToken(isSecondary)
            ?: return@withContext Result.failure(Exception("ড্রাইভ অ্যাক্সেস টোকেন পাওয়া যায়নি"))

        try {
            AppErrorLogger.logInfo("DbDirectUpload", "সরাসরি .db ব্যাকআপ শুরু হচ্ছে... (${targetAccount.email})")

            // 1. Force Room DB checkpoint
            try {
                val db = AppDatabase.getDatabase(context)
                val cursor = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                cursor.moveToFirst()
                cursor.close()
                AppErrorLogger.logInfo("DbDirectUpload", "SQLite WAL Checkpoint সম্পন্ন হয়েছে")
            } catch (e: Exception) {
                Log.w(TAG, "WAL checkpoint warning: ${e.message}")
            }

            // 2. Locate DB File
            val dbFile = context.getDatabasePath("anwesha_school_db")
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return@withContext Result.failure(Exception("ডাটাবেস ফাইল পাওয়া যায়নি বা ফাইল খালি (${dbFile.absolutePath})"))
            }

            // Create temporary copy for safe uploading
            val tempDir = File(context.cacheDir, "db_upload_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempDbFile = File(tempDir, "anwesha_school_db.db")
            
            FileInputStream(dbFile).use { input ->
                FileOutputStream(tempDbFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileSizeKb = tempDbFile.length() / 1024
            val fileSizeFormatted = if (fileSizeKb > 1024) String.format(Locale.US, "%.2f MB", fileSizeKb / 1024.0) else "$fileSizeKb KB"
            val targetFileName = "anwesha_school_db.db"

            AppErrorLogger.logInfo("DbDirectUpload", "আপলোডযোগ্য ডাটাবেস ফাইল প্রস্তুত: $targetFileName ($fileSizeFormatted)")

            // 3. Check if an existing file with same name exists in the folder
            val queryUrl = "https://www.googleapis.com/drive/v3/files" +
                    "?q=" + Uri.encode("'${targetAccount.folderId}' in parents and name = '$targetFileName' and trashed = false") +
                    "&fields=" + Uri.encode("files(id, name, webViewLink)")

            val searchReq = Request.Builder()
                .url(queryUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val searchResp = httpClient.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string() ?: ""
            var existingFileId: String? = null
            var existingWebLink: String? = null

            if (searchResp.isSuccessful) {
                val json = JSONObject(searchBody)
                val filesArr = json.optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    val f = filesArr.getJSONObject(0)
                    existingFileId = f.getString("id")
                    existingWebLink = f.optString("webViewLink")
                }
            }

            val uploadedFileId: String
            val webViewLink: String

            val fileRequestBody = tempDbFile.asRequestBody("application/x-sqlite3".toMediaType())

            if (existingFileId != null) {
                // Update existing file in folder
                AppErrorLogger.logInfo("DbDirectUpload", "ড্রাইভ ফোল্ডারে পূর্বের .db ফাইল আপডেট করা হচ্ছে (ID: $existingFileId)")
                val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                val updateReq = Request.Builder()
                    .url(updateUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .patch(fileRequestBody)
                    .build()

                val updateResp = httpClient.newCall(updateReq).execute()
                val updateBody = updateResp.body?.string() ?: ""
                if (!updateResp.isSuccessful) {
                    throw Exception("আপডেট ব্যর্থ: HTTP ${updateResp.code} - $updateBody")
                }
                uploadedFileId = existingFileId
                webViewLink = existingWebLink ?: "https://drive.google.com/file/d/$uploadedFileId/view"
            } else {
                // Create new file inside the school folder
                AppErrorLogger.logInfo("DbDirectUpload", "ড্রাইভ ফোল্ডারে নতুন .db ফাইল তৈরি করা হচ্ছে...")
                
                val metaJson = JSONObject().apply {
                    put("name", targetFileName)
                    put("parents", JSONArray().apply { put(targetAccount.folderId) })
                    put("description", "Full Room SQLite Database snapshot of ANWESHA School Management")
                }

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", null, metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addFormDataPart("file", targetFileName, fileRequestBody)
                    .build()

                val createUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink"
                val createReq = Request.Builder()
                    .url(createUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                val createResp = httpClient.newCall(createReq).execute()
                val createBody = createResp.body?.string() ?: ""
                if (!createResp.isSuccessful) {
                    throw Exception("আপলোড ব্যর্থ: HTTP ${createResp.code} - $createBody")
                }

                val createdJson = JSONObject(createBody)
                uploadedFileId = createdJson.getString("id")
                webViewLink = createdJson.optString("webViewLink", "https://drive.google.com/file/d/$uploadedFileId/view")
            }

            tempDbFile.delete()

            val uploadResult = DirectDbUploadResult(
                fileId = uploadedFileId,
                fileName = targetFileName,
                fileSizeFormatted = fileSizeFormatted,
                folderId = targetAccount.folderId,
                webViewLink = webViewLink,
                uploadTimestamp = System.currentTimeMillis()
            )

            // Save upload info
            prefs.edit()
                .putString(KEY_LAST_DB_FILE_ID, uploadedFileId)
                .putLong(KEY_LAST_DB_UPLOAD_TIME, uploadResult.uploadTimestamp)
                .putString(KEY_LAST_DB_SIZE, fileSizeFormatted)
                .apply()

            _lastDbUploadInfo.value = uploadResult
            AppErrorLogger.logInfo("DbDirectUpload", "সফলভাবে .db ব্যাকআপ ড্রাইভে সম্পন্ন হয়েছে (File ID: $uploadedFileId, Size: $fileSizeFormatted)")
            Result.success(uploadResult)
        } catch (e: Exception) {
            AppErrorLogger.logError("DbDirectUpload", "সরাসরি .db আপলোড ব্যর্থ: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads and restores a full SQLite database snapshot (.db) from Google Drive.
     */
    suspend fun downloadAndRestoreDirectDatabase(
        isSecondary: Boolean = false,
        targetFileId: String? = null,
        onProgress: (String) -> Unit = {}
    ): Result<DirectDbRestoreResult> = withContext(Dispatchers.IO) {
        val targetAccount = if (isSecondary) _secondaryAccount.value else _primaryAccount.value
        if (targetAccount == null) {
            val msg = if (isSecondary) "দ্বিতীয় ড্রাইভ অ্যাকাউন্ট সংযুক্ত নেই" else "মূল ড্রাইভ অ্যাকাউন্ট সংযুক্ত নেই"
            return@withContext Result.failure(Exception(msg))
        }

        val token = getValidAccessToken(isSecondary)
            ?: return@withContext Result.failure(Exception("ড্রাইভ অ্যাক্সেস টোকেন পাওয়া যায়নি"))

        try {
            onProgress("ড্রাইভে .db ব্যাকআপ ফাইল অনুসন্ধান করা হচ্ছে...")
            AppErrorLogger.logInfo("DbDirectRestore", "সরাসরি .db রিস্টোর শুরু হচ্ছে... (${targetAccount.email})")

            var dbFileId = targetFileId
            var dbFileName = "anwesha_school_db.db"

            if (dbFileId.isNullOrBlank()) {
                // Search inside folder first
                val folderId = targetAccount.folderId
                val queryUrl = "https://www.googleapis.com/drive/v3/files" +
                        "?q=" + Uri.encode("'$folderId' in parents and (name contains '.db' or name contains 'anwesha') and trashed = false") +
                        "&fields=" + Uri.encode("files(id, name, size, modifiedTime)") +
                        "&orderBy=modifiedTime desc"

                val searchReq = Request.Builder()
                    .url(queryUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val searchResp = httpClient.newCall(searchReq).execute()
                val searchBody = searchResp.body?.string() ?: ""

                if (searchResp.isSuccessful) {
                    val json = JSONObject(searchBody)
                    val filesArr = json.optJSONArray("files")
                    if (filesArr != null && filesArr.length() > 0) {
                        val f = filesArr.getJSONObject(0)
                        dbFileId = f.getString("id")
                        dbFileName = f.optString("name", "anwesha_school_db.db")
                    }
                }

                // If not found in folder, search globally in user's Drive
                if (dbFileId.isNullOrBlank()) {
                    val globalUrl = "https://www.googleapis.com/drive/v3/files" +
                            "?q=" + Uri.encode("(name = 'anwesha_school_db.db' or (name contains 'anwesha' and name contains '.db')) and trashed = false") +
                            "&fields=" + Uri.encode("files(id, name, size, modifiedTime)") +
                            "&orderBy=modifiedTime desc"

                    val gReq = Request.Builder()
                        .url(globalUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .get()
                        .build()
                    val gResp = httpClient.newCall(gReq).execute()
                    val gBody = gResp.body?.string() ?: ""
                    if (gResp.isSuccessful) {
                        val json = JSONObject(gBody)
                        val filesArr = json.optJSONArray("files")
                        if (filesArr != null && filesArr.length() > 0) {
                            val f = filesArr.getJSONObject(0)
                            dbFileId = f.getString("id")
                            dbFileName = f.optString("name", "anwesha_school_db.db")
                        }
                    }
                }
            }

            if (dbFileId.isNullOrBlank()) {
                return@withContext Result.failure(Exception("গুগল ড্রাইভে কোনো .db ডাটাবেস ব্যাকআপ ফাইল পাওয়া যায়নি"))
            }

            onProgress("গুগল ড্রাইভ থেকে .db ফাইল ডাউনলোড করা হচ্ছে...")
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$dbFileId?alt=media"
            val downloadReq = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val downloadResp = httpClient.newCall(downloadReq).execute()
            if (!downloadResp.isSuccessful) {
                return@withContext Result.failure(Exception("ডাউনলোড ব্যর্থ: HTTP ${downloadResp.code}"))
            }

            val tempDir = File(context.cacheDir, "db_restore_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempDownloadedDb = File(tempDir, "downloaded_restore.db")

            downloadResp.body?.byteStream()?.use { input ->
                FileOutputStream(tempDownloadedDb).use { output ->
                    input.copyTo(output)
                }
            }

            val fileSizeKb = tempDownloadedDb.length() / 1024
            val fileSizeFormatted = if (fileSizeKb > 1024) String.format(Locale.US, "%.2f MB", fileSizeKb / 1024.0) else "$fileSizeKb KB"

            onProgress("ডাটাবেসের নির্ভুলতা যাচাই ও প্রতিস্থাপন করা হচ্ছে...")

            val replaceResult = replaceLocalDatabaseWithFile(tempDownloadedDb)
            tempDownloadedDb.delete()

            if (replaceResult.isFailure) {
                return@withContext Result.failure(replaceResult.exceptionOrNull() ?: Exception("ডাটাবেস প্রতিস্থাপন ব্যর্থ হয়েছে"))
            }

            val restoreResult = DirectDbRestoreResult(
                fileName = dbFileName,
                fileSizeFormatted = fileSizeFormatted,
                restoredTimestamp = System.currentTimeMillis(),
                success = true,
                message = "সফলভাবে .db ডাটাবেস রিস্টোর সম্পন্ন হয়েছে ($fileSizeFormatted)"
            )

            AppErrorLogger.logInfo("DbDirectRestore", "সফলভাবে .db ফাইল থেকে ডাটাবেস রিস্টোর সম্পন্ন হয়েছে ($fileSizeFormatted)")
            Result.success(restoreResult)
        } catch (e: Exception) {
            AppErrorLogger.logError("DbDirectRestore", "সরাসরি .db রিস্টোর ত্রুটি: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    fun isValidSqliteDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        var db: android.database.sqlite.SQLiteDatabase? = null
        return try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table'", null)
            var tableCount = 0
            if (cursor.moveToFirst()) {
                tableCount = cursor.getInt(0)
            }
            cursor.close()
            tableCount > 0
        } catch (e: Exception) {
            false
        } finally {
            try { db?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun replaceLocalDatabaseWithFile(sourceDbFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!isValidSqliteDatabase(sourceDbFile)) {
                return@withContext Result.failure(Exception("ফাইলের ফরম্যাট বৈধ SQLite ডাটাবেস নয়"))
            }

            // 1. Close active Room DB
            AppDatabase.resetDatabaseInstance()

            // 2. Locate target db
            val targetDbFile = context.getDatabasePath("anwesha_school_db")
            targetDbFile.parentFile?.mkdirs()

            // Delete wal, shm, journal
            try {
                File(targetDbFile.path + "-wal").delete()
                File(targetDbFile.path + "-shm").delete()
                File(targetDbFile.path + "-journal").delete()
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup journal error: ${e.message}")
            }

            // Copy over
            FileInputStream(sourceDbFile).use { input ->
                FileOutputStream(targetDbFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 3. Re-open database instance and trigger vault save
            val newDb = AppDatabase.getDatabase(context)
            val studentCount = newDb.studentDao().getStudentCountSync()
            AppErrorLogger.logInfo("DbDirectRestore", "সফলভাবে স্থানীয় ডাটাবেস আপডেট হয়েছে ($studentCount জন শিক্ষার্থী)")
            InternalAutoBackupManager.getInstance(context).saveInternalSnapshot(newDb)
            Result.success(true)
        } catch (e: Exception) {
            AppErrorLogger.logError("DbDirectRestore", "ডাটাবেস ফাইল প্রতিস্থাপন ত্রুটি: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun restoreDatabaseFromUri(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "db_uri_restore")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, "temp_uri_db.db")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val result = replaceLocalDatabaseWithFile(tempFile)
            tempFile.delete()
            result
        } catch (e: Exception) {
            AppErrorLogger.logError("DbDirectRestore", "URI থেকে ডাটাবেস রিস্টোর ত্রুটি: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private fun savePrimaryAccountInfo(info: ConnectedDriveAccountInfo) {
        prefs.edit()
            .putBoolean(KEY_IS_CONNECTED, true)
            .putString(KEY_EMAIL, info.email)
            .putString(KEY_DISPLAY_NAME, info.displayName)
            .putString(KEY_FOLDER_ID, info.folderId)
            .putString(KEY_FOLDER_NAME, info.folderName)
            .putString(KEY_FOLDER_LINK, info.folderWebViewLink)
            .putLong(KEY_CONNECTED_TIME, info.connectedAt)
            .apply()
    }

    private fun saveSecondaryAccountInfo(info: ConnectedDriveAccountInfo) {
        prefs.edit()
            .putBoolean(KEY_SEC_IS_CONNECTED, true)
            .putString(KEY_SEC_EMAIL, info.email)
            .putString(KEY_SEC_DISPLAY_NAME, info.displayName)
            .putString(KEY_SEC_FOLDER_ID, info.folderId)
            .putString(KEY_SEC_FOLDER_NAME, info.folderName)
            .putString(KEY_SEC_FOLDER_LINK, info.folderWebViewLink)
            .putLong(KEY_SEC_CONNECTED_TIME, info.connectedAt)
            .apply()
    }

    suspend fun getValidAccessToken(forSecondary: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val email = if (forSecondary) {
                prefs.getString(KEY_SEC_EMAIL, null)
            } else {
                prefs.getString(KEY_EMAIL, null)
            } ?: return@withContext null

            val androidAccount = Account(email, "com.google")
            val oauthScope = "oauth2:$DRIVE_SCOPE_FILE $DRIVE_SCOPE_APPDATA https://www.googleapis.com/auth/drive.readonly $DRIVE_SCOPE_USER_EMAIL $DRIVE_SCOPE_USER_PROFILE"
            GoogleAuthUtil.getToken(context, androidAccount, oauthScope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get valid access token (secondary=$forSecondary): ${e.message}")
            null
        }
    }

    fun disconnectPrimary(enteredPin: String?, onResult: (Boolean, String) -> Unit) {
        if (AppSecurityManager.isPasswordSet(context) && AppSecurityManager.isScopeProtected(context, AppSecurityScope.DRIVE_UNLINK)) {
            if (enteredPin.isNullOrBlank() || !AppSecurityManager.verifyPassword(context, enteredPin)) {
                onResult(false, "ভুল সিকিউরিটি পিন/পাসওয়ার্ড। আনলিঙ্ক করা সম্ভব হয়নি।")
                return
            }
        }

        prefs.edit()
            .remove(KEY_IS_CONNECTED)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_FOLDER_ID)
            .remove(KEY_FOLDER_NAME)
            .remove(KEY_FOLDER_LINK)
            .remove(KEY_CONNECTED_TIME)
            .apply()

        _primaryAccount.value = null
        _setupState.value = DriveSetupState.Idle
        AppErrorLogger.logInfo("DriveSetup", "মূল ড্রাইভ অ্যাকাউন্ট আনলিঙ্ক করা হয়েছে।")
        onResult(true, "মূল ড্রাইভ সংযোগ বিচ্ছিন্ন করা হয়েছে।")
    }

    fun disconnectSecondary(enteredPin: String?, onResult: (Boolean, String) -> Unit) {
        if (AppSecurityManager.isPasswordSet(context) && AppSecurityManager.isScopeProtected(context, AppSecurityScope.DRIVE_UNLINK)) {
            if (enteredPin.isNullOrBlank() || !AppSecurityManager.verifyPassword(context, enteredPin)) {
                onResult(false, "ভুল সিকিউরিটি পিন/পাসওয়ার্ড। আনলিঙ্ক করা সম্ভব হয়নি।")
                return
            }
        }

        prefs.edit()
            .remove(KEY_SEC_IS_CONNECTED)
            .remove(KEY_SEC_EMAIL)
            .remove(KEY_SEC_DISPLAY_NAME)
            .remove(KEY_SEC_FOLDER_ID)
            .remove(KEY_SEC_FOLDER_NAME)
            .remove(KEY_SEC_FOLDER_LINK)
            .remove(KEY_SEC_CONNECTED_TIME)
            .apply()

        _secondaryAccount.value = null
        AppErrorLogger.logInfo("DriveSetup", "দ্বিতীয় ড্রাইভ অ্যাকাউন্ট আনলিঙ্ক করা হয়েছে।")
        onResult(true, "দ্বিতীয় ড্রাইভ সংযোগ বিচ্ছিন্ন করা হয়েছে।")
    }

    fun clearAllAccountsDirect() {
        prefs.edit()
            .remove(KEY_IS_CONNECTED)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_FOLDER_ID)
            .remove(KEY_FOLDER_NAME)
            .remove(KEY_FOLDER_LINK)
            .remove(KEY_CONNECTED_TIME)
            .remove(KEY_SEC_IS_CONNECTED)
            .remove(KEY_SEC_EMAIL)
            .remove(KEY_SEC_DISPLAY_NAME)
            .remove(KEY_SEC_FOLDER_ID)
            .remove(KEY_SEC_FOLDER_NAME)
            .remove(KEY_SEC_FOLDER_LINK)
            .remove(KEY_SEC_CONNECTED_TIME)
            .apply()
        _primaryAccount.value = null
        _secondaryAccount.value = null
        _setupState.value = DriveSetupState.Idle
    }

    fun applyDiscoveredAccount(discovery: CloudBackupDiscoveryResult, account: GoogleSignInAccount) {
        val email = account.email ?: ""
        val displayName = account.displayName ?: email
        val updatedPrimary = ConnectedDriveAccountInfo(
            email = email,
            displayName = displayName,
            folderId = discovery.folderId,
            folderName = discovery.folderName.ifBlank { "School_Data_Storage" },
            folderWebViewLink = null,
            connectedAt = System.currentTimeMillis()
        )
        savePrimaryAccountInfo(updatedPrimary)
        _primaryAccount.value = updatedPrimary
    }

    fun clearStatusState() {
        _setupState.value = DriveSetupState.Idle
    }

    fun getAutoSyncMode(): com.example.data.model.AutoSyncMode {
        val key = prefs.getString(KEY_AUTO_SYNC_MODE, com.example.data.model.AutoSyncMode.ON_DATA_CHANGE.key)
        return com.example.data.model.AutoSyncMode.fromKey(key)
    }

    fun saveAutoSyncMode(mode: com.example.data.model.AutoSyncMode) {
        prefs.edit().putString(KEY_AUTO_SYNC_MODE, mode.key).apply()
    }

    fun isSyncImagesEnabled(): Boolean {
        return prefs.getBoolean(KEY_SYNC_IMAGES, true)
    }

    fun saveSyncImagesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_IMAGES, enabled).apply()
    }

    fun isSyncPdfsEnabled(): Boolean {
        return prefs.getBoolean(KEY_SYNC_PDFS, true)
    }

    fun saveSyncPdfsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_PDFS, enabled).apply()
    }

    fun getLastAutoSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_AUTO_SYNC_TIME, 0L)
    }

    fun saveLastAutoSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_AUTO_SYNC_TIME, timestamp).apply()
    }

    /**
     * Searches the user's Google Drive account for existing School backups
     * (such as School_Data_Storage folders, backup_manifest.json, school_profile.json, or .db snapshots).
     */
    suspend fun searchExistingSchoolBackups(account: GoogleSignInAccount): CloudBackupDiscoveryResult = withContext(Dispatchers.IO) {
        try {
            val email = account.email ?: "Unknown Email"
            val androidAccount: Account = account.account ?: Account(email, "com.google")
            val oauthScope = "oauth2:$DRIVE_SCOPE_FILE $DRIVE_SCOPE_APPDATA https://www.googleapis.com/auth/drive.readonly $DRIVE_SCOPE_USER_EMAIL $DRIVE_SCOPE_USER_PROFILE"
            val accessToken = GoogleAuthUtil.getToken(context, androidAccount, oauthScope)

            var candidateFolderId: String? = null
            var candidateFolderName = ""
            var candidateFolderWebViewLink = ""

            // 1. Search for Candidate Folders on Drive (all recent folders)
            val candidateFoldersList = mutableListOf<JSONObject>()
            try {
                val folderFields = Uri.encode("files(id, name, modifiedTime, webViewLink)")
                val folderReqUrl = "https://www.googleapis.com/drive/v3/files?q=" +
                        Uri.encode("mimeType = 'application/vnd.google-apps.folder' and trashed = false") +
                        "&orderBy=" + Uri.encode("modifiedTime desc") +
                        "&fields=$folderFields&pageSize=50"
                val folderReq = Request.Builder().url(folderReqUrl).addHeader("Authorization", "Bearer $accessToken").get().build()
                val folderResp = httpClient.newCall(folderReq).execute()
                val folderBody = folderResp.body?.string() ?: ""

                if (folderResp.isSuccessful) {
                    val json = JSONObject(folderBody)
                    val files = json.optJSONArray("files")
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            candidateFoldersList.add(files.getJSONObject(i))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying candidate folders: ${e.message}")
            }

            // Filter or prioritize folders that look like school storage folders
            for (f in candidateFoldersList) {
                val name = f.optString("name", "")
                if (name.contains("Data_Storage") || name.contains("School") || name.contains("Backup") ||
                    name.contains("বিদ্যালয়") || name.contains("বিদ্যালয়") || name.contains("প্রাথমিক") ||
                    name.contains("স্কুল") || name.contains("নং") || name.contains("কটুরাকান্দি") || name.contains("Anwesha")) {
                    candidateFolderId = f.getString("id")
                    candidateFolderName = name
                    candidateFolderWebViewLink = f.optString("webViewLink", "")
                    break
                }
            }
            if (candidateFolderId == null && candidateFoldersList.isNotEmpty()) {
                val first = candidateFoldersList.first()
                candidateFolderId = first.getString("id")
                candidateFolderName = first.optString("name", "")
                candidateFolderWebViewLink = first.optString("webViewLink", "")
            }

            // 2. Global search for backup files (manifest, profile, .db, students, settings, users, routines, zip, etc.)
            val allDiscoveredFiles = mutableListOf<JSONObject>()
            try {
                val fileQuery = Uri.encode(
                    "trashed = false and (" +
                    "name = 'backup_manifest.json' or " +
                    "name = 'anwesha_school_db.db' or " +
                    "name = 'school_profile.json' or " +
                    "name = 'school_system_info.json' or " +
                    "name = 'students_all.json' or " +
                    "name = 'settings_and_preferences.json' or " +
                    "name = 'school_users.json' or " +
                    "name = 'attendance_records.json' or " +
                    "name = 'routine_items.json' or " +
                    "name = 'document_templates.json' or " +
                    "name = 'custom_fields_and_formulas.json' or " +
                    "name contains 'students_class' or " +
                    "name contains 'anwesha' or " +
                    "name contains '.db' or " +
                    "name contains '.zip'" +
                    ")"
                )
                val fileFields = Uri.encode("files(id, name, modifiedTime, size, parents, mimeType, webViewLink)")
                val fileReqUrl = "https://www.googleapis.com/drive/v3/files?q=$fileQuery&fields=$fileFields&pageSize=100"
                val fileReq = Request.Builder().url(fileReqUrl).addHeader("Authorization", "Bearer $accessToken").get().build()
                val fileResp = httpClient.newCall(fileReq).execute()
                val fileBody = fileResp.body?.string() ?: ""

                if (fileResp.isSuccessful) {
                    val json = JSONObject(fileBody)
                    val files = json.optJSONArray("files")
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            allDiscoveredFiles.add(files.getJSONObject(i))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying backup files globally: ${e.message}")
            }

            // If candidateFolderId is still null, look up parents from discovered files
            if (candidateFolderId == null) {
                for (f in allDiscoveredFiles) {
                    val parents = f.optJSONArray("parents")
                    if (parents != null && parents.length() > 0) {
                        val pId = parents.getString(0)
                        if (pId.isNotBlank() && pId != "root") {
                            candidateFolderId = pId
                            break
                        }
                    }
                }
            }

            // If we found a candidate folder ID, fetch its metadata (name & webViewLink) and any other files inside it
            if (candidateFolderId != null) {
                try {
                    val folderInfoUrl = "https://www.googleapis.com/drive/v3/files/$candidateFolderId?fields=" + Uri.encode("id,name,webViewLink")
                    val req = Request.Builder().url(folderInfoUrl).addHeader("Authorization", "Bearer $accessToken").get().build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val fObj = JSONObject(body)
                        candidateFolderName = fObj.optString("name", candidateFolderName)
                        candidateFolderWebViewLink = fObj.optString("webViewLink", candidateFolderWebViewLink)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching candidate folder details: ${e.message}")
                }

                try {
                    val insideQuery = Uri.encode("'$candidateFolderId' in parents and trashed = false")
                    val insideFields = Uri.encode("files(id, name, modifiedTime, size, parents, mimeType, webViewLink)")
                    val insideUrl = "https://www.googleapis.com/drive/v3/files?q=$insideQuery&fields=$insideFields&pageSize=100"
                    val req = Request.Builder().url(insideUrl).addHeader("Authorization", "Bearer $accessToken").get().build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        val files = json.optJSONArray("files")
                        if (files != null) {
                            for (i in 0 until files.length()) {
                                val item = files.getJSONObject(i)
                                val itemId = item.getString("id")
                                if (allDiscoveredFiles.none { it.getString("id") == itemId }) {
                                    allDiscoveredFiles.add(item)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching files inside candidate folder: ${e.message}")
                }
            }

            var profileFileId: String? = null
            var manifestFileId: String? = null
            var dbFileId: String? = null
            var dbFileSizeBytes = 0L
            var dbFileName = "anwesha_school_db.db"
            var studentClassFilesCount = 0

            for (f in allDiscoveredFiles) {
                val name = f.optString("name")
                val id = f.optString("id")
                val size = f.optLong("size", 0L)

                if (name == "school_profile.json" || (name.contains("school_profile") && name.endsWith(".json"))) {
                    profileFileId = id
                }
                if (name == "backup_manifest.json") {
                    manifestFileId = id
                }
                if (name.endsWith(".db") || name.contains("anwesha_school_db")) {
                    dbFileId = id
                    dbFileName = name
                    dbFileSizeBytes = size
                }
                if (name.contains("students_class") || name.contains("students_all") || name == "students.json") {
                    studentClassFilesCount++
                }
            }

            if (profileFileId == null && manifestFileId == null && dbFileId == null && allDiscoveredFiles.isEmpty()) {
                return@withContext CloudBackupDiscoveryResult(
                    found = false,
                    message = "এই গুগল ড্রাইভে কোনো পূর্বের ব্যাকআপ বা স্কুলের তথ্য পাওয়া যায়নি।"
                )
            }

            // Extract detailed metadata from school_profile.json or backup_manifest.json
            var foundSchoolName = if (candidateFolderName.isNotBlank() && candidateFolderName != "School_Data_Storage") {
                candidateFolderName
                    .replace("_Data_Storage", "")
                    .replace("_Backup_Storage", "")
                    .replace("Data_Storage", "")
                    .replace("_", " ")
                    .trim()
            } else {
                "সংরক্ষিত বিদ্যালয়"
            }
            var foundEiin = ""
            var foundStudentCount = 0
            var backupTs = System.currentTimeMillis()

            if (profileFileId != null) {
                try {
                    val readReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$profileFileId?alt=media")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .get()
                        .build()
                    val readResp = httpClient.newCall(readReq).execute()
                    val profileContent = readResp.body?.string() ?: ""
                    if (profileContent.isNotBlank()) {
                        val pJson = JSONObject(profileContent)
                        val sn = pJson.optString("schoolName", pJson.optString("name", ""))
                        if (sn.isNotBlank()) foundSchoolName = sn
                        foundEiin = pJson.optString("eiinCode", pJson.optString("emisCode", pJson.optString("eiin", "")))
                        if (pJson.has("studentCount")) foundStudentCount = pJson.optInt("studentCount", 0)
                        if (pJson.has("totalStudents")) foundStudentCount = pJson.optInt("totalStudents", foundStudentCount)
                        if (pJson.has("updatedAt")) backupTs = pJson.optLong("updatedAt", backupTs)
                        if (pJson.has("timestamp")) backupTs = pJson.optLong("timestamp", backupTs)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read school_profile.json: ${e.message}")
                }
            }

            if (manifestFileId != null) {
                try {
                    val readReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$manifestFileId?alt=media")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .get()
                        .build()
                    val readResp = httpClient.newCall(readReq).execute()
                    val manifestContent = readResp.body?.string() ?: ""
                    if (manifestContent.isNotBlank()) {
                        val mJson = JSONObject(manifestContent)
                        val sn = mJson.optString("schoolName", "")
                        if (sn.isNotBlank()) foundSchoolName = sn
                        val eiin = mJson.optString("eiinCode", "")
                        if (eiin.isNotBlank() && foundEiin.isBlank()) foundEiin = eiin
                        if (mJson.has("totalStudents")) {
                            foundStudentCount = mJson.optInt("totalStudents", foundStudentCount)
                        } else if (mJson.has("totalRecords")) {
                            foundStudentCount = mJson.optInt("totalRecords", foundStudentCount)
                        }
                        val ts = mJson.optLong("backupTimestamp", mJson.optLong("timestamp", 0L))
                        if (ts > 0L) backupTs = ts
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read backup_manifest.json: ${e.message}")
                }
            }

            val sdf = SimpleDateFormat("dd MMMM, yyyy (hh:mm a)", Locale.getDefault())
            val formattedDate = BanglaUtils.toBanglaDigits(sdf.format(Date(backupTs)))

            val dbSizeFormatted = if (dbFileSizeBytes > 0) {
                val kb = dbFileSizeBytes / 1024.0
                if (kb >= 1024) String.format(Locale.US, "%.1f MB", kb / 1024.0) else String.format(Locale.US, "%.0f KB", kb)
            } else ""

            val hasDb = dbFileId != null
            val hasJson = profileFileId != null || manifestFileId != null || allDiscoveredFiles.any { it.optString("name").endsWith(".json") }
            val hasFolder = candidateFolderId != null && candidateFolderName.isNotBlank() && candidateFolderName != "root"
            val isFound = hasDb || hasJson || (hasFolder && candidateFolderName != "School_Data_Storage")

            val resolvedFolderId = candidateFolderId ?: ""
            val resolvedFolderName = candidateFolderName.ifBlank { "School_Data_Storage" }

            if (!isFound && allDiscoveredFiles.isEmpty()) {
                return@withContext CloudBackupDiscoveryResult(
                    found = false,
                    folderId = resolvedFolderId,
                    folderName = resolvedFolderName,
                    message = "এই গুগল ড্রাইভ অ্যাকাউন্টে পূর্বের কোনো সংরক্ষিত বিদ্যালয়ের ব্যাকআপ পাওয়া যায়নি।"
                )
            }

            AppErrorLogger.logInfo("DriveDiscovery", "ব্যাকআপ পাওয়া গেছে: $foundSchoolName ($resolvedFolderName, $resolvedFolderId, DB: $hasDb, JSON: $hasJson)")

            CloudBackupDiscoveryResult(
                found = true,
                schoolName = foundSchoolName,
                eiinCode = foundEiin,
                studentCount = foundStudentCount,
                backupTimestamp = backupTs,
                backupDateFormatted = formattedDate,
                folderId = resolvedFolderId,
                folderName = resolvedFolderName,
                dbFileId = dbFileId,
                dbFileName = dbFileName,
                dbFileSizeFormatted = dbSizeFormatted,
                hasDbBackup = hasDb,
                hasJsonBackup = hasJson,
                message = "গুগল ড্রাইভে পূর্বের সংরক্ষিত স্কুলের ব্যাকআপ পাওয়া গেছে!"
            )
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "UserRecoverableAuthException during drive search: ${e.message}")
            pendingGoogleAccount = account
            CloudBackupDiscoveryResult(
                found = false,
                consentIntent = e.intent,
                message = "গুগল ড্রাইভ স্ক্যান করতে অ্যাকাউন্টের অনুমতি (Consent) প্রয়োজন।"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching backups: ${e.message}", e)
            CloudBackupDiscoveryResult(
                found = false,
                message = "ড্রাইভ স্ক্যান করতে সমস্যা হয়েছে: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
