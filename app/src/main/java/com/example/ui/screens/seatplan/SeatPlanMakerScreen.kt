package com.example.ui.screens.seatplan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StudentEntity
import com.example.data.model.*
import com.example.util.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeatPlanMakerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val allDbStudents by viewModel.allStudents.collectAsState()
    val currentBengaliFont by viewModel.bengaliFont.collectAsState()
    val currentClassPreset by viewModel.classPreset.collectAsState()

    val defaultSchoolName = remember(schoolInfo) {
        schoolInfo?.schoolName?.ifBlank { null } ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
    }
    val defaultSchoolAddress = remember(schoolInfo) {
        schoolInfo?.address?.ifBlank { null } ?: "আলফাডাঙ্গা, ফরিদপুর।"
    }

    var state by remember {
        mutableStateOf(SeatPlanStorage.loadState(context, defaultSchoolName, defaultSchoolAddress))
    }

    // Auto-update school info if updated in settings
    LaunchedEffect(schoolInfo) {
        if (schoolInfo != null) {
            val sName = schoolInfo?.schoolName ?: ""
            val sAddr = schoolInfo?.address ?: ""
            var updated = false
            var newState = state
            if (state.schoolName.isBlank() && sName.isNotBlank()) {
                newState = newState.copy(schoolName = sName)
                updated = true
            }
            if (state.schoolAddress.isBlank() && sAddr.isNotBlank()) {
                newState = newState.copy(schoolAddress = sAddr)
                updated = true
            }
            if (updated) {
                state = newState
                SeatPlanStorage.saveState(context, state)
            }
        }
    }

    // Convert students to list with sample fallback if empty
    val effectiveStudents = remember(allDbStudents, currentClassPreset) {
        if (allDbStudents.isNotEmpty()) {
            allDbStudents.map {
                AdmitCardStudent(
                    id = it.id,
                    name = it.name,
                    studentClass = it.studentClass,
                    rollNumber = it.rollNumber.toString(),
                    fatherName = it.fatherName,
                    motherName = it.motherName
                )
            }
        } else {
            // Generate realistic sample student list for instant preview & test
            val presetClasses = currentClassPreset.classNames
            val sampleNames = listOf(
                "মোসাঃ ছিনাতিয়া", "মোঃ ইয়ামিন মোল্যা", "মোছাঃ মরিয়ম খানম", "মোঃ সালামুন",
                "মোঃ জিহাদ সিকদার", "নুসরাত জাহান", "মোঃ আহাদ মোল্যা", "মোসাঃ বন্যা খানম",
                "মোসাঃ ফারিয়া খানম", "মোঃ মুস্তাকিম খাঁন", "আবু তালহা", "মোঃ রহিম ফকির",
                "আয়শা ইসলাম", "তানভীর আহমেদ", "মারিয়াম খাতুন", "মোঃ নাঈম ইসলাম"
            )
            val list = mutableListOf<AdmitCardStudent>()
            var roll = 1
            sampleNames.forEach { name ->
                val cls = if (presetClasses.isNotEmpty()) presetClasses[0] else "প্রথম শ্রেণি"
                list.add(
                    AdmitCardStudent(
                        id = "sample_$roll",
                        name = name,
                        studentClass = cls,
                        rollNumber = roll.toString()
                    )
                )
                roll++
            }
            list
        }
    }

    // Filter and sort students based on Scope
    val filteredStudents = remember(effectiveStudents, state.scope) {
        var list = when (state.scope.scopeType) {
            "CLASS" -> {
                if (state.scope.selectedClasses.isEmpty()) effectiveStudents
                else effectiveStudents.filter { it.studentClass in state.scope.selectedClasses }
            }
            "STUDENT" -> {
                if (state.scope.selectedStudentIds.isEmpty()) effectiveStudents
                else effectiveStudents.filter { it.id in state.scope.selectedStudentIds }
            }
            else -> effectiveStudents
        }

        list = when (state.scope.sortBy) {
            "ROLL" -> list.sortedBy { it.rollNumber.toIntOrNull() ?: 9999 }
            "NAME" -> list.sortedBy { it.name }
            else -> list.sortedWith(compareBy({ it.studentClass }, { it.rollNumber.toIntOrNull() ?: 9999 }))
        }
        list
    }

    // Available Classes
    val availableClasses = remember(effectiveStudents, currentClassPreset) {
        val fromStudents = effectiveStudents.map { it.studentClass }.distinct().filter { it.isNotBlank() }
        if (fromStudents.isNotEmpty()) fromStudents else currentClassPreset.classNames
    }

    // 3 Main Tabs: 0: ডাটা ও শিক্ষার্থী, 1: লেআউট ও ফিল্ডস, 2: প্রিন্ট ও প্রিভিউ
    var activeTab by remember { mutableIntStateOf(2) } // Default to preview so user immediately sees the visual
    var showQuickSettingsSheet by remember { mutableStateOf(false) }
    var showStudentPickerSheet by remember { mutableStateOf(false) }

    fun updateState(newState: SeatPlanMakerState) {
        state = newState
        SeatPlanStorage.saveState(context, newState)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "সিট প্ল্যান মেকার",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "আসন বিন্যাস ও বেঞ্চ স্টিকার জেনারেটর",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickSettingsSheet = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Quick Settings")
                    }
                    IconButton(
                        onClick = {
                            SeatPlanNativePdfUtil.exportAndSharePdf(
                                context = context,
                                state = state,
                                students = filteredStudents,
                                bengaliFont = currentBengaliFont
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    label = { Text("১. ডাটা", fontSize = 11.sp, fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.DashboardCustomize, contentDescription = null) },
                    label = { Text("২. লেআউট", fontSize = 11.sp, fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.Print, contentDescription = null) },
                    label = { Text("৩. প্রিন্ট ও প্রিভিউ", fontSize = 11.sp, fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 2) {
                FloatingActionButton(
                    onClick = {
                        SeatPlanNativePdfUtil.printSeatPlansDirectly(
                            context = context,
                            state = state,
                            students = filteredStudents,
                            bengaliFont = currentBengaliFont
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("seat_plan_fab_print")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null)
                        Text("প্রিন্ট করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> SeatPlanDataTab(
                    state = state,
                    availableClasses = availableClasses,
                    allStudents = effectiveStudents,
                    onStateChange = { updateState(it) },
                    onOpenStudentPicker = { showStudentPickerSheet = true }
                )
                1 -> SeatPlanLayoutTab(
                    state = state,
                    onStateChange = { updateState(it) }
                )
                2 -> SeatPlanPreviewTab(
                    state = state,
                    students = filteredStudents,
                    bengaliFont = currentBengaliFont,
                    onOpenSettings = { showQuickSettingsSheet = true },
                    onPrint = {
                        SeatPlanNativePdfUtil.printSeatPlansDirectly(
                            context = context,
                            state = state,
                            students = filteredStudents,
                            bengaliFont = currentBengaliFont
                        )
                    },
                    onExportPdf = {
                        SeatPlanNativePdfUtil.exportAndSharePdf(
                            context = context,
                            state = state,
                            students = filteredStudents,
                            bengaliFont = currentBengaliFont
                        )
                    }
                )
            }
        }
    }

    // Quick Settings Bottom Sheet
    if (showQuickSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickSettingsSheet = false }
        ) {
            SeatPlanQuickSettingsContent(
                state = state,
                onStateChange = { updateState(it) },
                onDismiss = { showQuickSettingsSheet = false }
            )
        }
    }

    // Student Multi-picker Sheet
    if (showStudentPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStudentPickerSheet = false }
        ) {
            SeatPlanStudentPickerContent(
                allStudents = effectiveStudents,
                selectedIds = state.scope.selectedStudentIds,
                onSelectionChange = { newIds ->
                    updateState(
                        state.copy(
                            scope = state.scope.copy(
                                scopeType = "STUDENT",
                                selectedStudentIds = newIds
                            )
                        )
                    )
                },
                onDismiss = { showStudentPickerSheet = false }
            )
        }
    }
}

