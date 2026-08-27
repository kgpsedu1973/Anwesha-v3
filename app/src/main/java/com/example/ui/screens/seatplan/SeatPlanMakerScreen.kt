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
import androidx.compose.ui.text.font.FontFamily
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
    val currentClassPreset by viewModel.classPreset.collectAsState()

    // Strictly Noto Serif Bengali for Seat Plan
    val notoSerifFont = AppBengaliFont.NOTO_SERIF_BENGALI

    val defaultSchoolName = remember(schoolInfo) {
        schoolInfo?.schoolName?.ifBlank { null } ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
    }
    val defaultSchoolAddress = remember(schoolInfo) {
        schoolInfo?.address?.ifBlank { null } ?: "আলফাডাঙ্গা, ফরিদপুর।"
    }

    var state by remember {
        mutableStateOf(SeatPlanStorage.loadState(context, defaultSchoolName, defaultSchoolAddress))
    }

    // Auto-update school info if available from system settings
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

    // Convert students to list with sample fallback if empty for instant preview
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

    // Modal Sheet State Controls
    var showLayoutSettingsSheet by remember { mutableStateOf(false) }
    var showFieldFontSettingsSheet by remember { mutableStateOf(false) }
    var showDataInputSheet by remember { mutableStateOf(false) }
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
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "আসন বিন্যাস ও বেঞ্চ স্টিকার জেনারেটর (ইঞ্চি পরিমাপ)",
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
                    IconButton(
                        onClick = { showDataInputSheet = true },
                        modifier = Modifier.testTag("seat_plan_data_btn")
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = "Edit Text & Info")
                    }
                    IconButton(
                        onClick = { showFieldFontSettingsSheet = true },
                        modifier = Modifier.testTag("seat_plan_font_btn")
                    ) {
                        Icon(Icons.Filled.FormatSize, contentDescription = "Field & Font Settings")
                    }
                    IconButton(
                        onClick = { showLayoutSettingsSheet = true },
                        modifier = Modifier.testTag("seat_plan_layout_btn")
                    ) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = "Layout & Grid Settings")
                    }
                    IconButton(
                        onClick = {
                            SeatPlanNativePdfUtil.exportAndSharePdf(
                                context = context,
                                state = state,
                                students = filteredStudents,
                                bengaliFont = notoSerifFont
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    SeatPlanNativePdfUtil.printSeatPlansDirectly(
                        context = context,
                        state = state,
                        students = filteredStudents,
                        bengaliFont = notoSerifFont
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // The unified Print & Preview Screen with Student Selection & Scope at top
            SeatPlanUnifiedPreviewScreen(
                state = state,
                students = filteredStudents,
                totalAllStudents = effectiveStudents.size,
                availableClasses = availableClasses,
                allStudents = effectiveStudents,
                onStateChange = { updateState(it) },
                onOpenLayoutSettings = { showLayoutSettingsSheet = true },
                onOpenFontSettings = { showFieldFontSettingsSheet = true },
                onOpenDataEdit = { showDataInputSheet = true },
                onOpenStudentPicker = { showStudentPickerSheet = true },
                onPrint = {
                    SeatPlanNativePdfUtil.printSeatPlansDirectly(
                        context = context,
                        state = state,
                        students = filteredStudents,
                        bengaliFont = notoSerifFont
                    )
                },
                onExportPdf = {
                    SeatPlanNativePdfUtil.exportAndSharePdf(
                        context = context,
                        state = state,
                        students = filteredStudents,
                        bengaliFont = notoSerifFont
                    )
                }
            )
        }
    }

    // 1. Popup Sheet: Layout & Grid Settings (All in Inches)
    if (showLayoutSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLayoutSettingsSheet = false }
        ) {
            SeatPlanLayoutSettingsPopup(
                state = state,
                onStateChange = { updateState(it) },
                onDismiss = { showLayoutSettingsSheet = false }
            )
        }
    }

    // 2. Popup Sheet: Compact Field Font Sizes & Style Settings
    if (showFieldFontSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFieldFontSettingsSheet = false }
        ) {
            SeatPlanFieldFontSettingsPopup(
                state = state,
                onStateChange = { updateState(it) },
                onDismiss = { showFieldFontSettingsSheet = false }
            )
        }
    }

    // 3. Popup Sheet: Text Data Input with Frequency-Based Smart Suggestions
    if (showDataInputSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDataInputSheet = false }
        ) {
            SeatPlanDataInputPopup(
                state = state,
                onStateChange = { updateState(it) },
                onDismiss = { showDataInputSheet = false }
            )
        }
    }

    // 4. Student Multi-picker Sheet
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
 * Unified Print & Preview Screen with Student Selection & Scope placed at the top
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatPlanUnifiedPreviewScreen(
    state: SeatPlanMakerState,
    students: List<AdmitCardStudent>,
    totalAllStudents: Int,
    availableClasses: List<String>,
    allStudents: List<AdmitCardStudent>,
    onStateChange: (SeatPlanMakerState) -> Unit,
    onOpenLayoutSettings: () -> Unit,
    onOpenFontSettings: () -> Unit,
    onOpenDataEdit: () -> Unit,
    onOpenStudentPicker: () -> Unit,
    onPrint: () -> Unit,
    onExportPdf: () -> Unit
) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val cardsPerPage = state.page.totalCardsPerPage
    val totalPages = remember(students.size, cardsPerPage) {
        if (students.isEmpty()) 1 else ((students.size + cardsPerPage - 1) / cardsPerPage)
    }

    // Clamp page index
    LaunchedEffect(totalPages) {
        if (currentPageIndex >= totalPages) {
            currentPageIndex = (totalPages - 1).coerceAtLeast(0)
        }
    }

    // Bitmap rendering for canvas preview
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }

    val (pageW, pageH) = remember(state.page.pageSize, state.page.orientation) {
        SeatPlanNativePdfUtil.getPageDimensionsPt(state.page.pageSize, state.page.orientation)
    }

    LaunchedEffect(state, students, currentPageIndex) {
        isRendering = true
        withContext(Dispatchers.Default) {
            try {
                val scale = 1.3f // Crisp preview rendering scale
                val bmpW = (pageW * scale).toInt()
                val bmpH = (pageH * scale).toInt()
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.scale(scale, scale)

                val pageStudents = students.drop(currentPageIndex * cardsPerPage).take(cardsPerPage)
                SeatPlanNativePdfUtil.drawPage(
                    canvas = canvas,
                    pageWidth = pageW,
                    pageHeight = pageH,
                    students = pageStudents,
                    startIndex = currentPageIndex * cardsPerPage,
                    state = state,
                    bengaliFont = AppBengaliFont.NOTO_SERIF_BENGALI
                )
                previewBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRendering = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Section: শিক্ষার্থী নির্বাচন ও পরিধি (Moved to Print & Preview tab)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "শিক্ষার্থী নির্বাচন ও পরিধি",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${students.size} জন নির্বাচিত",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Scope options: All, Class-based, Specific student
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = state.scope.scopeType == "ALL",
                            onClick = {
                                onStateChange(state.copy(scope = state.scope.copy(scopeType = "ALL")))
                            },
                            label = { Text("সকল (${totalAllStudents})", fontSize = 11.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.scope.scopeType == "CLASS",
                            onClick = {
                                onStateChange(state.copy(scope = state.scope.copy(scopeType = "CLASS")))
                            },
                            label = { Text("শ্রেণিভিত্তিক", fontSize = 11.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.scope.scopeType == "STUDENT",
                            onClick = {
                                onStateChange(state.copy(scope = state.scope.copy(scopeType = "STUDENT")))
                            },
                            label = { Text("নির্দিষ্ট শিক্ষার্থী", fontSize = 11.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Class list selector if "CLASS" is chosen
                    if (state.scope.scopeType == "CLASS") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    label = { Text("$cls ($count)", fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Specific student picker button if "STUDENT" is chosen
                    if (state.scope.scopeType == "STUDENT") {
                        val selCount = state.scope.selectedStudentIds.size
                        OutlinedButton(
                            onClick = onOpenStudentPicker,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (selCount == 0) "শিক্ষার্থী নির্বাচন করুন (০ জন নির্বাচিত)"
                                else "$selCount জন শিক্ষার্থী নির্বাচিত (পরিবর্তন করতে চাপুন)",
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    // Sorting & Fast Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("সাজানো:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            FilterChip(
                                selected = state.scope.sortBy == "CLASS_AND_ROLL",
                                onClick = { onStateChange(state.copy(scope = state.scope.copy(sortBy = "CLASS_AND_ROLL"))) },
                                label = { Text("শ্রেণি ও রোল", fontSize = 10.5.sp) }
                            )
                            FilterChip(
                                selected = state.scope.sortBy == "ROLL",
                                onClick = { onStateChange(state.copy(scope = state.scope.copy(sortBy = "ROLL"))) },
                                label = { Text("শুধু রোল", fontSize = 10.5.sp) }
                            )
                        }

                        IconButton(
                            onClick = onOpenDataEdit,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Text", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // 2. Quick Settings Bar: Layout, Fonts & Exam Info
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenLayoutSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("লেআউট ও গ্রিড", fontSize = 11.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onOpenFontSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ফন্ট ও ফিল্ড সাইজ", fontSize = 11.5.sp, maxLines = 1)
                }
            }
        }

        // 3. Print Preview Canvas Frame & Page Navigation
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Page navigation header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "প্রিন্ট প্রিভিউ (${state.page.pageSize} • ${state.page.columns}×${state.page.rows})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }

                        // Pagination controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev")
                            }

                            Text(
                                text = "পৃষ্ঠা ${currentPageIndex + 1} / $totalPages",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                                enabled = currentPageIndex < totalPages - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                            }
                        }
                    }

                    // Rendered Visual Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(pageW / pageH)
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Seat Plan Page Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    }

                    // Bottom info label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "মোট শিক্ষার্থী: ${students.size} জন • প্রতি পাতায় $cardsPerPage টি",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "ফন্ট: Noto Serif Bengali",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bottom Spacer for Floating Action Button clearance
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

/**
 * 1. Popup: Layout & Grid Settings (All Measurements in Inches)
 */
@Composable
private fun SeatPlanLayoutSettingsPopup(
    state: SeatPlanMakerState,
    onStateChange: (SeatPlanMakerState) -> Unit,
    onDismiss: () -> Unit
) {
    val p = state.page
    val f = state.fields

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("লেআউট ও গ্রিড সেটিংস (ইঞ্চি)", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }

        // Grid Presets
        item {
            Text("প্রস্তাবিত গ্রিড প্রিসেট:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                            fontSize = 12.sp,
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
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Manual Columns and Rows Counter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columns
                Column(modifier = Modifier.weight(1f)) {
                    Text("কলাম: ${p.columns}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("রো: ${p.rows}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

        // Page Size
        item {
            Text("পেইজ সাইজ:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("A4" to "A4", "Letter" to "Letter", "Legal" to "Legal").forEach { (sz, label) ->
                    FilterChip(
                        selected = p.pageSize == sz,
                        onClick = { onStateChange(state.copy(page = p.copy(pageSize = sz))) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Card Margins (Inches) with Suggestion Chips
        item {
            Text("পেইজ মার্জিন (ইঞ্চি):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val marginSuggestions = SeatPlanStorage.getMarginInchSuggestions()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    marginSuggestions.forEach { mVal ->
                        FilterChip(
                            selected = p.marginTopInch == mVal && p.marginLeftInch == mVal,
                            onClick = {
                                onStateChange(
                                    state.copy(
                                        page = p.copy(
                                            marginTopInch = mVal,
                                            marginBottomInch = mVal,
                                            marginLeftInch = mVal,
                                            marginRightInch = mVal
                                        )
                                    )
                                )
                            },
                            label = { Text("${String.format("%.2f", mVal)}″", fontSize = 11.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Custom Slider in Inches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("মার্জিন মান:", fontSize = 12.sp)
                    Text("${String.format("%.2f", p.marginTopInch)} ইঞ্চি", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Slider(
                    value = p.marginTopInch,
                    onValueChange = {
                        onStateChange(
                            state.copy(
                                page = p.copy(
                                    marginTopInch = it,
                                    marginBottomInch = it,
                                    marginLeftInch = it,
                                    marginRightInch = it
                                )
                            )
                        )
                    },
                    valueRange = 0.10f..0.60f,
                    steps = 10
                )
            }
        }

        // Horizontal & Vertical Gaps (Inches)
        item {
            Text("কার্ডের মাঝের গ্যাপ (ইঞ্চি):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val gapSuggestions = SeatPlanStorage.getGapInchSuggestions()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(gapSuggestions) { gVal ->
                        FilterChip(
                            selected = (p.horizontalGapInch - gVal) in -0.01f..0.01f,
                            onClick = {
                                onStateChange(
                                    state.copy(
                                        page = p.copy(
                                            horizontalGapInch = gVal,
                                            verticalGapInch = gVal
                                        )
                                    )
                                )
                            },
                            label = { Text("${String.format("%.2f", gVal)}″", fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("গ্যাপ মান:", fontSize = 12.sp)
                    Text("${String.format("%.2f", p.horizontalGapInch)} ইঞ্চি", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Slider(
                    value = p.horizontalGapInch,
                    onValueChange = {
                        onStateChange(
                            state.copy(
                                page = p.copy(
                                    horizontalGapInch = it,
                                    verticalGapInch = it
                                )
                            )
                        )
                    },
                    valueRange = 0.00f..0.25f,
                    steps = 10
                )
            }
        }

        // Cutting Lines & Card Border Styles
        item {
            Text("কাটিং লাইন (কাটার দাগ):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("dotted" to "ডটেড", "dashed" to "ড্যাশড", "solid" to "সলিড", "none" to "নাই").forEach { (st, label) ->
                    FilterChip(
                        selected = p.cuttingLineStyle == st,
                        onClick = { onStateChange(state.copy(page = p.copy(cuttingLineStyle = st))) },
                        label = { Text(label, fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Text("কার্ড বর্ডার স্টাইল:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("solid" to "সলিড", "double" to "ডাবল", "dashed" to "ড্যাশড", "dotted" to "ডটেড").forEach { (st, label) ->
                    FilterChip(
                        selected = f.cardBorderStyle == st,
                        onClick = { onStateChange(state.copy(fields = f.copy(cardBorderStyle = st))) },
                        label = { Text(label, fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("সম্পন্ন")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 2. Popup: Compact Field Font Sizes & Style Settings
 */
@Composable
private fun SeatPlanFieldFontSettingsPopup(
    state: SeatPlanMakerState,
    onStateChange: (SeatPlanMakerState) -> Unit,
    onDismiss: () -> Unit
) {
    val f = state.fields

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("ফিল্ড ও ফন্ট সাইজ সেটিংস", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("ফন্ট: Noto Serif Bengali (ডিফল্ট সক্রিয়)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 1. School Name Settings
        item {
            CompactFieldRow(
                title = "১. বিদ্যালয়ের নাম",
                isVisible = f.showSchoolName,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showSchoolName = it))) },
                fontSizePt = f.schoolNameFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(schoolNameFontSizePt = it))) },
                isBold = f.isSchoolNameBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isSchoolNameBold = it))) },
                minSize = 9f,
                maxSize = 16f
            )
        }

        // 2. School Address Settings
        item {
            CompactFieldRow(
                title = "২. ঠিকানা / উপজেলা, জেলা",
                isVisible = f.showSchoolAddress,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showSchoolAddress = it))) },
                fontSizePt = f.addressFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(addressFontSizePt = it))) },
                isBold = f.isAddressBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isAddressBold = it))) },
                minSize = 7f,
                maxSize = 14f
            )
        }

        // 3. Exam Name Settings
        item {
            CompactFieldRow(
                title = "৩. পরীক্ষার নাম",
                isVisible = f.showExamName,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showExamName = it))) },
                fontSizePt = f.examNameFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(examNameFontSizePt = it))) },
                isBold = f.isExamNameBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isExamNameBold = it))) },
                minSize = 8f,
                maxSize = 14f
            )
        }

        // 4. Seat Plan Title Settings
        item {
            CompactFieldRow(
                title = "৪. কার্ডের শিরোনাম (আসন বিন্যাস)",
                isVisible = f.showSeatPlanTitle,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showSeatPlanTitle = it))) },
                fontSizePt = f.titleFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(titleFontSizePt = it))) },
                isBold = f.isTitleBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isTitleBold = it))) },
                minSize = 8f,
                maxSize = 15f
            )
        }

        // 5. Student Name Settings
        item {
            CompactFieldRow(
                title = "৫. শিক্ষার্থীর নাম",
                isVisible = f.showStudentName,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showStudentName = it))) },
                fontSizePt = f.studentNameFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(studentNameFontSizePt = it))) },
                isBold = f.isStudentNameBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isStudentNameBold = it))) },
                minSize = 8f,
                maxSize = 15f
            )
        }

        // 6. Student Class Settings & Format
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactFieldRow(
                    title = "৬. শ্রেণি",
                    isVisible = f.showStudentClass,
                    onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showStudentClass = it))) },
                    fontSizePt = f.classFontSizePt,
                    onFontSizeChange = { onStateChange(state.copy(fields = f.copy(classFontSizePt = it))) },
                    isBold = f.isClassBold,
                    onBoldChange = { onStateChange(state.copy(fields = f.copy(isClassBold = it))) },
                    minSize = 8f,
                    maxSize = 15f
                )

                if (f.showStudentClass) {
                    Text("শ্রেণির নাম ফরম্যাট:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("SHORT" to "সংক্ষিপ্ত (১ম)", "FULL" to "পূর্ণ (প্রথম)", "RAW" to "মূল ডাটা").forEach { (fmt, label) ->
                            FilterChip(
                                selected = f.classFormat == fmt,
                                onClick = { onStateChange(state.copy(fields = f.copy(classFormat = fmt))) },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 7. Roll Number Settings
        item {
            CompactFieldRow(
                title = "৭. রোল নম্বর",
                isVisible = f.showRollNumber,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showRollNumber = it))) },
                fontSizePt = f.rollFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(rollFontSizePt = it))) },
                isBold = f.isRollBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isRollBold = it))) },
                minSize = 8f,
                maxSize = 15f
            )
        }

        // 8. Room / Bench Number Settings
        item {
            CompactFieldRow(
                title = "৮. কক্ষ / বেঞ্চ নম্বর",
                isVisible = f.showRoomNumber,
                onVisibilityChange = { onStateChange(state.copy(fields = f.copy(showRoomNumber = it))) },
                fontSizePt = f.roomFontSizePt,
                onFontSizeChange = { onStateChange(state.copy(fields = f.copy(roomFontSizePt = it))) },
                isBold = f.isRoomBold,
                onBoldChange = { onStateChange(state.copy(fields = f.copy(isRoomBold = it))) },
                minSize = 8f,
                maxSize = 15f
            )
        }

        // Digit conversion
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onStateChange(state.copy(fields = f.copy(convertBanglaDigits = !f.convertBanglaDigits)))
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("বাংলা সংখ্যা রূপান্তর (১, ২, ৩...)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("রোল, সাল ও কক্ষ নম্বর বাংলায় রূপান্তর করবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = f.convertBanglaDigits,
                    onCheckedChange = { onStateChange(state.copy(fields = f.copy(convertBanglaDigits = it))) }
                )
            }
        }

        item {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("সম্পন্ন")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Helper: Compact Field Setting Row with Visibility, Font Size (Pt) and Bold Toggle
 */
@Composable
private fun CompactFieldRow(
    title: String,
    isVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    fontSizePt: Float,
    onFontSizeChange: (Float) -> Unit,
    isBold: Boolean,
    onBoldChange: (Boolean) -> Unit,
    minSize: Float = 8f,
    maxSize: Float = 16f
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isVisible) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Checkbox(
                        checked = isVisible,
                        onCheckedChange = onVisibilityChange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = title,
                        fontSize = 12.5.sp,
                        fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isVisible) {
                    // Bold Toggle Button
                    FilterChip(
                        selected = isBold,
                        onClick = { onBoldChange(!isBold) },
                        label = { Text("B", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            if (isVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "সাইজ: ${String.format("%.1f", fontSizePt)} pt",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp)
                    )

                    Slider(
                        value = fontSizePt,
                        onValueChange = onFontSizeChange,
                        valueRange = minSize..maxSize,
                        steps = ((maxSize - minSize) * 2).toInt(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 3. Popup: Text Data Input with Frequency-Based Smart Suggestions + Manual Input
 */
@Composable
private fun SeatPlanDataInputPopup(
    state: SeatPlanMakerState,
    onStateChange: (SeatPlanMakerState) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val examSuggestions = remember { SeatPlanStorage.getExamSuggestions(context) }
    val titleSuggestions = remember { SeatPlanStorage.getTitleSuggestions(context) }
    val roomSuggestions = remember { SeatPlanStorage.getRoomSuggestions(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("প্রতিষ্ঠান ও পরীক্ষার তথ্য", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Serif)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }

        // School Name
        item {
            OutlinedTextField(
                value = state.schoolName,
                onValueChange = { onStateChange(state.copy(schoolName = it)) },
                label = { Text("বিদ্যালয়ের নাম") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // School Address
        item {
            OutlinedTextField(
                value = state.schoolAddress,
                onValueChange = { onStateChange(state.copy(schoolAddress = it)) },
                label = { Text("ঠিকানা / উপজেলা, জেলা") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Exam Name with Smart Suggestion Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.examName,
                    onValueChange = { onStateChange(state.copy(examName = it)) },
                    label = { Text("পরীক্ষার নাম / মূল্যায়ন") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("সাজেশন (ঘন ঘন ব্যবহৃত মানসমূহ):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(examSuggestions) { examText ->
                        SuggestionChip(
                            onClick = { onStateChange(state.copy(examName = examText)) },
                            label = { Text(examText, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Seat Plan Title with Smart Suggestion Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.fields.seatPlanTitleText,
                    onValueChange = {
                        onStateChange(state.copy(fields = state.fields.copy(seatPlanTitleText = it)))
                    },
                    label = { Text("কার্ডের শিরোনাম") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("শিরোনাম সাজেশন:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(titleSuggestions) { titleText ->
                        SuggestionChip(
                            onClick = {
                                onStateChange(state.copy(fields = state.fields.copy(seatPlanTitleText = titleText)))
                            },
                            label = { Text(titleText, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Room Number with Smart Suggestions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.fields.showRoomNumber,
                        onCheckedChange = {
                            onStateChange(state.copy(fields = state.fields.copy(showRoomNumber = it)))
                        }
                    )
                    Text("কক্ষ নং যুক্ত করুন", fontSize = 13.sp)
                }

                if (state.fields.showRoomNumber) {
                    OutlinedTextField(
                        value = state.fields.roomNumberText,
                        onValueChange = {
                            onStateChange(state.copy(fields = state.fields.copy(roomNumberText = it)))
                        },
                        placeholder = { Text("যেমন: ১০১ / হল রুম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("কক্ষ সাজেশন:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(roomSuggestions) { roomText ->
                            SuggestionChip(
                                onClick = {
                                    onStateChange(state.copy(fields = state.fields.copy(roomNumberText = roomText)))
                                },
                                label = { Text(roomText, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("সংরক্ষণ ও সম্পন্ন")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 4. Multi-student Picker Sheet
 */
@Composable
private fun SeatPlanStudentPickerContent(
    allStudents: List<AdmitCardStudent>,
    selectedIds: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterClass by remember { mutableStateOf<String?>(null) }

    val allClasses = remember(allStudents) {
        allStudents.map { it.studentClass }.distinct().filter { it.isNotBlank() }
    }

    val displayedStudents = remember(allStudents, searchQuery, selectedFilterClass) {
        allStudents.filter { s ->
            (selectedFilterClass == null || s.studentClass == selectedFilterClass) &&
            (searchQuery.isBlank() || s.name.contains(searchQuery, ignoreCase = true) || s.rollNumber.contains(searchQuery))
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "নির্দিষ্ট শিক্ষার্থী নির্বাচন (${selectedIds.size} জন নির্বাচিত)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("নাম বা রোল দিয়ে খুঁজুন...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Class filters
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedFilterClass == null,
                    onClick = { selectedFilterClass = null },
                    label = { Text("সকল শ্রেণি") }
                )
            }
            items(allClasses) { cls ->
                FilterChip(
                    selected = selectedFilterClass == cls,
                    onClick = { selectedFilterClass = if (selectedFilterClass == cls) null else cls },
                    label = { Text(cls) }
                )
            }
        }

        // Selection Actions: Select All displayed / Deselect All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val displayedIds = displayedStudents.map { it.id }
                    val merged = (selectedIds + displayedIds).distinct()
                    onSelectionChange(merged)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("দৃশ্যমান সকলকে নির্বাচন", fontSize = 11.5.sp)
            }

            OutlinedButton(
                onClick = {
                    val displayedIds = displayedStudents.map { it.id }.toSet()
                    val remaining = selectedIds.filterNot { it in displayedIds }
                    onSelectionChange(remaining)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("নির্বাচন বাতিল", fontSize = 11.5.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(displayedStudents, key = { it.id }) { student ->
                val isSelected = student.id in selectedIds
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        0.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newIds = if (isSelected) selectedIds - student.id else selectedIds + student.id
                            onSelectionChange(newIds)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    val newIds = if (checked) selectedIds + student.id else selectedIds - student.id
                                    onSelectionChange(newIds)
                                }
                            )
                            Column {
                                Text(student.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text("${student.studentClass} • রোল: ${student.rollNumber}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("সম্পন্ন (${selectedIds.size} জন)")
        }
    }
}
