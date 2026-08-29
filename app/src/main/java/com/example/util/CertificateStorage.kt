package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.model.CertificateMakerState
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object CertificateStorage {
    private const val PREFS_NAME = "certificate_maker_prefs"
    private const val KEY_STATE = "certificate_state_json"

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

    fun getCurrentIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun formatDateToBanglaDisplay(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate
        return "${BanglaUtils.toBanglaDigits(parts[2])}/${BanglaUtils.toBanglaDigits(parts[1])}/${BanglaUtils.toBanglaDigits(parts[0])}"
    }

    fun saveState(context: Context, state: CertificateMakerState) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("schoolName", state.schoolName)
                put("upazila", state.upazila)
                put("district", state.district)
                put("estYear", state.estYear)
                put("govtHeader1", state.govtHeader1)
                put("govtHeader2", state.govtHeader2)
                put("govtHeader3", state.govtHeader3)
                put("certificateTitle", state.certificateTitle)
                put("issueDate", state.issueDate)
                put("sessionYear", state.sessionYear)
                put("serialFormatMode", state.serialFormatMode)
                put("customSerialPrefix", state.customSerialPrefix)
                put("autoIncrementStart", state.autoIncrementStart)
                put("studyTense", state.studyTense)
                put("characterRemark", state.characterRemark)
                put("wishRemark", state.wishRemark)
                put("headTeacherTitle", state.headTeacherTitle)
                put("showHeadTeacherSignature", state.showHeadTeacherSignature)
                put("headTeacherSignatureBase64", state.headTeacherSignatureBase64)
                put("showStudentSignatureBox", state.showStudentSignatureBox)
                put("showIssuerSignatureBox", state.showIssuerSignatureBox)
                put("pageSize", state.pageSize)
                put("orientation", state.orientation)
                put("marginLeftInch", state.marginLeftInch.toDouble())
                put("marginRightInch", state.marginRightInch.toDouble())
                put("marginTopInch", state.marginTopInch.toDouble())
                put("marginBottomInch", state.marginBottomInch.toDouble())
                put("borderStyle", state.borderStyle)
                put("showWatermark", state.showWatermark)
                put("showGovtEmblems", state.showGovtEmblems)
                put("showCounterfoil", state.showCounterfoil)
                put("fontStyle", state.fontStyle)
                put("scope", state.scope)

                val classArr = JSONArray()
                state.selectedClasses.forEach { classArr.put(it) }
                put("selectedClasses", classArr)

                val stuArr = JSONArray()
                state.selectedStudentIds.forEach { stuArr.put(it) }
                put("selectedStudentIds", stuArr)

                val manualObj = JSONObject()
                state.manualSerialMap.forEach { (k, v) -> manualObj.put(k, v) }
                put("manualSerialMap", manualObj)
            }
            prefs.edit().putString(KEY_STATE, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadState(
        context: Context,
        defaultSchoolName: String = "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়",
        defaultAddress: String = "আলফাডাঙ্গা, ফরিদপুর।"
    ): CertificateMakerState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null)

        val defaultUpazila = if (defaultAddress.contains("আলফাডাঙ্গা")) "আলফাডাঙ্গা" else defaultAddress.split(",").getOrNull(0)?.trim() ?: "আলফাডাঙ্গা"
        val defaultDistrict = if (defaultAddress.contains("ফরিদপুর")) "ফরিদপুর" else defaultAddress.split(",").getOrNull(1)?.replace("।", "")?.trim() ?: "ফরিদপুর"

        if (raw.isNullOrBlank()) {
            return CertificateMakerState(
                schoolName = defaultSchoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" },
                upazila = defaultUpazila,
                district = defaultDistrict,
                issueDate = getCurrentIsoDate()
            )
        }

        return try {
            val json = JSONObject(raw)

            val selectedClasses = mutableListOf<String>()
            val classArr = json.optJSONArray("selectedClasses")
            if (classArr != null) {
                for (i in 0 until classArr.length()) selectedClasses.add(classArr.getString(i))
            }

            val selectedStudentIds = mutableListOf<String>()
            val stuArr = json.optJSONArray("selectedStudentIds")
            if (stuArr != null) {
                for (i in 0 until stuArr.length()) selectedStudentIds.add(stuArr.getString(i))
            }

            val manualSerialMap = mutableMapOf<String, String>()
            val manualObj = json.optJSONObject("manualSerialMap")
            if (manualObj != null) {
                val keys = manualObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    manualSerialMap[k] = manualObj.optString(k)
                }
            }

            CertificateMakerState(
                schoolName = json.optString("schoolName", defaultSchoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" }),
                upazila = json.optString("upazila", defaultUpazila),
                district = json.optString("district", defaultDistrict),
                estYear = json.optString("estYear", "১৯৭৩"),
                govtHeader1 = json.optString("govtHeader1", "গণপ্রজাতন্ত্রী বাংলাদেশ সরকার"),
                govtHeader2 = json.optString("govtHeader2", "প্রাথমিক শিক্ষা অধিদপ্তর"),
                govtHeader3 = json.optString("govtHeader3", "প্রাথমিক ও গণশিক্ষা মন্ত্রণালয়"),
                certificateTitle = json.optString("certificateTitle", "প্রত্যয়নপত্র"),
                issueDate = json.optString("issueDate", getCurrentIsoDate()),
                sessionYear = json.optString("sessionYear", "২০২৬"),
                serialFormatMode = json.optString("serialFormatMode", "YEAR_CLASS_ROLL"),
                customSerialPrefix = json.optString("customSerialPrefix", "PR-2026-"),
                autoIncrementStart = json.optInt("autoIncrementStart", 1),
                studyTense = json.optString("studyTense", "PAST"),
                characterRemark = json.optString("characterRemark", "তার স্বভাব চরিত্র ভালো।"),
                wishRemark = json.optString("wishRemark", "আমি তার সর্বাঙ্গীণ সাফল্য কামনা করি।"),
                headTeacherTitle = json.optString("headTeacherTitle", "(প্রধান শিক্ষক)"),
                showHeadTeacherSignature = json.optBoolean("showHeadTeacherSignature", true),
                headTeacherSignatureBase64 = json.optString("headTeacherSignatureBase64", ""),
                showStudentSignatureBox = json.optBoolean("showStudentSignatureBox", true),
                showIssuerSignatureBox = json.optBoolean("showIssuerSignatureBox", true),
                pageSize = json.optString("pageSize", "Legal"),
                orientation = json.optString("orientation", "landscape"),
                marginLeftInch = json.optDouble("marginLeftInch", 0.25).toFloat(),
                marginRightInch = json.optDouble("marginRightInch", 0.25).toFloat(),
                marginTopInch = json.optDouble("marginTopInch", 0.25).toFloat(),
                marginBottomInch = json.optDouble("marginBottomInch", 0.25).toFloat(),
                borderStyle = json.optString("borderStyle", "ornate"),
                showWatermark = json.optBoolean("showWatermark", true),
                showGovtEmblems = json.optBoolean("showGovtEmblems", true),
                showCounterfoil = json.optBoolean("showCounterfoil", true),
                fontStyle = json.optString("fontStyle", "serif"),
                scope = json.optString("scope", "all"),
                selectedClasses = selectedClasses,
                selectedStudentIds = selectedStudentIds,
                manualSerialMap = manualSerialMap
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CertificateMakerState(
                schoolName = defaultSchoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" },
                upazila = defaultUpazila,
                district = defaultDistrict,
                issueDate = getCurrentIsoDate()
            )
        }
    }
}
