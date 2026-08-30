package com.example.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.OcrLanguage
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface OcrRepository {
    suspend fun initialize(): Boolean
    suspend fun recognizeText(bitmap: Bitmap, language: OcrLanguage = OcrLanguage.AUTO_DUAL): Result<Pair<String, Int>>
    fun isReady(): Boolean
    fun getTrainedDataFileSizes(): Map<String, Long>
}

class TesseractOcrRepository(
    private val context: Context
) : OcrRepository {

    companion object {
        private const val TAG = "TesseractOcrRepo"
        private const val TESSDATA_FOLDER = "tessdata"
        val TRAINEDDATA_FILES = listOf("ben.traineddata", "eng.traineddata")
    }

    private var tessBaseAPI: TessBaseAPI? = null
    private var currentLoadedLanguage: String? = null
    private var initialized: Boolean = false

    override fun isReady(): Boolean = initialized

    override fun getTrainedDataFileSizes(): Map<String, Long> {
        val sizes = mutableMapOf<String, Long>()
        val tessDir = File(context.filesDir, TESSDATA_FOLDER)
        TRAINEDDATA_FILES.forEach { fileName ->
            val file = File(tessDir, fileName)
            if (file.exists()) {
                sizes[fileName] = file.length()
            } else {
                try {
                    context.assets.open("$TESSDATA_FOLDER/$fileName").use { input ->
                        sizes[fileName] = input.available().toLong()
                    }
                } catch (e: Exception) {
                    sizes[fileName] = 0L
                }
            }
        }
        return sizes
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureTessDataExtracted()
            initialized = true
            Log.i(TAG, "Tesseract trained data verified and ready at ${context.filesDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tesseract trained data: ${e.message}", e)
            initialized = false
            false
        }
    }

    private fun ensureTessDataExtracted() {
        val tessDir = File(context.filesDir, TESSDATA_FOLDER)
        if (!tessDir.exists()) {
            tessDir.mkdirs()
        }

        TRAINEDDATA_FILES.forEach { fileName ->
            val targetFile = File(tessDir, fileName)
            var needCopy = !targetFile.exists() || targetFile.length() == 0L

            if (!needCopy) {
                // Verify size against asset
                try {
                    val assetLength = context.assets.open("$TESSDATA_FOLDER/$fileName").use { it.available() }
                    if (targetFile.length() < assetLength) {
                        needCopy = true
                    }
                } catch (e: Exception) {
                    // Ignore asset check error
                }
            }

            if (needCopy) {
                Log.d(TAG, "Copying $fileName from assets to ${targetFile.absolutePath}...")
                context.assets.open("$TESSDATA_FOLDER/$fileName").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Extracted $fileName (${targetFile.length()} bytes) successfully.")
            }
        }
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        language: OcrLanguage
    ): Result<Pair<String, Int>> = withContext(Dispatchers.Default) {
        try {
            ensureTessDataExtracted()

            val dataPath = context.filesDir.absolutePath
            val langCode = language.code

            // Reuse or recreate TessBaseAPI instance if language changed
            if (tessBaseAPI == null || currentLoadedLanguage != langCode) {
                tessBaseAPI?.recycle()
                val api = TessBaseAPI()
                val initSuccess = api.init(dataPath, langCode)
                if (!initSuccess) {
                    api.recycle()
                    return@withContext Result.failure(
                        IllegalStateException("Tesseract initialization failed for language code: $langCode")
                    )
                }
                tessBaseAPI = api
                currentLoadedLanguage = langCode
            }

            val api = tessBaseAPI ?: return@withContext Result.failure(
                IllegalStateException("TessBaseAPI is not initialized")
            )

            // Convert bitmap to ARGB_8888 if necessary
            val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                bitmap
            }

            api.setImage(safeBitmap)
            val recognizedText = api.utF8Text ?: ""
            val confidence = api.meanConfidence()
            api.clear()

            if (safeBitmap != bitmap && !safeBitmap.isRecycled) {
                safeBitmap.recycle()
            }

            Log.i(TAG, "OCR recognition complete in offline mode: ${recognizedText.length} chars, confidence: $confidence%")
            Result.success(Pair(recognizedText, confidence))
        } catch (e: Exception) {
            Log.e(TAG, "OCR error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