/**
 * Tab 1: Data & Scope Selection
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatPlanDataTab(
    state: SeatPlanMakerState,
    availableClasses: List<String>,
    allStudents: List<AdmitCardStudent>,
    onStateChange: (SeatPlanMakerState) -> Unit,
    onOpenStudentPicker: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Header & Exam Info
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "প্রতিষ্ঠান ও পরীক্ষার তথ্য",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedTextField(
                        value = state.schoolName,
                        onValueChange = { onStateChange(state.copy(schoolName = it)) },
                        label = { Text("বিদ্যালয়ের নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = state.schoolAddress,
                        onValueChange = { onStateChange(state.copy(schoolAddress = it)) },
                        label = { Text("ঠিকানা / উপজেলা, জেলা") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = state.examName,
                        onValueChange = { onStateChange(state.copy(examName = it)) },
                        label = { Text("পরীক্ষার নাম / মূল্যায়ন") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = state.fields.seatPlanTitleText,
                        onValueChange = {
                            onStateChange(
                                state.copy(
                                    fields = state.fields.copy(seatPlanTitleText = it)
                                )
                            )
                        },
                        label = { Text("কার্ডের শিরোনাম (যেমন: আসন বিন্যাস)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // Section: Student Scope & Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "শিক্ষার্থী নির্বাচন ও পরিধি",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Scope options: All, By Class, By Student
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.scope.scopeType == "ALL",
                            onClick = {
                                onStateChange(
                                    state.copy(scope = state.scope.copy(scopeType = "ALL"))
                                )
                            },
                            label = { Text("সকল শ্রেণি (${allStudents.size})", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.scope.scopeType == "CLASS",
                            onClick = {
                                onStateChange(
                                    state.copy(scope = state.scope.copy(scopeType = "CLASS"))
                                )
                            },
                            label = { Text("শ্রেণিভিত্তিক", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.scope.scopeType == "STUDENT",
                            onClick = {
                                onStateChange(
                                    state.copy(scope = state.scope.copy(scopeType = "STUDENT"))
                                )
                            },
                            label = { Text("নির্দিষ্ট শিক্ষার্থী", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // If Class-based scope is selected
                    if (state.scope.scopeType == "CLASS") {
                        Text(
                            text = "যে যে শ্রেণির জন্য সিট প্ল্যান তৈরি করবেন:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableClasses.forEach { cls ->
                                val isSelected = cls in state.scope.selectedClasses
                                val count = allStudents.count { it.studentClass == cls }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val newClasses = if (isSelected) {
                                            state.scope.selectedClasses - cls
                                        } else {
                                            state.scope.selectedClasses + cls
                                        }
                                        onStateChange(
                                            state.copy(
                                                scope = state.scope.copy(selectedClasses = newClasses)
                                            )
                                        )
                                    },
                                    label = { Text("$cls ($count)") },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // If Student-based scope is selected
                    if (state.scope.scopeType == "STUDENT") {
                        val selCount = state.scope.selectedStudentIds.size
                        OutlinedButton(
                            onClick = onOpenStudentPicker,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PersonSearch, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selCount == 0) "শিক্ষার্থী নির্বাচন করুন (০ জন নির্বাচিত)"
                                else "$selCount জন শিক্ষার্থী নির্বাচিত (পরিবর্তন করতে চাপুন)"
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Sorting & Bench Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ক্রমানুসার (Sorting):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.scope.sortBy == "CLASS_AND_ROLL",
                                onClick = { onStateChange(state.copy(scope = state.scope.copy(sortBy = "CLASS_AND_ROLL"))) },
                                label = { Text("শ্রেণি ও রোল", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = state.scope.sortBy == "ROLL",
                                onClick = { onStateChange(state.copy(scope = state.scope.copy(sortBy = "ROLL"))) },
                                label = { Text("শুধু রোল", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Optional Room No
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = state.fields.showRoomNumber,
                                onCheckedChange = {
                                    onStateChange(
                                        state.copy(fields = state.fields.copy(showRoomNumber = it))
                                    )
                                }
                            )
                            Text("কক্ষ নং যুক্ত করুন", fontSize = 13.sp)
                        }

                        if (state.fields.showRoomNumber) {
                            OutlinedTextField(
                                value = state.fields.roomNumberText,
                                onValueChange = {
                                    onStateChange(
                                        state.copy(fields = state.fields.copy(roomNumberText = it))
                                    )
                                },
                                placeholder = { Text("যেমন: ১০১") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Layout, Grid Presets & Visual Fields Setup
 */
