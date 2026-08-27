package com.example.util

import android.content.Context
import com.example.data.local.entity.CustomFieldEntity
import org.json.JSONArray
import org.json.JSONObject

data class FormFieldItem(
    val key: String,
    val label: String,
    val isCustom: Boolean = false,
    val isVisible: Boolean = true
)

data class FormGroupItem(
    val id: String,
    val title: String,
    val fields: List<FormFieldItem>
)

object FormLayoutManager {
    private const val PREFS_NAME = "anwesha_form_layout"
    private const val KEY_LAYOUT_JSON = "student_form_layout_json"

    val standardFieldDefinitions = mapOf(
        "photo" to "ছবি (Passport Photo)",
        "name" to "শিক্ষার্থীর নাম *",
        "studentClass" to "শ্রেণি",
        "rollNumber" to "রোল নম্বর",
        "gender" to "লিঙ্গ",
        "birthDate" to "জন্মতারিখ",
        "birthRegNumber" to "জন্ম নিবন্ধন নম্বর",
        "id" to "শিক্ষার্থী আইডি",
        "fatherName" to "পিতার নাম",
        "motherName" to "মাতার নাম",
        "mobile" to "মোবাইল নম্বর",
        "village" to "গ্রাম (Village)",
        "address" to "ঠিকানা (Address)",
        "academicYear" to "শিক্ষাবর্ষ",
        "isSpecialNeeds" to "বিশেষ চাহিদাসম্পন্ন"
    )

    fun getDefaultGroups(customFields: List<CustomFieldEntity> = emptyList()): List<FormGroupItem> {
        val basicFields = listOf(
            FormFieldItem("photo", standardFieldDefinitions["photo"]!!),
            FormFieldItem("name", standardFieldDefinitions["name"]!!),
            FormFieldItem("studentClass", standardFieldDefinitions["studentClass"]!!),
            FormFieldItem("rollNumber", standardFieldDefinitions["rollNumber"]!!),
            FormFieldItem("gender", standardFieldDefinitions["gender"]!!),
            FormFieldItem("birthDate", standardFieldDefinitions["birthDate"]!!),
            FormFieldItem("birthRegNumber", standardFieldDefinitions["birthRegNumber"]!!),
            FormFieldItem("id", standardFieldDefinitions["id"]!!),
            FormFieldItem("isSpecialNeeds", standardFieldDefinitions["isSpecialNeeds"]!!)
        )

        val guardianFields = listOf(
            FormFieldItem("fatherName", standardFieldDefinitions["fatherName"]!!),
            FormFieldItem("motherName", standardFieldDefinitions["motherName"]!!),
            FormFieldItem("mobile", standardFieldDefinitions["mobile"]!!)
        )

        val addressFields = listOf(
            FormFieldItem("village", standardFieldDefinitions["village"]!!),
            FormFieldItem("address", standardFieldDefinitions["address"]!!),
            FormFieldItem("academicYear", standardFieldDefinitions["academicYear"]!!)
        )

        val customFieldItems = customFields.filter { !it.isCalculated }.map {
            FormFieldItem(key = it.id, label = it.name, isCustom = true)
        }

        val groups = mutableListOf(
            FormGroupItem("grp_basic", "মৌলিক ও ব্যক্তিগত তথ্য", basicFields),
            FormGroupItem("grp_guardian", "অভিভাবক ও পরিবার", guardianFields),
            FormGroupItem("grp_address", "যোগাযোগ ও ঠিকানা", addressFields)
        )

        if (customFieldItems.isNotEmpty()) {
            groups.add(FormGroupItem("grp_custom", "অতিরিক্ত কাস্টম তথ্য", customFieldItems))
        }

        return groups
    }

    fun loadGroups(context: Context, customFields: List<CustomFieldEntity>): List<FormGroupItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_LAYOUT_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            return getDefaultGroups(customFields)
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            val groups = mutableListOf<FormGroupItem>()
            val handledFieldKeys = mutableSetOf<String>()

