package com.example.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class DriveSetupState {
    object Idle : DriveSetupState()
    data class Loading(val message: String) : DriveSetupState()
    data class Success(
        val email: String,
        val displayName: String,
        val folderId: String,
        val folderName: String,
        val folderWebViewLink: String?,
        val message: String
    ) : DriveSetupState()
    data class Error(val errorMessage: String) : DriveSetupState()
}

data class ConnectedDriveAccountInfo(
    val email: String,
    val displayName: String,
    val folderId: String,
    val folderName: String,
    val folderWebViewLink: String?,
    val connectedAt: Long
)

class GoogleDriveSetupManager(private val context: Context) {

    companion object {
        private const val TAG = "DriveSetupManager"
        private const val PREFS_NAME = "google_drive_school_prefs"
        private const val KEY_IS_CONNECTED = "key_is_connected"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_FOLDER_ID = "key_folder_id"
        private const val KEY_FOLDER_NAME = "key_folder_name"
        private const val KEY_FOLDER_LINK = "key_folder_link"
        private const val KEY_CONNECTED_TIME = "key_connected_time"

        const val DRIVE_SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_SCOPE_USER_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
        const val DRIVE_SCOPE_USER_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val _setupState = MutableStateFlow<DriveSetupState>(DriveSetupState.Idle)
    val setupState: StateFlow<DriveSetupState> = _setupState.asStateFlow()

    private val _connectedAccountInfo = MutableStateFlow<ConnectedDriveAccountInfo?>(loadSavedAccountInfo())
    val connectedAccountInfo: StateFlow<ConnectedDriveAccountInfo?> = _connectedAccountInfo.asStateFlow()

    private fun loadSavedAccountInfo(): ConnectedDriveAccountInfo? {
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
                connectedAt = prefs.getLong(KEY_CONNECTED_TIME, System.currentTimeMillis())
            )
        } else {
            null
        }
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(
                Scope(DRIVE_SCOPE_FILE),
                Scope(DRIVE_SCOPE_USER_EMAIL),
                Scope(DRIVE_SCOPE_USER_PROFILE)
            )
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent {
        val client = getGoogleSignInClient()
        // Sign out client first so account picker always appears allowing user to choose any phone Gmail
        client.signOut()
        return client.signInIntent
    }

    suspend fun handleSignInAccount(
        account: GoogleSignInAccount,
        schoolName: String = "School_Data_Storage"
    ): Result<ConnectedDriveAccountInfo> = withContext(Dispatchers.IO) {
        try {
            _setupState.value = DriveSetupState.Loading("গুগল অ্যাকাউন্ট যাচাই করা হচ্ছে...")
            val email = account.email ?: "Unknown Email"
            val displayName = account.displayName ?: email
            val androidAccount: Account? = account.account

            if (androidAccount == null) {
                val errorMsg = "অ্যাকাউন্ট সনাক্ত করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।"
                _setupState.value = DriveSetupState.Error(errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            _setupState.value = DriveSetupState.Loading("Google Drive অ্যাক্সেস টোকেন গ্রহণ করা হচ্ছে...")
            val oauthScope = "oauth2:$DRIVE_SCOPE_FILE $DRIVE_SCOPE_USER_EMAIL $DRIVE_SCOPE_USER_PROFILE"
            val accessToken = try {
                GoogleAuthUtil.getToken(context, androidAccount, oauthScope)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching OAuth token", e)
                val errorMsg = "OAuth অনুমোদন পাওয়া যায়নি: ${e.localizedMessage}"
                _setupState.value = DriveSetupState.Error(errorMsg)
                return@withContext Result.failure(e)
            }

            val sanitizedSchoolFolderName = if (schoolName.isBlank()) {
                "School_Data_Storage"
            } else {
                "${schoolName.trim()}_Data_Storage"
            }

            _setupState.value = DriveSetupState.Loading("Google Drive এ স্কুলের জন্য ডেটা ফোল্ডার তৈরি বা যাচাই করা হচ্ছে...")

            val folderResult = createOrFindSchoolFolder(accessToken, sanitizedSchoolFolderName)

            val info = ConnectedDriveAccountInfo(
                email = email,
                displayName = displayName,
                folderId = folderResult.id,
                folderName = folderResult.name,
                folderWebViewLink = folderResult.webViewLink,
                connectedAt = System.currentTimeMillis()
            )

            // Save in prefs
            saveAccountInfo(info)
            _connectedAccountInfo.value = info
            _setupState.value = DriveSetupState.Success(
                email = email,
                displayName = displayName,
                folderId = folderResult.id,
                folderName = folderResult.name,
                folderWebViewLink = folderResult.webViewLink,
                message = if (folderResult.isNewlyCreated) {
                    "Google Drive এ নতুন ফোল্ডার '${folderResult.name}' সফলভাবে তৈরি হয়েছে!"
                } else {
                    "Google Drive এ পূর্বের ফোল্ডার '${folderResult.name}' এর সাথে সফলভাবে সংযুক্ত হয়েছে!"
                }
            )

            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Drive folder", e)
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
        // Step 1: Search for existing folder with same name
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

        // Step 2: Create new folder on Google Drive
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

    private fun saveAccountInfo(info: ConnectedDriveAccountInfo) {
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

    fun disconnect(onComplete: () -> Unit = {}) {
        prefs.edit().clear().apply()
        _connectedAccountInfo.value = null
        _setupState.value = DriveSetupState.Idle
        try {
            getGoogleSignInClient().signOut().addOnCompleteListener {
                onComplete()
            }
        } catch (e: Exception) {
            onComplete()
        }
    }

    fun clearStatusState() {
        _setupState.value = DriveSetupState.Idle
    }
}
