package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.util.FormulaEvaluator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ==========================================
// 1. DATA MODELS FOR STUDENT CARD & LIST VIEW
// ==========================================

enum class DisplayAreaType(val labelBangla: String) {
    HEADER("হেডার এরিয়া (Header)"),
    SECONDARY_ROW("দ্বিতীয় সারি (Secondary Row)"),
    THIRD_ROW("তৃতীয় সারি (Third Row)"),
    BADGE_AREA("ব্যাজ এরিয়া (Badge Area)"),
    AVATAR_AREA("ছবি / অবতার এরিয়া (Avatar)")
}

data class ConditionalRule(
    val fieldKey: String = "studentClass",
    val operator: String = "EQUALS", // EQUALS, NOT_EQUALS, CONTAINS, IS_TRUE, IS_FALSE, GREATER_THAN, LESS_THAN
    val targetValue: String = ""
) {
    fun isMet(student: StudentEntity, customFields: List<CustomFieldEntity>): Boolean {
        val value = FormulaEvaluator.getFieldValue(student, fieldKey, customFields).trim()
        val target = targetValue.trim()
        return when (operator) {
            "EQUALS" -> value.equals(target, ignoreCase = true)
            "NOT_EQUALS" -> !value.equals(target, ignoreCase = true)
            "CONTAINS" -> value.contains(target, ignoreCase = true)
            "IS_TRUE" -> value.equals("true", ignoreCase = true) || value == "হ্যাঁ" || value == "1"
            "IS_FALSE" -> value.equals("false", ignoreCase = true) || value == "না" || value == "0" || value.isBlank()
            "GREATER_THAN" -> {
                val numVal = value.toDoubleOrNull()
                val numTarget = target.toDoubleOrNull()
                if (numVal != null && numTarget != null) numVal > numTarget else value > target
            }
            "LESS_THAN" -> {
                val numVal = value.toDoubleOrNull()
                val numTarget = target.toDoubleOrNull()
                if (numVal != null && numTarget != null) numVal < numTarget else value < target
            }
            else -> true
        }
    }
}

data class DisplayFieldConfig(
    val key: String,
    val label: String,
    val showLabel: Boolean = true,
    val customPrefix: String = "",
    val customSuffix: String = "",
    val isVisible: Boolean = true,
    val hasCondition: Boolean = false,
    val condition: ConditionalRule? = null
)

data class DisplayAreaConfig(
    val areaType: DisplayAreaType,
    val fields: List<DisplayFieldConfig> = emptyList(),
    val isCombined: Boolean = false,
    val separator: String = " | "
)

data class CardActionConfig(
    val id: String, // "view", "edit", "delete", "call", "whatsapp", "print", "id_card", "select"
    val label: String,
    val iconName: String,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0
)

data class CardVisualConfig(
    val viewMode: String = "CARD", // "CARD" or "LIST"
    val density: String = "NORMAL", // "COMPACT", "NORMAL", "SPACIOUS"
    val showAvatar: Boolean = true,
    val avatarType: String = "PHOTO_OR_INITIALS", // "PHOTO_OR_INITIALS", "INITIALS_ONLY", "NONE"
    val showBadges: Boolean = true,
    val showIcons: Boolean = true,
    val showLabels: Boolean = true,
    val cornerRadiusDp: Int = 14, // 0, 8, 14, 20, 28
    val borderWidthDp: Int = 0, // 0, 1, 2
    val elevationDp: Int = 2, // 0, 1, 2, 4
    val spacingDp: Int = 10, // 6, 10, 14, 18
    val colorThemeHex: String = "DEFAULT" // "DEFAULT", "#1E88E5", "#00897B", "#5E35B1", "#E65100", "#2E7D32", "#37474F"
)

data class QuickFilterItem(
    val key: String, // "class", "gender", "status", "village", or custom field ID
    val label: String,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0
)

data class RolePermissionConfig(
    val role: String, // "Admin", "Teacher", "Staff", "Viewer"
    val viewableFieldKeys: List<String> = emptyList(), // empty means all
    val editableFieldKeys: List<String> = emptyList(),
    val canDelete: Boolean = true,
    val canManageCustomFields: Boolean = true,
    val canCustomizeLayout: Boolean = true
)

