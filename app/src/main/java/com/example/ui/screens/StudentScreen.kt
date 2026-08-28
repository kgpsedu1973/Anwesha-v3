package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.util.FormulaEvaluator
import com.example.ui.components.*
import com.example.util.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(
    viewModel: MainViewModel,
    onNavigateToDashboard: () -> Unit = {}
) {
    val filteredStudents by viewModel.filteredStudents.collectAsState()
    val allStudents by viewModel.allStudents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterClass by viewModel.filterClass.collectAsState()
    val filterClasses by viewModel.filterClasses.collectAsState()
    val filterGender by viewModel.filterGender.collectAsState()
    val filterGenders by viewModel.filterGenders.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val filterStatuses by viewModel.filterStatuses.collectAsState()
    val filterVillage by viewModel.filterVillage.collectAsState()
    val filterVillages by viewModel.filterVillages.collectAsState()
    val filterSpecialNeeds by viewModel.filterSpecialNeeds.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val formulaRules by viewModel.formulaRules.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // View Configuration & Saved Views State
    var savedViewsVersion by remember { mutableStateOf(0) }
    val allSavedViews = remember(savedViewsVersion, customFields) {
        StudentViewConfigManager.loadAllSavedViews(context, customFields)
    }
    var activeView by remember(savedViewsVersion, customFields) {
        mutableStateOf(StudentViewConfigManager.loadActiveView(context, customFields))
    }

    // Modal dialogs state
    var showAddDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var viewingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var deletingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var idCardStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var showImportExportModal by remember { mutableStateOf(false) }
    var showFormLayoutManager by remember { mutableStateOf(false) }
    var showViewCustomizerDialog by remember { mutableStateOf(false) }
    var showDetailedFilterSheet by remember { mutableStateOf(false) }
    var showBulkEditDialog by remember { mutableStateOf(false) }

    // Multi-selection state
    val selectedStudentIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showAddDialog || editingStudent != null) {
            showAddDialog = false
            editingStudent = null
        } else if (viewingStudent != null) {
            viewingStudent = null
        } else if (deletingStudent != null) {
            deletingStudent = null
        } else if (idCardStudent != null) {
            idCardStudent = null
        } else if (showImportExportModal) {
            showImportExportModal = false
        } else if (showFormLayoutManager) {
            showFormLayoutManager = false
        } else if (showViewCustomizerDialog) {
            showViewCustomizerDialog = false
        } else if (showDetailedFilterSheet) {
            showDetailedFilterSheet = false
        } else if (showBulkEditDialog) {
            showBulkEditDialog = false
        } else if (isSelectionMode) {
            isSelectionMode = false
            selectedStudentIds.clear()
        } else {
            onNavigateToDashboard()
        }
    }

    // Additional Custom Field Filter state (Dynamic Filter Map supporting multi-values)
    val customFilterValues = remember { mutableStateMapOf<String, Set<String>>() }

    // Dynamic Filtered list combining standard and custom field filters
    val dynamicallyFilteredStudents = remember(filteredStudents, customFilterValues, customFields, formulaRules) {
        if (customFilterValues.isEmpty()) {
            filteredStudents
        } else {
            filteredStudents.filter { student ->
                customFilterValues.entries.all { (fieldKey, filterVals) ->
                    if (filterVals.isEmpty() || filterVals.contains("ALL")) true
                    else {
                        val studentVal = FormulaEvaluator.getFieldValue(student, fieldKey, customFields, formulaRules)
                        filterVals.contains(studentVal)
                    }
                }
            }
        }
    }

    // Calculate Active Filters Count for the Filter Button Badge
    val activeFiltersCount = remember(filterClass, filterClasses, filterGender, filterGenders, filterStatus, filterStatuses, filterVillage, filterVillages, filterSpecialNeeds, customFilterValues) {
        var count = 0
        if (filterClasses.isNotEmpty()) count += filterClasses.size
        else if (filterClass != null && filterClass != "ALL") count++

        if (filterGenders.isNotEmpty()) count += filterGenders.size
        else if (filterGender != null && filterGender != "ALL") count++

        if (filterStatuses.isNotEmpty()) {
            if (!filterStatuses.contains("ALL") && (filterStatuses != setOf("Current"))) count += filterStatuses.size
        } else if (filterStatus != null && filterStatus != "Current" && filterStatus != "ALL") count++

        if (filterVillages.isNotEmpty()) count += filterVillages.size
        else if (filterVillage != null && filterVillage != "ALL") count++

        if (filterSpecialNeeds != null) count++
        count += customFilterValues.values.sumOf { it.size }
        count
    }

    val classOptions = listOf("ALL", "প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val statusOptions = listOf("Current", "Former", "Transferred", "Inactive", "ALL")
    val genderOptions = listOf("ALL", "ছাত্র", "ছাত্রী")

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingStudent = null; showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Student") },
                text = { Text("নতুন শিক্ষার্থী") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_student")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Unified Compact Control Bar & View Preset Strip (No empty/duplicate row)
            Surface(
                tonalElevation = 1.5.dp,
                shadowElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ==========================================
                        // SAVED VIEWS PRESET STRIP (Scrollable)
                        // ==========================================
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ভিউ:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 2.dp)
                            )

                            allSavedViews.forEach { viewPreset ->
                                val isSelected = viewPreset.id == activeView.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        activeView = viewPreset
                                        StudentViewConfigManager.setActiveViewId(context, viewPreset.id)
                                        if (viewPreset.filterStatus != null) {
                                            viewModel.filterStatus.value = viewPreset.filterStatus
                                            viewModel.filterStatuses.value = if (viewPreset.filterStatus == "ALL") emptySet() else setOf(viewPreset.filterStatus!!)
                                        }
                                        if (viewPreset.filterClass != null) {
                                            viewModel.filterClass.value = viewPreset.filterClass
                                            viewModel.filterClasses.value = if (viewPreset.filterClass == "ALL") emptySet() else setOf(viewPreset.filterClass!!)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = viewPreset.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )
                            }

                            IconButton(
                                onClick = { showViewCustomizerDialog = true },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = "Add View Preset", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // ==========================================
                        // TOOLBAR ACTION BUTTONS (Compact)
                        // ==========================================
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // 1. Detailed Filter Button with Badged Count
                            BadgedBox(
                                badge = {
                                    if (activeFiltersCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text(BanglaUtils.toBanglaDigits(activeFiltersCount))
                                        }
                                    }
                                }
                            ) {
                                FilledTonalIconButton(
                                    onClick = { showDetailedFilterSheet = true },
                                    modifier = Modifier.size(34.dp).testTag("btn_open_filters_sheet")
                                ) {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = "ফিল্টার",
                                        tint = if (activeFiltersCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            // 2. Multi-Select Toggle Button
                            FilledTonalIconButton(
                                onClick = {
                                    isSelectionMode = !isSelectionMode
                                    if (!isSelectionMode) selectedStudentIds.clear()
                                },
                                modifier = Modifier.size(34.dp).testTag("btn_toggle_selection_mode")
                            ) {
                                Icon(
                                    if (isSelectionMode) Icons.Filled.ChecklistRtl else Icons.Filled.Checklist,
                                    contentDescription = "মাল্টি সিলেট",
                                    tint = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // 3. Customize View Button
                            FilledTonalIconButton(
                                onClick = { showViewCustomizerDialog = true },
                                modifier = Modifier.size(34.dp).testTag("btn_customize_student_view")
                            ) {
                                Icon(Icons.Filled.DashboardCustomize, contentDescription = "ভিউ কাস্টমাইজ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            }

                            // 4. Form Layout Button
                            IconButton(
                                onClick = { showFormLayoutManager = true },
                                modifier = Modifier.size(30.dp).testTag("btn_form_layout_manager")
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = "ফর্ম বিন্যাস", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }

                            // 5. Import / Export Button
                            IconButton(
                                onClick = { showImportExportModal = true },
                                modifier = Modifier.size(30.dp).testTag("btn_import_export")
                            ) {
                                Icon(Icons.Filled.ImportExport, contentDescription = "Import/Export", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // ==========================================
                    // COMPACT SINGLE-ROW HORIZONTALLY SCROLLABLE QUICK FILTERS
                    // ==========================================
                    val enabledQuickFilters = activeView.quickFilters.filter { it.isEnabled }
                    if (enabledQuickFilters.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Reset Chip if active filters
                            if (activeFiltersCount > 0 || searchQuery.isNotBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.searchQuery.value = ""
                                            viewModel.filterClass.value = null
                                            viewModel.filterClasses.value = emptySet()
                                            viewModel.filterStatus.value = "Current"
                                            viewModel.filterStatuses.value = setOf("Current")
                                            viewModel.filterGender.value = null
                                            viewModel.filterGenders.value = emptySet()
                                            viewModel.filterVillage.value = null
                                            viewModel.filterVillages.value = emptySet()
                                            viewModel.filterSpecialNeeds.value = null
                                            customFilterValues.clear()
                                        },
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Reset", modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("রিসেট", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }

                            // Dynamic Quick Filter Chips from View Settings (with multi-select support)
                            enabledQuickFilters.forEach { qf ->
                                when (qf.key) {
                                    "class" -> {
                                        val effectiveSelectedClasses = remember(filterClasses, filterClass) {
                                            if (filterClasses.isNotEmpty()) filterClasses
                                            else if (filterClass != null && filterClass != "ALL") setOf(filterClass!!)
                                            else emptySet()
                                        }
                                        ClassDropdownQuickFilter(
                                            selectedClasses = effectiveSelectedClasses,
                                            options = classOptions,
                                            onToggleClass = { opt ->
                                                val newSet = effectiveSelectedClasses.toMutableSet()
                                                if (newSet.contains(opt)) newSet.remove(opt) else newSet.add(opt)
                                                viewModel.filterClasses.value = newSet
                                                viewModel.filterClass.value = if (newSet.size == 1) newSet.first() else null
                                            },
                                            onClearClasses = {
                                                viewModel.filterClasses.value = emptySet()
                                                viewModel.filterClass.value = null
                                            }
                                        )
                                    }
                                    "gender" -> {
                                        val effectiveSelectedGenders = remember(filterGenders, filterGender) {
                                            if (filterGenders.isNotEmpty()) filterGenders
                                            else if (filterGender != null && filterGender != "ALL") setOf(filterGender!!)
                                            else emptySet()
                                        }
                                        GenderDropdownQuickFilter(
                                            selectedGenders = effectiveSelectedGenders,
                                            options = genderOptions,
                                            onToggleGender = { opt ->
                                                val newSet = effectiveSelectedGenders.toMutableSet()
                                                if (newSet.contains(opt)) newSet.remove(opt) else newSet.add(opt)
                                                viewModel.filterGenders.value = newSet
                                                viewModel.filterGender.value = if (newSet.size == 1) newSet.first() else null
                                            },
                                            onClearGenders = {
                                                viewModel.filterGenders.value = emptySet()
                                                viewModel.filterGender.value = null
                                            }
                                        )
                                    }
                                    "status" -> {
                                        val effectiveSelectedStatuses = remember(filterStatuses, filterStatus) {
                                            if (filterStatuses.isNotEmpty()) filterStatuses
                                            else if (filterStatus != null && filterStatus != "ALL") setOf(filterStatus!!)
                                            else emptySet()
                                        }
                                        StatusDropdownQuickFilter(
                                            selectedStatuses = effectiveSelectedStatuses,
                                            options = statusOptions,
                                            onToggleStatus = { opt ->
                                                val newSet = effectiveSelectedStatuses.toMutableSet()
                                                if (newSet.contains(opt)) newSet.remove(opt) else newSet.add(opt)
                                                viewModel.filterStatuses.value = newSet
                                                viewModel.filterStatus.value = if (newSet.size == 1) newSet.first() else null
                                            },
                                            onClearStatuses = {
                                                viewModel.filterStatuses.value = emptySet()
                                                viewModel.filterStatus.value = "ALL"
                                            }
                                        )
                                    }
                                    "village" -> {
                                        val villages = remember(allStudents) {
                                            allStudents.map { it.village }.filter { it.isNotBlank() }.distinct()
                                        }
                                        val effectiveSelectedVillages = remember(filterVillages, filterVillage) {
                                            if (filterVillages.isNotEmpty()) filterVillages
                                            else if (filterVillage != null && filterVillage != "ALL") setOf(filterVillage!!)
                                            else emptySet()
                                        }
                                        VillageDropdownQuickFilter(
                                            selectedVillages = effectiveSelectedVillages,
                                            villages = villages,
                                            onToggleVillage = { opt ->
                                                val newSet = effectiveSelectedVillages.toMutableSet()
                                                if (newSet.contains(opt)) newSet.remove(opt) else newSet.add(opt)
                                                viewModel.filterVillages.value = newSet
                                                viewModel.filterVillage.value = if (newSet.size == 1) newSet.first() else null
                                            },
                                            onClearVillages = {
                                                viewModel.filterVillages.value = emptySet()
                                                viewModel.filterVillage.value = null
                                            }
                                        )
                                    }
                                    "specialNeeds" -> {
                                        SpecialNeedsQuickFilter(
                                            currentSpecialNeeds = filterSpecialNeeds,
                                            onToggle = {
                                                viewModel.filterSpecialNeeds.value = when (filterSpecialNeeds) {
                                                    null -> true
                                                    true -> false
                                                    false -> null
                                                }
                                            }
                                        )
                                    }
                                    else -> {
                                        // Custom Field Dropdown Quick Filter
                                        val cfKey = qf.key.removePrefix("cf_")
                                        val matchedCf = customFields.find { it.id == cfKey || it.name == qf.label }
                                        if (matchedCf != null && !matchedCf.isCalculated) {
                                            CustomFieldDropdownQuickFilter(
                                                customField = matchedCf,
                                                selectedValues = customFilterValues[matchedCf.id] ?: emptySet(),
                                                onToggleValue = { opt ->
                                                    val currentSet = (customFilterValues[matchedCf.id] ?: emptySet()).toMutableSet()
                                                    if (currentSet.contains(opt)) currentSet.remove(opt) else currentSet.add(opt)
                                                    if (currentSet.isEmpty()) customFilterValues.remove(matchedCf.id)
                                                    else customFilterValues[matchedCf.id] = currentSet
                                                },
                                                onClearValues = {
                                                    customFilterValues.remove(matchedCf.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Filter Customization button at end of swipeable row
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showViewCustomizerDialog = true },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("ফিল্টার কনফিগার", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Student Count Summary & Selection Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "মোট শিক্ষার্থী: ${BanglaUtils.toBanglaDigits(dynamicallyFilteredStudents.size)} জন",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isSelectionMode) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "নির্বাচিত: ${BanglaUtils.toBanglaDigits(selectedStudentIds.size)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Multi-select toggle
                    TextButton(onClick = {
                        isSelectionMode = !isSelectionMode
                        if (!isSelectionMode) selectedStudentIds.clear()
                    }) {
                        Text(if (isSelectionMode) "সিলেকশন বাতিল" else "সিলেক্ট মোড", fontSize = 11.sp)
                    }
                }
            }

            // Multi-selection Action Toolbar (if selection mode active or items selected)
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Checkbox(
                                checked = selectedStudentIds.size == dynamicallyFilteredStudents.size && dynamicallyFilteredStudents.isNotEmpty(),
                                onCheckedChange = { chk ->
                                    if (chk) {
                                        selectedStudentIds.clear()
                                        selectedStudentIds.addAll(dynamicallyFilteredStudents.map { it.id })
                                    } else {
                                        selectedStudentIds.clear()
                                    }
                                }
                            )
                            Text(
                                text = "${BanglaUtils.toBanglaDigits(selectedStudentIds.size)} / ${BanglaUtils.toBanglaDigits(dynamicallyFilteredStudents.size)} নির্বাচিত",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            // 1. Bulk Field Value Change Button
                            FilledTonalButton(
                                onClick = { showBulkEditDialog = true },
                                enabled = selectedStudentIds.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_bulk_edit_fields")
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("মান পরিবর্তন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // 2. Recalculate Formulas Button
                            FilledTonalButton(
                                onClick = {
                                    viewModel.bulkRecalculateFormulasForStudents(selectedStudentIds.toSet())
                                },
                                enabled = selectedStudentIds.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_recalculate_formulas")
                            ) {
                                Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("হিসাব রিফ্রেশ", fontSize = 11.sp)
                            }

                            // 3. Share Button
                            Button(
                                onClick = {
                                    val selectedList = allStudents.filter { selectedStudentIds.contains(it.id) }
                                    val textSummary = selectedList.joinToString("\n") {
                                        "${it.name} | শ্রেণি: ${it.studentClass} | রোল: ${it.rollNumber} | মোবাইল: ${it.mobile}"
                                    }
                                    viewModel.shareCsvContent("selected_students.txt", textSummary, "নির্বাচিত শিক্ষার্থীর তথ্য")
                                },
                                enabled = selectedStudentIds.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("শেয়ার", fontSize = 11.sp)
                            }

                            // 4. Delete Button
                            Button(
                                onClick = {
                                    val toDelete = allStudents.filter { selectedStudentIds.contains(it.id) }
                                    toDelete.forEach { viewModel.deleteStudent(it) }
                                    selectedStudentIds.clear()
                                    isSelectionMode = false
                                },
                                enabled = selectedStudentIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("মুছুন", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Student List / Card View
            if (dynamicallyFilteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PersonOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "কোন শিক্ষার্থী পাওয়া যায়নি",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "অনুগ্রহ করে নতুন শিক্ষার্থী যোগ করুন বা অনুসন্ধান ফিল্টার পরিবর্তন করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(activeView.visual.spacingDp.dp)
                ) {
                    items(dynamicallyFilteredStudents, key = { it.id }) { student ->
                        val isSelected = selectedStudentIds.contains(student.id)

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { chk ->
                                        if (chk) selectedStudentIds.add(student.id)
                                        else selectedStudentIds.remove(student.id)
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }

                            Box(modifier = Modifier.weight(1f).clickable {
                                if (isSelectionMode) {
                                    if (isSelected) selectedStudentIds.remove(student.id)
                                    else selectedStudentIds.add(student.id)
                                } else {
                                    viewingStudent = student
                                }
                            }) {
                                DynamicStudentCard(
                                    student = student,
                                    viewConfig = activeView,
                                    customFields = customFields,
                                    formulaRules = formulaRules,
                                    onActionClick = { actionId ->
                                        when (actionId) {
                                            "view" -> viewingStudent = student
                                            "edit" -> {
                                                editingStudent = student
                                                showAddDialog = true
                                            }
                                            "delete" -> deletingStudent = student
                                            "call" -> {
                                                if (student.mobile.isNotBlank()) {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.mobile.trim()}"))
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    try { context.startActivity(intent) } catch (e: Exception) { }
                                                }
                                            }
                                            "whatsapp" -> {
                                                if (student.mobile.isNotBlank()) {
                                                    val cleanNum = student.mobile.replace("-", "").replace(" ", "").trim()
                                                    val finalNum = if (cleanNum.startsWith("0")) "+88$cleanNum" else cleanNum
                                                    val url = "https://api.whatsapp.com/send?phone=$finalNum"
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    try { context.startActivity(intent) } catch (e: Exception) { }
                                                }
                                            }
                                            "id_card" -> idCardStudent = student
                                            "print" -> {
                                                val printable = "শিক্ষার্থী নাম: ${student.name}\nশ্রেণি: ${student.studentClass}\nরোল: ${student.rollNumber}\nআইডি: ${student.id}\nপিতা: ${student.fatherName}\nমোবাইল: ${student.mobile}\nগ্রাম: ${student.village}"
                                                viewModel.shareCsvContent("student_${student.id}.txt", printable, "শিক্ষার্থী তথ্য")
                                            }
                                            "select" -> {
                                                if (isSelected) selectedStudentIds.remove(student.id)
                                                else selectedStudentIds.add(student.id)
                                                isSelectionMode = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    // ==========================================
    // DETAILED COMPREHENSIVE FILTER MODAL
    // ==========================================
    if (showDetailedFilterSheet) {
        val effectiveSelectedClasses = remember(filterClasses, filterClass) {
            if (filterClasses.isNotEmpty()) filterClasses
            else if (filterClass != null && filterClass != "ALL") setOf(filterClass!!)
            else emptySet()
        }
        val effectiveSelectedGenders = remember(filterGenders, filterGender) {
            if (filterGenders.isNotEmpty()) filterGenders
            else if (filterGender != null && filterGender != "ALL") setOf(filterGender!!)
            else emptySet()
        }
        val effectiveSelectedStatuses = remember(filterStatuses, filterStatus) {
            if (filterStatuses.isNotEmpty()) filterStatuses
            else if (filterStatus != null && filterStatus != "ALL") setOf(filterStatus!!)
            else emptySet()
        }
        val effectiveSelectedVillages = remember(filterVillages, filterVillage) {
            if (filterVillages.isNotEmpty()) filterVillages
            else if (filterVillage != null && filterVillage != "ALL") setOf(filterVillage!!)
            else emptySet()
        }

        StudentDetailedFilterModal(
            selectedClasses = effectiveSelectedClasses,
            selectedGenders = effectiveSelectedGenders,
            selectedStatuses = effectiveSelectedStatuses,
            selectedVillages = effectiveSelectedVillages,
            currentSpecialNeeds = filterSpecialNeeds,
            customFilterValues = customFilterValues,
            allStudents = allStudents,
            customFields = customFields,
            matchCount = dynamicallyFilteredStudents.size,
            onDismiss = { showDetailedFilterSheet = false },
            onApply = { newClasses, newGenders, newStatuses, newVillages, newSpecialNeeds, newCustomFilters ->
                viewModel.filterClasses.value = newClasses
                viewModel.filterClass.value = if (newClasses.size == 1) newClasses.first() else null
                viewModel.filterGenders.value = newGenders
                viewModel.filterGender.value = if (newGenders.size == 1) newGenders.first() else null
                viewModel.filterStatuses.value = newStatuses
                viewModel.filterStatus.value = if (newStatuses.size == 1) newStatuses.first() else (if (newStatuses.isEmpty()) "ALL" else null)
                viewModel.filterVillages.value = newVillages
                viewModel.filterVillage.value = if (newVillages.size == 1) newVillages.first() else null
                viewModel.filterSpecialNeeds.value = newSpecialNeeds
                customFilterValues.clear()
                customFilterValues.putAll(newCustomFilters)
                showDetailedFilterSheet = false
            },
            onReset = {
                viewModel.filterClass.value = null
                viewModel.filterClasses.value = emptySet()
                viewModel.filterGender.value = null
                viewModel.filterGenders.value = emptySet()
                viewModel.filterStatus.value = "Current"
                viewModel.filterStatuses.value = setOf("Current")
                viewModel.filterVillage.value = null
                viewModel.filterVillages.value = emptySet()
                viewModel.filterSpecialNeeds.value = null
                customFilterValues.clear()
                showDetailedFilterSheet = false
            }
        )
    }

    // ==========================================
    // MODALS & DIALOGS
    // ==========================================

    // Student View Customizer Panel (Full Visual Layout Manager!)
    if (showViewCustomizerDialog) {
        StudentViewCustomizerDialog(
            currentView = activeView,
            allSavedViews = allSavedViews,
            customFields = customFields,
            formulaRules = formulaRules,
            sampleStudent = allStudents.firstOrNull(),
            onDismiss = { showViewCustomizerDialog = false },
            onApplyView = { updatedView ->
                activeView = updatedView
                StudentViewConfigManager.saveActiveView(context, updatedView, customFields)
                savedViewsVersion++
            },
            onSaveNewPreset = { newPreset ->
                StudentViewConfigManager.saveActiveView(context, newPreset, customFields)
                activeView = newPreset
                savedViewsVersion++
            },
            onOverwritePreset = { updatedView ->
                StudentViewConfigManager.overwritePreset(context, updatedView.id, updatedView, customFields)
                activeView = updatedView
                savedViewsVersion++
            },
            onRenamePreset = { presetId, newName ->
                StudentViewConfigManager.renamePreset(context, presetId, newName, customFields)
                if (activeView.id == presetId) {
                    activeView = activeView.copy(name = newName)
                }
                savedViewsVersion++
            },
            onDuplicatePreset = { presetId ->
                val newPreset = StudentViewConfigManager.duplicatePreset(context, presetId, customFields)
                activeView = newPreset
                savedViewsVersion++
            },
            onDeletePreset = { presetId ->
                val nextActive = StudentViewConfigManager.deletePreset(context, presetId, customFields)
                activeView = nextActive
                savedViewsVersion++
            },
            onSetDefaultPreset = { presetId ->
                val updated = allSavedViews.map { it.copy(isDefault = it.id == presetId) }
                StudentViewConfigManager.saveAllViews(context, updated)
                savedViewsVersion++
            }
        )
    }

    // Add / Edit Student Dialog
    if (showAddDialog) {
        StudentAddEditDialog(
            student = editingStudent,
            allStudents = allStudents,
            customFields = customFields,
            onDismiss = { showAddDialog = false },
            onSave = { updatedStudent ->
                if (editingStudent == null) {
                    viewModel.insertStudent(updatedStudent)
                } else {
                    viewModel.updateStudent(updatedStudent)
                }
                showAddDialog = false
            }
        )
    }

    // Student Detail Profile Dialog
    if (viewingStudent != null) {
        val baseDateConfig by viewModel.baseDateConfig.collectAsState()
        StudentDetailDialog(
            student = viewingStudent!!,
            category = viewModel.getStudentCategory(viewingStudent!!),
            customFields = customFields,
            formulaRules = formulaRules,
            baseEndDate = baseDateConfig.endDate,
            onDismiss = { viewingStudent = null },
            onEdit = {
                editingStudent = viewingStudent
                viewingStudent = null
                showAddDialog = true
            },
            onIdCard = {
                idCardStudent = viewingStudent
                viewingStudent = null
            }
        )
    }

    // Student ID Card Dialog
    if (idCardStudent != null) {
        StudentIdCardDialog(
            student = idCardStudent!!,
            category = viewModel.getStudentCategory(idCardStudent!!),
            onDismiss = { idCardStudent = null },
            onShare = {
                val cardSummary = "আইডি কার্ড\nনাম: ${idCardStudent!!.name}\nশ্রেণি: ${idCardStudent!!.studentClass}\nরোল: ${idCardStudent!!.rollNumber}\nআইডি: ${idCardStudent!!.id}\nমোবাইল: ${idCardStudent!!.mobile}"
                viewModel.shareCsvContent("id_card_${idCardStudent!!.id}.txt", cardSummary, "শিক্ষার্থী আইডি কার্ড")
            }
        )
    }

    // Delete Confirmation Dialog
    if (deletingStudent != null) {
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("শিক্ষার্থী তথ্য অপসরণ") },
            text = { Text("${deletingStudent!!.name}-এর তথ্য কি স্থায়ীভাবে মুছে ফেলতে চান?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStudent(deletingStudent!!)
                        deletingStudent = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("হ্যাঁ, মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Import / Export Dialog (CSV & PDF)
    if (showImportExportModal) {
        DataImportExportDialog(
            viewModel = viewModel,
            onDismiss = { showImportExportModal = false }
        )
    }

    // Form Layout Manager Dialog
    if (showFormLayoutManager) {
        FormLayoutManagerDialog(
            customFields = customFields,
            onDismiss = { showFormLayoutManager = false },
            onLayoutSaved = { }
        )
    }

    // Student Multi-Select Bulk Edit Field Dialog
    if (showBulkEditDialog && selectedStudentIds.isNotEmpty()) {
        val selectedList = allStudents.filter { selectedStudentIds.contains(it.id) }
        StudentBulkEditFieldDialog(
            selectedStudents = selectedList,
            customFields = customFields,
            formulaRules = formulaRules,
            onDismiss = { showBulkEditDialog = false },
            onApplyBulkUpdate = { fieldKey, newValue, isCustomField ->
                viewModel.bulkUpdateStudentsField(selectedStudentIds.toSet(), fieldKey, newValue, isCustomField)
                selectedStudentIds.clear()
                isSelectionMode = false
            },
            onRecalculateFormulas = {
                viewModel.bulkRecalculateFormulasForStudents(selectedStudentIds.toSet())
            }
        )
    }
}

// ==========================================
// COMPACT QUICK FILTER DROPDOWN COMPONENTS
// ==========================================

@Composable
fun ClassDropdownQuickFilter(
    selectedClasses: Set<String>,
    options: List<String>,
    onToggleClass: (String) -> Unit,
    onClearClasses: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedClasses.isNotEmpty()

    val labelText = when {
        selectedClasses.isEmpty() -> "শ্রেণি: সকল"
        selectedClasses.size == 1 -> "শ্রেণি: ${selectedClasses.first()}"
        else -> "শ্রেণি: ${selectedClasses.joinToString(", ")}"
    }

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = labelText,
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("সকল শ্রেণি", fontSize = 12.sp, fontWeight = if (selectedClasses.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onClearClasses()
                    expanded = false
                },
                leadingIcon = if (selectedClasses.isEmpty()) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                } else null
            )
            options.filter { it != "ALL" }.forEach { opt ->
                val isSelected = selectedClasses.contains(opt)
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onToggleClass(opt)
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun GenderDropdownQuickFilter(
    selectedGenders: Set<String>,
    options: List<String>,
    onToggleGender: (String) -> Unit,
    onClearGenders: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedGenders.isNotEmpty()

    val labelText = when {
        selectedGenders.isEmpty() -> "লিঙ্গ: সকল"
        selectedGenders.size == 1 -> "লিঙ্গ: ${selectedGenders.first()}"
        else -> "লিঙ্গ: ${selectedGenders.joinToString(", ")}"
    }

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = labelText,
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("সকল লিঙ্গ", fontSize = 12.sp, fontWeight = if (selectedGenders.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onClearGenders()
                    expanded = false
                },
                leadingIcon = if (selectedGenders.isEmpty()) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                } else null
            )
            options.filter { it != "ALL" }.forEach { opt ->
                val isSelected = selectedGenders.contains(opt)
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onToggleGender(opt)
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun StatusDropdownQuickFilter(
    selectedStatuses: Set<String>,
    options: List<String>,
    onToggleStatus: (String) -> Unit,
    onClearStatuses: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedStatuses.isNotEmpty() && selectedStatuses != setOf("Current") && !selectedStatuses.contains("ALL")

    val displayLabel = when {
        selectedStatuses.isEmpty() || selectedStatuses.contains("ALL") -> "স্ট্যাটাস: সকল"
        selectedStatuses == setOf("Current") -> "স্ট্যাটাস: বর্তমান"
        else -> {
            val names = selectedStatuses.map {
                when (it) {
                    "Current" -> "বর্তমান"
                    "Former" -> "সাবেক"
                    "Transferred" -> "বদলীকৃত"
                    "Inactive" -> "নিষ্ক্রিয়"
                    else -> it
                }
            }
            "স্ট্যাটাস: ${names.joinToString(", ")}"
        }
    }

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = { Text(displayLabel, fontSize = 11.sp) },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("সকল স্ট্যাটাস", fontSize = 12.sp, fontWeight = if (selectedStatuses.isEmpty() || selectedStatuses.contains("ALL")) FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onClearStatuses()
                    expanded = false
                },
                leadingIcon = if (selectedStatuses.isEmpty() || selectedStatuses.contains("ALL")) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                } else null
            )
            options.filter { it != "ALL" }.forEach { opt ->
                val label = when (opt) {
                    "Current" -> "বর্তমান (Current)"
                    "Former" -> "সাবেক (Former)"
                    "Transferred" -> "বদলীকৃত (Transferred)"
                    "Inactive" -> "নিষ্ক্রিয় (Inactive)"
                    else -> opt
                }
                val isSelected = selectedStatuses.contains(opt)
                DropdownMenuItem(
                    text = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onToggleStatus(opt)
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun VillageDropdownQuickFilter(
    selectedVillages: Set<String>,
    villages: List<String>,
    onToggleVillage: (String) -> Unit,
    onClearVillages: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedVillages.isNotEmpty()

    val labelText = when {
        selectedVillages.isEmpty() -> "গ্রাম: সকল"
        selectedVillages.size == 1 -> "গ্রাম: ${selectedVillages.first()}"
        else -> "গ্রাম: ${selectedVillages.joinToString(", ")}"
    }

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = labelText,
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("সকল গ্রাম", fontSize = 12.sp, fontWeight = if (selectedVillages.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onClearVillages()
                    expanded = false
                },
                leadingIcon = if (selectedVillages.isEmpty()) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                } else null
            )
            villages.take(25).forEach { opt ->
                val isSelected = selectedVillages.contains(opt)
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onToggleVillage(opt)
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun SpecialNeedsQuickFilter(
    currentSpecialNeeds: Boolean?,
    onToggle: () -> Unit
) {
    val isFiltered = currentSpecialNeeds != null
    val label = when (currentSpecialNeeds) {
        true -> "চাহিদা: বিশেষ"
        false -> "চাহিদা: সাধারণ"
        null -> "চাহিদা: সকল"
    }

    FilterChip(
        selected = isFiltered,
        onClick = onToggle,
        label = { Text(label, fontSize = 11.sp) },
        leadingIcon = {
            Icon(Icons.Filled.Accessible, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    )
}

@Composable
fun CustomFieldDropdownQuickFilter(
    customField: CustomFieldEntity,
    selectedValues: Set<String>,
    onToggleValue: (String) -> Unit,
    onClearValues: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedValues.isNotEmpty()
    val options = customField.optionsList

    val labelText = when {
        selectedValues.isEmpty() -> "${customField.name}: সকল"
        selectedValues.size == 1 -> "${customField.name}: ${selectedValues.first()}"
        else -> "${customField.name}: ${selectedValues.joinToString(", ")}"
    }

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = labelText,
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("সকল (${customField.name})", fontSize = 12.sp, fontWeight = if (selectedValues.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onClearValues()
                    expanded = false
                },
                leadingIcon = if (selectedValues.isEmpty()) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                } else null
            )
            options.forEach { opt ->
                val isSelected = selectedValues.contains(opt)
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onToggleValue(opt)
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

// ==========================================
// DETAILED COMPREHENSIVE FILTER MODAL DIALOG
// ==========================================

@Composable
fun StudentDetailedFilterModal(
    selectedClasses: Set<String>,
    selectedGenders: Set<String>,
    selectedStatuses: Set<String>,
    selectedVillages: Set<String>,
    currentSpecialNeeds: Boolean?,
    customFilterValues: Map<String, Set<String>>,
    allStudents: List<StudentEntity>,
    customFields: List<CustomFieldEntity>,
    matchCount: Int,
    onDismiss: () -> Unit,
    onApply: (Set<String>, Set<String>, Set<String>, Set<String>, Boolean?, Map<String, Set<String>>) -> Unit,
    onReset: () -> Unit
) {
    val selClasses = remember { mutableStateListOf<String>().apply { addAll(selectedClasses) } }
    val selGenders = remember { mutableStateListOf<String>().apply { addAll(selectedGenders) } }
    val selStatuses = remember { mutableStateListOf<String>().apply { addAll(selectedStatuses) } }
    val selVillages = remember { mutableStateListOf<String>().apply { addAll(selectedVillages) } }
    var selSpecialNeeds by remember { mutableStateOf(currentSpecialNeeds) }
    val tempCustomFilters = remember { mutableStateMapOf<String, Set<String>>().apply { putAll(customFilterValues) } }

    val classOptions = listOf("প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val genderOptions = listOf("ছাত্র", "ছাত্রী")
    val statusOptions = listOf("Current" to "বর্তমান", "Former" to "সাবেক", "Transferred" to "বদলীকৃত", "Inactive" to "নিষ্ক্রিয়")
    val villages = remember(allStudents) { allStudents.map { it.village }.filter { it.isNotBlank() }.distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("শিক্ষার্থী ফিল্টার প্যানেল", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "প্রাপ্ত: ${BanglaUtils.toBanglaDigits(matchCount)} জন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Class Filter (Multi-Select)
                Text("শ্রেণি নির্বাচন (একাধিক নির্বাচনযোগ্য):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        val isAll = selClasses.isEmpty()
                        FilterChip(
                            selected = isAll,
                            onClick = { selClasses.clear() },
                            label = { Text("সকল শ্রেণি", fontSize = 11.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isAll) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                            } else null
                        )
                    }
                    items(classOptions) { c ->
                        val isSelected = selClasses.contains(c)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selClasses.remove(c)
                                else selClasses.add(c)
                            },
                            label = { Text(c, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                            } else null
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. Gender Filter (Multi-Select)
                Text("লিঙ্গ (একাধিক নির্বাচনযোগ্য):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isAll = selGenders.isEmpty()
                    FilterChip(
                        selected = isAll,
                        onClick = { selGenders.clear() },
                        label = { Text("সকল লিঙ্গ", fontSize = 11.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = if (isAll) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                        } else null
                    )
                    genderOptions.forEach { g ->
                        val isSelected = selGenders.contains(g)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selGenders.remove(g)
                                else selGenders.add(g)
                            },
                            label = { Text(g, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                            } else null
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 3. Status Filter (Multi-Select)
                Text("শিক্ষার্থীর স্ট্যাটাস (একাধিক নির্বাচনযোগ্য):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isAll = selStatuses.isEmpty() || selStatuses.contains("ALL")
                    FilterChip(
                        selected = isAll,
                        onClick = { selStatuses.clear() },
                        label = { Text("সকল স্ট্যাটাস", fontSize = 11.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = if (isAll) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                        } else null
                    )
                    statusOptions.forEach { (stKey, stLabel) ->
                        val isSelected = !isAll && selStatuses.contains(stKey)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isAll) {
                                    selStatuses.clear()
                                    selStatuses.add(stKey)
                                } else {
                                    if (isSelected) selStatuses.remove(stKey)
                                    else selStatuses.add(stKey)
                                }
                            },
                            label = { Text(stLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                            } else null
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 4. Special Needs Filter
                Text("বিশেষ চাহিদা সম্পন্ন শিক্ষার্থী:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "সকল শিক্ষার্থী", true to "শুধু বিশেষ চাহিদা সম্পন্ন", false to "সাধারণ").forEach { (snVal, snLabel) ->
                        val isSelected = selSpecialNeeds == snVal
                        FilterChip(
                            selected = isSelected,
                            onClick = { selSpecialNeeds = snVal },
                            label = { Text(snLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                            } else null
                        )
                    }
                }

                // 5. Village Filter (Multi-Select)
                if (villages.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("গ্রাম (Village - একাধিক নির্বাচনযোগ্য):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val isAll = selVillages.isEmpty()
                            FilterChip(
                                selected = isAll,
                                onClick = { selVillages.clear() },
                                label = { Text("সকল গ্রাম", fontSize = 11.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isAll) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                } else null
                            )
                        }
                        items(villages) { v ->
                            val isSelected = selVillages.contains(v)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selVillages.remove(v)
                                    else selVillages.add(v)
                                },
                                label = { Text(v, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // 6. Custom Fields Dynamic Filters (Multi-Select)
                val filterableCustomFields = customFields.filter { !it.isCalculated && it.optionsList.isNotEmpty() }
                if (filterableCustomFields.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("কাস্টম ফিল্ড ফিল্টার:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                    filterableCustomFields.forEach { cf ->
                        val currentSet = tempCustomFilters[cf.id] ?: emptySet()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${cf.name}:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    val isAll = currentSet.isEmpty()
                                    FilterChip(
                                        selected = isAll,
                                        onClick = { tempCustomFilters.remove(cf.id) },
                                        label = { Text("সকল", fontSize = 11.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isAll) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                        } else null
                                    )
                                }
                                items(cf.optionsList) { opt ->
                                    val isSelected = currentSet.contains(opt)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val newSet = currentSet.toMutableSet()
                                            if (isSelected) newSet.remove(opt)
                                            else newSet.add(opt)
                                            if (newSet.isEmpty()) tempCustomFilters.remove(cf.id)
                                            else tempCustomFilters[cf.id] = newSet
                                        },
                                        label = { Text(opt, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        selClasses.toSet(),
                        selGenders.toSet(),
                        selStatuses.toSet(),
                        selVillages.toSet(),
                        selSpecialNeeds,
                        tempCustomFilters.toMap()
                    )
                }
            ) {
                Text("প্রয়োগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("সব রিসেট", color = Color.Red)
            }
        }
    )
}

@Composable
fun StudentDetailDialog(
    student: StudentEntity,
    category: String,
    customFields: List<CustomFieldEntity>,
    formulaRules: List<FormulaRuleEntity> = emptyList(),
    baseEndDate: String? = null,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onIdCard: () -> Unit = {}
) {
    val customMap = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson)
    val ageYears = if (!baseEndDate.isNullOrBlank()) {
        com.example.util.BaseDateManager.calculateAgeYearsInt(student.birthDate, baseEndDate)
    } else {
        FormulaEvaluator.calculateAge(student.birthDate)
    }
    val ageText = if (student.birthDate.isNotBlank()) "${BanglaUtils.toBanglaDigits(ageYears)} বছর" else "তথ্য নেই"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("শিক্ষার্থী প্রোফাইল", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main Highlight Header Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = student.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = "আইডি: ${student.id} | শ্রেণি: ${student.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}", fontSize = 14.sp)
                        Text(
                            text = "ক্যাটাগরি: $category | বয়স: $ageText",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!baseEndDate.isNullOrBlank()) {
                            Text(
                                text = "বেস ডেট: ${com.example.util.BaseDateManager.formatDateBengali(baseEndDate)} অনুযায়ী",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                DetailRow(label = "পিতার নাম", value = student.fatherName)
                DetailRow(label = "মাতার নাম", value = student.motherName)
                DetailRow(label = "জন্মতারিখ", value = "${student.birthDate} (${BanglaUtils.formatBanglaDate(student.birthDate)})")
                DetailRow(label = "জন্ম নিবন্ধন নম্বর", value = student.birthRegNumber)
                DetailRow(label = "মোবাইল নম্বর", value = student.mobile)
                DetailRow(label = "গ্রাম", value = student.village)
                DetailRow(label = "ঠিকানা", value = student.address)
                DetailRow(label = "শিক্ষাবর্ষ", value = student.academicYear)
                DetailRow(label = "লিঙ্গ", value = student.gender)
                DetailRow(label = "স্ট্যাটাস", value = student.status)
                DetailRow(label = "বিশেষ চাহিদা সম্পন্ন", value = if (student.isSpecialNeeds) "হ্যাঁ" else "না")
                if (student.admissionDate.isNotBlank()) {
                    DetailRow(label = "ভর্তির তারিখ", value = "${student.admissionDate} (${BanglaUtils.formatBanglaDate(student.admissionDate)})")
                }
                if (student.lastModifiedDate.isNotBlank()) {
                    DetailRow(label = "সর্বশেষ পরিবর্তনের তারিখ", value = "${student.lastModifiedDate} (${BanglaUtils.formatBanglaDate(student.lastModifiedDate)})")
                }

                if (customFields.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "অতিরিক্ত ও ক্যালকুলেটেড ফিল্ড (Custom & Calculated Fields)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    customFields.forEach { cf ->
                        val valStr = FormulaEvaluator.getFieldValue(student, cf.id, customFields, formulaRules, baseEndDate = baseEndDate)
                        DetailRow(label = if (cf.isCalculated) "${cf.name} (Calculated)" else cf.name, value = valStr)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onIdCard) {
                    Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("আইডি কার্ড")
                }
                Button(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("সম্পাদনা")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@Composable
fun StudentIdCardDialog(
    student: StudentEntity,
    category: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("শিক্ষার্থী ডিজিটাল আইডি কার্ড", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ডিজিটাল স্টুডেন্ট আইডি কার্ড",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student.name.take(1),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("আইডি: ${student.id}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("শ্রেণি: ${student.studentClass}  |  রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}", fontSize = 14.sp)
                    Text("পিতা: ${student.fatherName}", fontSize = 12.sp, color = Color.Gray)
                    Text("গ্রাম: ${student.village}  |  মোবাইল: ${student.mobile}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("শেয়ার করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = if (value.isBlank()) "-" else value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StudentAddEditDialog(
    student: StudentEntity?,
    allStudents: List<StudentEntity>,
    customFields: List<CustomFieldEntity>,
    onDismiss: () -> Unit,
    onSave: (StudentEntity) -> Unit
) {
    val context = LocalContext.current
    var id by remember { mutableStateOf(student?.id ?: "STU-2026-${(100..999).random()}") }
    var studentClass by remember { mutableStateOf(student?.studentClass ?: "১ম শ্রেণি") }
    var rollNumber by remember { mutableStateOf(student?.rollNumber?.toString() ?: "1") }
    var name by remember { mutableStateOf(student?.name ?: "") }
    var fatherName by remember { mutableStateOf(student?.fatherName ?: "") }
    var motherName by remember { mutableStateOf(student?.motherName ?: "") }
    var birthDate by remember { mutableStateOf(student?.birthDate ?: "2019-01-01") }
    var mobile by remember { mutableStateOf(student?.mobile ?: "") }
    var village by remember { mutableStateOf(student?.village ?: "পশ্চিম রামপুর") }
    var academicYear by remember { mutableStateOf(student?.academicYear ?: "২০২৬") }
    var address by remember { mutableStateOf(student?.address ?: "") }
    var birthRegNumber by remember { mutableStateOf(student?.birthRegNumber ?: "") }
    var gender by remember { mutableStateOf(student?.gender ?: "ছাত্র") }
    var isSpecialNeeds by remember { mutableStateOf(student?.isSpecialNeeds ?: false) }
    var status by remember { mutableStateOf(student?.status ?: "Current") }
    var photoUri by remember { mutableStateOf(student?.photoUri) }

    val todayDateStr = remember { BaseDateManager.getTodayStr() }
    var admissionDate by remember {
        mutableStateOf(if (!student?.admissionDate.isNullOrBlank()) student!!.admissionDate else todayDateStr)
    }
    var lastModifiedDate by remember {
        mutableStateOf(todayDateStr)
    }

    var showPhotoCaptureDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }

    // Dynamic Groups & Fields
    var layoutVersion by remember { mutableStateOf(0) }
    val formGroups = remember(layoutVersion, customFields) {
        FormLayoutManager.loadGroups(context, customFields)
    }
    // Collapsible group state: all groups are expanded (open) by default initially
    val expandedGroups = remember(formGroups) {
        mutableStateMapOf<String, Boolean>().apply {
            formGroups.forEach { g -> put(g.id, true) }
        }
    }

    // Distinct suggestion lists across dataset
    val villageSuggestions = remember(allStudents) { allStudents.map { it.village }.filter { it.isNotBlank() }.distinct() }
    val fatherSuggestions = remember(allStudents) { allStudents.map { it.fatherName }.filter { it.isNotBlank() }.distinct() }
    val motherSuggestions = remember(allStudents) { allStudents.map { it.motherName }.filter { it.isNotBlank() }.distinct() }
    val addressSuggestions = remember(allStudents) { allStudents.map { it.address }.filter { it.isNotBlank() }.distinct() }

    val initialCustomMap = remember { FormulaEvaluator.parseCustomValuesJson(student?.customValuesJson ?: "{}") }
    val customValueMap = remember { mutableStateMapOf<String, String>().apply { putAll(initialCustomMap) } }

    val classOptions = listOf("প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val genderOptions = listOf("ছাত্র", "ছাত্রী")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val updated = StudentEntity(
                        id = id,
                        studentClass = studentClass,
                        rollNumber = rollNumber.toIntOrNull() ?: 1,
                        name = name,
                        fatherName = fatherName,
                        motherName = motherName,
                        birthDate = birthDate,
                        mobile = mobile,
                        village = village,
                        academicYear = academicYear,
                        address = address,
                        birthRegNumber = birthRegNumber,
                        gender = gender,
                        isSpecialNeeds = isSpecialNeeds,
                        status = status,
                        photoUri = photoUri,
                        customValuesJson = FormulaEvaluator.buildCustomValuesJson(customValueMap),
                        admissionDate = admissionDate,
                        lastModifiedDate = lastModifiedDate
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("btn_save_student")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (student == null) "নতুন শিক্ষার্থী যুক্তকরণ" else "শিক্ষার্থী তথ্য সম্পাদনা",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showLayoutDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "ফিল্ড বিন্যাস ও গ্রুপ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick expand/collapse all toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ফর্ম গ্রুপসমূহ (ক্লিক করে খুলুন)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            val anyOpen = formGroups.any { expandedGroups[it.id] == true }
                            formGroups.forEach { g ->
                                expandedGroups[g.id] = !anyOpen
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        val anyOpen = formGroups.any { expandedGroups[it.id] == true }
                        Text(if (anyOpen) "সব বন্ধ করুন" else "সব খুলুন", fontSize = 11.sp)
                    }
                }

                formGroups.forEachIndexed { gIdx, group ->
                    val visibleFields = group.fields.filter { it.isVisible }
                    if (visibleFields.isNotEmpty()) {
                        // Normally closed by default: clicking header toggles open
                        val isExpanded = expandedGroups[group.id] == true

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = if (isExpanded) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Header: Clickable row to open/close menu group
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedGroups[group.id] = !isExpanded
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = group.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${BanglaUtils.toBanglaDigits(visibleFields.size)}টি ফিল্ড",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "সংকুচিত করুন" else "প্রসারিত করুন",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        visibleFields.forEachIndexed { fIdx, field ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        when (field.key) {
                                            "photo" -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(60.dp, 80.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFE0F2F1))
                                                            .clickable { showPhotoCaptureDialog = true },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (!photoUri.isNullOrBlank()) {
                                                            val bmp = try {
                                                                if (photoUri!!.startsWith("data:image")) {
                                                                    val b64 = photoUri!!.substringAfter("base64,")
                                                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                                } else null
                                                            } catch (e: Exception) { null }

                                                            if (bmp != null) {
                                                                Image(
                                                                    bitmap = bmp.asImageBitmap(),
                                                                    contentDescription = "Photo",
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            } else {
                                                                Icon(Icons.Filled.AccountBox, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                        } else {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                Text("ছবি", fontSize = 10.sp)
                                                            }
                                                        }
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        OutlinedButton(
                                                            onClick = { showPhotoCaptureDialog = true },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(if (photoUri == null) "ছবি তুলুন / আপলোড করুন" else "ছবি পরিবর্তন", fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                            "name" -> {
                                                OutlinedTextField(
                                                    value = name,
                                                    onValueChange = { name = it },
                                                    label = { Text("শিক্ষার্থীর নাম *") },
                                                    modifier = Modifier.fillMaxWidth().testTag("input_student_name")
                                                )
                                            }
                                            "rollNumber" -> {
                                                OutlinedTextField(
                                                    value = rollNumber,
                                                    onValueChange = { rollNumber = it },
                                                    label = { Text("রোল নং") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            "studentClass" -> {
                                                Column {
                                                    Text("শ্রেণি", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    LazyRow {
                                                        items(classOptions) { c ->
                                                            FilterChip(
                                                                selected = studentClass == c,
                                                                onClick = { studentClass = c },
                                                                label = { Text(c, fontSize = 11.sp) }
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            "gender" -> {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("লিঙ্গ: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    genderOptions.forEach { g ->
                                                        RadioButton(selected = gender == g, onClick = { gender = g })
                                                        Text(g, fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    }
                                                }
                                            }
                                            "birthDate" -> {
                                                DateInputField(
                                                    dateValue = birthDate,
                                                    onDateChange = { birthDate = it },
                                                    label = "জন্মতারিখ (Date of Birth)"
                                                )
                                            }
                                            "birthRegNumber" -> {
                                                OutlinedTextField(
                                                    value = birthRegNumber,
                                                    onValueChange = { birthRegNumber = it },
                                                    label = { Text("জন্ম নিবন্ধন নম্বর") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            "id" -> {
                                                OutlinedTextField(
                                                    value = id,
                                                    onValueChange = { id = it },
                                                    label = { Text("শিক্ষার্থী আইডি") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            "fatherName" -> {
                                                GlobalSuggestionTextField(
                                                    value = fatherName,
                                                    onValueChange = { fatherName = it },
                                                    label = "পিতার নাম",
                                                    suggestions = fatherSuggestions
                                                )
                                            }
                                            "motherName" -> {
                                                GlobalSuggestionTextField(
                                                    value = motherName,
                                                    onValueChange = { motherName = it },
                                                    label = "মাতার নাম",
                                                    suggestions = motherSuggestions
                                                )
                                            }
                                            "mobile" -> {
                                                OutlinedTextField(
                                                    value = mobile,
                                                    onValueChange = { mobile = it },
                                                    label = { Text("মোবাইল নম্বর") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            "village" -> {
                                                GlobalSuggestionTextField(
                                                    value = village,
                                                    onValueChange = { village = it },
                                                    label = "গ্রাম (Village)",
                                                    suggestions = villageSuggestions
                                                )
                                            }
                                            "address" -> {
                                                GlobalSuggestionTextField(
                                                    value = address,
                                                    onValueChange = { address = it },
                                                    label = "ঠিকানা (Address)",
                                                    suggestions = addressSuggestions
                                                )
                                            }
                                            "academicYear" -> {
                                                OutlinedTextField(
                                                    value = academicYear,
                                                    onValueChange = { academicYear = it },
                                                    label = { Text("শিক্ষাবর্ষ") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            "isSpecialNeeds" -> {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = isSpecialNeeds, onCheckedChange = { isSpecialNeeds = it })
                                                    Text("বিশেষ চাহিদাসম্পন্ন শিক্ষার্থী", fontSize = 12.sp)
                                                }
                                            }
                                            "admissionDate" -> {
                                                DateInputField(
                                                    dateValue = admissionDate,
                                                    onDateChange = { admissionDate = it },
                                                    label = "ভর্তির তারিখ (Admission Date)"
                                                )
                                            }
                                            "lastModifiedDate" -> {
                                                DateInputField(
                                                    dateValue = lastModifiedDate,
                                                    onDateChange = { lastModifiedDate = it },
                                                    label = "সর্বশেষ পরিবর্তনের তারিখ (Last Modified Date)"
                                                )
                                            }
                                            else -> {
                                                val customField = customFields.find { it.id == field.key }
                                                if (customField != null && !customField.isCalculated) {
                                                    val currentVal = customValueMap[customField.id] ?: ""
                                                    when (customField.fieldType) {
                                                        "Dropdown", "Multiple choice" -> {
                                                            val opts = customField.optionsList
                                                            Column {
                                                                Text(customField.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    opts.forEach { opt ->
                                                                        FilterChip(
                                                                            selected = currentVal == opt,
                                                                            onClick = { customValueMap[customField.id] = opt },
                                                                            label = { Text(opt, fontSize = 11.sp) }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        "Yes/No" -> {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text("${customField.name}: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                listOf("হ্যাঁ", "না").forEach { opt ->
                                                                    FilterChip(
                                                                        selected = currentVal == opt,
                                                                        onClick = { customValueMap[customField.id] = opt },
                                                                        label = { Text(opt, fontSize = 11.sp) }
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                }
                                                            }
                                                        }
                                                        else -> {
                                                            OutlinedTextField(
                                                                value = currentVal,
                                                                onValueChange = { customValueMap[customField.id] = it },
                                                                label = { Text(customField.name) },
                                                                modifier = Modifier.fillMaxWidth()
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
                }
            }
        }
    }
}
)

    if (showPhotoCaptureDialog) {
        PhotoCaptureDialog(
            currentPhotoUri = photoUri,
            title = "শিক্ষার্থীর ছবি (Passport Photo)",
            onDismiss = { showPhotoCaptureDialog = false },
            onPhotoSelected = { newPhotoBase64 ->
                photoUri = newPhotoBase64
            }
        )
    }

    if (showLayoutDialog) {
        FormLayoutManagerDialog(
            customFields = customFields,
            onDismiss = { showLayoutDialog = false },
            onLayoutSaved = {
                layoutVersion++
            }
        )
    }
}
