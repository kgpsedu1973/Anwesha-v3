package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.AdmitCardStudent
import com.example.data.model.SeatPlanMakerState
import java.io.File
import java.io.FileOutputStream

object SeatPlanNativePdfUtil {

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Native Android Printing without WebView.
     * Uses Android's native PrintManager and custom PrintDocumentAdapter.
     */
    fun printSeatPlansDirectly(
        context: Context,
        state: SeatPlanMakerState,
        students: List<AdmitCardStudent>,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (students.isEmpty()) {
            val msg = "প্রিন্ট করার জন্য কোনো শিক্ষার্থী নির্বাচিত নেই"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onError?.invoke(msg)
            return
        }

        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                val msg = "এই ডিভাইসে প্রিন্টিং সার্ভিস উপলব্ধ নয়।"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onError?.invoke(msg)
                return
            }

            val docName = "SeatPlan_${state.examName.replace(" ", "_")}"
            val printAdapter = NativeSeatPlanPrintAdapter(context, state, students, bengaliFont)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(
                    when (state.page.pageSize) {
                        "Letter" -> PrintAttributes.MediaSize.NA_LETTER
                        "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
                        else -> PrintAttributes.MediaSize.ISO_A4
                    }
                )
                .setResolution(PrintAttributes.Resolution("pdf_print", "Standard PDF", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(docName, printAdapter, printAttributes)
            onSuccess?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "প্রিন্ট ডায়লগ খুলতে ত্রুটি: ${e.localizedMessage ?: e.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onError?.invoke(msg)
        }
    }

