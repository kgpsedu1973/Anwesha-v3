package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.SchoolDatabaseModel
import com.example.repository.SchoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EasyBackupManager provides foolproof, zero-error, 1-tap backup and restore solutions:
 * 1. SAF Direct Google Drive / Device Storage Saver (ACTION_CREATE_DOCUMENT)
 * 2. SAF Direct Google Drive / Device Storage Restore (ACTION_OPEN_DOCUMENT)
 * 3. 1-Tap Share to Google Drive, WhatsApp, Email, or Files (ACTION_SEND via FileProvider)
 * 4. Local Quick Cache / Internal Storage Backup
 */
class EasyBackupManager(private val context: Context) {

    private val TAG = "EASY_BACKUP"

    companion object {
        fun generateDefaultBackupFileName(): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            return "school_database_backup_$dateStr.json"
        }
    }

    /**
     * Saves JSON backup directly to a user-chosen Storage Access Framework URI (e.g. Google Drive, Downloads, SD Card).
     */
    suspend fun saveBackupToUri(uri: Uri, jsonContent: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val bytes = jsonContent.toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            } ?: return@withContext Result.failure(Exception("Could not open output stream for destination"))

            Log.d(TAG, "BACKUP_SAVED_SAF: ${bytes.size} bytes written to $uri")
            Result.success(bytes.size.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "BACKUP_SAVE_ERROR: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Reads JSON backup directly from a user-chosen Storage Access Framework URI (e.g. Google Drive, Downloads, SD Card),
     * validates the model and imports it directly into the local Room database.
     */
    suspend fun restoreBackupFromUri(
        uri: Uri,
        repository: SchoolRepository
    ): Result<SchoolDatabaseModel> = withContext(Dispatchers.IO) {
        try {
            val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("ফাইল পড়া যায়নি"))

            if (jsonContent.isBlank()) {
                return@withContext Result.failure(Exception("নির্বাচিত ফাইলটি সম্পূর্ণ খালি"))
            }

            val masterModel = SchoolDatabaseModel.fromJson(jsonContent)
                ?: return@withContext Result.failure(Exception("ফাইলের ফরম্যাট সঠিক নয় (Invalid JSON structure)"))

            repository.importMasterModel(masterModel)
            val totalRecords = masterModel.studentsList.size + masterModel.usersList.size + masterModel.attendanceList.size + masterModel.examResultsList.size
            Log.d(TAG, "BACKUP_RESTORED_SAF: Imported $totalRecords records successfully from $uri")
            Result.success(masterModel)
        } catch (e: Exception) {
            Log.e(TAG, "BACKUP_RESTORE_ERROR: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Creates an Intent to share or save the backup directly to Google Drive, WhatsApp, Email, etc.
     */
    suspend fun createShareIntent(jsonContent: String): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.cacheDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val fileName = generateDefaultBackupFileName()
            val backupFile = File(backupDir, fileName)

            FileOutputStream(backupFile).use { fos ->
                fos.write(jsonContent.toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "অন্বেষা বিদ্যালয় ডেটাবেস ব্যাকআপ - $fileName")
                putExtra(Intent.EXTRA_TEXT, "অন্বেষা বিদ্যালয় ডেটাবেস ব্যাকআপ ফাইল ($fileName)। এটি Google Drive এ সেভ করুন বা সংরক্ষণ করুন।")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Google Drive এ সেভ করুন বা ব্যাকআপ পাঠান")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Result.success(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "CREATE_SHARE_INTENT_ERROR: ${e.localizedMessage}")
            Result.failure(e)
        }
    }
}
