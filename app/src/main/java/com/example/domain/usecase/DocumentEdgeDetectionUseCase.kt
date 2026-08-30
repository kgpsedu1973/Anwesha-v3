package com.example.domain.usecase

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Clean Architecture UseCase for Document Edge Detection, Perspective Correction & Deskewing.
 *
 * Designed to provide automatic corner straightening for gallery imports & non-MLKit camera captures:
 * 1. Contour-based 4-point Document Edge Detection (Canny -> Dilate/Close -> findContours -> approxPolyDP)
 * 2. Perspective Transformation (getPerspectiveTransform + warpPerspective)
 * 3. Graceful Fallback to Deskew-only (Hough line / minAreaRect orientation) when no 4-point quadrilateral exists.
 */
class DocumentEdgeDetectionUseCase {

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
     * Attempts to find document boundary and perform perspective transform;
     * falls back gracefully to deskewing if 4 corners are not detected.
     */
    suspend fun straightenOrDeskew(
        sourceBitmap: Bitmap,
        forceDeskewOnly: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!isOpenCvReady) {
            return@withContext sourceBitmap
        }

        try {
            if (!forceDeskewOnly) {
                val perspectiveCorrected = detectAndWarpPerspectiveOpenCV(sourceBitmap)
                if (perspectiveCorrected != null) {
                    return@withContext perspectiveCorrected
                }
            }

            // Fallback to deskew-only
            val deskewed = deskewOpenCV(sourceBitmap)
            return@withContext deskewed ?: sourceBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext sourceBitmap
        }
    }

    /**
     * Finds the largest 4-point quadrilateral contour and warps perspective.
     * Returns null if no reliable 4-corner document quad is found.
     */
    private fun detectAndWarpPerspectiveOpenCV(srcBitmap: Bitmap): Bitmap? {
        val origW = srcBitmap.width
        val origH = srcBitmap.height

        // Downscale for robust and fast contour detection
        val maxDim = max(origW, origH)
        val scale = if (maxDim > 1000) 1000f / maxDim else 1f
        val procW = (origW * scale).toInt()
        val procH = (origH * scale).toInt()

        val origMat = Mat()
        Utils.bitmapToMat(srcBitmap, origMat)

        val smallMat = Mat()
        Imgproc.resize(origMat, smallMat, Size(procW.toDouble(), procH.toDouble()))

        val grayMat = Mat()
        Imgproc.cvtColor(smallMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(grayMat, blurred, Size(5.0, 5.0), 0.0)

        // Canny edge detection
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 35.0, 120.0)

        // Morphological close to join disconnected edge lines
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        // Sort contours by area descending
        contours.sortByDescending { Imgproc.contourArea(it) }

        val imageArea = procW.toDouble() * procH.toDouble()
        var foundCorners: Array<Point>? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            // Contour must occupy at least 15% of the total image area
            if (area < imageArea * 0.15) {
                break
            }

            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx2f = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx2f, 0.02 * peri, true)

            if (approx2f.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx2f.toArray()))) {
                val points = approx2f.toArray()
                // Map points back to original image scale
                val invScale = 1.0 / scale
                val scaledPoints = points.map { pt ->
                    Point(pt.x * invScale, pt.y * invScale)
                }.toTypedArray()

                foundCorners = orderPoints(scaledPoints)
                contour2f.release()
                approx2f.release()
                break
            }
            contour2f.release()
            approx2f.release()
        }

        // Cleanup detection mats
        smallMat.release()
        grayMat.release()
        blurred.release()
        edges.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { it.release() }

        if (foundCorners == null) {
            origMat.release()
            return null
        }

        // Compute destination dimensions
        val tl = foundCorners[0]
        val tr = foundCorners[1]
        val br = foundCorners[2]
        val bl = foundCorners[3]

        val widthA = hypot(br.x - bl.x, br.y - bl.y)
        val widthB = hypot(tr.x - tl.x, tr.y - tl.y)
        val maxTargetW = max(widthA, widthB).toInt()

        val heightA = hypot(tr.x - br.x, tr.y - br.y)
        val heightB = hypot(tl.x - bl.x, bl.y - tl.y)
        val maxTargetH = max(heightA, heightB).toInt()

        if (maxTargetW < 100 || maxTargetH < 100) {
            origMat.release()
            return null
        }

        val srcPoints = MatOfPoint2f(tl, tr, br, bl)
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxTargetW.toDouble() - 1, 0.0),
            Point(maxTargetW.toDouble() - 1, maxTargetH.toDouble() - 1),
            Point(0.0, maxTargetH.toDouble() - 1)
        )

        val transformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val warpedMat = Mat()
        Imgproc.warpPerspective(
            origMat,
            warpedMat,
            transformMatrix,
            Size(maxTargetW.toDouble(), maxTargetH.toDouble()),
            Imgproc.INTER_LINEAR
        )

        val outBitmap = Bitmap.createBitmap(maxTargetW, maxTargetH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warpedMat, outBitmap)

        // Memory cleanup
        origMat.release()
        srcPoints.release()
        dstPoints.release()
        transformMatrix.release()
        warpedMat.release()

        return outBitmap
    }

    /**
     * Orders 4 points in top-left, top-right, bottom-right, bottom-left sequence.
     */
    private fun orderPoints(pts: Array<Point>): Array<Point> {
        // sum = x + y -> TL has min sum, BR has max sum
        // diff = y - x -> TR has min diff, BL has max diff
        var tl = pts[0]
        var br = pts[0]
        var minSum = pts[0].x + pts[0].y
        var maxSum = pts[0].x + pts[0].y

        var tr = pts[0]
        var bl = pts[0]
        var minDiff = pts[0].y - pts[0].x
        var maxDiff = pts[0].y - pts[0].x

        for (pt in pts) {
            val sum = pt.x + pt.y
            if (sum < minSum) {
                minSum = sum
                tl = pt
            }
            if (sum > maxSum) {
                maxSum = sum
                br = pt
            }

            val diff = pt.y - pt.x
            if (diff < minDiff) {
                minDiff = diff
                tr = pt
            }
            if (diff > maxDiff) {
                maxDiff = diff
                bl = pt
            }
        }

        return arrayOf(tl, tr, br, bl)
    }

    /**
     * Deskews image based on dominant text line orientation using Hough Line Transform.
     */
    private fun deskewOpenCV(srcBitmap: Bitmap): Bitmap? {
        val origW = srcBitmap.width
        val origH = srcBitmap.height

        val maxDim = max(origW, origH)
        val scale = if (maxDim > 800) 800f / maxDim else 1f
        val procW = (origW * scale).toInt()
        val procH = (origH * scale).toInt()

        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        val smallMat = Mat()
        Imgproc.resize(srcMat, smallMat, Size(procW.toDouble(), procH.toDouble()))

        val grayMat = Mat()
        Imgproc.cvtColor(smallMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0, 3, false)

        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180.0, 70, procW * 0.25, 20.0)

        var angleSum = 0.0
        var count = 0

        for (i in 0 until lines.rows()) {
            val vec = lines.get(i, 0) ?: continue
            val x1 = vec[0]
            val y1 = vec[1]
            val x2 = vec[2]
            val y2 = vec[3]

            val dx = x2 - x1
            val dy = y2 - y1
            val angleDeg = Math.toDegrees(atan2(dy, dx))

            // Keep nearly horizontal lines (-40 to +40 degrees)
            if (abs(angleDeg) in 0.5..40.0) {
                angleSum += angleDeg
                count++
            }
        }

        // Release intermediate mats
        srcMat.release()
        smallMat.release()
        grayMat.release()
        edges.release()
        lines.release()

        if (count == 0) {
            return null
        }

        val medianAngle = angleSum / count
        if (abs(medianAngle) < 0.6) {
            return null // Near-perfect alignment, no rotation needed
        }

        // Rotate original bitmap by -medianAngle
        val matrix = Matrix().apply {
            postRotate(-medianAngle.toFloat())
        }

        return Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.width, srcBitmap.height, matrix, true)
    }
}
