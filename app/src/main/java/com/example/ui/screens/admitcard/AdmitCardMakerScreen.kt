package com.example.ui.screens.admitcard

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StudentEntity
import com.example.data.model.*
import com.example.util.AdmitCardNativePdfUtil
import com.example.util.AdmitCardStorage
import com.example.util.BanglaUtils
import com.example.util.CsvUtils
import com.example.viewmodel.MainViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmitCardMakerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val allDbStudents by viewModel.allStudents.collectAsState()

    // Default School Name & Address from app data
    val defaultSchoolName = remember(schoolInfo) {
        schoolInfo?.schoolName?.ifBlank { null } ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
    }
    val defaultSchoolAddress = remember(schoolInfo) {
        schoolInfo?.address?.ifBlank { null } ?: "আলফাডাঙ্গা, ফরিদপুর।"
    }

    var state by remember {
        mutableStateOf(AdmitCardStorage.loadState(context, defaultSchoolName, defaultSchoolAddress))
    }

    // Auto-fill school info from app data if empty
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
                AdmitCardStorage.saveState(context, state)
            }
        }
    }

    // 3 Main Tabs: 0: তথ্য ও শিক্ষার্থী, 1: রুটিন ও বিষয়, 2: প্রিভিউ ও প্রিন্ট
    var activeTab by remember { mutableIntStateOf(0) }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var newClassText by remember { mutableStateOf("") }
    var showAddTimePresetDialog by remember { mutableStateOf(false) }
    var newTimePresetText by remember { mutableStateOf("") }

    // Map DB students to AdmitCardStudent and filter based on scope
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
            listOf(
                AdmitCardStudent("1", "তাওহিদ মোল্যা", "প্রাক-প্রাথমিক", "১"),
                AdmitCardStudent("2", "মো. তামিম শেখ", "প্রাক-প্রাথমিক", "২"),
                AdmitCardStudent("3", "মোসা. মরিয়ম আক্তার", "১ম শ্রেণি", "১"),
                AdmitCardStudent("4", "মোঃ তারিফ মাহমুদ", "১ম শ্রেণি", "২"),
                AdmitCardStudent("5", "ইসরাত জাহান", "২য় শ্রেণি", "৬"),
                AdmitCardStudent("6", "মো: কাওসার মল্লিক", "২য় শ্রেণি", "১১")
            )
        }

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
                Toast.makeText(context, "স্বাক্ষর লোড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // CSV student import launcher
    val csvImportLauncher = rememberLauncherForActivityResult(
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
                Toast.makeText(context, "ইমপোর্ট করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Direct Native Android Print using PrintManager
    val doPrint = {
        AdmitCardNativePdfUtil.printAdmitCardsDirectly(
            context = context,
            state = state,
            students = currentStudents,
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        )
    }

    // Native PDF Export and Share
    val doSharePdf = {
        AdmitCardNativePdfUtil.exportAndSharePdf(
            context = context,
            state = state,
            students = currentStudents
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Column {
                                Text(
                                    text = "প্রবেশপত্র ও রুটিন মেকার",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${BanglaUtils.toBanglaDigits(currentStudents.size)} জন শিক্ষার্থী নির্বাচিত",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Top Quick Action (Native Print Dialog)
                        Button(
                            onClick = doPrint,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্রিন্ট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3 Compact Tabs
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { HorizontalDivider(thickness = 0.8.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
                    ) {
                        val tabs = listOf(
                            Triple(0, "তথ্য ও শিক্ষার্থী", Icons.Filled.Badge),
                            Triple(1, "রুটিন ও বিষয়", Icons.Filled.CalendarMonth),
                            Triple(2, "প্রিভিউ ও প্রিন্ট", Icons.Filled.Visibility)
                        )
                        tabs.forEach { (index, title, icon) ->
                            Tab(
                                selected = activeTab == index,
                                onClick = { activeTab = index },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Text(title, fontSize = 11.5.sp, fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
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
                0 -> InfoAndStudentSelectionTab(
                    state = state,
                    allStudents = allDbStudents,
                    onStateChange = { newState ->
                        state = newState
                        AdmitCardStorage.saveState(context, state)
                    },
                    onAutofillSchoolInfo = {
                        val sName = schoolInfo?.schoolName?.ifBlank { null } ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
                        val sAddr = schoolInfo?.address?.ifBlank { null } ?: "আলফাডাঙ্গা, ফরিদপুর।"
                        state = state.copy(schoolName = sName, schoolAddress = sAddr)
                        AdmitCardStorage.saveState(context, state)
                        Toast.makeText(context, "বিদ্যালয়ের নাম ও ঠিকানা পূরণ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    onPickSignature = { signaturePickerLauncher.launch("image/*") },
                    onClearSignature = {
                        state = state.copy(signature = "")
                        AdmitCardStorage.saveState(context, state)
                    },
                    onImportCsv = { csvImportLauncher.launch("*/*") },
                    onLoadDemo = {
                        val demo = listOf(
                            StudentEntity(id = "1", studentClass = "প্রাক-প্রাথমিক", rollNumber = 1, name = "তাওহিদ মোল্যা", fatherName = "মো. কবির মোল্যা", motherName = "তাহমিনা বেগম"),
                            StudentEntity(id = "2", studentClass = "প্রাক-প্রাথমিক", rollNumber = 2, name = "মো. তামিম শেখ", fatherName = "সবুজ শেখ", motherName = "রীনা বেগম"),
                            StudentEntity(id = "3", studentClass = "১ম শ্রেণি", rollNumber = 1, name = "মোসা. মরিয়ম আক্তার", fatherName = "শাহিন মাতুব্বর", motherName = "মর্জিনা বেগম"),
                            StudentEntity(id = "4", studentClass = "১ম শ্রেণি", rollNumber = 2, name = "মোঃ তারিফ মাহমুদ", fatherName = "তারিক মাহমুদ", motherName = "রুমা পারভীন"),
                            StudentEntity(id = "5", studentClass = "২য় শ্রেণি", rollNumber = 6, name = "ইসরাত জাহান", fatherName = "মো. দেলোয়ার হোসেন", motherName = "নাজমা বেগম"),
                            StudentEntity(id = "6", studentClass = "২য় শ্রেণি", rollNumber = 11, name = "মো: কাওসার মল্লিক", fatherName = "আনোয়ার মল্লিক", motherName = "ফাতেমা বেগম")
                        )
                        demo.forEach { viewModel.insertStudent(it) }
                        Toast.makeText(context, "ডেমো শিক্ষার্থী ডেটা লোড হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                )

                1 -> CompactRoutineEditorTab(
                    state = state,
                    onStateChange = { newState ->
                        state = newState
                        AdmitCardStorage.saveState(context, state)
                    },
                    onCopyBaseToAll = {
                        val baseRoutine = state.classRoutines[AdmitCardStorage.BASE_KEY] ?: emptyList()
                        val baseTime = state.classTimes[AdmitCardStorage.BASE_KEY] ?: state.defaultTime
                        val updatedRoutines = state.classRoutines.toMutableMap()
                        val updatedTimes = state.classTimes.toMutableMap()
                        state.classes.forEach { c ->
                            updatedRoutines[c] = baseRoutine.map { it.copy(subjects = it.subjects.toList()) }
                            updatedTimes[c] = baseTime
                        }
                        state = state.copy(classRoutines = updatedRoutines, classTimes = updatedTimes)
                        AdmitCardStorage.saveState(context, state)
                        Toast.makeText(context, "বেস রুটিন ও সময় সকল শ্রেণিতে কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    onShowAddClassDialog = { showAddClassDialog = true },
                    onShowAddTimePresetDialog = { showAddTimePresetDialog = true }
                )

                2 -> PreviewAndPrintTab(
                    state = state,
                    allStudents = allDbStudents,
                    selectedStudents = currentStudents,
                    onStateChange = { newState ->
                        state = newState
                        AdmitCardStorage.saveState(context, state)
                    },
                    onPrint = doPrint,
                    onSharePdf = doSharePdf
                )
            }
        }
    }

    // Add Class Dialog
    if (showAddClassDialog) {
        AlertDialog(
            onDismissRequest = { showAddClassDialog = false },
            title = { Text("নতুন শ্রেণি যোগ করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newClassText,
                    onValueChange = { newClassText = it },
                    label = { Text("শ্রেণির নাম (যেমন: ৬ষ্ঠ শ্রেণি)") },
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
                            Toast.makeText(context, "শ্রেণি যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
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
            title = { Text("নতুন পরীক্ষার সময় যোগ করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTimePresetText,
                    onValueChange = { newTimePresetText = it },
                    label = { Text("সময় ফরম্যাট (যেমন: ০৯:০০-১১:৩০)") },
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
                            Toast.makeText(context, "সময় প্রিসেট যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
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
 * Tab 1: তথ্য ও শিক্ষার্থী নির্বাচন (Compact, Autofill, Signature preview, Searchable multi-student picker)
 */
@Composable
private fun InfoAndStudentSelectionTab(
    state: AdmitCardMakerState,
    allStudents: List<StudentEntity>,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onAutofillSchoolInfo: () -> Unit,
    onPickSignature: () -> Unit,
    onClearSignature: () -> Unit,
    onImportCsv: () -> Unit,
    onLoadDemo: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterClass by remember { mutableStateOf("সকল") }
    var showAdvanced by remember { mutableStateOf(false) }

    val decodedSignature = remember(state.signature) {
        AdmitCardStorage.decodeBase64ToBitmap(state.signature)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. School Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "প্রাতিষ্ঠানিক ও পরীক্ষার বিবরণ",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = onAutofillSchoolInfo,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("অটোফিল", fontSize = 10.5.sp)
                        }
                    }

                    OutlinedTextField(
                        value = state.schoolName,
                        onValueChange = { onStateChange(state.copy(schoolName = it)) },
                        label = { Text("বিদ্যালয়ের নাম", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.schoolAddress,
                        onValueChange = { onStateChange(state.copy(schoolAddress = it)) },
                        label = { Text("ঠিকানা (যেমন: আলফাডাঙ্গা, ফরিদপুর।)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.examName,
                        onValueChange = { onStateChange(state.copy(examName = it)) },
                        label = { Text("পরীক্ষার নাম", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Exam Name Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val exams = listOf("১ম প্রান্তিক মূল্যায়ন - ২০২৬", "২য় প্রান্তিক মূল্যায়ন - ২০২৬", "বার্ষিক পরীক্ষা - ২০২৬", "প্রাক-নির্বাচনী পরীক্ষা")
                        exams.forEach { e ->
                            SuggestionChip(
                                onClick = { onStateChange(state.copy(examName = e)) },
                                label = { Text(e, fontSize = 9.5.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Headmaster Signature Card with Live Preview and Size Adjustment
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "প্রধান শিক্ষকের স্বাক্ষর ও সাইজ",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Live Signature Render Box
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(width = 110.dp, height = 54.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (decodedSignature != null) {
                                    Image(
                                        bitmap = decodedSignature.asImageBitmap(),
                                        contentDescription = "স্বাক্ষর",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                } else {
                                    Text("স্বাক্ষর নেই", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = onPickSignature,
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(if (state.signature.isNotBlank()) "পরিবর্তন" else "ছবি আপলোড", fontSize = 10.5.sp)
                                }
                                if (state.signature.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = onClearSignature,
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("মুছুন", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            // Signature Size Selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("সাইজ:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                listOf("1" to "ছোট", "2" to "মাঝারি", "3" to "স্ট্যান্ডার্ড", "4" to "বড়").forEach { (sz, label) ->
                                    FilterChip(
                                        selected = state.settings.sigSize == sz,
                                        onClick = {
                                            onStateChange(state.copy(settings = state.settings.copy(sigSize = sz)))
                                        },
                                        label = { Text(label, fontSize = 9.sp) },
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Student & Class Multi-Selector with Live Search Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "শিক্ষার্থী ও শ্রেণি নির্বাচন",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "মোট: ${BanglaUtils.toBanglaDigits(allStudents.size)} জন",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Scope Toggle Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "all" to "সকল শিক্ষার্থী",
                            "class" to "শ্রেণিভিত্তিক নির্বাচন",
                            "student" to "নির্দিষ্ট শিক্ষার্থী"
                        ).forEach { (sc, label) ->
                            FilterChip(
                                selected = state.scope == sc,
                                onClick = { onStateChange(state.copy(scope = sc)) },
                                label = { Text(label, fontSize = 10.5.sp, fontWeight = if (state.scope == sc) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Class Multi-Selection
                    if (state.scope == "class") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("শ্রেণি সিলেক্ট করুন:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { onStateChange(state.copy(selectedClasses = state.classes)) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("সব সিলেক্ট", fontSize = 10.sp)
                                    }
                                    TextButton(
                                        onClick = { onStateChange(state.copy(selectedClasses = emptyList())) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("ক্লিয়ার", fontSize = 10.sp)
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(state.classes) { cName ->
                                    val isSelected = state.selectedClasses.contains(cName)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val current = state.selectedClasses.toMutableList()
                                            if (isSelected) current.remove(cName) else current.add(cName)
                                            onStateChange(state.copy(selectedClasses = current))
                                        },
                                        label = { Text(cName, fontSize = 10.5.sp) },
                                        leadingIcon = {
                                            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Specific Student Selection with Search Bar
                    if (state.scope == "student") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Search Box
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("শিক্ষার্থীর নাম বা রোল খুঁজুন...", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Class Filter Chips for Students
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedFilterClass == "সকল",
                                        onClick = { selectedFilterClass = "সকল" },
                                        label = { Text("সকল", fontSize = 10.sp) }
                                    )
                                }
                                items(state.classes) { cName ->
                                    FilterChip(
                                        selected = selectedFilterClass == cName,
                                        onClick = { selectedFilterClass = cName },
                                        label = { Text(cName, fontSize = 10.sp) }
                                    )
                                }
                            }

                            val filteredStudents = allStudents.filter { stu ->
                                val matchQuery = searchQuery.isBlank() ||
                                        stu.name.contains(searchQuery, ignoreCase = true) ||
                                        stu.rollNumber.toString().contains(searchQuery)
                                val matchClass = selectedFilterClass == "সকল" || stu.studentClass == selectedFilterClass
                                matchQuery && matchClass
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "খুঁজে পাওয়া গেছে: ${BanglaUtils.toBanglaDigits(filteredStudents.size)} জন",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            val allIds = (state.selectedStudentIds + filteredStudents.map { it.id }).distinct()
                                            onStateChange(state.copy(selectedStudentIds = allIds))
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("এই পেজের সব", fontSize = 10.sp)
                                    }
                                    TextButton(
                                        onClick = { onStateChange(state.copy(selectedStudentIds = emptyList())) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("সব বাতিল", fontSize = 10.sp)
                                    }
                                }
                            }

                            // Student Selection List
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (filteredStudents.isEmpty()) {
                                    Text("কোনো শিক্ষার্থী পাওয়া যায়নি", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                                } else {
                                    filteredStudents.forEach { stu ->
                                        val isChecked = state.selectedStudentIds.contains(stu.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val list = state.selectedStudentIds.toMutableList()
                                                    if (isChecked) list.remove(stu.id) else list.add(stu.id)
                                                    onStateChange(state.copy(selectedStudentIds = list))
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    val list = state.selectedStudentIds.toMutableList()
                                                    if (checked) list.add(stu.id) else list.remove(stu.id)
                                                    onStateChange(state.copy(selectedStudentIds = list))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${stu.name} (${stu.studentClass}, রোল: ${BanglaUtils.toBanglaDigits(stu.rollNumber)})",
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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

        // Advanced CSV and Demo Options
        item {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("উন্নত ডেটা অপশন ও CSV ইমপোর্ট", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Icon(
                        if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showAdvanced) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportCsv,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV ইমপোর্ট", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onLoadDemo,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ডেমো ডেটা", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: বিষয় ও রুটিন এডিটর (Compact table layout per prompt specifications)
 * Layout format:
 * তারিখ । বার (auto)। বিষয় । ডিলেট বাটন (day)
 *                            । বিষয় ২ । ডিলেট বাটন
 * নতুন দিন । এই শ্রেণির সেভ বাটন । সকল শ্রেণীতে কপি বাটন
 */
@Composable
private fun CompactRoutineEditorTab(
    state: AdmitCardMakerState,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onCopyBaseToAll: () -> Unit,
    onShowAddClassDialog: () -> Unit,
    onShowAddTimePresetDialog: () -> Unit
) {
    val context = LocalContext.current
    val currentKey = state.activeRoutineKey
    val isBase = currentKey == AdmitCardStorage.BASE_KEY
    val currentRoutine = state.classRoutines[currentKey] ?: state.classRoutines[AdmitCardStorage.BASE_KEY] ?: emptyList()
    val currentTime = state.classTimes[currentKey] ?: state.classTimes[AdmitCardStorage.BASE_KEY] ?: state.defaultTime

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Class Selector Tabs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Base Routine tab
                        item {
                            FilterChip(
                                selected = isBase,
                                onClick = { onStateChange(state.copy(activeRoutineKey = AdmitCardStorage.BASE_KEY)) },
                                label = { Text("★ মূল বেস রুটিন", fontSize = 10.5.sp, fontWeight = if (isBase) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    if (isBase) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            )
                        }

                        // Individual Class tabs
                        items(state.classes) { cName ->
                            val isSelected = currentKey == cName
                            val hasCustom = (state.classRoutines[cName]?.isNotEmpty() == true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onStateChange(state.copy(activeRoutineKey = cName)) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(cName, fontSize = 10.5.sp)
                                        if (hasCustom) {
                                            Box(modifier = Modifier.size(5.dp).background(Color(0xFF047857), CircleShape))
                                        }
                                    }
                                }
                            )
                        }

                        // Add Class Button
                        item {
                            IconButton(
                                onClick = onShowAddClassDialog,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Class", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // 2. Exam Time for this specific class
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentTime,
                        onValueChange = { newT ->
                            val updatedTimes = state.classTimes.toMutableMap()
                            updatedTimes[currentKey] = newT
                            onStateChange(state.copy(classTimes = updatedTimes))
                        },
                        label = { Text("পরীক্ষার সময় ($currentKey)", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Quick Time Chips
                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.timePresets.forEach { t ->
                            SuggestionChip(
                                onClick = {
                                    val updatedTimes = state.classTimes.toMutableMap()
                                    updatedTimes[currentKey] = t
                                    onStateChange(state.copy(classTimes = updatedTimes))
                                },
                                label = { Text(t, fontSize = 9.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Compact Routine Table Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBase) "মূল বেস রুটিনের দিন ও বিষয়" else "$currentKey-এর পরীক্ষার রুটিন",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${BanglaUtils.toBanglaDigits(currentRoutine.size)} টি দিন",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4. Compact Routine Rows with 2/6 (Date & Day), 3/6 (Subject), 1/6 (Actions) ratio
        itemsIndexed(items = currentRoutine) { dayIndex, day ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ২/৬ অংশ (Date & Day Name Picker)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(2f)
                            .clickable {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val calSelected = Calendar.getInstance()
                                        calSelected.set(year, month, dayOfMonth)
                                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calSelected.time)
                                        val dayName = AdmitCardStorage.getDayNameFromDate(dateStr)
                                        val updatedList = currentRoutine.toMutableList()
                                        updatedList[dayIndex] = day.copy(date = dateStr, day = dayName)
                                        val updatedMap = state.classRoutines.toMutableMap()
                                        updatedMap[currentKey] = updatedList
                                        onStateChange(state.copy(classRoutines = updatedMap))
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = AdmitCardStorage.formatDateToBangla(day.date).ifBlank { "তারিখ" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = if (day.day.isNotBlank()) day.day else "বার (auto)",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }

                    // ৩/৬ অংশ (Subjects)
                    Column(
                        modifier = Modifier.weight(3f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val activeSubs = if (day.subjects.isEmpty()) listOf("") else day.subjects
                        activeSubs.forEachIndexed { subIndex, subText ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                OutlinedTextField(
                                    value = subText,
                                    onValueChange = { newSub ->
                                        val updatedSubs = activeSubs.toMutableList()
                                        updatedSubs[subIndex] = newSub
                                        val updatedList = currentRoutine.toMutableList()
                                        updatedList[dayIndex] = day.copy(subjects = updatedSubs)
                                        val updatedMap = state.classRoutines.toMutableMap()
                                        updatedMap[currentKey] = updatedList
                                        onStateChange(state.copy(classRoutines = updatedMap))
                                    },
                                    placeholder = { Text("বিষয় ${BanglaUtils.toBanglaDigits(subIndex + 1)}", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                if (activeSubs.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val updatedSubs = activeSubs.toMutableList()
                                            updatedSubs.removeAt(subIndex)
                                            val updatedList = currentRoutine.toMutableList()
                                            updatedList[dayIndex] = day.copy(subjects = updatedSubs)
                                            val updatedMap = state.classRoutines.toMutableMap()
                                            updatedMap[currentKey] = updatedList
                                            onStateChange(state.copy(classRoutines = updatedMap))
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove Subject", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ১/৬ অংশ (Action Buttons - Add subject & Delete day)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val updatedSubs = (day.subjects + "").toMutableList()
                                val updatedList = currentRoutine.toMutableList()
                                updatedList[dayIndex] = day.copy(subjects = updatedSubs)
                                val updatedMap = state.classRoutines.toMutableMap()
                                updatedMap[currentKey] = updatedList
                                onStateChange(state.copy(classRoutines = updatedMap))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.AddCircleOutline, contentDescription = "Add Subject", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        }

                        IconButton(
                            onClick = {
                                val updatedList = currentRoutine.toMutableList()
                                updatedList.removeAt(dayIndex)
                                val updatedMap = state.classRoutines.toMutableMap()
                                updatedMap[currentKey] = updatedList
                                onStateChange(state.copy(classRoutines = updatedMap))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Day", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }

        // 5. Bottom Action Bar per prompt specification:
        // [নতুন দিন] । [এই শ্রেণির সেভ বাটন] । [সকল শ্রেণীতে কপি বাটন]
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // নতুন দিন
                    OutlinedButton(
                        onClick = {
                            val updatedList = currentRoutine.toMutableList()
                            val nextDate = if (updatedList.isNotEmpty()) {
                                AdmitCardStorage.addDaysToDate(updatedList.last().date, 1).ifBlank {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                }
                            } else {
                                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            }
                            updatedList.add(
                                RoutineDay(
                                    date = nextDate,
                                    day = AdmitCardStorage.getDayNameFromDate(nextDate),
                                    subjects = listOf("")
                                )
                            )
                            val updatedMap = state.classRoutines.toMutableMap()
                            updatedMap[currentKey] = updatedList
                            onStateChange(state.copy(classRoutines = updatedMap))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("নতুন দিন", fontSize = 11.sp)
                    }

                    // এই শ্রেণির সেভ বাটন
                    Button(
                        onClick = {
                            val updatedMap = state.classRoutines.toMutableMap()
                            updatedMap[currentKey] = currentRoutine
                            val updatedTimes = state.classTimes.toMutableMap()
                            updatedTimes[currentKey] = currentTime
                            onStateChange(state.copy(classRoutines = updatedMap, classTimes = updatedTimes))
                            AdmitCardStorage.saveState(context, state)
                            Toast.makeText(context, "$currentKey-এর রুটিন ও সময় সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1.1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("এই শ্রেণি সেভ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // সকল শ্রেণীতে কপি বাটন
                    OutlinedButton(
                        onClick = onCopyBaseToAll,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("সকল শ্রেণিতে কপি", fontSize = 10.5.sp)
                    }
                }
            }
        }
    }
}

/**
 * Tab 3: প্রিভিউ ও প্রিন্ট (Realistic Sheet Preview + Student/Class Selector at Top)
 */
@Composable
private fun PreviewAndPrintTab(
    state: AdmitCardMakerState,
    allStudents: List<StudentEntity>,
    selectedStudents: List<AdmitCardStudent>,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit
) {
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var showSettingsExpander by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }

    val allClasses = remember(allStudents) {
        allStudents.map { it.studentClass.trim() }.filter { it.isNotBlank() }.distinct()
    }

    val cardsPerPage = Math.max(1, state.settings.cardsPerPage)
    val pages = remember(selectedStudents, cardsPerPage) {
        selectedStudents.chunked(cardsPerPage)
    }
    val totalPages = Math.max(1, pages.size)
    val safePageIndex = currentPageIndex.coerceIn(0, totalPages - 1)
    val currentCards = if (pages.isNotEmpty()) pages[safePageIndex] else emptyList()

    val decodedSignature = remember(state.signature) {
        AdmitCardStorage.decodeBase64ToBitmap(state.signature)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. TOP: শিক্ষার্থী ও শ্রেণি নির্বাচন (Student & Class Selection at Top)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("শিক্ষার্থী ও শ্রেণি নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${BanglaUtils.toBanglaDigits(selectedStudents.size)} / ${BanglaUtils.toBanglaDigits(allStudents.size)} জন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Class Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedClassFilter == null,
                            onClick = { selectedClassFilter = null },
                            label = { Text("সকল শ্রেণি (${BanglaUtils.toBanglaDigits(allStudents.size)})", fontSize = 10.5.sp) }
                        )
                        allClasses.forEach { cls ->
                            val count = allStudents.count { it.studentClass.trim() == cls }
                            FilterChip(
                                selected = selectedClassFilter == cls,
                                onClick = {
                                    selectedClassFilter = if (selectedClassFilter == cls) null else cls
                                },
                                label = { Text("$cls (${BanglaUtils.toBanglaDigits(count)})", fontSize = 10.5.sp) }
                            )
                        }
                    }

                    // Quick Select Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val targetStudents = if (selectedClassFilter == null) allStudents else allStudents.filter { it.studentClass.trim() == selectedClassFilter }
                                val newIds = (state.selectedStudentIds + targetStudents.map { it.id }).distinct()
                                onStateChange(state.copy(selectedStudentIds = newIds))
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("সব নির্বাচন", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val targetStudents = if (selectedClassFilter == null) allStudents else allStudents.filter { it.studentClass.trim() == selectedClassFilter }
                                val targetIds = targetStudents.map { it.id }.toSet()
                                val newIds = state.selectedStudentIds.filter { it !in targetIds }
                                onStateChange(state.copy(selectedStudentIds = newIds))
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("সব বাতিল", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 2. Action Card & Live Signature Size / Page Layout Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "পৃষ্ঠা ${BanglaUtils.toBanglaDigits(safePageIndex + 1)} / ${BanglaUtils.toBanglaDigits(totalPages)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "প্রিন্ট শিট: প্রতি পাতায় ${BanglaUtils.toBanglaDigits(cardsPerPage)}টি কার্ড",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Page Navigation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { if (safePageIndex > 0) currentPageIndex-- },
                                enabled = safePageIndex > 0,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                            }
                            IconButton(
                                onClick = { if (safePageIndex < totalPages - 1) currentPageIndex++ },
                                enabled = safePageIndex < totalPages - 1,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                            }
                        }
                    }

                    // Native Print and Share PDF buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPrint,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্রিন্ট / PDF ডায়লগ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF ফাইল শেয়ার", fontSize = 12.sp)
                        }
                    }

                    // লাইভ স্বাক্ষর সাইজ কন্ট্রোল (Live Signature Size Controls)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("স্বাক্ষরের আকার (লাইভ পরিবর্তন):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = when (state.settings.sigSize) {
                                    "1" -> "১ (ছোট)"
                                    "2" -> "২ (স্বাভাবিক)"
                                    "4" -> "৪ (বড়)"
                                    "5" -> "৫ (খুব বড়)"
                                    else -> "৩ (মাঝারি)"
                                },
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "1" to "১",
                                "2" to "২",
                                "3" to "৩",
                                "4" to "৪",
                                "5" to "৫"
                            ).forEach { (sVal, sLbl) ->
                                val isSelected = state.settings.sigSize == sVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onStateChange(state.copy(settings = state.settings.copy(sigSize = sVal)))
                                    },
                                    label = { Text(sLbl, fontSize = 10.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Expandable Page Settings within Preview Tab
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSettingsExpander = !showSettingsExpander }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("প্রতি পৃষ্ঠায় কার্ড সংখ্যা ও বর্ডার পরিবর্তন", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            Icon(if (showSettingsExpander) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (showSettingsExpander) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Cards Per Page
                            Text("প্রতি পৃষ্ঠায় কার্ড সংখ্যা:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(1, 2, 3, 4, 6).forEach { cnt ->
                                    FilterChip(
                                        selected = state.settings.cardsPerPage == cnt,
                                        onClick = {
                                            onStateChange(state.copy(settings = state.settings.copy(cardsPerPage = cnt)))
                                        },
                                        label = { Text("$cnt টি", fontSize = 10.5.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Frame Border Style
                            Text("বর্ডার স্টাইল:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "dashed" to "ড্যাশ (রেফারেন্স)",
                                    "solid" to "সলিড",
                                    "double" to "ডাবল",
                                    "none" to "বর্ডার ছাড়া"
                                ).forEach { (fStyle, fLabel) ->
                                    FilterChip(
                                        selected = state.settings.frameStyle == fStyle,
                                        onClick = {
                                            onStateChange(state.copy(settings = state.settings.copy(frameStyle = fStyle)))
                                        },
                                        label = { Text(fLabel, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Realistic A4 Sheet Canvas Preview (Showing Exact Output on Paper)
        item {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentCards.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("কোনো শিক্ষার্থী পাওয়া যায়নি", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else {
                        currentCards.forEach { student ->
                            AdmitCardExactLayout(
                                student = student,
                                state = state,
                                decodedSignature = decodedSignature
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * EXACT REPLICA of the uploaded reference image (Screenshot_20260820_075236_Slides.jpg)
 */
@Composable
private fun AdmitCardExactLayout(
    student: AdmitCardStudent,
    state: AdmitCardMakerState,
    decodedSignature: Bitmap?
) {
    val routine = state.classRoutines[student.studentClass]?.ifEmpty { null }
        ?: state.classRoutines[AdmitCardStorage.BASE_KEY]
        ?: emptyList()
    val examTime = state.classTimes[student.studentClass]
        ?: state.classTimes[AdmitCardStorage.BASE_KEY]
        ?: state.defaultTime.ifBlank { "১০:০০-১১:০০" }

    val dispSchoolName = state.schoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" }
    val dispAddress = state.schoolAddress.ifBlank { "আলফাডাঙ্গা, ফরিদপুর।" }
    val examName = state.examName.ifBlank { "২য় প্রান্তিক মূল্যায়ন - ২০২৬" }

    // Outer card container with dashed border matching reference image
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFF222222)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN (43% width) - School Name, Address, Exam Name, Underlined Title, Student details, Signature
            Column(
                modifier = Modifier
                    .weight(0.43f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header (Center aligned)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dispSchoolName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp,
                        color = Color.Black
                    )
                    if (dispAddress.isNotBlank()) {
                        Text(
                            text = dispAddress,
                            fontSize = 9.5.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Black
                        )
                    }
                    Text(
                        text = examName,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Text(
                        text = "প্রবেশপত্র",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // এক রো সমান স্পেস (Space after প্রবেশপত্র)
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Student Details (Left aligned)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row {
                        Text("নাম : ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(student.name, fontSize = 10.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row {
                        Text("শ্রেণি : ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(student.studentClass, fontSize = 10.sp, color = Color.Black)
                    }
                    Row {
                        Text("রোল : ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(BanglaUtils.toBanglaDigits(student.rollNumber), fontSize = 10.sp, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom-Right Signature Image, Signature Line & Label
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    val sigHeightDp = when (state.settings.sigSize) {
                        "1" -> 16.dp
                        "2" -> 22.dp
                        "4" -> 36.dp
                        "5" -> 46.dp
                        else -> 28.dp // "3"
                    }

                    if (decodedSignature != null) {
                        Image(
                            bitmap = decodedSignature.asImageBitmap(),
                            contentDescription = "স্বাক্ষর",
                            modifier = Modifier
                                .height(sigHeightDp)
                                .widthIn(max = 100.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(sigHeightDp))
                    }

                    // প্রধান শিক্ষকের স্বাক্ষর লাইন উপরে থাকবে
                    HorizontalDivider(
                        modifier = Modifier
                            .width(95.dp)
                            .padding(top = 2.dp, bottom = 2.dp),
                        thickness = 0.8.dp,
                        color = Color.Black
                    )

                    Text(
                        text = "প্রধান শিক্ষকের স্বাক্ষর",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // VERTICAL DASHED DIVIDER
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (routine.size > 4) (routine.size * 22).dp else 115.dp)
                    .background(Color(0xFF555555))
            )

            // RIGHT COLUMN (57% width) - Solid Routine Table
            Column(
                modifier = Modifier
                    .weight(0.57f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = Color.White,
                    border = BorderStroke(0.8.dp, Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Row 1: Exam Name Routine Header
                        Surface(
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(0.4.dp, Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$examName এর রুটিন",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(2.dp),
                                color = Color.Black
                            )
                        }

                        // Row 2 & 3: Table Headers
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Col 1: তারিখ
                            Surface(
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.4.dp, Color.Black),
                                modifier = Modifier.weight(0.28f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        text = "তারিখ",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = Color.Black
                                    )
                                }
                            }
                            // Col 2: বার
                            Surface(
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.4.dp, Color.Black),
                                modifier = Modifier.weight(0.22f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        text = "বার",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = Color.Black
                                    )
                                }
                            }
                            // Col 3: Time & Subject
                            Column(modifier = Modifier.weight(0.50f)) {
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    border = BorderStroke(0.4.dp, Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = examTime,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(1.dp),
                                        color = Color.Black
                                    )
                                }
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    border = BorderStroke(0.4.dp, Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "বিষয়",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(1.dp),
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        // Data Rows
                        if (routine.isEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.28f)) {
                                    Text("—", fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp))
                                }
                                Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.22f)) {
                                    Text("—", fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp))
                                }
                                Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.50f)) {
                                    Text("সকল বিষয়", fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp))
                                }
                            }
                        } else {
                            routine.forEach { day ->
                                val dateStr = AdmitCardStorage.formatDateToBangla(day.date).ifBlank { "—" }
                                val dayName = if (day.day.isNotBlank()) day.day else AdmitCardStorage.getDayNameFromDate(day.date).ifBlank { "—" }
                                val activeSubs = day.subjects.filter { it.isNotBlank() }

                                if (activeSubs.isEmpty() || activeSubs.size == 1) {
                                    val subText = if (activeSubs.isEmpty()) "—" else activeSubs.first()
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.28f)) {
                                            Text(dateStr, fontSize = 7.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                        }
                                        Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.22f)) {
                                            Text(dayName, fontSize = 7.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                        }
                                        Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.50f)) {
                                            Text(subText, fontSize = 7.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                        }
                                    }
                                } else {
                                    // Multiple subjects on same day
                                    activeSubs.forEachIndexed { subIndex, subText ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.28f)) {
                                                Text(if (subIndex == 0) dateStr else "", fontSize = 7.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                            }
                                            Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.22f)) {
                                                Text(if (subIndex == 0) dayName else "", fontSize = 7.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                            }
                                            Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.50f)) {
                                                Text(subText, fontSize = 7.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
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
