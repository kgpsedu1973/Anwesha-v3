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
            val boundary = "==DriveOCRBoundary_${System.currentTimeMillis()}=="
            val metadataJson = JSONObject().apply {
                put("name", "OCR_Temp_${System.currentTimeMillis()}")
                put("mimeType", "application/vnd.google-apps.document")
            }.toString()

            val multipartBody = MultipartBody.Builder(boundary)
                .setType("multipart/related".toMediaType())
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val uploadRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody)
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
        var name = ""
        var fatherName = ""
        var motherName = ""
        var birthDate = ""
        var birthRegNumber = ""
        var studentClass = "১ম শ্রেণি"
        var roll = 1
        var mobile = ""
        var address = ""
        var village = ""
        var gender = "ছাত্র"

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for (line in lines) {
            val lower = line.lowercase()

            // Name
            if ((line.contains("শিক্ষার্থীর নাম") || line.contains("নাম") || lower.contains("name")) &&
                !line.contains("পিতা") && !line.contains("মাতা") && !line.contains("অভিভাবক") &&
                !line.contains("বিদ্যালয়") && !line.contains("স্কুল") && !line.contains("প্রধান") && name.isBlank()
            ) {
                name = extractCleanValue(line)
            }

            // Father Name
            if ((line.contains("পিতা") || line.contains("পিতার নাম") || lower.contains("father")) && fatherName.isBlank()) {
                fatherName = extractCleanValue(line)
            }

            // Mother Name
            if ((line.contains("মাতা") || line.contains("মাতার নাম") || lower.contains("mother")) && motherName.isBlank()) {
                motherName = extractCleanValue(line)
            }

            // Birth Date
            if ((line.contains("জন্ম তারিখ") || line.contains("জন্মতারিখ") || line.contains("জন্ম সন") || lower.contains("dob") || lower.contains("date of birth") || lower.contains("birth date")) && birthDate.isBlank()) {
                val dateRegex = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})|(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
                val match = dateRegex.find(line)
                birthDate = if (match != null) match.value else extractCleanValue(line)
            }

            // Birth Registration Number (17-digit standard or similar)
            if (line.contains("নিবন্ধন") || line.contains("জন্ম নিবন্ধন") || lower.contains("birth reg") || lower.contains("brn") || lower.contains("registration")) {
                val digitRegex = Regex("[0-9\u09E6-\u09EF]{10,20}")
                val match = digitRegex.find(line)
                if (match != null) {
                    birthRegNumber = BanglaUtils.toEnglishDigits(match.value)
                } else if (birthRegNumber.isBlank()) {
                    birthRegNumber = extractCleanValue(line)
                }
            }

            // Mobile number (BD 11 digit e.g. 01XXXXXXXXX)
            val mobileRegex = Regex("(?:01|০১)[0-9\u09E6-\u09EF]{9}")
            val mobileMatch = mobileRegex.find(line)
            if (mobileMatch != null && mobile.isBlank()) {
                mobile = BanglaUtils.toEnglishDigits(mobileMatch.value)
            }

            // Village
            if ((line.contains("গ্রাম") || lower.contains("village")) && village.isBlank()) {
                village = extractCleanValue(line)
            }

            // Address
            if ((line.contains("ঠিকানা") || lower.contains("address") || line.contains("ডাকঘর")) && address.isBlank()) {
                address = extractCleanValue(line)
            }

            // Class detection
            if (line.contains("১ম") || line.contains("প্রথম") || lower.contains("class 1")) studentClass = "১ম শ্রেণি"
            else if (line.contains("২য়") || line.contains("২য়") || line.contains("দ্বিতীয়") || lower.contains("class 2")) studentClass = "২য় শ্রেণি"
            else if (line.contains("৩য়") || line.contains("৩য়") || line.contains("তৃতীয়") || lower.contains("class 3")) studentClass = "৩য় শ্রেণি"
            else if (line.contains("৪র্থ") || line.contains("চতুর্থ") || lower.contains("class 4")) studentClass = "৪র্থ শ্রেণি"
            else if (line.contains("৫ম") || line.contains("পঞ্চম") || lower.contains("class 5")) studentClass = "৫ম শ্রেণি"
            else if (line.contains("প্রাক") || lower.contains("pre-primary") || lower.contains("nursery") || lower.contains("kg")) studentClass = "প্রাক-প্রাথমিক ৪+"

            // Roll detection
            if (line.contains("রোল") || line.contains("ক্রমিক") || lower.contains("roll")) {
                val rollMatch = Regex("[0-9\u09E6-\u09EF]{1,4}").find(line)
                if (rollMatch != null) {
                    val parsedRoll = BanglaUtils.toEnglishDigits(rollMatch.value).toIntOrNull()
                    if (parsedRoll != null && parsedRoll > 0) {
                        roll = parsedRoll
                    }
                }
            }

            // Gender
            if (line.contains("ছাত্রী") || lower.contains("female") || lower.contains("girl")) {
                gender = "ছাত্রী"
            } else if (line.contains("ছাত্র") || lower.contains("male") || lower.contains("boy")) {
                gender = "ছাত্র"
            }
        }

        return ParsedStudentInfo(
            name = name,
            fatherName = fatherName,
            motherName = motherName,
            birthDate = birthDate,
            birthRegNumber = birthRegNumber,
            studentClass = studentClass,
            rollNumber = roll,
            mobile = mobile,
            address = address,
            village = village,
            gender = gender
        )
    }

    private fun extractCleanValue(line: String): String {
        val parts = line.split(":", "ঃ", "-", "–", "=")
        val raw = if (parts.size > 1) {
            parts.drop(1).joinToString(":").trim()
        } else {
            line.trim()
        }
        return raw.replace(Regex("^[.,:;\\-_/| ]+"), "").trim()
    }
}
