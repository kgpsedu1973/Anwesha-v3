package com.example.util

import android.content.Context
import android.print.*
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.StudentEntity

data class ExamRoutineEntry(
    val examDate: String,
    val day: String,
    val subject: String,
    val time: String
)

object PrintUtils {

    fun printHtmlContent(context: Context, documentName: String, htmlContent: String) {
        try {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter(documentName)
                    val builder = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                        .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
                    printManager.print(documentName, printAdapter, builder.build())
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateAdmitCardHtml(
        schoolName: String,
        examName: String,
        studentName: String,
        className: String,
        roll: Int,
        fatherName: String,
        motherName: String,
        subjects: List<String>,
        cardsPerPage: Int = 1,
        routineEntries: List<ExamRoutineEntry> = emptyList(),
        showCuttingLine: Boolean = true,
        photoBase64: String? = null
    ): String {
        return generateMultiAdmitCardHtml(
            schoolName = schoolName,
            examName = examName,
            students = listOf(
                StudentEntity(
                    id = "1",
                    studentClass = className,
                    rollNumber = roll,
                    name = studentName,
                    fatherName = fatherName,
                    motherName = motherName,
                    birthDate = "",
                    mobile = "",
                    village = "",
                    academicYear = "২০২৬",
                    address = "",
                    birthRegNumber = "",
                    gender = "ছাত্র",
                    photoUri = photoBase64
                )
            ),
            routineEntries = if (routineEntries.isNotEmpty()) routineEntries else subjects.map {
                ExamRoutineEntry(examDate = "-", day = "-", subject = it, time = "১০:০০ AM")
            },
            cardsPerPage = cardsPerPage,
            showCuttingLine = showCuttingLine
        )
    }

    fun generateMultiAdmitCardHtml(
        schoolName: String,
        examName: String,
        students: List<StudentEntity>,
        routineEntries: List<ExamRoutineEntry>,
        cardsPerPage: Int = 1,
        showCuttingLine: Boolean = true
    ): String {
        val routineRows = if (routineEntries.isEmpty()) {
            "<tr><td style='border:1px solid #999;padding:4px;'>সকল বিষয়</td><td style='border:1px solid #999;padding:4px;'>-</td><td style='border:1px solid #999;padding:4px;'>১০:০০ AM</td><td style='border:1px solid #999;padding:4px;'></td></tr>"
        } else {
            routineEntries.joinToString("") {
                "<tr><td style='border:1px solid #999;padding:4px;'>${it.subject}</td><td style='border:1px solid #999;padding:4px;'>${it.examDate} (${it.day})</td><td style='border:1px solid #999;padding:4px;'>${it.time}</td><td style='border:1px solid #999;padding:4px;'></td></tr>"
            }
        }

        val cardHeight = when (cardsPerPage) {
            4 -> "48%"
            2 -> "48%"
            else -> "96%"
        }
        val cardWidth = when (cardsPerPage) {
            4 -> "48%"
            else -> "98%"
        }

        val cardsHtml = students.joinToString("") { s ->
            val banglaRoll = BanglaUtils.toBanglaDigits(s.rollNumber)
            val photoTag = if (!s.photoUri.isNullOrBlank()) {
                "<img src='${s.photoUri}' style='width:55px;height:70px;object-fit:cover;border:1px solid #004D40;border-radius:4px;'/>"
            } else {
                "<div style='width:55px;height:70px;border:1px solid #999;background:#f5f5f5;display:flex;align-items:center;justify-content:center;font-size:10px;color:#777;border-radius:4px;'>ছবি</div>"
            }

            """
            <div class="admit-card" style="width: $cardWidth; min-height: $cardHeight; margin: 1%; box-sizing: border-box; border: ${if (showCuttingLine) "1px dashed #004D40" else "1px solid #004D40"}; padding: 12px; border-radius: 8px; page-break-inside: avoid; display: inline-block; vertical-align: top; background: #fff;">
                <div style="text-align:center; border-bottom: 1.5px solid #004D40; padding-bottom: 4px; margin-bottom: 8px;">
                    <h3 style="margin:0; color:#004D40; font-size: 16px;">$schoolName</h3>
                    <h4 style="margin:2px 0 0 0; color:#D81B60; font-size: 13px;">$examName - প্রবেশপত্র</h4>
                </div>
                <table style="width:100%; border-collapse:collapse; margin-bottom:6px; font-size:12px;">
                    <tr>
                        <td style="vertical-align:top;">
                            <strong>নাম:</strong> ${s.name}<br/>
                            <strong>শ্রেণি:</strong> ${s.studentClass} | <strong>রোল:</strong> $banglaRoll<br/>
                            <strong>পিতা:</strong> ${s.fatherName}<br/>
                            <strong>মাতা:</strong> ${s.motherName}
                        </td>
                        <td style="width:65px; text-align:right; vertical-align:top;">
                            $photoTag
                        </td>
                    </tr>
                </table>
                <div style="font-size:11px; font-weight:bold; color:#004D40; margin-bottom:3px;">পরীক্ষার বিষয়সূচি:</div>
                <table style="width:100%; border-collapse:collapse; font-size:10px;">
                    <thead>
                        <tr style="background:#E0F2F1;">
                            <th style="border:1px solid #999; padding:3px; text-align:left;">বিষয়</th>
                            <th style="border:1px solid #999; padding:3px; text-align:left;">তারিখ ও বার</th>
                            <th style="border:1px solid #999; padding:3px; text-align:left;">সময়</th>
                            <th style="border:1px solid #999; padding:3px; text-align:left;">স্বাক্ষর</th>
                        </tr>
                    </thead>
                    <tbody>
                        $routineRows
                    </tbody>
                </table>
                <div style="margin-top: 15px; text-align: right; font-size: 10px;">
                    <div style="display:inline-block; border-top: 1px dashed #333; padding-top:2px; width:120px; text-align:center;">
                        প্রধান শিক্ষকের স্বাক্ষর
                    </div>
                </div>
            </div>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    @page { size: A4 portrait; margin: 8mm; }
                    body { font-family: 'SolaimanLipi', sans-serif, 'Times New Roman'; margin: 0; background: #fff; }
                    .page-container { width: 100%; display: flex; flex-wrap: wrap; }
                </style>
            </head>
            <body>
                <div class="page-container">
                    $cardsHtml
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun generateSeatPlanHtml(
        schoolName: String,
        examName: String,
        sessionYear: String,
        students: List<StudentEntity>,
        columns: Int = 2,
        rowsPerPage: Int = 4,
        useBanglaRoll: Boolean = true,
        showCuttingLine: Boolean = true,
        namePretext: String = "নাম: ",
        classPretext: String = "শ্রেণি: ",
        rollPretext: String = "রোল নং: ",
        roomNo: String = ""
    ): String {
        val colWidthPercent = if (columns == 3) "31%" else "47%"
        val cardsHtml = students.joinToString("") { s ->
            val rollStr = if (useBanglaRoll) BanglaUtils.toBanglaDigits(s.rollNumber) else s.rollNumber.toString()
            """
            <div style="width: $colWidthPercent; margin: 1%; box-sizing: border-box; border: ${if (showCuttingLine) "1px dashed #004D40" else "1px solid #ccc"}; padding: 8px; border-radius: 6px; page-break-inside: avoid; display: inline-block; vertical-align: top; background: #fff;">
                <div style="text-align:center; border-bottom: 1px solid #004D40; padding-bottom: 3px; margin-bottom: 6px;">
                    <div style="font-weight:bold; font-size:12px; color:#004D40;">$schoolName</div>
                    <div style="font-size:10px; color:#D81B60; font-weight:bold;">$examName ($sessionYear) - আসন বিন্যাস</div>
                </div>
                <div style="font-size:11px; line-height: 1.4;">
                    <strong>$namePretext</strong> ${s.name}<br/>
                    <div style="display:flex; justify-content:space-between; margin-top:2px;">
                        <span><strong>$classPretext</strong> ${s.studentClass}</span>
                        <span><strong>$rollPretext</strong> <span style="font-weight:bold; color:#1565C0;">$rollStr</span></span>
                    </div>
                    ${if (roomNo.isNotBlank()) "<div style='font-size:9px; color:#555; margin-top:2px;'>কক্ষ: $roomNo</div>" else ""}
                </div>
            </div>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    @page { size: A4 portrait; margin: 8mm; }
                    body { font-family: 'SolaimanLipi', sans-serif, 'Times New Roman'; margin: 0; background: #fff; }
                    .sheet { width: 100%; display: flex; flex-wrap: wrap; }
                </style>
            </head>
            <body>
                <div class="sheet">
                    $cardsHtml
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}

