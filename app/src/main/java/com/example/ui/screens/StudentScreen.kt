package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.*
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
fun StudentScreen(viewModel: MainViewModel) {
    val filteredStudents by viewModel.filteredStudents.collectAsState()
    val allStudents by viewModel.allStudents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterClass by viewModel.filterClass.collectAsState()
    val filterGender by viewModel.filterGender.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val filterVillage by viewModel.filterVillage.collectAsState()
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

    // Multi-selection state
    val selectedStudentIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    // Additional Custom Field Filter state (Dynamic Filter Map)
    val customFilterValues = remember { mutableStateMapOf<String, String>() }

    // Dynamic Filtered list combining standard and custom field filters
    val dynamicallyFilteredStudents = remember(filteredStudents, customFilterValues, customFields, formulaRules) {
        if (customFilterValues.isEmpty()) {
            filteredStudents
        } else {
            filteredStudents.filter { student ->
                customFilterValues.entries.all { (fieldKey, filterVal) ->
                    if (filterVal.isBlank() || filterVal == "ALL") true
                    else {
                        val studentVal = FormulaEvaluator.getFieldValue(student, fieldKey, customFields, formulaRules)
                        studentVal.equals(filterVal, ignoreCase = true)
                    }
                }
            }
        }
    }

    // Calculate Active Filters Count for the Filter Button Badge
    val activeFiltersCount = remember(filterClass, filterGender, filterStatus, filterVillage, filterSpecialNeeds, customFilterValues) {
        var count = 0
        if (filterClass != null && filterClass != "ALL") count++
        if (filterGender != null && filterGender != "ALL") count++
        if (filterStatus != null && filterStatus != "Current" && filterStatus != "ALL") count++
        if (filterVillage != null && filterVillage != "ALL") count++
        if (filterSpecialNeeds != null) count++
        count += customFilterValues.count { it.value.isNotBlank() && it.value != "ALL" }
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
            // Search Bar & Compact Control Bar
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("নাম, রোল, পিতা, মোবাইল বা আইডি...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_input_student")
                        )

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
                                modifier = Modifier.testTag("btn_open_filters_sheet")
                            ) {
                                Icon(
                                    Icons.Filled.FilterList,
                                    contentDescription = "ফিল্টার",
                                    tint = if (activeFiltersCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 2. Customize View Button
                        FilledTonalIconButton(
                            onClick = { showViewCustomizerDialog = true },
                            modifier = Modifier.testTag("btn_customize_student_view")
                        ) {
                            Icon(Icons.Filled.DashboardCustomize, contentDescription = "ভিউ কাস্টমাইজ", tint = MaterialTheme.colorScheme.primary)
                        }

                        // 3. Form Layout Button
                        IconButton(
                            onClick = { showFormLayoutManager = true },
                            modifier = Modifier.testTag("btn_form_layout_manager")
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "ফর্ম বিন্যাস", tint = MaterialTheme.colorScheme.primary)
                        }

                        // 4. Import / Export Button
                        IconButton(
                            onClick = { showImportExportModal = true },
                            modifier = Modifier.testTag("btn_import_export")
                        ) {
                            Icon(Icons.Filled.ImportExport, contentDescription = "Import/Export", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ==========================================
                    // SAVED VIEWS PRESET STRIP
                    // ==========================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    if (viewPreset.filterStatus != null) viewModel.filterStatus.value = viewPreset.filterStatus
                                    if (viewPreset.filterClass != null) viewModel.filterClass.value = viewPreset.filterClass
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

                    // ==========================================
                    // COMPACT SINGLE-ROW HORIZONTALLY SCROLLABLE QUICK FILTERS
                    // ==========================================
                    val enabledQuickFilters = activeView.quickFilters.filter { it.isEnabled }
                    if (enabledQuickFilters.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Reset Chip if active filters
                            if (activeFiltersCount > 0 || searchQuery.isNotBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.searchQuery.value = ""
                                            viewModel.filterClass.value = null
                                            viewModel.filterStatus.value = "Current"
                                            viewModel.filterGender.value = null
                                            viewModel.filterVillage.value = null
                                            viewModel.filterSpecialNeeds.value = null
                                            customFilterValues.clear()
                                        },
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Reset", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("রিসেট", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }

                            // Dynamic Quick Filter Chips from View Settings
                            enabledQuickFilters.forEach { qf ->
                                when (qf.key) {
                                    "class" -> {
                                        ClassDropdownQuickFilter(
                                            currentClass = filterClass,
                                            options = classOptions,
                                            onSelectClass = { viewModel.filterClass.value = if (it == "ALL") null else it }
                                        )
                                    }
                                    "gender" -> {
                                        GenderDropdownQuickFilter(
                                            currentGender = filterGender,
                                            options = genderOptions,
                                            onSelectGender = { viewModel.filterGender.value = if (it == "ALL") null else it }
                                        )
                                    }
                                    "status" -> {
                                        StatusDropdownQuickFilter(
                                            currentStatus = filterStatus,
                                            options = statusOptions,
                                            onSelectStatus = { viewModel.filterStatus.value = if (it == "ALL") null else it }
                                        )
                                    }
                                    "village" -> {
                                        val villages = remember(allStudents) {
                                            listOf("ALL") + allStudents.map { it.village }.filter { it.isNotBlank() }.distinct()
                                        }
                                        VillageDropdownQuickFilter(
                                            currentVillage = filterVillage,
                                            villages = villages,
                                            onSelectVillage = { viewModel.filterVillage.value = if (it == "ALL") null else it }
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
                                                selectedValue = customFilterValues[matchedCf.id] ?: "ALL",
                                                onSelectValue = { opt ->
                                                    if (opt == "ALL") customFilterValues.remove(matchedCf.id)
                                                    else customFilterValues[matchedCf.id] = opt
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
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

            // Multi-selection Action Toolbar (if items selected)
            if (isSelectionMode && selectedStudentIds.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${BanglaUtils.toBanglaDigits(selectedStudentIds.size)} জন নির্বাচিত",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    val selectedList = allStudents.filter { selectedStudentIds.contains(it.id) }
                                    val textSummary = selectedList.joinToString("\n") {
                                        "${it.name} | শ্রেণি: ${it.studentClass} | রোল: ${it.rollNumber} | মোবাইল: ${it.mobile}"
                                    }
                                    viewModel.shareCsvContent("selected_students.txt", textSummary, "নির্বাচিত শিক্ষার্থীর তথ্য")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("শেয়ার", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val toDelete = allStudents.filter { selectedStudentIds.contains(it.id) }
                                    toDelete.forEach { viewModel.deleteStudent(it) }
                                    selectedStudentIds.clear()
                                    isSelectionMode = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
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
        StudentDetailedFilterModal(
            currentClass = filterClass,
            currentGender = filterGender,
            currentStatus = filterStatus,
            currentVillage = filterVillage,
            currentSpecialNeeds = filterSpecialNeeds,
            customFilterValues = customFilterValues,
            allStudents = allStudents,
            customFields = customFields,
            matchCount = dynamicallyFilteredStudents.size,
            onDismiss = { showDetailedFilterSheet = false },
            onApply = { newClass, newGender, newStatus, newVillage, newSpecialNeeds, newCustomFilters ->
                viewModel.filterClass.value = newClass
                viewModel.filterGender.value = newGender
                viewModel.filterStatus.value = newStatus
                viewModel.filterVillage.value = newVillage
                viewModel.filterSpecialNeeds.value = newSpecialNeeds
                customFilterValues.clear()
                customFilterValues.putAll(newCustomFilters)
                showDetailedFilterSheet = false
            },
            onReset = {
                viewModel.filterClass.value = null
                viewModel.filterGender.value = null
                viewModel.filterStatus.value = "Current"
                viewModel.filterVillage.value = null
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
            onDeletePreset = { presetId ->
                val remaining = allSavedViews.filter { it.id != presetId }
                StudentViewConfigManager.saveAllViews(context, remaining)
                if (activeView.id == presetId) {
                    activeView = remaining.firstOrNull() ?: StudentViewConfigManager.getDefaultPresetView(customFields)
                    StudentViewConfigManager.setActiveViewId(context, activeView.id)
                }
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
        StudentDetailDialog(
            student = viewingStudent!!,
            category = viewModel.getStudentCategory(viewingStudent!!),
            customFields = customFields,
            formulaRules = formulaRules,
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
}

// ==========================================
// COMPACT QUICK FILTER DROPDOWN COMPONENTS
// ==========================================

@Composable
fun ClassDropdownQuickFilter(
    currentClass: String?,
    options: List<String>,
    onSelectClass: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = currentClass != null && currentClass != "ALL"

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (isFiltered) "শ্রেণি: $currentClass" else "শ্রেণি: সকল",
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt == "ALL") "সকল শ্রেণি" else opt, fontSize = 12.sp) },
                    onClick = {
                        onSelectClass(opt)
                        expanded = false
                    },
                    leadingIcon = if ((opt == "ALL" && currentClass == null) || opt == currentClass) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun GenderDropdownQuickFilter(
    currentGender: String?,
    options: List<String>,
    onSelectGender: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = currentGender != null && currentGender != "ALL"

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (isFiltered) "লিঙ্গ: $currentGender" else "লিঙ্গ: সকল",
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt == "ALL") "সকল লিঙ্গ" else opt, fontSize = 12.sp) },
                    onClick = {
                        onSelectGender(opt)
                        expanded = false
                    },
                    leadingIcon = if ((opt == "ALL" && currentGender == null) || opt == currentGender) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun StatusDropdownQuickFilter(
    currentStatus: String?,
    options: List<String>,
    onSelectStatus: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = currentStatus != null && currentStatus != "ALL" && currentStatus != "Current"

    val displayLabel = when (currentStatus) {
        "Current" -> "স্ট্যাটাস: বর্তমান"
        "Former" -> "স্ট্যাটাস: সাবেক"
        "Transferred" -> "স্ট্যাটাস: বদলীকৃত"
        "Inactive" -> "স্ট্যাটাস: নিষ্ক্রিয়"
        else -> "স্ট্যাটাস: সকল"
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
            options.forEach { opt ->
                val label = when (opt) {
                    "Current" -> "বর্তমান (Current)"
                    "Former" -> "সাবেক (Former)"
                    "Transferred" -> "বদলীকৃত (Transferred)"
                    "Inactive" -> "নিষ্ক্রিয় (Inactive)"
                    else -> "সকল স্ট্যাটাস"
                }
                DropdownMenuItem(
                    text = { Text(label, fontSize = 12.sp) },
                    onClick = {
                        onSelectStatus(opt)
                        expanded = false
                    },
                    leadingIcon = if ((opt == "ALL" && currentStatus == null) || opt == currentStatus) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun VillageDropdownQuickFilter(
    currentVillage: String?,
    villages: List<String>,
    onSelectVillage: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = currentVillage != null && currentVillage != "ALL"

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (isFiltered) "গ্রাম: $currentVillage" else "গ্রাম: সকল",
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            villages.take(20).forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt == "ALL") "সকল গ্রাম" else opt, fontSize = 12.sp) },
                    onClick = {
                        onSelectVillage(opt)
                        expanded = false
                    },
                    leadingIcon = if ((opt == "ALL" && currentVillage == null) || opt == currentVillage) {
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
    selectedValue: String,
    onSelectValue: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedValue.isNotBlank() && selectedValue != "ALL"
    val options = listOf("ALL") + customField.optionsList

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (isFiltered) "${customField.name}: $selectedValue" else "${customField.name}: সকল",
                    fontSize = 11.sp
                )
            },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt == "ALL") "সকল" else opt, fontSize = 12.sp) },
                    onClick = {
                        onSelectValue(opt)
                        expanded = false
                    },
                    leadingIcon = if (opt == selectedValue) {
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
    currentClass: String?,
    currentGender: String?,
    currentStatus: String?,
    currentVillage: String?,
    currentSpecialNeeds: Boolean?,
    customFilterValues: Map<String, String>,
    allStudents: List<StudentEntity>,
    customFields: List<CustomFieldEntity>,
    matchCount: Int,
    onDismiss: () -> Unit,
    onApply: (String?, String?, String?, String?, Boolean?, Map<String, String>) -> Unit,
    onReset: () -> Unit
) {
    var selClass by remember { mutableStateOf(currentClass) }
    var selGender by remember { mutableStateOf(currentGender) }
    var selStatus by remember { mutableStateOf(currentStatus ?: "Current") }
    var selVillage by remember { mutableStateOf(currentVillage) }
    var selSpecialNeeds by remember { mutableStateOf(currentSpecialNeeds) }
    val tempCustomFilters = remember { mutableStateMapOf<String, String>().apply { putAll(customFilterValues) } }

    val classOptions = listOf("ALL", "প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val genderOptions = listOf("ALL", "ছাত্র", "ছাত্রী")
    val statusOptions = listOf("Current" to "বর্তমান", "Former" to "সাবেক", "Transferred" to "বদলীকৃত", "Inactive" to "নিষ্ক্রিয়", "ALL" to "সকল স্ট্যাটাস")
    val villages = remember(allStudents) { listOf("ALL") + allStudents.map { it.village }.filter { it.isNotBlank() }.distinct() }

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
                // 1. Class Filter
                Text("শ্রেণি নির্বাচন করুন:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(classOptions) { c ->
                        val isSelected = (c == "ALL" && selClass == null) || selClass == c
                        FilterChip(
                            selected = isSelected,
                            onClick = { selClass = if (c == "ALL") null else c },
                            label = { Text(if (c == "ALL") "সকল শ্রেণি" else c, fontSize = 11.sp) }
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. Gender Filter
                Text("লিঙ্গ:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genderOptions.forEach { g ->
                        val isSelected = (g == "ALL" && selGender == null) || selGender == g
                        FilterChip(
                            selected = isSelected,
                            onClick = { selGender = if (g == "ALL") null else g },
                            label = { Text(if (g == "ALL") "সকল লিঙ্গ" else g, fontSize = 11.sp) }
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 3. Status Filter
                Text("শিক্ষার্থীর স্ট্যাটাস:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusOptions.forEach { (stKey, stLabel) ->
                        val isSelected = selStatus == stKey || (stKey == "ALL" && selStatus == "ALL")
                        FilterChip(
                            selected = isSelected,
                            onClick = { selStatus = stKey },
                            label = { Text(stLabel, fontSize = 11.sp) }
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
                            label = { Text(snLabel, fontSize = 11.sp) }
                        )
                    }
                }

                // 5. Village Filter
                if (villages.size > 1) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("গ্রাম (Village):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(villages) { v ->
                            val isSelected = (v == "ALL" && selVillage == null) || selVillage == v
                            FilterChip(
                                selected = isSelected,
                                onClick = { selVillage = if (v == "ALL") null else v },
                                label = { Text(if (v == "ALL") "সকল গ্রাম" else v, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 6. Custom Fields Dynamic Filters
                val filterableCustomFields = customFields.filter { !it.isCalculated && it.optionsList.isNotEmpty() }
                if (filterableCustomFields.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text("কাস্টম ফিল্ড ফিল্টার:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                    filterableCustomFields.forEach { cf ->
                        val currentVal = tempCustomFilters[cf.id] ?: "ALL"
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${cf.name}:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("ALL") + cf.optionsList) { opt ->
                                    val isSelected = currentVal == opt
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (opt == "ALL") tempCustomFilters.remove(cf.id)
                                            else tempCustomFilters[cf.id] = opt
                                        },
                                        label = { Text(if (opt == "ALL") "সকল" else opt, fontSize = 11.sp) }
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
                    onApply(selClass, selGender, selStatus, selVillage, selSpecialNeeds, tempCustomFilters)
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
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onIdCard: () -> Unit = {}
) {
    val customMap = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson)
    val age = FormulaEvaluator.calculateAge(student.birthDate)

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
                        Text(text = "ক্যাটাগরি: $category | বয়স: ${BanglaUtils.toBanglaDigits(age)} বছর", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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

                if (customFields.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "অতিরিক্ত ও ক্যালকুলেটেড ফিল্ড (Custom & Calculated Fields)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    customFields.forEach { cf ->
                        val valStr = FormulaEvaluator.getFieldValue(student, cf.id, customFields, formulaRules)
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

    var showPhotoCaptureDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }

    // Dynamic Groups & Fields
    var layoutVersion by remember { mutableStateOf(0) }
    val formGroups = remember(layoutVersion, customFields) {
        FormLayoutManager.loadGroups(context, customFields)
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
                formGroups.forEachIndexed { gIdx, group ->
                    val visibleFields = group.fields.filter { it.isVisible }
                    if (visibleFields.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = group.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

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
        },
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
                        customValuesJson = FormulaEvaluator.buildCustomValuesJson(customValueMap)
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("btn_save_student")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
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
