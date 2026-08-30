package com.example.domain.model

import com.example.util.ExtractedStudentData

enum class OcrLanguage(
    val code: String,
    val titleBn: String,
    val titleEn: String,
    val requiresFiles: List<String>
) {
    BENGALI_AND_ENGLISH("ben+eng", "বাংলা + ইংরেজি (দ্বৈত)", "Auto (Bangla + English)", listOf("ben.traineddata", "eng.traineddata")),
    BANGLA("ben", "বাংলা (Bangla)", "Bangla", listOf("ben.traineddata")),
    ENGLISH("eng", "ইংরেজি (English)", "English", listOf("eng.traineddata"));

    val displayNameBn: String get() = titleBn
    val displayNameEn: String get() = titleEn

    companion object {
        val AUTO_DUAL = BENGALI_AND_ENGLISH
        val BENGALI = BANGLA
    }
}

data class OcrResult(
    val recognizedText: String,
    val language: OcrLanguage,
    val meanConfidence: Float,
    val extractedData: ExtractedStudentData,
    val processingTimeMs: Long,
    val isOffline: Boolean = true
) {
    val rawText: String get() = recognizedText
    val confidence: Int get() = meanConfidence.toInt()
}
