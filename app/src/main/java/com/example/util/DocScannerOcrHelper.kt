package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Robust Utility helper for Document Scanning image decoding, stream handling, and Exif orientation
 */
object DocScannerOcrHelper {

    suspend fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap = withContext(Dispatchers.IO) {
        // Read full bytes or stream safely to avoid closed/one-time stream issues
        val imageBytes: ByteArray = try {
            readBytesFromUri(context, uri)
        } catch (e: Exception) {
            throw IllegalStateException("ইমেজ রিড করা যায়নি: ${e.localizedMessage ?: "অজ্ঞাত ফাইল ত্রুটি"}")
        }

        if (imageBytes.isEmpty()) {
            throw IllegalStateException("ইমেজ ফাইলের আকার শূন্য বা অকার্যকর")
        }

        // 1. Decode bounds to determine sample size
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw IllegalStateException("ইমেজের ডাইমেনশন পড়া যায়নি")
        }

        var sampleSize = 1
        val maxDecodedDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
        if (maxDecodedDim > maxDimension) {
            while ((maxDecodedDim / (sampleSize * 2)) >= maxDimension) {
                sampleSize *= 2
            }
        }

        // 2. Decode sampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }

        val decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: throw IllegalStateException("ইমেজ ডিকোড ব্যর্থ হয়েছে")

        // 3. Check and apply Exif orientation if needed
        val rotationDegrees = getExifOrientationDegrees(imageBytes)
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
        } else {
            decodedBitmap
        }
    }

    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray {
        // 1. Try content resolver openInputStream
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return stream.readBytes()
            }
        } catch (_: Exception) {}

        // 2. If it's a file URI or file path scheme
        val path = uri.path
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                FileInputStream(file).use { stream ->
                    return stream.readBytes()
                }
            }
        }

        throw IllegalStateException("ইমেজ লোড করা যায়নি। ফাইলের অনুমতি নেই বা ফাইলটি পাওয়া যায়নি।")
    }

    private fun getExifOrientationDegrees(imageBytes: ByteArray): Int {
        return try {
            ByteArrayInputStream(imageBytes).use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }
}

