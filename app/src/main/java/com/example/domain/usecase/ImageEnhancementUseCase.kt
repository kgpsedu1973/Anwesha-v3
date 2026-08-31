package com.example.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Filter modes for document enhancement
 */
enum class EnhancementMode(val titleBn: String, val titleEn: String) {
    ORIGINAL("আসল (Original)", "Original"),
    MAGIC_COLOR("ম্যাজিক কালার (Magic Color)", "Magic Color"),
    ENHANCED_COLOR("এনহ্যান্সড কালার (Enhanced Color)", "Enhanced Color"),
    BW_TEXT("সাদা-কালো টেক্সট (B&W Text)", "B&W Text"),
    GRAYSCALE("গ্রেস্কেল (Grayscale)", "Grayscale"),
    LIGHTEN("উজ্জ্বল (Lighten)", "Lighten")
}

/**
 * Clean Architecture UseCase for document image processing & enhancement.
 *
 * Employs OpenCV's Core and Imgproc modules for CamScanner-grade output:
 * 1. "Magic Color" Illumination Normalization (Large-kernel Gaussian lighting map division + background whitening + saturation boost)
 * 2. Gray-World Auto White Balance Correction (fixes yellow/gray indoor lighting)
 * 3. LAB-space CLAHE (Contrast Limited Adaptive Histogram Equalization) for uniform white backgrounds
 * 4. Unsharp Masking (counters lens & compression blur for crystal-clear text)
 * 5. Adaptive Gaussian Thresholding for pure binary black-on-white text mode
 */
class ImageEnhancementUseCase {

