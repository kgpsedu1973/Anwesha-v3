package com.example.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.example.repository.OcrRepository
import com.example.util.DocScannerOcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrUseCase(
    private val ocrRepository: OcrRepository,
    private val imageEnhancementUseCase: ImageEnhancementUseCase
) {
    companion object {
        private const val TAG = "OcrUseCase"
    }

    suspend fun execute(
        sourceBitmap: Bitmap,
        language: OcrLanguage = OcrLanguage.AUTO_DUAL,
        enhancementMode: EnhancementMode = EnhancementMode.BW_TEXT,
        rotationDegrees: Float = 0f
    ): OcrResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // 1. Process Image through ImageEnhancementUseCase (Straightened + CamScanner Filtered)
        val enhancedBitmap = try {
            imageEnhancementUseCase.execute(
                sourceBitmap = sourceBitmap,
                mode = enhancementMode,
                rotationDegrees = rotationDegrees
            )
        } catch (e: Exception) {
            Log.w(TAG, "Image enhancement failed, falling back to source bitmap: ${e.message}")
            sourceBitmap
        }

        // 2. Feed Enhanced Bitmap to Tesseract OCR Repository
        val ocrResult = ocrRepository.recognizeText(enhancedBitmap, language)

        val (recognizedText, confidence) = if (ocrResult.isSuccess) {
            ocrResult.getOrThrow()
        } else {
            Log.e(TAG, "Tesseract OCR failed: ${ocrResult.exceptionOrNull()?.message}")
            // Fallback to ML Kit if available on failure
            try {
                val mlKitText = DocScannerOcrHelper.recognizeTextFromBitmap(enhancedBitmap)
                Pair(mlKitText.text, 80)
            } catch (e: Exception) {
                Pair("", 0)
            }
        }

        // 3. Extract Structured School & Student Fields
        val extractedData = DocScannerOcrHelper.extractStudentInformation(recognizedText)
        val duration = System.currentTimeMillis() - startTime

        OcrResult(
            recognizedText = recognizedText,
            language = language,
            meanConfidence = confidence.toFloat(),
            extractedData = extractedData,
            processingTimeMs = duration,
            isOffline = true
        )
    }

    suspend fun recognizeText(
        bitmap: Bitmap,
        language: OcrLanguage = OcrLanguage.AUTO_DUAL
    ): OcrResult = execute(sourceBitmap = bitmap, language = language)

    fun isEngineReady(): Boolean = ocrRepository.isReady()

    suspend fun initializeEngine(): Boolean = ocrRepository.initialize()

    fun getTrainedDataSizes(): Map<String, Long> = ocrRepository.getTrainedDataFileSizes()
}
