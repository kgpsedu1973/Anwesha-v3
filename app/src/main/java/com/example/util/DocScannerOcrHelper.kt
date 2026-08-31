package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.domain.model.OcrLanguage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Filter modes for document enhancement
 */
enum class DocScanFilterMode(val titleBn: String, val titleEn: String) {
    ORIGINAL("আসল (Original)", "Original"),
    MAGIC_COLOR("ম্যাজিক কালার (Magic Color)", "Magic Color"),
    ENHANCED_COLOR("এনহ্যান্সড কালার (Enhanced Color)", "Enhanced Color"),
    BW_HIGH_CONTRAST("সাদা-কালো টেক্সট (B&W Text)", "B&W Text"),
    GRAYSCALE("গ্রেস্কেল (Grayscale)", "Grayscale"),
    LIGHTEN("উজ্জ্বল (Lighten)", "Lighten")
}

/**
 * Data class representing extracted student and citizen profile information
 */
data class ExtractedStudentData(
    val rawText: String = "",
    val nameBn: String = "",
    val nameEn: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val spouseName: String = "",
    val nidNumber: String = "",
    val birthRegNumber: String = "",
    val birthDate: String = "",
    val studentClass: String = "১ম শ্রেণি",
    val rollNumber: Int? = 1,
    val mobileNumber: String = "",
    val gender: String = "ছাত্র",
    val bloodGroup: String = "",
    val placeOfBirth: String = "",
    val village: String = "",
    val postOffice: String = "",
    val upazila: String = "",
    val district: String = "",
    val address: String = "",
    val documentTypeDetected: String = "সাধারণ নথি (Document)",
    val extractionSource: String = "Offline OCR"
)

/**
 * Utility helper for Document Scanning and OCR heuristics
 */
object DocScannerOcrHelper {

