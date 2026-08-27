package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.util.FormulaEvaluator
import com.example.util.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentViewCustomizerDialog(
    currentView: StudentSavedView,
    allSavedViews: List<StudentSavedView>,
    customFields: List<CustomFieldEntity>,
    formulaRules: List<FormulaRuleEntity>,
    sampleStudent: StudentEntity?,
    onDismiss: () -> Unit,
    onApplyView: (StudentSavedView) -> Unit,
    onSaveNewPreset: (StudentSavedView) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSetDefaultPreset: (String) -> Unit
) {
    val context = LocalContext.current

    // Active editable copy of view
    var workingView by remember(currentView) { mutableStateOf(currentView) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Display Areas, 1: Actions, 2: Visual Style, 3: Quick Filters, 4: Saved Views, 5: Role Permissions

    // Dialog state for adding a field to an area
    var addingFieldToArea by remember { mutableStateOf<DisplayAreaType?>(null) }
    var editingFieldCondition by remember { mutableStateOf<Pair<DisplayAreaType, Int>?>(null) }

    // Dialog state for saving new preset
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Role permissions state
    var permissions by remember { mutableStateOf(StudentViewConfigManager.loadRolePermissions(context)) }

    // Dummy sample student if none exists
    val previewStudent = sampleStudent ?: StudentEntity(
        id = "STD-2026-001",
        studentClass = "৫ম শ্রেণি",
        rollNumber = 1,
        name = "মুহাম্মদ আবদুল্লাহ",
        fatherName = "মোঃ রফিকুল ইসলাম",
        motherName = "মোসাম্মৎ ফাতেমা বেগম",
        birthDate = "2015-03-12",
        mobile = "01711223344",
        village = "পশ্চিম রামপুর",
        academicYear = "2026",
        address = "গ্রাম: পশ্চিম রামপুর, ডাকঘর: রামপুর",
        birthRegNumber = "20151234567890123",
        gender = "ছাত্র",
        isSpecialNeeds = false,
        status = "Current",
        photoUri = null,
        customValuesJson = "{\"cf_blood\":\"A+\",\"cf_stipend\":\"হ্যাঁ\"}"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Header
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                            Column {
                                Text(
                                    text = "ভিউ ও কার্ড কাস্টমাইজেশন প্যানেল",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "সক্রিয় ভিউ: ${workingView.name}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showSavePresetDialog = true },
                                modifier = Modifier.testTag("btn_save_as_new_view")
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("নতুন ভিউ সেভ", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    onApplyView(workingView)
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("btn_apply_customized_view")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("প্রয়োগ করুন")
                            }
                        }
                    }
                }

                // ==========================================
                // LIVE REAL-TIME CARD PREVIEW BOX
                // ==========================================
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("লাইভ কার্ড প্রিভিউ (Live Card Preview):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("ভিউ মোড: ${if (workingView.visual.viewMode == "LIST") "তালিকা (List)" else "কার্ড (Card)"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Render sample student with active workingView
                        DynamicStudentCard(
                            student = previewStudent,
                            viewConfig = workingView,
                            customFields = customFields,
                            formulaRules = formulaRules,
                            onActionClick = { }
                        )
                    }
                }

                // Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("১. তথ্য ও এরিয়া (Areas)", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.DashboardCustomize, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("২. অ্যাকশন বাটন", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("৩. ভিজুয়াল স্টাইল", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("৪. কুইক ফিল্টার", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("৫. সংরক্ষিত ভিউ", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        text = { Text("৬. পারমিশন কন্ট্রোল", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Tab Contents Container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (selectedTab) {
                        0 -> DisplayAreasTab(
                            viewConfig = workingView,
                            onUpdateArea = { updatedArea ->
                                workingView = when (updatedArea.areaType) {
                                    DisplayAreaType.HEADER -> workingView.copy(headerArea = updatedArea)
                                    DisplayAreaType.SECONDARY_ROW -> workingView.copy(secondaryArea = updatedArea)
                                    DisplayAreaType.THIRD_ROW -> workingView.copy(thirdArea = updatedArea)
                                    DisplayAreaType.BADGE_AREA -> workingView.copy(badgeArea = updatedArea)
                                    DisplayAreaType.AVATAR_AREA -> workingView.copy(avatarArea = updatedArea)
                                }
                            },
                            onOpenAddField = { areaType -> addingFieldToArea = areaType },
                            onEditCondition = { areaType, index -> editingFieldCondition = areaType to index }
                        )

                        1 -> ActionsTab(
                            actions = workingView.actions,
                            onUpdateActions = { updated -> workingView = workingView.copy(actions = updated) }
                        )

                        2 -> VisualStyleTab(
                            visual = workingView.visual,
                            onUpdateVisual = { updated -> workingView = workingView.copy(visual = updated) }
                        )

                        3 -> QuickFiltersTab(
                            filters = workingView.quickFilters,
                            onUpdateFilters = { updated -> workingView = workingView.copy(quickFilters = updated) }
                        )

                        4 -> SavedViewsTab(
                            allViews = allSavedViews,
                            activeViewId = workingView.id,
                            onSelectView = { v -> workingView = v },
                            onSaveNew = { showSavePresetDialog = true },
                            onSetDefault = onSetDefaultPreset,
                            onDelete = onDeletePreset
                        )

                        5 -> PermissionsTab(
                            permissions = permissions,
                            onUpdatePermissions = { updated ->
                                permissions = updated
                                StudentViewConfigManager.saveRolePermissions(context, updated)
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: Add Field to Area
    if (addingFieldToArea != null) {
        val targetAreaType = addingFieldToArea!!
        AddFieldToAreaDialog(
            areaType = targetAreaType,
            customFields = customFields,
            onDismiss = { addingFieldToArea = null },
            onFieldSelected = { newField ->
                val currentArea = when (targetAreaType) {
                    DisplayAreaType.HEADER -> workingView.headerArea
                    DisplayAreaType.SECONDARY_ROW -> workingView.secondaryArea
                    DisplayAreaType.THIRD_ROW -> workingView.thirdArea
                    DisplayAreaType.BADGE_AREA -> workingView.badgeArea
                    DisplayAreaType.AVATAR_AREA -> workingView.avatarArea
                }
                val updatedArea = currentArea.copy(fields = currentArea.fields + newField)
                workingView = when (targetAreaType) {
                    DisplayAreaType.HEADER -> workingView.copy(headerArea = updatedArea)
                    DisplayAreaType.SECONDARY_ROW -> workingView.copy(secondaryArea = updatedArea)
                    DisplayAreaType.THIRD_ROW -> workingView.copy(thirdArea = updatedArea)
                    DisplayAreaType.BADGE_AREA -> workingView.copy(badgeArea = updatedArea)
                    DisplayAreaType.AVATAR_AREA -> workingView.copy(avatarArea = updatedArea)
                }
                addingFieldToArea = null
            }
        )
    }

    // Modal: Edit Field Condition
    if (editingFieldCondition != null) {
        val (areaType, fieldIdx) = editingFieldCondition!!
        val currentArea = when (areaType) {
            DisplayAreaType.HEADER -> workingView.headerArea
            DisplayAreaType.SECONDARY_ROW -> workingView.secondaryArea
            DisplayAreaType.THIRD_ROW -> workingView.thirdArea
            DisplayAreaType.BADGE_AREA -> workingView.badgeArea
            DisplayAreaType.AVATAR_AREA -> workingView.avatarArea
        }
        val targetField = currentArea.fields.getOrNull(fieldIdx)

        if (targetField != null) {
            FieldConditionDialog(
                field = targetField,
                customFields = customFields,
                onDismiss = { editingFieldCondition = null },
                onSave = { updatedField ->
                    val newFields = currentArea.fields.toMutableList()
                    newFields[fieldIdx] = updatedField
                    val updatedArea = currentArea.copy(fields = newFields)
                    workingView = when (areaType) {
                        DisplayAreaType.HEADER -> workingView.copy(headerArea = updatedArea)
                        DisplayAreaType.SECONDARY_ROW -> workingView.copy(secondaryArea = updatedArea)
                        DisplayAreaType.THIRD_ROW -> workingView.copy(thirdArea = updatedArea)
                        DisplayAreaType.BADGE_AREA -> workingView.copy(badgeArea = updatedArea)
                        DisplayAreaType.AVATAR_AREA -> workingView.copy(avatarArea = updatedArea)
                    }
                    editingFieldCondition = null
                }
            )
        }
    }

    // Modal: Save as New Preset
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("নতুন ভিউ হিসেবে সংরক্ষণ করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("বর্তমান লেআউট, ফিল্ড পজিশন ও কালার সেটিংস সংরক্ষণ করার জন্য একটি নাম দিন:")
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("ভিউ এর নাম") },
                        placeholder = { Text("যেমন: ক্লাস ৫ পরীক্ষার রোল ভিউ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            val newPreset = workingView.copy(
                                id = "view_${System.currentTimeMillis()}",
                                name = newPresetName.trim(),
                                isDefault = false
                            )
                            onSaveNewPreset(newPreset)
                            workingView = newPreset
                            showSavePresetDialog = false
                        }
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = { TextButton(onClick = { showSavePresetDialog = false }) { Text("বাতিল") } }
        )
    }
}

// ==========================================
// 1. DISPLAY AREAS TAB
// ==========================================

@Composable
fun DisplayAreasTab(
    viewConfig: StudentSavedView,
    onUpdateArea: (DisplayAreaConfig) -> Unit,
    onOpenAddField: (DisplayAreaType) -> Unit,
    onEditCondition: (DisplayAreaType, Int) -> Unit
) {
    val areas = listOf(
        viewConfig.headerArea to DisplayAreaType.HEADER,
        viewConfig.secondaryArea to DisplayAreaType.SECONDARY_ROW,
        viewConfig.thirdArea to DisplayAreaType.THIRD_ROW,
        viewConfig.badgeArea to DisplayAreaType.BADGE_AREA,
        viewConfig.avatarArea to DisplayAreaType.AVATAR_AREA
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "কার্ডের প্রতিটি অংশে (Header, Rows, Badges) কোন ফিল্ড থাকবে তা নির্ধারণ করুন, ক্রম পরিবর্তন করতে তীর চিহ্নে চাপুন অথবা শর্ত যোগ করুন।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        items(areas) { (areaConfig, areaType) ->
            AreaConfigCard(
                areaConfig = areaConfig,
                areaType = areaType,
                onUpdateArea = onUpdateArea,
                onOpenAddField = { onOpenAddField(areaType) },
                onEditCondition = { idx -> onEditCondition(areaType, idx) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AreaConfigCard(
    areaConfig: DisplayAreaConfig,
    areaType: DisplayAreaType,
    onUpdateArea: (DisplayAreaConfig) -> Unit,
    onOpenAddField: () -> Unit,
    onEditCondition: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Title & Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = when (areaType) {
                            DisplayAreaType.HEADER -> Icons.Filled.Title
                            DisplayAreaType.SECONDARY_ROW -> Icons.Filled.ViewAgenda
                            DisplayAreaType.THIRD_ROW -> Icons.Filled.TableRows
                            DisplayAreaType.BADGE_AREA -> Icons.Filled.Stars
                            DisplayAreaType.AVATAR_AREA -> Icons.Filled.AccountCircle
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = areaType.labelBangla,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Button(
                    onClick = onOpenAddField,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ফিল্ড যোগ", fontSize = 11.sp)
                }
            }

            // Options: Combined Value Toggle & Separator
            if (areaType != DisplayAreaType.AVATAR_AREA) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = areaConfig.isCombined,
                            onCheckedChange = { onUpdateArea(areaConfig.copy(isCombined = it)) },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ফিল্ডসমূহ একত্রে যুক্ত করুন (Combined)", fontSize = 12.sp)
                    }

                    if (areaConfig.isCombined) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("বিভাজক:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val separators = listOf(" | ", " • ", " - ", " , ")
                            separators.forEach { sep ->
                                FilterChip(
                                    selected = areaConfig.separator == sep,
                                    onClick = { onUpdateArea(areaConfig.copy(separator = sep)) },
                                    label = { Text(sep.trim(), fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Field Items in this area
            if (areaConfig.fields.isEmpty()) {
                Text(
                    text = "কোনো ফিল্ড যোগ করা হয়নি (খালি থাকবে)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    areaConfig.fields.forEachIndexed { index, field ->
                        FieldRowItem(
                            field = field,
                            isFirst = index == 0,
                            isLast = index == areaConfig.fields.size - 1,
                            onMoveUp = {
                                if (index > 0) {
                                    val list = areaConfig.fields.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    onUpdateArea(areaConfig.copy(fields = list))
                                }
                            },
                            onMoveDown = {
                                if (index < areaConfig.fields.size - 1) {
                                    val list = areaConfig.fields.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    onUpdateArea(areaConfig.copy(fields = list))
                                }
                            },
                            onToggleVisibility = {
                                val list = areaConfig.fields.toMutableList()
                                list[index] = field.copy(isVisible = !field.isVisible)
                                onUpdateArea(areaConfig.copy(fields = list))
                            },
                            onToggleLabel = {
                                val list = areaConfig.fields.toMutableList()
                                list[index] = field.copy(showLabel = !field.showLabel)
                                onUpdateArea(areaConfig.copy(fields = list))
                            },
                            onEditCondition = { onEditCondition(index) },
                            onRemove = {
                                val list = areaConfig.fields.toMutableList()
                                list.removeAt(index)
                                onUpdateArea(areaConfig.copy(fields = list))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FieldRowItem(
    field: DisplayFieldConfig,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLabel: () -> Unit,
    onEditCondition: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = if (field.isVisible) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (field.isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle Visibility",
                        tint = if (field.isVisible) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = field.label.ifBlank { field.key },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (field.isVisible) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                        if (field.hasCondition && field.condition != null) {
                            Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "শর্তযুক্ত",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (field.showLabel) "লেবেল সক্রিয় (পিতা: মান)" else "শুধু মান (মান)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onToggleLabel() }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Condition button
                IconButton(onClick = onEditCondition, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Rule,
                        contentDescription = "Condition",
                        tint = if (field.hasCondition) Color(0xFFE65100) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Move Up
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                }

                // Move Down
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                }

                // Delete
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ==========================================
// 2. ACTIONS TAB
// ==========================================

@Composable
fun ActionsTab(
    actions: List<CardActionConfig>,
    onUpdateActions: (List<CardActionConfig>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "প্রতিটি শিক্ষার্থী কার্ডে কোন কোন বাটন (View, Edit, Delete, Call, WhatsApp, ID Card, Print) দেখাবে এবং তাদের ক্রম নির্ধারণ করুন।",
                        fontSize = 12.sp
                    )
                }
            }
        }

        itemsIndexed(actions) { index, action ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(
                            checked = action.isEnabled,
                            onCheckedChange = { checked ->
                                val list = actions.toMutableList()
                                list[index] = action.copy(isEnabled = checked)
                                onUpdateActions(list)
                            }
                        )

                        Icon(
                            imageVector = when (action.id) {
                                "view" -> Icons.Filled.Visibility
                                "edit" -> Icons.Filled.Edit
                                "delete" -> Icons.Filled.Delete
                                "call" -> Icons.Filled.Phone
                                "whatsapp" -> Icons.Filled.Chat
                                "id_card" -> Icons.Filled.Badge
                                "print" -> Icons.Filled.Print
                                else -> Icons.Filled.TouchApp
                            },
                            contentDescription = null,
                            tint = if (action.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )

                        Column {
                            Text(
                                text = action.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (action.isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                            )
                            Text(
                                text = "অ্যাকশন আইডি: ${action.id}",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val list = actions.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    onUpdateActions(list)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                if (index < actions.size - 1) {
                                    val list = actions.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    onUpdateActions(list)
                                }
                            },
                            enabled = index < actions.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. VISUAL STYLE TAB
// ==========================================

@Composable
fun VisualStyleTab(
    visual: CardVisualConfig,
    onUpdateVisual: (CardVisualConfig) -> Unit
) {
    val themeColors = listOf(
        "DEFAULT" to ("ডিফল্ট নীল" to Color(0xFF1E88E5)),
        "#00897B" to ("টিয়াল গ্রিন" to Color(0xFF00897B)),
        "#5E35B1" to ("ইন্ডিগো পার্পল" to Color(0xFF5E35B1)),
        "#E65100" to ("অরেঞ্জ আম্বার" to Color(0xFFE65100)),
        "#2E7D32" to ("ফরেস্ট গ্রিন" to Color(0xFF2E7D32)),
        "#37474F" to ("ডার্ক স্লেট" to Color(0xFF37474F))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // View Mode: Card vs List
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ভিউ মোড (View Mode):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = visual.viewMode == "CARD",
                            onClick = { onUpdateVisual(visual.copy(viewMode = "CARD")) },
                            label = { Text("📇 বড় কার্ড ভিউ (Card)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = visual.viewMode == "LIST",
                            onClick = { onUpdateVisual(visual.copy(viewMode = "LIST")) },
                            label = { Text("📋 সংক্ষিপ্ত তালিকা (List Table)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Density / Sizing
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কার্ড সাইজ / ডেনসিটি (Density):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "COMPACT" to "কমপ্যাক্ট (Compact)",
                            "NORMAL" to "নরমাল (Normal)",
                            "SPACIOUS" to "প্রশস্ত (Spacious)"
                        ).forEach { (denKey, denLabel) ->
                            FilterChip(
                                selected = visual.density == denKey,
                                onClick = { onUpdateVisual(visual.copy(density = denKey)) },
                                label = { Text(denLabel, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Avatar & Badges Visibility
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("অবতার, ব্যাজ ও লেবেল অপশন:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = visual.showAvatar,
                            onCheckedChange = { onUpdateVisual(visual.copy(showAvatar = it)) }
                        )
                        Text("ছবি / অবতার দেখান", fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = visual.showBadges,
                            onCheckedChange = { onUpdateVisual(visual.copy(showBadges = it)) }
                        )
                        Text("স্ট্যাটাস ও ক্যাটাগরি ব্যাজ দেখান", fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = visual.showLabels,
                            onCheckedChange = { onUpdateVisual(visual.copy(showLabels = it)) }
                        )
                        Text("ফিল্ড লেবেল টেক্সট দেখান (যেমন 'পিতা: ')", fontSize = 12.sp)
                    }
                }
            }
        }

        // Color Theme
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কার্ড থিম ও কালার এক্সেন্ট:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(themeColors) { (code, pair) ->
                            val (label, color) = pair
                            val isSelected = visual.colorThemeHex == code
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onUpdateVisual(visual.copy(colorThemeHex = code)) }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) color else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Corner Radius & Elevation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("কর্নার রাউন্ডনেস (Corner Radius):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "স্কয়ার (0dp)", 8 to "মৃদু (8dp)", 14 to "স্ট্যান্ডার্ড (14dp)", 22 to "রাউন্ডেড (22dp)").forEach { (rad, lbl) ->
                            FilterChip(
                                selected = visual.cornerRadiusDp == rad,
                                onClick = { onUpdateVisual(visual.copy(cornerRadiusDp = rad)) },
                                label = { Text(lbl, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("এলিভেশন / ছায়া (Elevation):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "ফ্ল্যাট (0dp)", 2 to "হালকা (2dp)", 4 to "উচ্চ (4dp)").forEach { (ele, lbl) ->
                            FilterChip(
                                selected = visual.elevationDp == ele,
                                onClick = { onUpdateVisual(visual.copy(elevationDp = ele)) },
                                label = { Text(lbl, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ==========================================
// 4. QUICK FILTERS TAB
// ==========================================

@Composable
fun QuickFiltersTab(
    filters: List<QuickFilterItem>,
    onUpdateFilters: (List<QuickFilterItem>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "শিক্ষার্থী তালিকার উপরে যে কুইক ফিল্টার বারগুলো (শ্রেণি, লিঙ্গ, স্ট্যাটাস, গ্রাম বা কাস্টম ফিল্ড) প্রদর্শিত হবে তা নির্বাচন ও সাজান।",
                        fontSize = 12.sp
                    )
                }
            }
        }

        itemsIndexed(filters) { index, item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(
                            checked = item.isEnabled,
                            onCheckedChange = { checked ->
                                val list = filters.toMutableList()
                                list[index] = item.copy(isEnabled = checked)
                                onUpdateFilters(list)
                            }
                        )

                        Column {
                            Text(
                                text = item.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                            )
                            Text(
                                text = "ফিল্টার কী: ${item.key}",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val list = filters.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    onUpdateFilters(list)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                if (index < filters.size - 1) {
                                    val list = filters.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    onUpdateFilters(list)
                                }
                            },
                            enabled = index < filters.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. SAVED VIEWS TAB
// ==========================================

@Composable
fun SavedViewsTab(
    allViews: List<StudentSavedView>,
    activeViewId: String,
    onSelectView: (StudentSavedView) -> Unit,
    onSaveNew: () -> Unit,
    onSetDefault: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "সংরক্ষিত ভিউসমূহ (${allViews.size}):",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Button(onClick = onSaveNew) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("নতুন ভিউ সেভ")
                }
            }
        }

        items(allViews) { viewItem ->
            val isActive = viewItem.id == activeViewId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectView(viewItem) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = viewItem.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (viewItem.isDefault) {
                                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = "ডিফল্ট",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isActive) {
                                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = "সক্রিয়",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "মোড: ${if (viewItem.visual.viewMode == "LIST") "তালিকা" else "কার্ড"} • হেডার: ${viewItem.headerArea.fields.size}টি • সারি: ${viewItem.secondaryArea.fields.size + viewItem.thirdArea.fields.size}টি",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!viewItem.isDefault) {
                            TextButton(onClick = { onSetDefault(viewItem.id) }) {
                                Text("ডিফল্ট করুন", fontSize = 11.sp)
                            }
                        }
                        if (allViews.size > 1 && viewItem.id != "default_view") {
                            IconButton(onClick = { onDelete(viewItem.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. PERMISSIONS TAB
// ==========================================

@Composable
fun PermissionsTab(
    permissions: List<RolePermissionConfig>,
    onUpdatePermissions: (List<RolePermissionConfig>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ব্যবহারকারীর রোল (Admin, Teacher, Staff, Viewer) অনুযায়ী কে কোন তথ্য দেখতে, সম্পাদন করতে বা লেআউট পরিবর্তন করতে পারবে তা নির্ধারণ করুন।",
                        fontSize = 12.sp
                    )
                }
            }
        }

        itemsIndexed(permissions) { index, perm ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "রোল: ${perm.role}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Divider()

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = perm.canDelete,
                            onCheckedChange = { chk ->
                                val list = permissions.toMutableList()
                                list[index] = perm.copy(canDelete = chk)
                                onUpdatePermissions(list)
                            }
                        )
                        Text("শিক্ষার্থী তথ্য স্থায়ীভাবে মুছতে পারবে (Delete Permission)", fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = perm.canManageCustomFields,
                            onCheckedChange = { chk ->
                                val list = permissions.toMutableList()
                                list[index] = perm.copy(canManageCustomFields = chk)
                                onUpdatePermissions(list)
                            }
                        )
                        Text("কাস্টম ও ক্যালকুলেটেড ফিল্ড তৈরি/সম্পাদনা করতে পারবে", fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = perm.canCustomizeLayout,
                            onCheckedChange = { chk ->
                                val list = permissions.toMutableList()
                                list[index] = perm.copy(canCustomizeLayout = chk)
                                onUpdatePermissions(list)
                            }
                        )
                        Text("কার্ড ও টেবিল ভিউ কাস্টমাইজেশন পরিবর্তন করতে পারবে", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. HELPER DIALOGS (ADD FIELD & CONDITION)
// ==========================================

@Composable
fun AddFieldToAreaDialog(
    areaType: DisplayAreaType,
    customFields: List<CustomFieldEntity>,
    onDismiss: () -> Unit,
    onFieldSelected: (DisplayFieldConfig) -> Unit
) {
    val allOptions = remember(customFields) {
        val standard = StudentViewConfigManager.standardFieldDefinitions.map { (k, v) ->
            DisplayFieldConfig(key = k, label = v)
        }
        val custom = customFields.map { cf ->
            DisplayFieldConfig(
                key = cf.id,
                label = if (cf.isCalculated) "${cf.name} (Calculated)" else "${cf.name} (Custom)"
            )
        }
        (standard + custom)
    }

    var searchQuery by remember { mutableStateOf("") }
    val filtered = allOptions.filter {
        it.label.contains(searchQuery, ignoreCase = true) || it.key.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${areaType.labelBangla}-তে ফিল্ড যুক্ত করুন", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ফিল্ডের নাম খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFieldSelected(item) },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.label, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } }
    )
}

@Composable
fun FieldConditionDialog(
    field: DisplayFieldConfig,
    customFields: List<CustomFieldEntity>,
    onDismiss: () -> Unit,
    onSave: (DisplayFieldConfig) -> Unit
) {
    var hasCondition by remember(field) { mutableStateOf(field.hasCondition) }
    var fieldKey by remember(field) { mutableStateOf(field.condition?.fieldKey ?: "studentClass") }
    var operator by remember(field) { mutableStateOf(field.condition?.operator ?: "EQUALS") }
    var targetValue by remember(field) { mutableStateOf(field.condition?.targetValue ?: "৫ম শ্রেণি") }
    var customPrefix by remember(field) { mutableStateOf(field.customPrefix) }
    var customSuffix by remember(field) { mutableStateOf(field.customSuffix) }

    val operators = listOf("EQUALS", "NOT_EQUALS", "CONTAINS", "IS_TRUE", "IS_FALSE", "GREATER_THAN", "LESS_THAN")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${field.label} শর্ত ও প্রিফিক্স", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customPrefix,
                    onValueChange = { customPrefix = it },
                    label = { Text("কাস্টম প্রিফিক্স (Prefix: e.g. রোল: বা 📞)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customSuffix,
                    onValueChange = { customSuffix = it },
                    label = { Text("কাস্টম সাফিক্স (Suffix: e.g. জন বা নং)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasCondition, onCheckedChange = { hasCondition = it })
                    Text("শর্তসাপেক্ষ প্রদর্শন (Conditional Visibility)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (hasCondition) {
                    OutlinedTextField(
                        value = fieldKey,
                        onValueChange = { fieldKey = it },
                        label = { Text("যে ফিল্ডের ওপর শর্ত (e.g. studentClass বা status)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("অপারেটর:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(operators) { op ->
                            FilterChip(
                                selected = operator == op,
                                onClick = { operator = op },
                                label = { Text(op, fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetValue,
                        onValueChange = { targetValue = it },
                        label = { Text("তুলনা করার মান (Target Value)") },
                        placeholder = { Text("যেমন: ৫ম শ্রেণি বা Current") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = field.copy(
                        customPrefix = customPrefix,
                        customSuffix = customSuffix,
                        hasCondition = hasCondition,
                        condition = if (hasCondition) ConditionalRule(fieldKey, operator, targetValue) else null
                    )
                    onSave(updated)
                }
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

// ==========================================
// 8. DYNAMIC STUDENT CARD RENDERER
// ==========================================

@Composable
fun DynamicStudentCard(
    student: StudentEntity,
    viewConfig: StudentSavedView,
    customFields: List<CustomFieldEntity>,
    formulaRules: List<FormulaRuleEntity>,
    onActionClick: (String) -> Unit = {}
) {
    val visual = viewConfig.visual
    val accentColor = remember(visual.colorThemeHex) {
        when (visual.colorThemeHex) {
            "#00897B" -> Color(0xFF00897B)
            "#5E35B1" -> Color(0xFF5E35B1)
            "#E65100" -> Color(0xFFE65100)
            "#2E7D32" -> Color(0xFF2E7D32)
            "#37474F" -> Color(0xFF37474F)
            else -> Color(0xFF1E88E5)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.cornerRadiusDp.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(visual.elevationDp.dp),
        border = if (visual.borderWidthDp > 0) BorderStroke(visual.borderWidthDp.dp, accentColor.copy(alpha = 0.5f)) else null
    ) {
        if (visual.viewMode == "LIST") {
            // Table / List View Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Header
                    val headerText = renderAreaText(viewConfig.headerArea, student, customFields, formulaRules, visual.showLabels)
                    if (headerText.isNotBlank()) {
                        Text(text = headerText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Secondary
                    val secText = renderAreaText(viewConfig.secondaryArea, student, customFields, formulaRules, visual.showLabels)
                    if (secText.isNotBlank()) {
                        Text(text = secText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    viewConfig.actions.filter { it.isEnabled }.take(2).forEach { act ->
                        IconButton(
                            onClick = { onActionClick(act.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = when (act.id) {
                                    "view" -> Icons.Filled.Visibility
                                    "edit" -> Icons.Filled.Edit
                                    "delete" -> Icons.Filled.Delete
                                    "call" -> Icons.Filled.Phone
                                    "whatsapp" -> Icons.Filled.Chat
                                    "id_card" -> Icons.Filled.Badge
                                    else -> Icons.Filled.TouchApp
                                },
                                contentDescription = act.label,
                                tint = if (act.id == "delete") Color.Red.copy(alpha = 0.7f) else accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Standard Card View
            val pad = when (visual.density) {
                "COMPACT" -> 8.dp
                "SPACIOUS" -> 16.dp
                else -> 12.dp
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(pad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar / Photo Area
                if (visual.showAvatar) {
                    val initial = student.name.take(1)
                    val avatarBg = if (student.gender == "ছাত্র") Color(0xFFBBDEFB) else Color(0xFFF8BBD0)
                    val avatarFg = if (student.gender == "ছাত্র") Color(0xFF0D47A1) else Color(0xFF880E4F)

                    Box(
                        modifier = Modifier
                            .size(if (visual.density == "COMPACT") 40.dp else 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(avatarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = initial, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = avatarFg)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Content Areas
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Header Area + Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val headerText = renderAreaText(viewConfig.headerArea, student, customFields, formulaRules, visual.showLabels)
                        Text(
                            text = headerText.ifBlank { student.name },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Badge Area
                        if (visual.showBadges && viewConfig.badgeArea.fields.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                viewConfig.badgeArea.fields.filter { it.isVisible }.forEach { bf ->
                                    if (!bf.hasCondition || bf.condition?.isMet(student, customFields) == true) {
                                        val badgeVal = FormulaEvaluator.getFieldValue(student, bf.key, customFields, formulaRules)
                                        if (badgeVal.isNotBlank()) {
                                            Surface(
                                                color = if (badgeVal == "অভ্যন্তরীণ" || badgeVal == "Current") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${bf.customPrefix}$badgeVal${bf.customSuffix}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (badgeVal == "অভ্যন্তরীণ" || badgeVal == "Current") Color(0xFF2E7D32) else Color(0xFFE65100),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Secondary Row
                    val secText = renderAreaText(viewConfig.secondaryArea, student, customFields, formulaRules, visual.showLabels)
                    if (secText.isNotBlank()) {
                        Text(
                            text = secText,
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Third Row
                    val thirdText = renderAreaText(viewConfig.thirdArea, student, customFields, formulaRules, visual.showLabels)
                    if (thirdText.isNotBlank()) {
                        Text(
                            text = thirdText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions Area
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    viewConfig.actions.filter { it.isEnabled }.take(3).forEach { act ->
                        IconButton(
                            onClick = { onActionClick(act.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = when (act.id) {
                                    "view" -> Icons.Filled.Visibility
                                    "edit" -> Icons.Filled.Edit
                                    "delete" -> Icons.Filled.Delete
                                    "call" -> Icons.Filled.Phone
                                    "whatsapp" -> Icons.Filled.Chat
                                    "id_card" -> Icons.Filled.Badge
                                    "print" -> Icons.Filled.Print
                                    else -> Icons.Filled.TouchApp
                                },
                                contentDescription = act.label,
                                tint = if (act.id == "delete") Color.Red.copy(alpha = 0.7f) else accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun renderAreaText(
    area: DisplayAreaConfig,
    student: StudentEntity,
    customFields: List<CustomFieldEntity>,
    formulaRules: List<FormulaRuleEntity>,
    globalShowLabels: Boolean
): String {
    val visibleFields = area.fields.filter { it.isVisible && (!it.hasCondition || it.condition?.isMet(student, customFields) == true) }
    if (visibleFields.isEmpty()) return ""

    val pieces = visibleFields.mapNotNull { f ->
        val raw = FormulaEvaluator.getFieldValue(student, f.key, customFields, formulaRules)
        if (raw.isBlank()) null
        else {
            val labelPrefix = if (globalShowLabels && f.showLabel && f.label.isNotBlank()) "${f.label}: " else ""
            "${f.customPrefix}$labelPrefix$raw${f.customSuffix}"
        }
    }

    return if (area.isCombined) {
        pieces.joinToString(area.separator)
    } else {
        pieces.joinToString(" ")
    }
}
