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
import com.example.data.model.AdmitCardMakerState
import com.example.data.model.AdmitCardStudent
import java.io.File
import java.io.FileOutputStream

object AdmitCardNativePdfUtil {

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
    fun printAdmitCardsDirectly(
        context: Context,
        state: AdmitCardMakerState,
        students: List<AdmitCardStudent>,
        bengaliFont: AppBengaliFont = FontPreferences.getSavedFont(context),
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

            val docName = "AdmitCards_${state.examName.replace(" ", "_")}"
            val printAdapter = NativeAdmitCardPrintAdapter(context, state, students, bengaliFont)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
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
        state: AdmitCardMakerState,
        students: List<AdmitCardStudent>,
        bengaliFont: AppBengaliFont = FontPreferences.getSavedFont(context)
    ) {
        if (students.isEmpty()) {
            Toast.makeText(context, "কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = generatePdfDocument(state, students, bengaliFont)
            val cacheDir = File(context.cacheDir, "admit_cards")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val safeName = "AdmitCards_${state.examName.replace(Regex("[^a-zA-Z0-9_\\-\\u0980-\\u09FF]"), "_")}.pdf"
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
                putExtra(Intent.EXTRA_SUBJECT, "প্রবেশপত্র - ${state.examName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "প্রবেশপত্র PDF শেয়ার বা সংরক্ষণ করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generatePdfDocument(
        state: AdmitCardMakerState,
        students: List<AdmitCardStudent>,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        val cpp = state.settings.cardsPerPage.coerceIn(1, 8)
        val studentPages = students.chunked(cpp)
        val sigBitmap = AdmitCardStorage.decodeBase64ToBitmap(state.signature)

        val totalPages = if (studentPages.isEmpty()) 1 else studentPages.size

        for (pageIndex in 0 until totalPages) {
            val pageStudents = if (pageIndex < studentPages.size) studentPages[pageIndex] else emptyList()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create() // Standard A4 (595x842 pt)
            val page = pdfDocument.startPage(pageInfo)

            drawPage(
                canvas = page.canvas,
                pageWidth = 595f,
                pageHeight = 842f,
                students = pageStudents,
                state = state,
                sigBitmap = sigBitmap,
                bengaliFont = bengaliFont
            )

            pdfDocument.finishPage(page)
        }

        return pdfDocument
    }

    /**
     * Draws an exact replica of the user reference image onto the PDF canvas.
     */
    fun drawPage(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        students: List<AdmitCardStudent>,
        state: AdmitCardMakerState,
        sigBitmap: Bitmap?,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ) {
        // Background
        canvas.drawColor(Color.WHITE)

        val cpp = state.settings.cardsPerPage.coerceIn(1, 8)
        val marginH = state.settings.marginLeft * 72f // convert inches to pt (72pt = 1 inch)
        val marginTop = state.settings.marginTop * 72f
        val marginBottom = state.settings.marginBottom * 72f
        val gap = state.settings.vGap * 72f

        val usableWidth = pageWidth - (marginH * 2)
        val usableHeight = pageHeight - marginTop - marginBottom - (gap * (cpp - 1))
        val cardHeight = Math.max(80f, usableHeight / cpp)

        // Base typeface: Settings chosen font, English rendered with Serif (Times New Roman)
        val baseTypeface = if (bengaliFont.pdfFontFamily == "serif" || state.settings.cardFont == "serif") {
            Typeface.SERIF
        } else {
            Typeface.SANS_SERIF
        }

        // Paints
        val dashedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            when (state.settings.frameStyle) {
                "dashed" -> pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                "dotted" -> pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
                "solid" -> pathEffect = null
                "double" -> {
                    strokeWidth = 2.5f
                    pathEffect = null
                }
                "none" -> color = Color.TRANSPARENT
                else -> pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
        }

        val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val tableFillPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
        }

        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        }

        val underlineTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            isUnderlineText = true
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        }

        // Parse school name and address
        val dispSchoolName = state.schoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" }
        val dispAddress = state.schoolAddress.ifBlank { "আলফাডাঙ্গা, ফরিদপুর।" }
        val examName = state.examName.ifBlank { "২য় প্রান্তিক মূল্যায়ন - ২০২৬" }

        students.forEachIndexed { index, student ->
            val cardTop = marginTop + index * (cardHeight + gap)
            val cardBottom = cardTop + cardHeight
            val cardLeft = marginH
            val cardRight = cardLeft + usableWidth

            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

            // Draw outer rounded rectangle
            if (state.settings.frameStyle != "none") {
                canvas.drawRoundRect(cardRect, 10f, 10f, dashedBorderPaint)
            }

            val cardPadding = 10f
            val contentLeft = cardLeft + cardPadding
            val contentTop = cardTop + cardPadding
            val contentRight = cardRight - cardPadding
            val contentBottom = cardBottom - cardPadding
            val contentWidth = contentRight - contentLeft
            val contentHeight = contentBottom - contentTop

            // Divider position
            val leftColWidth = contentWidth * 0.44f
            val rightColLeft = contentLeft + leftColWidth + 10f
            val dividerX = contentLeft + leftColWidth + 5f

            // Draw dashed vertical divider
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(80, 80, 80)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            }
            canvas.drawLine(dividerX, contentTop + 4f, dividerX, contentBottom - 4f, dividerPaint)

            // ==================== LEFT COLUMN ====================
            val leftCenterX = contentLeft + leftColWidth / 2f
            var currY = contentTop + 12f

            // School Name
            boldTextPaint.textSize = 10.5f
            boldTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(dispSchoolName, leftCenterX, currY, boldTextPaint)
            currY += 13f

            // Address
            if (dispAddress.isNotBlank()) {
                textPaint.textSize = 9.5f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(dispAddress, leftCenterX, currY, textPaint)
                currY += 13f
            }

            // Exam Name
            textPaint.textSize = 9.5f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(examName, leftCenterX, currY, textPaint)
            currY += 14f

            // Underlined "প্রবেশপত্র"
            underlineTextPaint.textSize = 11.5f
            underlineTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("প্রবেশপত্র", leftCenterX, currY, underlineTextPaint)
            currY += 26f // 1 row equivalent space after প্রবেশপত্র

            // Student Details (Left Aligned)
            boldTextPaint.textAlign = Paint.Align.LEFT
            textPaint.textAlign = Paint.Align.LEFT
            boldTextPaint.textSize = 10.5f
            textPaint.textSize = 10.5f

            val infoLeft = contentLeft + 6f
            val valOffset = 42f

            // Name
            canvas.drawText("নাম :", infoLeft, currY, boldTextPaint)
            canvas.drawText(student.name, infoLeft + valOffset, currY, textPaint)
            currY += 14f

            // Class
            canvas.drawText("শ্রেণি :", infoLeft, currY, boldTextPaint)
            canvas.drawText(student.studentClass, infoLeft + valOffset, currY, textPaint)
            currY += 14f

            // Roll
            canvas.drawText("রোল :", infoLeft, currY, boldTextPaint)
            canvas.drawText(BanglaUtils.toBanglaDigits(student.rollNumber), infoLeft + valOffset, currY, textPaint)

            // Signature Section (Bottom Right of Left Column)
            val sigScale = when (state.settings.sigSize) {
                "1" -> 18f
                "2" -> 24f
                "4" -> 38f
                "5" -> 48f
                else -> 30f // "3"
            }

            val sigBottomY = contentBottom - 4f
            boldTextPaint.textSize = 8.5f
            boldTextPaint.textAlign = Paint.Align.RIGHT
            val sigRightX = contentLeft + leftColWidth
            val sigLineWidth = 95f

            // Line above "প্রধান শিক্ষকের স্বাক্ষর"
            val sigLinePaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            canvas.drawLine(sigRightX - sigLineWidth, sigBottomY - 12f, sigRightX, sigBottomY - 12f, sigLinePaint)

            // Signature label text
            canvas.drawText("প্রধান শিক্ষকের স্বাক্ষর", sigRightX, sigBottomY, boldTextPaint)

            if (sigBitmap != null) {
                val targetH = sigScale
                val aspect = sigBitmap.width.toFloat() / Math.max(1, sigBitmap.height)
                val targetW = targetH * aspect
                val sigRect = RectF(sigRightX - targetW, sigBottomY - 14f - targetH, sigRightX, sigBottomY - 14f)
                canvas.drawBitmap(sigBitmap, null, sigRect, null)
            }

            // ==================== RIGHT COLUMN (TABLE) ====================
            val routine = state.classRoutines[student.studentClass]?.ifEmpty { null }
                ?: state.classRoutines[AdmitCardStorage.BASE_KEY]
                ?: emptyList()
            val examTime = state.classTimes[student.studentClass]
                ?: state.classTimes[AdmitCardStorage.BASE_KEY]
                ?: state.defaultTime.ifBlank { "১০:০০-১১:০০" }

            val tableLeft = rightColLeft
            val tableRight = contentRight
            val tableWidth = tableRight - tableLeft
            val tableTop = contentTop + 4f

            // Total row items in routine (handling multiple subjects per day)
            data class TableRowItem(val dateText: String, val dayText: String, val subjectText: String)
            val tableItems = mutableListOf<TableRowItem>()

            if (routine.isEmpty()) {
                tableItems.add(TableRowItem("—", "—", "সকল বিষয়"))
            } else {
                routine.forEach { d ->
                    val dateFormatted = AdmitCardStorage.formatDateToBangla(d.date).ifBlank { d.date.ifBlank { "—" } }
                    val dayName = if (d.day.isNotBlank()) d.day else AdmitCardStorage.getDayNameFromDate(d.date).ifBlank { "—" }
                    val activeSubs = d.subjects.filter { it.isNotBlank() }
                    if (activeSubs.isEmpty()) {
                        tableItems.add(TableRowItem(dateFormatted, dayName, "—"))
                    } else {
                        activeSubs.forEachIndexed { subIndex, sub ->
                            if (subIndex == 0) {
                                tableItems.add(TableRowItem(dateFormatted, dayName, sub))
                            } else {
                                tableItems.add(TableRowItem("", "", sub))
                            }
                        }
                    }
                }
            }

            val numDataRows = tableItems.size
            val headerTopH = 15f
            val headerSubH = 14f
            val headerTotalH = headerTopH + (headerSubH * 2) // top row + 2 sub-header rows
            val remainingH = (contentBottom - tableTop - headerTotalH).coerceAtLeast(numDataRows * 13f)
            val rowHeight = Math.min(18f, Math.max(12f, remainingH / Math.max(1, numDataRows)))
            val tableBottom = tableTop + headerTotalH + (numDataRows * rowHeight)

            // Column Widths
            val col1W = tableWidth * 0.28f // তারিখ
            val col2W = tableWidth * 0.22f // বার
            val col3W = tableWidth * 0.50f // সময় / বিষয়

            val col1Right = tableLeft + col1W
            val col2Right = col1Right + col2W

            // 1. Table Top Header: "<<Exam Name>> এর রুটিন"
            val topHeaderRect = RectF(tableLeft, tableTop, tableRight, tableTop + headerTopH)
            canvas.drawRect(topHeaderRect, tableFillPaint)
            canvas.drawRect(topHeaderRect, solidLinePaint)

            boldTextPaint.textSize = 9f
            boldTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("$examName এর রুটিন", tableLeft + tableWidth / 2f, tableTop + 10.5f, boldTextPaint)

            // 2. Header Row 2 & 3:
            val h2Top = tableTop + headerTopH
            val h3Top = h2Top + headerSubH
            val hBottom = h3Top + headerSubH

            // তারিখ Header (Spans rows 2 & 3)
            val dRect = RectF(tableLeft, h2Top, col1Right, hBottom)
            canvas.drawRect(dRect, tableFillPaint)
            canvas.drawRect(dRect, solidLinePaint)
            boldTextPaint.textSize = 8.5f
            canvas.drawText("তারিখ", tableLeft + col1W / 2f, h2Top + headerSubH + 3f, boldTextPaint)

            // বার Header (Spans rows 2 & 3)
            val bRect = RectF(col1Right, h2Top, col2Right, hBottom)
            canvas.drawRect(bRect, tableFillPaint)
            canvas.drawRect(bRect, solidLinePaint)
            canvas.drawText("বার", col1Right + col2W / 2f, h2Top + headerSubH + 3f, boldTextPaint)

            // সময় Header (Row 2 Col 3)
            val tRect = RectF(col2Right, h2Top, tableRight, h3Top)
            canvas.drawRect(tRect, tableFillPaint)
            canvas.drawRect(tRect, solidLinePaint)
            textPaint.textSize = 8f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(examTime, col2Right + col3W / 2f, h2Top + 9.5f, textPaint)

            // বিষয় Header (Row 3 Col 3)
            val sRect = RectF(col2Right, h3Top, tableRight, hBottom)
            canvas.drawRect(sRect, tableFillPaint)
            canvas.drawRect(sRect, solidLinePaint)
            boldTextPaint.textSize = 8.5f
            canvas.drawText("বিষয়", col2Right + col3W / 2f, h3Top + 9.5f, boldTextPaint)

            // 3. Data Rows
            textPaint.textSize = 8.5f
            textPaint.textAlign = Paint.Align.CENTER

            var rowY = hBottom
            tableItems.forEach { item ->
                val rBottom = rowY + rowHeight

                // Col 1: Date
                val r1 = RectF(tableLeft, rowY, col1Right, rBottom)
                canvas.drawRect(r1, solidLinePaint)
                if (item.dateText.isNotBlank()) {
                    canvas.drawText(item.dateText, tableLeft + col1W / 2f, rowY + rowHeight * 0.7f, textPaint)
                }

                // Col 2: Day
                val r2 = RectF(col1Right, rowY, col2Right, rBottom)
                canvas.drawRect(r2, solidLinePaint)
                if (item.dayText.isNotBlank()) {
                    canvas.drawText(item.dayText, col1Right + col2W / 2f, rowY + rowHeight * 0.7f, textPaint)
                }

                // Col 3: Subject
                val r3 = RectF(col2Right, rowY, tableRight, rBottom)
                canvas.drawRect(r3, solidLinePaint)
                canvas.drawText(item.subjectText, col2Right + col3W / 2f, rowY + rowHeight * 0.7f, textPaint)

                rowY += rowHeight
            }
        }
    }
}

