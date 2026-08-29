package com.example.sync.drive

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RemoteFileInfo(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: Long = 0L,
    val etag: String = "",
    val size: Long = 0L
)

/**
 * Lightweight, direct REST API client for Google Drive API v3.
 * Uses OkHttpClient and OAuth access token obtained via GoogleAuthUtil.
 * Guarantees zero third-party backend servers and strictly communicates directly with Google Drive.
 */
class DriveDirectApiHelper(private val context: Context) {

    private val TAG = "DriveDirectApiHelper"
    private val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Obtains a fresh OAuth2 Bearer Access Token for the signed-in Google Account.
     */
    suspend fun getAccessToken(accountEmail: String): String? = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank()) return@withContext null
        try {
            val account = Account(accountEmail, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
            Log.d(TAG, "Successfully acquired OAuth token for $accountEmail")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get OAuth token: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Searches for a folder with the given name inside parentFolderId (or root).
     */
    suspend fun findFolder(token: String, folderName: String, parentFolderId: String? = null): RemoteFileInfo? = withContext(Dispatchers.IO) {
        try {
            val parentQuery = if (parentFolderId != null) "'$parentFolderId' in parents" else "'root' in parents"
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and $parentQuery and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&spaces=drive&fields=files(id,name,mimeType,modifiedTime,version)"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    return@withContext RemoteFileInfo(
                        id = fileObj.getString("id"),
                        name = fileObj.getString("name"),
                        mimeType = fileObj.optString("mimeType", "application/vnd.google-apps.folder")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findFolder error: ${e.localizedMessage}")
        }
        null
    }

    /**
     * Creates a new folder inside parentFolderId (or root).
     */
    suspend fun createFolder(token: String, folderName: String, parentFolderId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files"
            val metadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                if (parentFolderId != null) {
                    put("parents", JSONArray().put(parentFolderId))
                }
            }

            val requestBody = metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                val id = json.getString("id")
                Log.d(TAG, "Created folder $folderName with id: $id")
                return@withContext id
            }
        } catch (e: Exception) {
            Log.e(TAG, "createFolder error: ${e.localizedMessage}")
        }
        null
    }

    /**
     * Searches for a file by name inside parentFolderId.
     */
    suspend fun findFile(token: String, fileName: String, parentFolderId: String): RemoteFileInfo? = withContext(Dispatchers.IO) {
        try {
            val query = "name = '$fileName' and '$parentFolderId' in parents and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&spaces=drive&fields=files(id,name,mimeType,modifiedTime,version,size)"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val f = files.getJSONObject(0)
                    return@withContext RemoteFileInfo(
                        id = f.getString("id"),
                        name = f.getString("name"),
                        mimeType = f.optString("mimeType", "application/json"),
                        etag = f.optString("version", ""),
                        size = f.optLong("size", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findFile error: ${e.localizedMessage}")
        }
        null
    }

    /**
     * Downloads content of a file and retrieves its ETag/Revision for Optimistic Concurrency.
     */
    suspend fun downloadFileContentAndEtag(token: String, fileId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val content = response.body?.string() ?: ""
                val etag = response.header("ETag") ?: response.header("x-goog-generation") ?: System.currentTimeMillis().toString()
                return@withContext Pair(content, etag)
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadFileContent error: ${e.localizedMessage}")
        }
        null
    }

    /**
     * Uploads or updates a file on Google Drive and returns Pair(fileId, newETag).
     */
    suspend fun uploadOrUpdateFile(
        token: String,
        fileId: String?,
        fileName: String,
        parentFolderId: String?,
        content: String,
        mimeType: String = "application/json"
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            if (fileId.isNullOrBlank()) {
                // Create New File with Multipart Upload
                val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("mimeType", mimeType)
                    if (parentFolderId != null) {
                        put("parents", JSONArray().put(parentFolderId))
                    }
                }

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata",
                        null,
                        metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                    .addFormDataPart(
                        "file",
                        fileName,
                        content.toRequestBody(mimeType.toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val newId = json.getString("id")
                    val etag = response.header("ETag") ?: json.optString("version", System.currentTimeMillis().toString())
                    Log.d(TAG, "Uploaded new file $fileName (id=$newId)")
                    return@withContext Pair(newId, etag)
                }
            } else {
                // Update Existing File
                val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .patch(content.toRequestBody(mimeType.toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val etag = response.header("ETag") ?: System.currentTimeMillis().toString()
                    Log.d(TAG, "Updated existing file $fileName (id=$fileId)")
                    return@withContext Pair(fileId, etag)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadOrUpdateFile error for $fileName: ${e.localizedMessage}")
        }
        null
    }

    /**
     * Shares a Drive file or folder with another user by email (Drive Permissions API).
     * Role: "writer" (Editor) or "reader" (Viewer)
     */
    suspend fun shareWithUser(
        token: String,
        fileId: String,
        userEmail: String,
        role: String = "writer"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions"
            val permissionJson = JSONObject().apply {
                put("role", if (role.equals("Viewer", true) || role.equals("ViewOnly", true)) "reader" else "writer")
                put("type", "user")
                put("emailAddress", userEmail.trim().lowercase())
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(permissionJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful
            Log.d(TAG, "Share permission for $userEmail on $fileId: $success (code=${response.code})")
            success
        } catch (e: Exception) {
            Log.e(TAG, "shareWithUser error: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Lists all files inside a folder.
     */
    suspend fun listFilesInFolder(token: String, parentFolderId: String): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RemoteFileInfo>()
        try {
            val query = "'$parentFolderId' in parents and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&spaces=drive&fields=files(id,name,mimeType,modifiedTime,version,size)&pageSize=100"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null) {
                    for (i in 0 until files.length()) {
                        val f = files.getJSONObject(i)
                        result.add(
                            RemoteFileInfo(
                                id = f.getString("id"),
                                name = f.getString("name"),
                                mimeType = f.optString("mimeType", ""),
                                etag = f.optString("version", ""),
                                size = f.optLong("size", 0L)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listFilesInFolder error: ${e.localizedMessage}")
        }
        result
    }

    /**
     * Deletes a file on Google Drive.
     */
    suspend fun deleteFile(token: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile error: ${e.localizedMessage}")
            false
        }
    }
}
