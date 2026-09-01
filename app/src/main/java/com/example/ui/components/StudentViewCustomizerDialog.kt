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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onOverwritePreset: (StudentSavedView) -> Unit = {},
    onRenamePreset: (String, String) -> Unit = { _, _ -> },
    onDuplicatePreset: (String) -> Unit = {},
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

    // Dialog state for renaming preset
    var renamingPresetId by remember { mutableStateOf<String?>(null) }
    var renamePresetNameInput by remember { mutableStateOf("") }

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
        isSpecialNeeds = true,
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

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    onOverwritePreset(workingView)
                                    android.widget.Toast.makeText(context, "প্রিসেট '${workingView.name}' সফলভাবে আপডেট হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ওভাররাইট করুন", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { showSavePresetDialog = true },
                                modifier = Modifier.testTag("btn_save_as_new_view"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("নতুন প্রিসেট", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    onApplyView(workingView)
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("btn_apply_customized_view"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("প্রয়োগ", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ==========================================
                // LIVE INTERACTIVE PREVIEW CARD
                // ==========================================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "লাইভ কার্ড প্রিভিউ (Live Card Preview)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (workingView.visual.viewMode == "LIST") "লিস্ট টেবিল মোড" else "কার্ড মোড",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Dynamic Preview Card Rendering
                        DynamicStudentCard(
                            student = previewStudent,
                            viewConfig = workingView,
                            customFields = customFields,
                            formulaRules = formulaRules,
                            onActionClick = { }
                        )
                    }
                }

                // Tabs Navigation Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
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
                                    DisplayAreaType.RIGHT_ROW_1 -> workingView.copy(rightRow1 = updatedArea)
                                    DisplayAreaType.RIGHT_ROW_2 -> workingView.copy(rightRow2 = updatedArea)
                                    DisplayAreaType.RIGHT_ROW_3 -> workingView.copy(rightRow3 = updatedArea)
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
                            workingView = workingView,
                            onSelectView = { v -> workingView = v },
                            onSaveNew = { showSavePresetDialog = true },
                            onOverwriteCurrent = {
                                onOverwritePreset(workingView)
                                android.widget.Toast.makeText(context, "প্রিসেট '${workingView.name}' সফলভাবে আপডেট হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onRename = { id, currentName ->
                                renamingPresetId = id
                                renamePresetNameInput = currentName
                            },
                            onDuplicate = { id ->
                                onDuplicatePreset(id)
                                android.widget.Toast.makeText(context, "প্রিসেট সফলভাবে ডুপ্লিকেট করা হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
                            },
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
                    DisplayAreaType.RIGHT_ROW_1 -> workingView.rightRow1
                    DisplayAreaType.RIGHT_ROW_2 -> workingView.rightRow2
                    DisplayAreaType.RIGHT_ROW_3 -> workingView.rightRow3
                    DisplayAreaType.BADGE_AREA -> workingView.badgeArea
                    DisplayAreaType.AVATAR_AREA -> workingView.avatarArea
                }
                val updatedArea = currentArea.copy(fields = currentArea.fields + newField)
                workingView = when (targetAreaType) {
                    DisplayAreaType.HEADER -> workingView.copy(headerArea = updatedArea)
                    DisplayAreaType.SECONDARY_ROW -> workingView.copy(secondaryArea = updatedArea)
                    DisplayAreaType.THIRD_ROW -> workingView.copy(thirdArea = updatedArea)
                    DisplayAreaType.RIGHT_ROW_1 -> workingView.copy(rightRow1 = updatedArea)
                    DisplayAreaType.RIGHT_ROW_2 -> workingView.copy(rightRow2 = updatedArea)
                    DisplayAreaType.RIGHT_ROW_3 -> workingView.copy(rightRow3 = updatedArea)
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
            DisplayAreaType.RIGHT_ROW_1 -> workingView.rightRow1
            DisplayAreaType.RIGHT_ROW_2 -> workingView.rightRow2
            DisplayAreaType.RIGHT_ROW_3 -> workingView.rightRow3
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
                        DisplayAreaType.RIGHT_ROW_1 -> workingView.copy(rightRow1 = updatedArea)
                        DisplayAreaType.RIGHT_ROW_2 -> workingView.copy(rightRow2 = updatedArea)
                        DisplayAreaType.RIGHT_ROW_3 -> workingView.copy(rightRow3 = updatedArea)
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

    // Modal: Rename Preset
    if (renamingPresetId != null) {
        val targetId = renamingPresetId!!
        AlertDialog(
            onDismissRequest = { renamingPresetId = null },
            title = { Text("প্রিসেটের নাম পরিবর্তন করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("প্রিসেটের নতুন নাম দিন:")
                    OutlinedTextField(
                        value = renamePresetNameInput,
                        onValueChange = { renamePresetNameInput = it },
                        label = { Text("নতুন নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamePresetNameInput.isNotBlank()) {
                            onRenamePreset(targetId, renamePresetNameInput.trim())
                            if (workingView.id == targetId) {
                                workingView = workingView.copy(name = renamePresetNameInput.trim())
                            }
                            renamingPresetId = null
                        }
                    }
                ) {
                    Text("আপডেট করুন")
                }
            },
            dismissButton = { TextButton(onClick = { renamingPresetId = null }) { Text("বাতিল") } }
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
        viewConfig.rightRow1 to DisplayAreaType.RIGHT_ROW_1,
        viewConfig.rightRow2 to DisplayAreaType.RIGHT_ROW_2,
        viewConfig.rightRow3 to DisplayAreaType.RIGHT_ROW_3,
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
                        text = "কার্ডের প্রতিটি অংশে (Header, Left Rows, Right 3 Rows, Badges) কোন ফিল্ড থাকবে তা নির্ধারণ করুন, ক্রম পরিবর্তন করুন এবং টেক্সট বা আইকন মোড নির্বাচন করুন।",
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
                            DisplayAreaType.RIGHT_ROW_1 -> Icons.Filled.AlignHorizontalRight
                            DisplayAreaType.RIGHT_ROW_2 -> Icons.Filled.AlignHorizontalRight
                            DisplayAreaType.RIGHT_ROW_3 -> Icons.Filled.AlignHorizontalRight
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
                        if (field.displayMode == "ICON_ONLY") {
                            Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "আইকন মোড",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2),
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
                // Condition / Settings button
                IconButton(onClick = onEditCondition, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "Condition and Display Settings",
                        tint = if (field.hasCondition || field.displayMode != "AUTO") Color(0xFFE65100) else MaterialTheme.colorScheme.primary,
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
                        text = "প্রতিটি শিক্ষার্থী কার্ডের সর্বশেষ রোতে ডান পাশের নিচে প্রদর্শিত অ্যাকশন বাটনসমূহ (View, Edit, Delete, Call, WhatsApp, ID Card, Print) কনফিগার করুন।",
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
                            tint = if (action.id == "delete") Color.Red else MaterialTheme.colorScheme.primary
                        )

                        Column {
                            Text(text = action.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "আইডি: ${action.id}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Row {
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
                            enabled = index > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
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
                            enabled = index < actions.size - 1
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
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
    val colorPresets = listOf(
        "DEFAULT" to ("ডিফল্ট নীল" to Color(0xFF1E88E5)),
        "#00897B" to ("টিয়াল সবুজ" to Color(0xFF00897B)),
        "#5E35B1" to ("গাঢ় বেগুনি" to Color(0xFF5E35B1)),
        "#E65100" to ("উষ্ণ কমলা" to Color(0xFFE65100)),
        "#2E7D32" to ("গাঢ় সবুজ" to Color(0xFF2E7D32)),
        "#37474F" to ("স্লেট গ্রে" to Color(0xFF37474F))
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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ভিউ মোড (View Mode)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = visual.viewMode == "CARD",
                            onClick = { onUpdateVisual(visual.copy(viewMode = "CARD")) },
                            label = { Text("কার্ড ভিউ (Card View)") },
                            leadingIcon = { Icon(Icons.Filled.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = visual.viewMode == "LIST",
                            onClick = { onUpdateVisual(visual.copy(viewMode = "LIST")) },
                            label = { Text("সংক্ষিপ্ত লিস্ট (Compact List)") },
                            leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Density
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ঘনত্ব ও মার্জিন (Density)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("COMPACT" to "কম্প্যাক্ট", "NORMAL" to "স্ট্যান্ডার্ড", "SPACIOUS" to "প্রশস্ত").forEach { (d, label) ->
                            FilterChip(
                                selected = visual.density == d,
                                onClick = { onUpdateVisual(visual.copy(density = d)) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("অ্যাকসেন্ট কালার থিম", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(colorPresets) { (hex, pair) ->
                            val (title, color) = pair
                            val isSelected = visual.colorThemeHex == hex
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onUpdateVisual(visual.copy(colorThemeHex = hex)) }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card Border & Corners
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কার্ডের কোণা ও বর্ডার (Corner Radius & Elevation)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("কোণা (Radius): ${visual.cornerRadiusDp} dp", fontSize = 11.sp)
                            Slider(
                                value = visual.cornerRadiusDp.toFloat(),
                                onValueChange = { onUpdateVisual(visual.copy(cornerRadiusDp = it.toInt())) },
                                valueRange = 0f..28f,
                                steps = 3
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("উচ্চতা (Elevation): ${visual.elevationDp} dp", fontSize = 11.sp)
                            Slider(
                                value = visual.elevationDp.toFloat(),
                                onValueChange = { onUpdateVisual(visual.copy(elevationDp = it.toInt())) },
                                valueRange = 0f..6f,
                                steps = 2
                            )
                        }
                    }
                }
            }
        }

        // Toggles: Avatar, Badges, Labels
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("অন্যান্য প্রদর্শন সেটিংস", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ছবি / অবতার প্রদর্শন করুন", fontSize = 12.sp)
                        Switch(
                            checked = visual.showAvatar,
                            onCheckedChange = { onUpdateVisual(visual.copy(showAvatar = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ব্যাজসমূহ প্রদর্শন করুন (Status / Badges)", fontSize = 12.sp)
                        Switch(
                            checked = visual.showBadges,
                            onCheckedChange = { onUpdateVisual(visual.copy(showBadges = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ফিল্ডের লেবেল টেক্সট প্রদর্শন (e.g. পিতা: )", fontSize = 12.sp)
                        Switch(
                            checked = visual.showLabels,
                            onCheckedChange = { onUpdateVisual(visual.copy(showLabels = it)) }
                        )
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
                    Icon(Icons.Filled.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "শিক্ষার্থী তালিকার উপরে যে কুইক ফিল্টার চিপগুলো (শ্রেণি, লিঙ্গ, গ্রাম, স্ট্যাটাস, বিশেষ চাহিদা বা কাস্টম ফিল্ড) সোয়াইপযোগ্য এক সারিতে প্রদর্শিত হবে তা বাছাই করুন।",
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
                            Text(text = item.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "কী: ${item.key}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Row {
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
                            enabled = index > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
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
                            enabled = index < filters.size - 1
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. SAVED VIEWS PRESETS TAB
// ==========================================

@Composable
fun SavedViewsTab(
    allViews: List<StudentSavedView>,
    activeViewId: String,
    workingView: StudentSavedView,
    onSelectView: (StudentSavedView) -> Unit,
    onSaveNew: () -> Unit,
    onOverwriteCurrent: () -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ভিউ প্রিসেট ব্যবস্থাপনা",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "যেকোনো প্রিসেট নির্বাচন করে এডিট করুন এবং চাইলে পূর্বের প্রিসেটে ওভাররাইট করুন অথবা নতুন নামে সংরক্ষণ করুন।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onSaveNew,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("নতুন প্রিসেট", fontSize = 12.sp)
                    }
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
                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                imageVector = if (viewItem.isDefault) Icons.Filled.Star else Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = if (viewItem.isDefault) Color(0xFFF57F17) else MaterialTheme.colorScheme.primary
                            )

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = viewItem.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (viewItem.isDefault) {
                                        Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "ডিফল্ট",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (isActive) {
                                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "সক্রিয়",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "মোড: ${if (viewItem.visual.viewMode == "LIST") "লিস্ট টেবিল" else "কার্ড"} • অ্যাকশন: ${viewItem.actions.count { it.isEnabled }}টি",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (!viewItem.isDefault) {
                                IconButton(onClick = { onSetDefault(viewItem.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.StarBorder, contentDescription = "Set Default", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { onRename(viewItem.id, viewItem.name) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDuplicate(viewItem.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp))
                            }
                            if (allViews.size > 1 && !viewItem.isDefault) {
                                IconButton(onClick = { onDelete(viewItem.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete View", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    if (isActive) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "লেআউটে কোনো পরিবর্তন করলে সরাসরি এই প্রিসেটটিতে সেভ করুন:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onOverwriteCurrent,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ওভাররাইট / সেভ", fontSize = 11.sp)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "ব্যবহারকারীর রোল (Admin, Teacher, Staff, Viewer) অনুযায়ী শিক্ষার্থী তথ্য মুছে ফেলার অনুমতি, কাস্টম ফিল্ড ম্যানেজমেন্ট ও লেআউট পরিবর্তন নির্ধারণ করুন।",
                        fontSize = 12.sp
                    )
                }
            }
        }

        itemsIndexed(permissions) { index, rolePerm ->
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
                            text = "রোল: ${rolePerm.role}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("শিক্ষার্থী রেকর্ড ডিলিট করার অনুমতি", fontSize = 12.sp)
                        Switch(
                            checked = rolePerm.canDelete,
                            onCheckedChange = { chk ->
                                val list = permissions.toMutableList()
                                list[index] = rolePerm.copy(canDelete = chk)
                                onUpdatePermissions(list)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("কাস্টম ফিল্ড ও ফর্মুলা পরিচালনা", fontSize = 12.sp)
                        Switch(
                            checked = rolePerm.canManageCustomFields,
                            onCheckedChange = { chk ->
                                val list = permissions.toMutableList()
                                list[index] = rolePerm.copy(canManageCustomFields = chk)
                                onUpdatePermissions(list)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ভিউ ও কার্ড লেআউট কাস্টমাইজেশন", fontSize = 12.sp)
                        Switch(
                            checked = rolePerm.canCustomizeLayout,
                            onCheckedChange = { chk ->
                                val list = permissions.toMutableList()
                                list[index] = rolePerm.copy(canCustomizeLayout = chk)
                                onUpdatePermissions(list)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. DIALOGS: ADD FIELD & FIELD CONDITIONS
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
    var displayMode by remember(field) { mutableStateOf(field.displayMode) }

    val operators = listOf("EQUALS", "NOT_EQUALS", "CONTAINS", "IS_TRUE", "IS_FALSE", "GREATER_THAN", "LESS_THAN")
    val displayModes = listOf(
        "AUTO" to "স্বয়ংক্রিয় (Auto)",
        "TEXT" to "শুধু টেক্সট (Text)",
        "ICON_ONLY" to "শুধু আইকন (Icon)",
        "ICON_AND_TEXT" to "আইকন ও টেক্সট"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${field.label} প্রদর্শন ও শর্ত সেটিংস", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("প্রদর্শন মোড (Display Mode):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(displayModes) { (mode, label) ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { displayMode = mode },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

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
                        condition = if (hasCondition) ConditionalRule(fieldKey, operator, targetValue) else null,
                        displayMode = displayMode
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(visual.cornerRadiusDp.coerceAtLeast(12).dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(visual.elevationDp.coerceAtLeast(1).dp),
        border = BorderStroke(
            (if (visual.borderWidthDp > 0) visual.borderWidthDp else 1).dp,
            if (visual.borderWidthDp > 0) accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        if (visual.viewMode == "LIST") {
            // Table / List View Row - Responsive & Balanced
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visual.showAvatar) {
                        StudentAvatar(
                            photoUri = student.photoUri,
                            name = student.name,
                            gender = student.gender,
                            size = 36.dp,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        // Header
                        val headerText = renderAreaText(viewConfig.headerArea, student, customFields, formulaRules, visual.showLabels)
                        Text(
                            text = headerText.ifBlank { student.name },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Secondary
                        val secText = renderAreaText(viewConfig.secondaryArea, student, customFields, formulaRules, visual.showLabels)
                        if (secText.isNotBlank()) {
                            Text(
                                text = secText,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Side Badges Flow
                    FlowRow(
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        RenderAreaPills(viewConfig.rightRow1, student, customFields, formulaRules, visual.showLabels)
                        RenderAreaPills(viewConfig.rightRow2, student, customFields, formulaRules, visual.showLabels)
                    }
                }

                // Bottom Action Row - Bottom Right
                val enabledActions = viewConfig.actions.filter { it.isEnabled }
                if (enabledActions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        enabledActions.take(4).forEach { act ->
                            IconButton(
                                onClick = { onActionClick(act.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = getActionIcon(act.id),
                                    contentDescription = act.label,
                                    tint = if (act.id == "delete") MaterialTheme.colorScheme.error else accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Standard Rich Card View - Fully Responsive & Non-Overlapping
            val pad = when (visual.density) {
                "COMPACT" -> 8.dp
                "SPACIOUS" -> 14.dp
                else -> 10.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top Header Row: Avatar + Name & Subtitle + Top-Right Badges Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar / Photo Area
                    if (visual.showAvatar) {
                        StudentAvatar(
                            photoUri = student.photoUri,
                            name = student.name,
                            gender = student.gender,
                            size = if (visual.density == "COMPACT") 40.dp else 46.dp,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Student Name & Primary Info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val headerText = renderAreaText(viewConfig.headerArea, student, customFields, formulaRules, visual.showLabels)
                        Text(
                            text = headerText.ifBlank { student.name },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (visual.density == "COMPACT") 14.5.sp else 15.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Secondary row (Class / Roll / Academic year)
                        val secText = renderAreaText(viewConfig.secondaryArea, student, customFields, formulaRules, visual.showLabels)
                        if (secText.isNotBlank()) {
                            Text(
                                text = secText,
                                style = MaterialTheme.typography.bodySmall,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (visual.density == "COMPACT") 11.5.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top-right badges (Roll badge, Status pill, etc. wrapped with FlowRow to prevent layout breaking)
                    FlowRow(
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        RenderAreaPills(viewConfig.rightRow1, student, customFields, formulaRules, visual.showLabels)
                        RenderAreaPills(viewConfig.rightRow2, student, customFields, formulaRules, visual.showLabels)
                    }
                }

                // Middle Info Row (Third Area + Right Row 3 pills)
                val thirdText = renderAreaText(viewConfig.thirdArea, student, customFields, formulaRules, visual.showLabels)
                val hasRightRow3 = viewConfig.rightRow3.fields.any { it.isVisible }

                if (thirdText.isNotBlank() || hasRightRow3) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (thirdText.isNotBlank()) {
                                Text(
                                    text = thirdText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (hasRightRow3) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    RenderAreaPills(viewConfig.rightRow3, student, customFields, formulaRules, visual.showLabels)
                                }
                            }
                        }
                    }
                }

                // Bottom Row - Badges on Left, Action Buttons on Right
                val enabledActions = viewConfig.actions.filter { it.isEnabled }
                val visibleBadges = if (visual.showBadges) viewConfig.badgeArea.fields.filter { it.isVisible && (!it.hasCondition || it.condition?.isMet(student, customFields) == true) } else emptyList()

                if (enabledActions.isNotEmpty() || visibleBadges.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side of bottom row: Badges Flow (Wrap gracefully on small screens)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            visibleBadges.forEach { bf ->
                                val badgeVal = FormulaEvaluator.getFieldValue(student, bf.key, customFields, formulaRules)
                                if (badgeVal.isNotBlank()) {
                                    RenderStatusOrCustomBadge(
                                        key = bf.key,
                                        value = badgeVal,
                                        prefix = bf.customPrefix,
                                        suffix = bf.customSuffix
                                    )
                                }
                            }
                        }

                        // Right side of bottom row: Action Buttons
                        if (enabledActions.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                enabledActions.forEach { act ->
                                    FilledTonalIconButton(
                                        onClick = { onActionClick(act.id) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (act.id == "delete") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            contentColor = if (act.id == "delete") MaterialTheme.colorScheme.error else accentColor
                                        )
                                    ) {
                                        Icon(
                                            imageVector = getActionIcon(act.id),
                                            contentDescription = act.label,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. FIELD RENDERING & ICON UTILITIES
// ==========================================

@Composable
fun RenderStatusOrCustomBadge(
    key: String,
    value: String,
    prefix: String = "",
    suffix: String = ""
) {
    val cleanVal = value.trim()
    val isStatusField = key.equals("status", ignoreCase = true) || key.equals("স্ট্যাটাস", ignoreCase = true)

    val (bgCol, textCol) = when {
        isStatusField || cleanVal == "সক্রিয়" || cleanVal == "Current" || cleanVal == "active" || cleanVal == "বর্তমান" -> {
            Color(0xFFE8F5E9) to Color(0xFF1B5E20)
        }
        cleanVal == "সাবেক" || cleanVal == "Former" -> {
            Color(0xFFECEFF1) to Color(0xFF37474F)
        }
        cleanVal == "বদলীকৃত" || cleanVal == "Transferred" -> {
            Color(0xFFE1F5FE) to Color(0xFF0277BD)
        }
        cleanVal == "নিষ্ক্রিয়" || cleanVal == "Inactive" -> {
            Color(0xFFFFEBEE) to Color(0xFFC62828)
        }
        cleanVal == "অভ্যন্তরীণ" || cleanVal == "হ্যাঁ" -> {
            Color(0xFFE0F2F1) to Color(0xFF00695C)
        }
        cleanVal == "বহিরাগত" -> {
            Color(0xFFFFF3E0) to Color(0xFFE65100)
        }
        else -> {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        color = bgCol,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.6.dp, textCol.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isStatusField || cleanVal == "সক্রিয়" || cleanVal == "Current") {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(textCol)
                )
            }
            Text(
                text = "$prefix$cleanVal$suffix",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textCol
            )
        }
    }
}

@Composable
fun RenderAreaPills(
    area: DisplayAreaConfig,
    student: StudentEntity,
    customFields: List<CustomFieldEntity>,
    formulaRules: List<FormulaRuleEntity>,
    globalShowLabels: Boolean
) {
    val visibleFields = area.fields.filter { it.isVisible && (!it.hasCondition || it.condition?.isMet(student, customFields) == true) }
    if (visibleFields.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.wrapContentWidth()
    ) {
        visibleFields.forEach { f ->
            RenderSingleFieldPillOrIcon(f, student, customFields, formulaRules, globalShowLabels)
        }
    }
}

@Composable
fun RenderSingleFieldPillOrIcon(
    field: DisplayFieldConfig,
    student: StudentEntity,
    customFields: List<CustomFieldEntity>,
    formulaRules: List<FormulaRuleEntity>,
    globalShowLabels: Boolean
) {
    val raw = FormulaEvaluator.getFieldValue(student, field.key, customFields, formulaRules)
    if (raw.isBlank()) return

    val icon = getFieldIcon(field.key, raw)

    when (field.displayMode) {
        "ICON_ONLY" -> {
            if (icon != null) {
                Surface(
                    color = getIconBgColor(field.key, raw),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = field.label,
                            tint = getIconTintColor(field.key, raw),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${field.customPrefix}$raw${field.customSuffix}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    )
                }
            }
        }
        "ICON_AND_TEXT" -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = getIconTintColor(field.key, raw),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    val labelPrefix = if (globalShowLabels && field.showLabel && field.label.isNotBlank()) "${field.label}: " else ""
                    Text(
                        text = "${field.customPrefix}$labelPrefix$raw${field.customSuffix}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        else -> {
            // AUTO or TEXT
            if (field.key.equals("status", ignoreCase = true) || field.key.equals("স্ট্যাটাস", ignoreCase = true)) {
                RenderStatusOrCustomBadge(
                    key = field.key,
                    value = raw,
                    prefix = field.customPrefix,
                    suffix = field.customSuffix
                )
            } else if (field.key == "isSpecialNeeds" && (raw == "true" || raw == "হ্যাঁ")) {
                Surface(
                    color = Color(0xFFEDE7F6),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Filled.Accessible, contentDescription = "বিশেষ চাহিদা", tint = Color(0xFF512DA8), modifier = Modifier.size(12.dp))
                        Text("বিশেষ চাহিদা", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                    }
                }
            } else if (field.key == "gender") {
                val isBoy = com.example.util.GenderUtils.isBoy(raw)
                Surface(
                    color = if (isBoy) Color(0xFFE3F2FD) else Color(0xFFFCE4EC),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isBoy) Icons.Filled.Person else Icons.Filled.Face3,
                            contentDescription = raw,
                            tint = if (isBoy) Color(0xFF1976D2) else Color(0xFFC2185B),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = raw,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBoy) Color(0xFF1976D2) else Color(0xFFC2185B)
                        )
                    }
                }
            } else {
                val labelPrefix = if (globalShowLabels && field.showLabel && field.label.isNotBlank()) "${field.label}: " else ""
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${field.customPrefix}$labelPrefix$raw${field.customSuffix}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
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

fun getActionIcon(actionId: String): ImageVector {
    return when (actionId) {
        "view" -> Icons.Filled.Visibility
        "edit" -> Icons.Filled.Edit
        "delete" -> Icons.Filled.Delete
        "call" -> Icons.Filled.Phone
        "whatsapp" -> Icons.Filled.Chat
        "id_card" -> Icons.Filled.Badge
        "print" -> Icons.Filled.Print
        else -> Icons.Filled.TouchApp
    }
}

fun getFieldIcon(key: String, rawValue: String): ImageVector? {
    return when (key) {
        "gender" -> if (com.example.util.GenderUtils.isBoy(rawValue)) Icons.Filled.Person else Icons.Filled.Face3
        "isSpecialNeeds" -> Icons.Filled.Accessible
        "mobile" -> Icons.Filled.Phone
        "bloodGroup", "cf_blood" -> Icons.Filled.Bloodtype
        "village" -> Icons.Filled.LocationOn
        "rollNumber" -> Icons.Filled.Pin
        "studentClass" -> Icons.Filled.School
        "birthDate", "age" -> Icons.Filled.CalendarMonth
        "status" -> Icons.Filled.CheckCircle
        "category" -> Icons.Filled.Loyalty
        else -> null
    }
}

fun getIconBgColor(key: String, rawValue: String): Color {
    val clean = rawValue.trim()
    return when (key) {
        "gender" -> if (com.example.util.GenderUtils.isBoy(clean)) Color(0xFFE3F2FD) else Color(0xFFFCE4EC)
        "isSpecialNeeds" -> Color(0xFFEDE7F6)
        "bloodGroup", "cf_blood" -> Color(0xFFFFEBEE)
        "mobile" -> Color(0xFFE8F5E9)
        "status" -> when (clean) {
            "Current", "সক্রিয়", "বর্তমান", "active" -> Color(0xFFE8F5E9)
            "Former", "সাবেক" -> Color(0xFFECEFF1)
            "Transferred", "বদলীকৃত" -> Color(0xFFE1F5FE)
            "Inactive", "নিষ্ক্রিয়" -> Color(0xFFFFEBEE)
            else -> Color(0xFFE8F5E9)
        }
        else -> Color(0xFFF5F5F5)
    }
}

fun getIconTintColor(key: String, rawValue: String): Color {
    val clean = rawValue.trim()
    return when (key) {
        "gender" -> if (com.example.util.GenderUtils.isBoy(clean)) Color(0xFF1976D2) else Color(0xFFC2185B)
        "isSpecialNeeds" -> Color(0xFF512DA8)
        "bloodGroup", "cf_blood" -> Color(0xFFD32F2F)
        "mobile" -> Color(0xFF2E7D32)
        "status" -> when (clean) {
            "Current", "সক্রিয়", "বর্তমান", "active" -> Color(0xFF1B5E20)
            "Former", "সাবেক" -> Color(0xFF37474F)
            "Transferred", "বদলীকৃত" -> Color(0xFF0277BD)
            "Inactive", "নিষ্ক্রিয়" -> Color(0xFFC62828)
            else -> Color(0xFF1B5E20)
        }
        else -> Color(0xFF424242)
    }
}