/**
 * Custom PrintDocumentAdapter for native Android PrintManager
 */
class NativeAdmitCardPrintAdapter(
    private val context: Context,
    private val state: AdmitCardMakerState,
    private val students: List<AdmitCardStudent>,
    private val bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
) : PrintDocumentAdapter() {

    private var pageCount = 0

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

        val cpp = state.settings.cardsPerPage.coerceIn(1, 8)
        pageCount = (students.size + cpp - 1) / cpp
        if (pageCount == 0) pageCount = 1

        val info = PrintDocumentInfo.Builder("AdmitCards_${state.examName}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        val pdfDocument = PdfDocument()
        try {
            val cpp = state.settings.cardsPerPage.coerceIn(1, 8)
            val studentPages = students.chunked(cpp)
            val sigBitmap = AdmitCardStorage.decodeBase64ToBitmap(state.signature)

            for (pageIndex in 0 until pageCount) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    pdfDocument.close()
                    return
                }

                val pageStudents = if (pageIndex < studentPages.size) studentPages[pageIndex] else emptyList()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create() // Standard A4 (595x842 pt)
                val page = pdfDocument.startPage(pageInfo)

                AdmitCardNativePdfUtil.drawPage(
                    canvas = page.canvas,
                    pageWidth = 595f,
                    pageHeight = 842f,
                    students = pageStudents,
                    state = state,
                    sigBitmap = sigBitmap,
                    bengaliFont = bengaliFont
                )

                pdfDocument.finishPage(page)
            }

            destination?.let {
                FileOutputStream(it.fileDescriptor).use { output ->
                    pdfDocument.writeTo(output)
                }
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed(e.message)
        } finally {
            pdfDocument.close()
        }
    }
}
