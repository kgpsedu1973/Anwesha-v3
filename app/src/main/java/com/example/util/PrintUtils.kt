package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.StudentEntity
import com.example.data.model.AdmitCardMakerState
import com.example.data.model.AdmitCardStudent
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExamRoutineEntry(
    val examDate: String,
    val day: String,
    val subject: String,
    val time: String
)

object PrintUtils {

    // Retain WebView reference to prevent garbage collection during printing
    private var retainedWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Rock-solid print execution using Android's native PrintManager.
     * Works with both physical printers and "Save as PDF".
     */
    fun printHtmlContent(
        context: Context,
        documentName: String,
        htmlContent: String,
        isLandscape: Boolean = false,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        mainHandler.post {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    val err = "এই ডিভাইসে প্রিন্টিং সার্ভিস উপলব্ধ নয়।"
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    onError?.invoke(err)
                    return@post
                }

                val safeDocName = documentName.replace(Regex("[^a-zA-Z0-9_\\-\\u0980-\\u09FF]"), "_").take(40)
                val webView = WebView(context.applicationContext)
                retainedWebView = webView

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            val printAdapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(safeDocName)
                            val mediaSize = if (isLandscape) {
                                PrintAttributes.MediaSize.ISO_A4.asLandscape()
                            } else {
                                PrintAttributes.MediaSize.ISO_A4.asPortrait()
                            }
                            val printAttributes = PrintAttributes.Builder()
                                .setMediaSize(mediaSize)
                                .setResolution(PrintAttributes.Resolution("pdf_print", "Standard PDF", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()

                            printManager.print(safeDocName, printAdapter, printAttributes)
                            onSuccess?.invoke()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            val msg = "প্রিন্ট চালু করতে সমস্যা হয়েছে: ${e.localizedMessage}"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onError?.invoke(msg)
                        }
                    }
                }

