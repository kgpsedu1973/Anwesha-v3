package com.example.util

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Helper to upload image to Google Drive, execute Google Drive OCR via Document conversion,
 * retrieve extracted text, delete temporary drive files and local cache, and parse student attributes.
 */
object GoogleDriveOcrHelper {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads the bitmap to Google Drive as an application/vnd.google-apps.document to trigger Google Drive OCR,
     * exports the extracted plain text, and immediately deletes the temporary Google Doc and local cache.
     */
    suspend fun performGoogleDriveOcr(
        context: Context,
        accessToken: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        var uploadedFileId: String? = null
        var tempCacheFile: File? = null
        try {
            AppErrorLogger.logInfo("DriveOCR", "Google Drive OCR শুরু হচ্ছে...")

            // 1. Compress bitmap to JPEG byte array
            val byteStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, byteStream)
            val imageBytes = byteStream.toByteArray()

            val cacheDir = File(context.cacheDir, "drive_ocr_temp")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            tempCacheFile = File(cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempCacheFile).use { it.write(imageBytes) }

            // 2. Upload to Google Drive as converted Google Doc (mimeType: application/vnd.google-apps.document)
            // Use standard alphanumeric boundary without equal signs to avoid parameter parsing errors
            val boundary = "DriveOcrBoundary${System.currentTimeMillis()}"
            val metadataJson = JSONObject().apply {
                put("name", "OCR_Doc_${System.currentTimeMillis()}")
                put("mimeType", "application/vnd.google-apps.document")
            }.toString()

            val lineBreak = "\r\n"
            val baos = ByteArrayOutputStream()
            baos.write(("--$boundary$lineBreak").toByteArray(Charsets.UTF_8))
            baos.write(("Content-Type: application/json; charset=UTF-8$lineBreak$lineBreak").toByteArray(Charsets.UTF_8))
            baos.write(metadataJson.toByteArray(Charsets.UTF_8))
            baos.write(lineBreak.toByteArray(Charsets.UTF_8))

            baos.write(("--$boundary$lineBreak").toByteArray(Charsets.UTF_8))
            baos.write(("Content-Type: image/jpeg$lineBreak$lineBreak").toByteArray(Charsets.UTF_8))
            baos.write(imageBytes)
            baos.write(lineBreak.toByteArray(Charsets.UTF_8))

            baos.write(("--$boundary--$lineBreak").toByteArray(Charsets.UTF_8))

            val multipartRequestBody = baos.toByteArray().toRequestBody("multipart/related; boundary=$boundary".toMediaType())

            val uploadRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipartRequestBody)
                .build()

            val uploadResponse = okHttpClient.newCall(uploadRequest).execute()
            if (!uploadResponse.isSuccessful) {
                val errBody = uploadResponse.body?.string() ?: "Unknown error"
                AppErrorLogger.logError("DriveOCR", "আপলোড ব্যর্থ: $errBody (Code: ${uploadResponse.code})")
                return@withContext Result.failure(Exception("গুগল ড্রাইভে OCR আপলোড ব্যর্থ: $errBody"))
            }

            val uploadRespString = uploadResponse.body?.string() ?: ""
            val jsonObject = JSONObject(uploadRespString)
            uploadedFileId = jsonObject.optString("id")

            if (uploadedFileId.isNullOrBlank()) {
                return@withContext Result.failure(Exception("গুগল ড্রাইভ ফাইল আইডি পাওয়া যায়নি"))
            }

            AppErrorLogger.logInfo("DriveOCR", "Google Doc তৈরি সম্পন্ন (ID: $uploadedFileId), টেক্সট এক্সপোর্ট করা হচ্ছে...")

            // 3. Export the converted Google Doc as plain text (text/plain)
            val exportUrl = "https://www.googleapis.com/drive/v3/files/$uploadedFileId/export?mimeType=text/plain"
            val exportRequest = Request.Builder()
                .url(exportUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val exportResponse = okHttpClient.newCall(exportRequest).execute()
            if (!exportResponse.isSuccessful) {
                val errBody = exportResponse.body?.string() ?: "Export error"
                AppErrorLogger.logError("DriveOCR", "এক্সপোর্ট ব্যর্থ: $errBody")
                return@withContext Result.failure(Exception("OCR টেক্সট রিট্রিভ ব্যর্থ: $errBody"))
            }

            val extractedText = exportResponse.body?.string() ?: ""
            val cleanedText = extractedText.replace("\uFEFF", "").trim()

            AppErrorLogger.logInfo("DriveOCR", "OCR টেক্সট সফলভাবে নিষ্কাশন সম্পন্ন (${cleanedText.length} অক্ষর)")
            Result.success(cleanedText)
        } catch (e: Exception) {
            AppErrorLogger.logError("DriveOCR", "Google Drive OCR ব্যর্থ: ${e.localizedMessage}", e)
            Result.failure(e)
        } finally {
            // 4. Delete drive temp file and local cache
            if (!uploadedFileId.isNullOrBlank()) {
                try {
                    val deleteRequest = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$uploadedFileId")
                        .header("Authorization", "Bearer $accessToken")
                        .delete()
                        .build()
                    okHttpClient.newCall(deleteRequest).execute()
                    AppErrorLogger.logInfo("DriveOCR", "ড্রাইভ থেকে অস্থায়ী ফাইল ($uploadedFileId) সফলভাবে মুছে ফেলা হয়েছে")
                } catch (ignored: Exception) {
                }
            }
            try {
                tempCacheFile?.delete()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Parsed attributes structure for creating a student
     */
    data class ParsedStudentInfo(
        val name: String = "",
        val fatherName: String = "",
        val motherName: String = "",
        val birthDate: String = "",
        val birthRegNumber: String = "",
        val studentClass: String = "১ম শ্রেণি",
        val rollNumber: Int = 1,
        val mobile: String = "",
        val address: String = "",
        val village: String = "",
        val gender: String = "ছাত্র"
    )

    /**
     * Smart parser to extract standard student details from Bengali/English OCR text
     */
    fun parseStudentFromOcrText(text: String): ParsedStudentInfo {
        if (text.isBlank()) return ParsedStudentInfo()
        return try {
            val formattedDoc = DocumentOcrFormatter.formatOcrText(text)
            formattedDoc.studentInfo
        } catch (e: Exception) {
            ParsedStudentInfo()
        }
    }
}
