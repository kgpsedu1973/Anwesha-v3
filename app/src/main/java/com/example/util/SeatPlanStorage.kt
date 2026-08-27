package com.example.util

import android.content.Context
import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object SeatPlanStorage {
    private const val PREFS_NAME = "seat_plan_maker_prefs"
    private const val KEY_STATE = "seat_plan_state_json"

    val GRID_PRESETS = listOf(
        SeatPlanGridPreset(
            titleBn = "২ কলাম × ৬ রো (১২ কার্ড)",
            subtitleBn = "আদর্শ A4 মাপ • স্পষ্ট ও পরিমিত ফন্ট",
            columns = 2,
            rows = 6,
            totalCards = 12,
            isRecommended = true
        ),
        SeatPlanGridPreset(
            titleBn = "২ কলাম × ৭ রো (১৪ কার্ড)",
            subtitleBn = "কমপ্যাক্ট A4 শিট • কাগজ সাশ্রয়ী লেআউট",
            columns = 2,
            rows = 7,
            totalCards = 14
        ),
        SeatPlanGridPreset(
            titleBn = "২ কলাম × ৫ রো (১০ কার্ড)",
            subtitleBn = "বড় ফন্ট ও প্রশস্ত স্পেস • সহজে দূর থেকে দৃশ্যমান",
            columns = 2,
            rows = 5,
            totalCards = 10
        ),
        SeatPlanGridPreset(
            titleBn = "২ কলাম × ৪ রো (৮ কার্ড)",
            subtitleBn = "জাম্বো সাইজ • বেঞ্চের মাঝামাঝি লাগানোর উপযোগী",
            columns = 2,
            rows = 4,
            totalCards = 8
        ),
        SeatPlanGridPreset(
            titleBn = "১ কলাম × ৫ রো (৫ কার্ড)",
            subtitleBn = "ওয়াইড ব্যানার স্টাইল • বড় বেঞ্চ বা ডেস্কের জন্য",
            columns = 1,
            rows = 5,
            totalCards = 5
        ),
        SeatPlanGridPreset(
            titleBn = "৩ কলাম × ৬ রো (১৮ কার্ড)",
            subtitleBn = "মিনি স্টিকার সাইজ • অতিরিক্ত শিক্ষার্থী ধারণক্ষমতা",
            columns = 3,
            rows = 6,
            totalCards = 18
        )
    )

    fun formatClassDisplay(rawClass: String, format: String): String {
        return when (format) {
            "SHORT" -> {
                when {
                    rawClass.contains("প্রাক", ignoreCase = true) || rawClass.contains("শিশু", ignoreCase = true) -> "প্রাক"
                    rawClass.contains("প্রথম") || rawClass.contains("১ম") || rawClass == "1" -> "১ম"
                    rawClass.contains("দ্বিতীয়") || rawClass.contains("দ্বিতীয়") || rawClass.contains("২য়") || rawClass == "2" -> "২য়"
                    rawClass.contains("তৃতীয়") || rawClass.contains("তৃতীয়") || rawClass.contains("৩য়") || rawClass == "3" -> "৩য়"
                    rawClass.contains("চতুর্থ") || rawClass.contains("৪র্থ") || rawClass == "4" -> "৪র্থ"
                    rawClass.contains("পঞ্চম") || rawClass.contains("৫ম") || rawClass == "5" -> "৫ম"
                    else -> rawClass
                }
            }
            "FULL" -> {
                when {
                    rawClass.contains("প্রাক") || rawClass.contains("শিশু") -> "প্রাক-প্রাথমিক"
                    rawClass.contains("প্রথম") || rawClass.contains("১ম") || rawClass == "1" -> "প্রথম শ্রেণি"
                    rawClass.contains("দ্বিতীয়") || rawClass.contains("দ্বিতীয়") || rawClass.contains("২য়") || rawClass == "2" -> "দ্বিতীয় শ্রেণি"
                    rawClass.contains("তৃতীয়") || rawClass.contains("তৃতীয়") || rawClass.contains("৩য়") || rawClass == "3" -> "তৃতীয় শ্রেণি"
                    rawClass.contains("চতুর্থ") || rawClass.contains("৪র্থ") || rawClass == "4" -> "চতুর্থ শ্রেণি"
                    rawClass.contains("পঞ্চম") || rawClass.contains("৫ম") || rawClass == "5" -> "পঞ্চম শ্রেণি"
                    else -> rawClass
                }
            }
            else -> rawClass
        }
    }

    fun saveState(context: Context, state: SeatPlanMakerState) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject()
            json.put("schoolName", state.schoolName)
            json.put("schoolAddress", state.schoolAddress)
            json.put("examName", state.examName)
            json.put("fontId", state.fontId)

            // Fields
            val f = state.fields
            val fObj = JSONObject().apply {
                put("showSchoolName", f.showSchoolName)
                put("showSchoolAddress", f.showSchoolAddress)
                put("showExamName", f.showExamName)
                put("showSeatPlanTitle", f.showSeatPlanTitle)
                put("seatPlanTitleText", f.seatPlanTitleText)
                put("showStudentName", f.showStudentName)
                put("showStudentClass", f.showStudentClass)
                put("showRollNumber", f.showRollNumber)
                put("showSection", f.showSection)
                put("showRoomNumber", f.showRoomNumber)
                put("roomNumberText", f.roomNumberText)
                put("showBenchNumber", f.showBenchNumber)
                put("benchPrefix", f.benchPrefix)
                put("convertBanglaDigits", f.convertBanglaDigits)
                put("classFormat", f.classFormat)
                put("headerFontSizeScale", f.headerFontSizeScale.toDouble())
                put("contentFontSizeScale", f.contentFontSizeScale.toDouble())
                put("isSchoolNameBold", f.isSchoolNameBold)
                put("isTitleBold", f.isTitleBold)
                put("cardCornerRadiusDp", f.cardCornerRadiusDp.toDouble())
                put("cardBorderWidthDp", f.cardBorderWidthDp.toDouble())
                put("cardBorderStyle", f.cardBorderStyle)
            }
            json.put("fields", fObj)

            // Page
            val p = state.page
            val pObj = JSONObject().apply {
                put("pageSize", p.pageSize)
                put("orientation", p.orientation)
                put("columns", p.columns)
                put("rows", p.rows)
                put("marginTopInch", p.marginTopInch.toDouble())
                put("marginBottomInch", p.marginBottomInch.toDouble())
                put("marginLeftInch", p.marginLeftInch.toDouble())
                put("marginRightInch", p.marginRightInch.toDouble())
                put("horizontalGapMm", p.horizontalGapMm.toDouble())
                put("verticalGapMm", p.verticalGapMm.toDouble())
                put("cuttingLineStyle", p.cuttingLineStyle)
                put("cuttingLineColorHex", p.cuttingLineColorHex)
            }
            json.put("page", pObj)

            // Scope
            val s = state.scope
            val sObj = JSONObject().apply {
                put("scopeType", s.scopeType)
                put("sortBy", s.sortBy)
                put("autoNumberBenches", s.autoNumberBenches)
                put("startBenchNumber", s.startBenchNumber)

                val classArr = JSONArray()
                s.selectedClasses.forEach { classArr.put(it) }
                put("selectedClasses", classArr)

                val studentArr = JSONArray()
                s.selectedStudentIds.forEach { studentArr.put(it) }
                put("selectedStudentIds", studentArr)
            }
            json.put("scope", sObj)

            prefs.edit().putString(KEY_STATE, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadState(
        context: Context,
        defaultSchoolName: String = "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়",
        defaultAddress: String = "আলফাডাঙ্গা, ফরিদপুর।"
    ): SeatPlanMakerState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null) ?: return SeatPlanMakerState(
            schoolName = defaultSchoolName,
            schoolAddress = defaultAddress
        )

        return try {
            val json = JSONObject(raw)
            val schoolName = json.optString("schoolName", defaultSchoolName)
            val schoolAddress = json.optString("schoolAddress", defaultAddress)
            val examName = json.optString("examName", "২য় প্রান্তিক মূল্যায়ন - ২০২৬")
            val fontId = json.optString("fontId", "noto_serif_bengali")

            val fObj = json.optJSONObject("fields")
            val fields = if (fObj != null) {
                SeatPlanFieldConfig(
                    showSchoolName = fObj.optBoolean("showSchoolName", true),
                    showSchoolAddress = fObj.optBoolean("showSchoolAddress", true),
                    showExamName = fObj.optBoolean("showExamName", true),
                    showSeatPlanTitle = fObj.optBoolean("showSeatPlanTitle", true),
                    seatPlanTitleText = fObj.optString("seatPlanTitleText", "আসন বিন্যাস"),
                    showStudentName = fObj.optBoolean("showStudentName", true),
                    showStudentClass = fObj.optBoolean("showStudentClass", true),
                    showRollNumber = fObj.optBoolean("showRollNumber", true),
                    showSection = fObj.optBoolean("showSection", false),
                    showRoomNumber = fObj.optBoolean("showRoomNumber", false),
                    roomNumberText = fObj.optString("roomNumberText", ""),
                    showBenchNumber = fObj.optBoolean("showBenchNumber", false),
                    benchPrefix = fObj.optString("benchPrefix", "বেঞ্চ: "),
                    convertBanglaDigits = fObj.optBoolean("convertBanglaDigits", true),
                    classFormat = fObj.optString("classFormat", "SHORT"),
                    headerFontSizeScale = fObj.optDouble("headerFontSizeScale", 1.0).toFloat(),
                    contentFontSizeScale = fObj.optDouble("contentFontSizeScale", 1.0).toFloat(),
                    isSchoolNameBold = fObj.optBoolean("isSchoolNameBold", true),
                    isTitleBold = fObj.optBoolean("isTitleBold", true),
                    cardCornerRadiusDp = fObj.optDouble("cardCornerRadiusDp", 12.0).toFloat(),
                    cardBorderWidthDp = fObj.optDouble("cardBorderWidthDp", 1.5).toFloat(),
                    cardBorderStyle = fObj.optString("cardBorderStyle", "solid")
                )
            } else {
                SeatPlanFieldConfig()
            }

            val pObj = json.optJSONObject("page")
            val page = if (pObj != null) {
                SeatPlanPageConfig(
                    pageSize = pObj.optString("pageSize", "A4"),
                    orientation = pObj.optString("orientation", "portrait"),
                    columns = pObj.optInt("columns", 2),
                    rows = pObj.optInt("rows", 6),
                    marginTopInch = pObj.optDouble("marginTopInch", 0.25).toFloat(),
                    marginBottomInch = pObj.optDouble("marginBottomInch", 0.25).toFloat(),
                    marginLeftInch = pObj.optDouble("marginLeftInch", 0.25).toFloat(),
                    marginRightInch = pObj.optDouble("marginRightInch", 0.25).toFloat(),
                    horizontalGapMm = pObj.optDouble("horizontalGapMm", 2.0).toFloat(),
                    verticalGapMm = pObj.optDouble("verticalGapMm", 2.0).toFloat(),
                    cuttingLineStyle = pObj.optString("cuttingLineStyle", "dotted"),
                    cuttingLineColorHex = pObj.optString("cuttingLineColorHex", "#94A3B8")
                )
            } else {
                SeatPlanPageConfig()
            }

            val sObj = json.optJSONObject("scope")
            val scope = if (sObj != null) {
                val selectedClasses = mutableListOf<String>()
                val cArr = sObj.optJSONArray("selectedClasses")
                if (cArr != null) {
                    for (i in 0 until cArr.length()) selectedClasses.add(cArr.getString(i))
                }

                val selectedStudentIds = mutableListOf<String>()
                val sArr = sObj.optJSONArray("selectedStudentIds")
                if (sArr != null) {
                    for (i in 0 until sArr.length()) selectedStudentIds.add(sArr.getString(i))
                }

                SeatPlanScopeConfig(
                    scopeType = sObj.optString("scopeType", "ALL"),
                    selectedClasses = selectedClasses,
                    selectedStudentIds = selectedStudentIds,
                    sortBy = sObj.optString("sortBy", "CLASS_AND_ROLL"),
                    autoNumberBenches = sObj.optBoolean("autoNumberBenches", false),
                    startBenchNumber = sObj.optInt("startBenchNumber", 1)
                )
            } else {
                SeatPlanScopeConfig()
            }

            SeatPlanMakerState(
                schoolName = schoolName,
                schoolAddress = schoolAddress,
                examName = examName,
                fields = fields,
                page = page,
                scope = scope,
                fontId = fontId
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SeatPlanMakerState(
                schoolName = defaultSchoolName,
                schoolAddress = defaultAddress
            )
        }
    }
}