@Composable
private fun SeatPlanLayoutTab(
    state: SeatPlanMakerState,
    onStateChange: (SeatPlanMakerState) -> Unit
) {
    val p = state.page
    val f = state.fields

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Grid Presets
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "গ্রিড ও প্রিসেট সাজেশন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = "একটি A4 পেইজে কলাম ও রো সংখ্যা অনুযায়ী প্রিসেট নির্বাচন করুন:",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeatPlanStorage.GRID_PRESETS.chunked(2).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPresets.forEach { preset ->
                                    val isSelected = p.columns == preset.columns && p.rows == preset.rows
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onStateChange(
                                                    state.copy(
                                                        page = p.copy(
                                                            columns = preset.columns,
                                                            rows = preset.rows
                                                        )
                                                    )
                                                )
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = preset.titleBn,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (preset.isRecommended) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    ) {
                                                        Text(
                                                            "আদর্শ",
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = preset.subtitleBn,
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Manual Columns and Rows Adjuster
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Columns
                        Column(modifier = Modifier.weight(1f)) {
                            Text("কলাম সংখ্যা: ${p.columns}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (p.columns > 1) onStateChange(state.copy(page = p.copy(columns = p.columns - 1))) },
                                    enabled = p.columns > 1
                                ) {
                                    Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Decrease")
                                }
                                Text("${p.columns}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(
                                    onClick = { if (p.columns < 4) onStateChange(state.copy(page = p.copy(columns = p.columns + 1))) },
                                    enabled = p.columns < 4
                                ) {
                                    Icon(Icons.Filled.AddCircleOutline, contentDescription = "Increase")
                                }
                            }
                        }

                        // Rows
                        Column(modifier = Modifier.weight(1f)) {
                            Text("রো সংখ্যা: ${p.rows}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (p.rows > 1) onStateChange(state.copy(page = p.copy(rows = p.rows - 1))) },
                                    enabled = p.rows > 1
                                ) {
                                    Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Decrease")
                                }
                                Text("${p.rows}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(
                                    onClick = { if (p.rows < 12) onStateChange(state.copy(page = p.copy(rows = p.rows + 1))) },
                                    enabled = p.rows < 12
                                ) {
                                    Icon(Icons.Filled.AddCircleOutline, contentDescription = "Increase")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Card Appearance & Border Settings
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "কার্ডের ফ্রেম ও কর্নার স্টাইল",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Corner radius
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("কার্ডের কোনা (Corner Radius):", fontSize = 13.sp)
                            Text("${f.cardCornerRadiusDp.toInt()} dp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = f.cardCornerRadiusDp,
                            onValueChange = { onStateChange(state.copy(fields = f.copy(cardCornerRadiusDp = it))) },
                            valueRange = 0f..20f,
                            steps = 19
                        )
                    }

                    // Border style
                    Text("বর্ডার স্টাইল:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "solid" to "সলিড",
                            "dashed" to "ড্যাশড",
                            "dotted" to "ডটেড",
                            "double" to "ডাবল"
                        ).forEach { (styleKey, styleBn) ->
                            FilterChip(
                                selected = f.cardBorderStyle == styleKey,
                                onClick = { onStateChange(state.copy(fields = f.copy(cardBorderStyle = styleKey))) },
                                label = { Text(styleBn, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Cutting Line
                    Text("কাটিং লাইন (কাটার দাগ):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "dotted" to "ডটেড",
                            "dashed" to "ড্যাশড",
                            "solid" to "সলিড",
                            "none" to "নাই"
                        ).forEach { (styleKey, styleBn) ->
                            FilterChip(
                                selected = p.cuttingLineStyle == styleKey,
                                onClick = { onStateChange(state.copy(page = p.copy(cuttingLineStyle = styleKey))) },
                                label = { Text(styleBn, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Card Gaps (Horizontal & Vertical)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("হরাইজন্টাল গ্যাপ: ${p.horizontalGapMm.toInt()} mm", fontSize = 12.sp)
                            Slider(
                                value = p.horizontalGapMm,
                                onValueChange = { onStateChange(state.copy(page = p.copy(horizontalGapMm = it))) },
                                valueRange = 0f..8f,
                                steps = 7
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ভার্টিক্যাল গ্যাপ: ${p.verticalGapMm.toInt()} mm", fontSize = 12.sp)
                            Slider(
                                value = p.verticalGapMm,
                                onValueChange = { onStateChange(state.copy(page = p.copy(verticalGapMm = it))) },
                                valueRange = 0f..8f,
                                steps = 7
                            )
                        }
                    }
                }
            }
        }

        // Section: Field Visibility & Typography
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.FormatListBulleted, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "ফিল্ড প্রদর্শন ও ফন্ট সেটিংস",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Toggles
                    SwitchRow("বিদ্যালয়ের নাম দেখান", f.showSchoolName) {
                        onStateChange(state.copy(fields = f.copy(showSchoolName = it)))
                    }
                    SwitchRow("ঠিকানা দেখান", f.showSchoolAddress) {
                        onStateChange(state.copy(fields = f.copy(showSchoolAddress = it)))
                    }
                    SwitchRow("পরীক্ষার নাম দেখান", f.showExamName) {
                        onStateChange(state.copy(fields = f.copy(showExamName = it)))
                    }
                    SwitchRow("আসন বিন্যাস শিরোনাম দেখান", f.showSeatPlanTitle) {
                        onStateChange(state.copy(fields = f.copy(showSeatPlanTitle = it)))
                    }
                    SwitchRow("শিক্ষার্থীর নাম দেখান", f.showStudentName) {
                        onStateChange(state.copy(fields = f.copy(showStudentName = it)))
                    }
                    SwitchRow("শ্রেণি দেখান", f.showStudentClass) {
                        onStateChange(state.copy(fields = f.copy(showStudentClass = it)))
                    }
                    SwitchRow("রোল নম্বর দেখান", f.showRollNumber) {
                        onStateChange(state.copy(fields = f.copy(showRollNumber = it)))
                    }
                    SwitchRow("বাংলা সংখ্যা রূপান্তর (১, ২, ৩...)", f.convertBanglaDigits) {
                        onStateChange(state.copy(fields = f.copy(convertBanglaDigits = it)))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Class Format (১ম vs প্রথম শ্রেণি)
                    Text("শ্রেণির নাম প্রদর্শন ফরম্যাট:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = f.classFormat == "SHORT",
                            onClick = { onStateChange(state.copy(fields = f.copy(classFormat = "SHORT"))) },
                            label = { Text("সংক্ষিপ্ত (১ম)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = f.classFormat == "FULL",
                            onClick = { onStateChange(state.copy(fields = f.copy(classFormat = "FULL"))) },
                            label = { Text("পূর্ণ (প্রথম)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = f.classFormat == "RAW",
                            onClick = { onStateChange(state.copy(fields = f.copy(classFormat = "RAW"))) },
                            label = { Text("মূল ডাটা", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Font Size Scale
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ফন্ট সাইজ স্কেল:", fontSize = 13.sp)
                            Text("${String.format("%.1f", f.headerFontSizeScale)}x", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Slider(
                            value = f.headerFontSizeScale,
                            onValueChange = {
                                onStateChange(
                                    state.copy(
                                        fields = f.copy(
                                            headerFontSizeScale = it,
                                            contentFontSizeScale = it
                                        )
                                    )
                                )
                            },
                            valueRange = 0.8f..1.3f,
                            steps = 5
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 3: High-Fidelity Output Preview & Direct Print
 */
@Composable
private fun SeatPlanPreviewTab(
    state: SeatPlanMakerState,
    students: List<AdmitCardStudent>,
    bengaliFont: AppBengaliFont,
    onOpenSettings: () -> Unit,
    onPrint: () -> Unit,
    onExportPdf: () -> Unit
) {
    val cpp = state.page.totalCardsPerPage
    val totalPages = if (students.isEmpty()) 1 else ((students.size + cpp - 1) / cpp)
    var currentPageIndex by remember { mutableIntStateOf(0) }

    // Ensure currentPageIndex is valid if student list changes
    LaunchedEffect(totalPages) {
        if (currentPageIndex >= totalPages) {
            currentPageIndex = (totalPages - 1).coerceAtLeast(0)
        }
    }

    val pageStudents = remember(students, currentPageIndex, cpp) {
        val start = currentPageIndex * cpp
        if (start < students.size) {
            students.subList(start, Math.min(start + cpp, students.size))
        } else {
            emptyList()
        }
    }

    // Render exact high-resolution preview bitmap on background thread
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }

    LaunchedEffect(state, pageStudents, currentPageIndex, bengaliFont) {
        isRendering = true
        withContext(Dispatchers.Default) {
            try {
                val (wPt, hPt) = SeatPlanNativePdfUtil.getPageDimensionsPt(state.page.pageSize, state.page.orientation)
                // Scale factor for screen preview rendering
                val scale = 1.6f
                val bmpW = (wPt * scale).toInt()
                val bmpH = (hPt * scale).toInt()

                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.scale(scale, scale)

                SeatPlanNativePdfUtil.drawPage(
                    canvas = canvas,
                    pageWidth = wPt,
                    pageHeight = hPt,
                    students = pageStudents,
                    startIndex = currentPageIndex * cpp,
                    state = state,
                    bengaliFont = bengaliFont
                )
                previewBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRendering = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        // Control Bar: Summary info & Page navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Info badges
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "মোট ${BanglaUtils.toBanglaDigits(students.size)} জন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${BanglaUtils.toBanglaDigits(state.page.columns)}×${BanglaUtils.toBanglaDigits(state.page.rows)} (${BanglaUtils.toBanglaDigits(cpp)} টি/পেজ)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Page Navigator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev Page")
                    }

                    Text(
                        text = "পৃষ্ঠা ${BanglaUtils.toBanglaDigits(currentPageIndex + 1)} / ${BanglaUtils.toBanglaDigits(totalPages)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    IconButton(
                        onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                        enabled = currentPageIndex < totalPages - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Page")
                    }
                }
            }
        }

        // Live Render Area with Paper aspect ratio & Shadow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Card(
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            if (state.page.orientation == "landscape") 1.414f else 0.707f // A4 ratio
                        )
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Seat Plan Exact Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * Compact Quick Settings Bottom Sheet
 */
@Composable
private fun SeatPlanQuickSettingsContent(
    state: SeatPlanMakerState,
    onStateChange: (SeatPlanMakerState) -> Unit,
    onDismiss: () -> Unit
) {
    val p = state.page
    val f = state.fields

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "কমপ্যাক্ট সিট প্ল্যান সেটিংস",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        // Quick Layout Grid buttons
        Text("গ্রিড লেআউট প্রিসেট:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SeatPlanStorage.GRID_PRESETS) { preset ->
                val isSelected = p.columns == preset.columns && p.rows == preset.rows
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onStateChange(
                            state.copy(
                                page = p.copy(
                                    columns = preset.columns,
                                    rows = preset.rows
                                )
                            )
                        )
                    },
                    label = { Text(preset.titleBn, fontSize = 11.5.sp) }
                )
            }
        }

        // Font scaling
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ফন্ট সাইজ স্কেল:", fontSize = 13.sp)
                Text("${String.format("%.1f", f.headerFontSizeScale)}x", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = f.headerFontSizeScale,
                onValueChange = {
                    onStateChange(
                        state.copy(
                            fields = f.copy(
                                headerFontSizeScale = it,
                                contentFontSizeScale = it
                            )
                        )
                    )
                },
                valueRange = 0.8f..1.3f,
                steps = 5
            )
        }

        // Corner Radius
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("কার্ডের কোনা (Corner Radius):", fontSize = 13.sp)
                Text("${f.cardCornerRadiusDp.toInt()} dp", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = f.cardCornerRadiusDp,
                onValueChange = { onStateChange(state.copy(fields = f.copy(cardCornerRadiusDp = it))) },
                valueRange = 0f..20f,
                steps = 19
            )
        }

        // Cutting Line
        Text("কাটিং লাইন:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "dotted" to "ডটেড",
                "dashed" to "ড্যাশড",
                "solid" to "সলিড",
                "none" to "নাই"
            ).forEach { (styleKey, styleBn) ->
                FilterChip(
                    selected = p.cuttingLineStyle == styleKey,
                    onClick = { onStateChange(state.copy(page = p.copy(cuttingLineStyle = styleKey))) },
                    label = { Text(styleBn, fontSize = 11.5.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("সম্পন্ন")
        }
    }
}

/**
 * Multi Student Picker Bottom Sheet
 */
@Composable
private fun SeatPlanStudentPickerContent(
    allStudents: List<AdmitCardStudent>,
    selectedIds: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSet by remember { mutableStateOf(selectedIds.toSet()) }

    val filteredList = remember(allStudents, searchQuery) {
        if (searchQuery.isBlank()) allStudents
        else allStudents.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.rollNumber.contains(searchQuery) ||
            it.studentClass.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "নির্দিষ্ট শিক্ষার্থী নির্বাচন (${selectedSet.size} জন নির্বাচিত)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("নাম, শ্রেণি বা রোল লিখে খুঁজুন...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { selectedSet = allStudents.map { it.id }.toSet() },
                modifier = Modifier.weight(1f)
            ) {
                Text("সবাইকে নির্বাচন করুন", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { selectedSet = emptySet() },
                modifier = Modifier.weight(1f)
            ) {
                Text("বাতিল করুন", fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredList) { student ->
                val isChecked = student.id in selectedSet
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedSet = if (isChecked) selectedSet - student.id else selectedSet + student.id
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(student.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "শ্রেণি: ${student.studentClass} • রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                selectedSet = if (isChecked) selectedSet - student.id else selectedSet + student.id
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                onSelectionChange(selectedSet.toList())
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("সংরক্ষণ করুন (${selectedSet.size} জন)")
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 13.5.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
