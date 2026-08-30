package com.example.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.entity.StudentDocumentEntity
import com.example.repository.SchoolRepository
import com.example.repository.StudentDocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class StudentDocumentUseCase(
    private val studentDocumentRepository: StudentDocumentRepository,
    private val schoolRepository: SchoolRepository
) {

    companion object {
        private const val TAG = "StudentDocumentUseCase"
        private const val DOC_FOLDER = "student_documents"
    }

    fun getDocumentsForStudent(studentId: String): Flow<List<StudentDocumentEntity>> {
        return studentDocumentRepository.getDocumentsForStudent(studentId)
    }

    fun getAllDocuments(): Flow<List<StudentDocumentEntity>> {
        return studentDocumentRepository.getAllDocuments()
    }

    suspend fun getDocumentById(id: String): StudentDocumentEntity? {
        return studentDocumentRepository.getDocumentById(id)
    }

    /**
     * Save bitmap permanently to Internal App Sandbox storage
     */
    suspend fun saveBitmapToAppSandbox(
        context: Context,
        bitmap: Bitmap,
        studentId: String,
        prefix: String = "doc"
    ): Uri = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, DOC_FOLDER)
        if (!folder.exists()) folder.mkdirs()

        val safeStudentId = studentId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val timestamp = System.currentTimeMillis()
        val file = File(folder, "${prefix}_${safeStudentId}_$timestamp.jpg")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Create and save PDF from one or more bitmaps to internal app sandbox
     */
    suspend fun saveBitmapsAsPdfToAppSandbox(
        context: Context,
        bitmaps: List<Bitmap>,
        studentId: String,
        prefix: String = "doc"
    ): Uri = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, DOC_FOLDER)
        if (!folder.exists()) folder.mkdirs()

        val safeStudentId = studentId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val timestamp = System.currentTimeMillis()
        val file = File(folder, "${prefix}_${safeStudentId}_$timestamp.pdf")

        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                pdfDocument.finishPage(page)
            }

            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Create and attach document directly from bitmap
     */
    suspend fun createAndAttachDocument(
        context: Context,
        studentId: String,
        title: String,
        documentType: String,
        bitmap: Bitmap,
        extractedText: String = "",
        notes: String = ""
    ): StudentDocumentEntity = withContext(Dispatchers.IO) {
        val uri = saveBitmapToAppSandbox(context, bitmap, studentId, prefix = "scan")
        linkDocumentToStudent(
            studentId = studentId,
            title = title,
            documentType = documentType,
            fileUri = uri.toString(),
            fileType = "image/jpeg",
            extractedText = extractedText,
            pageCount = 1,
            notes = notes
        )
    }

    /**
     * Export a local file (JPG or PDF) directly to public Downloads folder with MediaStore
     */
    suspend fun exportFileToDownloads(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        mimeType: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Anwesha_Documents")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val destUri = contentResolver.insert(collection, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("MediaStore insert failed"))

                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(destUri, contentValues, null, null)

                Log.i(TAG, "Exported successfully to Downloads: $destUri")
                Result.success(destUri)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "Anwesha_Documents")
                if (!appDir.exists()) appDir.mkdirs()

                val destFile = File(appDir, fileName)
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val destUri = Uri.fromFile(destFile)
                Result.success(destUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export to downloads error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun exportDocumentToDownloads(
        context: Context,
        docUri: Uri,
        fileName: String,
        mimeType: String = "image/jpeg",
        onResult: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val result = exportFileToDownloads(context, docUri, fileName, mimeType)
        if (result.isSuccess) {
            val uri = result.getOrNull()
            withContext(Dispatchers.Main) {
                onResult(true, uri?.toString() ?: "ডাউনলোডস ফোল্ডারে সংরক্ষিত হয়েছে")
            }
        } else {
            withContext(Dispatchers.Main) {
                onResult(false, result.exceptionOrNull()?.message ?: "এক্সপোর্ট ব্যর্থ হয়েছে")
            }
        }
    }

    suspend fun exportBitmapsToPdfInDownloads(
        context: Context,
        bitmaps: List<Bitmap>,
        fileName: String,
        onResult: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val result = exportBitmapsAsPdfToDownloads(context, bitmaps, "$fileName.pdf")
        if (result.isSuccess) {
            val uri = result.getOrNull()
            withContext(Dispatchers.Main) {
                onResult(true, uri?.toString() ?: "PDF সফলভাবে ডাউনলোডস ফোল্ডারে সংরক্ষিত হয়েছে")
            }
        } else {
            withContext(Dispatchers.Main) {
                onResult(false, result.exceptionOrNull()?.message ?: "PDF তৈরি ব্যর্থ হয়েছে")
            }
        }
    }

    /**
     * Export Bitmap(s) as PDF directly to Downloads folder
     */
    suspend fun exportBitmapsAsPdfToDownloads(
        context: Context,
        bitmaps: List<Bitmap>,
        fileName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempUri = saveBitmapsAsPdfToAppSandbox(context, bitmaps, "temp_export", "pdf")
            exportFileToDownloads(context, tempUri, fileName, "application/pdf")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create Native Android Share Intent for single document
     */
    fun createShareIntent(
        context: Context,
        fileUri: Uri,
        title: String,
        mimeType: String = "image/jpeg"
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title - অন্বেষা বিদ্যালয় ব্যবস্থাপনা")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareDocument(
        context: Context,
        docUri: Uri,
        title: String,
        mimeType: String = "image/jpeg"
    ) {
        val intent = createShareIntent(context, docUri, title, mimeType).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "নথি শেয়ার করুন: $title").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Create Native Android Share Intent for multiple documents
     */
    fun createMultiShareIntent(
        context: Context,
        fileUris: ArrayList<Uri>,
        title: String
    ): Intent {
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title - অন্বেষা বিদ্যালয় ব্যবস্থাপনা নথিপত্র")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Link document to student record in Room DB
     */
    suspend fun linkDocumentToStudent(
        studentId: String,
        title: String,
        documentType: String,
        fileUri: String,
        fileType: String = "image/jpeg",
        extractedText: String = "",
        pageCount: Int = 1,
        notes: String = ""
    ): StudentDocumentEntity {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val scanDate = sdf.format(Date())

        val doc = StudentDocumentEntity(
            id = "DOC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6).uppercase(Locale.ROOT)}",
            studentId = studentId,
            title = title.ifBlank { documentType },
            documentType = documentType,
            fileUri = fileUri,
            fileType = fileType,
            extractedText = extractedText,
            pageCount = pageCount,
            notes = notes,
            scanDate = scanDate,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            version = 1,
            syncStatus = "SYNCED"
        )
        studentDocumentRepository.insertDocument(doc)
        return doc
    }

    suspend fun deleteDocument(doc: StudentDocumentEntity) {
        studentDocumentRepository.deleteDocument(doc)
    }

    suspend fun deleteDocumentById(id: String) {
        studentDocumentRepository.deleteDocumentById(id)
    }

    suspend fun updateDocument(doc: StudentDocumentEntity) {
        studentDocumentRepository.updateDocument(doc)
    }
}
