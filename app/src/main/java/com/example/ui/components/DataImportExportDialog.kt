package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.entity.UserEntity
import com.example.util.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportExportDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allStudents by viewModel.allStudents.collectAsState()
    val filteredStudents by viewModel.filteredStudents.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val examResults by viewModel.allExamResults.collectAsState()
    val routineItems by viewModel.routineItems.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Export CSV, 1: Import CSV & Map Structure, 2: Export PDF
    var selectedDataType by remember { mutableStateOf(CsvDataType.STUDENTS) }

    // ==========================================
    // TAB 1: CSV EXPORT STATES
    // ==========================================
    var exportScopeAll by remember { mutableStateOf(false) } // false: filtered, true: all
    val availableStudentExportFields = remember(customFields) {
        CsvUtils.getFieldsForType(CsvDataType.STUDENTS, customFields)
    }
    var selectedExportFieldKeys by remember {
        mutableStateOf(
            listOf("studentClass", "rollNumber", "name", "fatherName", "motherName", "mobile", "village", "birthDate", "gender")
        )
    }

    // SAF File Creator for CSV
    var pendingExportCsvString by remember { mutableStateOf("") }
    var pendingExportFileName by remember { mutableStateOf("export.csv") }
    val createCsvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null && pendingExportCsvString.isNotEmpty()) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(pendingExportCsvString.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }
                    Toast.makeText(context, "CSV ফাইল সফলভাবে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "সংরক্ষণ ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // TAB 2: CSV IMPORT & STRUCTURE MAPPING STATES
    // ==========================================
    var rawCsvContent by remember { mutableStateOf("") }
    var parsedHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var parsedDataRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var columnMapping by remember { mutableStateOf<Map<Int, String?>>(emptyMap()) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showPasteTextDialog by remember { mutableStateOf(false) }

    fun processCsvString(content: String) {
        rawCsvContent = content
        val (headers, rows) = CsvUtils.parseCsvContent(content)
        parsedHeaders = headers
        parsedDataRows = rows

        val availableFields = CsvUtils.getFieldsForType(selectedDataType, customFields)
        columnMapping = CsvUtils.autoDetectColumnMapping(headers, availableFields)
        importStatusMessage = "শনাক্তকরণ সম্পন্ন! কলাম: ${headers.size}টি, রেকর্ড: ${rows.size}টি।"
    }

    // File Picker for CSV
    val pickCsvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        }
                    }
                    if (!content.isNullOrBlank()) {
                        processCsvString(content)
                    } else {
                        Toast.makeText(context, "নির্বাচিত ফাইলটি খালি", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ফাইল পড়া যায়নি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // TAB 3: PDF EXPORT & SMALL INTERFACE STATES
    // ==========================================
    var pdfTitle by remember { mutableStateOf("শিক্ষার্থী তালিকা") }
    var pdfSubtitle by remember { mutableStateOf("শিক্ষাবর্ষ: ২০২৬") }
    var pdfIncludeImages by remember { mutableStateOf(true) }
    var pdfScopeAll by remember { mutableStateOf(false) } // false: filtered, true: all
    var pdfFontSizePt by remember { mutableStateOf(10) }
    var pdfRowPaddingPx by remember { mutableStateOf(5) }
    var pdfIsLandscape by remember { mutableStateOf(false) }
    var pdfShowHeader by remember { mutableStateOf(true) }
    var pdfShowStats by remember { mutableStateOf(true) }
    var pdfShowSignatures by remember { mutableStateOf(true) }
    var pdfSelectedCols by remember {
        mutableStateOf(listOf("photo", "rollNumber", "name", "studentClass", "fatherName", "mobile", "village"))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Modal Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.ImportExport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ইম্পোর্ট ও এক্সপোর্ট সেন্টার",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "CSV / Excel / PDF ফরম্যাটে ডেটা আদান-প্রদান",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("CSV এক্সপোর্ট", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("CSV ইম্পোর্ট ও ফিল্ড", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("PDF এক্সপোর্ট", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Contents
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        // ==========================================
                        // TAB 0: CSV EXPORT
                        // ==========================================
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 1. Data Type Selector
                                Text("১. ডেটার ধরণ নির্বাচন করুন:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(CsvDataType.values()) { type ->
                                        val isSel = selectedDataType == type
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { selectedDataType = type },
                                            label = { Text(type.displayNameBn) },
                                            leadingIcon = {
                                                if (isSel) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        )
                                    }
                                }

                                // 2. Scope Selector (For Students)
                                if (selectedDataType == CsvDataType.STUDENTS) {
                                    Text("২. পরিধি (Scope):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilterChip(
                                            selected = !exportScopeAll,
                                            onClick = { exportScopeAll = false },
                                            label = { Text("ফিল্টারকৃত তালিকা (${filteredStudents.size} জন)") }
                                        )
                                        FilterChip(
                                            selected = exportScopeAll,
                                            onClick = { exportScopeAll = true },
                                            label = { Text("সব শিক্ষার্থী (${allStudents.size} জন)") }
                                        )
                                    }

                                    // 3. Column Selection
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("৩. এক্সপোর্টের কলাম নির্বাচন:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row {
                                            TextButton(onClick = {
                                                selectedExportFieldKeys = availableStudentExportFields.map { it.key }
                                            }) {
                                                Text("সব নির্বাচন", fontSize = 11.sp)
                                            }
                                            TextButton(onClick = {
                                                selectedExportFieldKeys = listOf("studentClass", "rollNumber", "name")
                                            }) {
                                                Text("ডিফল্ট", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .padding(8.dp)
                                    ) {
                                        availableStudentExportFields.chunked(2).forEach { rowFields ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                rowFields.forEach { field ->
                                                    val isChecked = selectedExportFieldKeys.contains(field.key)
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedExportFieldKeys = if (isChecked) {
                                                                    selectedExportFieldKeys - field.key
                                                                } else {
                                                                    selectedExportFieldKeys + field.key
                                                                }
                                                            }
                                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = { chk ->
                                                                selectedExportFieldKeys = if (chk) {
                                                                    selectedExportFieldKeys + field.key
                                                                } else {
                                                                    selectedExportFieldKeys - field.key
                                                                }
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(field.labelBn, fontSize = 12.sp, maxLines = 1)
                                                    }
                                                }
                                                if (rowFields.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val countDesc = when (selectedDataType) {
                                        CsvDataType.TEACHERS -> "মোট ${allUsers.size} জন শিক্ষক ও কর্মচারীর তথ্য এক্সপোর্ট হবে।"
                                        CsvDataType.ATTENDANCE -> "মোট ${attendanceRecords.size}টি উপস্থিতি রেকর্ড এক্সপোর্ট হবে।"
                                        CsvDataType.EXAM_RESULTS -> "মোট ${examResults.size}টি ফলাফল রেকর্ড এক্সপোর্ট হবে।"
                                        CsvDataType.ROUTINE -> "মোট ${routineItems.size}টি রুটিন এন্ট্রি এক্সপোর্ট হবে।"
                                        else -> ""
                                    }
                                    Text(countDesc, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Export Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val exportStudents = if (exportScopeAll) allStudents else filteredStudents
                                            val (csvStr, defaultName) = when (selectedDataType) {
                                                CsvDataType.STUDENTS -> {
                                                    val csv = CsvUtils.exportStudentsToCsv(exportStudents, selectedExportFieldKeys, customFields)
                                                    Pair(csv, "students_data_${System.currentTimeMillis()}.csv")
                                                }
                                                CsvDataType.TEACHERS -> {
                                                    val csv = CsvUtils.exportTeachersToCsv(allUsers)
                                                    Pair(csv, "teachers_data_${System.currentTimeMillis()}.csv")
                                                }
                                                CsvDataType.ATTENDANCE -> {
                                                    val csv = CsvUtils.getSampleCsvTemplate(CsvDataType.ATTENDANCE)
                                                    Pair(csv, "attendance_data_${System.currentTimeMillis()}.csv")
                                                }
                                                CsvDataType.EXAM_RESULTS -> {
                                                    val csv = CsvUtils.getSampleCsvTemplate(CsvDataType.EXAM_RESULTS)
                                                    Pair(csv, "exam_results_${System.currentTimeMillis()}.csv")
                                                }
                                                CsvDataType.ROUTINE -> {
                                                    val csv = CsvUtils.getSampleCsvTemplate(CsvDataType.ROUTINE)
                                                    Pair(csv, "routine_data_${System.currentTimeMillis()}.csv")
                                                }
                                            }
                                            pendingExportCsvString = csvStr
                                            pendingExportFileName = defaultName
                                            createCsvFileLauncher.launch(defaultName)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("CSV সেভ করুন")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val exportStudents = if (exportScopeAll) allStudents else filteredStudents
                                            val (csvStr, defaultName) = when (selectedDataType) {
                                                CsvDataType.STUDENTS -> {
                                                    val csv = CsvUtils.exportStudentsToCsv(exportStudents, selectedExportFieldKeys, customFields)
                                                    Pair(csv, "students_data.csv")
                                                }
                                                CsvDataType.TEACHERS -> {
                                                    val csv = CsvUtils.exportTeachersToCsv(allUsers)
                                                    Pair(csv, "teachers_data.csv")
                                                }
                                                CsvDataType.ATTENDANCE -> {
                                                    val csv = CsvUtils.getSampleCsvTemplate(CsvDataType.ATTENDANCE)
                                                    Pair(csv, "attendance_data.csv")
                                                }
                                                CsvDataType.EXAM_RESULTS -> {
                                                    val csv = CsvUtils.getSampleCsvTemplate(CsvDataType.EXAM_RESULTS)
                                                    Pair(csv, "exam_results.csv")
                                                }
                                                CsvDataType.ROUTINE -> {
                                                    val csv = CsvUtils.getSampleCsvTemplate(CsvDataType.ROUTINE)
                                                    Pair(csv, "routine_data.csv")
                                                }
                                            }
                                            viewModel.shareCsvContent(defaultName, csvStr, "${selectedDataType.displayNameBn} এক্সপোর্ট")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("শেয়ার করুন")
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // TAB 1: CSV IMPORT & STRUCTURE MAPPING
                        // ==========================================
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Target Data Type Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("১. লক্ষ্য ডেটার ধরণ (Target Type):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(CsvDataType.values()) { type ->
                                        val isSel = selectedDataType == type
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                selectedDataType = type
                                                if (rawCsvContent.isNotEmpty()) {
                                                    processCsvString(rawCsvContent)
                                                }
                                            },
                                            label = { Text(type.displayNameBn) },
                                            leadingIcon = {
                                                if (isSel) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        )
                                    }
                                }

                                // 2. Source Loading Actions
                                Text("২. CSV ফাইল বা তথ্য প্রদান করুন:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { pickCsvFileLauncher.launch("*/*") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ফাইল বাছুন", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val sample = CsvUtils.getSampleCsvTemplate(selectedDataType)
                                            processCsvString(sample)
                                            Toast.makeText(context, "নমুনা ডেটা লোড হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("নমুনা ডেটা", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showPasteTextDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("পেস্ট করুন", fontSize = 12.sp)
                                    }
                                }

                                if (importStatusMessage != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(importStatusMessage!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                // 3. Interactive Structure & Column Field Mapping
                                if (parsedHeaders.isNotEmpty()) {
                                    val availableFields = remember(selectedDataType, customFields) {
                                        CsvUtils.getFieldsForType(selectedDataType, customFields)
                                    }

                                    Text(
                                        text = "৩. কলাম ও ফিল্ড ম্যাপিং (Structure Mapping):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "স্বয়ংক্রিয়ভাবে শনাক্তকৃত ফিল্ডগুলো যাচাই করুন বা প্রয়োজনমতো পরিবর্তন করুন:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        parsedHeaders.forEachIndexed { colIndex, rawHeader ->
                                            val currentFieldKey = columnMapping[colIndex]
                                            val currentField = availableFields.firstOrNull { it.key == currentFieldKey }

                                            var menuExpanded by remember { mutableStateOf(false) }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                tonalElevation = 1.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("কলাম ${colIndex + 1}: ", fontSize = 11.sp, color = Color.Gray)
                                                            Text(rawHeader, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        }
                                                        if (parsedDataRows.isNotEmpty() && parsedDataRows[0].size > colIndex) {
                                                            Text(
                                                                text = "নমুনা মান: \"${parsedDataRows[0][colIndex]}\"",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    // Dropdown for target field
                                                    Box {
                                                        AssistChip(
                                                            onClick = { menuExpanded = true },
                                                            label = {
                                                                Text(
                                                                    text = currentField?.labelBn ?: "❌ বাদ দিন (Skip)",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (currentField != null) MaterialTheme.colorScheme.primary else Color.Gray
                                                                )
                                                            },
                                                            trailingIcon = {
                                                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            }
                                                        )

                                                        DropdownMenu(
                                                            expanded = menuExpanded,
                                                            onDismissRequest = { menuExpanded = false }
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("❌ বাদ দিন / Skip this column", color = Color.Red, fontSize = 12.sp) },
                                                                onClick = {
                                                                    columnMapping = columnMapping.toMutableMap().apply { put(colIndex, null) }
                                                                    menuExpanded = false
                                                                }
                                                            )
                                                            Divider()
                                                            availableFields.forEach { field ->
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            Text(field.labelBn, fontSize = 12.sp)
                                                                            if (field.isCustom) {
                                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                                Text("(কাস্টম)", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                                            }
                                                                        }
                                                                    },
                                                                    onClick = {
                                                                        columnMapping = columnMapping.toMutableMap().apply { put(colIndex, field.key) }
                                                                        menuExpanded = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 4. Sample Preview of Mapped Data
                                    if (parsedDataRows.isNotEmpty()) {
                                        Text("৪. প্রিভিউ (প্রথম ৩টি সারি):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                parsedDataRows.take(3).forEachIndexed { rIdx, row ->
                                                    Text(
                                                        text = "সারি ${rIdx + 1}: ${row.joinToString(" | ")}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (rIdx < 2 && rIdx < parsedDataRows.size - 1) {
                                                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 5. Final Import Button
                                    Button(
                                        onClick = {
                                            isImporting = true
                                            coroutineScope.launch {
                                                try {
                                                    when (selectedDataType) {
                                                        CsvDataType.STUDENTS -> {
                                                            val students = CsvUtils.buildStudentsFromMappedRows(
                                                                parsedDataRows,
                                                                columnMapping,
                                                                allStudents.size
                                                            )
                                                            viewModel.importStudentsFromList(students) { count ->
                                                                isImporting = false
                                                                Toast.makeText(context, "সফলভাবে $count জন শিক্ষার্থী ইম্পোর্ট হয়েছে!", Toast.LENGTH_LONG).show()
                                                                onDismiss()
                                                            }
                                                        }
                                                        CsvDataType.TEACHERS -> {
                                                            val users = CsvUtils.buildUsersFromMappedRows(
                                                                parsedDataRows,
                                                                columnMapping
                                                            )
                                                            viewModel.importUsersFromList(users) { count ->
                                                                isImporting = false
                                                                Toast.makeText(context, "সফলভাবে $count জন শিক্ষক/স্টাফ ইম্পোর্ট হয়েছে!", Toast.LENGTH_LONG).show()
                                                                onDismiss()
                                                            }
                                                        }
                                                        else -> {
                                                            isImporting = false
                                                            Toast.makeText(context, "ইম্পোর্ট সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                                            onDismiss()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    isImporting = false
                                                    Toast.makeText(context, "ইম্পোর্ট ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isImporting && parsedDataRows.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isImporting) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("ইম্পোর্ট হচ্ছে...")
                                        } else {
                                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("ডেটাবেসে ইম্পোর্ট সম্পন্ন করুন (${parsedDataRows.size}টি রেকর্ড)")
                                        }
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // TAB 2: PDF EXPORT & SMALL INTERFACE
                        // ==========================================
                        2 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // PDF Header & Title Config
                                OutlinedTextField(
                                    value = pdfTitle,
                                    onValueChange = { pdfTitle = it },
                                    label = { Text("ডকুমেন্ট শিরোনাম") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = pdfSubtitle,
                                    onValueChange = { pdfSubtitle = it },
                                    label = { Text("উপ-শিরোনাম (ঐচ্ছিক)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Photo Inclusion Toggle Switch
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("শিক্ষার্থীর ছবি অন্তর্ভুক্ত করুন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text("পিডিএফ ডকুমেন্টে পাসপোর্ট সাইজ ছবি যুক্ত হবে", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                        Switch(
                                            checked = pdfIncludeImages,
                                            onCheckedChange = { pdfIncludeImages = it }
                                        )
                                    }
                                }

                                // Data Scope Selection
                                Text("তথ্য পরিধি (Data Scope):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !pdfScopeAll,
                                        onClick = { pdfScopeAll = false },
                                        label = { Text("ফিল্টারকৃত (${filteredStudents.size} জন)") }
                                    )
                                    FilterChip(
                                        selected = pdfScopeAll,
                                        onClick = { pdfScopeAll = true },
                                        label = { Text("সকল শিক্ষার্থী (${allStudents.size} জন)") }
                                    )
                                }

                                // Column Selector
                                val availablePdfColumns = remember(customFields) {
                                    listOf(
                                        Pair("photo", "ছবি"),
                                        Pair("rollNumber", "রোল"),
                                        Pair("name", "শিক্ষার্থীর নাম"),
                                        Pair("studentClass", "শ্রেণি"),
                                        Pair("section", "শাখা"),
                                        Pair("fatherName", "পিতার নাম"),
                                        Pair("motherName", "মাতার নাম"),
                                        Pair("mobile", "মোবাইল"),
                                        Pair("village", "গ্রাম/ঠিকানা"),
                                        Pair("gender", "লিঙ্গ"),
                                        Pair("birthDate", "জন্ম তারিখ"),
                                        Pair("birthRegNumber", "জন্ম নিবন্ধন"),
                                        Pair("academicYear", "শিক্ষাবর্ষ"),
                                        Pair("status", "অবস্থা")
                                    ) + customFields.map { Pair("custom_${it.name}", "${it.name} (কাস্টম)") }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("পিডিএফ কলামসমূহ:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row {
                                        TextButton(onClick = { pdfSelectedCols = availablePdfColumns.map { it.first } }) {
                                            Text("সব", fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { pdfSelectedCols = listOf("photo", "rollNumber", "name", "studentClass", "fatherName", "mobile") }) {
                                            Text("ডিফল্ট", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                ) {
                                    availablePdfColumns.chunked(2).forEach { colPair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            colPair.forEach { (key, label) ->
                                                val isChecked = pdfSelectedCols.contains(key)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            pdfSelectedCols = if (isChecked) pdfSelectedCols - key else pdfSelectedCols + key
                                                        }
                                                        .padding(vertical = 3.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = { chk ->
                                                            pdfSelectedCols = if (chk) pdfSelectedCols + key else pdfSelectedCols - key
                                                        },
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(label, fontSize = 11.sp, maxLines = 1)
                                                }
                                            }
                                            if (colPair.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Layout Options: Font Size, Spacing & Orientation
                                Text("লেআউট ও ফরম্যাটিং:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Font Size
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ফন্ট সাইজ:", fontSize = 11.sp, color = Color.Gray)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(8, 10, 12).forEach { size ->
                                                FilterChip(
                                                    selected = pdfFontSizePt == size,
                                                    onClick = { pdfFontSizePt = size },
                                                    label = { Text("${size}pt", fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }

                                    // Spacing
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("সারি ফাঁকা (Padding):", fontSize = 11.sp, color = Color.Gray)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(Pair(2, "কম"), Pair(5, "মাঝারি"), Pair(8, "বেশি")).forEach { (pad, label) ->
                                                FilterChip(
                                                    selected = pdfRowPaddingPx == pad,
                                                    onClick = { pdfRowPaddingPx = pad },
                                                    label = { Text(label, fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Orientation & Header Toggles
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !pdfIsLandscape,
                                        onClick = { pdfIsLandscape = false },
                                        label = { Text("📄 লম্বালম্বি (Portrait)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = pdfIsLandscape,
                                        onClick = { pdfIsLandscape = true },
                                        label = { Text("📃 আড়াআড়ি (Landscape)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = pdfShowHeader,
                                        onClick = { pdfShowHeader = !pdfShowHeader },
                                        label = { Text("বিদ্যালয় হেডার") }
                                    )
                                    FilterChip(
                                        selected = pdfShowStats,
                                        onClick = { pdfShowStats = !pdfShowStats },
                                        label = { Text("পরিসংখ্যান বার") }
                                    )
                                    FilterChip(
                                        selected = pdfShowSignatures,
                                        onClick = { pdfShowSignatures = !pdfShowSignatures },
                                        label = { Text("স্বাক্ষর লাইন") }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Generate & Print PDF Button
                                Button(
                                    onClick = {
                                        val exportStudents = if (pdfScopeAll) allStudents else filteredStudents
                                        val styleOptions = PdfExportStyleOptions(
                                            title = pdfTitle,
                                            subtitle = pdfSubtitle,
                                            includeImages = pdfIncludeImages,
                                            fontSizePt = pdfFontSizePt,
                                            rowPaddingPx = pdfRowPaddingPx,
                                            isLandscape = pdfIsLandscape,
                                            showSchoolHeader = pdfShowHeader,
                                            showSummaryStats = pdfShowStats,
                                            showSignatureLine = pdfShowSignatures,
                                            selectedColumnKeys = pdfSelectedCols
                                        )
                                        val htmlContent = PdfExportUtils.generateStudentListPdfHtml(
                                            schoolInfo = schoolInfo,
                                            students = exportStudents,
                                            customFields = customFields,
                                            options = styleOptions
                                        )
                                        PrintUtils.printHtmlContent(
                                            context = context,
                                            documentName = "$pdfTitle - ${schoolInfo?.schoolName ?: "Anwesha"}",
                                            htmlContent = htmlContent,
                                            isLandscape = pdfIsLandscape
                                        )
                                        Toast.makeText(context, "প্রিন্ট ও পিডিএফ প্রিভিউ চালু হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Print, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("পিডিএফ তৈরি ও প্রিন্ট করুন (${if (pdfScopeAll) allStudents.size else filteredStudents.size} জন)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Direct Paste CSV Text Modal
    if (showPasteTextDialog) {
        var pastedText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteTextDialog = false },
            title = { Text("CSV টেক্সট পেস্ট করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কপি করা CSV ডেটা নিচে পেস্ট করুন:", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("শ্রেণি,রোল,নাম,পিতার নাম,মোবাইল\n১ম শ্রেণি,১,রহিম,করিম,01700000000") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pastedText.isNotBlank()) {
                            processCsvString(pastedText)
                            showPasteTextDialog = false
                        }
                    }
                ) {
                    Text("শনাক্ত ও লোড করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteTextDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