data class StudentSavedView(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isDefault: Boolean = false,
    val headerArea: DisplayAreaConfig,
    val secondaryArea: DisplayAreaConfig,
    val thirdArea: DisplayAreaConfig,
    val badgeArea: DisplayAreaConfig,
    val avatarArea: DisplayAreaConfig,
    val actions: List<CardActionConfig>,
    val visual: CardVisualConfig,
    val quickFilters: List<QuickFilterItem>,
    val filterClass: String? = null,
    val filterGender: String? = null,
    val filterStatus: String? = "Current",
    val filterVillage: String? = null
)

// ==========================================
// 2. CONFIGURATION STORAGE & PRESETS
// ==========================================

object StudentViewConfigManager {
    private const val PREFS_NAME = "anwesha_student_view_config"
    private const val KEY_SAVED_VIEWS = "saved_views_json"
    private const val KEY_ACTIVE_VIEW_ID = "active_view_id"
    private const val KEY_PERMISSIONS = "role_permissions_json"

    val standardFieldDefinitions = mapOf(
        "name" to "শিক্ষার্থীর নাম",
        "studentClass" to "শ্রেণি",
        "rollNumber" to "রোল নম্বর",
        "id" to "শিক্ষার্থী আইডি",
        "gender" to "লিঙ্গ",
        "fatherName" to "পিতার নাম",
        "motherName" to "মাতার নাম",
        "mobile" to "মোবাইল নম্বর",
        "village" to "গ্রাম (Village)",
        "address" to "ঠিকানা",
        "academicYear" to "শিক্ষাবর্ষ",
        "birthDate" to "জন্মতারিখ",
        "age" to "বয়স (হিসাবকৃত)",
        "birthRegNumber" to "জন্ম নিবন্ধন নম্বর",
        "category" to "শিক্ষার্থীর ধরণ (অভ্যন্তরীণ/বহিরাগত)",
        "status" to "স্ট্যাটাস (Current/Former)",
        "isSpecialNeeds" to "বিশেষ চাহিদা"
    )

    fun getDefaultActions(): List<CardActionConfig> = listOf(
        CardActionConfig("view", "বিস্তারিত দেখুন", "Visibility", isEnabled = true, orderIndex = 0),
        CardActionConfig("edit", "সম্পাদনা", "Edit", isEnabled = true, orderIndex = 1),
        CardActionConfig("delete", "মুছুন", "Delete", isEnabled = true, orderIndex = 2),
        CardActionConfig("call", "কল করুন", "Phone", isEnabled = true, orderIndex = 3),
        CardActionConfig("whatsapp", "হোয়াটসঅ্যাপ", "Chat", isEnabled = true, orderIndex = 4),
        CardActionConfig("id_card", "আইডি কার্ড", "Badge", isEnabled = true, orderIndex = 5),
        CardActionConfig("print", "প্রিন্ট করুন", "Print", isEnabled = false, orderIndex = 6)
    )

    fun getDefaultQuickFilters(customFields: List<CustomFieldEntity> = emptyList()): List<QuickFilterItem> {
        val list = mutableListOf(
            QuickFilterItem("class", "শ্রেণি ফিল্টার", isEnabled = true, orderIndex = 0),
            QuickFilterItem("status", "স্ট্যাটাস ফিল্টার", isEnabled = true, orderIndex = 1),
            QuickFilterItem("gender", "লিঙ্গ ফিল্টার", isEnabled = true, orderIndex = 2),
            QuickFilterItem("village", "গ্রাম ফিল্টার", isEnabled = true, orderIndex = 3)
        )
        customFields.filter { !it.isCalculated }.forEachIndexed { index, cf ->
            list.add(
                QuickFilterItem(
                    key = "cf_${cf.id}",
                    label = cf.name,
                    isEnabled = false,
                    orderIndex = 4 + index
                )
            )
        }
        return list
    }

