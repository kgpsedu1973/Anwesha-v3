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
                Scope(DRIVE_SCOPE_FILE)
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
        isSecondary: Boolean = false
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

            val sanitizedSchoolFolderName = if (schoolName.isBlank()) {
                if (isSecondary) "School_Data_Storage_Backup" else "School_Data_Storage"
            } else {
                val clean = schoolName.trim().replace(Regex("[^a-zA-Z0-9\u0980-\u09FF]"), "_")
                if (isSecondary) "${clean}_Backup_Storage" else "${clean}_Data_Storage"
            }

            _setupState.value = DriveSetupState.Loading("$slotName এ স্কুলের জন্য ফোল্ডার তৈরি বা যাচাই করা হচ্ছে...")
            val folderResult = createOrFindSchoolFolder(accessToken, sanitizedSchoolFolderName)

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
        targetFolderName: String
    ): FolderQueryResult {
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
            val oauthScope = "oauth2:$DRIVE_SCOPE_FILE $DRIVE_SCOPE_USER_EMAIL $DRIVE_SCOPE_USER_PROFILE"
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

    fun clearStatusState() {
        _setupState.value = DriveSetupState.Idle
    }
}
