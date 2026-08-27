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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmitCardMakerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val allDbStudents by viewModel.allStudents.collectAsState()

    // Default School Name & Address
    val defaultSchoolName = remember(schoolInfo) {
        val sName = schoolInfo?.schoolName ?: "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়"
        val sAddr = schoolInfo?.address ?: "আলফাডাঙ্গা, ফরিদপুর।"
        if (sAddr.isNotBlank() && !sName.contains(sAddr)) "$sName, $sAddr" else sName
    }

    var state by remember {
        mutableStateOf(AdmitCardStorage.loadState(context, defaultSchoolName))
    }

    var activeTab by remember { mutableIntStateOf(0) }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var newClassText by remember { mutableStateOf("") }
    var showAddTimePresetDialog by remember { mutableStateOf(false) }
    var newTimePresetText by remember { mutableStateOf("") }

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
                AdmitCardStudent("3", "মোসা. মরিয়ম আক্তার", "প্রাক-প্রাথমিক", "৩"),
                AdmitCardStudent("4", "মোঃ তারিফ মাহমুদ", "প্রাক-প্রাথমিক", "৪"),
                AdmitCardStudent("5", "আফিয়া ইসলাম", "১ম শ্রেণি", "১"),
                AdmitCardStudent("6", "সাকিব আল হাসান", "১ম শ্রেণি", "২")
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

    // Print & Share Handlers
    val doPrint = {
        if (currentStudents.isEmpty()) {
            Toast.makeText(context, "প্রিন্ট করার জন্য কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
        } else {
            val html = PrintUtils.generateAdmitCardsHtml(state, currentStudents)
            PrintUtils.printHtmlContent(
                context = context,
                documentName = "AdmitCards_${state.examName}",
                htmlContent = html,
                isLandscape = state.settings.orientation == "landscape"
            )
        }
    }

    val doShareHtml = {
        if (currentStudents.isEmpty()) {
            Toast.makeText(context, "শেয়ার করার জন্য কোনো শিক্ষার্থী নির্বাচিত নেই", Toast.LENGTH_SHORT).show()
        } else {
            val html = PrintUtils.generateAdmitCardsHtml(state, currentStudents)
            PrintUtils.shareHtmlDocument(
                context = context,
                documentTitle = "AdmitCards_${state.examName}",
                htmlContent = html
            )
        }
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
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${BanglaUtils.toBanglaDigits(currentStudents.size)} জন শিক্ষার্থী নির্বাচিত",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Direct Quick Print Button in TopBar
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

                    // Compact Minimal Tabs
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { HorizontalDivider(thickness = 0.8.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
                    ) {
                        val tabs = listOf(
                            Triple(0, "তথ্য ও ছাত্র", Icons.Filled.Badge),
                            Triple(1, "রুটিন ও বিষয়", Icons.Filled.CalendarMonth),
                            Triple(2, "পৃষ্ঠা সেটিং", Icons.Filled.Tune),
                            Triple(3, "পেজ প্রিভিউ", Icons.Filled.Visibility)
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
                0 -> CompactInfoTab(
                    state = state,
                    allStudentsCount = allDbStudents.size,
                    onStateChange = { newState ->
                        state = newState
                        AdmitCardStorage.saveState(context, state)
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
                            StudentEntity(id = "4", studentClass = "১ম শ্রেণি", rollNumber = 2, name = "মোঃ তারিফ মাহমুদ", fatherName = "তারিক মাহমুদ", motherName = "রুমা পারভীন")
                        )
                        demo.forEach { viewModel.insertStudent(it) }
                        Toast.makeText(context, "ডেমো শিক্ষার্থী ডেটা লোড হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> CompactRoutineTab(
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
                            updatedRoutines[c] = baseRoutine.map { it.copy() }
                            updatedTimes[c] = baseTime
                        }
                        state = state.copy(classRoutines = updatedRoutines, classTimes = updatedTimes)
                        AdmitCardStorage.saveState(context, state)
                        Toast.makeText(context, "বেস রুটিন ও সময় সকল শ্রেণিতে কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    onShowAddClassDialog = { showAddClassDialog = true },
                    onShowAddTimePresetDialog = { showAddTimePresetDialog = true }
                )
                2 -> CompactPrintSettingsTab(
                    settings = state.settings,
                    onSettingsChange = { updatedSettings ->
                        state = state.copy(settings = updatedSettings)
                        AdmitCardStorage.saveState(context, state)
                    }
                )
                3 -> PageSheetPreviewTab(
                    state = state,
                    allStudents = allDbStudents,
                    selectedStudents = currentStudents,
                    onStateChange = { newState ->
                        state = newState
                        AdmitCardStorage.saveState(context, state)
                    },
                    onPrint = doPrint,
                    onShareHtml = doShareHtml
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
 * Tab 1: তথ্য ও শিক্ষার্থী (Compact & Minimal)
 */
@Composable
private fun CompactInfoTab(
    state: AdmitCardMakerState,
    allStudentsCount: Int,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onPickSignature: () -> Unit,
    onClearSignature: () -> Unit,
    onImportCsv: () -> Unit,
    onLoadDemo: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "প্রাতিষ্ঠানিক ও পরীক্ষার বিবরণ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = state.schoolName,
                        onValueChange = { onStateChange(state.copy(schoolName = it)) },
                        label = { Text("বিদ্যালয়ের নাম ও ঠিকানা") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.examName,
                        onValueChange = { onStateChange(state.copy(examName = it)) },
                        label = { Text("পরীক্ষার নাম") },
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
                                label = { Text(e, fontSize = 10.5.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Signature & Scope Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "প্রধান শিক্ষকের স্বাক্ষর",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(width = 100.dp, height = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.signature.isNotBlank()) {
                                    Text("✒️ সংযুক্ত", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                } else {
                                    Text("স্বাক্ষর নেই", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = onPickSignature,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (state.signature.isNotBlank()) "পরিবর্তন" else "ছবি আপলোড", fontSize = 11.sp)
                                }
                                if (state.signature.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = onClearSignature,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text("মুছুন", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Text("প্রবেশপত্রে প্রধান শিক্ষকের স্বাক্ষরের ঘরে স্বয়ংক্রিয়ভাবে বসবে।", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Collapsible Advanced Settings (Hides extra features)
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("উন্নত ডেটা অপশন ও CSV ইমপোর্ট", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(
                        if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showAdvanced) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("শিক্ষার্থী তালিকা ইমপোর্ট বা ব্যাকআপ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onImportCsv,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV ফাইল ইমপোর্ট", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = onLoadDemo,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ডেমো শিক্ষার্থী লোড", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: বিষয় ও পরীক্ষার রুটিন (Compact & Minimal)
 */
@Composable
private fun CompactRoutineTab(
    state: AdmitCardMakerState,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onCopyBaseToAll: () -> Unit,
    onShowAddClassDialog: () -> Unit,
    onShowAddTimePresetDialog: () -> Unit
) {
    val context = LocalContext.current
    val currentKey = state.activeRoutineKey
    val isBase = currentKey == AdmitCardStorage.BASE_KEY
    val currentRoutine = state.classRoutines[currentKey] ?: emptyList()
    val currentTime = state.classTimes[currentKey] ?: state.defaultTime

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Class Selector Tabs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Base Routine tab
                        item {
                            FilterChip(
                                selected = isBase,
                                onClick = { onStateChange(state.copy(activeRoutineKey = AdmitCardStorage.BASE_KEY)) },
                                label = { Text("★ মূল বেস রুটিন", fontSize = 11.sp, fontWeight = if (isBase) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    if (isBase) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            )
                        }

                        // Class tabs
                        items(state.classes) { cName ->
                            val isSelected = currentKey == cName
                            val hasCustom = (state.classRoutines[cName]?.isNotEmpty() == true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onStateChange(state.copy(activeRoutineKey = cName)) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(cName, fontSize = 11.sp)
                                        if (hasCustom) {
                                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF047857), CircleShape))
                                        }
                                    }
                                }
                            )
                        }

                        // Add Class Tab
                        item {
                            IconButton(
                                onClick = onShowAddClassDialog,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Class", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Exam Time Row
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
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
                        label = { Text("পরীক্ষার সময়সূচি", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Quick Time Preset Chips
                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.timePresets.take(4).forEach { t ->
                            SuggestionChip(
                                onClick = {
                                    val updatedTimes = state.classTimes.toMutableMap()
                                    updatedTimes[currentKey] = t
                                    onStateChange(state.copy(classTimes = updatedTimes))
                                },
                                label = { Text(t, fontSize = 9.5.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Routine Days Header & Copy Base Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBase) "মূল বেস রুটিনের দিন ও বিষয়" else "$currentKey-এর পরীক্ষার রুটিন",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (isBase) {
                    Button(
                        onClick = onCopyBaseToAll,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সকল শ্রেণিতে কপি করুন", fontSize = 10.5.sp)
                    }
                }
            }
        }

        // Routine Table Rows
        itemsIndexed(currentRoutine) { index: Int, day: RoutineDay ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date & Day Box
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
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
                                        updatedList[index] = day.copy(date = dateStr, day = dayName)
                                        val updatedMap = state.classRoutines.toMutableMap()
                                        updatedMap[currentKey] = updatedList
                                        onStateChange(state.copy(classRoutines = updatedMap))
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = AdmitCardStorage.formatDateToBangla(day.date).ifBlank { "তারিখ নির্বাচন" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (day.day.isNotBlank()) day.day else "বার",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Subject Input
                    val subStr = day.subjects.joinToString(", ")
                    OutlinedTextField(
                        value = subStr,
                        onValueChange = { newVal: String ->
                            val updatedList = currentRoutine.toMutableList()
                            updatedList[index] = day.copy(subjects = newVal.split(",").map { it.trim() })
                            val updatedMap = state.classRoutines.toMutableMap()
                            updatedMap[currentKey] = updatedList
                            onStateChange(state.copy(classRoutines = updatedMap))
                        },
                        placeholder = { Text("বিষয় (যেমন: বাংলা, ইংরেজি)", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Delete Row Button
                    IconButton(
                        onClick = {
                            val updatedList = currentRoutine.toMutableList()
                            updatedList.removeAt(index)
                            val updatedMap = state.classRoutines.toMutableMap()
                            updatedMap[currentKey] = updatedList
                            onStateChange(state.copy(classRoutines = updatedMap))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Add Row Button
        item {
            OutlinedButton(
                onClick = {
                    val updatedList = currentRoutine.toMutableList()
                    val nextDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    updatedList.add(RoutineDay(date = nextDate, day = AdmitCardStorage.getDayNameFromDate(nextDate), subjects = listOf("নতুন বিষয়")))
                    val updatedMap = state.classRoutines.toMutableMap()
                    updatedMap[currentKey] = updatedList
                    onStateChange(state.copy(classRoutines = updatedMap))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("নতুন পরীক্ষার দিন ও বিষয় যোগ করুন", fontSize = 11.5.sp)
            }
        }
    }
}

/**
 * Tab 3: প্রিন্ট ও লেআউট সেটিং (Compact & Minimal)
 */
@Composable
private fun CompactPrintSettingsTab(
    settings: AdmitCardSettings,
    onSettingsChange: (AdmitCardSettings) -> Unit
) {
    var showAdvancedMargins by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cards Per Page
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("প্রতি পৃষ্ঠায় প্রবেশপত্রের সংখ্যা (Cards Per Page)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 3, 4, 6).forEach { count ->
                            FilterChip(
                                selected = settings.cardsPerPage == count,
                                onClick = { onSettingsChange(settings.copy(cardsPerPage = count)) },
                                label = { Text("$count টি", fontSize = 11.sp, fontWeight = if (settings.cardsPerPage == count) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text("রেফারেন্স ফরম্যাটের মত প্রতি পৃষ্ঠায় ২ থেকে ৪টি কার্ড সবচেয়ে নিখুঁতভাবে প্রিন্ট হয়।", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Page Size & Border Style
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("কার্ডের বর্ডার স্টাইল", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val styles = listOf(
                        "dashed" to "ড্যাশ (রেফারেন্স ফরম্যাট)",
                        "solid" to "সলিড রেখা",
                        "double" to "ডাবল বর্ডার",
                        "none" to "বর্ডার ছাড়া"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styles.forEach { (sKey, label) ->
                            FilterChip(
                                selected = settings.frameStyle == sKey,
                                onClick = { onSettingsChange(settings.copy(frameStyle = sKey)) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text("কাগজের সাইজ ও ওরিয়েন্টেশন", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("A4", "Letter", "Legal").forEach { pSize ->
                            FilterChip(
                                selected = settings.pageSize == pSize,
                                onClick = { onSettingsChange(settings.copy(pageSize = pSize)) },
                                label = { Text(pSize, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Advanced Margins
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedMargins = !showAdvancedMargins }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("উন্নত মার্জিন ও ফন্ট সেটিং (ইঞ্চি হিসেবে)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(
                        if (showAdvancedMargins) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showAdvancedMargins) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = settings.marginTop.toString(),
                                onValueChange = { onSettingsChange(settings.copy(marginTop = it.toFloatOrNull() ?: 0.2f)) },
                                label = { Text("Top (in)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = settings.marginBottom.toString(),
                                onValueChange = { onSettingsChange(settings.copy(marginBottom = it.toFloatOrNull() ?: 0.2f)) },
                                label = { Text("Bottom (in)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = settings.marginLeft.toString(),
                                onValueChange = { onSettingsChange(settings.copy(marginLeft = it.toFloatOrNull() ?: 0.2f)) },
                                label = { Text("Left (in)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = settings.marginRight.toString(),
                                onValueChange = { onSettingsChange(settings.copy(marginRight = it.toFloatOrNull() ?: 0.2f)) },
                                label = { Text("Right (in)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 4: পেজ প্রিভিউ ও প্রিন্ট (Realistic Page Sheet Preview)
 */
@Composable
private fun PageSheetPreviewTab(
    state: AdmitCardMakerState,
    allStudents: List<StudentEntity>,
    selectedStudents: List<AdmitCardStudent>,
    onStateChange: (AdmitCardMakerState) -> Unit,
    onPrint: () -> Unit,
    onShareHtml: () -> Unit
) {
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var showFilterScope by remember { mutableStateOf(false) }

    val cardsPerPage = Math.max(1, state.settings.cardsPerPage)
    val pages = remember(selectedStudents, cardsPerPage) {
        selectedStudents.chunked(cardsPerPage)
    }
    val totalPages = Math.max(1, pages.size)
    val safePageIndex = currentPageIndex.coerceIn(0, totalPages - 1)
    val currentCards = if (pages.isNotEmpty()) pages[safePageIndex] else emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sticky Header / Print Action Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                text = "মোট ${BanglaUtils.toBanglaDigits(selectedStudents.size)} জন · প্রতি পাতায় ${BanglaUtils.toBanglaDigits(cardsPerPage)}টি",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Page Switcher
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (safePageIndex > 0) currentPageIndex-- },
                                enabled = safePageIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                            }
                            IconButton(
                                onClick = { if (safePageIndex < totalPages - 1) currentPageIndex++ },
                                enabled = safePageIndex < totalPages - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                            }
                        }
                    }

                    // Action Buttons (Print & Share)
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
                            Text("প্রিন্ট / PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onShareHtml,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF/ডকুমেন্ট শেয়ার", fontSize = 12.sp)
                        }
                    }

                    // Scope filter expander
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFilterScope = !showFilterScope }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("শিক্ষার্থী নির্বাচন ফিল্টার (${if (state.scope == "all") "সব শ্রেণি" else if (state.scope == "class") "নির্দিষ্ট শ্রেণি" else "বাছাইকৃত"})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Icon(if (showFilterScope) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    if (showFilterScope) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("all" to "সব শ্রেণি", "class" to "শ্রেণিভিত্তিক", "student" to "শিক্ষার্থী").forEach { (sc, lbl) ->
                                FilterChip(
                                    selected = state.scope == sc,
                                    onClick = { onStateChange(state.copy(scope = sc)) },
                                    label = { Text(lbl, fontSize = 10.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Realistic A4 Page Sheet Canvas View
        item {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                state = state
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * EXACT REPLICA of the uploaded admit card layout image.
 * Natural dynamic height expands only when new routine days are added!
 */
@Composable
private fun AdmitCardExactLayout(
    student: AdmitCardStudent,
    state: AdmitCardMakerState
) {
    val routine = state.classRoutines[student.studentClass]?.ifEmpty { null }
        ?: state.classRoutines[AdmitCardStorage.BASE_KEY]
        ?: emptyList()
    val examTime = state.classTimes[student.studentClass]
        ?: state.classTimes[AdmitCardStorage.BASE_KEY]
        ?: state.defaultTime.ifBlank { "১০:০০-১২:৩০" }

    val (dispSchoolName, dispAddress) = remember(state.schoolName) {
        if (state.schoolName.contains(",")) {
            val parts = state.schoolName.split(",", limit = 2)
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            Pair(state.schoolName.ifBlank { "৩৮ নং কটুরাকান্দি সরকারি প্রাথমিক বিদ্যালয়" }, "")
        }
    }

    // Outer container with rounded dashed border as in the reference image
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFF222222)), // Clean border matching dashed outline
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN (44% width) - School, Exam, Underlined Title, Student Info, Signature
            Column(
                modifier = Modifier
                    .weight(0.44f)
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
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.5.sp,
                        color = Color.Black
                    )
                    if (dispAddress.isNotBlank()) {
                        Text(
                            text = dispAddress,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Black
                        )
                    }
                    Text(
                        text = state.examName.ifBlank { "২য় প্রান্তিক মূল্যায়ন - ২০২৬" },
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "প্রবেশপত্র",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Student Info (Left aligned)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row {
                        Text("নাম : ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(student.name, fontSize = 10.5.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row {
                        Text("শ্রেণি : ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(student.studentClass, fontSize = 10.5.sp, color = Color.Black)
                    }
                    Row {
                        Text("রোল : ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(BanglaUtils.toBanglaDigits(student.rollNumber), fontSize = 10.5.sp, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom-Right Signature
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    if (state.signature.isNotBlank()) {
                        Text("✒️ [স্বাক্ষর]", fontSize = 8.sp, color = Color(0xFF047857))
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    Text(
                        text = "প্রধান শিক্ষকের স্বাক্ষর",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // VERTICAL DIVIDER (Dashed appearance)
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (routine.size > 4) (routine.size * 22).dp else 110.dp)
                    .background(Color(0xFF666666))
            )

            // RIGHT COLUMN (56% width) - Exam Routine Solid Table
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Table Container
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
                                text = "${state.examName.ifBlank { "পরীক্ষা" }} এর রুটিন",
                                fontSize = 9.sp,
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
                                Text(
                                    text = "তারিখ",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(2.dp),
                                    color = Color.Black
                                )
                            }
                            // Col 2: বার
                            Surface(
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.4.dp, Color.Black),
                                modifier = Modifier.weight(0.22f)
                            ) {
                                Text(
                                    text = "বার",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(2.dp),
                                    color = Color.Black
                                )
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
                                        fontSize = 8.sp,
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
                                        fontSize = 8.5.sp,
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
                                val subs = day.subjects.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.28f)) {
                                        Text(dateStr, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                    }
                                    Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.22f)) {
                                        Text(dayName, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
                                    }
                                    Surface(border = BorderStroke(0.4.dp, Color.Black), modifier = Modifier.weight(0.50f)) {
                                        Text(subs, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(2.dp), maxLines = 1)
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
