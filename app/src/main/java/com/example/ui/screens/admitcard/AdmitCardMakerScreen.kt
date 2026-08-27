package com.example.ui.screens.admitcard

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.SchoolInfoEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.model.*
import com.example.util.AdmitCardStorage
import com.example.util.BanglaUtils
import com.example.util.CsvUtils
import com.example.util.PrintUtils
import com.example.viewmodel.MainViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdmitCardMakerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val allDbStudents by viewModel.allStudents.collectAsState()

    // Base default school name
    val defaultSchoolName = remember(schoolInfo) {
        val sName = schoolInfo?.schoolName ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
        val sAddr = schoolInfo?.address ?: "আলফাডাঙ্গা, ফরিদপুর।"
        if (sAddr.isNotBlank() && !sName.contains(sAddr)) "$sName, $sAddr" else sName
    }

    var state by remember {
        mutableStateOf(AdmitCardStorage.loadState(context, defaultSchoolName))
    }

    var activeTab by remember { mutableIntStateOf(0) }
    var newSubjectText by remember { mutableStateOf("") }
    var newClassText by remember { mutableStateOf("") }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddTimePresetDialog by remember { mutableStateOf(false) }
    var newTimePresetText by remember { mutableStateOf("") }
    var studentSearchQuery by remember { mutableStateOf("") }

    // Map db students to AdmitCardStudent
    val currentStudents = remember(allDbStudents, state.scope, state.selectedClasses, state.selectedStudentIds) {
        val source = if (allDbStudents.isNotEmpty()) {
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
            // Demo fallback if DB is completely empty
            listOf(
                AdmitCardStudent("1", "তাওহিদ মোল্যা", "প্রাক-প্রাথমিক", "১"),
                AdmitCardStudent("2", "মো. তামিম শেখ", "প্রাক-প্রাথমিক", "২"),
                AdmitCardStudent("3", "মোসা. মরিয়াম আক্তার", "প্রাক-প্রাথমিক", "৩"),
                AdmitCardStudent("4", "মোঃ তারিফ মাহমুদ", "প্রাক-প্রাথমিক", "৪")
            )
        }

        // Apply Scope Filter
        when (state.scope) {
            "class" -> {
                if (state.selectedClasses.isEmpty()) source
                else source.filter { state.selectedClasses.contains(it.studentClass) }
            }
            "student" -> {
                if (state.selectedStudentIds.isEmpty()) source
                else source.filter { state.selectedStudentIds.contains(it.id) }
            }
            else -> source
        }
    }

    // Auto-update classes list if DB has extra classes
    LaunchedEffect(allDbStudents) {
        if (allDbStudents.isNotEmpty()) {
            val dbClasses = allDbStudents.map { it.studentClass }.distinct().filter { it.isNotBlank() }
            val mergedClasses = (state.classes + dbClasses).distinct()
            if (mergedClasses != state.classes) {
                state = state.copy(classes = mergedClasses)
                AdmitCardStorage.saveState(context, state)
            }
        }
    }

    // Signature file picker launcher
    val signaturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                val base64 = "data:image/png;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                state = state.copy(signature = base64)
                AdmitCardStorage.saveState(context, state)
                Toast.makeText(context, "স্বাক্ষর সফলভাবে যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "স্বাক্ষর লোড করতে ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // CSV/Excel file picker launcher
    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    val content = inputStream.bufferedReader().use { r -> r.readText() }
                    val (headers, rows) = CsvUtils.parseCsvContent(content)
                    val fields = CsvUtils.getFieldsForType(com.example.util.CsvDataType.STUDENTS)
                    val mapping = CsvUtils.autoDetectColumnMapping(headers, fields)
                    val importedStudents = CsvUtils.buildStudentsFromMappedRows(rows, mapping)
                    if (importedStudents.isNotEmpty()) {
                        importedStudents.forEach { s -> viewModel.insertStudent(s) }
                        Toast.makeText(context, "${importedStudents.size} জন শিক্ষার্থী সফলভাবে ইমপোর্ট হয়েছে", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "ফাইল থেকে শিক্ষার্থী পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ইমপোর্ট ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun doPrint() {
        if (state.schoolName.isBlank() || state.examName.isBlank()) {
            Toast.makeText(context, "বিদ্যালয় ও পরীক্ষার নাম পূরণ করুন", Toast.LENGTH_SHORT).show()
            activeTab = 0
            return
        }
        if (currentStudents.isEmpty()) {
            Toast.makeText(context, "নির্বাচিত রেঞ্জে কোনো শিক্ষার্থী নেই", Toast.LENGTH_SHORT).show()
            activeTab = 3
            return
        }
        val html = AdmitCardStorage.generatePrintHtml(state, currentStudents)
        PrintUtils.printHtmlContent(
            context = context,
            documentName = "প্রবেশপত্র_${state.examName}",
            htmlContent = html,
            isLandscape = false
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "স্মার্ট প্রবেশপত্র ও রুটিন মেকার",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "সব ডাটা ডিভাইসে সংরক্ষিত — নিরাপদ ও দ্রুত",
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
                    IconButton(onClick = { doPrint() }) {
                        Icon(
                            Icons.Filled.Print,
                            contentDescription = "Print",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val tabs = listOf(
                    Triple(0, "প্রাথমিক", Icons.Filled.Info),
                    Triple(1, "বিষয় ও রুটিন", Icons.Filled.Calculate),
                    Triple(2, "প্রিন্ট সেটিং", Icons.Filled.Tune),
                    Triple(3, "প্রিভিউ", Icons.Filled.Preview)
                )
                tabs.forEach { (index, title, icon) ->
                    val selected = activeTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = index },
                        icon = { Icon(icon, contentDescription = title) },
                        label = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab == 3) {
                ExtendedFloatingActionButton(
                    onClick = { doPrint() },
                    icon = { Icon(Icons.Filled.Print, contentDescription = null) },
                    text = { Text("🖨️ প্রিন্ট / PDF", fontWeight = FontWeight.Bold) },
                    containerColor = Color(0xFF059669),
                    contentColor = Color.White
                )
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
                0 -> BasicInfoTab(
                    state = state,
                    dbStudentsCount = allDbStudents.size,
                    onStateChange = {
                        state = it
                        AdmitCardStorage.saveState(context, it)
                    },
                    onPickSignature = { signaturePickerLauncher.launch("image/*") },
                    onClearSignature = {
                        state = state.copy(signature = "")
                        AdmitCardStorage.saveState(context, state)
                    },
                    onImportCsv = { importPickerLauncher.launch("*/*") },
                    onLoadDemo = {
                        // Demo student list
                        Toast.makeText(context, "নমুনা ডাটা সক্রিয়", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> SubjectAndRoutineTab(
                    state = state,
                    newSubjectText = newSubjectText,
                    onNewSubjectTextChange = { newSubjectText = it },
                    onAddSubject = {
                        val trimmed = newSubjectText.trim()
                        if (trimmed.isNotBlank() && !state.subjects.contains(trimmed)) {
                            state = state.copy(subjects = state.subjects + trimmed)
                            newSubjectText = ""
                            AdmitCardStorage.saveState(context, state)
                        }
                    },
                    onRemoveSubject = { index ->
                        val updated = state.subjects.toMutableList().apply { removeAt(index) }
                        state = state.copy(subjects = updated)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onSelectRoutineKey = { key ->
                        state = state.copy(activeRoutineKey = key)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onSetTimeForActive = { time ->
                        val key = state.activeRoutineKey
                        val updatedTimes = state.classTimes.toMutableMap().apply { put(key, time) }
                        state = if (key == AdmitCardStorage.BASE_KEY) {
                            state.copy(defaultTime = time, classTimes = updatedTimes)
                        } else {
                            state.copy(classTimes = updatedTimes)
                        }
                        AdmitCardStorage.saveState(context, state)
                    },
                    onAddDay = {
                        val key = state.activeRoutineKey
                        val currentDays = state.classRoutines[key] ?: emptyList()
                        val lastDate = currentDays.lastOrNull()?.date ?: ""
                        val nextDate = if (lastDate.isNotBlank()) AdmitCardStorage.addDaysToDate(lastDate, 1) else ""
                        val newDay = RoutineDay(
                            date = nextDate,
                            day = AdmitCardStorage.getDayNameFromDate(nextDate),
                            subjects = listOf("")
                        )
                        val updatedMap = state.classRoutines.toMutableMap().apply {
                            put(key, currentDays + newDay)
                        }
                        state = state.copy(classRoutines = updatedMap)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onRemoveDay = { index ->
                        val key = state.activeRoutineKey
                        val currentDays = (state.classRoutines[key] ?: emptyList()).toMutableList()
                        if (index in currentDays.indices) {
                            currentDays.removeAt(index)
                            val updatedMap = state.classRoutines.toMutableMap().apply {
                                put(key, currentDays)
                            }
                            state = state.copy(classRoutines = updatedMap)
                            AdmitCardStorage.saveState(context, state)
                        }
                    },
                    onUpdateDayDate = { index, dateStr ->
                        val key = state.activeRoutineKey
                        val currentDays = (state.classRoutines[key] ?: emptyList()).toMutableList()
                        if (index in currentDays.indices) {
                            val old = currentDays[index]
                            currentDays[index] = old.copy(
                                date = dateStr,
                                day = AdmitCardStorage.getDayNameFromDate(dateStr)
                            )
                            val updatedMap = state.classRoutines.toMutableMap().apply {
                                put(key, currentDays)
                            }
                            state = state.copy(classRoutines = updatedMap)
                            AdmitCardStorage.saveState(context, state)
                        }
                    },
                    onUpdateDayName = { index, dayName ->
                        val key = state.activeRoutineKey
                        val currentDays = (state.classRoutines[key] ?: emptyList()).toMutableList()
                        if (index in currentDays.indices) {
                            val old = currentDays[index]
                            currentDays[index] = old.copy(day = dayName)
                            val updatedMap = state.classRoutines.toMutableMap().apply {
                                put(key, currentDays)
                            }
                            state = state.copy(classRoutines = updatedMap)
                            AdmitCardStorage.saveState(context, state)
                        }
                    },
                    onAddSubjectToDay = { dayIndex ->
                        val key = state.activeRoutineKey
                        val currentDays = (state.classRoutines[key] ?: emptyList()).toMutableList()
                        if (dayIndex in currentDays.indices) {
                            val old = currentDays[dayIndex]
                            currentDays[dayIndex] = old.copy(subjects = old.subjects + "")
                            val updatedMap = state.classRoutines.toMutableMap().apply {
                                put(key, currentDays)
                            }
                            state = state.copy(classRoutines = updatedMap)
                            AdmitCardStorage.saveState(context, state)
                        }
                    },
                    onUpdateSubjectInDay = { dayIndex, subjIndex, subjName ->
                        val key = state.activeRoutineKey
                        val currentDays = (state.classRoutines[key] ?: emptyList()).toMutableList()
                        if (dayIndex in currentDays.indices) {
                            val old = currentDays[dayIndex]
                            val subList = old.subjects.toMutableList()
                            if (subjIndex in subList.indices) {
                                subList[subjIndex] = subjName
                                currentDays[dayIndex] = old.copy(subjects = subList)
                                val updatedMap = state.classRoutines.toMutableMap().apply {
                                    put(key, currentDays)
                                }
                                state = state.copy(classRoutines = updatedMap)
                                AdmitCardStorage.saveState(context, state)
                            }
                        }
                    },
                    onRemoveSubjectFromDay = { dayIndex, subjIndex ->
                        val key = state.activeRoutineKey
                        val currentDays = (state.classRoutines[key] ?: emptyList()).toMutableList()
                        if (dayIndex in currentDays.indices) {
                            val old = currentDays[dayIndex]
                            val subList = old.subjects.toMutableList()
                            if (subList.size <= 1) {
                                subList[0] = ""
                            } else if (subjIndex in subList.indices) {
                                subList.removeAt(subjIndex)
                            }
                            currentDays[dayIndex] = old.copy(subjects = subList)
                            val updatedMap = state.classRoutines.toMutableMap().apply {
                                put(key, currentDays)
                            }
                            state = state.copy(classRoutines = updatedMap)
                            AdmitCardStorage.saveState(context, state)
                        }
                    },
                    onCopyBaseToAll = {
                        val baseRoutine = state.classRoutines[AdmitCardStorage.BASE_KEY] ?: emptyList()
                        if (baseRoutine.isEmpty()) {
                            Toast.makeText(context, "আগে মূল বেস রুটিনে দিন যোগ করুন!", Toast.LENGTH_SHORT).show()
                        } else {
                            val baseTime = state.classTimes[AdmitCardStorage.BASE_KEY] ?: state.defaultTime
                            val updatedRoutines = state.classRoutines.toMutableMap()
                            val updatedTimes = state.classTimes.toMutableMap()
                            state.classes.forEach { c ->
                                updatedRoutines[c] = baseRoutine.map { it.copy(id = UUID.randomUUID().toString()) }
                                updatedTimes[c] = baseTime
                            }
                            state = state.copy(classRoutines = updatedRoutines, classTimes = updatedTimes)
                            AdmitCardStorage.saveState(context, state)
                            Toast.makeText(context, "বেস রুটিন ও সময় সকল শ্রেণিতে সফলভাবে কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShowAddClassDialog = { showAddClassDialog = true },
                    onShowAddTimePresetDialog = { showAddTimePresetDialog = true }
                )
                2 -> PrintSettingsTab(
                    settings = state.settings,
                    onSettingsChange = { updatedSettings ->
                        state = state.copy(settings = updatedSettings)
                        AdmitCardStorage.saveState(context, state)
                    }
                )
                3 -> PreviewAndExportTab(
                    state = state,
                    allStudents = allDbStudents,
                    selectedStudents = currentStudents,
                    studentSearchQuery = studentSearchQuery,
                    onStudentSearchQueryChange = { studentSearchQuery = it },
                    onScopeChange = { scope ->
                        state = state.copy(scope = scope)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onToggleClass = { className ->
                        val current = state.selectedClasses.toMutableList()
                        if (current.contains(className)) current.remove(className) else current.add(className)
                        state = state.copy(selectedClasses = current)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onToggleAllClasses = {
                        val allClasses = (state.classes + allDbStudents.map { it.studentClass }).distinct()
                        val updated = if (state.selectedClasses.size == allClasses.size) emptyList() else allClasses
                        state = state.copy(selectedClasses = updated)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onToggleStudent = { studentId ->
                        val current = state.selectedStudentIds.toMutableList()
                        if (current.contains(studentId)) current.remove(studentId) else current.add(studentId)
                        state = state.copy(selectedStudentIds = current)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onToggleAllStudents = {
                        val allIds = allDbStudents.map { it.id }
                        val updated = if (state.selectedStudentIds.size == allIds.size) emptyList() else allIds
                        state = state.copy(selectedStudentIds = updated)
                        AdmitCardStorage.saveState(context, state)
                    },
                    onPrint = { doPrint() }
                )
            }
        }
    }

    // Add Class Dialog
    if (showAddClassDialog) {
        AlertDialog(
            onDismissRequest = { showAddClassDialog = false },
            title = { Text("নতুন শ্রেণি যোগ করুন") },
            text = {
                OutlinedTextField(
                    value = newClassText,
                    onValueChange = { newClassText = it },
                    label = { Text("শ্রেণির নাম (যেমন: ষষ্ঠ শ্রেণি)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newClassText.trim()
                        if (trimmed.isNotBlank() && !state.classes.contains(trimmed)) {
                            state = state.copy(classes = state.classes + trimmed, activeRoutineKey = trimmed)
                            AdmitCardStorage.saveState(context, state)
                            newClassText = ""
                            showAddClassDialog = false
                            Toast.makeText(context, "শ্রেণি যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("যোগ করুন") }
            },
            dismissButton = {
                TextButton(onClick = { showAddClassDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Add Time Preset Dialog
    if (showAddTimePresetDialog) {
        AlertDialog(
            onDismissRequest = { showAddTimePresetDialog = false },
            title = { Text("নতুন পরীক্ষার সময় যোগ করুন") },
            text = {
                OutlinedTextField(
                    value = newTimePresetText,
                    onValueChange = { newTimePresetText = it },
                    label = { Text("সময় ফরম্যাট (যেমন: ০৯:০০-১১:৩০)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTimePresetText.trim()
                        if (trimmed.isNotBlank() && !state.timePresets.contains(trimmed)) {
                            state = state.copy(timePresets = state.timePresets + trimmed)
                            AdmitCardStorage.saveState(context, state)
                            newTimePresetText = ""
                            showAddTimePresetDialog = false
                            Toast.makeText(context, "সময় প্রিসেট যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("যোগ করুন") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTimePresetDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

/**
 * Tab 1: প্রাথমিক তথ্য ও শিক্ষার্থী ডেটা
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicInfoTab(
    state: AdmitCardMakerState,
    dbStudentsCount: Int,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onPickSignature: () -> Unit,
    onClearSignature: () -> Unit,
    onImportCsv: () -> Unit,
    onLoadDemo: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: School & Exam Details
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("১", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "প্রাথমিক তথ্য",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = state.schoolName,
                        onValueChange = { onStateChange(state.copy(schoolName = it)) },
                        label = { Text("বিদ্যালয়ের নাম ও ঠিকানা") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.examName,
                        onValueChange = { onStateChange(state.copy(examName = it)) },
                        label = { Text("পরীক্ষার নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Signature & Size
                    Text(
                        text = "প্রধান শিক্ষকের স্বাক্ষর ও সাইজ",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPickSignature,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.signature.isNotBlank()) "স্বাক্ষর পরিবর্তন" else "স্বাক্ষর আপলোড")
                        }

                        if (state.signature.isNotBlank()) {
                            OutlinedButton(onClick = onClearSignature) {
                                Text("মুছুন", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (state.signature.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("স্বাক্ষর সংযুক্ত আছে ✓", color = Color(0xFF00695C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Signature Size Selector
                    Text("স্বাক্ষর সাইজ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    val sigSizes = listOf(
                        "1" to "১ (খুব ছোট)",
                        "2" to "২ (ছোট)",
                        "3" to "৩ (মাঝারি)",
                        "4" to "৪ (বড়)",
                        "5" to "৫ (খুব বড়)"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sigSizes.forEach { (key, label) ->
                            FilterChip(
                                selected = state.settings.sigSize == key,
                                onClick = { onStateChange(state.copy(settings = state.settings.copy(sigSize = key))) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Student Data Connection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("২", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "শিক্ষার্থী ডেটা সংযোগ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "কলাম: Name, Class, Roll — অ্যাপের মূল ডাটাবেস থেকে সকল শিক্ষার্থী স্বয়ংক্রিয়ভাবে সিঙ্ক করা হয়েছে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "মূল ডাটাবেস থেকে ${BanglaUtils.toBanglaDigits(dbStudentsCount)} জন শিক্ষার্থী যুক্ত রয়েছে।",
                                color = Color(0xFF1B5E20),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportCsv,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel / CSV", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onLoadDemo,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("নমুনা ডাটা", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: বিষয় ও রুটিন মেকার
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectAndRoutineTab(
    state: AdmitCardMakerState,
    newSubjectText: String,
    onNewSubjectTextChange: (String) -> Unit,
    onAddSubject: () -> Unit,
    onRemoveSubject: (Int) -> Unit,
    onSelectRoutineKey: (String) -> Unit,
    onSetTimeForActive: (String) -> Unit,
    onAddDay: () -> Unit,
    onRemoveDay: (Int) -> Unit,
    onUpdateDayDate: (Int, String) -> Unit,
    onUpdateDayName: (Int, String) -> Unit,
    onAddSubjectToDay: (Int) -> Unit,
    onUpdateSubjectInDay: (Int, Int, String) -> Unit,
    onRemoveSubjectFromDay: (Int, Int) -> Unit,
    onCopyBaseToAll: () -> Unit,
    onShowAddClassDialog: () -> Unit,
    onShowAddTimePresetDialog: () -> Unit
) {
    val context = LocalContext.current
    val activeKey = state.activeRoutineKey
    val currentDays = state.classRoutines[activeKey] ?: emptyList()
    val activeTime = state.classTimes[activeKey]
        ?: (if (activeKey == AdmitCardStorage.BASE_KEY) state.defaultTime else state.classTimes[AdmitCardStorage.BASE_KEY] ?: state.defaultTime)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Subjects Suggestions Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("১", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "বিষয় ইনপুট ও সাজেশন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "এখানে যোগ করা বিষয়গুলো রুটিন তৈরির সময় অটো-সাজেশন হিসেবে দেখাবে।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubjectText,
                            onValueChange = onNewSubjectTextChange,
                            placeholder = { Text("নতুন বিষয় (যেমন: চারু ও কারুকলা)...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = onAddSubject) {
                            Text("+ বিষয়")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.subjects.forEachIndexed { index, subj ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(subj, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRemoveSubject(index) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Routine Management Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("২", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "রুটিন ম্যানেজমেন্ট (বেস ও শ্রেণিভিত্তিক)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Class selection tabs
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val routineKeys = listOf(AdmitCardStorage.BASE_KEY) + state.classes
                        items(routineKeys) { key ->
                            val isSelected = activeKey == key
                            val label = if (key == AdmitCardStorage.BASE_KEY) "★ মূল বেস রুটিন" else key
                            val hasCustom = key != AdmitCardStorage.BASE_KEY && (state.classRoutines[key]?.isNotEmpty() == true)

                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectRoutineKey(key) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (hasCustom) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF22C55E))
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = onShowAddClassDialog,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("+ শ্রেণি", fontSize = 11.sp)
                            }
                        }
                    }

                    // Status banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE0F2FE),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activeKey == AdmitCardStorage.BASE_KEY)
                                "মূল বেস রুটিন ও সময় সম্পাদনা চলছে — সব শ্রেণিতে এক ক্লিকে কপি করা যাবে।"
                            else
                                "「$activeKey」 শ্রেণির রুটিন ও পরীক্ষার সময় সম্পাদনা চলছে।",
                            color = Color(0xFF0369A1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    // Exam Time row & presets
                    Text("পরীক্ষার সময় (Time) — নির্বাচিত শ্রেণির জন্য", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = activeTime,
                        onValueChange = onSetTimeForActive,
                        placeholder = { Text("যেমন: ১০:০০-১১:০০") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.timePresets.forEach { preset ->
                            SuggestionChip(
                                onClick = { onSetTimeForActive(preset) },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                        SuggestionChip(
                            onClick = onShowAddTimePresetDialog,
                            label = { Text("+ নতুন সময়", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Days routine list
                    Text("পরীক্ষার দিন ও বিষয় তালিকা", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    if (currentDays.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "কোনো পরীক্ষার দিন যুক্ত করা হয়নি — নিচে «নতুন দিন যোগ» বাটনে চাপুন।",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        currentDays.forEachIndexed { dayIdx, routineDay ->
                            RoutineDayCard(
                                index = dayIdx,
                                routineDay = routineDay,
                                availableSubjects = state.subjects,
                                onUpdateDate = { onUpdateDayDate(dayIdx, it) },
                                onUpdateDayName = { onUpdateDayName(dayIdx, it) },
                                onAddSubject = { onAddSubjectToDay(dayIdx) },
                                onUpdateSubject = { sIdx, name -> onUpdateSubjectInDay(dayIdx, sIdx, name) },
                                onRemoveSubject = { sIdx -> onRemoveSubjectFromDay(dayIdx, sIdx) },
                                onRemoveDay = { onRemoveDay(dayIdx) }
                            )
                        }
                    }

                    // Routine Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddDay,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("নতুন দিন যোগ", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onCopyBaseToAll,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বেস রুটিন সব শ্রেণিতে কপি", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Compact Routine Day Row Card
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineDayCard(
    index: Int,
    routineDay: RoutineDay,
    availableSubjects: List<String>,
    onUpdateDate: (String) -> Unit,
    onUpdateDayName: (String) -> Unit,
    onAddSubject: () -> Unit,
    onUpdateSubject: (Int, String) -> Unit,
    onRemoveSubject: (Int) -> Unit,
    onRemoveDay: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date picker trigger button / text field
                OutlinedTextField(
                    value = routineDay.date,
                    onValueChange = onUpdateDate,
                    label = { Text("তারিখ (YYYY-MM-DD)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1.3f),
                    trailingIcon = {
                        IconButton(onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val formatted = String.format("%04d-%02d-%02d", y, m + 1, d)
                                    onUpdateDate(formatted)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = "Pick Date", modifier = Modifier.size(16.dp))
                        }
                    }
                )

                // Day name (Auto-calculated, editable)
                OutlinedTextField(
                    value = routineDay.day,
                    onValueChange = onUpdateDayName,
                    label = { Text("বার (Auto)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )

                IconButton(
                    onClick = onRemoveDay,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Day", modifier = Modifier.size(20.dp))
                }
            }

            // Subjects list in this day
            Text("বিষয়(সমূহ):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            routineDay.subjects.forEachIndexed { subjIdx, subjectValue ->
                var expandedSuggestions by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = subjectValue,
                            onValueChange = { onUpdateSubject(subjIdx, it) },
                            placeholder = { Text("বিষয় লিখুন বা সাজেশন থেকে নিন", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedSuggestions = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Suggestions")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = expandedSuggestions,
                            onDismissRequest = { expandedSuggestions = false }
                        ) {
                            availableSubjects.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        onUpdateSubject(subjIdx, opt)
                                        expandedSuggestions = false
                                    }
                                )
                            }
                        }
                    }

                    if (routineDay.subjects.size > 1) {
                        IconButton(
                            onClick = { onRemoveSubject(subjIdx) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove Subject", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onAddSubject,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ আরও বিষয়", fontSize = 10.sp)
            }
        }
    }
}

/**
 * Tab 3: প্রিন্ট সেটিং
 */
@Composable
private fun PrintSettingsTab(
    settings: AdmitCardSettings,
    onSettingsChange: (AdmitCardSettings) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("৩", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "প্রিন্ট ও পেজ লেআউট সেটিংস",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Page Size
                    Text("কাগজের সাইজ (Page Size)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("A4", "Letter", "Legal").forEach { size ->
                            FilterChip(
                                selected = settings.pageSize == size,
                                onClick = { onSettingsChange(settings.copy(pageSize = size)) },
                                label = { Text(size) }
                            )
                        }
                    }

                    // Cards Per Page
                    Text("প্রতি পৃষ্ঠায় কার্ড সংখ্যা (Cards Per Page)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 4, 5, 6, 8).forEach { num ->
                            FilterChip(
                                selected = settings.cardsPerPage == num,
                                onClick = { onSettingsChange(settings.copy(cardsPerPage = num)) },
                                label = { Text(BanglaUtils.toBanglaDigits(num)) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Margins in inches
                    Text("মার্জিন ও ব্যবধান (ইঞ্চি হিসেবে)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.marginTop.toString(),
                            onValueChange = { onSettingsChange(settings.copy(marginTop = it.toFloatOrNull() ?: 0.25f)) },
                            label = { Text("Top (in)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = settings.marginBottom.toString(),
                            onValueChange = { onSettingsChange(settings.copy(marginBottom = it.toFloatOrNull() ?: 0.25f)) },
                            label = { Text("Bottom (in)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = settings.marginLeft.toString(),
                            onValueChange = { onSettingsChange(settings.copy(marginLeft = it.toFloatOrNull() ?: 0.25f)) },
                            label = { Text("Left (in)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = settings.marginRight.toString(),
                            onValueChange = { onSettingsChange(settings.copy(marginRight = it.toFloatOrNull() ?: 0.25f)) },
                            label = { Text("Right (in)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = settings.vGap.toString(),
                        onValueChange = { onSettingsChange(settings.copy(vGap = it.toFloatOrNull() ?: 0.5f)) },
                        label = { Text("Vertical Gap (মাঝের ফাঁকা স্থান - ইঞ্চি)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Card Border & Font Style
                    Text("কার্ডের বর্ডার ও ফন্ট স্টাইল", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val frameStyles = listOf(
                        "solid" to "রেখা (Solid)",
                        "dashed" to "ড্যাশ (Dashed)",
                        "dotted" to "ডট (Dotted)",
                        "double" to "ডাবল (Double)",
                        "none" to "নেই (None)"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        frameStyles.forEach { (style, label) ->
                            FilterChip(
                                selected = settings.frameStyle == style,
                                onClick = { onSettingsChange(settings.copy(frameStyle = style)) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.cardFont == "serif",
                            onClick = { onSettingsChange(settings.copy(cardFont = "serif")) },
                            label = { Text("সেরিফ (Serif)") }
                        )
                        FilterChip(
                            selected = settings.cardFont == "sans",
                            onClick = { onSettingsChange(settings.copy(cardFont = "sans")) },
                            label = { Text("স্যান্স (Sans)") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 4: এক্সপোর্ট রেঞ্জ ও প্রিভিউ
 */
@Composable
private fun PreviewAndExportTab(
    state: AdmitCardMakerState,
    allStudents: List<StudentEntity>,
    selectedStudents: List<AdmitCardStudent>,
    studentSearchQuery: String,
    onStudentSearchQueryChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onToggleClass: (String) -> Unit,
    onToggleAllClasses: () -> Unit,
    onToggleStudent: (String) -> Unit,
    onToggleAllStudents: () -> Unit,
    onPrint: () -> Unit
) {
    val allClasses = remember(state.classes, allStudents) {
        (state.classes + allStudents.map { it.studentClass }).distinct().filter { it.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scope Filter Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("৪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "এক্সপোর্ট ও প্রিন্ট রেঞ্জ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Scope tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "all" to "সব শ্রেণি",
                            "class" to "একাধিক শ্রেণি",
                            "student" to "একাধিক শিক্ষার্থী"
                        ).forEach { (scopeKey, label) ->
                            FilterChip(
                                selected = state.scope == scopeKey,
                                onClick = { onScopeChange(scopeKey) },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // If scope == "class"
                    if (state.scope == "class") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("শ্রেণি নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    TextButton(onClick = onToggleAllClasses) {
                                        Text("সব / নয়", fontSize = 11.sp)
                                    }
                                }
                                allClasses.forEach { cName ->
                                    val count = allStudents.count { it.studentClass == cName }
                                    val isChecked = state.selectedClasses.contains(cName)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleClass(cName) }
                                    ) {
                                        Checkbox(checked = isChecked, onCheckedChange = { onToggleClass(cName) })
                                        Text("$cName (${BanglaUtils.toBanglaDigits(count)} জন)", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // If scope == "student"
                    if (state.scope == "student") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("শিক্ষার্থী নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    TextButton(onClick = onToggleAllStudents) {
                                        Text("সব / নয়", fontSize = 11.sp)
                                    }
                                }
                                OutlinedTextField(
                                    value = studentSearchQuery,
                                    onValueChange = onStudentSearchQueryChange,
                                    placeholder = { Text("খুঁজুন (নাম, শ্রেণি বা রোল)...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val filteredStudentsList = allStudents.filter {
                                    if (studentSearchQuery.isBlank()) true
                                    else (it.name + " " + it.studentClass + " " + it.rollNumber).contains(studentSearchQuery, ignoreCase = true)
                                }.take(80)

                                filteredStudentsList.forEach { st ->
                                    val isChecked = state.selectedStudentIds.contains(st.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleStudent(st.id) }
                                    ) {
                                        Checkbox(checked = isChecked, onCheckedChange = { onToggleStudent(st.id) })
                                        Column {
                                            Text(st.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text("${st.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(st.rollNumber)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Selection Status
                    val cpp = Math.max(1, state.settings.cardsPerPage)
                    val estPages = (selectedStudents.size + cpp - 1) / cpp
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "নির্বাচিত: ${BanglaUtils.toBanglaDigits(selectedStudents.size)} জন · আনুমানিক ${BanglaUtils.toBanglaDigits(estPages)} পৃষ্ঠা",
                            color = Color(0xFF1E40AF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { /* Preview auto-updates */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("রিফ্রেশ প্রিভিউ", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onPrint,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্রিন্ট / PDF", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Visual Cards Preview Section
        item {
            Text(
                text = "লাইভ কার্ড প্রিভিউ (নমুনা পৃষ্ঠা)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (selectedStudents.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "নির্বাচিত রেঞ্জে কোনো শিক্ষার্থী পাওয়া যায়নি।",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        } else {
            // Render first page sample cards (up to cardsPerPage)
            val sampleCards = selectedStudents.take(state.settings.cardsPerPage)
            items(sampleCards) { student ->
                AdmitCardLiveView(
                    student = student,
                    state = state
                )
            }
        }
    }
}

/**
 * Faithful Live Visual Rendering of an Admit Card
 */
@Composable
private fun AdmitCardLiveView(
    student: AdmitCardStudent,
    state: AdmitCardMakerState
) {
    val routine = state.classRoutines[student.studentClass]?.ifEmpty { null }
        ?: state.classRoutines[AdmitCardStorage.BASE_KEY] ?: emptyList()
    val defTime = state.classTimes[student.studentClass]
        ?: state.classTimes[AdmitCardStorage.BASE_KEY]
        ?: state.defaultTime

    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.5.dp,
            if (state.settings.frameStyle == "none") Color.Transparent else Color(0xFF0F172A)
        ),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Left Column (38% width) - Student & School Info
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.schoolName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = state.examName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "প্রবেশপত্র",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("নাম : ${student.name}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("শ্রেণি : ${student.studentClass}", fontSize = 10.sp)
                        Text("রোল : ${BanglaUtils.toBanglaDigits(student.rollNumber)}", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Signature Line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.signature.isNotBlank()) {
                        Text("✒️ [স্বাক্ষর সংযুক্ত]", fontSize = 8.sp, color = Color(0xFF00695C))
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    HorizontalDivider(color = Color.Black, thickness = 1.dp, modifier = Modifier.fillMaxWidth(0.85f))
                    Text(
                        text = "প্রধান শিক্ষকের স্বাক্ষর",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Dashed Divider between columns
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF94A3B8))
            )

            // Right Column (62% width) - Exam Routine Table
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = "${state.examName}-এর রুটিন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Routine Table Header
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(0.7.dp, Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Text("তারিখ", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                        Text("বার", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                        Text("বিষয় ($defTime)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
                    }
                }

                if (routine.isEmpty()) {
                    Text(
                        text = "রুটিন সেট করা হয়নি",
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                } else {
                    routine.forEach { day ->
                        val subs = day.subjects.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = Color.White,
                            border = BorderStroke(0.5.dp, Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Text(
                                    text = AdmitCardStorage.formatDateToBangla(day.date).ifBlank { "—" },
                                    fontSize = 8.sp,
                                    modifier = Modifier.weight(0.9f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (day.day.isNotBlank()) day.day else AdmitCardStorage.getDayNameFromDate(day.date),
                                    fontSize = 8.sp,
                                    modifier = Modifier.weight(0.8f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = subs,
                                    fontSize = 8.sp,
                                    modifier = Modifier.weight(1.3f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