            for (i in 0 until jsonArray.length()) {
                val groupObj = jsonArray.getJSONObject(i)
                val id = groupObj.optString("id", "grp_$i")
                val title = groupObj.optString("title", "গ্রুপ")
                val fieldsArr = groupObj.optJSONArray("fields") ?: JSONArray()

                val fieldList = mutableListOf<FormFieldItem>()
                for (j in 0 until fieldsArr.length()) {
                    val fObj = fieldsArr.getJSONObject(j)
                    val key = fObj.optString("key")
                    val isCustom = fObj.optBoolean("isCustom", false)
                    val isVisible = fObj.optBoolean("isVisible", true)

                    val label = if (isCustom) {
                        customFields.find { it.id == key }?.name ?: fObj.optString("label", key)
                    } else {
                        standardFieldDefinitions[key] ?: fObj.optString("label", key)
                    }

                    if (isCustom) {
                        // verify custom field still exists
                        val exists = customFields.any { it.id == key && !it.isCalculated }
                        if (exists) {
                            fieldList.add(FormFieldItem(key, label, isCustom = true, isVisible = isVisible))
                            handledFieldKeys.add(key)
                        }
                    } else {
                        if (standardFieldDefinitions.containsKey(key)) {
                            fieldList.add(FormFieldItem(key, label, isCustom = false, isVisible = isVisible))
                            handledFieldKeys.add(key)
                        }
                    }
                }
                groups.add(FormGroupItem(id, title, fieldList))
            }

            // Append any missing standard fields to the first group
            val missingStandard = standardFieldDefinitions.keys.filter { !handledFieldKeys.contains(it) }
            if (missingStandard.isNotEmpty() && groups.isNotEmpty()) {
                val firstGroup = groups.first()
                val updatedFields = firstGroup.fields.toMutableList()
                missingStandard.forEach { k ->
                    updatedFields.add(FormFieldItem(k, standardFieldDefinitions[k]!!, isCustom = false))
                }
                groups[0] = firstGroup.copy(fields = updatedFields)
            }

            // Append any new unhandled custom fields to the last group (or create custom group)
            val missingCustom = customFields.filter { !it.isCalculated && !handledFieldKeys.contains(it.id) }
            if (missingCustom.isNotEmpty()) {
                val customGroupIndex = groups.indexOfFirst { it.id == "grp_custom" }
                if (customGroupIndex >= 0) {
                    val customGroup = groups[customGroupIndex]
                    val updatedFields = customGroup.fields.toMutableList()
                    missingCustom.forEach { cf ->
                        updatedFields.add(FormFieldItem(cf.id, cf.name, isCustom = true))
                    }
                    groups[customGroupIndex] = customGroup.copy(fields = updatedFields)
                } else {
                    groups.add(
                        FormGroupItem(
                            "grp_custom",
                            "অতিরিক্ত কাস্টম তথ্য",
                            missingCustom.map { FormFieldItem(it.id, it.name, isCustom = true) }
                        )
                    )
                }
            }

            return groups
        } catch (e: Exception) {
            e.printStackTrace()
            return getDefaultGroups(customFields)
        }
    }

    fun saveGroups(context: Context, groups: List<FormGroupItem>) {
        try {
            val jsonArray = JSONArray()
            for (g in groups) {
                val groupObj = JSONObject()
                groupObj.put("id", g.id)
                groupObj.put("title", g.title)
                val fieldsArr = JSONArray()
                for (f in g.fields) {
                    val fObj = JSONObject()
                    fObj.put("key", f.key)
                    fObj.put("label", f.label)
                    fObj.put("isCustom", f.isCustom)
                    fObj.put("isVisible", f.isVisible)
                    fieldsArr.put(fObj)
                }
                groupObj.put("fields", fieldsArr)
                jsonArray.put(groupObj)
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAYOUT_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAYOUT_JSON).apply()
    }
}
