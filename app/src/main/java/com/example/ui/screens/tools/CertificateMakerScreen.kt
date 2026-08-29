package com.example.ui.screens.tools

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
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
import com.example.data.model.CertificateMakerState
import com.example.data.model.CertificateStudent
import com.example.ui.components.AppDatePickerDialog
import com.example.util.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CertificateMakerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val allDbStudents by viewModel.allStudents.collectAsState()
    val currentClassPreset by viewModel.classPreset.collectAsState()
    val activeBengaliFont by viewModel.bengaliFont.collectAsState()

    val defaultSchoolName = remember(schoolInfo) {
        schoolInfo?.schoolName?.ifBlank { null } ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
    }
    val defaultSchoolAddress = remember(schoolInfo) {
        schoolInfo?.address?.ifBlank { null } ?: "আলফাডাঙ্গা, ফরিদপুর।"
    }

    var state by remember {
        mutableStateOf(CertificateStorage.loadState(context, defaultSchoolName, defaultSchoolAddress))
    }

    // Auto-update school info from database if blank
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
            if (sAddr.isNotBlank()) {
                val upazila = if (sAddr.contains("আলফাডাঙ্গা")) "আলফাডাঙ্গা" else sAddr.split(",").getOrNull(0)?.trim() ?: state.upazila
                val district = if (sAddr.contains("ফরিদপুর")) "ফরিদপুর" else sAddr.split(",").getOrNull(1)?.replace("।", "")?.trim() ?: state.district
                if (state.upazila.isBlank() || state.district.isBlank()) {
                    newState = newState.copy(upazila = upazila, district = district)
                    updated = true
                }
            }
            if (updated) {
                state = newState
                CertificateStorage.saveState(context, state)
            }
        }
    }

    // Map DB students to CertificateStudent
    val allStudentsList = remember(allDbStudents, currentClassPreset) {
        if (allDbStudents.isNotEmpty()) {
            allDbStudents.map {
                CertificateStudent(
                    id = it.id,
                    name = it.name,
                    studentClass = it.studentClass,
                    rollNumber = it.rollNumber.toString(),
                    fatherName = it.fatherName,
                    motherName = it.motherName,
                    birthDate = it.birthDate,
                    academicYear = it.academicYear,
                    gender = it.gender
                )
            }
        } else {
            val presetClasses = currentClassPreset.classNames
            val c1 = presetClasses.getOrNull(2) ?: "১ম শ্রেণি"
            val c2 = presetClasses.getOrNull(3) ?: "২য় শ্রেণি"
            val c5 = presetClasses.getOrNull(6) ?: "৫ম শ্রেণি"
            listOf(
                CertificateStudent("1", "মোসাঃ মরিয়ম আক্তার", c5, "১২", "মোঃ রফিকুল ইসলাম", "মোসাঃ পারভীন বেগম", "2015-05-12", "২০২৬", "ছাত্রী"),
                CertificateStudent("2", "মো: কাওসার মল্লিক", c5, "১৫", "মো: আতিয়ার রহমান", "মোসা: সাহিদা বেগম", "2015-08-20", "২০২৬", "ছাত্র"),
                CertificateStudent("3", "তাওহিদ মোল্যা", c1, "১", "মোঃ শফিকুল ইসলাম", "মোসাঃ রহিমা খাতুন", "2019-01-10", "২০২৬", "ছাত্র"),
                CertificateStudent("4", "মোছাঃ মরিয়ম খানম", c1, "২", "মোঃ শাহজাহান খান", "নাজমা বেগম", "2019-03-15", "২০২৬", "ছাত্রী"),
                CertificateStudent("5", "মোঃ তারিফ মাহমুদ", c2, "১", "মোঃ খোকন মাহমুদ", "মোসাঃ শিরিন আক্তার", "2018-06-18", "২০২৬", "ছাত্র"),
                CertificateStudent("6", "ইসরাত জাহান", c2, "৬", "মোঃ আনোয়ার হোসেন", "মোসাঃ বিলকিস বেগম", "2018-11-25", "২০২৬", "ছাত্রী")
            )
        }
    }

    // Filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) } // null = All classes

    // Selected students map (ID -> Boolean)
    var selectedStudentIdSet by remember {
        mutableStateOf(
            if (state.selectedStudentIds.isNotEmpty()) {
                state.selectedStudentIds.toSet()
            } else {
                allStudentsList.map { it.id }.toSet()
            }
        )
    }

    // Distinct available classes
    val availableClasses = remember(allStudentsList, currentClassPreset) {
        val fromStudents = allStudentsList.map { it.studentClass.trim() }.filter { it.isNotBlank() }.distinct()
        if (fromStudents.isNotEmpty()) fromStudents else currentClassPreset.classNames
    }

    // Filtered list based on Search & Class filter
    val displayedStudents = remember(allStudentsList, searchQuery, selectedClassFilter) {
        allStudentsList.filter { student ->
            val matchClass = selectedClassFilter == null || student.studentClass == selectedClassFilter
            val q = searchQuery.trim().lowercase()
            val matchSearch = if (q.isBlank()) true else {
                student.name.lowercase().contains(q) ||
                student.rollNumber.contains(q) ||
                student.fatherName.lowercase().contains(q) ||
                student.studentClass.lowercase().contains(q)
            }
            matchClass && matchSearch
        }
    }

    // Final selected students for print/preview
    val finalSelectedStudents = remember(allStudentsList, selectedStudentIdSet) {
        allStudentsList.filter { selectedStudentIdSet.contains(it.id) }
    }

    // Tabs: 0 = শিক্ষার্থী নির্বাচন, 1 = সার্টিফিকেট ও স্কুল সেটিংস, 2 = লাইভ প্রিভিউ ও প্রিন্ট
    var activeTab by remember { mutableIntStateOf(0) }

    // Dialog state for Date Picker
    var showIssueDatePicker by remember { mutableStateOf(false) }

    // Preview bitmap caching
    var previewStudentIndex by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPreview by remember { mutableStateOf(false) }

    // Update preview bitmap when state, selected students or preview index changes
    LaunchedEffect(state, finalSelectedStudents, previewStudentIndex, activeTab, activeBengaliFont) {
        if (activeTab == 2 && finalSelectedStudents.isNotEmpty()) {
            isRenderingPreview = true
            val curIdx = previewStudentIndex.coerceIn(0, finalSelectedStudents.size - 1)
            val student = finalSelectedStudents[curIdx]
            val serial = state.computeSerialForStudent(student, curIdx)

            withContext(Dispatchers.Default) {
                try {
                    val bmp = CertificateNativePdfUtil.renderPreviewBitmap(
                        state = state,
                        student = student,
                        serialNumber = serial,
                        bengaliFont = activeBengaliFont,
                        previewScale = 1.3f
                    )
                    withContext(Dispatchers.Main) {
                        previewBitmap = bmp
                        isRenderingPreview = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isRenderingPreview = false
                    }
                }
            }
        }
    }

    BackHandler(enabled = true) {
        if (activeTab != 0) {
            activeTab = 0
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "প্রত্যয়নপত্র ও প্রশংসাপত্র মেকার",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "লিগ্যাল ল্যান্ডস্কেপ • অফিস কপি ও মূল প্রত্যয়নপত্র",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeTab != 0) activeTab = 0 else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Direct Print
                    IconButton(
                        onClick = {
                            if (finalSelectedStudents.isEmpty()) {
                                Toast.makeText(context, "কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
                            } else {
                                CertificateNativePdfUtil.printCertificatesDirectly(
                                    context = context,
                                    state = state,
                                    students = finalSelectedStudents,
                                    bengaliFont = activeBengaliFont
                                )
                            }
                        },
                        modifier = Modifier.testTag("cert_quick_print_btn")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = "Print", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Quick Share PDF
                    IconButton(
                        onClick = {
                            if (finalSelectedStudents.isEmpty()) {
                                Toast.makeText(context, "কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
                            } else {
                                CertificateNativePdfUtil.exportAndSharePdf(
                                    context = context,
                                    state = state,
                                    students = finalSelectedStudents,
                                    bengaliFont = activeBengaliFont
                                )
                            }
                        },
                        modifier = Modifier.testTag("cert_quick_share_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Selected Count & Preview switch
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${BanglaUtils.toBanglaDigits(finalSelectedStudents.size)} জন শিক্ষার্থী নির্বাচিত",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সাইজ: ${state.pageSize} ল্যান্ডস্কেপ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (activeTab != 2) {
                        Button(
                            onClick = { activeTab = 2 },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("cert_preview_nav_btn")
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রিভিউ দেখুন")
                        }
                    } else {
                        Button(
                            onClick = {
                                CertificateNativePdfUtil.printCertificatesDirectly(
                                    context = context,
                                    state = state,
                                    students = finalSelectedStudents,
                                    bengaliFont = activeBengaliFont
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("cert_bottom_print_btn")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রিন্ট করুন")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 3 Clean Tabs
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("১. শিক্ষার্থী নির্বাচন", fontSize = 13.sp, fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("২. তথ্য ও ফরম্যাট", fontSize = 13.sp, fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("৩. প্রিভিউ ও প্রিন্ট", fontSize = 13.sp, fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Main Tab Content
            when (activeTab) {
                0 -> StudentSelectionTab(
                    allStudents = displayedStudents,
                    availableClasses = availableClasses,
                    selectedClassFilter = selectedClassFilter,
                    onSelectClassFilter = { selectedClassFilter = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedIds = selectedStudentIdSet,
                    onToggleStudent = { id ->
                        selectedStudentIdSet = if (selectedStudentIdSet.contains(id)) {
                            selectedStudentIdSet - id
                        } else {
                            selectedStudentIdSet + id
                        }
                        state = state.copy(selectedStudentIds = selectedStudentIdSet.toList())
                        CertificateStorage.saveState(context, state)
                    },
                    onSelectAll = {
                        val allIds = displayedStudents.map { it.id }.toSet()
                        selectedStudentIdSet = selectedStudentIdSet + allIds
                        state = state.copy(selectedStudentIds = selectedStudentIdSet.toList())
                        CertificateStorage.saveState(context, state)
                    },
                    onDeselectAll = {
                        val currentIds = displayedStudents.map { it.id }.toSet()
                        selectedStudentIdSet = selectedStudentIdSet - currentIds
                        state = state.copy(selectedStudentIds = selectedStudentIdSet.toList())
                        CertificateStorage.saveState(context, state)
                    },
                    state = state,
                    onUpdateCustomSerial = { studentId, customSerial ->
                        val newMap = state.manualSerialMap.toMutableMap()
                        if (customSerial.isBlank()) {
                            newMap.remove(studentId)
                        } else {
                            newMap[studentId] = customSerial
                        }
                        state = state.copy(manualSerialMap = newMap)
                        CertificateStorage.saveState(context, state)
                    }
                )
                1 -> CertificateSettingsTab(
                    state = state,
                    onStateChange = { newState ->
                        state = newState
                        CertificateStorage.saveState(context, state)
                    },
                    onOpenDatePicker = { showIssueDatePicker = true }
                )
                2 -> LivePreviewAndPrintTab(
                    state = state,
                    selectedStudents = finalSelectedStudents,
                    previewIndex = previewStudentIndex,
                    onPreviewIndexChange = { previewStudentIndex = it },
                    previewBitmap = previewBitmap,
                    isRendering = isRenderingPreview,
                    activeBengaliFont = activeBengaliFont,
                    onStateChange = { newState ->
                        state = newState
                        CertificateStorage.saveState(context, state)
                    },
                    onPrint = {
                        CertificateNativePdfUtil.printCertificatesDirectly(
                            context = context,
                            state = state,
                            students = finalSelectedStudents,
                            bengaliFont = activeBengaliFont
                        )
                    },
                    onSharePdf = {
                        CertificateNativePdfUtil.exportAndSharePdf(
                            context = context,
                            state = state,
                            students = finalSelectedStudents,
                            bengaliFont = activeBengaliFont
                        )
                    }
                )
            }
        }
    }

    // Date Picker Dialog
    if (showIssueDatePicker) {
        AppDatePickerDialog(
            onDateSelected = { selectedDateIso ->
                state = state.copy(issueDate = selectedDateIso)
                CertificateStorage.saveState(context, state)
                showIssueDatePicker = false
            },
            onDismiss = { showIssueDatePicker = false }
        )
    }
}

/**
 * Tab 1: Student Selection & Multi-Filter Search
 */
@Composable
private fun StudentSelectionTab(
    allStudents: List<CertificateStudent>,
    availableClasses: List<String>,
    selectedClassFilter: String?,
    onSelectClassFilter: (String?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedIds: Set<String>,
    onToggleStudent: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    state: CertificateMakerState,
    onUpdateCustomSerial: (String, String) -> Unit
) {
    var editingStudentForSerial by remember { mutableStateOf<CertificateStudent?>(null) }
    var tempCustomSerialInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cert_student_search_input"),
                placeholder = { Text("শিক্ষার্থীর নাম, রোল বা পিতার নাম দিয়ে খুঁজুন...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Class Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "শ্রেণি অনুযায়ী ফিল্টার:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedClassFilter == null,
                            onClick = { onSelectClassFilter(null) },
                            label = { Text("সকল শ্রেণি (${allStudents.size})") },
                            leadingIcon = if (selectedClassFilter == null) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                    items(availableClasses) { cls ->
                        FilterChip(
                            selected = selectedClassFilter == cls,
                            onClick = {
                                onSelectClassFilter(if (selectedClassFilter == cls) null else cls)
                            },
                            label = { Text(cls) },
                            leadingIcon = if (selectedClassFilter == cls) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // Select All / Deselect All Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSelectAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সবাইকে সিলেক্ট", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onDeselectAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Deselect, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সব বাতিল", fontSize = 12.sp)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${BanglaUtils.toBanglaDigits(selectedIds.size)} জন সিলেক্টেড",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Student Cards
        if (allStudents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("কোনো শিক্ষার্থী পাওয়া যায়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(allStudents) { index, student ->
                val isSelected = selectedIds.contains(student.id)
                val computedSerial = state.computeSerialForStudent(student, index)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleStudent(student.id) }
                        .testTag("cert_student_card_${student.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleStudent(student.id) }
                        )

                        // Student Info
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = student.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "শ্রেণি: ${student.studentClass} • পিতা: ${student.fatherName.ifBlank { "—" }}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Computed Serial Badge with Quick Edit
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = Color(0xFFE0F2FE),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "সিরিয়াল: $computedSerial",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        editingStudentForSerial = student
                                        tempCustomSerialInput = state.manualSerialMap[student.id] ?: ""
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Edit serial",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Individual Serial Edit Dialog
    if (editingStudentForSerial != null) {
        val student = editingStudentForSerial!!
        AlertDialog(
            onDismissRequest = { editingStudentForSerial = null },
            title = { Text("সিরিয়াল নম্বর কাস্টমাইজ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "শিক্ষার্থী: ${student.name} (${student.studentClass}, রোল: ${student.rollNumber})",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = tempCustomSerialInput,
                        onValueChange = { tempCustomSerialInput = it },
                        label = { Text("কাস্টম সিরিয়াল নম্বর") },
                        placeholder = { Text("যেমন: ২০২৬০৫১২") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "খালি রাখলে স্বয়ংক্রিয় ফরম্যাট (${state.computeSerialForStudent(student)}) প্রযোজ্য হবে।",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateCustomSerial(student.id, tempCustomSerialInput.trim())
                    editingStudentForSerial = null
                }) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudentForSerial = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

/**
 * Tab 2: Certificate Settings, Serial Format & School Information
 */
@Composable
private fun CertificateSettingsTab(
    state: CertificateMakerState,
    onStateChange: (CertificateMakerState) -> Unit,
    onOpenDatePicker: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: School Information
        item {
            SettingsCard(title = "বিদ্যালয়ের প্রাতিষ্ঠানিক তথ্য", icon = Icons.Filled.School) {
                OutlinedTextField(
                    value = state.schoolName,
                    onValueChange = { onStateChange(state.copy(schoolName = it)) },
                    label = { Text("বিদ্যালয়ের পূর্ণ নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.upazila,
                        onValueChange = { onStateChange(state.copy(upazila = it)) },
                        label = { Text("উপজেলা") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.district,
                        onValueChange = { onStateChange(state.copy(district = it)) },
                        label = { Text("জেলা") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.estYear,
                        onValueChange = { onStateChange(state.copy(estYear = it)) },
                        label = { Text("স্থাপিত সাল") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.sessionYear,
                        onValueChange = { onStateChange(state.copy(sessionYear = it)) },
                        label = { Text("শিক্ষাবর্ষ (Session)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 2: Certificate Titles & Issue Date
        item {
            SettingsCard(title = "সনদের শিরোনাম ও তারিখ", icon = Icons.Filled.WorkspacePremium) {
                OutlinedTextField(
                    value = state.certificateTitle,
                    onValueChange = { onStateChange(state.copy(certificateTitle = it)) },
                    label = { Text("সনদের শিরোনাম (Title)") },
                    placeholder = { Text("প্রত্যয়নপত্র / প্রশংসাপত্র / চারিত্রিক সনদপত্র") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Title Presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("প্রত্যয়নপত্র", "প্রশংসাপত্র", "চারিত্রিক সনদপত্র").forEach { preset ->
                        FilterChip(
                            selected = state.certificateTitle == preset,
                            onClick = { onStateChange(state.copy(certificateTitle = preset)) },
                            label = { Text(preset, fontSize = 12.sp) }
                        )
                    }
                }

                // Issue Date Picker Button
                OutlinedCard(
                    onClick = onOpenDatePicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ইস্যু তারিখ (Issue Date):",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (state.issueDate.isNotBlank()) {
                                    CertificateStorage.formatDateToBanglaDisplay(state.issueDate)
                                } else {
                                    CertificateStorage.formatDateToBanglaDisplay(CertificateStorage.getCurrentIsoDate())
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Section 3: Serial Number Generator Formats
        item {
            SettingsCard(title = "সিরিয়াল নম্বর তৈরি ফরম্যাট (Serial Generator)", icon = Icons.Filled.FormatListNumbered) {
                Text(
                    text = "সিরিয়াল নম্বর ফরম্যাট নির্ধারণ করুন:",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Mode 1: YEAR_CLASS_ROLL (যেমন: ২০২৬০৫১২)
                SerialOptionRadio(
                    selected = state.serialFormatMode == "YEAR_CLASS_ROLL",
                    title = "সাল + শ্রেণি কোড + রোল (e.g. ২০২৬০৫১২)",
                    subtitle = "সাল (২০২৬) + শ্রেণি (০৫ ৫ম) + রোল (১২) = ২০২৬০৫১২",
                    onSelect = { onStateChange(state.copy(serialFormatMode = "YEAR_CLASS_ROLL")) }
                )

                // Mode 2: CUSTOM_PREFIX_ROLL (e.g. PR-2026-0012)
                SerialOptionRadio(
                    selected = state.serialFormatMode == "CUSTOM_PREFIX_ROLL",
                    title = "কাস্টম প্রিফিক্স + রোল (e.g. PR-2026-0012)",
                    subtitle = "নির্দিষ্ট প্রিফিক্স দিয়ে রোল নম্বর ফরম্যাট করা",
                    onSelect = { onStateChange(state.copy(serialFormatMode = "CUSTOM_PREFIX_ROLL")) }
                )

                if (state.serialFormatMode == "CUSTOM_PREFIX_ROLL") {
                    OutlinedTextField(
                        value = state.customSerialPrefix,
                        onValueChange = { onStateChange(state.copy(customSerialPrefix = it)) },
                        label = { Text("কাস্টম প্রিফিক্স") },
                        placeholder = { Text("যেমন: PR-2026-") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Mode 3: AUTO_INCREMENT (e.g. 001, 002)
                SerialOptionRadio(
                    selected = state.serialFormatMode == "AUTO_INCREMENT",
                    title = "ক্রমিক সংখ্যা অটো-ইনক্রিমেন্ট (১, ২, ৩... / ০০১, ০০২)",
                    subtitle = "ক্রমানুসারে স্বয়ংক্রিয় সিরিয়াল তৈরি",
                    onSelect = { onStateChange(state.copy(serialFormatMode = "AUTO_INCREMENT")) }
                )
            }
        }

        // Section 4: Body Text & Tense Customization
        item {
            SettingsCard(title = "প্রত্যয়ন বিবরণী ও ভাষা", icon = Icons.Filled.Description) {
                Text(
                    text = "অধ্যয়নকাল বা পাসের বিবরণ:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = state.studyTense == "PAST",
                        onClick = { onStateChange(state.copy(studyTense = "PAST")) },
                        label = { Text("অধ্যয়ন করেছে (পাসকৃত/সাবেক)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.studyTense == "PRESENT",
                        onClick = { onStateChange(state.copy(studyTense = "PRESENT")) },
                        label = { Text("অধ্যয়ন করছে (বর্তমান)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = state.characterRemark,
                    onValueChange = { onStateChange(state.copy(characterRemark = it)) },
                    label = { Text("স্বভাব চরিত্র সংক্রান্ত মন্তব্য") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.wishRemark,
                    onValueChange = { onStateChange(state.copy(wishRemark = it)) },
                    label = { Text("শুভকামনা বা শেষ বাক্য") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Section 5: Page Size, Margins & Layout
        item {
            SettingsCard(title = "কাগজের সাইজ ও মার্জিন (Layout & Margins)", icon = Icons.Filled.AspectRatio) {
                // Page Size Selector
                Text(
                    text = "কাগজের সাইজ (Paper Size):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Legal" to "লিগ্যাল (ডিফল্ট)", "A4" to "A4 সাইজ", "Letter" to "লেটার").forEach { (sizeKey, label) ->
                        FilterChip(
                            selected = state.pageSize == sizeKey,
                            onClick = { onStateChange(state.copy(pageSize = sizeKey)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // Margins
                Text(
                    text = "মার্জিন (ইঞ্চি):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.marginLeftInch.toString(),
                        onValueChange = {
                            val v = it.toFloatOrNull() ?: state.marginLeftInch
                            onStateChange(state.copy(marginLeftInch = v, marginRightInch = v))
                        },
                        label = { Text("ডান-বাম") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.marginTopInch.toString(),
                        onValueChange = {
                            val v = it.toFloatOrNull() ?: state.marginTopInch
                            onStateChange(state.copy(marginTopInch = v, marginBottomInch = v))
                        },
                        label = { Text("উপর-নিচ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Toggles for Elements
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SwitchRow(
                    label = "বাম পাশের অফিস কপি (কাউন্টার ফয়েল)",
                    checked = state.showCounterfoil,
                    onCheckedChange = { onStateChange(state.copy(showCounterfoil = it)) }
                )
                SwitchRow(
                    label = "সরকারি মনোগ্রাম ও শিক্ষা লোগো",
                    checked = state.showGovtEmblems,
                    onCheckedChange = { onStateChange(state.copy(showGovtEmblems = it)) }
                )
                SwitchRow(
                    label = "ব্যাকগ্রাউন্ড জলছাপ (Watermark)",
                    checked = state.showWatermark,
                    onCheckedChange = { onStateChange(state.copy(showWatermark = it)) }
                )
            }
        }
    }
}

/**
 * Tab 3: Interactive Live Preview & Print Pager
 */
@Composable
private fun LivePreviewAndPrintTab(
    state: CertificateMakerState,
    selectedStudents: List<CertificateStudent>,
    previewIndex: Int,
    onPreviewIndexChange: (Int) -> Unit,
    previewBitmap: Bitmap?,
    isRendering: Boolean,
    activeBengaliFont: AppBengaliFont,
    onStateChange: (CertificateMakerState) -> Unit,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit
) {
    if (selectedStudents.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.GroupOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Text("কোনো শিক্ষার্থী নির্বাচিত নেই। ১ম ট্যাবে গিয়ে শিক্ষার্থী সিলেক্ট করুন।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val curIdx = previewIndex.coerceIn(0, selectedStudents.size - 1)
    val curStudent = selectedStudents[curIdx]
    val computedSerial = state.computeSerialForStudent(curStudent, curIdx)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Student Navigation Pager
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { if (curIdx > 0) onPreviewIndexChange(curIdx - 1) },
                    enabled = curIdx > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${curStudent.name} (${curStudent.studentClass})",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "রোল: ${BanglaUtils.toBanglaDigits(curStudent.rollNumber)} • সিরিয়াল: $computedSerial • (${BanglaUtils.toBanglaDigits(curIdx + 1)}/${BanglaUtils.toBanglaDigits(selectedStudents.size)})",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { if (curIdx < selectedStudents.size - 1) onPreviewIndexChange(curIdx + 1) },
                    enabled = curIdx < selectedStudents.size - 1
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Next")
                }
            }
        }

        // Live High-Res Bitmap Canvas Preview
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRendering || previewBitmap == null) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Certificate Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .shadow(4.dp)
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSharePdf,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PDF শেয়ার")
            }

            Button(
                onClick = onPrint,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("প্রিন্ট করুন (${BanglaUtils.toBanglaDigits(selectedStudents.size)}টি)")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            content()
        }
    }
}

@Composable
private fun SerialOptionRadio(
    selected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(text = title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
            Text(text = subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