    fun getDefaultPresetView(customFields: List<CustomFieldEntity> = emptyList()): StudentSavedView {
        val headerFields = listOf(
            DisplayFieldConfig(key = "name", label = "শিক্ষার্থীর নাম", showLabel = false)
        )
        val secondaryFields = listOf(
            DisplayFieldConfig(key = "studentClass", label = "শ্রেণি", showLabel = false),
            DisplayFieldConfig(key = "rollNumber", label = "রোল", showLabel = true, customPrefix = "রোল: "),
            DisplayFieldConfig(key = "id", label = "আইডি", showLabel = true, customPrefix = "আইডি: ")
        )
        val thirdFields = listOf(
            DisplayFieldConfig(key = "fatherName", label = "পিতার নাম", showLabel = true, customPrefix = "পিতা: "),
            DisplayFieldConfig(key = "village", label = "গ্রাম", showLabel = true, customPrefix = "গ্রাম: ")
        )
        val badgeFields = listOf(
            DisplayFieldConfig(key = "category", label = "ধরণ", showLabel = false),
            DisplayFieldConfig(key = "status", label = "স্ট্যাটাস", showLabel = false),
            DisplayFieldConfig(
                key = "isSpecialNeeds",
                label = "বিশেষ চাহিদা",
                showLabel = false,
                hasCondition = true,
                condition = ConditionalRule("isSpecialNeeds", "IS_TRUE", "")
            )
        )
        val avatarFields = listOf(
            DisplayFieldConfig(key = "photo", label = "ছবি / অবতার", showLabel = false)
        )

        return StudentSavedView(
            id = "default_view",
            name = "ডিফল্ট স্ট্যান্ডার্ড ভিউ",
            isDefault = true,
            headerArea = DisplayAreaConfig(DisplayAreaType.HEADER, headerFields, isCombined = false),
            secondaryArea = DisplayAreaConfig(DisplayAreaType.SECONDARY_ROW, secondaryFields, isCombined = true, separator = " | "),
            thirdArea = DisplayAreaConfig(DisplayAreaType.THIRD_ROW, thirdFields, isCombined = true, separator = " | "),
            badgeArea = DisplayAreaConfig(DisplayAreaType.BADGE_AREA, badgeFields, isCombined = false),
            avatarArea = DisplayAreaConfig(DisplayAreaType.AVATAR_AREA, avatarFields, isCombined = false),
            actions = getDefaultActions(),
            visual = CardVisualConfig(),
            quickFilters = getDefaultQuickFilters(customFields)
        )
    }

    fun getCompactGuardianPreset(customFields: List<CustomFieldEntity> = emptyList()): StudentSavedView {
        val headerFields = listOf(
            DisplayFieldConfig(key = "name", label = "শিক্ষার্থীর নাম", showLabel = false),
            DisplayFieldConfig(key = "studentClass", label = "শ্রেণি", showLabel = false, customPrefix = " (", customSuffix = ")")
        )
        val secondaryFields = listOf(
            DisplayFieldConfig(key = "fatherName", label = "পিতা", showLabel = true, customPrefix = "পিতা: "),
            DisplayFieldConfig(key = "mobile", label = "মোবাইল", showLabel = true, customPrefix = " 📞 ")
        )
        val thirdFields = listOf(
            DisplayFieldConfig(key = "motherName", label = "মাতা", showLabel = true, customPrefix = "মাতা: "),
            DisplayFieldConfig(key = "village", label = "গ্রাম", showLabel = true, customPrefix = "গ্রাম: ")
        )
        val badgeFields = listOf(
            DisplayFieldConfig(key = "status", label = "স্ট্যাটাস", showLabel = false)
        )

        return StudentSavedView(
            id = "guardian_contact_view",
            name = "অভিভাবক যোগাযোগ ভিউ",
            isDefault = false,
            headerArea = DisplayAreaConfig(DisplayAreaType.HEADER, headerFields, isCombined = true, separator = ""),
            secondaryArea = DisplayAreaConfig(DisplayAreaType.SECONDARY_ROW, secondaryFields, isCombined = true, separator = " | "),
            thirdArea = DisplayAreaConfig(DisplayAreaType.THIRD_ROW, thirdFields, isCombined = true, separator = " | "),
            badgeArea = DisplayAreaConfig(DisplayAreaType.BADGE_AREA, badgeFields, isCombined = false),
            avatarArea = DisplayAreaConfig(DisplayAreaType.AVATAR_AREA, listOf(DisplayFieldConfig("photo", "ছবি")), isCombined = false),
            actions = listOf(
                CardActionConfig("call", "কল করুন", "Phone", isEnabled = true, orderIndex = 0),
                CardActionConfig("whatsapp", "হোয়াটসঅ্যাপ", "Chat", isEnabled = true, orderIndex = 1),
                CardActionConfig("view", "বিস্তারিত", "Visibility", isEnabled = true, orderIndex = 2),
                CardActionConfig("edit", "সম্পাদনা", "Edit", isEnabled = true, orderIndex = 3)
            ),
            visual = CardVisualConfig(viewMode = "CARD", density = "NORMAL", colorThemeHex = "#00897B"),
            quickFilters = getDefaultQuickFilters(customFields)
        )
    }

