package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 2D Spatial OCR & Geometry Layout Intelligence Engine.
 * Extracts individual Bounding Boxes for every text block/line,
 * applies 2D geometric alignment and multi-ray proximity search to map labels to their adjacent values,
 * and seamlessly synchronizes with DocumentOcrFormatter's multi-concept pipeline.
 */
object SpatialOcrEngine {

    data class OcrBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val imageWidth: Int,
        val imageHeight: Int
    ) {
        val width: Float get() = max(0f, right - left)
        val height: Float get() = max(0f, bottom - top)
        val centerX: Float get() = left + width / 2f
        val centerY: Float get() = top + height / 2f

        // Normalized 0.0 .. 1.0 coordinates for responsive canvas rendering
        val normLeft: Float get() = if (imageWidth > 0) left / imageWidth else 0f
        val normTop: Float get() = if (imageHeight > 0) top / imageHeight else 0f
        val normRight: Float get() = if (imageWidth > 0) right / imageWidth else 0f
        val normBottom: Float get() = if (imageHeight > 0) bottom / imageHeight else 0f
        val normWidth: Float get() = if (imageWidth > 0) width / imageWidth else 0f
        val normHeight: Float get() = if (imageHeight > 0) height / imageHeight else 0f

        fun verticalOverlapRatio(other: OcrBox): Float {
            val overlapTop = max(top, other.top)
            val overlapBottom = min(bottom, other.bottom)
            val overlap = max(0f, overlapBottom - overlapTop)
            val minH = min(height, other.height)
            return if (minH > 0) overlap / minH else 0f
        }

        fun horizontalOverlapRatio(other: OcrBox): Float {
            val overlapLeft = max(left, other.left)
            val overlapRight = min(right, other.right)
            val overlap = max(0f, overlapRight - overlapLeft)
            val minW = min(width, other.width)
            return if (minW > 0) overlap / minW else 0f
        }
    }

    enum class BoxRole {
        LABEL,
        VALUE,
        HEADER,
        PARAGRAPH,
        OTHER
    }

    data class OcrElement(
        val text: String,
        val box: OcrBox,
        var role: BoxRole = BoxRole.OTHER
    )

    data class OcrLine(
        val text: String,
        val box: OcrBox,
        val elements: List<OcrElement> = emptyList(),
        var role: BoxRole = BoxRole.OTHER
    )

    data class OcrBlock(
        val text: String,
        val box: OcrBox,
        val lines: List<OcrLine> = emptyList(),
        var role: BoxRole = BoxRole.OTHER
    )

    enum class SpatialRelation(val titleBn: String) {
        RIGHT_OF("ডানে অবস্থিত (Same Row)"),
        BELOW_OF("নিচে অবস্থিত (Below)"),
        SAME_LINE("একই লাইনে (Inline)"),
        CONTAINED("সংযুক্ত (Embedded)")
    }

    data class SpatialLabelValue(
        val labelKey: String,
        val labelNameBn: String,
        val labelText: String,
        val labelBox: OcrBox?,
        val valueText: String,
        val valueBox: OcrBox?,
        val relation: SpatialRelation,
        val category: String,
        val isImportant: Boolean = false
    )

    enum class OcrScriptMode(
        val titleBn: String,
        val subtitleBn: String,
        val badgeText: String
    ) {
        DEVANAGARI_BILINGUAL(
            "গুগল ML Kit v2 (বাংলা + ইংরেজি দেবনাগরী মডেল)",
            "বাংলা বর্ণমালা ও ইংরেজি মিশ্রিত নথির জন্য গুগল ML Kit v2",
            "বাংলা + ইংরেজি"
        ),
        LATIN_STANDARD(
            "গুগল ML Kit (ল্যাটিন/ইংরেজি মডেল)",
            "ইংরেজি টেক্সট ও সংখ্যার সাধারণ মডেল",
            "ইংরেজি/ল্যাটিন"
        )
    }

    data class SpatialAnalysisResult(
        val rawText: String,
        val blocks: List<OcrBlock>,
        val lines: List<OcrLine>,
        val labelValuePairs: List<SpatialLabelValue>,
        val formattedResult: DocumentOcrFormatter.FormattedDocResult,
        val imageWidth: Int,
        val imageHeight: Int,
        val scriptMode: OcrScriptMode = OcrScriptMode.DEVANAGARI_BILINGUAL
    )

    /**
     * Known Standard Bangladeshi Document Labels with extensive fuzzy variations.
     */
    private val KNOWN_LABELS = listOf(
        LabelDef("brn", "জন্ম নিবন্ধন নম্বর", listOf("Birth Registration Number", "Birth Registration No", "Registration Number", "BRN", "জন্ম নিবন্ধন নম্বর", "জন্ম নিবন্ধন নং", "নিবন্ধন নম্বর", "নিবন্ধন নং"), "সনদ বিবরণ", true),
        LabelDef("reg_date", "নিবন্ধনের তারিখ", listOf("Date of Registration", "Registration Date", "Reg Date", "নিবন্ধনের তারিখ", "নিবন্ধন তারিখ"), "সনদ বিবরণ", false),
        LabelDef("issue_date", "প্রদানের তারিখ", listOf("Date of Issuance", "Date of Issue", "Issue Date", "প্রদানের তারিখ", "ইস্যু তারিখ"), "সনদ বিবরণ", false),
        LabelDef("dob", "জন্ম তারিখ", listOf("Date of Birth", "DOB", "Birth Date", "জন্ম তারিখ", "জন্মতারিখ"), "ব্যক্তিগত তথ্য", true),
        LabelDef("dob_words", "জন্ম তারিখ (কথায়)", listOf("In Word", "In Words", "কথায়", "কথায়"), "ব্যক্তিগত তথ্য", false),
        LabelDef("gender", "লিঙ্গ", listOf("Sex", "Gender", "লিঙ্গ", "ছাত্র/ছাত্রী"), "ব্যক্তিগত তথ্য", true),
        LabelDef("name_bn", "নাম (বাংলা)", listOf("নাম :", "নামঃ", "নাম:", "ব্যক্তির নাম :", "ব্যক্তির নাম", "পূর্ণ নাম :", "পূর্ণ নাম", "শিক্ষার্থীর নাম :", "শিক্ষার্থীর নাম"), "ব্যক্তিগত তথ্য", true),
        LabelDef("name_en", "নাম (ইংরেজি)", listOf("Name :", "Name:", "Student's Name :", "Student's Name", "Student Name :", "Student Name", "Child's Name :", "Child's Name", "Name of Child", "Pupil's Name"), "ব্যক্তিগত তথ্য", true),
        LabelDef("father_bn", "পিতার নাম (বাংলা)", listOf("পিতার নাম :", "পিতার নাম", "পিতারনাম", "পিতা :", "পিতাঃ", "পিতা:"), "পারিবারিক তথ্য", true),
        LabelDef("father_en", "পিতার নাম (ইংরেজি)", listOf("Father's Name :", "Father's Name", "Fathers Name", "Father Name :", "Father Name", "Father :", "Father:"), "পারিবারিক তথ্য", true),
        LabelDef("mother_bn", "মাতার নাম (বাংলা)", listOf("মাতার নাম :", "মাতার নাম", "মাতারনাম", "মাতা :", "মাতাঃ", "মাতা:"), "পারিবারিক তথ্য", true),
        LabelDef("mother_en", "মাতার নাম (ইংরেজি)", listOf("Mother's Name :", "Mother's Name", "Mothers Name", "Mother Name :", "Mother Name", "Mother :", "Mother:"), "পারিবারিক তথ্য", true),
        LabelDef("father_nat", "পিতার জাতীয়তা", listOf("পিতার জাতীয়তা", "পিতার জাতীয়তা", "Father's Nationality", "Fathers Nationality"), "পারিবারিক তথ্য", false),
        LabelDef("mother_nat", "মাতার জাতীয়তা", listOf("মাতার জাতীয়তা", "Mother's Nationality", "মাতার জাতীয়তা", "Mothers Nationality"), "পারিবারিক তথ্য", false),
        LabelDef("nationality", "জাতীয়তা", listOf("Nationality", "জাতীয়তা", "জাতীয়তা"), "ব্যক্তিগত তথ্য", false),
        LabelDef("pob", "জন্মস্থান", listOf("Place of Birth", "Birth Place", "জন্মস্থান"), "ঠিকানা", false),
        LabelDef("perm_address", "স্থায়ী ঠিকানা", listOf("Permanent Address", "Permanent", "স্থায়ী ঠিকানা", "স্থায়ী ঠিকানা"), "ঠিকানা", true),
        LabelDef("pres_address", "বর্তমান ঠিকানা", listOf("Present Address", "Present", "বর্তমান ঠিকানা"), "ঠিকানা", false),
        LabelDef("nid_no", "জাতীয় পরিচয়পত্র নং", listOf("NID No", "National ID No", "National ID", "ভোটার নং", "পরিচয়পত্র নং", "স্মার্ট কার্ড নং"), "পরিচিতি", true),
        LabelDef("blood", "রক্তের গ্রুপ", listOf("Blood Group", "Blood", "রক্তের গ্রুপ", "গ্রুপ"), "ব্যক্তিগত তথ্য", false),
        LabelDef("student_class", "শ্রেণি", listOf("শ্রেণি", "Class", "শ্রেণী"), "একাডেমিক তথ্য", true),
        LabelDef("roll_no", "রোল নং", listOf("রোল নং", "রোল", "Roll No", "Roll", "ক্রমিক নং"), "একাডেমিক তথ্য", true),
        LabelDef("mobile", "মোবাইল নম্বর", listOf("মোবাইল", "Mobile", "ফোন", "Phone", "যোগাযোগ"), "যোগাযোগ", true)
    )

    private data class LabelDef(
        val key: String,
        val nameBn: String,
        val aliases: List<String>,
        val category: String,
        val isImportant: Boolean
    )

    /**
     * Executes Spatial ML Kit Recognition and builds 2D Bounding Box Geometry + Label Associator.
     */
    suspend fun analyzeBitmap(
        context: Context,
        bitmap: Bitmap,
        cloudOcrText: String? = null,
        scriptMode: OcrScriptMode = OcrScriptMode.DEVANAGARI_BILINGUAL
    ): SpatialAnalysisResult = withContext(Dispatchers.Default) {
        // Safe scaling for memory stability on all devices
        val safeBitmap = if (bitmap.width > 2560 || bitmap.height > 2560) {
            val scale = 2560f / maxOf(bitmap.width, bitmap.height)
            val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }

        val width = safeBitmap.width
        val height = safeBitmap.height
        val image = InputImage.fromBitmap(safeBitmap, 0)

        var effectiveMode = scriptMode
        var mlkitResult: Text? = null
        var lastError: Throwable? = null

        // 1. Try Requested Script Mode (Devanagari Bilingual Model)
        if (scriptMode == OcrScriptMode.DEVANAGARI_BILINGUAL) {
            try {
                val options = DevanagariTextRecognizerOptions.Builder().build()
                val devanagariRecognizer = TextRecognition.getClient(options)
                try {
                    mlkitResult = suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation {
                            try { devanagariRecognizer.close() } catch (_: Throwable) {}
                        }
                        devanagariRecognizer.process(image)
                            .addOnSuccessListener { textResult ->
                                if (continuation.isActive) {
                                    continuation.resume(textResult)
                                }
                            }
                            .addOnFailureListener { exc ->
                                if (continuation.isActive) {
                                    continuation.resumeWithException(exc)
                                }
                            }
                    }
                } finally {
                    try { devanagariRecognizer.close() } catch (_: Throwable) {}
                }
            } catch (t: Throwable) {
                lastError = t
                AppErrorLogger.logError("SpatialOcrEngine", "Devanagari ML Kit model failed or downloading: ${t.localizedMessage}", t)
            }
        }

        // 2. If Devanagari model was requested but failed/unavailable on device, fallback to Latin Recognizer
        if (mlkitResult == null) {
            try {
                val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    mlkitResult = suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation {
                            try { latinRecognizer.close() } catch (_: Throwable) {}
                        }
                        latinRecognizer.process(image)
                            .addOnSuccessListener { textResult ->
                                if (continuation.isActive) {
                                    continuation.resume(textResult)
                                }
                            }
                            .addOnFailureListener { exc ->
                                if (continuation.isActive) {
                                    continuation.resumeWithException(exc)
                                }
                            }
                    }
                    effectiveMode = OcrScriptMode.LATIN_STANDARD
                } finally {
                    try { latinRecognizer.close() } catch (_: Throwable) {}
                }
            } catch (t: Throwable) {
                lastError = t
                AppErrorLogger.logError("SpatialOcrEngine", "Latin OCR fallback failed: ${t.localizedMessage}", t)
            }
        }

        if (mlkitResult == null) {
            val errorMsg = lastError?.localizedMessage ?: "OCR ইঞ্জিন আরম্ভ করা সম্ভব হয়নি।"
            throw RuntimeException("OCR ব্যর্থ হয়েছে: $errorMsg", lastError)
        }

        val ocrBlocks = mutableListOf<OcrBlock>()
        val allLines = mutableListOf<OcrLine>()

        for (block in mlkitResult.textBlocks) {
            val blockBox = toOcrBox(block.boundingBox, width, height)
            val blockLines = mutableListOf<OcrLine>()

            for (line in block.lines) {
                val lineBox = toOcrBox(line.boundingBox, width, height)
                val lineElements = line.elements.map { elem ->
                    OcrElement(
                        text = elem.text,
                        box = toOcrBox(elem.boundingBox, width, height)
                    )
                }
                val ocrLine = OcrLine(
                    text = line.text,
                    box = lineBox,
                    elements = lineElements
                )
                blockLines.add(ocrLine)
                allLines.add(ocrLine)
            }

            ocrBlocks.add(
                OcrBlock(
                    text = block.text,
                    box = blockBox,
                    lines = blockLines
                )
            )
        }

        // Run 2D Spatial Proximity & Alignment Matcher
        val matchedPairs = matchLabelsAndValuesSpatially(allLines, width, height)

        // Tag Roles for visual rendering
        matchedPairs.forEach { pair ->
            pair.labelBox?.let { lBox ->
                allLines.find { it.box == lBox }?.role = BoxRole.LABEL
            }
            pair.valueBox?.let { vBox ->
                allLines.find { it.box == vBox }?.role = BoxRole.VALUE
            }
        }

        // Formatted Document Result using Multi-Concept Pipeline
        val effectiveRawText = if (!cloudOcrText.isNullOrBlank()) cloudOcrText else mlkitResult.text
        val baseFormatted = DocumentOcrFormatter.formatOcrText(effectiveRawText)

        // Merge spatial pairs into formatted fields if spatial found higher precision values
        val enhancedFields = baseFormatted.fields.toMutableList()
        val personNameKeys = setOf("name_bn", "name_en", "father_bn", "father_en", "mother_bn", "mother_en")
        for (pair in matchedPairs) {
            if (pair.labelKey in personNameKeys && !DocumentOcrFormatter.isValidPersonName(pair.valueText)) {
                continue
            }
            val existingIndex = enhancedFields.indexOfFirst { it.key == pair.labelKey }
            if (existingIndex >= 0) {
                if (pair.valueText.isNotBlank() && (enhancedFields[existingIndex].value.isBlank() || pair.valueText.length > enhancedFields[existingIndex].value.length)) {
                    enhancedFields[existingIndex] = enhancedFields[existingIndex].copy(value = pair.valueText)
                }
            } else if (pair.valueText.isNotBlank()) {
                enhancedFields.add(
                    DocumentOcrFormatter.FormattedField(
                        key = pair.labelKey,
                        labelBn = pair.labelNameBn,
                        labelEn = pair.labelText,
                        value = pair.valueText,
                        category = pair.category,
                        isImportant = pair.isImportant
                    )
                )
            }
        }

        val finalFormatted = baseFormatted.copy(fields = enhancedFields)

        SpatialAnalysisResult(
            rawText = effectiveRawText,
            blocks = ocrBlocks,
            lines = allLines,
            labelValuePairs = matchedPairs,
            formattedResult = finalFormatted,
            imageWidth = width,
            imageHeight = height,
            scriptMode = effectiveMode
        )
    }

    /**
     * 2D Spatial Alignment Algorithm with Multi-Ray Search:
     * Discovers labels and accurately identifies value blocks to the RIGHT or BELOW.
     */
    private fun matchLabelsAndValuesSpatially(
        allLines: List<OcrLine>,
        imgWidth: Int,
        imgHeight: Int
    ): List<SpatialLabelValue> {
        val results = mutableListOf<SpatialLabelValue>()
        val claimedValueBoxes = mutableSetOf<OcrBox>()

        for (labelDef in KNOWN_LABELS) {
            var bestMatch: SpatialLabelValue? = null

            for (line in allLines) {
                val lineText = line.text.trim()

                // Check if line contains any alias
                val matchedAlias = labelDef.aliases.firstOrNull { alias ->
                    lineText.contains(alias, ignoreCase = true)
                } ?: continue

                // Check Case 1: Embedded in the same line after delimiter
                val splitParts = lineText.split(Regex("[:ঃ=–—/|]"), limit = 2)
                if (splitParts.size >= 2 && splitParts[1].trim().isNotBlank()) {
                    val valText = cleanValueString(splitParts[1].trim())
                    if (valText.isNotBlank() && !isLineALabel(valText)) {
                        bestMatch = SpatialLabelValue(
                            labelKey = labelDef.key,
                            labelNameBn = labelDef.nameBn,
                            labelText = splitParts[0].trim(),
                            labelBox = line.box,
                            valueText = valText,
                            valueBox = line.box,
                            relation = SpatialRelation.SAME_LINE,
                            category = labelDef.category,
                            isImportant = labelDef.isImportant
                        )
                        break
                    }
                }

                // Check Case 2: Multi-Ray Rightward Row Search
                val labelBox = line.box
                val rightCandidates = allLines.filter { cand ->
                    cand != line &&
                    cand.box !in claimedValueBoxes &&
                    cand.box.left >= (labelBox.left + labelBox.width * 0.4f) &&
                    cand.box.left - labelBox.right < (imgWidth * 0.70f) &&
                    labelBox.verticalOverlapRatio(cand.box) > 0.30f &&
                    !isLineALabel(cand.text)
                }.sortedBy { it.box.left }

                if (rightCandidates.isNotEmpty()) {
                    val rightVal = rightCandidates.take(2).joinToString(" ") { it.text.trim() }
                    val cleanRight = cleanValueString(rightVal)
                    if (cleanRight.isNotBlank()) {
                        val vBox = rightCandidates[0].box
                        claimedValueBoxes.add(vBox)
                        bestMatch = SpatialLabelValue(
                            labelKey = labelDef.key,
                            labelNameBn = labelDef.nameBn,
                            labelText = matchedAlias,
                            labelBox = labelBox,
                            valueText = cleanRight,
                            valueBox = vBox,
                            relation = SpatialRelation.RIGHT_OF,
                            category = labelDef.category,
                            isImportant = labelDef.isImportant
                        )
                        break
                    }
                }

                // Check Case 3: Downward Column Search
                val belowCandidates = allLines.filter { cand ->
                    cand != line &&
                    cand.box !in claimedValueBoxes &&
                    cand.box.top >= labelBox.bottom - 4 &&
                    cand.box.top - labelBox.bottom < (imgHeight * 0.15f) &&
                    labelBox.horizontalOverlapRatio(cand.box) > 0.20f &&
                    !isLineALabel(cand.text)
                }.sortedBy { it.box.top }

                if (belowCandidates.isNotEmpty()) {
                    val belowVal = belowCandidates.take(2).joinToString(" ") { it.text.trim() }
                    val cleanBelow = cleanValueString(belowVal)
                    if (cleanBelow.isNotBlank()) {
                        val vBox = belowCandidates[0].box
                        claimedValueBoxes.add(vBox)
                        bestMatch = SpatialLabelValue(
                            labelKey = labelDef.key,
                            labelNameBn = labelDef.nameBn,
                            labelText = matchedAlias,
                            labelBox = labelBox,
                            valueText = cleanBelow,
                            valueBox = vBox,
                            relation = SpatialRelation.BELOW_OF,
                            category = labelDef.category,
                            isImportant = labelDef.isImportant
                        )
                        break
                    }
                }
            }

            if (bestMatch != null) {
                results.add(bestMatch)
            }
        }

        return results
    }

    private fun isLineALabel(text: String): Boolean {
        val lower = text.lowercase()
        return KNOWN_LABELS.any { def ->
            def.aliases.any { alias -> text.contains(alias, ignoreCase = true) }
        }
    }

    private fun cleanValueString(valStr: String): String {
        return valStr
            .replace(Regex("^[\\s:ঃ=–—\\-_./|,]+"), "")
            .replace(Regex("[\\s:ঃ=–—\\-_./|,]+$"), "")
            .trim()
    }

    private fun toOcrBox(rect: Rect?, imgWidth: Int, imgHeight: Int): OcrBox {
        return if (rect != null) {
            OcrBox(
                left = rect.left.toFloat().coerceAtLeast(0f),
                top = rect.top.toFloat().coerceAtLeast(0f),
                right = rect.right.toFloat().coerceAtMost(imgWidth.toFloat()),
                bottom = rect.bottom.toFloat().coerceAtMost(imgHeight.toFloat()),
                imageWidth = imgWidth,
                imageHeight = imgHeight
            )
        } else {
            OcrBox(0f, 0f, 0f, 0f, imgWidth, imgHeight)
        }
    }
}
