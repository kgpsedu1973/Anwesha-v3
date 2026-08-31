package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
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
 * High-precision 2D Spatial OCR & Bounding Box Layout Analyzer.
 * Extracts bounding boxes for all text blocks, lines, and words,
 * and performs geometric 2D spatial association to understand which value
 * is located to the right or below each specific document label.
 */
object SpatialOcrEngine {

    data class OcrBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val imageWidth: Int = 1,
        val imageHeight: Int = 1
    ) {
        val width: Float get() = max(1f, right - left)
        val height: Float get() = max(1f, bottom - top)
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f

        // Normalized 0..1 coordinates
        val normLeft: Float get() = (left / imageWidth.toFloat()).coerceIn(0f, 1f)
        val normTop: Float get() = (top / imageHeight.toFloat()).coerceIn(0f, 1f)
        val normRight: Float get() = (right / imageWidth.toFloat()).coerceIn(0f, 1f)
        val normBottom: Float get() = (bottom / imageHeight.toFloat()).coerceIn(0f, 1f)
        val normWidth: Float get() = (width / imageWidth.toFloat()).coerceIn(0f, 1f)
        val normHeight: Float get() = (height / imageHeight.toFloat()).coerceIn(0f, 1f)

        fun intersects(other: OcrBox): Boolean {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top
        }

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

    data class SpatialAnalysisResult(
        val rawText: String,
        val blocks: List<OcrBlock>,
        val lines: List<OcrLine>,
        val labelValuePairs: List<SpatialLabelValue>,
        val formattedResult: DocumentOcrFormatter.FormattedDocResult,
        val imageWidth: Int,
        val imageHeight: Int
    )

    /**
     * Known Standard Bangladeshi Document Labels (English & Bengali aliases)
     */
    private val KNOWN_LABELS = listOf(
        LabelDef("brn", "জন্ম নিবন্ধন নম্বর", listOf("Birth Registration Number", "Registration Number", "BRN", "জন্ম নিবন্ধন নম্বর", "নিবন্ধন নম্বর"), "সনদ বিবরণ", true),
        LabelDef("reg_date", "নিবন্ধনের তারিখ", listOf("Date of Registration", "Registration Date", "নিবন্ধনের তারিখ", "নিবন্ধন তারিখ"), "সনদ বিবরণ", false),
        LabelDef("issue_date", "প্রদানের তারিখ", listOf("Date of Issuance", "Date of Issue", "প্রদানের তারিখ", "ইস্যু তারিখ"), "সনদ বিবরণ", false),
        LabelDef("dob", "জন্ম তারিখ", listOf("Date of Birth", "DOB", "জন্ম তারিখ"), "ব্যক্তিগত তথ্য", true),
        LabelDef("dob_words", "জন্ম তারিখ (কথায়)", listOf("In Word", "In Words", "কথায়"), "ব্যক্তিগত তথ্য", false),
        LabelDef("gender", "লিঙ্গ", listOf("Sex", "Gender", "লিঙ্গ"), "ব্যক্তিগত তথ্য", true),
        LabelDef("name_bn", "নাম (বাংলা)", listOf("নাম :", "নামঃ", "নাম:"), "ব্যক্তিগত তথ্য", true),
        LabelDef("name_en", "নাম (ইংরেজি)", listOf("Name :", "Name:", "Name"), "ব্যক্তিগত তথ্য", true),
        LabelDef("father_bn", "পিতার নাম (বাংলা)", listOf("পিতা :", "পিতাঃ", "পিতা:", "পিতার নাম :", "পিতার নাম"), "পারিবারিক তথ্য", true),
        LabelDef("father_en", "পিতার নাম (ইংরেজি)", listOf("Father :", "Father:", "Father's Name", "Father"), "পারিবারিক তথ্য", true),
        LabelDef("mother_bn", "মাতার নাম (বাংলা)", listOf("মাতা :", "মাতাঃ", "মাতা:", "মাতার নাম :", "মাতার নাম"), "পারিবারিক তথ্য", true),
        LabelDef("mother_en", "মাতার নাম (ইংরেজি)", listOf("Mother :", "Mother:", "Mother's Name", "Mother"), "পারিবারিক তথ্য", true),
        LabelDef("father_nat", "পিতার জাতীয়তা", listOf("পিতার জাতীয়তা", "Father's Nationality"), "পারিবারিক তথ্য", false),
        LabelDef("mother_nat", "মাতার জাতীয়তা", listOf("মাতার জাতীয়তা", "Mother's Nationality", "মাতার জাতীয়তা"), "পারিবারিক তথ্য", false),
        LabelDef("nationality", "জাতীয়তা", listOf("Nationality", "জাতীয়তা", "জাতীয়তা"), "ব্যক্তিগত তথ্য", false),
        LabelDef("pob", "জন্মস্থান", listOf("Place of Birth", "জন্মস্থান"), "ঠিকানা", false),
        LabelDef("perm_address", "স্থায়ী ঠিকানা", listOf("Permanent Address", "Permanent", "স্থায়ী ঠিকানা", "স্থায়ী ঠিকানা"), "ঠিকানা", true),
        LabelDef("pres_address", "বর্তমান ঠিকানা", listOf("Present Address", "বর্তমান ঠিকানা"), "ঠিকানা", false),
        LabelDef("nid_no", "জাতীয় পরিচয়পত্র নং", listOf("NID No", "National ID", "ভোটার নং", "পরিচয়পত্র নং"), "পরিচিতি", true),
        LabelDef("blood", "রক্তের গ্রুপ", listOf("Blood Group", "রক্তের গ্রুপ"), "ব্যক্তিগত তথ্য", false),
        LabelDef("student_class", "শ্রেণি", listOf("শ্রেণি", "Class"), "একাডেমিক তথ্য", true),
        LabelDef("roll_no", "রোল নং", listOf("রোল নং", "রোল", "Roll No", "Roll"), "একাডেমিক তথ্য", true),
        LabelDef("mobile", "মোবাইল নম্বর", listOf("মোবাইল", "Mobile", "ফোন", "Phone"), "যোগাযোগ", true)
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
        cloudOcrText: String? = null
    ): SpatialAnalysisResult = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        val mlkitResult: Text = try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { textResult ->
                        continuation.resume(textResult)
                    }
                    .addOnFailureListener { exc ->
                        continuation.resumeWithException(exc)
                    }
            }
        } catch (e: Exception) {
            AppErrorLogger.logError("SpatialOcrEngine", "MLKit OCR processing failed: ${e.localizedMessage}", e)
            throw e
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

        // Formatted Document Result
        val effectiveRawText = if (!cloudOcrText.isNullOrBlank()) cloudOcrText else mlkitResult.text
        val baseFormatted = DocumentOcrFormatter.formatOcrText(effectiveRawText)

        // Merge spatial pairs into formatted fields if spatial found higher precision values
        val enhancedFields = baseFormatted.fields.toMutableList()
        for (pair in matchedPairs) {
            val existingIndex = enhancedFields.indexOfFirst { it.key == pair.labelKey }
            if (existingIndex >= 0) {
                // If spatial value is clean and non-empty, keep or refine
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
            imageHeight = height
        )
    }

    /**
     * 2D Spatial Alignment Algorithm:
     * Discovers labels and accurately identifies the value block/element to its immediate RIGHT or BELOW.
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

                // Check if this line contains any alias of the label
                val matchedAlias = labelDef.aliases.firstOrNull { alias ->
                    lineText.contains(alias, ignoreCase = true)
                } ?: continue

                // Check Case 1: Value is embedded in the same line after a separator
                // Example: "Birth Registration Number: 19961511813129309" or "Sex : Female"
                val splitParts = lineText.split(Regex("[:ঃ=–-]"), limit = 2)
                if (splitParts.size >= 2 && splitParts[1].trim().isNotBlank()) {
                    val valText = cleanValueString(splitParts[1].trim())
                    if (valText.isNotBlank()) {
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

                // Check Case 2: Value is located to the RIGHT of the label on the same horizontal row
                val labelBox = line.box
                val rightCandidates = allLines.filter { cand ->
                    cand != line &&
                    cand.box !in claimedValueBoxes &&
                    cand.box.left >= (labelBox.left + labelBox.width * 0.5f) && // to the right
                    cand.box.left - labelBox.right < (imgWidth * 0.65f) && // reasonable distance
                    labelBox.verticalOverlapRatio(cand.box) > 0.35f && // on same row
                    !isLineALabel(cand.text) // candidate is not another label
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

                // Check Case 3: Value is located DIRECTLY BELOW the label in a column layout
                // Example:
                // "Date of Registration"
                // "23/09/2023"
                val belowCandidates = allLines.filter { cand ->
                    cand != line &&
                    cand.box !in claimedValueBoxes &&
                    cand.box.top >= labelBox.bottom - 4 && // below
                    cand.box.top - labelBox.bottom < (imgHeight * 0.12f) && // not too far below
                    labelBox.horizontalOverlapRatio(cand.box) > 0.25f && // aligned in column
                    !isLineALabel(cand.text) // candidate is not another label
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
