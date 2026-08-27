package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.model.AdmitCardMakerState
import com.example.data.model.AdmitCardSettings
import com.example.data.model.AdmitCardStudent
import com.example.data.model.RoutineDay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object AdmitCardStorage {
    private const val PREFS_NAME = "admit_card_maker_prefs"
    private const val KEY_STATE = "admit_card_state_json"

    val DAYS_BN = listOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")
    val DEFAULT_SUBJ = listOf(
        "বাংলা", "ইংরেজি", "প্রাথমিক বিজ্ঞান", "বাংলাদেশ ও বিশ্বপরিচয়",
        "প্রাথমিক গণিত", "ধর্ম ও নৈতিক শিক্ষা", "শারীরিক ও মানসিক স্বাস্থ্য শিক্ষা", "চারু ও কারুকলা"
    )
    val DEFAULT_CLASSES = listOf("প্রাক-প্রাথমিক", "প্রথম", "দ্বিতীয়", "তৃতীয়", "চতুর্থ", "পঞ্চম")
    val DEFAULT_TIMES = listOf("১০:০০-১১:০০", "১০:০০-১২:৩০", "০১:০০-০৩:৩০")
    const val BASE_KEY = "BASE"

    fun decodeBase64ToBitmap(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val pure = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val decodedBytes = Base64.decode(pure, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun getDayNameFromDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(isoDate) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, ...
            DAYS_BN[(dayOfWeek - 1) % 7]
        } catch (e: Exception) {
            ""
        }
    }

    fun addDaysToDate(isoDate: String, daysToAdd: Int): String {
        if (isoDate.isBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(isoDate) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.DAY_OF_MONTH, daysToAdd)
            sdf.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    fun formatDateToBangla(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate
        return "${BanglaUtils.toBanglaDigits(parts[2])}/${BanglaUtils.toBanglaDigits(parts[1])}/${BanglaUtils.toBanglaDigits(parts[0])}"
    }

    fun saveState(context: Context, state: AdmitCardMakerState) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject()
            json.put("schoolName", state.schoolName)
            json.put("schoolAddress", state.schoolAddress)
            json.put("examName", state.examName)
            json.put("defaultTime", state.defaultTime)
            json.put("signature", state.signature)
            json.put("scope", state.scope)
            json.put("activeRoutineKey", state.activeRoutineKey)

            // Subjects
            val subjArr = JSONArray()
            state.subjects.forEach { subjArr.put(it) }
            json.put("subjects", subjArr)

            // Classes
            val classArr = JSONArray()
            state.classes.forEach { classArr.put(it) }
            json.put("classes", classArr)

            // Time Presets
            val timeArr = JSONArray()
            state.timePresets.forEach { timeArr.put(it) }
            json.put("timePresets", timeArr)

            // Class Times
            val classTimesObj = JSONObject()
            state.classTimes.forEach { (k, v) -> classTimesObj.put(k, v) }
            json.put("classTimes", classTimesObj)

            // Class Routines
            val routinesObj = JSONObject()
            state.classRoutines.forEach { (classKey, days) ->
                val daysArr = JSONArray()
                days.forEach { d ->
                    val dayObj = JSONObject()
                    dayObj.put("id", d.id)
                    dayObj.put("date", d.date)
                    dayObj.put("day", d.day)
                    val sArr = JSONArray()
                    d.subjects.forEach { sArr.put(it) }
                    dayObj.put("subjects", sArr)
                    daysArr.put(dayObj)
                }
                routinesObj.put(classKey, daysArr)
            }
            json.put("classRoutines", routinesObj)

            // Settings
            val setObj = JSONObject()
            setObj.put("pageSize", state.settings.pageSize)
            setObj.put("cardsPerPage", state.settings.cardsPerPage)
            setObj.put("marginTop", state.settings.marginTop.toDouble())
            setObj.put("marginBottom", state.settings.marginBottom.toDouble())
            setObj.put("marginLeft", state.settings.marginLeft.toDouble())
            setObj.put("marginRight", state.settings.marginRight.toDouble())
            setObj.put("vGap", state.settings.vGap.toDouble())
            setObj.put("frameStyle", state.settings.frameStyle)
            setObj.put("cardFont", state.settings.cardFont)
            setObj.put("sigSize", state.settings.sigSize)
            json.put("settings", setObj)

            prefs.edit().putString(KEY_STATE, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadState(context: Context, defaultSchoolName: String = "", defaultAddress: String = ""): AdmitCardMakerState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null) ?: return AdmitCardMakerState(
            schoolName = if (defaultSchoolName.isNotBlank()) defaultSchoolName else "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়",
            schoolAddress = if (defaultAddress.isNotBlank()) defaultAddress else "আলফাডাঙ্গা, ফরিদপুর।"
        )

        return try {
            val json = JSONObject(raw)
            val schoolName = json.optString("schoolName", if (defaultSchoolName.isNotBlank()) defaultSchoolName else "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়")
            val schoolAddress = json.optString("schoolAddress", if (defaultAddress.isNotBlank()) defaultAddress else "আলফাডাঙ্গা, ফরিদপুর।")
            val examName = json.optString("examName", "দ্বিতীয় প্রান্তিক মূল্যায়ন - ২০২৬")
            val defaultTime = json.optString("defaultTime", "১০:০০-১১:০০")
            val signature = json.optString("signature", "")
            val scope = json.optString("scope", "all")
            val activeRoutineKey = json.optString("activeRoutineKey", BASE_KEY)

            val subjects = mutableListOf<String>()
            val subjArr = json.optJSONArray("subjects")
            if (subjArr != null && subjArr.length() > 0) {
                for (i in 0 until subjArr.length()) subjects.add(subjArr.getString(i))
            } else {
                subjects.addAll(DEFAULT_SUBJ)
            }

            val classes = mutableListOf<String>()
            val classArr = json.optJSONArray("classes")
            if (classArr != null && classArr.length() > 0) {
                for (i in 0 until classArr.length()) classes.add(classArr.getString(i))
            } else {
                classes.addAll(DEFAULT_CLASSES)
            }

            val timePresets = mutableListOf<String>()
            val timeArr = json.optJSONArray("timePresets")
            if (timeArr != null && timeArr.length() > 0) {
                for (i in 0 until timeArr.length()) timePresets.add(timeArr.getString(i))
            } else {
                timePresets.addAll(DEFAULT_TIMES)
            }

            val classTimes = mutableMapOf<String, String>()
            val classTimesObj = json.optJSONObject("classTimes")
            if (classTimesObj != null) {
                val keys = classTimesObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    classTimes[k] = classTimesObj.optString(k, defaultTime)
                }
            }

            val classRoutines = mutableMapOf<String, List<RoutineDay>>()
            val routinesObj = json.optJSONObject("classRoutines")
            if (routinesObj != null) {
                val keys = routinesObj.keys()
                while (keys.hasNext()) {
                    val classKey = keys.next()
                    val daysArr = routinesObj.optJSONArray(classKey) ?: JSONArray()
                    val daysList = mutableListOf<RoutineDay>()
                    for (i in 0 until daysArr.length()) {
                        val dObj = daysArr.getJSONObject(i)
                        val sList = mutableListOf<String>()
                        val sArr = dObj.optJSONArray("subjects") ?: JSONArray()
                        for (s in 0 until sArr.length()) sList.add(sArr.getString(s))
                        daysList.add(
                            RoutineDay(
                                id = dObj.optString("id", UUID.randomUUID().toString()),
                                date = dObj.optString("date", ""),
                                day = dObj.optString("day", ""),
                                subjects = if (sList.isEmpty()) listOf("") else sList
                            )
                        )
                    }
                    classRoutines[classKey] = daysList
                }
            }

            val setObj = json.optJSONObject("settings")
            val settings = if (setObj != null) {
                AdmitCardSettings(
                    pageSize = setObj.optString("pageSize", "A4"),
                    cardsPerPage = setObj.optInt("cardsPerPage", 4),
                    marginTop = setObj.optDouble("marginTop", 0.25).toFloat(),
                    marginBottom = setObj.optDouble("marginBottom", 0.25).toFloat(),
                    marginLeft = setObj.optDouble("marginLeft", 0.25).toFloat(),
                    marginRight = setObj.optDouble("marginRight", 0.25).toFloat(),
                    vGap = setObj.optDouble("vGap", 0.5).toFloat(),
                    frameStyle = setObj.optString("frameStyle", "dashed"),
                    cardFont = setObj.optString("cardFont", "serif"),
                    sigSize = setObj.optString("sigSize", "3")
                )
            } else {
                AdmitCardSettings()
            }

            AdmitCardMakerState(
                schoolName = schoolName,
                schoolAddress = schoolAddress,
                examName = examName,
                subjects = subjects,
                classes = classes,
                timePresets = timePresets,
                defaultTime = defaultTime,
                classTimes = classTimes,
                classRoutines = classRoutines,
                signature = signature,
                settings = settings,
                scope = scope,
                activeRoutineKey = activeRoutineKey
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AdmitCardMakerState(
                schoolName = if (defaultSchoolName.isNotBlank()) defaultSchoolName else "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়",
                schoolAddress = if (defaultAddress.isNotBlank()) defaultAddress else "আলফাডাঙ্গা, ফরিদপুর।"
            )
        }
    }

    /**
     * Generate 100% faithful HTML for Print & PDF export matching the reference template design
     */
    fun generatePrintHtml(
        state: AdmitCardMakerState,
        students: List<AdmitCardStudent>
    ): String {
        val st = state.settings
        val pageDimensions = when (st.pageSize) {
            "Letter" -> Pair(8.5, 11.0)
            "Legal" -> Pair(8.5, 14.0)
            else -> Pair(8.27, 11.69) // A4
        }
        val pageW = pageDimensions.first
        val pageH = pageDimensions.second
        val cpp = st.cardsPerPage.coerceIn(1, 8)
        val mt = st.marginTop.toDouble()
        val mb = st.marginBottom.toDouble()
        val ml = st.marginLeft.toDouble()
        val mr = st.marginRight.toDouble()
        val gap = st.vGap.toDouble()

        val usableH = pageH - mt - mb - gap * (cpp - 1)
        val cardH = Math.max(0.9, usableH / cpp)
        val cardW = pageW - ml - mr

        val frame = when (st.frameStyle) {
            "none" -> "none"
            "double" -> "2.5px double #0f172a"
            else -> "1.5px ${st.frameStyle} #0f172a"
        }

        val sigDimensions = when (st.sigSize) {
            "1" -> Pair(28, 90)
            "2" -> Pair(40, 130)
            "4" -> Pair(72, 230)
            "5" -> Pair(90, 280)
            else -> Pair(55, 180) // 3
        }

        val pagesHtml = StringBuilder()

        for (i in students.indices step cpp) {
            val slice = students.subList(i, Math.min(i + cpp, students.size))
            val cardsHtml = StringBuilder()

            slice.forEachIndexed { idx, stu ->
                val top = mt + idx * (cardH + gap)
                val routine = state.classRoutines[stu.studentClass]?.ifEmpty { null }
                    ?: state.classRoutines[BASE_KEY] ?: emptyList()
                val defTime = state.classTimes[stu.studentClass]
                    ?: state.classTimes[BASE_KEY]
                    ?: state.defaultTime

                val rowsHtml = if (routine.isEmpty()) {
                    "<tr><td colspan=\"3\">রুটিন সেট করা হয়নি</td></tr>"
                } else {
                    routine.joinToString("") { d ->
                        val subs = d.subjects.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }
                        "<tr><td>${escapeHtml(formatDateToBangla(d.date))}</td><td>${escapeHtml(if (d.day.isNotBlank()) d.day else getDayNameFromDate(d.date))}</td><td>${escapeHtml(subs)}</td></tr>"
                    }
                }

                val maxSigH = Math.min(sigDimensions.first, Math.max(16, (cardH * 96 * 0.28).toInt()))
                val maxSigW = Math.min(sigDimensions.second, (cardW * 96 * 0.32).toInt())

                val sigImg = if (state.signature.isNotBlank()) {
                    "<img src=\"${state.signature}\" alt=\"\" style=\"height:${maxSigH}px;max-width:${maxSigW}px;display:block;margin:0 auto 1px;object-fit:contain;\">"
                } else {
                    "<div style=\"height:${Math.max(14, (maxSigH * 0.6).toInt())}px;\"></div>"
                }

                cardsHtml.append("""
                    <div class="admit-card ${if (st.cardFont == "serif") "serif" else ""}" style="left:${ml}in;top:${top}in;width:${cardW}in;height:${cardH}in;border:${frame};position:absolute;box-sizing:border-box;background:#fff;border-radius:2px;display:flex;overflow:hidden;">
                        <div class="left" style="width:38%;padding:6px 8px 0;display:flex;flex-direction:column;border-right:1.5px dashed #94a3b8;overflow:hidden;min-height:0;position:relative;box-sizing:border-box;">
                            <div class="left-top" style="flex:1 1 auto;min-height:0;padding-bottom:52px;">
                                <div class="school-header" style="text-align:center;font-weight:800;font-size:12.5px;line-height:1.3;">${escapeHtml(state.schoolName)}</div>
                                <div class="exam-sub" style="text-align:center;font-size:11px;font-weight:600;margin-top:1px;">${escapeHtml(state.examName)}</div>
                                <div class="admit-title" style="text-align:center;font-size:13.5px;font-weight:700;text-decoration:underline;text-underline-offset:2px;margin:6px 0 10px;">প্রবেশপত্র</div>
                                <div class="student-info" style="font-size:11.5px;line-height:1.6;margin-top:4px;">
                                    <div><b>নাম :</b> ${escapeHtml(stu.name)}</div>
                                    <div><b>শ্রেণি :</b> ${escapeHtml(stu.studentClass)}</div>
                                    <div><b>রোল :</b> ${escapeHtml(BanglaUtils.toBanglaDigits(stu.rollNumber))}</div>
                                </div>
                            </div>
                            <div class="sig-wrap" style="position:absolute;right:8px;bottom:10px;left:auto;width:auto;min-width:100px;max-width:92%;text-align:center;z-index:2;background:#fff;padding:0;">
                                $sigImg
                                <div class="sig-line" style="border-top:1px solid #000;width:100%;min-width:100px;margin:0 auto;"></div>
                                <div class="sig-label" style="display:block;padding-top:2px;font-size:10px;font-weight:700;text-align:center;line-height:1.2;white-space:nowrap;">প্রধান শিক্ষকের স্বাক্ষর</div>
                            </div>
                        </div>
                        <div class="right" style="width:62%;padding:6px 8px;display:flex;flex-direction:column;">
                            <div class="rt-head" style="text-align:center;font-size:11px;font-weight:700;margin-bottom:3px;">${escapeHtml(state.examName)}-এর রুটিন</div>
                            <table class="rt-table" style="width:100%;border-collapse:collapse;text-align:center;font-size:9.5px;">
                                <thead>
                                    <tr>
                                        <th rowspan="2" style="width:22%;border:1px solid #000;padding:2px;font-weight:700;background:#f8fafc;">তারিখ</th>
                                        <th rowspan="2" style="width:22%;border:1px solid #000;padding:2px;font-weight:700;background:#f8fafc;">বার</th>
                                        <th style="border:1px solid #000;padding:2px;font-weight:700;background:#f8fafc;"><span style="font-size:9px;font-weight:600;">সময়: ${escapeHtml(defTime)}</span></th>
                                    </tr>
                                    <tr><th style="border:1px solid #000;padding:2px;font-weight:700;background:#f8fafc;">বিষয়</th></tr>
                                </thead>
                                <tbody>$rowsHtml</tbody>
                            </table>
                        </div>
                    </div>
                """.trimIndent())

                if (idx < slice.size - 1) {
                    cardsHtml.append("""
                        <div class="card-divider" style="position:absolute;left:0;right:0;top:${top + cardH + gap / 2}in;border-bottom:1.2px dashed #64748b;"></div>
                    """.trimIndent())
                }
            }

            pagesHtml.append("""
                <div class="page-sheet" style="width:${pageW}in;height:${pageH}in;position:relative;background:#fff;page-break-after:always;break-after:page;overflow:hidden;">
                    <div style="position:relative;width:100%;height:100%;">
                        $cardsHtml
                    </div>
                </div>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html lang="bn">
            <head>
            <meta charset="UTF-8">
            <title>প্রবেশপত্র - ${escapeHtml(state.examName)}</title>
            <style>
            @page { size: ${pageW}in ${pageH}in; margin: 0; }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: 'Times New Roman', serif; background: #fff; color: #0f172a; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .admit-card.serif { font-family: 'Times New Roman', serif; }
            .page-sheet { page-break-after: always; break-after: page; page-break-inside: avoid; break-inside: avoid; overflow: hidden !important; }
            .page-sheet:last-child { page-break-after: auto !important; break-after: auto !important; }
            .rt-table th, .rt-table td { border: 1px solid #000; padding: 2px 2px; vertical-align: middle; }
            </style>
            </head>
            <body>
            $pagesHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(s: String?): String {
        return (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