    fun getListViewPreset(customFields: List<CustomFieldEntity> = emptyList()): StudentSavedView {
        val headerFields = listOf(
            DisplayFieldConfig(key = "rollNumber", label = "রোল", showLabel = false, customSuffix = ". "),
            DisplayFieldConfig(key = "name", label = "নাম", showLabel = false)
        )
        val secondaryFields = listOf(
            DisplayFieldConfig(key = "studentClass", label = "শ্রেণি", showLabel = false),
            DisplayFieldConfig(key = "mobile", label = "মোবাইল", showLabel = false),
            DisplayFieldConfig(key = "village", label = "গ্রাম", showLabel = false)
        )
        return StudentSavedView(
            id = "table_list_view",
            name = "সংক্ষিপ্ত তালিকা (List Table)",
            isDefault = false,
            headerArea = DisplayAreaConfig(DisplayAreaType.HEADER, headerFields, isCombined = true, separator = ""),
            secondaryArea = DisplayAreaConfig(DisplayAreaType.SECONDARY_ROW, secondaryFields, isCombined = true, separator = " • "),
            thirdArea = DisplayAreaConfig(DisplayAreaType.THIRD_ROW, emptyList()),
            badgeArea = DisplayAreaConfig(DisplayAreaType.BADGE_AREA, listOf(DisplayFieldConfig("category", "ধরণ"))),
            avatarArea = DisplayAreaConfig(DisplayAreaType.AVATAR_AREA, emptyList()),
            actions = listOf(
                CardActionConfig("view", "ভিউ", "Visibility", isEnabled = true),
                CardActionConfig("edit", "এডিট", "Edit", isEnabled = true)
            ),
            visual = CardVisualConfig(viewMode = "LIST", density = "COMPACT", showAvatar = false, cornerRadiusDp = 6, elevationDp = 1),
            quickFilters = getDefaultQuickFilters(customFields)
        )
    }

    // ==========================================
    // 3. PERSISTENCE IN SHARED PREFERENCES
    // ==========================================