    private val isOpenCvReady: Boolean by lazy {
        try {
            if (OpenCVLoader.initLocal()) {
                true
            } else {
                OpenCVLoader.initDebug()
            }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Primary entry point to execute post-processing on a scanned/cropped Bitmap.
     */
    suspend fun execute(
        sourceBitmap: Bitmap,
        mode: EnhancementMode,
        rotationDegrees: Float = 0f
    ): Bitmap = withContext(Dispatchers.Default) {
        var base = sourceBitmap

        // Handle rotation if needed
        if (rotationDegrees % 360f != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            base = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
        }

        when (mode) {
            EnhancementMode.ORIGINAL -> base

            EnhancementMode.MAGIC_COLOR -> {
                if (isOpenCvReady) {
                    magicColorOpenCV(base)
                } else {
                    magicColorNative(base)
                }
            }

            EnhancementMode.ENHANCED_COLOR -> {
                if (isOpenCvReady) {
                    enhanceColorOpenCV(base)
                } else {
                    enhanceColorNative(base)
                }
            }

            EnhancementMode.BW_TEXT -> {
                if (isOpenCvReady) {
                    adaptiveThresholdOpenCV(base)
                } else {
                    adaptiveThresholdNative(base)
                }
            }

            EnhancementMode.GRAYSCALE -> {
                if (isOpenCvReady) {
                    grayscaleClaheOpenCV(base)
                } else {
                    grayscaleNative(base)
                }
            }

            EnhancementMode.LIGHTEN -> {
                if (isOpenCvReady) {
                    lightenOpenCV(base)
                } else {
                    lightenNative(base)
                }
            }
        }
    }

    // =========================================================================
    // OpenCV Pipeline Implementations (Core + Imgproc)
    // =========================================================================

    /**
     * CamScanner-Grade Magic Color:
     * 1. Large-kernel Gaussian lighting map estimation
     * 2. Illumination division (original / lighting_map) to eliminate shadows and uneven light
     * 3. Background whitening clip & text contrast preservation
     * 4. Moderate saturation boost (~20%) in HSV space for vivid stamps and colored diagrams
     * 5. Unsharp masking for crisp text edges
     */
    private fun magicColorOpenCV(srcBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        val rgbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        // Convert to Float Mat for high-precision division
        val floatSrc = Mat()
        rgbMat.convertTo(floatSrc, CvType.CV_32FC3)

        // Step 1: Estimate lighting map with large-kernel Gaussian Blur
        val maxDim = max(srcBitmap.width, srcBitmap.height)
        val kSize = (maxDim / 20).let { if (it % 2 == 0) it + 1 else it }.coerceIn(31, 151)
        val sigma = kSize / 3.0

        val bgLightingMap = Mat()
        Imgproc.GaussianBlur(floatSrc, bgLightingMap, Size(kSize.toDouble(), kSize.toDouble()), sigma)

        // Add small epsilon to prevent division by zero
        Core.add(bgLightingMap, Scalar(1.0, 1.0, 1.0), bgLightingMap)

        // Step 2: Illumination Normalization (Divide original by lighting map and scale by 255.0)
        val normalized = Mat()
        Core.divide(floatSrc, bgLightingMap, normalized, 255.0)

        // Step 3: Push near-white background pixels to pure white and keep foreground dark
        val normalized8u = Mat()
        normalized.convertTo(normalized8u, CvType.CV_8UC3)

        val lut = Mat(1, 256, CvType.CV_8U)
        val lutData = ByteArray(256)
        for (i in 0..255) {
            val v = when {
                i >= 235 -> 255
                i >= 150 -> {
                    val t = (i - 150) / 85.0
                    (150 + t * 105).roundToInt().coerceIn(0, 255)
                }
                i < 100 -> {
                    (i * 0.88).roundToInt().coerceIn(0, 255)
                }
                else -> i
            }
            lutData[i] = v.toByte()
        }
        lut.put(0, 0, lutData)

        val whiteCleanedMat = Mat()
        Core.LUT(normalized8u, lut, whiteCleanedMat)

        // Step 4: Boost saturation moderately (~20%) in HSV color space
        val hsvMat = Mat()
        Imgproc.cvtColor(whiteCleanedMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        val hsvChannels = ArrayList<Mat>(3)
        Core.split(hsvMat, hsvChannels)

        // Scale Saturation channel (index 1) by 1.20
        Core.multiply(hsvChannels[1], Scalar(1.20), hsvChannels[1])
        Core.merge(hsvChannels, hsvMat)

        val saturatedRgb = Mat()
        Imgproc.cvtColor(hsvMat, saturatedRgb, Imgproc.COLOR_HSV2RGB)

        // Step 5: Unsharp Masking for crisp text
        val sharpened = applyUnsharpMaskOpenCV(saturatedRgb, amount = 1.2f, sigma = 1.2)

        val outBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(sharpened, outBitmap)

        // Memory cleanup
        srcMat.release()
        rgbMat.release()
        floatSrc.release()
        bgLightingMap.release()
        normalized.release()
        normalized8u.release()
        lut.release()
        whiteCleanedMat.release()
        hsvMat.release()
        hsvChannels.forEach { it.release() }
        saturatedRgb.release()
        sharpened.release()

        return outBitmap
    }

    /**
     * Enhanced Color:
     * 1. Gray-World Auto White Balance
     * 2. CLAHE on L-channel in LAB space
     * 3. Unsharp Masking
     */
    private fun enhanceColorOpenCV(srcBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        // Convert RGBA -> RGB / BGR
        val rgbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        // Step 1: Auto White Balance (Gray-World Algorithm)
        val wbMat = applyGrayWorldWhiteBalanceOpenCV(rgbMat)

        // Step 2: Unsharp Masking
        val sharpMat = applyUnsharpMaskOpenCV(wbMat, amount = 1.35f, sigma = 1.5)

        // Step 3: Convert to LAB and apply CLAHE to L-channel
        val labMat = Mat()
        Imgproc.cvtColor(sharpMat, labMat, Imgproc.COLOR_RGB2Lab)

        val labChannels = ArrayList<Mat>(3)
        Core.split(labMat, labChannels)

        val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
        val lEnhanced = Mat()
        clahe.apply(labChannels[0], lEnhanced)
        labChannels[0] = lEnhanced

        val mergedLab = Mat()
        Core.merge(labChannels, mergedLab)

        val resultRgb = Mat()
        Imgproc.cvtColor(mergedLab, resultRgb, Imgproc.COLOR_Lab2RGB)

        // Convert back to ARGB_8888 Bitmap
        val outBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultRgb, outBitmap)

        // Release Mats
        srcMat.release()
        rgbMat.release()
        wbMat.release()
        sharpMat.release()
        labMat.release()
        labChannels.forEach { it.release() }
        clahe.collectGarbage()
        lEnhanced.release()
        mergedLab.release()
        resultRgb.release()

        return outBitmap
    }

    /**
     * B&W Text Mode using Adaptive Gaussian Thresholding
     */
    private fun adaptiveThresholdOpenCV(srcBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        // Light bilateral or Gaussian filter to reduce scanner grain
        val smoothMat = Mat()
        Imgproc.GaussianBlur(grayMat, smoothMat, Size(3.0, 3.0), 0.0)

        // Adaptive Gaussian thresholding
        val binMat = Mat()
        Imgproc.adaptiveThreshold(
            smoothMat,
            binMat,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            25,
            11.0
        )

        val outBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(binMat, outBitmap)

        srcMat.release()
        grayMat.release()
        smoothMat.release()
        binMat.release()

        return outBitmap
    }

    /**
     * Grayscale with CLAHE contrast enhancement
     */
    private fun grayscaleClaheOpenCV(srcBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhancedMat = Mat()
        clahe.apply(grayMat, enhancedMat)

        val outBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(enhancedMat, outBitmap)

        srcMat.release()
        grayMat.release()
        enhancedMat.release()

        return outBitmap
    }

    /**
     * Lighten filter mode with mild CLAHE & gamma lift
     */
    private fun lightenOpenCV(srcBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        val rgbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        val labMat = Mat()
        Imgproc.cvtColor(rgbMat, labMat, Imgproc.COLOR_RGB2Lab)

        val labChannels = ArrayList<Mat>(3)
        Core.split(labMat, labChannels)

        // Mild CLAHE
        val clahe = Imgproc.createCLAHE(1.6, Size(8.0, 8.0))
        val lEnhanced = Mat()
        clahe.apply(labChannels[0], lEnhanced)

        // Lift brightness slightly
        Core.add(lEnhanced, Scalar(18.0), lEnhanced)
        labChannels[0] = lEnhanced

        val mergedLab = Mat()
        Core.merge(labChannels, mergedLab)

        val resultRgb = Mat()
        Imgproc.cvtColor(mergedLab, resultRgb, Imgproc.COLOR_Lab2RGB)

        val outBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultRgb, outBitmap)

        srcMat.release()
        rgbMat.release()
        labMat.release()
        labChannels.forEach { it.release() }
        lEnhanced.release()
        mergedLab.release()
        resultRgb.release()

        return outBitmap
    }

    /**
     * Gray-World White Balance algorithm in OpenCV
     */
    private fun applyGrayWorldWhiteBalanceOpenCV(srcRgb: Mat): Mat {
        val channels = ArrayList<Mat>(3)
        Core.split(srcRgb, channels)

        val meanR = Core.mean(channels[0]).`val`[0]
        val meanG = Core.mean(channels[1]).`val`[0]
        val meanB = Core.mean(channels[2]).`val`[0]

        val meanGray = (meanR + meanG + meanB) / 3.0

        if (meanR > 0) Core.multiply(channels[0], Scalar(meanGray / meanR), channels[0])
        if (meanG > 0) Core.multiply(channels[1], Scalar(meanGray / meanG), channels[1])
        if (meanB > 0) Core.multiply(channels[2], Scalar(meanGray / meanB), channels[2])

        val dst = Mat()
        Core.merge(channels, dst)
        channels.forEach { it.release() }
        return dst
    }

    /**
     * Unsharp Masking in OpenCV: dst = src * (1 + amount) - blur * amount
     */
    private fun applyUnsharpMaskOpenCV(src: Mat, amount: Float = 1.3f, sigma: Double = 1.5): Mat {
        val blurred = Mat()
        Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), sigma)

