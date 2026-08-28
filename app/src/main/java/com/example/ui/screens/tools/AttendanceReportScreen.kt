package com.example.ui.screens.tools

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.AttendanceEntity
import com.example.util.BanglaUtils
import com.example.util.ClassPreset
import com.example.util.PrintUtils
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allStudents by viewModel.allStudents.collectAsState()
    val allAttendance by viewModel.attendanceRecords.collectAsState()
    val schoolInfo by viewModel.schoolInfo.collectAsState()

    // Class Preset state (PRESET_DIGIT: ১ম, ২য়... or PRESET_WORD: প্রথম, দ্বিতীয়...)
    var currentPreset by remember { mutableStateOf(ClassPreset.getSavedPreset(context)) }

    var selectedTab by remember { mutableStateOf(0) } // 0: দৈনিক হাজিরা, 1: মাসিক রিপোর্ট, 2: ইতিহাস
    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    // Selected Month & Year for Monthly Report
    val currentCal = Calendar.getInstance()
    var selectedMonthIndex by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) } // 0-11
    var selectedYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }

    // State for Class-by-Class Attendance Entry Dialog
    var showAddAttendanceDialog by remember { mutableStateOf(false) }
    var initialDialogClassIndex by remember { mutableStateOf(0) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }

    // Back handler
    BackHandler(enabled = true) {
        if (showAddAttendanceDialog) {
            showAddAttendanceDialog = false
        } else if (showDeleteConfirmDialog != null) {
            showDeleteConfirmDialog = null
        } else {
            onNavigateBack()
        }
    }

    // Standard normalized class names based on active preset
    val standardClassNames: List<String> = remember(currentPreset) {
        currentPreset.classNames
    }

    // Pre-calculate latest enrolled counts from allStudents, cleanly normalized to standard classes
    val enrolledCountsByClass: Map<String, Pair<Int, Int>> = remember(allStudents, standardClassNames, currentPreset) {
        val activeStudents = allStudents.filter { it.status == "Current" || it.status == "নিয়মিত" || it.status.isBlank() }
        
        standardClassNames.associateWith { stdClass ->
            val matchingStudents = activeStudents.filter { student ->
                val normalized = ClassPreset.convertClassName(student.studentClass, currentPreset)
                normalized.trim() == stdClass.trim()
            }
            val boys = matchingStudents.count { it.gender.trim() in listOf("ছাত্র", "পুরুষ", "Male", "boy", "ছেলে") }
            val girls = matchingStudents.count { it.gender.trim() in listOf("ছাত্রী", "মহিলা", "Female", "girl", "মেয়ে") }
            val other = matchingStudents.size - (boys + girls)
            val finalBoys = boys + (other / 2)
            val finalGirls = girls + (other - other / 2)
            Pair(finalBoys, finalGirls)
        }
    }

    // Daily input state mapped by className: Pair(presentBoysStr, presentGirlsStr)
    val dailyInputMap = remember(selectedDate, allAttendance, standardClassNames) {
        val recordsForDate = allAttendance.filter { it.date == selectedDate }
        val map = mutableStateMapOf<String, Pair<String, String>>()
        standardClassNames.forEach { cls ->
            val record = recordsForDate.find { 
                ClassPreset.convertClassName(it.className, currentPreset).trim() == cls.trim() 
            }
            if (record != null) {
                map[cls] = Pair(record.presentBoys.toString(), record.presentGirls.toString())
            } else {
                map[cls] = Pair("", "")
            }
        }
        map
    }

    // Check if selected date has saved records
    val isDateSaved = remember(selectedDate, allAttendance) {
        allAttendance.any { it.date == selectedDate }
    }

    // Date Picker Dialog setup
    fun openDatePicker() {
        val parts = selectedDate.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: currentCal.get(Calendar.YEAR)
        val m = (parts.getOrNull(1)?.toIntOrNull() ?: (currentCal.get(Calendar.MONTH) + 1)) - 1
        val d = parts.getOrNull(2)?.toIntOrNull() ?: currentCal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, yr, mon, day ->
            val formatted = String.format(Locale.US, "%04d-%02d-%02d", yr, mon + 1, day)
            selectedDate = formatted
        }, y, m, d).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "হাজিরা ও মাসিক রিপোর্ট",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = schoolInfo?.schoolName ?: "প্রাথমিক বিদ্যালয়",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পিছনে")
                    }
                },
                actions = {
                    // Class format switch button
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "শ্রেণির ফরম্যাট পরিবর্তন")
                    }
                    IconButton(
                        onClick = {
                            val helpMsg = "• '+ Add Attendance' বাটনে চাপ দিয়ে প্রতিটি শ্রেণি আলাদা আলাদা এন্ট্রি করুন।\n• ভর্তি সংখ্যার বেশি এন্ট্রি প্রতিরোধ করা হয়েছে।\n• দিনশেষে সামারি কার্ড ও আধুনিক মাসিক রিপোর্ট সরাসরি শেয়ার ও প্রিন্ট করা যায়।"
                            Toast.makeText(context, helpMsg, Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "সাহায্য")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Modern Minimal Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = { HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("দৈনিক হাজিরা", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("মাসিক রিপোর্ট", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("হাজিরা লগ", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> ModernDailyAttendanceView(
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    onOpenDatePicker = { openDatePicker() },
                    standardClassNames = standardClassNames,
                    enrolledCountsByClass = enrolledCountsByClass,
                    dailyInputMap = dailyInputMap,
                    isDateSaved = isDateSaved,
                    onOpenAddAttendance = { classIndex ->
                        initialDialogClassIndex = classIndex
                        showAddAttendanceDialog = true
                    },
                    onDeleteDate = {
                        showDeleteConfirmDialog = selectedDate
                    },
                    schoolName = schoolInfo?.schoolName ?: "প্রাথমিক বিদ্যালয়"
                )
                1 -> ModernMonthlyReportView(
                    selectedMonth = selectedMonthIndex,
                    selectedYear = selectedYear,
                    onMonthYearChange = { m, y ->
                        selectedMonthIndex = m
                        selectedYear = y
                    },
                    standardClassNames = standardClassNames,
                    enrolledCountsByClass = enrolledCountsByClass,
                    allAttendance = allAttendance,
                    currentPreset = currentPreset,
                    schoolName = schoolInfo?.schoolName ?: "প্রাথমিক বিদ্যালয়"
                )
                2 -> ModernAttendanceHistoryView(
                    allAttendance = allAttendance,
                    onSelectDate = { dt ->
                        selectedDate = dt
                        selectedTab = 0
                    },
                    onDeleteDate = { dt ->
                        showDeleteConfirmDialog = dt
                    }
                )
            }
        }
    }

    // Step-by-Step Class-by-Class Attendance Dialog
    if (showAddAttendanceDialog) {
        ClassByClassAttendanceDialog(
            selectedDate = selectedDate,
            standardClassNames = standardClassNames,
            enrolledCountsByClass = enrolledCountsByClass,
            initialClassIndex = initialDialogClassIndex,
            dailyInputMap = dailyInputMap,
            onDismiss = { showAddAttendanceDialog = false },
            onSaveAll = {
                val records = standardClassNames.map { cls ->
                    val (eB, eG) = enrolledCountsByClass[cls] ?: Pair(0, 0)
                    val (pBStr, pGStr) = dailyInputMap[cls] ?: Pair("0", "0")
                    val pB = pBStr.toIntOrNull()?.coerceIn(0, eB) ?: 0
                    val pG = pGStr.toIntOrNull()?.coerceIn(0, eG) ?: 0
                    val aB = (eB - pB).coerceAtLeast(0)
                    val aG = (eG - pG).coerceAtLeast(0)

                    AttendanceEntity(
                        id = "${selectedDate}_$cls",
                        date = selectedDate,
                        className = cls,
                        presentBoys = pB,
                        presentGirls = pG,
                        absentBoys = aB,
                        absentGirls = aG,
                        totalBoys = eB,
                        totalGirls = eG,
                        notes = null
                    )
                }
                viewModel.saveDailyAttendanceList(selectedDate, records)
                showAddAttendanceDialog = false
                Toast.makeText(context, "${BanglaUtils.formatBanglaDate(selectedDate)} এর হাজিরা সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Class Format Preset Switcher Dialog
    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("শ্রেণির নাম ফরম্যাট নির্ধারণ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("আপনার বিদ্যালয়ের জন্য পছন্দের ফরম্যাট নির্বাচন করুন:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    // Format 1: Digit style
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentPreset == ClassPreset.PRESET_DIGIT) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (currentPreset == ClassPreset.PRESET_DIGIT) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentPreset = ClassPreset.PRESET_DIGIT
                                ClassPreset.savePreset(context, ClassPreset.PRESET_DIGIT)
                                showPresetDialog = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("১. সংখ্যার ফরম্যাট (ডিজিটাল)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("প্রাক-প্রাথমিক ৪+, প্রাক-প্রাথমিক ৫+, ১ম, ২য়, ৩য়, ৪র্থ, ৫ম", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Format 2: Word style
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentPreset == ClassPreset.PRESET_WORD) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (currentPreset == ClassPreset.PRESET_WORD) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentPreset = ClassPreset.PRESET_WORD
                                ClassPreset.savePreset(context, ClassPreset.PRESET_WORD)
                                showPresetDialog = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("২. শব্দের ফরম্যাট (বাংলা বানান)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("প্রাক-প্রাথমিক ৪+, প্রাক-প্রাথমিক ৫+, প্রথম, দ্বিতীয়, তৃতীয়, চতুর্থ, পঞ্চম", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog != null) {
        val dtToDelete = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("হাজিরা মুছে ফেলতে চান?") },
            text = { Text("${BanglaUtils.formatBanglaDate(dtToDelete)} তারিখের সমস্ত শ্রেণির হাজিরা মুছে ফেলা হবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAttendanceForDate(dtToDelete)
                        showDeleteConfirmDialog = null
                        Toast.makeText(context, "রেকর্ড মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 1. MODERN MINIMAL DAILY ATTENDANCE VIEW
// -------------------------------------------------------------
@Composable
private fun ModernDailyAttendanceView(
    selectedDate: String,
    onDateChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    standardClassNames: List<String>,
    enrolledCountsByClass: Map<String, Pair<Int, Int>>,
    dailyInputMap: MutableMap<String, Pair<String, String>>,
    isDateSaved: Boolean,
    onOpenAddAttendance: (Int) -> Unit,
    onDeleteDate: () -> Unit,
    schoolName: String
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun shiftDate(days: Int) {
        try {
            val d = sdf.parse(selectedDate) ?: Date()
            val cal = Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, days) }
            onDateChange(sdf.format(cal.time))
        } catch (e: Exception) {}
    }

    val dayOfWeekBangla = remember(selectedDate) {
        try {
            val d = sdf.parse(selectedDate) ?: Date()
            val cal = Calendar.getInstance().apply { time = d }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> "শনিবার"
                Calendar.SUNDAY -> "রবিবার"
                Calendar.MONDAY -> "সোমবার"
                Calendar.TUESDAY -> "মঙ্গলবার"
                Calendar.WEDNESDAY -> "বুধবার"
                Calendar.THURSDAY -> "বৃহস্পতিবার"
                Calendar.FRIDAY -> "শুক্রবার (ছুটি)"
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // Compute live daily summary
    val dailySummary = remember(dailyInputMap, enrolledCountsByClass, standardClassNames) {
        var totalEnrolledBoys = 0
        var totalEnrolledGirls = 0
        var totalPresentBoys = 0
        var totalPresentGirls = 0

        standardClassNames.forEach { cls ->
            val (eBoys, eGirls) = enrolledCountsByClass[cls] ?: Pair(0, 0)
            val (pBoysStr, pGirlsStr) = dailyInputMap[cls] ?: Pair("", "")
            val pBoys = pBoysStr.toIntOrNull()?.coerceIn(0, eBoys) ?: 0
            val pGirls = pGirlsStr.toIntOrNull()?.coerceIn(0, eGirls) ?: 0

            totalEnrolledBoys += eBoys
            totalEnrolledGirls += eGirls
            totalPresentBoys += pBoys
            totalPresentGirls += pGirls
        }

        val totalEnrolled = totalEnrolledBoys + totalEnrolledGirls
        val totalPresent = totalPresentBoys + totalPresentGirls
        val totalAbsent = (totalEnrolled - totalPresent).coerceAtLeast(0)
        val rate = if (totalEnrolled > 0) (totalPresent.toDouble() / totalEnrolled) * 100.0 else 0.0

        DailySummaryData(
            totalEnrolled = totalEnrolled,
            totalEnrolledBoys = totalEnrolledBoys,
            totalEnrolledGirls = totalEnrolledGirls,
            totalPresent = totalPresent,
            totalPresentBoys = totalPresentBoys,
            totalPresentGirls = totalPresentGirls,
            totalAbsent = totalAbsent,
            ratePercent = rate
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("modern_daily_attendance_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date Switcher Header
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { shiftDate(-1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পূর্ববর্তী দিন", modifier = Modifier.size(18.dp))
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenDatePicker() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = BanglaUtils.formatBanglaDate(selectedDate),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = dayOfWeekBangla,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (dayOfWeekBangla.contains("ছুটি")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { shiftDate(1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "পরবর্তী দিন", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Minimal Modern Day Summary Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(0.5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card Top: Title & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "দৈনিক হাজিরা সামারি",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDateSaved) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color(0xFFE65100).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isDateSaved) "✓ সংরক্ষিত" else "খসড়া",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDateSaved) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Main Metric Highlights: Rate % and Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Rate Percentage
                        Column {
                            Text("উপস্থিতির হার", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", dailySummary.ratePercent))}%",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = if (dailySummary.ratePercent >= 80) Color(0xFF2E7D32) else if (dailySummary.ratePercent >= 60) Color(0xFFF57F17) else Color(0xFFC62828)
                            )
                        }

                        // Compact Stat Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModernKpiBadge(
                                label = "ভর্তি",
                                value = BanglaUtils.toBanglaDigits(dailySummary.totalEnrolled),
                                sub = "ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledBoys)} | ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledGirls)}"
                            )
                            ModernKpiBadge(
                                label = "উপস্থিত",
                                value = BanglaUtils.toBanglaDigits(dailySummary.totalPresent),
                                valueColor = Color(0xFF2E7D32),
                                sub = "ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalPresentBoys)} | ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalPresentGirls)}"
                            )
                            ModernKpiBadge(
                                label = "অনুপস্থিত",
                                value = BanglaUtils.toBanglaDigits(dailySummary.totalAbsent),
                                valueColor = Color(0xFFC62828)
                            )
                        }
                    }

                    // Progress bar
                    val progressFloat = (dailySummary.ratePercent / 100.0).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (dailySummary.ratePercent >= 80) Color(0xFF2E7D32) else Color(0xFF0288D1),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    // Quick Share and Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDateSaved) {
                            TextButton(
                                onClick = onDeleteDate,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("মুছুন", fontSize = 11.sp)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        TextButton(
                            onClick = {
                                val text = buildString {
                                    appendLine("📅 দৈনিক হাজিরা প্রতিবেদন")
                                    appendLine("বিদ্যালয়: $schoolName")
                                    appendLine("তারিখ: ${BanglaUtils.formatBanglaDate(selectedDate)} ($dayOfWeekBangla)")
                                    appendLine("----------------------------------")
                                    appendLine("মোট ভর্তি: ${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolled)} জন (ছাত্র ${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledBoys)}, ছাত্রী ${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledGirls)})")
                                    appendLine("মোট উপস্থিতি: ${BanglaUtils.toBanglaDigits(dailySummary.totalPresent)} জন (ছাত্র ${BanglaUtils.toBanglaDigits(dailySummary.totalPresentBoys)}, ছাত্রী ${BanglaUtils.toBanglaDigits(dailySummary.totalPresentGirls)})")
                                    appendLine("মোট অনুপস্থিত: ${BanglaUtils.toBanglaDigits(dailySummary.totalAbsent)} জন")
                                    appendLine("উপস্থিতির গড় হার: ${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", dailySummary.ratePercent))}%")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "হাজিরা সামারি পাঠান"))
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("সামারি কপি / শেয়ার", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // PROMINENT "+ Add Attendance" Action Button
        item {
            Button(
                onClick = { onOpenAddAttendance(0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_attendance_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isDateSaved) "+ Edit / Update Attendance" else "+ Add Attendance (হাজিরা এন্ট্রি)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Section: শ্রেণিভিত্তিক অবস্থা
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "শ্রেণিভিত্তিক উপস্থিতির অবস্থা",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "ট্যাপ করে এডিট করুন",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Compact Class Breakdown Cards
        itemsIndexed(standardClassNames) { index, className ->
            val (enrolledBoys, enrolledGirls) = enrolledCountsByClass[className] ?: Pair(0, 0)
            val currentPair = dailyInputMap[className] ?: Pair("", "")
            val presentBoysStr = currentPair.first
            val presentGirlsStr = currentPair.second

            val pBoysInt = presentBoysStr.toIntOrNull() ?: 0
            val pGirlsInt = presentGirlsStr.toIntOrNull() ?: 0
            val classEnrolledTotal = enrolledBoys + enrolledGirls
            val classPresentTotal = pBoysInt + pGirlsInt
            val classRate = if (classEnrolledTotal > 0) (classPresentTotal.toDouble() / classEnrolledTotal) * 100.0 else 0.0
            val hasInput = presentBoysStr.isNotBlank() || presentGirlsStr.isNotBlank()

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasInput) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAddAttendance(index) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Class Name & Details
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = className,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasInput) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                        Text(
                            text = "ভর্তি: ${BanglaUtils.toBanglaDigits(classEnrolledTotal)} (ছাত্র ${BanglaUtils.toBanglaDigits(enrolledBoys)}, ছাত্রী ${BanglaUtils.toBanglaDigits(enrolledGirls)})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Right: Present Count & Percentage Pill
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasInput) {
                            Text(
                                text = "উপস্থিত: ${BanglaUtils.toBanglaDigits(classPresentTotal)} / ${BanglaUtils.toBanglaDigits(classEnrolledTotal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (classRate >= 80) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFF57F17).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", classRate))}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (classRate >= 80) Color(0xFF2E7D32) else Color(0xFFF57F17),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onOpenAddAttendance(index) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("এন্ট্রি করুন", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// 2. STEP-BY-STEP CLASS-BY-CLASS ATTENDANCE DIALOG (EACH CLASS INDIVIDUALLY)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassByClassAttendanceDialog(
    selectedDate: String,
    standardClassNames: List<String>,
    enrolledCountsByClass: Map<String, Pair<Int, Int>>,
    initialClassIndex: Int,
    dailyInputMap: MutableMap<String, Pair<String, String>>,
    onDismiss: () -> Unit,
    onSaveAll: () -> Unit
) {
    var activeIndex by remember { mutableStateOf(initialClassIndex.coerceIn(0, standardClassNames.size - 1)) }
    val activeClass = standardClassNames[activeIndex]

    val (enrolledBoys, enrolledGirls) = enrolledCountsByClass[activeClass] ?: Pair(0, 0)
    val totalEnrolled = enrolledBoys + enrolledGirls

    val currentPair = dailyInputMap[activeClass] ?: Pair("", "")
    val presentBoysStr = currentPair.first
    val presentGirlsStr = currentPair.second

    val pBoys = presentBoysStr.toIntOrNull()?.coerceIn(0, enrolledBoys) ?: 0
    val pGirls = presentGirlsStr.toIntOrNull()?.coerceIn(0, enrolledGirls) ?: 0
    val totalPresent = pBoys + pGirls
    val totalAbsent = (totalEnrolled - totalPresent).coerceAtLeast(0)
    val classRate = if (totalEnrolled > 0) (totalPresent.toDouble() / totalEnrolled) * 100.0 else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Header: Date & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "হাজিরা এন্ট্রি",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = BanglaUtils.formatBanglaDate(selectedDate),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন", modifier = Modifier.size(18.dp))
                    }
                }

                // Horizontal Class Selector Stepper (Pills)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(standardClassNames) { idx, cls ->
                        val isSelected = idx == activeIndex
                        val hasData = dailyInputMap[cls]?.first?.isNotBlank() == true || dailyInputMap[cls]?.second?.isNotBlank() == true

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else if (hasData) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { activeIndex = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (hasData && !isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(10.dp))
                                }
                                Text(
                                    text = cls,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Active Class Card: Big Name, Progress, Quick 100% Button
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = activeClass,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "মোট ভর্তি: ${BanglaUtils.toBanglaDigits(totalEnrolled)} জন (ছাত্র ${BanglaUtils.toBanglaDigits(enrolledBoys)}, ছাত্রী ${BanglaUtils.toBanglaDigits(enrolledGirls)})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick Fill Button
                            FilledTonalButton(
                                onClick = {
                                    dailyInputMap[activeClass] = Pair(enrolledBoys.toString(), enrolledGirls.toString())
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("সবাই উপস্থিত", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Progress Dots indicating step
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "শ্রেণি ধাপ: ${BanglaUtils.toBanglaDigits(activeIndex + 1)} / ${BanglaUtils.toBanglaDigits(standardClassNames.size)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "অনুপস্থিত: ${BanglaUtils.toBanglaDigits(totalAbsent)} জন",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalAbsent > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                // Individual Boy & Girl Input Steppers
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. BOYS INPUT
                    ClassGenderInputRow(
                        label = "ছাত্র উপস্থিতি",
                        enrolled = enrolledBoys,
                        currentValueStr = presentBoysStr,
                        tintColor = Color(0xFF1565C0),
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() }
                            val num = filtered.toIntOrNull()
                            if (num == null) {
                                dailyInputMap[activeClass] = Pair("", presentGirlsStr)
                            } else {
                                val capped = if (enrolledBoys > 0) num.coerceAtMost(enrolledBoys) else num
                                dailyInputMap[activeClass] = Pair(capped.toString(), presentGirlsStr)
                            }
                        },
                        onIncrement = {
                            val cur = presentBoysStr.toIntOrNull() ?: 0
                            if (cur < enrolledBoys) {
                                dailyInputMap[activeClass] = Pair((cur + 1).toString(), presentGirlsStr)
                            }
                        },
                        onDecrement = {
                            val cur = presentBoysStr.toIntOrNull() ?: 0
                            if (cur > 0) {
                                dailyInputMap[activeClass] = Pair((cur - 1).toString(), presentGirlsStr)
                            }
                        }
                    )

                    // 2. GIRLS INPUT
                    ClassGenderInputRow(
                        label = "ছাত্রী উপস্থিতি",
                        enrolled = enrolledGirls,
                        currentValueStr = presentGirlsStr,
                        tintColor = Color(0xFFC2185B),
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() }
                            val num = filtered.toIntOrNull()
                            if (num == null) {
                                dailyInputMap[activeClass] = Pair(presentBoysStr, "")
                            } else {
                                val capped = if (enrolledGirls > 0) num.coerceAtMost(enrolledGirls) else num
                                dailyInputMap[activeClass] = Pair(presentBoysStr, capped.toString())
                            }
                        },
                        onIncrement = {
                            val cur = presentGirlsStr.toIntOrNull() ?: 0
                            if (cur < enrolledGirls) {
                                dailyInputMap[activeClass] = Pair(presentBoysStr, (cur + 1).toString())
                            }
                        },
                        onDecrement = {
                            val cur = presentGirlsStr.toIntOrNull() ?: 0
                            if (cur > 0) {
                                dailyInputMap[activeClass] = Pair(presentBoysStr, (cur - 1).toString())
                            }
                        }
                    )
                }

                // Live Class Rate Preview
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "মোট উপস্থিত: ${BanglaUtils.toBanglaDigits(totalPresent)} / ${BanglaUtils.toBanglaDigits(totalEnrolled)} জন",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "হার: ${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", classRate))}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (classRate >= 80) Color(0xFF2E7D32) else Color(0xFFF57F17)
                        )
                    }
                }

                // Navigation Controls (Previous, Next / Save & Finish)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Class Button
                    OutlinedButton(
                        onClick = {
                            if (activeIndex > 0) activeIndex--
                        },
                        enabled = activeIndex > 0,
                        modifier = Modifier.weight(0.7f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("পূর্ববর্তী", fontSize = 12.sp)
                    }

                    // Next Class or Save Button
                    if (activeIndex < standardClassNames.size - 1) {
                        Button(
                            onClick = { activeIndex++ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text("পরবর্তী শ্রেণি", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Button(
                            onClick = onSaveAll,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("সংরক্ষণ ও সমাপ্ত", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick "Save All Now" footer option
                if (activeIndex < standardClassNames.size - 1) {
                    TextButton(
                        onClick = onSaveAll,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("এখনই সব সেভ করুন", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// GENDER INPUT ROW COMPONENT WITH +/- STEPPERS
// -------------------------------------------------------------
@Composable
private fun ClassGenderInputRow(
    label: String,
    enrolled: Int,
    currentValueStr: String,
    tintColor: Color,
    onValueChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tintColor)
                Text("ভর্তি: ${BanglaUtils.toBanglaDigits(enrolled)} জন", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Decrement button
                FilledIconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // Number Input Field
                OutlinedTextField(
                    value = currentValueStr,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("0", fontSize = 13.sp, textAlign = TextAlign.Center) },
                    modifier = Modifier
                        .width(58.dp)
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                // Increment button
                FilledIconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = tintColor.copy(alpha = 0.15f))
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tintColor)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. MODERN MINIMAL MONTHLY REPORT VIEW (COMPACT & CLUTTER-FREE)
// -------------------------------------------------------------
@Composable
private fun ModernMonthlyReportView(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthYearChange: (Int, Int) -> Unit,
    standardClassNames: List<String>,
    enrolledCountsByClass: Map<String, Pair<Int, Int>>,
    allAttendance: List<AttendanceEntity>,
    currentPreset: ClassPreset,
    schoolName: String
) {
    val context = LocalContext.current
    val banglaMonths = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    var showMonthDropdown by remember { mutableStateOf(false) }
    var showYearDropdown by remember { mutableStateOf(false) }

    val monthPrefix = String.format(Locale.US, "%04d-%02d", selectedYear, selectedMonth + 1)
    val monthlyRecords = remember(allAttendance, monthPrefix) {
        allAttendance.filter { it.date.startsWith(monthPrefix) }
    }

    val uniqueWorkingDates = remember(monthlyRecords) {
        monthlyRecords.map { it.date }.distinct()
    }
    val effectiveWorkingDays = uniqueWorkingDates.size.coerceAtLeast(1)

    // Compute monthly aggregation per class
    val monthlyClassReports = remember(standardClassNames, monthlyRecords, enrolledCountsByClass, effectiveWorkingDays, currentPreset) {
        standardClassNames.map { cls ->
            val recordsForClass = monthlyRecords.filter { 
                ClassPreset.convertClassName(it.className, currentPreset).trim() == cls.trim() 
            }
            val (baseEB, baseEG) = enrolledCountsByClass[cls] ?: Pair(0, 0)

            val enrolledBoys = if (recordsForClass.isNotEmpty()) {
                recordsForClass.first().totalBoys.coerceAtLeast(baseEB)
            } else baseEB

            val enrolledGirls = if (recordsForClass.isNotEmpty()) {
                recordsForClass.first().totalGirls.coerceAtLeast(baseEG)
            } else baseEG

            val totalPresentBoys = recordsForClass.sumOf { it.presentBoys }
            val totalPresentGirls = recordsForClass.sumOf { it.presentGirls }

            val daysCount = if (uniqueWorkingDates.isNotEmpty()) effectiveWorkingDays else 1

            val avgPresentBoys = if (daysCount > 0) totalPresentBoys.toDouble() / daysCount else 0.0
            val avgPresentGirls = if (daysCount > 0) totalPresentGirls.toDouble() / daysCount else 0.0

            val rateBoys = if (enrolledBoys > 0 && daysCount > 0) (avgPresentBoys / enrolledBoys) * 100.0 else 0.0
            val rateGirls = if (enrolledGirls > 0 && daysCount > 0) (avgPresentGirls / enrolledGirls) * 100.0 else 0.0

            val totalEnrolled = enrolledBoys + enrolledGirls
            val totalPresent = totalPresentBoys + totalPresentGirls
            val avgPresentTotal = avgPresentBoys + avgPresentGirls
            val rateTotal = if (totalEnrolled > 0 && daysCount > 0) (avgPresentTotal / totalEnrolled) * 100.0 else 0.0

            ClassMonthlyReportData(
                className = cls,
                enrolledBoys = enrolledBoys,
                enrolledGirls = enrolledGirls,
                totalEnrolled = totalEnrolled,
                totalPresentBoys = totalPresentBoys,
                totalPresentGirls = totalPresentGirls,
                totalPresent = totalPresent,
                avgPresentBoys = avgPresentBoys,
                avgPresentGirls = avgPresentGirls,
                avgPresentTotal = avgPresentTotal,
                rateBoys = rateBoys,
                rateGirls = rateGirls,
                rateTotal = rateTotal
            )
        }
    }

    // Grand Totals
    val grandTotalReport = remember(monthlyClassReports, effectiveWorkingDays) {
        val enrolledBoys = monthlyClassReports.sumOf { it.enrolledBoys }
        val enrolledGirls = monthlyClassReports.sumOf { it.enrolledGirls }
        val totalEnrolled = enrolledBoys + enrolledGirls

        val totalPresentBoys = monthlyClassReports.sumOf { it.totalPresentBoys }
        val totalPresentGirls = monthlyClassReports.sumOf { it.totalPresentGirls }
        val totalPresent = totalPresentBoys + totalPresentGirls

        val avgPresentBoys = if (effectiveWorkingDays > 0) totalPresentBoys.toDouble() / effectiveWorkingDays else 0.0
        val avgPresentGirls = if (effectiveWorkingDays > 0) totalPresentGirls.toDouble() / effectiveWorkingDays else 0.0
        val avgPresentTotal = avgPresentBoys + avgPresentGirls

        val rateBoys = if (enrolledBoys > 0) (avgPresentBoys / enrolledBoys) * 100.0 else 0.0
        val rateGirls = if (enrolledGirls > 0) (avgPresentGirls / enrolledGirls) * 100.0 else 0.0
        val rateTotal = if (totalEnrolled > 0) (avgPresentTotal / totalEnrolled) * 100.0 else 0.0

        GrandTotalReportData(
            enrolledBoys = enrolledBoys,
            enrolledGirls = enrolledGirls,
            totalEnrolled = totalEnrolled,
            totalPresentBoys = totalPresentBoys,
            totalPresentGirls = totalPresentGirls,
            totalPresent = totalPresent,
            avgPresentBoys = avgPresentBoys,
            avgPresentGirls = avgPresentGirls,
            avgPresentTotal = avgPresentTotal,
            rateBoys = rateBoys,
            rateGirls = rateGirls,
            rateTotal = rateTotal
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("modern_monthly_report_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Month & Year Selector Header
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Month dropdown
                    Box {
                        FilledTonalButton(
                            onClick = { showMonthDropdown = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(banglaMonths[selectedMonth], fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showMonthDropdown,
                            onDismissRequest = { showMonthDropdown = false }
                        ) {
                            banglaMonths.forEachIndexed { idx, mName ->
                                DropdownMenuItem(
                                    text = { Text(mName, fontWeight = if (idx == selectedMonth) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        onMonthYearChange(idx, selectedYear)
                                        showMonthDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Year dropdown
                    Box {
                        OutlinedButton(
                            onClick = { showYearDropdown = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(BanglaUtils.toBanglaDigits(selectedYear), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showYearDropdown,
                            onDismissRequest = { showYearDropdown = false }
                        ) {
                            ((selectedYear - 2)..(selectedYear + 2)).forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text(BanglaUtils.toBanglaDigits(yr), fontWeight = if (yr == selectedYear) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        onMonthYearChange(selectedMonth, yr)
                                        showYearDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Export / Print Quick Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val text = buildString {
                                    appendLine("📊 মাসিক উপস্থিতি প্রতিবেদন")
                                    appendLine("বিদ্যালয়: $schoolName")
                                    appendLine("মাস: ${banglaMonths[selectedMonth]} ${BanglaUtils.toBanglaDigits(selectedYear)}")
                                    appendLine("কার্যদিবস: ${BanglaUtils.toBanglaDigits(uniqueWorkingDates.size)} দিন")
                                    appendLine("মোট ভর্তি: ${BanglaUtils.toBanglaDigits(grandTotalReport.totalEnrolled)} জন")
                                    appendLine("গড় উপস্থিতি: ${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.avgPresentTotal))} জন")
                                    appendLine("মাসিক গড় উপস্থিতির হার: ${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.rateTotal))}%")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "মাসিক রিপোর্ট শেয়ার করুন"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "শেয়ার", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                val htmlContent = generateMonthlyReportHtml(
                                    schoolName = schoolName,
                                    monthName = banglaMonths[selectedMonth],
                                    year = selectedYear,
                                    workingDays = uniqueWorkingDates.size,
                                    classReports = monthlyClassReports,
                                    grandTotal = grandTotalReport
                                )
                                PrintUtils.printHtmlContent(
                                    context = context,
                                    documentName = "মাসিক_উপস্থিতি_রিপোর্ট_${selectedMonth + 1}_$selectedYear",
                                    htmlContent = htmlContent
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "প্রিন্ট", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Minimal KPI Summary Cards Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModernMonthlySummaryCard(
                    title = "মোট কার্যদিবস",
                    value = "${BanglaUtils.toBanglaDigits(uniqueWorkingDates.size)} দিন",
                    modifier = Modifier.weight(1f)
                )
                ModernMonthlySummaryCard(
                    title = "মোট শিক্ষার্থী",
                    value = "${BanglaUtils.toBanglaDigits(grandTotalReport.totalEnrolled)} জন",
                    modifier = Modifier.weight(1f)
                )
                ModernMonthlySummaryCard(
                    title = "গড় উপস্থিতি",
                    value = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.avgPresentTotal))} জন",
                    modifier = Modifier.weight(1f)
                )
                ModernMonthlySummaryCard(
                    title = "মাসিক গড় হার",
                    value = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.rateTotal))}%",
                    valueColor = if (grandTotalReport.rateTotal >= 80) Color(0xFF2E7D32) else Color(0xFF1565C0),
                    modifier = Modifier.weight(1.1f)
                )
            }
        }

        // Clean, Compact Table Header & Body
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(0.5.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Table Sub-header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "মাসিক উপস্থিতি বিবরণী (${banglaMonths[selectedMonth]} ${BanglaUtils.toBanglaDigits(selectedYear)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "কার্যদিবস: ${BanglaUtils.toBanglaDigits(uniqueWorkingDates.size)} দিন",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Minimal Horizontal Scrollable Table
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column(modifier = Modifier.width(520.dp)) {
                            // Column Headers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("শ্রেণি", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("ধরন", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("ভর্তি", modifier = Modifier.width(55.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("মোট উপস্থিতি", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("গড় উপস্থিতি", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("গড় হার %", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }

                            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Table Rows per Class
                            monthlyClassReports.forEachIndexed { idx, cr ->
                                val isEven = idx % 2 == 0
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isEven) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                ) {
                                    // Row 1: Boys
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(cr.className, modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("ছাত্র", modifier = Modifier.width(50.dp), fontSize = 10.sp, color = Color(0xFF1565C0), textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(cr.enrolledBoys), modifier = Modifier.width(55.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(cr.totalPresentBoys), modifier = Modifier.width(85.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", cr.avgPresentBoys)), modifier = Modifier.width(85.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", cr.rateBoys))}%", modifier = Modifier.width(75.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    }

                                    // Row 2: Girls
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("", modifier = Modifier.width(80.dp))
                                        Text("ছাত্রী", modifier = Modifier.width(50.dp), fontSize = 10.sp, color = Color(0xFFC2185B), textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(cr.enrolledGirls), modifier = Modifier.width(55.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(cr.totalPresentGirls), modifier = Modifier.width(85.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", cr.avgPresentGirls)), modifier = Modifier.width(85.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", cr.rateGirls))}%", modifier = Modifier.width(75.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    }

                                    // Row 3: Total for Class (Highlighted)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("", modifier = Modifier.width(80.dp))
                                        Text("মোট", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(cr.totalEnrolled), modifier = Modifier.width(55.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(cr.totalPresent), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", cr.avgPresentTotal)), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", cr.rateTotal))}%", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32), textAlign = TextAlign.Center)
                                    }

                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }

                            // Grand Total Footer (সর্বমোট)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFB71C1C).copy(alpha = 0.1f))
                            ) {
                                // Grand Total Boys
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("সর্বমোট", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB71C1C), textAlign = TextAlign.Center)
                                    Text("ছাত্র", modifier = Modifier.width(50.dp), fontSize = 10.sp, color = Color(0xFF1565C0), textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(grandTotalReport.enrolledBoys), modifier = Modifier.width(55.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(grandTotalReport.totalPresentBoys), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.avgPresentBoys)), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.rateBoys))}%", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }

                                // Grand Total Girls
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("", modifier = Modifier.width(80.dp))
                                    Text("ছাত্রী", modifier = Modifier.width(50.dp), fontSize = 10.sp, color = Color(0xFFC2185B), textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(grandTotalReport.enrolledGirls), modifier = Modifier.width(55.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(grandTotalReport.totalPresentGirls), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.avgPresentGirls)), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.rateGirls))}%", modifier = Modifier.width(75.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }

                                // Grand Total School Total
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFB71C1C).copy(alpha = 0.18f))
                                        .padding(vertical = 5.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("", modifier = Modifier.width(80.dp))
                                    Text("মোট", modifier = Modifier.width(50.dp), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color(0xFFB71C1C), textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(grandTotalReport.totalEnrolled), modifier = Modifier.width(55.dp), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFB71C1C), textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(grandTotalReport.totalPresent), modifier = Modifier.width(85.dp), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFB71C1C), textAlign = TextAlign.Center)
                                    Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.avgPresentTotal)), modifier = Modifier.width(85.dp), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFB71C1C), textAlign = TextAlign.Center)
                                    Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", grandTotalReport.rateTotal))}%", modifier = Modifier.width(75.dp), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFB71C1C), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// 4. MODERN ATTENDANCE HISTORY LOG VIEW
// -------------------------------------------------------------
@Composable
private fun ModernAttendanceHistoryView(
    allAttendance: List<AttendanceEntity>,
    onSelectDate: (String) -> Unit,
    onDeleteDate: (String) -> Unit
) {
    val historyDates = remember(allAttendance) {
        allAttendance.map { it.date }.distinct().sortedDescending()
    }

    if (historyDates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.EventNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "কোনো হাজিরা রেকর্ড পাওয়া যায়নি",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "দৈনিক হাজিরা এন্ট্রি করে সংরক্ষণ করুন।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "সংরক্ষিত হাজিরার তালিকা (মোট ${BanglaUtils.toBanglaDigits(historyDates.size)} দিন)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                )
            }

            items(historyDates) { dt ->
                val recordsForDate = allAttendance.filter { it.date == dt }
                val totalEnrolled = recordsForDate.sumOf { it.totalBoys + it.totalGirls }
                val totalPresent = recordsForDate.sumOf { it.presentBoys + it.presentGirls }
                val rate = if (totalEnrolled > 0) (totalPresent.toDouble() / totalEnrolled) * 100.0 else 0.0

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDate(dt) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = BanglaUtils.formatBanglaDate(dt),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "উপস্থিতি: ${BanglaUtils.toBanglaDigits(totalPresent)} / ${BanglaUtils.toBanglaDigits(totalEnrolled)} জন",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (rate >= 80) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFF57F17).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", rate))}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rate >= 80) Color(0xFF2E7D32) else Color(0xFFF57F17),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteDate(dt) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// COMPACT KPI BADGES & CARDS
// -------------------------------------------------------------
@Composable
private fun ModernKpiBadge(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    sub: String? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
            if (sub != null) {
                Text(sub, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ModernMonthlySummaryCard(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor, textAlign = TextAlign.Center)
        }
    }
}

// Data models
private data class DailySummaryData(
    val totalEnrolled: Int,
    val totalEnrolledBoys: Int,
    val totalEnrolledGirls: Int,
    val totalPresent: Int,
    val totalPresentBoys: Int,
    val totalPresentGirls: Int,
    val totalAbsent: Int,
    val ratePercent: Double
)

private data class ClassMonthlyReportData(
    val className: String,
    val enrolledBoys: Int,
    val enrolledGirls: Int,
    val totalEnrolled: Int,
    val totalPresentBoys: Int,
    val totalPresentGirls: Int,
    val totalPresent: Int,
    val avgPresentBoys: Double,
    val avgPresentGirls: Double,
    val avgPresentTotal: Double,
    val rateBoys: Double,
    val rateGirls: Double,
    val rateTotal: Double
)

private data class GrandTotalReportData(
    val enrolledBoys: Int,
    val enrolledGirls: Int,
    val totalEnrolled: Int,
    val totalPresentBoys: Int,
    val totalPresentGirls: Int,
    val totalPresent: Int,
    val avgPresentBoys: Double,
    val avgPresentGirls: Double,
    val avgPresentTotal: Double,
    val rateBoys: Double,
    val rateGirls: Double,
    val rateTotal: Double
)

// HTML generator for clean printing
private fun generateMonthlyReportHtml(
    schoolName: String,
    monthName: String,
    year: Int,
    workingDays: Int,
    classReports: List<ClassMonthlyReportData>,
    grandTotal: GrandTotalReportData
): String {
    val tableRows = StringBuilder()

    classReports.forEach { cr ->
        tableRows.append("""
            <tr>
                <td rowspan="3" style="font-weight: bold; text-align: center; vertical-align: middle;">${cr.className}</td>
                <td>ছাত্র</td>
                <td>${cr.enrolledBoys}</td>
                <td>${cr.totalPresentBoys}</td>
                <td>${String.format(Locale.US, "%.1f", cr.avgPresentBoys)}</td>
                <td>${String.format(Locale.US, "%.1f", cr.rateBoys)}%</td>
            </tr>
            <tr>
                <td>ছাত্রী</td>
                <td>${cr.enrolledGirls}</td>
                <td>${cr.totalPresentGirls}</td>
                <td>${String.format(Locale.US, "%.1f", cr.avgPresentGirls)}</td>
                <td>${String.format(Locale.US, "%.1f", cr.rateGirls)}%</td>
            </tr>
            <tr style="background-color: #f0f4f8; font-weight: bold;">
                <td>মোট</td>
                <td>${cr.totalEnrolled}</td>
                <td>${cr.totalPresent}</td>
                <td>${String.format(Locale.US, "%.1f", cr.avgPresentTotal)}</td>
                <td>${String.format(Locale.US, "%.1f", cr.rateTotal)}%</td>
            </tr>
        """.trimIndent())
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: sans-serif; margin: 20px; color: #222; }
                .header { text-align: center; margin-bottom: 20px; }
                .header h2 { margin: 0 0 5px 0; color: #0d47a1; }
                .header h4 { margin: 0 0 5px 0; color: #555; }
                .meta { display: flex; justify-content: space-between; margin-bottom: 12px; font-weight: bold; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
                th, td { border: 1px solid #777; padding: 6px 8px; text-align: center; }
                th { background-color: #e3f2fd; color: #0d47a1; }
                .grand-total { background-color: #ffebee; font-weight: bold; color: #b71c1c; }
            </style>
        </head>
        <body>
            <div class="header">
                <h2>$schoolName</h2>
                <h4>মাসিক শিক্ষার্থী উপস্থিতি প্রতিবেদন</h4>
            </div>
            <div class="meta">
                <div>মাস: $monthName $year</div>
                <div>মোট কার্যদিবস: $workingDays দিন</div>
            </div>
            <table>
                <thead>
                    <tr>
                        <th>শ্রেণি</th>
                        <th>ধরন</th>
                        <th>ভর্তি সংখ্যা</th>
                        <th>মোট উপস্থিতি</th>
                        <th>গড় উপস্থিতি</th>
                        <th>গড় উপস্থিতির হার</th>
                    </tr>
                </thead>
                <tbody>
                    $tableRows
                    <tr class="grand-total">
                        <td rowspan="3" style="text-align: center; vertical-align: middle;">সর্বমোট</td>
                        <td>ছাত্র</td>
                        <td>${grandTotal.enrolledBoys}</td>
                        <td>${grandTotal.totalPresentBoys}</td>
                        <td>${String.format(Locale.US, "%.1f", grandTotal.avgPresentBoys)}</td>
                        <td>${String.format(Locale.US, "%.1f", grandTotal.rateBoys)}%</td>
                    </tr>
                    <tr class="grand-total">
                        <td>ছাত্রী</td>
                        <td>${grandTotal.enrolledGirls}</td>
                        <td>${grandTotal.totalPresentGirls}</td>
                        <td>${String.format(Locale.US, "%.1f", grandTotal.avgPresentGirls)}</td>
                        <td>${String.format(Locale.US, "%.1f", grandTotal.rateGirls)}%</td>
                    </tr>
                    <tr class="grand-total" style="background-color: #ffcdd2; font-size: 14px;">
                        <td>মোট</td>
                        <td>${grandTotal.totalEnrolled}</td>
                        <td>${grandTotal.totalPresent}</td>
                        <td>${String.format(Locale.US, "%.1f", grandTotal.avgPresentTotal)}</td>
                        <td>${String.format(Locale.US, "%.1f", grandTotal.rateTotal)}%</td>
                    </tr>
                </tbody>
            </table>
            <div style="margin-top: 40px; display: flex; justify-content: space-between;">
                <div>তারিখ: _________________</div>
                <div>প্রধান শিক্ষকের স্বাক্ষর: _________________</div>
            </div>
        </body>
        </html>
    """.trimIndent()
}
