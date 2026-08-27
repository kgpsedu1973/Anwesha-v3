package com.example.util

import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.SchoolInfoEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.entity.UserEntity
import org.json.JSONObject

data class PdfColumnConfig(
    val key: String,
    val label: String,
    val widthPercent: Int? = null,
    val isImage: Boolean = false
)

data class PdfExportStyleOptions(
    val title: String = "শিক্ষার্থী তালিকা",
    val subtitle: String? = null,
    val includeImages: Boolean = true,
    val fontSizePt: Int = 10,
    val rowPaddingPx: Int = 5,
    val isLandscape: Boolean = false,
    val showSchoolHeader: Boolean = true,
    val showSummaryStats: Boolean = true,
    val showSignatureLine: Boolean = true,
    val selectedColumnKeys: List<String> = listOf("photo", "rollNumber", "name", "studentClass", "fatherName", "mobile", "village")
)

object PdfExportUtils {

    fun generateStudentListPdfHtml(
        schoolInfo: SchoolInfoEntity?,
        students: List<StudentEntity>,
        customFields: List<CustomFieldEntity> = emptyList(),
        options: PdfExportStyleOptions
    ): String {
        val schoolName = schoolInfo?.schoolName ?: "অন্বেষা আদর্শ উচ্চ বিদ্যালয়"
        val eiin = schoolInfo?.eiinCode ?: "১২৩৪৫৬"
        val address = schoolInfo?.address ?: "বাংলাদেশ"
        val logoUri = schoolInfo?.logoUri

        val orientationCss = if (options.isLandscape) "@page { size: A4 landscape; margin: 8mm; }" else "@page { size: A4 portrait; margin: 8mm; }"

        // Determine columns to display
        val allAvailableCols = listOf(
            PdfColumnConfig("photo", "ছবি", 10, isImage = true),
            PdfColumnConfig("rollNumber", "রোল", 8),
            PdfColumnConfig("name", "শিক্ষার্থীর নাম", 22),
            PdfColumnConfig("studentClass", "শ্রেণি", 12),
            PdfColumnConfig("section", "শাখা", 6),
            PdfColumnConfig("fatherName", "পিতার নাম", 18),
            PdfColumnConfig("motherName", "মাতার নাম", 16),
            PdfColumnConfig("mobile", "মোবাইল নম্বর", 14),
            PdfColumnConfig("village", "গ্রাম/ঠিকানা", 14),
            PdfColumnConfig("gender", "লিঙ্গ", 8),
            PdfColumnConfig("birthDate", "জন্ম তারিখ", 12),
            PdfColumnConfig("birthRegNumber", "জন্ম নিবন্ধন", 14),
            PdfColumnConfig("academicYear", "শিক্ষাবর্ষ", 8),
            PdfColumnConfig("status", "অবস্থা", 8),
            PdfColumnConfig("id", "আইডি", 12)
        ) + customFields.map { cf ->
            PdfColumnConfig("custom_${cf.name}", cf.name, 12)
        }

        val activeCols = allAvailableCols.filter { options.selectedColumnKeys.contains(it.key) }
            .ifEmpty { allAvailableCols.take(6) }

        // Header Table HTML
        val headerHtml = if (options.showSchoolHeader) {
            val logoTag = if (!logoUri.isNullOrBlank()) {
                "<img src='$logoUri' style='width:52px; height:52px; object-fit:contain; border-radius:50%; margin-right:12px;' />"
            } else ""

            """
            <div style="display:flex; align-items:center; justify-content:center; border-bottom:2px solid #004D40; padding-bottom:8px; margin-bottom:12px;">
                $logoTag
                <div style="text-align:center;">
                    <h2 style="margin:0; color:#004D40; font-size:${options.fontSizePt + 8}pt; font-weight:bold;">$schoolName</h2>
                    <div style="font-size:${options.fontSizePt - 1}pt; color:#444; margin-top:2px;">ইআইআইএন (EIIN): $eiin | $address</div>
                    <div style="font-size:${options.fontSizePt + 2}pt; color:#D81B60; font-weight:bold; margin-top:4px;">${options.title}</div>
                    ${if (!options.subtitle.isNullOrBlank()) "<div style='font-size:${options.fontSizePt - 1}pt; color:#666;'>${options.subtitle}</div>" else ""}
                </div>
            </div>
            """.trimIndent()
        } else {
            """
            <div style="text-align:center; margin-bottom:10px; border-bottom:1px solid #004D40; padding-bottom:4px;">
                <h3 style="margin:0; color:#004D40; font-size:${options.fontSizePt + 4}pt;">${options.title}</h3>
                ${if (!options.subtitle.isNullOrBlank()) "<div style='font-size:${options.fontSizePt}pt; color:#555;'>${options.subtitle}</div>" else ""}
            </div>
            """.trimIndent()
        }

        // Table Columns Headers
        val thCells = activeCols.joinToString("") { col ->
            val align = if (col.key == "rollNumber" || col.key == "photo" || col.key == "gender") "center" else "left"
            "<th style='border:1px solid #004D40; background:#E0F2F1; color:#004D40; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; text-align:$align;'>${col.label}</th>"
        }

        // Table Data Rows
        val tbodyRows = students.mapIndexed { idx, s ->
            val customMap = parseCustomValues(s.customValuesJson)
            val bg = if (idx % 2 == 0) "#ffffff" else "#f9fbfb"

            val cells = activeCols.joinToString("") { col ->
                when (col.key) {
                    "photo" -> {
                        val imgTag = if (options.includeImages && !s.photoUri.isNullOrBlank()) {
                            "<img src='${s.photoUri}' style='width:36px; height:42px; object-fit:cover; border-radius:4px; border:1px solid #ccc; display:block; margin:0 auto;' />"
                        } else {
                            "<div style='width:36px; height:42px; background:#eef2f2; border:1px dashed #bbb; border-radius:4px; margin:0 auto; display:flex; align-items:center; justify-content:center; font-size:8pt; color:#888;'>ছবি নেই</div>"
                        }
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 4px; text-align:center; background:$bg;'>$imgTag</td>"
                    }
                    "rollNumber" -> {
                        val rollStr = BanglaUtils.toBanglaDigits(s.rollNumber)
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; font-weight:bold; color:#004D40; font-size:${options.fontSizePt}pt; background:$bg;'>$rollStr</td>"
                    }
                    "gender" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; font-size:${options.fontSizePt}pt; background:$bg;'>${s.gender}</td>"
                    }
                    "name" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-weight:600; font-size:${options.fontSizePt}pt; background:$bg;'>${s.name}</td>"
                    }
                    "studentClass" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.studentClass}</td>"
                    }
                    "section" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; font-size:${options.fontSizePt}pt; background:$bg;'>${s.section}</td>"
                    }
                    "fatherName" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.fatherName}</td>"
                    }
                    "motherName" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.motherName}</td>"
                    }
                    "mobile" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.mobile}</td>"
                    }
                    "village" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.village}</td>"
                    }
                    "birthDate" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.birthDate}</td>"
                    }
                    "birthRegNumber" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.birthRegNumber}</td>"
                    }
                    "academicYear" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; font-size:${options.fontSizePt}pt; background:$bg;'>${s.academicYear}</td>"
                    }
                    "status" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; font-size:${options.fontSizePt}pt; background:$bg;'>${s.status}</td>"
                    }
                    "id" -> {
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>${s.id}</td>"
                    }
                    else -> {
                        val customName = col.key.removePrefix("custom_")
                        val customVal = customMap[customName] ?: ""
                        "<td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-size:${options.fontSizePt}pt; background:$bg;'>$customVal</td>"
                    }
                }
            }
            "<tr>$cells</tr>"
        }.joinToString("\n")

        // Summary Stats
        val totalCount = students.size
        val boysCount = students.count { it.gender == "ছাত্র" }
        val girlsCount = students.count { it.gender == "ছাত্রী" }

        val statsHtml = if (options.showSummaryStats) {
            """
            <div style="margin-top:10px; font-size:${options.fontSizePt}pt; display:flex; justify-content:space-between; background:#F0F7F7; padding:6px 12px; border-radius:4px; border:1px solid #B2DFDB;">
                <span><strong>মোট শিক্ষার্থী:</strong> ${BanglaUtils.toBanglaDigits(totalCount)} জন</span>
                <span><strong>ছাত্র:</strong> ${BanglaUtils.toBanglaDigits(boysCount)} জন</span>
                <span><strong>ছাত্রী:</strong> ${BanglaUtils.toBanglaDigits(girlsCount)} জন</span>
                <span><strong>তারিখ:</strong> ${BanglaUtils.getBanglaDateString()}</span>
            </div>
            """.trimIndent()
        } else ""

        // Signature Line
        val signatureHtml = if (options.showSignatureLine) {
            """
            <div style="margin-top:28px; display:flex; justify-content:space-between; font-size:${options.fontSizePt}pt;">
                <div style="text-align:center; border-top:1px dashed #333; padding-top:4px; width:160px;">
                    শ্রেণি শিক্ষকের স্বাক্ষর
                </div>
                <div style="text-align:center; border-top:1px dashed #333; padding-top:4px; width:160px;">
                    প্রধান শিক্ষকের স্বাক্ষর ও সীল
                </div>
            </div>
            """.trimIndent()
        } else ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                $orientationCss
                body {
                    font-family: 'SolaimanLipi', 'Kalpurush', sans-serif, 'Times New Roman';
                    margin: 0;
                    padding: 0;
                    color: #222;
                    background: #fff;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    page-break-inside: auto;
                }
                tr {
                    page-break-inside: avoid;
                    page-break-after: auto;
                }
                thead {
                    display: table-header-group;
                }
                tfoot {
                    display: table-footer-group;
                }
            </style>
        </head>
        <body>
            $headerHtml
            <table>
                <thead>
                    <tr>
                        $thCells
                    </tr>
                </thead>
                <tbody>
                    $tbodyRows
                </tbody>
            </table>
            $statsHtml
            $signatureHtml
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Generates Teachers & Staff PDF report.
     */
    fun generateTeacherListPdfHtml(
        schoolInfo: SchoolInfoEntity?,
        teachers: List<UserEntity>,
        options: PdfExportStyleOptions
    ): String {
        val schoolName = schoolInfo?.schoolName ?: "অন্বেষা আদর্শ উচ্চ বিদ্যালয়"
        val eiin = schoolInfo?.eiinCode ?: "১২৩৪৫৬"
        val address = schoolInfo?.address ?: "বাংলাদেশ"

        val orientationCss = if (options.isLandscape) "@page { size: A4 landscape; margin: 8mm; }" else "@page { size: A4 portrait; margin: 8mm; }"

        val tbodyRows = teachers.mapIndexed { idx, t ->
            val bg = if (idx % 2 == 0) "#ffffff" else "#f9fbfb"
            val slStr = BanglaUtils.toBanglaDigits(idx + 1)
            """
            <tr>
                <td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; background:$bg;'>$slStr</td>
                <td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; font-weight:600; background:$bg;'>${t.name}</td>
                <td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; color:#004D40; font-weight:bold; background:$bg;'>${t.role}</td>
                <td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; background:$bg;'>${t.phone}</td>
                <td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; background:$bg;'>${t.email}</td>
                <td style='border:1px solid #ccc; padding:${options.rowPaddingPx}px 6px; text-align:center; background:$bg;'>${t.status}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                $orientationCss
                body { font-family: 'SolaimanLipi', sans-serif, 'Times New Roman'; margin:0; color:#222; }
                table { width: 100%; border-collapse: collapse; }
            </style>
        </head>
        <body>
            <div style="text-align:center; border-bottom:2px solid #004D40; padding-bottom:8px; margin-bottom:12px;">
                <h2 style="margin:0; color:#004D40; font-size:${options.fontSizePt + 8}pt;">$schoolName</h2>
                <div style="font-size:${options.fontSizePt - 1}pt; color:#444;">ইআইআইএন: $eiin | $address</div>
                <div style="font-size:${options.fontSizePt + 2}pt; color:#D81B60; font-weight:bold; margin-top:4px;">শিক্ষক ও কর্মচারী তালিকা</div>
            </div>
            <table>
                <thead>
                    <tr style="background:#E0F2F1; color:#004D40;">
                        <th style="border:1px solid #004D40; padding:${options.rowPaddingPx}px 6px; width:40px;">ক্রমিক</th>
                        <th style="border:1px solid #004D40; padding:${options.rowPaddingPx}px 6px; text-align:left;">নাম</th>
                        <th style="border:1px solid #004D40; padding:${options.rowPaddingPx}px 6px; text-align:left;">পদবী</th>
                        <th style="border:1px solid #004D40; padding:${options.rowPaddingPx}px 6px; text-align:left;">মোবাইল</th>
                        <th style="border:1px solid #004D40; padding:${options.rowPaddingPx}px 6px; text-align:left;">ইমেইল</th>
                        <th style="border:1px solid #004D40; padding:${options.rowPaddingPx}px 6px; width:70px;">অবস্থা</th>
                    </tr>
                </thead>
                <tbody>
                    $tbodyRows
                </tbody>
            </table>
            <div style="margin-top:12px; font-size:${options.fontSizePt}pt; background:#F0F7F7; padding:6px 12px; border:1px solid #B2DFDB;">
                <strong>মোট শিক্ষক ও স্টাফ:</strong> ${BanglaUtils.toBanglaDigits(teachers.size)} জন | <strong>তারিখ:</strong> ${BanglaUtils.getBanglaDateString()}
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun parseCustomValues(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