    private val mlKitLatinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        mlKitLatinRecognizer.process(inputImage)
            .addOnSuccessListener { text ->
                continuation.resume(text)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    suspend fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap = withContext(Dispatchers.IO) {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw IllegalStateException("ইমেজ লোড করা যায়নি")

        var sampleSize = 1
        val maxDecodedDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
        if (maxDecodedDim > maxDimension) {
            while ((maxDecodedDim / (sampleSize * 2)) >= maxDimension) {
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

    suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String = "doc_scan"): Uri = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "scanned_docs")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Advanced Multi-Pattern Extraction Parser for Bangladeshi School & Identity Records:
     * - Smart National ID Cards (NID Front + Back)
     * - Laminated National ID Cards
     * - Digital Online Birth Certificates (BRIS)
     * - School Admission/Registration Forms
     */
    fun extractStudentInformation(rawOcrText: String): ExtractedStudentData {
        if (rawOcrText.isBlank()) return ExtractedStudentData()

        val fullText = rawOcrText
        val normalizedFullText = BanglaUtils.toEnglishDigits(fullText)
        val lines = rawOcrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var nameBn = ""
        var nameEn = ""
        var fatherName = ""
        var motherName = ""
        var spouseName = ""
        var birthRegNumber = ""
        var nidNumber = ""
        var birthDate = ""
        var studentClass = ""
        var rollNumber: Int? = null
        var mobileNumber = ""
        var gender = "ছাত্র"
        var bloodGroup = ""
        var placeOfBirth = ""
        var village = ""
        var postOffice = ""
        var upazila = ""
        var district = ""
        var address = ""
        var docType = "সাধারণ নথি (Document)"

        // 1. Detect Document Type
        when {
            fullText.contains("জন্ম ও মৃত্যু নিবন্ধন") || fullText.contains("Birth Registration") ||
                    fullText.contains("জন্ম নিবন্ধন") || fullText.contains("BRIS") ||
                    fullText.contains("Register General") || fullText.contains("Birth Certificate") ||
                    fullText.contains("BR Number") -> {
                docType = "অনলাইন জন্ম নিবন্ধন সনদপত্র (Birth Certificate)"
            }
            fullText.contains("স্মার্ট") || fullText.contains("Smart Card") ||
                    fullText.contains("I<BGD") || (fullText.contains("NID") && fullText.contains("Blood Group")) -> {
                docType = "স্মার্ট জাতীয় পরিচয়পত্র (Smart NID)"
            }
            fullText.contains("জাতীয় পরিচয়পত্র") || fullText.contains("জাতীয় পরিচয়পত্র") ||
                    fullText.contains("National ID") || fullText.contains("NID") ||
                    fullText.contains("ID NO") || fullText.contains("নির্বাচন কমিশন") -> {
                docType = "জাতীয় পরিচয়পত্র (NID Card)"
            }
            fullText.contains("ভর্তি ফরম") || fullText.contains("Admission Form") || fullText.contains("ভর্তি আবেদন") -> {
                docType = "ভর্তি ফরম (Admission Form)"
            }
            fullText.contains("প্রত্যয়ন") || fullText.contains("প্রশংসাপত্র") || fullText.contains("সনদপত্র") -> {
                docType = "প্রত্যয়নপত্র (Certificate)"
            }
        }

        // 2. Extract 17-digit Birth Registration Number (BRN)
        val brn17Regex = Regex("\\b(19\\d{15}|20\\d{15}|\\d{17})\\b")
        val brnMatch = brn17Regex.find(normalizedFullText)
        if (brnMatch != null) {
            birthRegNumber = brnMatch.value
        } else {
            val spacedDigitsRegex = Regex("(?<=BR Number|BRN|নিবন্ধন নম্বর|Registration No|No)[^\\n\\r0-9]*(([0-9]\\s*){17})", RegexOption.IGNORE_CASE)
            val spacedMatch = spacedDigitsRegex.find(normalizedFullText)
            if (spacedMatch != null) {
                val cleaned = spacedMatch.groupValues[1].replace("\\s+".toRegex(), "")
                if (cleaned.length == 17) {
                    birthRegNumber = cleaned
                }
            } else {
                val anySpaced17 = Regex("\\b(?:[0-9]\\s+){16}[0-9]\\b").find(normalizedFullText)
                if (anySpaced17 != null) {
                    val digits = anySpaced17.value.replace("\\s+".toRegex(), "")
                    if (digits.length == 17 && (digits.startsWith("19") || digits.startsWith("20"))) {
                        birthRegNumber = digits
                    }
                }
            }
        }

        // 3. Extract National ID Number (NID: 10-digit Smart NID, 13-digit Old NID, 17-digit Old NID)
        val smartNidRegex = Regex("(?<=NID No|ID NO|ID NO:|NID|আইডি নম্বর|এনআইডি)[^\\n\\r0-9]*(\\d{3}\\s*\\d{3}\\s*\\d{4}|\\d{10}|\\d{13}|\\d{17})", RegexOption.IGNORE_CASE)
        val smartNidMatch = smartNidRegex.find(normalizedFullText)
        if (smartNidMatch != null) {
            nidNumber = smartNidMatch.groupValues[1].replace(" ", "").replace("-", "")
        } else {
            val spaced10Regex = Regex("\\b(\\d{3})\\s+(\\d{3})\\s+(\\d{4})\\b")
            val spaced10Match = spaced10Regex.find(normalizedFullText)
            if (spaced10Match != null) {
                nidNumber = "${spaced10Match.groupValues[1]}${spaced10Match.groupValues[2]}${spaced10Match.groupValues[3]}"
            } else {
                val standalone10 = Regex("\\b(\\d{10})\\b").find(normalizedFullText)
                if (standalone10 != null && !standalone10.value.startsWith("01")) {
                    nidNumber = standalone10.value
                } else {
                    val nid13Match = Regex("\\b(\\d{13})\\b").find(normalizedFullText)
                    if (nid13Match != null) {
                        nidNumber = nid13Match.value
                    }
                }
            }
        }

        // Check MRZ line on Smart NID back
        val mrzRegex = Regex("I<BGD(\\d{9,10})<(\\d+)<*")
        val mrzMatch = mrzRegex.find(fullText)
        if (mrzMatch != null) {
            if (nidNumber.isBlank()) {
                nidNumber = mrzMatch.groupValues[1]
            }
            if (docType.contains("সাধারণ")) {
                docType = "স্মার্ট জাতীয় পরিচয়পত্র (Smart NID)"
            }
        }

        // 4. Extract Mobile Number
        val mobileRegex = Regex("\\b(01[3-9]\\d{8})\\b")
        val mobileMatch = mobileRegex.find(normalizedFullText)
        if (mobileMatch != null) {
            mobileNumber = mobileMatch.value
        }

        // 5. Extract Date of Birth
        val textMonthRegex = Regex("\\b(\\d{1,2})[\\s/\\-]+([A-Za-z]{3,9})[\\s/\\-,]+(\\d{4})\\b")
        val textMonthMatch = textMonthRegex.find(normalizedFullText)
        if (textMonthMatch != null) {
            val day = textMonthMatch.groupValues[1].padStart(2, '0')
            val monthStr = textMonthMatch.groupValues[2].lowercase()
            val year = textMonthMatch.groupValues[3]
            val monthNum = parseMonthNameToNumber(monthStr)
            birthDate = "$year-$monthNum-$day"
        }

        if (birthDate.isBlank()) {
            val bnTextMonthRegex = Regex("(\\d{1,2}|[০-৯]{1,2})\\s*([\\u0980-\\u09FF]+)\\s*(\\d{4}|[০-৯]{4})")
            val bnMatch = bnTextMonthRegex.find(fullText)
            if (bnMatch != null) {
                val day = BanglaUtils.toEnglishDigits(bnMatch.groupValues[1]).padStart(2, '0')
                val monthBn = bnMatch.groupValues[2]
                val year = BanglaUtils.toEnglishDigits(bnMatch.groupValues[3])
                val monthNum = parseBnMonthToNumber(monthBn)
                if (monthNum.isNotBlank() && year.length == 4) {
                    birthDate = "$year-$monthNum-$day"
                }
            }
        }

        if (birthDate.isBlank()) {
            val dateRegex1 = Regex("(?<=Date of Birth|DOB|Date of birth|জন্ম তারিখ|তারিখ)[^\\n\\r0-9]*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", RegexOption.IGNORE_CASE)
            val dateMatch1 = dateRegex1.find(normalizedFullText)
            if (dateMatch1 != null) {
                birthDate = normalizeDateStr(dateMatch1.groupValues[1])
            } else {
                val genDate1 = Regex("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})\\b").find(normalizedFullText)
                if (genDate1 != null) {
                    birthDate = normalizeDateStr(genDate1.value)
                } else {
                    val genDate2 = Regex("\\b(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\b").find(normalizedFullText)
                    if (genDate2 != null) {
                        birthDate = genDate2.value.replace('/', '-')
                    }
                }
            }
        }

        // 6. Extract Gender
        if (fullText.contains("নারী") || fullText.contains("মহিলা") || fullText.contains("ছাত্রী") ||
            fullText.contains("Female", ignoreCase = true) || fullText.contains("মেয়ে") || fullText.contains("মেয়ে")) {
            gender = "ছাত্রী"
        } else if (fullText.contains("পুরুষ") || fullText.contains("ছাত্র") || fullText.contains("Male", ignoreCase = true) || fullText.contains("ছেলে")) {
            gender = "ছাত্র"
        }

        // 7. Extract Blood Group
        val bloodRegex = Regex("(?:Blood Group|রক্তের গ্রুপ)[^A-Za-z0-9]*(A\\+|B\\+|O\\+|AB\\+|A-|B-|O-|AB-)", RegexOption.IGNORE_CASE)
        val bloodMatch = bloodRegex.find(fullText)
        if (bloodMatch != null) {
            bloodGroup = bloodMatch.groupValues[1].uppercase()
        }

        // 8. Extract Place of Birth
        val pobRegex = Regex("(?:Place of Birth|জন্মস্থান)[^\\n\\r:]*[:\\-=]\\s*([^\\n\\r,;]+)", RegexOption.IGNORE_CASE)
        val pobMatch = pobRegex.find(fullText)
        if (pobMatch != null) {
            placeOfBirth = pobMatch.groupValues[1].trim()
        }

        // 9. Line-by-Line Extraction
        for (i in lines.indices) {
            val line = lines[i]
            val normLine = BanglaUtils.toEnglishDigits(line)

            // Class detection
            if (studentClass.isBlank()) {
                when {
                    line.contains("প্রাক-প্রাথমিক") || line.contains("শিশু") || line.contains("Play") || line.contains("Nursery") -> studentClass = "প্রাক-প্রাথমিক ৪+"
                    line.contains("১ম") || line.contains("প্রথম") || line.contains("Class 1") || line.contains("Class 01") || line.contains("One") -> studentClass = "১ম শ্রেণি"
                    line.contains("২য়") || line.contains("২য়") || line.contains("দ্বিতীয়") || line.contains("Class 2") || line.contains("Class 02") || line.contains("Two") -> studentClass = "২য় শ্রেণি"
                    line.contains("৩য়") || line.contains("৩য়") || line.contains("তৃতীয়") || line.contains("Class 3") || line.contains("Class 03") || line.contains("Three") -> studentClass = "৩য় শ্রেণি"
                    line.contains("৪র্থ") || line.contains("চতুর্থ") || line.contains("Class 4") || line.contains("Class 04") || line.contains("Four") -> studentClass = "৪র্থ শ্রেণি"
                    line.contains("৫ম") || line.contains("পঞ্চম") || line.contains("Class 5") || line.contains("Class 05") || line.contains("Five") -> studentClass = "৫ম শ্রেণি"
                }
            }

            // Roll number
            if (rollNumber == null && (line.contains("রোল") || line.contains("Roll") || line.contains("ক্রমিক") || line.contains("Sl"))) {
                val digitsInLine = Regex("\\d+").findAll(normLine).mapNotNull { it.value.toIntOrNull() }.toList()
                if (digitsInLine.isNotEmpty()) {
                    val candidate = digitsInLine.firstOrNull { it in 1..200 }
                    if (candidate != null) rollNumber = candidate
                }
            }

            // A) Bengali Name ("নাম: রাইয়ান আরা পুর্নি")
            if (line.contains("নাম:") || line.contains("নাম :") || (line.startsWith("নাম") && !line.contains("পিতা") && !line.contains("মাতা") && !line.contains("স্বামী"))) {
                val extracted = cleanExtractedValue(line, listOf("নাম:", "নাম :", "নাম", "শিক্ষার্থীর নাম:", "শিক্ষার্থীর নাম"))
                if (extracted.isNotBlank() && isBengaliText(extracted) && nameBn.isBlank()) {
                    nameBn = extracted
                }
            }

            // English Name ("Name: RAIYAN ARA PURNY")
            if (line.contains("Name:") || line.contains("Name :") || (line.startsWith("Name") && !line.contains("Father") && !line.contains("Mother") && !line.contains("Husband"))) {
                val extracted = cleanExtractedValue(line, listOf("Name:", "Name :", "Name", "Student's Name:", "Student Name:"))
                if (extracted.isNotBlank() && nameEn.isBlank()) {
                    nameEn = extracted
                }
            }

            // Single Bilingual Name line: "নাম: রাইয়ান আরা পুর্নি Name: RAIYAN ARA PURNY"
            if ((line.contains("নাম") || line.contains("Name")) &&
                !line.contains("পিতা") && !line.contains("মাতা") && !line.contains("স্বামী") &&
                !line.contains("Father") && !line.contains("Mother") && !line.contains("Husband") &&
                !line.contains("বিদ্যালয়") && !line.contains("স্কুল") && !line.contains("School")) {

                val (extractedBn, extractedEn) = parseBilingualLine(line, listOf("শিক্ষার্থীর নাম", "নাম", "Name", "Student's Name", "Student Name"))
                if (extractedBn.isNotBlank() && nameBn.isBlank()) nameBn = extractedBn
                if (extractedEn.isNotBlank() && nameEn.isBlank()) nameEn = extractedEn
            }

            // B) Father's Name ("পিতা: মোঃ আবুল বাশার")
            if (line.contains("পিতা") || line.contains("Father")) {
                val (bn, en) = parseBilingualLine(line, listOf("পিতার নাম", "পিতা", "Father's Name", "Father Name", "Father"))
                if (bn.isNotBlank() && fatherName.isBlank()) fatherName = bn
                if (fatherName.isBlank() && en.isNotBlank()) fatherName = en

                if (fatherName.isBlank() && i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (!isReservedKeyword(nextLine)) fatherName = cleanValue(nextLine)
                }
            }

            // C) Husband / Spouse in NID ("স্বামী: মোঃ আমির হোসেন")
            if (line.contains("স্বামী") || line.contains("Husband") || line.contains("স্ত্রী") || line.contains("Spouse")) {
                val (bn, en) = parseBilingualLine(line, listOf("স্বামীর নাম", "স্বামী", "Husband's Name", "Husband", "স্ত্রীর নাম", "স্ত্রী", "Spouse"))
                if (bn.isNotBlank()) spouseName = bn
                if (spouseName.isBlank() && en.isNotBlank()) spouseName = en

                if (spouseName.isBlank() && i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (!isReservedKeyword(nextLine)) spouseName = cleanValue(nextLine)
                }

                if (fatherName.isBlank() && spouseName.isNotBlank()) {
                    fatherName = spouseName
                }
            }

            // D) Mother's Name ("মাতা: সুফিয়া পারভীন")
            if (line.contains("মাতা") || line.contains("Mother")) {
                val (bn, en) = parseBilingualLine(line, listOf("মাতার নাম", "মাতা", "Mother's Name", "Mother Name", "Mother"))
                if (bn.isNotBlank() && motherName.isBlank()) motherName = bn
                if (motherName.isBlank() && en.isNotBlank()) motherName = en

                if (motherName.isBlank() && i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (!isReservedKeyword(nextLine)) motherName = cleanValue(nextLine)
                }
            }

            // E) Address parsing:
            // "ঠিকানা: গ্রাম/রাস্তা: কটূরাকান্দি, ডাকঘর: শিয়ালদী - ৭৮৬০, আলফাডাঙ্গা, ফরিদপুর"
            if (line.contains("ঠিকানা") || line.contains("Address") || line.contains("গ্রাম/রাস্তা") || line.contains("ডাকঘর")) {
                address = cleanExtractedValue(line, listOf("ঠিকানা:", "ঠিকানা", "Address:", "Address", "স্থায়ী ঠিকানা:", "স্থায়ী ঠিকানা"))

                // Parse components inside address line
                if (line.contains("গ্রাম/রাস্তা:") || line.contains("গ্রাম:")) {
                    val vMatch = Regex("(?:গ্রাম/রাস্তা|গ্রাম):\\s*([^,;\\n]+)").find(line)
                    if (vMatch != null) village = cleanValue(vMatch.groupValues[1])
                }
                if (line.contains("ডাকঘর:") || line.contains("ডাকঘর :")) {
                    val poMatch = Regex("ডাকঘর:\\s*([^,;\\n]+)").find(line)
                    if (poMatch != null) postOffice = cleanValue(poMatch.groupValues[1])
                }

                // If comma-separated tokens exist, extract Upazila and District
                val parts = line.split(',').map { it.trim() }
                if (parts.size >= 3) {
                    if (district.isBlank()) district = cleanValue(parts.last())
                    if (upazila.isBlank() && parts.size >= 4) upazila = cleanValue(parts[parts.size - 2])
                }
            }

            if (line.contains("গ্রাম/রাস্তা:") || line.contains("গ্রাম:") || line.contains("Village:")) {
                if (village.isBlank()) village = cleanExtractedValue(line, listOf("গ্রাম/রাস্তা:", "গ্রাম/রাস্তা", "গ্রাম:", "গ্রাম", "Village:", "Village"))
            }

            if (line.contains("ডাকঘর:") || line.contains("Post Office:") || line.contains("ডাকঘর")) {
                if (postOffice.isBlank()) postOffice = cleanExtractedValue(line, listOf("ডাকঘর:", "ডাকঘর", "Post Office:", "Post Office"))
            }

            if (line.contains("উপজেলা:") || line.contains("থানা:") || line.contains("Upazila:") || line.contains("Thana:")) {
                if (upazila.isBlank()) upazila = cleanExtractedValue(line, listOf("উপজেলা:", "উপজেলা", "থানা:", "থানা", "Upazila:", "Upazila", "Thana:", "Thana"))
            }

            if (line.contains("জেলা:") || line.contains("District:")) {
                if (district.isBlank()) district = cleanExtractedValue(line, listOf("জেলা:", "জেলা", "District:", "District"))
            }
        }

        // Build combined address if address field is empty
        val combinedAddress = when {
            address.isNotBlank() -> address
            village.isNotBlank() || postOffice.isNotBlank() || upazila.isNotBlank() || district.isNotBlank() -> {
                listOf(village, postOffice, upazila, district).filter { it.isNotBlank() }.joinToString(", ")
            }
            else -> village
        }

        return ExtractedStudentData(
            rawText = rawOcrText,
            nameBn = nameBn,
            nameEn = nameEn,
            fatherName = fatherName,
            motherName = motherName,
            spouseName = spouseName,
            nidNumber = nidNumber,
            birthRegNumber = birthRegNumber,
            birthDate = birthDate,
            studentClass = studentClass.ifBlank { "১ম শ্রেণি" },
            rollNumber = rollNumber ?: 1,
            mobileNumber = mobileNumber,
            gender = gender,
            bloodGroup = bloodGroup,
            placeOfBirth = placeOfBirth,
            village = village,
            postOffice = postOffice,
            upazila = upazila,
            district = district,
            address = combinedAddress,
            documentTypeDetected = docType,
            extractionSource = "অফলাইন OCR"
        )
    }

    private fun parseBilingualLine(line: String, labels: List<String>): Pair<String, String> {
        val cleanLine = line
        val englishKeywordRegex = Regex("(?<=[\\u0980-\\u09FF\\s])(Name|Father(?:'s)?\\s*Name|Mother(?:'s)?\\s*Name|Husband(?:'s)?\\s*Name|Father|Mother|Husband)\\s*[:\\-=]\\s*", RegexOption.IGNORE_CASE)
        val splitMatch = englishKeywordRegex.find(cleanLine)

        if (splitMatch != null) {
            val banglaPart = cleanLine.substring(0, splitMatch.range.first)
            val englishPart = cleanLine.substring(splitMatch.range.last + 1)
            return Pair(cleanValue(cleanExtractedValue(banglaPart, labels)), cleanValue(englishPart))
        }

        val hasBangla = isBengaliText(cleanLine)
        val hasEnglish = cleanLine.any { it in 'A'..'Z' || it in 'a'..'z' }

        if (hasBangla && hasEnglish) {
            val match = Regex("([\\u0980-\\u09FF\\s\\.:\\-=]+)\\s+([A-Za-z][A-Za-z\\s\\.]+)").find(cleanLine)
            if (match != null) {
                val bn = cleanValue(cleanExtractedValue(match.groupValues[1], labels))
                val en = cleanValue(match.groupValues[2])
                return Pair(bn, en)
            }
        }

        val cleaned = cleanValue(cleanExtractedValue(cleanLine, labels))
        return if (isBengaliText(cleaned)) {
            Pair(cleaned, "")
        } else {
            Pair("", cleaned)
        }
    }

    private fun isReservedKeyword(str: String): Boolean {
        val s = str.lowercase()
        return s.contains("নাম") || s.contains("পিতা") || s.contains("মাতা") || s.contains("স্বামী") ||
                s.contains("গ্রাম") || s.contains("ঠিকানা") || s.contains("জন্ম") || s.contains("রোল") ||
                s.contains("name") || s.contains("father") || s.contains("mother") || s.contains("husband") ||
                s.contains("date") || s.contains("address") || s.contains("nid") || s.contains("brn")
    }

    private fun cleanValue(str: String): String {
        return str.replace(":", "")
            .replace(";", "")
            .replace("=", "")
            .replace("|", "")
            .replace("~", "")
            .replace("`", "")
            .trim()
    }

    private fun cleanExtractedValue(line: String, labelsToRemove: List<String>): String {
        var res = line
        for (label in labelsToRemove) {
            res = res.replace(label, "", ignoreCase = true)
        }
        return cleanValue(res)
    }

    private fun isBengaliText(str: String): Boolean {
        return str.any { it in '\u0980'..'\u09FF' }
    }

    private fun parseMonthNameToNumber(monthStr: String): String {
        val m = monthStr.lowercase()
        return when {
            m.startsWith("jan") -> "01"
            m.startsWith("feb") -> "02"
            m.startsWith("mar") -> "03"
            m.startsWith("apr") -> "04"
            m.startsWith("may") -> "05"
            m.startsWith("jun") -> "06"
            m.startsWith("jul") -> "07"
            m.startsWith("aug") -> "08"
            m.startsWith("sep") -> "09"
            m.startsWith("oct") -> "10"
            m.startsWith("nov") -> "11"
            m.startsWith("dec") -> "12"
            else -> "01"
        }
    }

    private fun parseBnMonthToNumber(bnMonth: String): String {
        val m = bnMonth.trim()
        return when {
            m.contains("জানু") -> "01"
            m.contains("ফেব্রু") -> "02"
            m.contains("মার্চ") -> "03"
            m.contains("এপ্রিল") -> "04"
            m.contains("মে") -> "05"
            m.contains("জুন") -> "06"
            m.contains("জুলাই") -> "07"
            m.contains("আগস্ট") -> "08"
            m.contains("সেপ্টে") -> "09"
            m.contains("অক্টো") -> "10"
            m.contains("নভে") -> "11"
            m.contains("ডিসে") -> "12"
            else -> ""
        }
    }

    private fun normalizeDateStr(dateStr: String): String {
        val parts = dateStr.split('/', '-')
        if (parts.size == 3) {
            val p1 = parts[0].padStart(2, '0')
            val p2 = parts[1].padStart(2, '0')
            val p3 = parts[2]
            return if (p3.length == 4) {
                "$p3-$p2-$p1"
            } else if (p1.length == 4) {
                "$p1-$p2-$p3"
            } else {
                "$p1-$p2-$p3"
            }
        }
        return dateStr
    }
}
