package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Clean & concise helper for Google Sign-In and uploading Room SQLite database files
 * to Google Drive's hidden appDataFolder using official play-services-auth & google-api-services-drive:v3.
 */
object GoogleDriveHelper {

    private const val TAG = "GoogleDriveHelper"
    const val DEFAULT_DB_NAME = "school_db.db"

    /**
     * Build GoogleSignInClient configured with DRIVE_APPDATA scope and email.
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Get the sign-in intent. Signs out first to always prompt account picker.
     */
    fun getSignInIntent(context: Context): Intent {
        val client = getGoogleSignInClient(context)
        client.signOut()
        return client.signInIntent
    }

    /**
     * Finds the local database file (checks school_db.db, then active app room db name).
     */
    fun getLocalDbFile(context: Context, databaseFileName: String = DEFAULT_DB_NAME): java.io.File? {
        val primary = context.getDatabasePath(databaseFileName)
        if (primary.exists()) return primary

        val defaultRoomDb = context.getDatabasePath("anwesha_school_db")
        if (defaultRoomDb.exists()) return defaultRoomDb

        return if (primary.parentFile?.exists() == true) primary else null
    }

    /**
     * Background suspending function (Dispatchers.IO) to upload a local database file to appDataFolder.
     * Searches for existing file in appDataFolder to update, or creates a new one.
     */
    suspend fun uploadDatabaseToAppDataFolder(
        context: Context,
        account: GoogleSignInAccount,
        databaseFileName: String = DEFAULT_DB_NAME
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve local SQLite database file
            val localDbFile = getLocalDbFile(context, databaseFileName)
            if (localDbFile == null || !localDbFile.exists()) {
                return@withContext Result.failure(
                    java.io.FileNotFoundException("ডাটাবেস ফাইল পাওয়া যায়নি: $databaseFileName")
                )
            }

            // 2. Initialize Google Drive Service with official GoogleAccountCredential
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(DriveScopes.DRIVE_APPDATA)
            ).apply {
                selectedAccount = account.account
            }

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("School Management System").build()

            // 3. Search for existing database file in appDataFolder
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$databaseFileName' and trashed = false")
                .setFields("files(id, name, modifiedTime, size)")
                .execute()

            val mediaContent = FileContent("application/x-sqlite3", localDbFile)

            val existingFile = fileList.files?.firstOrNull()
            val uploadedFileId = if (existingFile != null) {
                // Update existing file content
                Log.d(TAG, "Updating existing file in appDataFolder: ${existingFile.id}")
                val updateMeta = File()
                val updated = driveService.files().update(existingFile.id, updateMeta, mediaContent).execute()
                updated.id
            } else {
                // Create new file inside appDataFolder
                Log.d(TAG, "Creating new file in appDataFolder: $databaseFileName")
                val fileMetadata = File().apply {
                    name = databaseFileName
                    parents = listOf("appDataFolder")
                }
                val created = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, size")
                    .execute()
                created.id
            }

            Log.i(TAG, "Database successfully uploaded to appDataFolder. File ID: $uploadedFileId")
            Result.success(uploadedFileId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload database file to appDataFolder", e)
            Result.failure(e)
        }
    }
}