        val sharpened = Mat()
        val alpha = 1.0 + amount.toDouble()
        val beta = -amount.toDouble()
        Core.addWeighted(src, alpha, blurred, beta, 0.0, sharpened)

        blurred.release()
        return sharpened
    }

    // =========================================================================
    // High-Performance Native Fallback Pipeline (Zero-Crash Guarantee)
    // =========================================================================

    private fun magicColorNative(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // Step 1: Create downscaled version to estimate background lighting map quickly
        val scaleFactor = max(1, max(width, height) / 100)
        val smallW = max(1, width / scaleFactor)
        val smallH = max(1, height / scaleFactor)
        val smallBmp = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val smallPixels = IntArray(smallW * smallH)
        smallBmp.getPixels(smallPixels, 0, smallW, 0, 0, smallW, smallH)
        if (smallBmp != src) smallBmp.recycle()

        // Fast box blur on small image for lighting map
        val blurredSmall = IntArray(smallW * smallH)
        val radius = max(2, min(smallW, smallH) / 10)
        for (y in 0 until smallH) {
            for (x in 0 until smallW) {
                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0
                val yMin = max(0, y - radius)
                val yMax = min(smallH - 1, y + radius)
                val xMin = max(0, x - radius)
                val xMax = min(smallW - 1, x + radius)
                for (ny in yMin..yMax) {
                    for (nx in xMin..xMax) {
                        val p = smallPixels[ny * smallW + nx]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
                val rAvg = (rSum / count).toInt().coerceIn(1, 255)
                val gAvg = (gSum / count).toInt().coerceIn(1, 255)
                val bAvg = (bSum / count).toInt().coerceIn(1, 255)
                blurredSmall[y * smallW + x] = (0xFF shl 24) or (rAvg shl 16) or (gAvg shl 8) or bAvg
            }
        }

        // Step 2, 3, 4: Divide original by lighting map, push white, boost saturation
        for (y in 0 until height) {
            val sy = (y / scaleFactor).coerceIn(0, smallH - 1)
            for (x in 0 until width) {
                val sx = (x / scaleFactor).coerceIn(0, smallW - 1)
                val bg = blurredSmall[sy * smallW + sx]
                val bgR = max(1, (bg shr 16) and 0xFF)
                val bgG = max(1, (bg shr 8) and 0xFF)
                val bgB = max(1, bg and 0xFF)

                val orig = pixels[y * width + x]
                val a = (orig shr 24) and 0xFF
                val r = (orig shr 16) and 0xFF
                val g = (orig shr 8) and 0xFF
                val b = orig and 0xFF

                // Illumination Division (orig / bg * 255)
                var normR = ((r.toFloat() / bgR.toFloat()) * 255f).roundToInt().coerceIn(0, 255)
                var normG = ((g.toFloat() / bgG.toFloat()) * 255f).roundToInt().coerceIn(0, 255)
                var normB = ((b.toFloat() / bgB.toFloat()) * 255f).roundToInt().coerceIn(0, 255)

                // Push background to pure white
                val lum = 0.299f * normR + 0.587f * normG + 0.114f * normB
                if (lum > 200f) {
                    val boost = (lum - 200f) / 55f
                    normR = (normR + (255 - normR) * boost).roundToInt().coerceIn(0, 255)
                    normG = (normG + (255 - normG) * boost).roundToInt().coerceIn(0, 255)
                    normB = (normB + (255 - normB) * boost).roundToInt().coerceIn(0, 255)
                }

                // Boost saturation moderately (~20%)
                val maxC = max(normR, max(normG, normB))
                val minC = min(normR, min(normG, normB))
                if (maxC > minC && maxC > 0) {
                    val gray = (normR + normG + normB) / 3f
                    normR = (gray + (normR - gray) * 1.20f).roundToInt().coerceIn(0, 255)
                    normG = (gray + (normG - gray) * 1.20f).roundToInt().coerceIn(0, 255)
                    normB = (gray + (normB - gray) * 1.20f).roundToInt().coerceIn(0, 255)
                }

                pixels[y * width + x] = (a shl 24) or (normR shl 16) or (normG shl 8) or normB
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun enhanceColorNative(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Gray-World White Balance
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        val totalPixels = width * height

        for (i in 0 until totalPixels) {
            val p = pixels[i]
            sumR += (p shr 16) and 0xFF
            sumG += (p shr 8) and 0xFF
            sumB += p and 0xFF
        }

        val avgR = (sumR.toDouble() / totalPixels).coerceAtLeast(1.0)
        val avgG = (sumG.toDouble() / totalPixels).coerceAtLeast(1.0)
        val avgB = (sumB.toDouble() / totalPixels).coerceAtLeast(1.0)
        val avgGray = (avgR + avgG + avgB) / 3.0

        val scaleR = (avgGray / avgR).toFloat()
        val scaleG = (avgGray / avgG).toFloat()
        val scaleB = (avgGray / avgB).toFloat()

        // 2. Apply White Balance + Contrast Stretch
        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            var r = (((p shr 16) and 0xFF) * scaleR).roundToInt().coerceIn(0, 255)
            var g = (((p shr 8) and 0xFF) * scaleG).roundToInt().coerceIn(0, 255)
            var b = ((p and 0xFF) * scaleB).roundToInt().coerceIn(0, 255)

            // Dynamic background whitening & text boost
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            if (lum > 185) {
                val boost = (lum - 185f) / 70f
                r = (r + (255 - r) * boost * 0.85f).roundToInt().coerceIn(0, 255)
                g = (g + (255 - g) * boost * 0.85f).roundToInt().coerceIn(0, 255)
                b = (b + (255 - b) * boost * 0.85f).roundToInt().coerceIn(0, 255)
            } else if (lum < 90) {
                val darken = lum / 90f
                r = (r * (0.6f + 0.4f * darken)).roundToInt().coerceIn(0, 255)
                g = (g * (0.6f + 0.4f * darken)).roundToInt().coerceIn(0, 255)
                b = (b * (0.6f + 0.4f * darken)).roundToInt().coerceIn(0, 255)
            }

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun adaptiveThresholdNative(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        val integral = LongArray((width + 1) * (height + 1))

        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gVal = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                gray[y * width + x] = gVal
            }
        }

        // Integral image calculation for fast O(1) box window
        for (y in 0 until height) {
            var sum = 0L
            for (x in 0 until width) {
                sum += gray[y * width + x]
                integral[(y + 1) * (width + 1) + (x + 1)] = integral[y * (width + 1) + (x + 1)] + sum
            }
        }

        val s = max(width, height) / 24
        val s2 = s / 2
        val c = 9.0

        for (y in 0 until height) {
            val y1 = max(0, y - s2)
            val y2 = min(height - 1, y + s2)
            for (x in 0 until width) {
                val x1 = max(0, x - s2)
                val x2 = min(width - 1, x + s2)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)] -
                        integral[y1 * (width + 1) + (x2 + 1)] -
                        integral[(y2 + 1) * (width + 1) + x1] +
                        integral[y1 * (width + 1) + x1]

                val threshold = (sum.toDouble() / count) - c
                val pixelVal = if (gray[y * width + x] < threshold) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                pixels[y * width + x] = pixelVal
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun grayscaleNative(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = android.graphics.ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun lightenNative(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = android.graphics.ColorMatrix(
            floatArrayOf(
                1.2f, 0f, 0f, 0f, 25f,
                0f, 1.2f, 0f, 0f, 25f,
                0f, 0f, 1.2f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    // =========================================================================
    // Room DB Attachment Storage Path Integration
    // =========================================================================

    /**
     * Persists the final enhanced document bitmap to the app's internal attachment directory
     * and returns the content/file Uri compatible with Room Entity storage (e.g. StudentEntity.photoUri).
     */
    suspend fun saveAttachment(
        context: Context,
        bitmap: Bitmap,
        studentId: String? = null,
        prefix: String = "doc_scan"
    ): Uri = withContext(Dispatchers.IO) {
        val attachmentsDir = File(context.filesDir, "student_attachments")
        if (!attachmentsDir.exists()) {
            attachmentsDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val identifier = studentId ?: timeStamp
        val fileName = "${prefix}_${identifier}_${System.currentTimeMillis()}.jpg"
        val destFile = File(attachmentsDir, fileName)

        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )
    }
}
