package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Utility helper for Document Scanning image decoding and basic utilities
 */
object DocScannerOcrHelper {

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
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("ইমেজ ডিকোড ব্যর্থ হয়েছে")
    }
}
