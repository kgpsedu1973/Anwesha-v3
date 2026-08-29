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
import com.example.data.model.CertificateMakerState
import com.example.data.model.CertificateStudent
import java.io.File
import java.io.FileOutputStream

object CertificateNativePdfUtil {

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Return page dimensions in PDF points (72 pt = 1 Inch)
     * Legal Landscape = 14" x 8.5" = 1008 pt x 612 pt
     */
    fun getPageDimensionsPt(pageSize: String, orientation: String): Pair<Float, Float> {
        val (w, h) = when (pageSize) {
            "Legal" -> Pair(612f, 1008f) // 8.5" x 14"
            "Letter" -> Pair(612f, 792f) // 8.5" x 11"
            else -> Pair(595.28f, 841.89f) // A4
        }
        return if (orientation == "landscape") Pair(h, w) else Pair(w, h)
    }

    /**
     * Native Android Printing directly via system PrintManager
     */
    fun printCertificatesDirectly(
        context: Context,
        state: CertificateMakerState,
        students: List<CertificateStudent>,
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

            val docName = "Certificate_${state.certificateTitle.replace(" ", "_")}"
            val printAdapter = NativeCertificatePrintAdapter(context, state, students, bengaliFont)
            val (pageW, pageH) = getPageDimensionsPt(state.pageSize, state.orientation)

            val mediaSize = if (state.orientation == "landscape") {
                when (state.pageSize) {
                    "Legal" -> PrintAttributes.MediaSize.NA_LEGAL.asLandscape()
                    "Letter" -> PrintAttributes.MediaSize.NA_LETTER.asLandscape()
                    else -> PrintAttributes.MediaSize.ISO_A4.asLandscape()
                }
            } else {
                when (state.pageSize) {
                    "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
                    "Letter" -> PrintAttributes.MediaSize.NA_LETTER
                    else -> PrintAttributes.MediaSize.ISO_A4
                }
            }

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
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
     * Generates a PDF file and launches Android file share intent
     */
    fun exportAndSharePdf(
        context: Context,
        state: CertificateMakerState,
        students: List<CertificateStudent>,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ) {
        if (students.isEmpty()) {
            Toast.makeText(context, "কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = generatePdfDocument(state, students, bengaliFont)
            val cacheDir = File(context.cacheDir, "certificates")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val safeTitle = state.certificateTitle.replace(Regex("[^a-zA-Z0-9_\\-\\u0980-\\u09FF]"), "_")
            val pdfFile = File(cacheDir, "${safeTitle}_${System.currentTimeMillis()}.pdf")

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
                putExtra(Intent.EXTRA_SUBJECT, "${state.certificateTitle} - ${state.schoolName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "${state.certificateTitle} PDF শেয়ার বা সংরক্ষণ করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generate complete PdfDocument
     */
    fun generatePdfDocument(
        state: CertificateMakerState,
        students: List<CertificateStudent>,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        val (pageW, pageH) = getPageDimensionsPt(state.pageSize, state.orientation)
        val sigBitmap = CertificateStorage.decodeBase64ToBitmap(state.headTeacherSignatureBase64)

        val totalPages = if (students.isEmpty()) 1 else students.size

        for (pageIndex in 0 until totalPages) {
            val student = if (pageIndex < students.size) students[pageIndex] else getDummyStudent()
            val serial = state.computeSerialForStudent(student, pageIndex)

            val pageInfo = PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)

            drawSingleCertificatePage(
                canvas = page.canvas,
                pageWidth = pageW,
                pageHeight = pageH,
                student = student,
                serialNumber = serial,
                state = state,
                sigBitmap = sigBitmap,
                bengaliFont = bengaliFont
            )

            pdfDocument.finishPage(page)
        }

        return pdfDocument
    }

    /**
     * Render high-resolution preview bitmap for Compose UI
     */
    fun renderPreviewBitmap(
        state: CertificateMakerState,
        student: CertificateStudent,
        serialNumber: String,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI,
        previewScale: Float = 1.3f
    ): Bitmap {
        val (pageW, pageH) = getPageDimensionsPt(state.pageSize, state.orientation)
        val bmpW = (pageW * previewScale).toInt().coerceAtLeast(400)
        val bmpH = (pageH * previewScale).toInt().coerceAtLeast(300)

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(previewScale, previewScale)

        val sigBitmap = CertificateStorage.decodeBase64ToBitmap(state.headTeacherSignatureBase64)

        drawSingleCertificatePage(
            canvas = canvas,
            pageWidth = pageW,
            pageHeight = pageH,
            student = student,
            serialNumber = serialNumber,
            state = state,
            sigBitmap = sigBitmap,
            bengaliFont = bengaliFont
        )

        return bitmap
    }

    /**
     * Core Drawing Method: Renders exact layout matching user reference
     */
    fun drawSingleCertificatePage(
        canvas: Canvas,
        pageWidth: Float,
        pageHeight: Float,
        student: CertificateStudent,
        serialNumber: String,
        state: CertificateMakerState,
        sigBitmap: Bitmap?,
        bengaliFont: AppBengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
    ) {
        // 1. Page Background (Pure White)
        canvas.drawColor(Color.WHITE)

        val ml = state.marginLeftInch * 72f
        val mr = state.marginRightInch * 72f
        val mt = state.marginTopInch * 72f
        val mb = state.marginBottomInch * 72f

        val usableW = pageWidth - ml - mr
        val usableH = pageHeight - mt - mb

        val hasCounterfoil = state.showCounterfoil

        // Division of width:
        // If Counterfoil enabled: Left Stub takes ~27% of usable width, Right Certificate takes remaining ~73%
        val stubW = if (hasCounterfoil) (usableW * 0.27f).coerceIn(180f, 290f) else 0f
        val certLeft = if (hasCounterfoil) ml + stubW + 12f else ml
        val certW = if (hasCounterfoil) usableW - stubW - 12f else usableW

        val baseTypeface = Typeface.SERIF

        // Formatted Date
        val displayDate = if (state.issueDate.isNotBlank()) {
            CertificateStorage.formatDateToBanglaDisplay(state.issueDate)
        } else {
            CertificateStorage.formatDateToBanglaDisplay(CertificateStorage.getCurrentIsoDate())
        }

        // Formatted DOB
        val displayDob = if (student.birthDate.isNotBlank()) {
            if (student.birthDate.contains("-")) {
                CertificateStorage.formatDateToBanglaDisplay(student.birthDate)
            } else {
                student.birthDate
            }
        } else {
            "—"
        }

        val displayRoll = BanglaUtils.toBanglaDigits(student.rollNumber)
        val displaySession = BanglaUtils.toBanglaDigits(state.sessionYear.ifBlank { student.academicYear })

        // ==========================================
        // 2. DRAW LEFT COUNTERFOIL (STUB / অফিস কপি)
        // ==========================================
        if (hasCounterfoil) {
            val stubRect = RectF(ml, mt, ml + stubW, mt + usableH)

            // Header Box for Stub
            val stubHeaderH = 76f
            val headerBoxRect = RectF(stubRect.left + 4f, stubRect.top + 4f, stubRect.right - 4f, stubRect.top + stubHeaderH)

            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1.0f
            }
            canvas.drawRoundRect(headerBoxRect, 6f, 6f, boxPaint)

            // Stub School Info
            val sTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                textSize = 9.5f
                textAlign = Paint.Align.CENTER
            }
            val sSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
                textSize = 8.0f
                textAlign = Paint.Align.CENTER
            }

            val stubCenter = headerBoxRect.centerX()
            canvas.drawText(state.schoolName, stubCenter, headerBoxRect.top + 14f, sTitlePaint)
            val upazilaZilla = "উপজেলা: ${state.upazila}, জেলা: ${state.district}।"
            canvas.drawText(upazilaZilla, stubCenter, headerBoxRect.top + 26f, sSubPaint)
            val estText = "স্থাপিত: ${BanglaUtils.toBanglaDigits(state.estYear)}"
            canvas.drawText(estText, stubCenter, headerBoxRect.top + 37f, sSubPaint)

            // Mini Ribbon Banner inside Header Box: "প্রত্যয়নপত্র"
            val miniRibbonRect = RectF(stubCenter - 44f, headerBoxRect.top + 44f, stubCenter + 44f, headerBoxRect.top + 66f)
            drawRibbonBanner(canvas, miniRibbonRect, state.certificateTitle, 9.5f, baseTypeface)

            // Stub Fields (Vertical Layout)
            val fieldLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
                textSize = 9.2f
            }
            val fieldValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                textSize = 9.2f
            }

            var fieldY = stubRect.top + stubHeaderH + 24f
            val fieldSpacing = 19.5f
            val labelLeft = stubRect.left + 8f

            fun drawStubField(label: String, value: String) {
                canvas.drawText(label, labelLeft, fieldY, fieldLabelPaint)
                val labelWidth = fieldLabelPaint.measureText(label)
                canvas.drawText(value, labelLeft + labelWidth + 4f, fieldY, fieldValuePaint)
                fieldY += fieldSpacing
            }

            drawStubField("সিরিয়াল নম্বর: ", serialNumber)
            drawStubField("তারিখ: ", displayDate)
            fieldY += 4f
            drawStubField("নাম: ", student.name)
            drawStubField("পিতার নাম: ", student.fatherName.ifBlank { "—" })
            drawStubField("মাতার নাম: ", student.motherName.ifBlank { "—" })
            drawStubField("জন্মতারিখ: ", displayDob)
            fieldY += 4f
            drawStubField("শ্রেণি: ", student.studentClass)
            drawStubField("রোল নম্বর: ", displayRoll)
            drawStubField("শিক্ষাবর্ষ: ", displaySession)

            // Signature Boxes in Left Stub (Bottom Area)
            val sigBoxW = stubRect.width() - 16f
            val sigBoxH = 48f
            val sigBoxLeft = stubRect.left + 8f

            val sigBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(80, 90, 105)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val sigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
                textSize = 8.5f
            }
            val dotLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(120, 130, 145)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
                pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
            }

            // Box 1: ছাত্র/ছাত্রীর স্বাক্ষর
            val box1Top = stubRect.bottom - 116f
            val box1Rect = RectF(sigBoxLeft, box1Top, sigBoxLeft + sigBoxW, box1Top + sigBoxH)
            canvas.drawRoundRect(box1Rect, 6f, 6f, sigBoxPaint)
            canvas.drawText("ছাত্র/ছাত্রীর স্বাক্ষর:", box1Rect.left + 6f, box1Top + 16f, sigTextPaint)
            canvas.drawText("তারিখ:", box1Rect.left + 6f, box1Top + 38f, sigTextPaint)
            canvas.drawLine(box1Rect.left + 36f, box1Top + 38f, box1Rect.left + sigBoxW - 8f, box1Top + 38f, dotLinePaint)

            // Box 2: প্রদানকারী শিক্ষকের স্বাক্ষর
            val box2Top = stubRect.bottom - 58f
            val box2Rect = RectF(sigBoxLeft, box2Top, sigBoxLeft + sigBoxW, box2Top + sigBoxH)
            canvas.drawRoundRect(box2Rect, 6f, 6f, sigBoxPaint)
            canvas.drawText("প্রদানকারী শিক্ষকের স্বাক্ষর:", box2Rect.left + 6f, box2Top + 16f, sigTextPaint)
            canvas.drawText("তারিখ:", box2Rect.left + 6f, box2Top + 38f, sigTextPaint)
            canvas.drawLine(box2Rect.left + 36f, box2Top + 38f, box2Rect.left + sigBoxW - 8f, box2Top + 38f, dotLinePaint)

            // Vertical Cutting Line between Stub and Main Certificate
            val cutX = ml + stubW + 6f
            val cutLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                style = Paint.Style.STROKE
                strokeWidth = 1.0f
                pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            canvas.drawLine(cutX, mt, cutX, mt + usableH, cutLinePaint)

            // Draw scissors indicator at 30% and 70% height
            drawScissorsIcon(canvas, cutX, mt + usableH * 0.28f)
            drawScissorsIcon(canvas, cutX, mt + usableH * 0.72f)
        }

        // ==========================================
        // 3. DRAW RIGHT MAIN CERTIFICATE (মূল অংশ)
        // ==========================================
        val certRect = RectF(certLeft, mt, certLeft + certW, mt + usableH)

        // Draw Ornate Border
        drawOrnateCertificateBorder(canvas, certRect, state.borderStyle)

        // Draw Watermark in Background
        if (state.showWatermark) {
            drawWatermarkBackground(canvas, certRect, baseTypeface)
        }

        val certContentRect = RectF(
            certRect.left + 16f,
            certRect.top + 14f,
            certRect.right - 16f,
            certRect.bottom - 14f
        )
        val certCenter = certContentRect.centerX()

        // 3.1 Government Header & Logos
        val headerTop = certContentRect.top + 6f

        if (state.showGovtEmblems) {
            // Draw Bangladesh Govt Monogram on Left Top
            val govtLogoRect = RectF(certContentRect.left + 12f, headerTop, certContentRect.left + 64f, headerTop + 52f)
            drawGovtMonogramSeal(canvas, govtLogoRect, baseTypeface)

            // Draw Primary Education Emblem on Right Top
            val eduLogoRect = RectF(certContentRect.right - 64f, headerTop, certContentRect.right - 12f, headerTop + 52f)
            drawPrimaryEducationSeal(canvas, eduLogoRect, baseTypeface)
        }

        // Center Government 3-Line Text
        val govtHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textSize = 10.8f
            textAlign = Paint.Align.CENTER
        }
        val govtHeaderSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textSize = 10.0f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(state.govtHeader1, certCenter, headerTop + 14f, govtHeaderPaint)
        canvas.drawText(state.govtHeader2, certCenter, headerTop + 27f, govtHeaderSubPaint)
        canvas.drawText(state.govtHeader3, certCenter, headerTop + 40f, govtHeaderSubPaint)

        // 3.2 School Name & Details Header
        val schoolNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textSize = 19.5f
            textAlign = Paint.Align.CENTER
        }
        val schoolSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textSize = 11.2f
            textAlign = Paint.Align.CENTER
        }

        val schoolNameY = headerTop + 72f
        canvas.drawText(state.schoolName, certCenter, schoolNameY, schoolNamePaint)

        val upazilaZillaText = "উপজেলা: ${state.upazila}, জেলা: ${state.district}।"
        canvas.drawText(upazilaZillaText, certCenter, schoolNameY + 16.5f, schoolSubPaint)

        val estYearText = "স্থাপিত: ${BanglaUtils.toBanglaDigits(state.estYear)}"
        canvas.drawText(estYearText, certCenter, schoolNameY + 31f, schoolSubPaint)

        // 3.3 Large 3D Ribbon Banner for "প্রত্যয়নপত্র"
        val ribbonW = 200f
        val ribbonH = 34f
        val ribbonY = schoolNameY + 44f
        val mainRibbonRect = RectF(certCenter - ribbonW / 2, ribbonY, certCenter + ribbonW / 2, ribbonY + ribbonH)
        drawRibbonBanner(canvas, mainRibbonRect, state.certificateTitle, 15.5f, baseTypeface)

        // 3.4 Serial Number (Left) & Date (Right) Row
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textSize = 11.2f
        }
        val metaY = ribbonY + ribbonH + 26f
        val metaMargin = certContentRect.left + 24f

        canvas.drawText("সিরিয়াল নম্বর: $serialNumber", metaMargin, metaY, metaPaint)

        val dateText = "তারিখ: $displayDate"
        val dateWidth = metaPaint.measureText(dateText)
        canvas.drawText(dateText, certContentRect.right - 24f - dateWidth, metaY, metaPaint)

        // 3.5 Main Certificate Text Paragraph
        // Layout with high-precision typographic flow and underlined placeholder values
        val bodyTopY = metaY + 42f
        drawCertificateBodyParagraph(
            canvas = canvas,
            contentRect = certContentRect,
            startY = bodyTopY,
            student = student,
            displayRoll = displayRoll,
            displayDob = displayDob,
            displaySession = displaySession,
            state = state,
            baseTypeface = baseTypeface
        )

        // 3.6 Bottom Right Head Teacher Signature Block
        val sigBlockRight = certContentRect.right - 26f
        val sigBlockBottom = certContentRect.bottom - 16f

        // If Signature Image is loaded
        if (state.showHeadTeacherSignature && sigBitmap != null) {
            val sigW = 100f
            val sigH = 38f
            val sigRect = RectF(sigBlockRight - 150f, sigBlockBottom - 78f, sigBlockRight - 30f, sigBlockBottom - 40f)
            canvas.drawBitmap(sigBitmap, null, sigRect, null)
        }

        val htDesignationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textSize = 11.0f
            textAlign = Paint.Align.CENTER
        }
        val htSchoolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
        }

        val htCenterX = sigBlockRight - 80f
        canvas.drawText(state.headTeacherTitle, htCenterX, sigBlockBottom - 32f, htDesignationPaint)
        canvas.drawText(state.schoolName, htCenterX, sigBlockBottom - 18f, htSchoolPaint)
        val shortAddr = "${state.upazila}, ${state.district}।"
        canvas.drawText(shortAddr, htCenterX, sigBlockBottom - 4f, htSchoolPaint)
    }

    /**
     * Draws the main text body of the certificate with styled placeholders
     */
    private fun drawCertificateBodyParagraph(
        canvas: Canvas,
        contentRect: RectF,
        startY: Float,
        student: CertificateStudent,
        displayRoll: String,
        displayDob: String,
        displaySession: String,
        state: CertificateMakerState,
        baseTypeface: Typeface
    ) {
        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textSize = 12.0f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textSize = 12.2f
        }
        val boldLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textSize = 12.0f
        }

        val tenseVerb = if (state.studyTense == "PRESENT") "অধ্যয়ন করছে।" else "অধ্যয়ন করেছে।"
        val wishText = state.wishRemark.ifBlank { "আমি তার সর্বাঙ্গীণ সাফল্য কামনা করি।" }
        val charText = state.characterRemark.ifBlank { "তার স্বভাব চরিত্র ভালো।" }

        val lineSpacing = 24.5f
        val leftX = contentRect.left + 28f
        val rightX = contentRect.right - 28f
        val availW = rightX - leftX

        var curY = startY

        // Line 1: এই মর্মে প্রত্যয়ন করা যাচ্ছে যে, [student name] ,
        val prefix1 = "এই মর্মে প্রত্যয়ন করা যাচ্ছে যে, "
        val nameVal = "${student.name} "
        val comma = ","

        val p1W = normalPaint.measureText(prefix1)
        val nW = valuePaint.measureText(nameVal)
        val totalL1 = p1W + nW + normalPaint.measureText(comma)

        // Center line 1 or indent left
        val l1StartX = (leftX + (availW - totalL1) / 2).coerceAtLeast(leftX)
        canvas.drawText(prefix1, l1StartX, curY, normalPaint)
        canvas.drawText(nameVal, l1StartX + p1W, curY, valuePaint)
        canvas.drawText(comma, l1StartX + p1W + nW, curY, normalPaint)

        curY += lineSpacing

        // Line 2: পিতা: [father] , মাতা: [mother] ,
        val fLabel = "পিতা: "
        val fVal = "${student.fatherName.ifBlank { "—" }} "
        val mLabel = " , মাতা: "
        val mVal = "${student.motherName.ifBlank { "—" }} "

        val fLW = boldLabelPaint.measureText(fLabel)
        val fVW = valuePaint.measureText(fVal)
        val mLW = boldLabelPaint.measureText(mLabel)
        val mVW = valuePaint.measureText(mVal)
        val totalL2 = fLW + fVW + mLW + mVW + normalPaint.measureText(comma)

        val l2StartX = (leftX + (availW - totalL2) / 2).coerceAtLeast(leftX)
        var x = l2StartX
        canvas.drawText(fLabel, x, curY, boldLabelPaint); x += fLW
        canvas.drawText(fVal, x, curY, valuePaint); x += fVW
        canvas.drawText(mLabel, x, curY, boldLabelPaint); x += mLW
        canvas.drawText(mVal, x, curY, valuePaint); x += mVW
        canvas.drawText(comma, x, curY, normalPaint)

        curY += lineSpacing

        // Line 3: জন্মতারিখ: [dob] , রোল নম্বর: [roll] এই বিদ্যালয়ে [session] শিক্ষাবর্ষে [class]
        val dobLabel = "জন্মতারিখ: "
        val dobVal = "$displayDob "
        val rollLabel = ", রোল নম্বর: "
        val rollVal = "$displayRoll "
        val schText = "এই বিদ্যালয়ে "
        val sesVal = "$displaySession "
        val sesText = "শিক্ষাবর্ষে "
        val clsVal = "${student.studentClass} "

        val dLW = boldLabelPaint.measureText(dobLabel)
        val dVW = valuePaint.measureText(dobVal)
        val rLW = boldLabelPaint.measureText(rollLabel)
        val rVW = valuePaint.measureText(rollVal)
        val sTW = normalPaint.measureText(schText)
        val sVW = valuePaint.measureText(sesVal)
        val sEtW = normalPaint.measureText(sesText)
        val cVW = valuePaint.measureText(clsVal)

        val totalL3 = dLW + dVW + rLW + rVW + sTW + sVW + sEtW + cVW
        val l3StartX = (leftX + (availW - totalL3) / 2).coerceAtLeast(leftX)
        x = l3StartX
        canvas.drawText(dobLabel, x, curY, boldLabelPaint); x += dLW
        canvas.drawText(dobVal, x, curY, valuePaint); x += dVW
        canvas.drawText(rollLabel, x, curY, boldLabelPaint); x += rLW
        canvas.drawText(rollVal, x, curY, valuePaint); x += rVW
        canvas.drawText(schText, x, curY, normalPaint); x += sTW
        canvas.drawText(sesVal, x, curY, valuePaint); x += sVW
        canvas.drawText(sesText, x, curY, normalPaint); x += sEtW
        canvas.drawText(clsVal, x, curY, valuePaint)

        curY += lineSpacing

        // Line 4: শ্রেণিতে অধ্যয়ন করেছে। তার স্বভাব চরিত্র ভালো।
        val line4Text = "শ্রেণিতে $tenseVerb $charText"
        val l4W = normalPaint.measureText(line4Text)
        val l4StartX = (leftX + (availW - l4W) / 2).coerceAtLeast(leftX)
        canvas.drawText(line4Text, l4StartX, curY, normalPaint)

        curY += lineSpacing + 4f

        // Line 5: আমি তার সর্বাঙ্গীণ সাফল্য কামনা করি।
        val wishW = normalPaint.measureText(wishText)
        val wishStartX = (leftX + (availW - wishW) / 2).coerceAtLeast(leftX)
        canvas.drawText(wishText, wishStartX, curY, normalPaint)
    }

    /**
     * Draw 3D-styled certificate banner ribbon
     */
    private fun drawRibbonBanner(
        canvas: Canvas,
        rect: RectF,
        title: String,
        fontSize: Float,
        typeface: Typeface
    ) {
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 245, 245)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 200, 200)
            style = Paint.Style.FILL
        }

        val notch = 10f
        val fold = 6f

        // Main Center Banner Body
        val bodyRect = RectF(rect.left + notch + fold, rect.top, rect.right - notch - fold, rect.bottom)
        canvas.drawRoundRect(bodyRect, 4f, 4f, ribbonPaint)
        canvas.drawRoundRect(bodyRect, 4f, 4f, strokePaint)

        // Left Tail / Ribbon Fold
        val leftPath = Path().apply {
            moveTo(bodyRect.left, bodyRect.top + 3f)
            lineTo(rect.left + notch, bodyRect.top - fold)
            lineTo(rect.left, bodyRect.centerY())
            lineTo(rect.left + notch, bodyRect.bottom + fold)
            lineTo(bodyRect.left, bodyRect.bottom - 3f)
            close()
        }
        canvas.drawPath(leftPath, ribbonPaint)
        canvas.drawPath(leftPath, strokePaint)

        // Left shadow triangle
        val leftShadow = Path().apply {
            moveTo(bodyRect.left, bodyRect.bottom - 3f)
            lineTo(bodyRect.left, bodyRect.bottom)
            lineTo(rect.left + notch + 2f, bodyRect.bottom + fold)
            close()
        }
        canvas.drawPath(leftShadow, shadowPaint)

        // Right Tail / Ribbon Fold
        val rightPath = Path().apply {
            moveTo(bodyRect.right, bodyRect.top + 3f)
            lineTo(rect.right - notch, bodyRect.top - fold)
            lineTo(rect.right, bodyRect.centerY())
            lineTo(rect.right - notch, bodyRect.bottom + fold)
            lineTo(bodyRect.right, bodyRect.bottom - 3f)
            close()
        }
        canvas.drawPath(rightPath, ribbonPaint)
        canvas.drawPath(rightPath, strokePaint)

        // Right shadow triangle
        val rightShadow = Path().apply {
            moveTo(bodyRect.right, bodyRect.bottom - 3f)
            lineTo(bodyRect.right, bodyRect.bottom)
            lineTo(rect.right - notch - 2f, bodyRect.bottom + fold)
            close()
        }
        canvas.drawPath(rightShadow, shadowPaint)

        // Text inside Ribbon
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = fontSize
            textAlign = Paint.Align.CENTER
        }
        val textY = bodyRect.centerY() + fontSize * 0.35f
        canvas.drawText(title, bodyRect.centerX(), textY, textPaint)
    }

    /**
     * Draws Ornate Classical Double Certificate Border with Corner Accents
     */
    private fun drawOrnateCertificateBorder(canvas: Canvas, rect: RectF, borderStyle: String) {
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        // Outer Rectangle with smooth rounded corners
        canvas.drawRoundRect(rect, 10f, 10f, outerPaint)

        // Inner Rectangle
        val gap = 4.5f
        val innerRect = RectF(rect.left + gap, rect.top + gap, rect.right - gap, rect.bottom - gap)
        canvas.drawRoundRect(innerRect, 8f, 8f, innerPaint)

        // Draw Ornate Corner Ornaments (Corner Flourishes)
        val ornamentSize = 18f
        val ornPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }

        fun drawCornerFlourish(cx: Float, cy: Float, flipX: Boolean, flipY: Boolean) {
            val sx = if (flipX) -1f else 1f
            val sy = if (flipY) -1f else 1f

            val path = Path().apply {
                moveTo(cx + 2f * sx, cy + 16f * sy)
                cubicTo(cx + 8f * sx, cy + 14f * sy, cx + 14f * sx, cy + 8f * sy, cx + 16f * sx, cy + 2f * sy)
                moveTo(cx + 4f * sx, cy + 12f * sy)
                cubicTo(cx + 7f * sx, cy + 10f * sy, cx + 10f * sx, cy + 7f * sy, cx + 12f * sx, cy + 4f * sy)
            }
            canvas.drawPath(path, ornPaint)
            // Small corner dot
            canvas.drawCircle(cx + 7f * sx, cy + 7f * sy, 1.4f, ornPaint)
        }

        drawCornerFlourish(innerRect.left, innerRect.top, flipX = false, flipY = false)
        drawCornerFlourish(innerRect.right, innerRect.top, flipX = true, flipY = false)
        drawCornerFlourish(innerRect.left, innerRect.bottom, flipX = false, flipY = true)
        drawCornerFlourish(innerRect.right, innerRect.bottom, flipX = true, flipY = true)
    }

    /**
     * Draws light, subtle watermark background in the center of certificate
     */
    private fun drawWatermarkBackground(canvas: Canvas, rect: RectF, typeface: Typeface) {
        val cx = rect.centerX()
        val cy = rect.centerY() + 20f

        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(18, 0, 0, 0) // ~7% light opacity
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }

        // Circular Seal Rings
        canvas.drawCircle(cx, cy, 90f, watermarkPaint)
        canvas.drawCircle(cx, cy, 84f, watermarkPaint)
        canvas.drawCircle(cx, cy, 60f, watermarkPaint)

        // Watermark Text inside circle
        val wmTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(22, 0, 0, 0)
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = 14f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("সবার জন্য শিক্ষা", cx, cy - 8f, wmTextPaint)
        canvas.drawText("গণপ্রজাতন্ত্রী বাংলাদেশ", cx, cy + 12f, wmTextPaint)
    }

    /**
     * Vector draw: Bangladesh Govt Monogram Seal
     */
    private fun drawGovtMonogramSeal(canvas: Canvas, rect: RectF, typeface: Typeface) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = (rect.width() / 2).coerceAtMost(rect.height() / 2)

        val redFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 41, 28) // BD Red
            style = Paint.Style.FILL
        }
        val goldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 180, 26) // BD Gold
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 180, 26)
            style = Paint.Style.FILL
        }

        // Outer red circle
        canvas.drawCircle(cx, cy, radius, redFill)
        canvas.drawCircle(cx, cy, radius, goldStroke)
        canvas.drawCircle(cx, cy, radius - 3.5f, goldStroke)

        // Central Shapla (Water Lily) and Stars Vector Representation
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 235, 59)
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = 7f
        }

        // Shapla Petals
        val petalPath = Path().apply {
            moveTo(cx, cy - radius * 0.45f)
            quadTo(cx + radius * 0.25f, cy, cx, cy + radius * 0.25f)
            quadTo(cx - radius * 0.25f, cy, cx, cy - radius * 0.45f)
            close()
        }
        canvas.drawPath(petalPath, goldFill)

        // Left & Right Petals
        val leftPetal = Path().apply {
            moveTo(cx, cy + radius * 0.15f)
            quadTo(cx - radius * 0.4f, cy - radius * 0.1f, cx - radius * 0.35f, cy - radius * 0.35f)
            quadTo(cx - radius * 0.15f, cy - radius * 0.15f, cx, cy + radius * 0.15f)
            close()
        }
        canvas.drawPath(leftPetal, goldFill)

        val rightPetal = Path().apply {
            moveTo(cx, cy + radius * 0.15f)
            quadTo(cx + radius * 0.4f, cy - radius * 0.1f, cx + radius * 0.35f, cy - radius * 0.35f)
            quadTo(cx + radius * 0.15f, cy - radius * 0.15f, cx, cy + radius * 0.15f)
            close()
        }
        canvas.drawPath(rightPetal, goldFill)

        // 4 Stars
        canvas.drawText("★", cx - radius * 0.5f, cy + radius * 0.55f, starPaint)
        canvas.drawText("★", cx - radius * 0.2f, cy + radius * 0.65f, starPaint)
        canvas.drawText("★", cx + radius * 0.2f, cy + radius * 0.65f, starPaint)
        canvas.drawText("★", cx + radius * 0.5f, cy + radius * 0.55f, starPaint)
    }

    /**
     * Vector draw: Primary Education Logo ("সবার জন্য মানসম্মত শিক্ষা")
     */
    private fun drawPrimaryEducationSeal(canvas: Canvas, rect: RectF, typeface: Typeface) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = (rect.width() / 2).coerceAtMost(rect.height() / 2)

        val greenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 106, 78) // BD Bottle Green
            style = Paint.Style.FILL
        }
        val goldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 180, 26)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        canvas.drawCircle(cx, cy, radius, greenFill)
        canvas.drawCircle(cx, cy, radius, goldStroke)
        canvas.drawCircle(cx, cy, radius - 3.5f, goldStroke)

        // Inner circle
        canvas.drawCircle(cx, cy, radius * 0.65f, whiteFill)

        // Open Book & Student Vector
        val bookPath = Path().apply {
            moveTo(cx, cy + radius * 0.35f)
            lineTo(cx - radius * 0.38f, cy + radius * 0.25f)
            lineTo(cx - radius * 0.38f, cy - radius * 0.05f)
            lineTo(cx, cy + radius * 0.05f)
            lineTo(cx + radius * 0.38f, cy - radius * 0.05f)
            lineTo(cx + radius * 0.38f, cy + radius * 0.25f)
            close()
        }
        val bookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 41, 28)
            style = Paint.Style.FILL
        }
        canvas.drawPath(bookPath, bookPaint)

        // Sun / Rising light above book
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 180, 26)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy - radius * 0.22f, radius * 0.16f, sunPaint)

        // Top arc text indicator
        val miniTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = 5.0f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("সবার জন্য শিক্ষা", cx, cy - radius * 0.7f, miniTextPaint)
    }

    /**
     * Scissors Icon on cutting dashed lines
     */
    private fun drawScissorsIcon(canvas: Canvas, cx: Float, cy: Float) {
        val scissPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val scissFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // Background circle to break dashed line cleanly
        canvas.drawCircle(cx, cy, 6f, scissFill)

        // Two small rings
        canvas.drawCircle(cx - 3.5f, cy + 2.5f, 2.2f, scissPaint)
        canvas.drawCircle(cx - 3.5f, cy - 2.5f, 2.2f, scissPaint)
        // Two crossing blades
        canvas.drawLine(cx - 1.5f, cy + 2f, cx + 4.5f, cy - 3f, scissPaint)
        canvas.drawLine(cx - 1.5f, cy - 2f, cx + 4.5f, cy + 3f, scissPaint)
    }

    private fun getDummyStudent(): CertificateStudent {
        return CertificateStudent(
            id = "dummy_1",
            name = "মোসাঃ মরিয়ম আক্তার",
            studentClass = "৫ম শ্রেণি",
            rollNumber = "১২",
            fatherName = "মোঃ রফিকুল ইসলাম",
            motherName = "মোসাঃ পারভীন বেগম",
            birthDate = "2015-05-12",
            academicYear = "২০২৬",
            gender = "ছাত্রী"
        )
    }
}

/**
 * Native PrintDocumentAdapter for Certificate Printing
 */
class NativeCertificatePrintAdapter(
    private val context: Context,
    private val state: CertificateMakerState,
    private val students: List<CertificateStudent>,
    private val bengaliFont: AppBengaliFont
) : PrintDocumentAdapter() {

    private var pdfDocument: PdfDocument? = null

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

        val totalPages = if (students.isEmpty()) 1 else students.size

        val pdi = PrintDocumentInfo.Builder("Certificate_${state.certificateTitle}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(totalPages)
            .build()

        callback?.onLayoutFinished(pdi, true)
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
            pdfDocument = CertificateNativePdfUtil.generatePdfDocument(state, students, bengaliFont)

            destination?.fileDescriptor?.let { fd ->
                FileOutputStream(fd).use { out ->
                    pdfDocument?.writeTo(out)
                }
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            pdfDocument?.close()
            pdfDocument = null
        }
    }
}
