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
        .enableRawSizeMask()
        .build()

    /**
     * Segments the foreground person on-device using Google ML Kit Selfie Segmentation,
     * then paints the requested solid or transparent background color.
     */
    suspend fun removeBackgroundAndApplyColor(
        sourceBitmap: Bitmap,
        targetBgColor: Int?, // null = transparent
        confidenceThreshold: Float = 0.55f
    ): Bitmap = withContext(Dispatchers.Default) {
        val segmenter = Segmentation.getClient(segmenterOptions)
        try {
            val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
            val task = segmenter.process(inputImage)
            val mask: SegmentationMask = Tasks.await(task, 4000, TimeUnit.MILLISECONDS)

            val width = mask.width
            val height = mask.height
            val buffer: ByteBuffer = mask.buffer

            // Create output bitmap
            val output = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            // 1. Draw background color (if specified)
            if (targetBgColor != null) {
                canvas.drawColor(targetBgColor)
            } else {
                canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }

            // Extract foreground with smooth alpha blending
            val srcPixels = IntArray(sourceBitmap.width * sourceBitmap.height)
            sourceBitmap.getPixels(srcPixels, 0, sourceBitmap.width, 0, 0, sourceBitmap.width, sourceBitmap.height)

            val outPixels = IntArray(sourceBitmap.width * sourceBitmap.height)
            if (targetBgColor != null) {
                outPixels.fill(targetBgColor)
            }

            buffer.rewind()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val confidence = buffer.float // 0.0 to 1.0

                    val originalPixel = srcPixels[idx]
                    val origR = AndroidColor.red(originalPixel)
                    val origG = AndroidColor.green(originalPixel)
                    val origB = AndroidColor.blue(originalPixel)
                    val origA = AndroidColor.alpha(originalPixel)

                    if (targetBgColor == null) {
                        // Transparent mode
                        val alpha = (confidence * 255).toInt().coerceIn(0, 255)
                        val effectiveAlpha = (origA * alpha) / 255
                        outPixels[idx] = AndroidColor.argb(effectiveAlpha, origR, origG, origB)
                    } else {
                        // Solid background color blend
                        val bgR = AndroidColor.red(targetBgColor)
                        val bgG = AndroidColor.green(targetBgColor)
                        val bgB = AndroidColor.blue(targetBgColor)

                        // Smooth interpolation between BG and FG based on confidence
                        val factor = when {
                            confidence >= confidenceThreshold + 0.15f -> 1.0f
                            confidence <= confidenceThreshold - 0.15f -> 0.0f
                            else -> (confidence - (confidenceThreshold - 0.15f)) / 0.30f
                        }

                        val blendedR = ((origR * factor) + (bgR * (1f - factor))).toInt().coerceIn(0, 255)
                        val blendedG = ((origG * factor) + (bgG * (1f - factor))).toInt().coerceIn(0, 255)
                        val blendedB = ((origB * factor) + (bgB * (1f - factor))).toInt().coerceIn(0, 255)

                        outPixels[idx] = AndroidColor.argb(255, blendedR, blendedG, blendedB)
                    }
                }
            }

            output.setPixels(outPixels, 0, width, 0, 0, width, height)
            return@withContext output
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to algorithmic edge-aware segmentation
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