    fun loadAllSavedViews(context: Context, customFields: List<CustomFieldEntity>): List<StudentSavedView> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SAVED_VIEWS, null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = listOf(
                getDefaultPresetView(customFields),
                getCompactGuardianPreset(customFields),
                getListViewPreset(customFields)
            )
            saveAllViews(context, defaults)
            return defaults
        }

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<StudentSavedView>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(parseSavedViewFromJson(obj, customFields))
            }
            if (list.isEmpty()) {
                listOf(getDefaultPresetView(customFields))
            } else list
        } catch (e: Exception) {
            listOf(getDefaultPresetView(customFields))
        }
    }

    fun saveAllViews(context: Context, views: List<StudentSavedView>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        views.forEach { v ->
            array.put(savedViewToJson(v))
        }
        prefs.edit().putString(KEY_SAVED_VIEWS, array.toString()).apply()
    }

    fun getActiveViewId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_VIEW_ID, "default_view") ?: "default_view"
    }

    fun setActiveViewId(context: Context, viewId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_VIEW_ID, viewId).apply()
    }

    fun loadActiveView(context: Context, customFields: List<CustomFieldEntity>): StudentSavedView {
        val allViews = loadAllSavedViews(context, customFields)
        val activeId = getActiveViewId(context)
        return allViews.find { it.id == activeId }
            ?: allViews.find { it.isDefault }
            ?: allViews.firstOrNull()
            ?: getDefaultPresetView(customFields)
    }

    fun saveActiveView(context: Context, updatedView: StudentSavedView, customFields: List<CustomFieldEntity>) {
        val allViews = loadAllSavedViews(context, customFields).toMutableList()
        val index = allViews.indexOfFirst { it.id == updatedView.id }
        if (index >= 0) {
            allViews[index] = updatedView
        } else {
            allViews.add(updatedView)
        }
        saveAllViews(context, allViews)
        setActiveViewId(context, updatedView.id)
    }

    // Role Permissions
    fun loadRolePermissions(context: Context): List<RolePermissionConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PERMISSIONS, null)
        if (json.isNullOrBlank()) {
            return listOf(
                RolePermissionConfig("Admin", canDelete = true, canManageCustomFields = true, canCustomizeLayout = true),
                RolePermissionConfig("Teacher", canDelete = false, canManageCustomFields = false, canCustomizeLayout = true),
                RolePermissionConfig("Staff", canDelete = false, canManageCustomFields = false, canCustomizeLayout = false),
                RolePermissionConfig("Viewer", canDelete = false, canManageCustomFields = false, canCustomizeLayout = false)
            )
        }
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<RolePermissionConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    RolePermissionConfig(
                        role = o.optString("role", "Admin"),
                        viewableFieldKeys = jsonArrayToList(o.optJSONArray("viewable")),
                        editableFieldKeys = jsonArrayToList(o.optJSONArray("editable")),
                        canDelete = o.optBoolean("canDelete", true),
                        canManageCustomFields = o.optBoolean("canManageCustomFields", true),
                        canCustomizeLayout = o.optBoolean("canCustomizeLayout", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRolePermissions(context: Context, permissions: List<RolePermissionConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        permissions.forEach { p ->
            val o = JSONObject()
            o.put("role", p.role)
            o.put("viewable", JSONArray(p.viewableFieldKeys))
            o.put("editable", JSONArray(p.editableFieldKeys))
            o.put("canDelete", p.canDelete)
            o.put("canManageCustomFields", p.canManageCustomFields)
            o.put("canCustomizeLayout", p.canCustomizeLayout)
            arr.put(o)
        }
        prefs.edit().putString(KEY_PERMISSIONS, arr.toString()).apply()
    }

    // JSON Serialization Helpers
    private fun savedViewToJson(v: StudentSavedView): JSONObject {
        val obj = JSONObject()
        obj.put("id", v.id)
        obj.put("name", v.name)
        obj.put("isDefault", v.isDefault)
        obj.put("headerArea", areaToJson(v.headerArea))
        obj.put("secondaryArea", areaToJson(v.secondaryArea))
        obj.put("thirdArea", areaToJson(v.thirdArea))
        obj.put("badgeArea", areaToJson(v.badgeArea))
        obj.put("avatarArea", areaToJson(v.avatarArea))

        val actionsArr = JSONArray()
        v.actions.forEach { a ->
            val actObj = JSONObject()
            actObj.put("id", a.id)
            actObj.put("label", a.label)
            actObj.put("iconName", a.iconName)
            actObj.put("isEnabled", a.isEnabled)
            actObj.put("orderIndex", a.orderIndex)
            actionsArr.put(actObj)
        }
        obj.put("actions", actionsArr)

        val visObj = JSONObject()
        visObj.put("viewMode", v.visual.viewMode)
        visObj.put("density", v.visual.density)
        visObj.put("showAvatar", v.visual.showAvatar)
        visObj.put("avatarType", v.visual.avatarType)
        visObj.put("showBadges", v.visual.showBadges)
        visObj.put("showIcons", v.visual.showIcons)
        visObj.put("showLabels", v.visual.showLabels)
        visObj.put("cornerRadiusDp", v.visual.cornerRadiusDp)
        visObj.put("borderWidthDp", v.visual.borderWidthDp)
        visObj.put("elevationDp", v.visual.elevationDp)
        visObj.put("spacingDp", v.visual.spacingDp)
        visObj.put("colorThemeHex", v.visual.colorThemeHex)
        obj.put("visual", visObj)

        val qfArr = JSONArray()
        v.quickFilters.forEach { q ->
            val qObj = JSONObject()
            qObj.put("key", q.key)
            qObj.put("label", q.label)
            qObj.put("isEnabled", q.isEnabled)
            qObj.put("orderIndex", q.orderIndex)
            qfArr.put(qObj)
        }
        obj.put("quickFilters", qfArr)

        obj.put("filterClass", v.filterClass)
        obj.put("filterGender", v.filterGender)
        obj.put("filterStatus", v.filterStatus)
        obj.put("filterVillage", v.filterVillage)
        return obj
    }

    private fun areaToJson(area: DisplayAreaConfig): JSONObject {
        val obj = JSONObject()
        obj.put("areaType", area.areaType.name)
        obj.put("isCombined", area.isCombined)
        obj.put("separator", area.separator)
        val fieldsArr = JSONArray()
        area.fields.forEach { f ->
            val fObj = JSONObject()
            fObj.put("key", f.key)
            fObj.put("label", f.label)
            fObj.put("showLabel", f.showLabel)
            fObj.put("customPrefix", f.customPrefix)
            fObj.put("customSuffix", f.customSuffix)
            fObj.put("isVisible", f.isVisible)
            fObj.put("hasCondition", f.hasCondition)
            if (f.condition != null) {
                val cObj = JSONObject()
                cObj.put("fieldKey", f.condition.fieldKey)
                cObj.put("operator", f.condition.operator)
                cObj.put("targetValue", f.condition.targetValue)
                fObj.put("condition", cObj)
            }
            fieldsArr.put(fObj)
        }
        obj.put("fields", fieldsArr)
        return obj
    }

    private fun parseSavedViewFromJson(obj: JSONObject, customFields: List<CustomFieldEntity>): StudentSavedView {
        val id = obj.optString("id", UUID.randomUUID().toString())
        val name = obj.optString("name", "কাস্টম ভিউ")
        val isDefault = obj.optBoolean("isDefault", false)

        val headerArea = parseAreaFromJson(obj.optJSONObject("headerArea"), DisplayAreaType.HEADER)
        val secondaryArea = parseAreaFromJson(obj.optJSONObject("secondaryArea"), DisplayAreaType.SECONDARY_ROW)
        val thirdArea = parseAreaFromJson(obj.optJSONObject("thirdArea"), DisplayAreaType.THIRD_ROW)
        val badgeArea = parseAreaFromJson(obj.optJSONObject("badgeArea"), DisplayAreaType.BADGE_AREA)
        val avatarArea = parseAreaFromJson(obj.optJSONObject("avatarArea"), DisplayAreaType.AVATAR_AREA)

        val actionsList = mutableListOf<CardActionConfig>()
        val actionsArr = obj.optJSONArray("actions")
        if (actionsArr != null) {
            for (i in 0 until actionsArr.length()) {
                val actObj = actionsArr.getJSONObject(i)
                actionsList.add(
                    CardActionConfig(
                        id = actObj.optString("id", "view"),
                        label = actObj.optString("label", "ভিউ"),
                        iconName = actObj.optString("iconName", "Visibility"),
                        isEnabled = actObj.optBoolean("isEnabled", true),
                        orderIndex = actObj.optInt("orderIndex", i)
                    )
                )
            }
        }
        if (actionsList.isEmpty()) actionsList.addAll(getDefaultActions())

        val visObj = obj.optJSONObject("visual")
        val visual = if (visObj != null) {
            CardVisualConfig(
                viewMode = visObj.optString("viewMode", "CARD"),
                density = visObj.optString("density", "NORMAL"),
                showAvatar = visObj.optBoolean("showAvatar", true),
                avatarType = visObj.optString("avatarType", "PHOTO_OR_INITIALS"),
                showBadges = visObj.optBoolean("showBadges", true),
                showIcons = visObj.optBoolean("showIcons", true),
                showLabels = visObj.optBoolean("showLabels", true),
                cornerRadiusDp = visObj.optInt("cornerRadiusDp", 14),
                borderWidthDp = visObj.optInt("borderWidthDp", 0),
                elevationDp = visObj.optInt("elevationDp", 2),
                spacingDp = visObj.optInt("spacingDp", 10),
                colorThemeHex = visObj.optString("colorThemeHex", "DEFAULT")
            )
        } else CardVisualConfig()

        val qfList = mutableListOf<QuickFilterItem>()
        val qfArr = obj.optJSONArray("quickFilters")
        if (qfArr != null) {
            for (i in 0 until qfArr.length()) {
                val qObj = qfArr.getJSONObject(i)
                qfList.add(
                    QuickFilterItem(
                        key = qObj.optString("key", "class"),
                        label = qObj.optString("label", "ফিল্টার"),
                        isEnabled = qObj.optBoolean("isEnabled", true),
                        orderIndex = qObj.optInt("orderIndex", i)
                    )
                )
            }
        }
        if (qfList.isEmpty()) qfList.addAll(getDefaultQuickFilters(customFields))

        return StudentSavedView(
            id = id,
            name = name,
            isDefault = isDefault,
            headerArea = headerArea,
            secondaryArea = secondaryArea,
            thirdArea = thirdArea,
            badgeArea = badgeArea,
            avatarArea = avatarArea,
            actions = actionsList,
            visual = visual,
            quickFilters = qfList,
            filterClass = if (obj.has("filterClass") && !obj.isNull("filterClass")) obj.getString("filterClass") else null,
            filterGender = if (obj.has("filterGender") && !obj.isNull("filterGender")) obj.getString("filterGender") else null,
            filterStatus = if (obj.has("filterStatus") && !obj.isNull("filterStatus")) obj.getString("filterStatus") else "Current",
            filterVillage = if (obj.has("filterVillage") && !obj.isNull("filterVillage")) obj.getString("filterVillage") else null
        )
    }

    private fun parseAreaFromJson(obj: JSONObject?, defaultType: DisplayAreaType): DisplayAreaConfig {
        if (obj == null) return DisplayAreaConfig(defaultType)
        val areaTypeStr = obj.optString("areaType", defaultType.name)
        val areaType = try { DisplayAreaType.valueOf(areaTypeStr) } catch (e: Exception) { defaultType }
        val isCombined = obj.optBoolean("isCombined", false)
        val separator = obj.optString("separator", " | ")
        val fieldsList = mutableListOf<DisplayFieldConfig>()
        val fieldsArr = obj.optJSONArray("fields")
        if (fieldsArr != null) {
            for (i in 0 until fieldsArr.length()) {
                val fObj = fieldsArr.getJSONObject(i)
                val condObj = fObj.optJSONObject("condition")
                val condition = if (condObj != null) {
                    ConditionalRule(
                        fieldKey = condObj.optString("fieldKey", "studentClass"),
                        operator = condObj.optString("operator", "EQUALS"),
                        targetValue = condObj.optString("targetValue", "")
                    )
                } else null

                fieldsList.add(
                    DisplayFieldConfig(
                        key = fObj.optString("key", "name"),
                        label = fObj.optString("label", ""),
                        showLabel = fObj.optBoolean("showLabel", true),
                        customPrefix = fObj.optString("customPrefix", ""),
                        customSuffix = fObj.optString("customSuffix", ""),
                        isVisible = fObj.optBoolean("isVisible", true),
                        hasCondition = fObj.optBoolean("hasCondition", false),
                        condition = condition
                    )
                )
            }
        }
        return DisplayAreaConfig(areaType, fieldsList, isCombined, separator)
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }
}
