package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object MLKitBackgroundRemover {

    private val segmenterOptions = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()

    /**
     * Segments the foreground person on-device using Google ML Kit Selfie Segmentation,
     * then paints the requested solid or transparent background color.
     */
    suspend fun removeBackgroundAndApplyColor(
        sourceBitmap: Bitmap,
        targetBgColor: Int?, // null = transparent
        confidenceThreshold: Float = 0.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        val segmenter = Segmentation.getClient(segmenterOptions)
        try {
            val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
            val task = segmenter.process(inputImage)
            val mask: SegmentationMask = Tasks.await(task, 4000, TimeUnit.MILLISECONDS)

            val maskWidth = mask.width
            val maskHeight = mask.height
            val buffer: ByteBuffer = mask.buffer
            buffer.rewind()

            // 1. Create Raw Mask Bitmap from ML Kit buffer (maskWidth x maskHeight)
            val rawMaskPixels = IntArray(maskWidth * maskHeight)
            val totalPixels = maskWidth * maskHeight
            for (i in 0 until totalPixels) {
                val confidence = if (buffer.hasRemaining()) buffer.float else 0f
                // Smooth Sigmoidal/Linear feathering around threshold for clean edges
                val alphaFactor = when {
                    confidence >= confidenceThreshold + 0.12f -> 1.0f
                    confidence <= confidenceThreshold - 0.12f -> 0.0f
                    else -> (confidence - (confidenceThreshold - 0.12f)) / 0.24f
                }
                val alpha = (alphaFactor.coerceIn(0f, 1f) * 255).toInt()
                rawMaskPixels[i] = AndroidColor.argb(alpha, 255, 255, 255)
            }

            val rawMaskBitmap = Bitmap.createBitmap(rawMaskPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)

            // 2. Scale Mask Bitmap accurately to match sourceBitmap's exact pixel dimensions
            val srcW = sourceBitmap.width
            val srcH = sourceBitmap.height
            val scaledMaskBitmap = if (maskWidth != srcW || maskHeight != srcH) {
                Bitmap.createScaledBitmap(rawMaskBitmap, srcW, srcH, true)
            } else {
                rawMaskBitmap
            }

            // 3. Composite source photo and background using the scaled mask
            val srcPixels = IntArray(srcW * srcH)
            val maskPixels = IntArray(srcW * srcH)
            val outPixels = IntArray(srcW * srcH)

            sourceBitmap.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
            scaledMaskBitmap.getPixels(maskPixels, 0, srcW, 0, 0, srcW, srcH)

            val bgR = if (targetBgColor != null) AndroidColor.red(targetBgColor) else 0
            val bgG = if (targetBgColor != null) AndroidColor.green(targetBgColor) else 0
            val bgB = if (targetBgColor != null) AndroidColor.blue(targetBgColor) else 0

            for (i in 0 until (srcW * srcH)) {
                val maskAlpha = AndroidColor.alpha(maskPixels[i]) / 255.0f
                val srcPixel = srcPixels[i]
                val origR = AndroidColor.red(srcPixel)
                val origG = AndroidColor.green(srcPixel)
                val origB = AndroidColor.blue(srcPixel)
                val origA = AndroidColor.alpha(srcPixel)

                if (targetBgColor == null) {
                    // Transparent PNG mode
                    val finalAlpha = (origA * maskAlpha).toInt().coerceIn(0, 255)
                    outPixels[i] = AndroidColor.argb(finalAlpha, origR, origG, origB)
                } else {
                    // Solid background color blend
                    val blendedR = ((origR * maskAlpha) + (bgR * (1.0f - maskAlpha))).toInt().coerceIn(0, 255)
                    val blendedG = ((origG * maskAlpha) + (bgG * (1.0f - maskAlpha))).toInt().coerceIn(0, 255)
                    val blendedB = ((origB * maskAlpha) + (bgB * (1.0f - maskAlpha))).toInt().coerceIn(0, 255)
                    outPixels[i] = AndroidColor.argb(255, blendedR, blendedG, blendedB)
                }
            }

            val output = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
            output.setPixels(outPixels, 0, srcW, 0, 0, srcW, srcH)
            return@withContext output
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext fallbackEdgeAwareBackground(sourceBitmap, targetBgColor ?: AndroidColor.WHITE)
        } finally {
            try {
                segmenter.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun fallbackEdgeAwareBackground(source: Bitmap, targetColor: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val samplePoints = listOf(
            pixels[0],
            pixels[w - 1],
            pixels[w / 4],
            pixels[(w * 3) / 4],
            pixels[((h / 4) * w)],
            pixels[((h / 4) * w) + (w - 1)]
        )

        var avgR = 0
        var avgG = 0
        var avgB = 0
        samplePoints.forEach { color ->
            avgR += AndroidColor.red(color)
            avgG += AndroidColor.green(color)
            avgB += AndroidColor.blue(color)
        }
        val count = samplePoints.size
        avgR /= count
        avgG /= count
        avgB /= count

        val tolSq = (35 * 2.5).let { it * it }
        val centerX = w / 2
        val centerY = (h * 0.45).toInt()
        val subjectRadiusX = w * 0.38
        val subjectRadiusY = h * 0.45

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val p = pixels[idx]
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val b = AndroidColor.blue(p)

                val dr = r - avgR
                val dg = g - avgG
                val db = b - avgB
                val distSq = dr * dr + dg * dg + db * db

                val dxNorm = (x - centerX) / subjectRadiusX
                val dyNorm = (y - centerY) / subjectRadiusY
                val inSubjectCore = (dxNorm * dxNorm + dyNorm * dyNorm) < 0.65

                if (!inSubjectCore && distSq < tolSq) {
                    pixels[idx] = targetColor
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return resultBitmap
    }
}
