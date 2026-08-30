package com.example.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import com.example.data.local.entity.StudentEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Filter mode for scanned documents resembling CamScanner filters
 */
enum class DocScanFilterMode(val titleBn: String, val titleEn: String) {
    ORIGINAL("আসল (Original)", "Original"),
    MAGIC_COLOR("ম্যাজিক কালার (Magic Color)", "Magic Color"),
    ENHANCED_COLOR("এনহ্যান্সড কালার (Enhanced Color)", "Enhanced Color"),
    BW_HIGH_CONTRAST("ডকুমেন্ট সাদা-কালো (B&W)", "Black & White"),
    GRAYSCALE("গ্রেস্কেল (Grayscale)", "Grayscale"),
    LIGHTEN("উজ্জ্বল ও ছায়া মুক্ত (Lighten)", "Lighten")
}

/**
 * Extracted structured student & school data from document OCR
 */
data class ExtractedStudentData(
    val rawText: String = "",
    val nameBn: String = "",
    val nameEn: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val birthRegNumber: String = "",
    val birthDate: String = "",
    val studentClass: String = "",
    val rollNumber: Int? = null,
    val mobileNumber: String = "",
    val gender: String = "ছাত্র",
    val village: String = "",
    val address: String = "",
    val documentTypeDetected: String = "সাধারণ নথি (Document)"
)