                webView.loadDataWithBaseURL(null, htmlContent, "text/html; charset=UTF-8", "UTF-8", null)
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = "প্রিন্ট তৈরিতে ত্রুটি: ${e.localizedMessage}"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onError?.invoke(msg)
            }
        }
    }

    /**
     * Share HTML directly or open in browser/PDF viewer as fallback.
     */
    fun shareHtmlDocument(
        context: Context,
        documentTitle: String,
        htmlContent: String
    ) {
        try {
            val cacheDir = File(context.cacheDir, "documents")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "${documentTitle.replace(" ", "_")}.html")
            FileOutputStream(file).use { it.write(htmlContent.toByteArray(Charsets.UTF_8)) }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, documentTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "ডকুমেন্ট শেয়ার বা ওপেন করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "শেয়ার করতে ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates HTML matching the exact design shown in the user's reference image.
     * Dynamic card height naturally scales with the number of routine days.
     */
    fun generateAdmitCardsHtml(
        state: AdmitCardMakerState,
        students: List<AdmitCardStudent>
    ): String {
        val schoolName = state.schoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" }
        val examName = state.examName.ifBlank { "২য় প্রান্তিক মূল্যায়ন - ২০২৬" }
        val cardsPerPage = state.settings.cardsPerPage
        val borderStyle = when (state.settings.frameStyle) {
            "solid" -> "1.5px solid #000"
            "dotted" -> "1.5px dotted #333"
            "double" -> "3px double #000"
            "none" -> "none"
            else -> "1.5px dashed #222" // default matching reference image
        }
        val fontFamily = if (state.settings.cardFont == "serif") "'SolaimanLipi', 'Times New Roman', serif" else "'Kalpurush', sans-serif"

        // Group students into pages
        val pages = students.chunked(cardsPerPage)
        val pagesHtml = StringBuilder()

        pages.forEachIndexed { pageIndex, pageStudents ->
            val isLastPage = pageIndex == pages.size - 1
            val pageBreakStyle = if (!isLastPage) "page-break-after: always;" else ""

            pagesHtml.append("<div class='sheet-page' style='$pageBreakStyle'>")

            pageStudents.forEach { student ->
                val routine = state.classRoutines[student.studentClass]?.ifEmpty { null }
                    ?: state.classRoutines[AdmitCardStorage.BASE_KEY]
                    ?: emptyList()
                val examTime = state.classTimes[student.studentClass]
                    ?: state.classTimes[AdmitCardStorage.BASE_KEY]
                    ?: state.defaultTime.ifBlank { "১০:০০-১২:৩০" }

                // Build routine table body rows
                val routineRows = if (routine.isEmpty()) {
                    """
                    <tr>
                        <td style="border: 1px solid #000; padding: 3px; text-align: center;">—</td>
                        <td style="border: 1px solid #000; padding: 3px; text-align: center;">—</td>
                        <td style="border: 1px solid #000; padding: 3px; text-align: center;">সকল বিষয়</td>
                    </tr>
                    """.trimIndent()
                } else {
                    routine.joinToString("") { day ->
                        val dateFormatted = AdmitCardStorage.formatDateToBangla(day.date).ifBlank { day.date.ifBlank { "—" } }
                        val dayName = if (day.day.isNotBlank()) day.day else AdmitCardStorage.getDayNameFromDate(day.date).ifBlank { "—" }
                        val subs = day.subjects.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }
                        """
                        <tr>
                            <td style="border: 1px solid #000; padding: 2px 4px; text-align: center;">$dateFormatted</td>
                            <td style="border: 1px solid #000; padding: 2px 4px; text-align: center;">$dayName</td>
                            <td style="border: 1px solid #000; padding: 2px 4px; text-align: center;">$subs</td>
                        </tr>
                        """.trimIndent()
                    }
                }

                // Signature section
                val signatureTag = if (state.signature.isNotBlank()) {
                    "<img src='${state.signature}' style='max-height: 38px; max-width: 105px; object-fit: contain; display: block; margin: 0 0 2px auto;' />"
                } else {
                    "<div style='height: 24px;'></div>"
                }

                // Address parsing
                val (dispSchoolName, dispAddress) = if (schoolName.contains(",")) {
                    val parts = schoolName.split(",", limit = 2)
                    Pair(parts[0].trim(), parts[1].trim())
                } else {
                    Pair(schoolName, "")
                }

                // Card inner HTML - EXACT MATCH TO REFERENCE IMAGE
                val cardHtml = """
                <div class="admit-card-box">
                    <div class="left-section">
                        <div class="school-header">
                            <div class="school-name">$dispSchoolName</div>
                            ${if (dispAddress.isNotBlank()) "<div class='school-address'>$dispAddress</div>" else ""}
                            <div class="exam-name">$examName</div>
                            <div class="admit-title"><u><b>প্রবেশপত্র</b></u></div>
                        </div>

                        <div class="student-info">
                            <div class="info-row"><span class="info-label">নাম :</span> <span class="info-val">${student.name}</span></div>
                            <div class="info-row"><span class="info-label">শ্রেণি :</span> <span class="info-val">${student.studentClass}</span></div>
                            <div class="info-row"><span class="info-label">রোল :</span> <span class="info-val">${BanglaUtils.toBanglaDigits(student.rollNumber)}</span></div>
                        </div>

                        <div class="signature-section">
                            $signatureTag
                            <div class="sig-label">প্রধান শিক্ষকের স্বাক্ষর</div>
                        </div>
                    </div>

                    <div class="right-section">
                        <table class="routine-table">
                            <thead>
                                <tr>
                                    <th colspan="3" class="table-top-header">$examName এর রুটিন</th>
                                </tr>
                                <tr>
                                    <th rowspan="2" class="th-col" style="width: 28%;">তারিখ</th>
                                    <th rowspan="2" class="th-col" style="width: 22%;">বার</th>
                                    <th class="th-col th-time" style="width: 50%;">$examTime</th>
                                </tr>
                                <tr>
                                    <th class="th-col th-sub">বিষয়</th>
                                </tr>
                            </thead>
                            <tbody>
                                $routineRows
                            </tbody>
                        </table>
                    </div>
                </div>
                """.trimIndent()

                pagesHtml.append(cardHtml)
            }

            pagesHtml.append("</div>")
        }

        return """
        <!DOCTYPE html>
        <html lang="bn">
        <head>
            <meta charset="utf-8">
            <title>প্রবেশপত্র - $schoolName</title>
            <style>
                @page {
                    size: ${state.settings.pageSize} ${if (state.settings.orientation == "landscape") "landscape" else "portrait"};
                    margin: ${state.settings.marginTop}in ${state.settings.marginRight}in ${state.settings.marginBottom}in ${state.settings.marginLeft}in;
                }
                * {
                    box-sizing: border-box;
                    -webkit-print-color-adjust: exact;
                    print-color-adjust: exact;
                }
                body {
                    margin: 0;
                    padding: 0;
                    font-family: $fontFamily;
                    color: #000;
                    background: #fff;
                }
                .sheet-page {
                    width: 100%;
                    display: flex;
                    flex-direction: column;
                    gap: ${state.settings.vGap}in;
                    padding: 0;
                    margin-bottom: ${state.settings.vGap}in;
                }
                .admit-card-box {
                    width: 100%;
                    display: flex;
                    flex-direction: row;
                    border: $borderStyle;
                    border-radius: 8px;
                    padding: 8px 12px;
                    page-break-inside: avoid;
                    background: #fff;
                    align-items: stretch;
                }
                .left-section {
                    width: 44%;
                    padding-right: 12px;
                    border-right: 1.5px dashed #333;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                }
                .school-header {
                    text-align: center;
                }
                .school-name {
                    font-size: 13px;
                    font-weight: bold;
                    line-height: 1.25;
                }
                .school-address {
                    font-size: 11px;
                    margin-top: 1px;
                }
                .exam-name {
                    font-size: 11.5px;
                    margin-top: 3px;
                }
                .admit-title {
                    font-size: 13px;
                    font-weight: bold;
                    margin-top: 3px;
                }
                .student-info {
                    margin: 8px 0;
                    font-size: 12px;
                    line-height: 1.6;
                    text-align: left;
                }
                .info-row {
                    display: block;
                }
                .info-label {
                    font-weight: bold;
                }
                .info-val {
                    font-weight: 500;
                }
                .signature-section {
                    text-align: right;
                    margin-top: 4px;
                    align-self: flex-end;
                    width: 100%;
                }
                .sig-label {
                    font-size: 10.5px;
                    font-weight: bold;
                    text-align: right;
                }
                .right-section {
                    width: 56%;
                    padding-left: 12px;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                }
                .routine-table {
                    width: 100%;
                    border-collapse: collapse;
                    border: 1px solid #000;
                    font-size: 11px;
                    text-align: center;
                }
                .table-top-header {
                    border: 1px solid #000;
                    padding: 3px 2px;
                    font-size: 11.5px;
                    font-weight: bold;
                    background: #fdfdfd;
                }
                .th-col {
                    border: 1px solid #000;
                    padding: 2px;
                    font-weight: bold;
                }
                .th-time {
                    font-size: 10.5px;
                    font-weight: normal;
                }
                .th-sub {
                    font-size: 11px;
                    font-weight: bold;
                }
                .routine-table td {
                    border: 1px solid #000;
                    padding: 2.5px 3px;
                    font-size: 10.5px;
                }
            </style>
        </head>
        <body>
            $pagesHtml
        </body>
        </html>
        """.trimIndent()
    }
}