    /**
     * Creates a PDF file and launches standard Android file share/view.
     */
    fun exportAndSharePdf(
        context: Context,
        state: SeatPlanMakerState,
        students: List<AdmitCardStudent>,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ) {
        if (students.isEmpty()) {
            Toast.makeText(context, "কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = generatePdfDocument(state, students, bengaliFont)
            val cacheDir = File(context.cacheDir, "seat_plans")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val safeName = "SeatPlan_${state.examName.replace(Regex("[^a-zA-Z0-9_\\-\\u0980-\\u09FF]"), "_")}.pdf"
            val pdfFile = File(cacheDir, safeName)

            FileOutputStream(pdfFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "সিট প্ল্যান - ${state.examName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "সিট প্ল্যান PDF শেয়ার বা সংরক্ষণ করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getPageDimensionsPt(pageSize: String, orientation: String): Pair<Float, Float> {
        val (w, h) = when (pageSize) {
            "Letter" -> Pair(612f, 792f) // 8.5" x 11"
            "Legal" -> Pair(612f, 1008f) // 8.5" x 14"
            else -> Pair(595.28f, 841.89f) // A4 (8.27" x 11.69")
        }
        return if (orientation == "landscape") Pair(h, w) else Pair(w, h)
    }

    fun generatePdfDocument(
        state: SeatPlanMakerState,
        students: List<AdmitCardStudent>,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        val cpp = state.page.totalCardsPerPage
        val studentPages = students.chunked(cpp)
        val (pageW, pageH) = getPageDimensionsPt(state.page.pageSize, state.page.orientation)

        val totalPages = if (studentPages.isEmpty()) 1 else studentPages.size

        for (pageIndex in 0 until totalPages) {
            val pageStudents = if (pageIndex < studentPages.size) studentPages[pageIndex] else emptyList()
            val pageInfo = PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)

            drawPage(
                canvas = page.canvas,
                pageWidth = pageW,
                pageHeight = pageH,
                students = pageStudents,
                startIndex = pageIndex * cpp,
                state = state,
                bengaliFont = bengaliFont
            )

            pdfDocument.finishPage(page)
        }

        return pdfDocument
    }

    /**
     * Draws an exact replica of the seat plan onto canvas.
     * Dimensions are strictly calculated based on Inches (1 Inch = 72 Pt).
     */
    fun drawPage(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        students: List<AdmitCardStudent>,
        startIndex: Int,
        state: SeatPlanMakerState,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ) {
        // Page background
        canvas.drawColor(Color.WHITE)

        val p = state.page
        val f = state.fields

        val cols = p.columns.coerceIn(1, 4)
        val rows = p.rows.coerceIn(1, 12)

        // 1 Inch = 72 Points (Standard PDF Points)
        val ml = p.marginLeftInch * 72f
        val mr = p.marginRightInch * 72f
        val mt = p.marginTopInch * 72f
        val mb = p.marginBottomInch * 72f

        val hGap = p.horizontalGapInch * 72f
        val vGap = p.verticalGapInch * 72f

        val gridW = pageWidth - ml - mr
        val gridH = pageHeight - mt - mb

        val totalHGap = (cols - 1) * hGap
        val totalVGap = (rows - 1) * vGap

        val cardW = ((gridW - totalHGap) / cols).coerceAtLeast(40f)
        val cardH = ((gridH - totalVGap) / rows).coerceAtLeast(30f)

        // Font: Strictly Noto Serif Bengali (Typeface.SERIF)
        val baseTypeface = Typeface.SERIF

        // Paints
        val cardFillPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (f.cardBorderWidthDp * 0.75f).coerceIn(0.5f, 4f)
            when (f.cardBorderStyle) {
                "dashed" -> pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                "dotted" -> pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
                "solid" -> pathEffect = null
                "double" -> {
                    strokeWidth = 2.2f
                    pathEffect = null
                }
                "none" -> color = Color.TRANSPARENT
                else -> pathEffect = null
            }
        }

        val cuttingLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            try {
                color = Color.parseColor(p.cuttingLineColorHex)
            } catch (e: Exception) {
                color = Color.rgb(148, 163, 184)
            }
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
            when (p.cuttingLineStyle) {
                "dotted" -> pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
                "dashed" -> pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
                "solid" -> pathEffect = null
                "none" -> color = Color.TRANSPARENT
                else -> pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
            }
        }

        // Draw Cutting Lines between columns
        if (p.cuttingLineStyle != "none") {
            for (c in 1 until cols) {
                val cutX = ml + c * cardW + (c - 0.5f) * hGap
                canvas.drawLine(cutX, 0f, cutX, pageHeight, cuttingLinePaint)
            }
            // Draw Cutting Lines between rows
            for (r in 1 until rows) {
                val cutY = mt + r * cardH + (r - 0.5f) * vGap
                canvas.drawLine(0f, cutY, pageWidth, cutY, cuttingLinePaint)
            }
        }

        // Adaptive scaling reference to preserve aesthetic layout if cards are very small/large
        val cardScale = (cardH / 105f).coerceIn(0.7f, 1.3f)

        val schoolNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = f.schoolNameFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isSchoolNameBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val addressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textAlign = Paint.Align.CENTER
            textSize = f.addressFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isAddressBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val examPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = f.examNameFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isExamNameBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = f.titleFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isTitleBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val studentNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = f.studentNameFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isStudentNameBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val classPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            textSize = f.classFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isClassBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val rollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
            textSize = f.rollFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isRollBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val roomBenchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
            textSize = f.roomFontSizePt * cardScale
            typeface = Typeface.create(baseTypeface, if (f.isRoomBold) Typeface.BOLD else Typeface.NORMAL)
        }

        val cornerRadiusPt = (f.cardCornerRadiusDp * 0.75f).coerceIn(0f, 24f)

        // Draw each card
        for (i in 0 until (cols * rows)) {
            val student = if (i < students.size) students[i] else null
            val colIdx = i % cols
            val rowIdx = i / cols

            val cardLeft = ml + colIdx * (cardW + hGap)
            val cardTop = mt + rowIdx * (cardH + vGap)
            val cardRight = cardLeft + cardW
            val cardBottom = cardTop + cardH

            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

            // Draw Card background & border
            if (cornerRadiusPt > 0) {
                canvas.drawRoundRect(cardRect, cornerRadiusPt, cornerRadiusPt, cardFillPaint)
                if (f.cardBorderStyle != "none") {
                    canvas.drawRoundRect(cardRect, cornerRadiusPt, cornerRadiusPt, cardStrokePaint)
                    if (f.cardBorderStyle == "double") {
                        val innerRect = RectF(cardRect.left + 2f, cardRect.top + 2f, cardRect.right - 2f, cardRect.bottom - 2f)
                        canvas.drawRoundRect(innerRect, cornerRadiusPt - 1.5f, cornerRadiusPt - 1.5f, cardStrokePaint)
                    }
                }
            } else {
                canvas.drawRect(cardRect, cardFillPaint)
                if (f.cardBorderStyle != "none") {
                    canvas.drawRect(cardRect, cardStrokePaint)
                    if (f.cardBorderStyle == "double") {
                        val innerRect = RectF(cardRect.left + 2f, cardRect.top + 2f, cardRect.right - 2f, cardRect.bottom - 2f)
                        canvas.drawRect(innerRect, cardStrokePaint)
                    }
                }
            }

            if (student == null) {
                continue
            }

            val centerX = cardRect.centerX()
            var currentY = cardTop + 13f * cardScale

            // 1. School Name (Centered)
            if (f.showSchoolName) {
                val schoolName = state.schoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" }
                // Ensure text fits width
                var measuredSize = schoolNamePaint.textSize
                while (schoolNamePaint.measureText(schoolName) > (cardW - 20f) && measuredSize > 7f) {
                    measuredSize -= 0.5f
                    schoolNamePaint.textSize = measuredSize
                }
                canvas.drawText(schoolName, centerX, currentY, schoolNamePaint)
                currentY += (schoolNamePaint.textSize * 1.15f)
            }

            // 2. Address / Subheader (Centered)
            if (f.showSchoolAddress && state.schoolAddress.isNotBlank()) {
                val address = state.schoolAddress
                canvas.drawText(address, centerX, currentY, addressPaint)
                currentY += (addressPaint.textSize * 1.2f)
            }

            // 3. Exam Name (Centered)
            if (f.showExamName) {
                val rawExam = state.examName.ifBlank { "২য় প্রান্তিক মূল্যায়ন ২০২৬" }
                val examStr = if (f.convertBanglaDigits) BanglaUtils.toBanglaDigits(rawExam) else rawExam
                canvas.drawText(examStr, centerX, currentY, examPaint)
                currentY += (examPaint.textSize * 1.2f)
            }

            // 4. Seat Plan Title (e.g. আসন বিন্যাস) (Centered)
            if (f.showSeatPlanTitle && f.seatPlanTitleText.isNotBlank()) {
                canvas.drawText(f.seatPlanTitleText, centerX, currentY, titlePaint)
                currentY += (titlePaint.textSize * 1.15f)
            }

            // Bottom Section: Student Name, Class, Roll Number
            val padX = 14f * cardScale
            val leftX = cardLeft + padX
            val rightX = cardRight - padX

            // Calculate bottom positions
            val lineSpacing = 14.5f * cardScale
            val bottomMargin = 12f * cardScale

            val row2Y = cardBottom - bottomMargin
            val row1Y = row2Y - lineSpacing

            // Student Name (Left)
            if (f.showStudentName) {
                val nameLabel = "নাম: "
                val nameValue = student.name
                val labelWidth = studentNamePaint.measureText(nameLabel)
                canvas.drawText(nameLabel, leftX, row1Y, studentNamePaint)
                canvas.drawText(nameValue, leftX + labelWidth, row1Y, studentNamePaint)
            }

            // Student Class (Left Bottom)
            if (f.showStudentClass) {
                val classLabel = "শ্রেণি: "
                val classValue = SeatPlanStorage.formatClassDisplay(student.studentClass, f.classFormat)
                val labelWidth = classPaint.measureText(classLabel)
                canvas.drawText(classLabel, leftX, row2Y, classPaint)
                canvas.drawText(classValue, leftX + labelWidth, row2Y, classPaint)
            }

            // Roll Number (Right Bottom)
            if (f.showRollNumber) {
                val rawRoll = student.rollNumber
                val rollFormatted = if (f.convertBanglaDigits) BanglaUtils.toBanglaDigits(rawRoll) else rawRoll
                val rollText = "রোল: $rollFormatted"
                canvas.drawText(rollText, rightX, row2Y, rollPaint)
            }

            // Optional Room No / Bench No
            if (f.showRoomNumber && f.roomNumberText.isNotBlank()) {
                val roomText = if (f.convertBanglaDigits) BanglaUtils.toBanglaDigits(f.roomNumberText) else f.roomNumberText
                val fullRoom = "কক্ষ: $roomText"
                canvas.drawText(fullRoom, rightX, row1Y, roomBenchPaint)
            } else if (f.showBenchNumber) {
                val benchNum = if (state.scope.autoNumberBenches) {
                    val benchVal = (startIndex + i) / 2 + state.scope.startBenchNumber
                    if (f.convertBanglaDigits) BanglaUtils.toBanglaDigits(benchVal) else benchVal.toString()
                } else ""
                val benchText = "${f.benchPrefix}$benchNum"
                canvas.drawText(benchText, rightX, row1Y, roomBenchPaint)
            }
        }
    }
}

/**
 * Custom PrintDocumentAdapter for Native Android Printing
 */
class NativeSeatPlanPrintAdapter(
    private val context: Context,
    private val state: SeatPlanMakerState,
    private val students: List<AdmitCardStudent>,
    private val bengaliFont: AppBengaliFont
) : PrintDocumentAdapter() {

    private var pdfDocument: PdfDocument? = null
    private var totalPages = 0

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val cpp = state.page.totalCardsPerPage
        totalPages = if (students.isEmpty()) 1 else ((students.size + cpp - 1) / cpp)

        val info = PrintDocumentInfo.Builder("SeatPlan_${state.examName}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(totalPages)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        try {
            pdfDocument = SeatPlanNativePdfUtil.generatePdfDocument(state, students, bengaliFont)
            destination?.let {
                FileOutputStream(it.fileDescriptor).use { outStream ->
                    pdfDocument?.writeTo(outStream)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            pdfDocument?.close()
            pdfDocument = null
        }
    }
}