object DocScannerOcrHelper {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Run ML Kit Text Recognition on an image URI or Bitmap
     */
    suspend fun recognizeTextFromUri(context: Context, imageUri: Uri): Text = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText)
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Text = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText)
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private val imageEnhancer by lazy { com.example.domain.usecase.ImageEnhancementUseCase() }

    /**
     * Apply CamScanner-like image enhancement filters (Contrast, Binarization, Grayscale, Magic Color)
     * using OpenCV post-processing pipeline via ImageEnhancementUseCase.
     */
    suspend fun applyFilter(
        sourceBitmap: Bitmap,
        filterMode: DocScanFilterMode,
        rotationDegrees: Float = 0f
    ): Bitmap = withContext(Dispatchers.Default) {
        val enhancementMode = when (filterMode) {
            DocScanFilterMode.ORIGINAL -> com.example.domain.usecase.EnhancementMode.ORIGINAL
            DocScanFilterMode.MAGIC_COLOR -> com.example.domain.usecase.EnhancementMode.MAGIC_COLOR
            DocScanFilterMode.ENHANCED_COLOR -> com.example.domain.usecase.EnhancementMode.ENHANCED_COLOR
            DocScanFilterMode.BW_HIGH_CONTRAST -> com.example.domain.usecase.EnhancementMode.BW_TEXT
            DocScanFilterMode.GRAYSCALE -> com.example.domain.usecase.EnhancementMode.GRAYSCALE
            DocScanFilterMode.LIGHTEN -> com.example.domain.usecase.EnhancementMode.LIGHTEN
        }
        imageEnhancer.execute(sourceBitmap, enhancementMode, rotationDegrees)
    }

    /**
     * Decode bitmap safely with dimension downscaling if too large (prevents OutOfMemoryError and crashes)
     */
    suspend fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap = withContext(Dispatchers.IO) {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        var sampleSize = 1
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight

        if (rawWidth > maxDimension || rawHeight > maxDimension) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / sampleSize) >= maxDimension || (halfWidth / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("ইমেজ লোড করা যায়নি")

        decoded
    }

    /**
     * Save processed bitmap to a temporary or permanent app cache file
     */
    suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String = "doc_scan"): Uri = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "scanned_docs")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Smart Extraction Parser for School & Student Records
     * Detects Birth Certificates, Admission Forms, NID, and Student Cards
     */
    fun extractStudentInformation(rawOcrText: String): ExtractedStudentData {
        if (rawOcrText.isBlank()) return ExtractedStudentData()

        val lines = rawOcrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fullText = rawOcrText

        var nameBn = ""
        var nameEn = ""
        var fatherName = ""
        var motherName = ""
        var birthRegNumber = ""
        var birthDate = ""
        var studentClass = ""
        var rollNumber: Int? = null
        var mobileNumber = ""
        var gender = "ছাত্র"
        var village = ""
        var address = ""
        var docType = "সাধারণ নথি (Document)"

        // Check Document Type
        val lowerText = fullText.lowercase()
        if (fullText.contains("জন্ম ও মৃত্যু নিবন্ধন") || fullText.contains("Birth Registration") || fullText.contains("জন্ম নিবন্ধন") || fullText.contains("BRIS") || fullText.contains("Register General")) {
            docType = "জন্ম নিবন্ধন সনদ (Birth Certificate)"
        } else if (fullText.contains("ভর্তি ফরম") || fullText.contains("Admission Form") || fullText.contains("ভর্তি আবেদন")) {
            docType = "ভর্তি ফরম (Admission Form)"
        } else if (fullText.contains("জাতীয় পরিচয়পত্র") || fullText.contains("National ID") || fullText.contains("NID")) {
            docType = "জাতীয় পরিচয়পত্র (NID)"
        } else if (fullText.contains("প্রত্যয়ন") || fullText.contains("প্রশংসাপত্র") || fullText.contains("Certificate")) {
            docType = "প্রত্যয়ন / প্রশংসাপত্র (Certificate)"
        }

        // 1. Extract 17-digit Birth Registration Number (English or Bengali digits)
        val normalizedDigitsText = BanglaUtils.toEnglishDigits(fullText)
        val birthRegRegex = Regex("\\b(19\\d{15}|20\\d{15}|\\d{17})\\b")
        val birthRegMatch = birthRegRegex.find(normalizedDigitsText)
        if (birthRegMatch != null) {
            birthRegNumber = birthRegMatch.value
        } else {
            // Check 16 or 17 digit fallback
            val fallbackRegex = Regex("\\b\\d{16,17}\\b")
            val fallbackMatch = fallbackRegex.find(normalizedDigitsText)
            if (fallbackMatch != null) {
                birthRegNumber = fallbackMatch.value
            }
        }

        // 2. Extract Mobile Number (01XXXXXXXXX)
        val mobileRegex = Regex("\\b(01[3-9]\\d{8})\\b")
        val mobileMatch = mobileRegex.find(normalizedDigitsText)
        if (mobileMatch != null) {
            mobileNumber = mobileMatch.value
        }

        // 3. Extract Date of Birth (DD/MM/YYYY or YYYY-MM-DD or DD-MM-YYYY)
        val dateRegex1 = Regex("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})\\b")
        val dateRegex2 = Regex("\\b(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\b")
        val dateMatch1 = dateRegex1.find(normalizedDigitsText)
        val dateMatch2 = dateRegex2.find(normalizedDigitsText)

        if (dateMatch1 != null) {
            birthDate = normalizeDateStr(dateMatch1.value)
        } else if (dateMatch2 != null) {
            birthDate = dateMatch2.value.replace('/', '-')
        }

        // 4. Extract Gender
        if (fullText.contains("নারী") || fullText.contains("Female") || fullText.contains("ছাত্রী") || fullText.contains("মেয়ে") || fullText.contains("মহিলা")) {
            gender = "ছাত্রী"
        } else if (fullText.contains("পুরুষ") || fullText.contains("Male") || fullText.contains("ছাত্র") || fullText.contains("ছেলে")) {
            gender = "ছাত্র"
        }

        // 5. Line by line heuristics for Names, Parents, Class, Roll, Village
        for (i in lines.indices) {
            val line = lines[i]
            val normLine = BanglaUtils.toEnglishDigits(line)

            // Class detection
            if (studentClass.isBlank()) {
                when {
                    line.contains("প্রাক-প্রাথমিক") || line.contains("শিশু") || line.contains("Play") || line.contains("Nursery") -> studentClass = "প্রাক-প্রাথমিক ৪+"
                    line.contains("১ম") || line.contains("প্রথম") || line.contains("Class 1") || line.contains("Class 01") || line.contains("One") -> studentClass = "১ম শ্রেণি"
                    line.contains("২য়") || line.contains("দ্বিতীয়") || line.contains("Class 2") || line.contains("Class 02") || line.contains("Two") -> studentClass = "২য় শ্রেণি"
                    line.contains("৩য়") || line.contains("তৃতীয়") || line.contains("Class 3") || line.contains("Class 03") || line.contains("Three") -> studentClass = "৩য় শ্রেণি"
                    line.contains("৪র্থ") || line.contains("চতুর্থ") || line.contains("Class 4") || line.contains("Class 04") || line.contains("Four") -> studentClass = "৪র্থ শ্রেণি"
                    line.contains("৫ম") || line.contains("পঞ্চম") || line.contains("Class 5") || line.contains("Class 05") || line.contains("Five") -> studentClass = "৫ম শ্রেণি"
                }
            }

            // Roll number
            if (rollNumber == null && (line.contains("রোল") || line.contains("Roll") || line.contains("ক্রমিক") || line.contains("Sl"))) {
                val digitsInLine = Regex("\\d+").findAll(normLine).map { it.value.toIntOrNull() }.filterNotNull().toList()
                if (digitsInLine.isNotEmpty()) {
                    val candidate = digitsInLine.firstOrNull { it in 1..200 }
                    if (candidate != null) rollNumber = candidate
                }
            }

            // Father's name
            if (fatherName.isBlank() && (line.contains("পিতা") || line.contains("পিতার নাম") || line.contains("Father"))) {
                val extracted = cleanExtractedValue(line, listOf("পিতা", "পিতার নাম", "Father's Name", "Father", "পিতার নাম:", "Father Name:"))
                if (extracted.isNotBlank()) {
                    fatherName = extracted
                } else if (i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (!next.contains("মাতা") && !next.contains("গ্রাম") && !next.contains("ঠিকানা")) {
                        fatherName = next
                    }
                }
            }

            // Mother's name
            if (motherName.isBlank() && (line.contains("মাতা") || line.contains("মাতার নাম") || line.contains("Mother"))) {
                val extracted = cleanExtractedValue(line, listOf("মাতা", "মাতার নাম", "Mother's Name", "Mother", "মাতার নাম:", "Mother Name:"))
                if (extracted.isNotBlank()) {
                    motherName = extracted
                } else if (i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (!next.contains("পিতা") && !next.contains("গ্রাম") && !next.contains("ঠিকানা")) {
                        motherName = next
                    }
                }
            }

            // Student Name (Bangla or English)
            if (nameBn.isBlank() && (line.contains("নাম") || line.contains("শিক্ষার্থীর নাম") || line.contains("Name")) && !line.contains("পিতা") && !line.contains("মাতা") && !line.contains("বিদ্যালয়") && !line.contains("স্কুল")) {
                val extracted = cleanExtractedValue(line, listOf("শিক্ষার্থীর নাম", "নাম", "Name", "Student's Name", "Student Name", "নাম:"))
                if (extracted.isNotBlank()) {
                    if (isBengaliText(extracted)) {
                        nameBn = extracted
                    } else {
                        nameEn = extracted
                    }
                } else if (i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (!next.contains("পিতা") && !next.contains("মাতা") && !next.contains("জন্ম") && !next.contains("রোল")) {
                        if (isBengaliText(next)) nameBn = next else nameEn = next
                    }
                }
            }

            // Village / Address
            if (village.isBlank() && (line.contains("গ্রাম") || line.contains("Village") || line.contains("গ্রাম:"))) {
                val extracted = cleanExtractedValue(line, listOf("গ্রাম", "Village", "গ্রাম:", "Village:"))
                if (extracted.isNotBlank()) village = extracted
            }

            if (address.isBlank() && (line.contains("ঠিকানা") || line.contains("Address") || line.contains("স্থায়ী ঠিকানা") || line.contains("বর্তমান ঠিকানা"))) {
                val extracted = cleanExtractedValue(line, listOf("ঠিকানা", "Address", "ঠিকানা:", "Address:", "স্থায়ী ঠিকানা", "বর্তমান ঠিকানা"))
                if (extracted.isNotBlank()) address = extracted
            }
        }

        // Fallback for Name if not extracted from explicit label
        if (nameBn.isBlank() && nameEn.isBlank()) {
            val candidate = lines.firstOrNull { l ->
                l.length in 4..35 &&
                        !l.contains("সনদ") &&
                        !l.contains("গণপ্রজাতন্ত্রী") &&
                        !l.contains("বাংলাদেশ") &&
                        !l.contains("নিবন্ধন") &&
                        !l.contains("ফরম") &&
                        !l.contains("Government") &&
                        !l.contains("People's Republic")
            }
            if (candidate != null) {
                if (isBengaliText(candidate)) nameBn = candidate else nameEn = candidate
            }
        }

        return ExtractedStudentData(
            rawText = rawOcrText,
            nameBn = nameBn,
            nameEn = nameEn,
            fatherName = fatherName,
            motherName = motherName,
            birthRegNumber = birthRegNumber,
            birthDate = birthDate,
            studentClass = studentClass.ifBlank { "১ম শ্রেণি" },
            rollNumber = rollNumber ?: 1,
            mobileNumber = mobileNumber,
            gender = gender,
            village = village,
            address = address.ifBlank { village },
            documentTypeDetected = docType
        )
    }

    private fun cleanExtractedValue(line: String, labelsToRemove: List<String>): String {
        var res = line
        for (label in labelsToRemove) {
            res = res.replace(label, "", ignoreCase = true)
        }
        res = res.replace(":", "").replace(";", "").replace("=", "").replace("-", " ").replace("|", "").trim()
        return res
    }

    private fun isBengaliText(str: String): Boolean {
        return str.any { it in '\u0980'..'\u09FF' }
    }

    private fun normalizeDateStr(dateStr: String): String {
        val parts = dateStr.split('/', '-')
        if (parts.size == 3) {
            val p1 = parts[0].padStart(2, '0')
            val p2 = parts[1].padStart(2, '0')
            val p3 = parts[2]
            return if (p3.length == 4) {
                "$p3-$p2-$p1" // YYYY-MM-DD
            } else {
                "$p1-$p2-$p3"
            }
        }
        return dateStr
    }
}
