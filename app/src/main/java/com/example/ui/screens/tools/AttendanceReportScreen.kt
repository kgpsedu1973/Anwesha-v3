package com.example.ui.screens.tools

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AttendanceEntity
import com.example.util.BanglaUtils
import com.example.util.ClassPreset
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
    val savedPreset = remember { ClassPreset.getSavedPreset(context) }

    var selectedTab by remember { mutableStateOf(0) } // 0: দৈনিক হাজিরা ও কার্ড, 1: মাসিক সার্ভিস রিপোর্ট, 2: হাজিরা ইতিহাস
    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    // Selected Month & Year for Monthly Report
    val currentCal = Calendar.getInstance()
    var selectedMonthIndex by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) } // 0-11
    var selectedYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }

    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }

    // Back handler
    BackHandler(enabled = true) {
        if (showDeleteConfirmDialog != null) {
            showDeleteConfirmDialog = null
        } else {
            onNavigateBack()
        }
    }

    // Determine list of standard classes
    val classNames: List<String> = remember(allStudents, savedPreset) {
        val studentClasses = allStudents.map { it.studentClass }.distinct().filter { it.isNotBlank() }
        val presetClasses = savedPreset.classNames
        val combined = (presetClasses + studentClasses).distinct()
        if (combined.isEmpty()) listOf("প্রাক-প্রাথমিক ৫+", "১ম", "২য়", "৩য়", "৪র্থ", "৫ম") else combined
    }

    // Pre-calculate latest enrolled counts from allStudents (status == "Current")
    val enrolledCountsByClass: Map<String, Pair<Int, Int>> = remember(allStudents, classNames) {
        val activeStudents = allStudents.filter { it.status == "Current" || it.status == "নিয়মিত" || it.status.isBlank() }
        classNames.associateWith { className ->
            val inClass = activeStudents.filter { it.studentClass.trim() == className.trim() }
            val boys = inClass.count { it.gender.trim() in listOf("ছাত্র", "পুরুষ", "Male", "boy", "ছেলে") }
            val girls = inClass.count { it.gender.trim() in listOf("ছাত্রী", "মহিলা", "Female", "girl", "মেয়ে") }
            // If gender is unspecified, balance it
            val other = inClass.size - (boys + girls)
            val finalBoys = boys + (other / 2)
            val finalGirls = girls + (other - other / 2)
            Pair(finalBoys, finalGirls)
        }
    }

    // Daily input state mapped by className: Pair(presentBoysStr, presentGirlsStr)
    val dailyInputMap = remember(selectedDate, allAttendance) {
        val recordsForDate = allAttendance.filter { it.date == selectedDate }
        val map = mutableStateMapOf<String, Pair<String, String>>()
        classNames.forEach { cls ->
            val record = recordsForDate.find { it.className.trim() == cls.trim() }
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
                            fontSize = 18.sp
                        )
                        Text(
                            text = schoolInfo?.schoolName ?: "অন্বেষা ডিজিটাল প্রাথমিক বিদ্যালয়",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পিছনে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val helpMsg = "• দৈনিক হাজিরা: প্রতিটি শ্রেণির ছাত্র ও ছাত্রীর উপস্থিতি সংখ্যা এন্ট্রি করুন। ভর্তি সংখ্যার বেশি এন্ট্রি করা যাবে না।\n• দিনশেষে কার্ড: স্বয়ংক্রিয়ভাবে দৈনিক সামারি তৈরি হয়।\n• মাসিক রিপোর্ট: ছবির মতো মোট, গড় ও উপস্থিতির শতকরা হারের পূর্ণাঙ্গ চার্ট।"
                            Toast.makeText(context, helpMsg, Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = "সাহায্য")
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
            // Segmented Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = { HorizontalDivider(thickness = 0.8.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("দৈনিক এন্ট্রি ও কার্ড", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("মাসিক রিপোর্ট", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("হাজিরা লগ", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> DailyAttendanceEntryView(
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    onOpenDatePicker = { openDatePicker() },
                    classNames = classNames,
                    enrolledCountsByClass = enrolledCountsByClass,
                    dailyInputMap = dailyInputMap,
                    isDateSaved = isDateSaved,
                    onSave = { records ->
                        viewModel.saveDailyAttendanceList(selectedDate, records)
                        Toast.makeText(context, "${BanglaUtils.formatBanglaDate(selectedDate)} এর হাজিরা সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        viewModel.deleteAttendanceForDate(selectedDate)
                        Toast.makeText(context, "হাজিরা রেকর্ড মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> MonthlyAttendanceReportView(
                    selectedMonth = selectedMonthIndex,
                    selectedYear = selectedYear,
                    onMonthYearChange = { m, y ->
                        selectedMonthIndex = m
                        selectedYear = y
                    },
                    classNames = classNames,
                    enrolledCountsByClass = enrolledCountsByClass,
                    allAttendance = allAttendance,
                    schoolName = schoolInfo?.schoolName ?: "অন্বেষা বিদ্যালয়"
                )
                2 -> AttendanceHistoryView(
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

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog != null) {
        val dtToDelete = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("হাজিরা মুছে ফেলতে চান?") },
            text = { Text("${BanglaUtils.formatBanglaDate(dtToDelete)} তারিখের সকল শ্রেণির হাজিরা মুছে ফেলা হবে।") },
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
// TAB 1: DAILY ATTENDANCE ENTRY & VISUAL CARD
// -------------------------------------------------------------
@Composable
private fun DailyAttendanceEntryView(
    selectedDate: String,
    onDateChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    classNames: List<String>,
    enrolledCountsByClass: Map<String, Pair<Int, Int>>,
    dailyInputMap: MutableMap<String, Pair<String, String>>,
    isDateSaved: Boolean,
    onSave: (List<AttendanceEntity>) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Date calculations for prev/next day
    fun shiftDate(days: Int) {
        try {
            val d = sdf.parse(selectedDate) ?: Date()
            val cal = Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, days) }
            onDateChange(sdf.format(cal.time))
        } catch (e: Exception) {
            // fallback
        }
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

    // Compute live daily summary for card
    val dailySummary = remember(dailyInputMap, enrolledCountsByClass) {
        var totalEnrolledBoys = 0
        var totalEnrolledGirls = 0
        var totalPresentBoys = 0
        var totalPresentGirls = 0

        classNames.forEach { cls ->
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("daily_attendance_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date Navigator Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { shiftDate(-1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "পূর্ববর্তী দিন", modifier = Modifier.size(20.dp))
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenDatePicker() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = BanglaUtils.formatBanglaDate(selectedDate),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "পরবর্তী দিন", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Quick Actions & Fast-Fill Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        // Mark all present
                        classNames.forEach { cls ->
                            val (eB, eG) = enrolledCountsByClass[cls] ?: Pair(0, 0)
                            dailyInputMap[cls] = Pair(eB.toString(), eG.toString())
                        }
                        Toast.makeText(context, "সকল শ্রেণিতে সবাই উপস্থিত সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("সবাই উপস্থিত", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        // Clear all inputs
                        classNames.forEach { cls ->
                            dailyInputMap[cls] = Pair("", "")
                        }
                    },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("রিসেট", fontSize = 12.sp)
                }
            }
        }

        // Daily Visual Summary Card (দিনশেষে ভিসুয়াল সামারি কার্ড)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                text = "আজকের হাজিরা সামারি কার্ড",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDateSaved) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFE65100).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isDateSaved) "✓ সংরক্ষিত" else "খসড়া (অসংরক্ষিত)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDateSaved) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Main Metric Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rate Percentage
                        Column {
                            Text("উপস্থিতির হার", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", dailySummary.ratePercent))}%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (dailySummary.ratePercent >= 80) Color(0xFF2E7D32) else if (dailySummary.ratePercent >= 60) Color(0xFFF57F17) else Color(0xFFC62828)
                            )
                        }

                        // Compact metrics
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DailyKpiItem(
                                title = "ভর্তি সংখ্যা",
                                value = BanglaUtils.toBanglaDigits(dailySummary.totalEnrolled),
                                sub = "ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledBoys)} ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledGirls)}"
                            )
                            DailyKpiItem(
                                title = "উপস্থিত",
                                value = BanglaUtils.toBanglaDigits(dailySummary.totalPresent),
                                valueColor = Color(0xFF2E7D32),
                                sub = "ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalPresentBoys)} ছা:${BanglaUtils.toBanglaDigits(dailySummary.totalPresentGirls)}"
                            )
                            DailyKpiItem(
                                title = "অনুপস্থিত",
                                value = BanglaUtils.toBanglaDigits(dailySummary.totalAbsent),
                                valueColor = Color(0xFFC62828)
                            )
                        }
                    }

                    // Linear Progress Indicator
                    val progressFloat = (dailySummary.ratePercent / 100.0).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (dailySummary.ratePercent >= 80) Color(0xFF2E7D32) else Color(0xFF0288D1),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Share Text Summary action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val text = buildString {
                                    appendLine("📅 দৈনিক হাজিরা সারসংক্ষেপ")
                                    appendLine("তারিখ: ${BanglaUtils.formatBanglaDate(selectedDate)} ($dayOfWeekBangla)")
                                    appendLine("----------------------------------")
                                    appendLine("মোট ভর্তি: ${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolled)} জন (ছাত্র ${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledBoys)}, ছাত্রী ${BanglaUtils.toBanglaDigits(dailySummary.totalEnrolledGirls)})")
                                    appendLine("মোট উপস্থিতি: ${BanglaUtils.toBanglaDigits(dailySummary.totalPresent)} জন (ছাত্র ${BanglaUtils.toBanglaDigits(dailySummary.totalPresentBoys)}, ছাত্রী ${BanglaUtils.toBanglaDigits(dailySummary.totalPresentGirls)})")
                                    appendLine("মোট অনুপস্থিত: ${BanglaUtils.toBanglaDigits(dailySummary.totalAbsent)} জন")
                                    appendLine("উপস্থিতির হার: ${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", dailySummary.ratePercent))}%")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "হাজিরা সারসংক্ষেপ পাঠান"))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("সামারি কপি / শেয়ার", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section Title: শ্রেণিভিত্তিক হাজিরা এন্ট্রি
        item {
            Text(
                text = "শ্রেণিভিত্তিক হাজিরা ইনপুট (ভর্তি অনুযায়ী সর্বোচ্চ সীমা ধার্য)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        // Class Entry Rows (Compact Card per Class)
        items(classNames) { className ->
            val (enrolledBoys, enrolledGirls) = enrolledCountsByClass[className] ?: Pair(0, 0)
            val currentPair = dailyInputMap[className] ?: Pair("", "")
            val presentBoysStr = currentPair.first
            val presentGirlsStr = currentPair.second

            val pBoysInt = presentBoysStr.toIntOrNull() ?: 0
            val pGirlsInt = presentGirlsStr.toIntOrNull() ?: 0
            val classEnrolledTotal = enrolledBoys + enrolledGirls
            val classPresentTotal = pBoysInt + pGirlsInt
            val classRate = if (classEnrolledTotal > 0) (classPresentTotal.toDouble() / classEnrolledTotal) * 100.0 else 0.0

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(0.5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Class Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = className,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "ভর্তি: ${BanglaUtils.toBanglaDigits(classEnrolledTotal)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // Quick class "All Present" chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    dailyInputMap[className] = Pair(enrolledBoys.toString(), enrolledGirls.toString())
                                }
                        ) {
                            Text(
                                text = "ক্লাস পূর্ণ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Input Fields: Boys & Girls Side by Side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Boys Input
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ছাত্র (উপস্থিত)", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1565C0))
                                Text(
                                    "ভর্তি: ${BanglaUtils.toBanglaDigits(enrolledBoys)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            OutlinedTextField(
                                value = presentBoysStr,
                                onValueChange = { newVal ->
                                    val filtered = newVal.filter { it.isDigit() }
                                    val num = filtered.toIntOrNull()
                                    if (num == null) {
                                        dailyInputMap[className] = Pair("", presentGirlsStr)
                                    } else {
                                        val capped = if (enrolledBoys > 0) num.coerceAtMost(enrolledBoys) else num
                                        dailyInputMap[className] = Pair(capped.toString(), presentGirlsStr)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text("0", fontSize = 13.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            )
                        }

                        // Girls Input
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ছাত্রী (উপস্থিত)", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFC2185B))
                                Text(
                                    "ভর্তি: ${BanglaUtils.toBanglaDigits(enrolledGirls)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            OutlinedTextField(
                                value = presentGirlsStr,
                                onValueChange = { newVal ->
                                    val filtered = newVal.filter { it.isDigit() }
                                    val num = filtered.toIntOrNull()
                                    if (num == null) {
                                        dailyInputMap[className] = Pair(presentBoysStr, "")
                                    } else {
                                        val capped = if (enrolledGirls > 0) num.coerceAtMost(enrolledGirls) else num
                                        dailyInputMap[className] = Pair(presentBoysStr, capped.toString())
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text("0", fontSize = 13.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }

                    // Row Footer Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "মোট উপস্থিত: ${BanglaUtils.toBanglaDigits(classPresentTotal)} / ${BanglaUtils.toBanglaDigits(classEnrolledTotal)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "হার: ${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.1f", classRate))}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (classRate >= 80) Color(0xFF2E7D32) else Color(0xFFF57F17)
                        )
                    }
                }
            }
        }

        // Save & Delete Buttons Bottom Sticky Card
        item {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDateSaved) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("মুছুন", fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = {
                        val records = classNames.map { cls ->
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
                        onSave(records)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isDateSaved) "হাজিরা আপডেট করুন" else "হাজিরা সংরক্ষণ করুন",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: MONTHLY ATTENDANCE REPORT VIEW (EXACT AS SCREENSHOT)
// -------------------------------------------------------------
@Composable
private fun MonthlyAttendanceReportView(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthYearChange: (Int, Int) -> Unit,
    classNames: List<String>,
    enrolledCountsByClass: Map<String, Pair<Int, Int>>,
    allAttendance: List<AttendanceEntity>,
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

    // Unique dates recorded in this month = working days
    val uniqueWorkingDates = remember(monthlyRecords) {
        monthlyRecords.map { it.date }.distinct()
    }
    val effectiveWorkingDays = uniqueWorkingDates.size.coerceAtLeast(1)

    // Compute aggregation per class exactly according to image
    val monthlyClassReports = remember(classNames, monthlyRecords, enrolledCountsByClass, effectiveWorkingDays) {
        classNames.map { cls ->
            val recordsForClass = monthlyRecords.filter { it.className.trim() == cls.trim() }
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

    // Grand Totals (সর্বমোট)
    val grandTotalReport = remember(monthlyClassReports, effectiveWorkingDays) {
        val enrolledBoys = monthlyClassReports.sumOf { it.enrolledBoys }
        val enrolledGirls = monthlyClassReports.sumOf { it.enrolledGirls }
        val totalEnrolled = enrolledBoys + enrolledGirls

        val totalPresentBoys = monthlyClassReports.sumOf { it.totalPresentBoys }
        val totalPresentGirls = monthlyClassReports.sumOf { it.totalPresentGirls }
        val totalPresent = totalPresentBoys + totalPresentGirls

        val avgPresentBoys = monthlyClassReports.sumOf { it.avgPresentBoys }
        val avgPresentGirls = monthlyClassReports.sumOf { it.avgPresentGirls }
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
            .padding(12.dp)
            .testTag("monthly_attendance_report"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Month & Year Selector + Controls
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Month dropdown
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showMonthDropdown = true },
                            label = { Text("${banglaMonths[selectedMonth]} ▼", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                        DropdownMenu(
                            expanded = showMonthDropdown,
                            onDismissRequest = { showMonthDropdown = false }
                        ) {
                            banglaMonths.forEachIndexed { idx, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
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
                        FilterChip(
                            selected = true,
                            onClick = { showYearDropdown = true },
                            label = { Text("${BanglaUtils.toBanglaDigits(selectedYear)} ▼", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        )
                        DropdownMenu(
                            expanded = showYearDropdown,
                            onDismissRequest = { showYearDropdown = false }
                        ) {
                            (2023..2030).forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text(BanglaUtils.toBanglaDigits(yr)) },
                                    onClick = {
                                        onMonthYearChange(selectedMonth, yr)
                                        showYearDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Working Days Chip / Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "কার্যদিবস: ${BanglaUtils.toBanglaDigits(uniqueWorkingDates.size)} দিন",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Action Toolbar (Print / Share / Export)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        val reportText = buildString {
                            appendLine("📊 মাসিক উপস্থিতি রিপোর্ট - ${banglaMonths[selectedMonth]} ${BanglaUtils.toBanglaDigits(selectedYear)}")
                            appendLine("বিদ্যালয়: $schoolName")
                            appendLine("মোট কার্যদিবস: ${BanglaUtils.toBanglaDigits(effectiveWorkingDays)} দিন")
                            appendLine("--------------------------------------------------")
                            appendLine("শ্রেণি | ধরন | ভর্তি | মোট উপস্থিতি | গড় উপস্থিতি | গড় হার")
                            appendLine("--------------------------------------------------")
                            monthlyClassReports.forEach { rep ->
                                appendLine("${rep.className} | ছাত্র | ${rep.enrolledBoys} | ${rep.totalPresentBoys} | ${String.format(Locale.US, "%.1f", rep.avgPresentBoys)} | ${String.format(Locale.US, "%.1f", rep.rateBoys)}%")
                                appendLine("${rep.className} | ছাত্রী | ${rep.enrolledGirls} | ${rep.totalPresentGirls} | ${String.format(Locale.US, "%.1f", rep.avgPresentGirls)} | ${String.format(Locale.US, "%.1f", rep.rateGirls)}%")
                                appendLine("${rep.className} | মোট | ${rep.totalEnrolled} | ${rep.totalPresent} | ${String.format(Locale.US, "%.1f", rep.avgPresentTotal)} | ${String.format(Locale.US, "%.1f", rep.rateTotal)}%")
                            }
                            appendLine("--------------------------------------------------")
                            appendLine("সর্বমোট | ছাত্র | ${grandTotalReport.enrolledBoys} | ${grandTotalReport.totalPresentBoys} | ${String.format(Locale.US, "%.1f", grandTotalReport.avgPresentBoys)} | ${String.format(Locale.US, "%.1f", grandTotalReport.rateBoys)}%")
                            appendLine("সর্বমোট | ছাত্রী | ${grandTotalReport.enrolledGirls} | ${grandTotalReport.totalPresentGirls} | ${String.format(Locale.US, "%.1f", grandTotalReport.avgPresentGirls)} | ${String.format(Locale.US, "%.1f", grandTotalReport.rateGirls)}%")
                            appendLine("সর্বমোট | মোট | ${grandTotalReport.totalEnrolled} | ${grandTotalReport.totalPresent} | ${String.format(Locale.US, "%.1f", grandTotalReport.avgPresentTotal)} | ${String.format(Locale.US, "%.1f", grandTotalReport.rateTotal)}%")
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "মাসিক রিপোর্ট শেয়ার করুন"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("রিপোর্ট শেয়ার", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "টেবিলটি সরাসরি দেখার ও প্রিন্ট করার উপযোগী ফরমেটে নিচে সাজানো আছে।", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ভিউ / প্রিন্ট", fontSize = 12.sp)
                }
            }
        }

        // Title Header (Report)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Report",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${banglaMonths[selectedMonth]} ${BanglaUtils.toBanglaDigits(selectedYear)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // EXACT TABLE MATCHING ATTACHED SCREENSHOT
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Horizontal Scroll Container to ensure crisp columns on any screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val colWidthClass = 75.dp
                    val colWidthType = 65.dp
                    val colWidthEnrolled = 65.dp
                    val colWidthTotal = 85.dp
                    val colWidthAvg = 80.dp
                    val colWidthRate = 95.dp

                    // 1. Table Header
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFAFAFA))
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("শ্রেণি", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(colWidthClass), color = Color(0xFF333333))
                        Text("ধরন", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(colWidthType), color = Color(0xFF333333))
                        Text("ভর্তি", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(colWidthEnrolled), color = Color(0xFF333333))
                        Text("মোট উপস্থিতি", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(colWidthTotal), color = Color(0xFF333333))
                        Text("গড় উপস্থিতি", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(colWidthAvg), color = Color(0xFF333333))
                        Text("গড় উপস্থিতির হার", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(colWidthRate), color = Color(0xFF333333))
                    }
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE0E0E0))

                    // 2. Class Rows
                    monthlyClassReports.forEachIndexed { index, item ->
                        val classLabelColor = when (index % 5) {
                            0 -> Color(0xFF2E7D32) // প্রাক ৫+ (Green)
                            1 -> Color(0xFF0288D1) // ১ম (Teal/Blue)
                            2 -> Color(0xFFC2185B) // ২য় (Pink/Magenta)
                            3 -> Color(0xFF6A1B9A) // ৩য় (Purple)
                            4 -> Color(0xFF00838F) // ৪র্থ (Teal)
                            else -> Color(0xFF2E7D32)
                        }

                        // Row 1: ছাত্র
                        Row(
                            modifier = Modifier
                                .background(Color.White)
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(colWidthClass))
                            Text("ছাত্র", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32), modifier = Modifier.width(colWidthType))
                            Text(BanglaUtils.toBanglaDigits(item.enrolledBoys), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color(0xFF2E7D32), modifier = Modifier.width(colWidthEnrolled))
                            Text(BanglaUtils.toBanglaDigits(item.totalPresentBoys), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF00695C), modifier = Modifier.width(colWidthTotal))
                            Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.0f", item.avgPresentBoys)), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF00695C), modifier = Modifier.width(colWidthAvg))
                            Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.2f", item.rateBoys))}%", fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF2E7D32), modifier = Modifier.width(colWidthRate))
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))

                        // Row 2: ছাত্রী
                        Row(
                            modifier = Modifier
                                .background(Color.White)
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.className, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = classLabelColor, modifier = Modifier.width(colWidthClass))
                            Text("ছাত্রী", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32), modifier = Modifier.width(colWidthType))
                            Text(BanglaUtils.toBanglaDigits(item.enrolledGirls), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color(0xFF2E7D32), modifier = Modifier.width(colWidthEnrolled))
                            Text(BanglaUtils.toBanglaDigits(item.totalPresentGirls), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF00695C), modifier = Modifier.width(colWidthTotal))
                            Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.0f", item.avgPresentGirls)), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF00695C), modifier = Modifier.width(colWidthAvg))
                            Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.2f", item.rateGirls))}%", fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF2E7D32), modifier = Modifier.width(colWidthRate))
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))

                        // Row 3: মোট (Bold, Colored, Italic with Underline matching the image)
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF7FBF9))
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(colWidthClass))
                            Text(
                                text = "মোট",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = classLabelColor,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.width(colWidthType)
                            )
                            Text(
                                text = BanglaUtils.toBanglaDigits(item.totalEnrolled),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = classLabelColor,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.width(colWidthEnrolled)
                            )
                            Text(
                                text = BanglaUtils.toBanglaDigits(item.totalPresent),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = classLabelColor,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.width(colWidthTotal)
                            )
                            Text(
                                text = BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.0f", item.avgPresentTotal)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = classLabelColor,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.width(colWidthAvg)
                            )
                            Text(
                                text = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.2f", item.rateTotal))}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = classLabelColor,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.width(colWidthRate)
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE0E0E0))
                    }

                    // 3. Bottom Grand Total (সর্বমোট) - Red/Maroon style from Image
                    val maroonColor = Color(0xFFB71C1C)

                    // Grand Total: ছাত্র
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFFF8F8))
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "সর্বমোট",
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp,
                            color = maroonColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.width(colWidthClass)
                        )
                        Text("ছাত্র", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = maroonColor, modifier = Modifier.width(colWidthType))
                        Text(BanglaUtils.toBanglaDigits(grandTotalReport.enrolledBoys), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthEnrolled))
                        Text(BanglaUtils.toBanglaDigits(grandTotalReport.totalPresentBoys), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthTotal))
                        Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.0f", grandTotalReport.avgPresentBoys)), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthAvg))
                        Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.2f", grandTotalReport.rateBoys))}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthRate))
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFFFCDD2))

                    // Grand Total: ছাত্রী
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFFF8F8))
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(colWidthClass))
                        Text("ছাত্রী", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = maroonColor, modifier = Modifier.width(colWidthType))
                        Text(BanglaUtils.toBanglaDigits(grandTotalReport.enrolledGirls), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthEnrolled))
                        Text(BanglaUtils.toBanglaDigits(grandTotalReport.totalPresentGirls), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthTotal))
                        Text(BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.0f", grandTotalReport.avgPresentGirls)), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthAvg))
                        Text("${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.2f", grandTotalReport.rateGirls))}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = maroonColor, modifier = Modifier.width(colWidthRate))
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFFFCDD2))

                    // Grand Total: মোট
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFFEBEE))
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(colWidthClass))
                        Text(
                            text = "মোট",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = maroonColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.width(colWidthType)
                        )
                        Text(
                            text = BanglaUtils.toBanglaDigits(grandTotalReport.totalEnrolled),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = maroonColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.width(colWidthEnrolled)
                        )
                        Text(
                            text = BanglaUtils.toBanglaDigits(grandTotalReport.totalPresent),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = maroonColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.width(colWidthTotal)
                        )
                        Text(
                            text = BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.0f", grandTotalReport.avgPresentTotal)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = maroonColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.width(colWidthAvg)
                        )
                        Text(
                            text = "${BanglaUtils.toBanglaDigits(String.format(Locale.US, "%.2f", grandTotalReport.rateTotal))}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = maroonColor,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.width(colWidthRate)
                        )
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
// TAB 3: ATTENDANCE HISTORY VIEW
// -------------------------------------------------------------
@Composable
private fun AttendanceHistoryView(
    allAttendance: List<AttendanceEntity>,
    onSelectDate: (String) -> Unit,
    onDeleteDate: (String) -> Unit
) {
    val datesGrouped = remember(allAttendance) {
        allAttendance.groupBy { it.date }
            .toList()
            .sortedByDescending { it.first }
    }

    if (datesGrouped.isEmpty()) {
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
                    Icons.Outlined.EventBusy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "এখনো কোনো হাজিরা এন্ট্রি করা হয়নি",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "দৈনিক এন্ট্রি ট্যাবে গিয়ে তারিখ অনুযায়ী হাজিরা সংরক্ষণ করুন",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "হাজিরা রেকর্ড হিস্ট্রি (${BanglaUtils.toBanglaDigits(datesGrouped.size)} দিন)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(datesGrouped) { (dateStr, records) ->
            val totalBoys = records.sumOf { it.totalBoys }
            val totalGirls = records.sumOf { it.totalGirls }
            val presentBoys = records.sumOf { it.presentBoys }
            val presentGirls = records.sumOf { it.presentGirls }

            val totalEnrolled = totalBoys + totalGirls
            val totalPresent = presentBoys + presentGirls
            val rate = if (totalEnrolled > 0) (totalPresent.toDouble() / totalEnrolled) * 100.0 else 0.0

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDate(dateStr) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = BanglaUtils.formatBanglaDate(dateStr),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "মোট উপস্থিত: ${BanglaUtils.toBanglaDigits(totalPresent)} / ${BanglaUtils.toBanglaDigits(totalEnrolled)} জন (ছাত্র: ${BanglaUtils.toBanglaDigits(presentBoys)}, ছাত্রী: ${BanglaUtils.toBanglaDigits(presentGirls)})",
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rate >= 80) Color(0xFF2E7D32) else Color(0xFFF57F17),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = { onDeleteDate(dateStr) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPOSABLES & DATA MODELS
// -------------------------------------------------------------
@Composable
private fun DailyKpiItem(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    sub: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
        if (sub != null) {
            Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

data class DailySummaryData(
    val totalEnrolled: Int,
    val totalEnrolledBoys: Int,
    val totalEnrolledGirls: Int,
    val totalPresent: Int,
    val totalPresentBoys: Int,
    val totalPresentGirls: Int,
    val totalAbsent: Int,
    val ratePercent: Double
)

data class ClassMonthlyReportData(
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

data class GrandTotalReportData(
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
