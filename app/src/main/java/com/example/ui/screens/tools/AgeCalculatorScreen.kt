package com.example.ui.screens.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StudentEntity
import com.example.ui.components.AppDatePickerDialog
import com.example.util.BanglaUtils
import com.example.util.BaseDateManager
import com.example.viewmodel.MainViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeCalculatorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allStudents by viewModel.allStudents.collectAsState()
    val baseConfig by viewModel.baseDateConfig.collectAsState()

    // Default dates
    val todayStr = remember { BaseDateManager.getTodayStr() }
    val defaultTargetEnd = remember(baseConfig) {
        if (baseConfig.baseDate.isNotBlank()) baseConfig.baseDate else todayStr
    }

    var startDateInput by remember { mutableStateOf("2018-05-15") }
    var endDateInput by remember { mutableStateOf(defaultTargetEnd) }

    var includeStartDay by remember { mutableStateOf(false) }
    var includeEndDay by remember { mutableStateOf(false) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStudentPickerSheet by remember { mutableStateOf(false) }

    var selectedStudentName by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) {
        if (showStartDatePicker) {
            showStartDatePicker = false
        } else if (showEndDatePicker) {
            showEndDatePicker = false
        } else if (showStudentPickerSheet) {
            showStudentPickerSheet = false
        } else {
            onNavigateBack()
        }
    }

    // Live calculation result
    val ageResult = remember(startDateInput, endDateInput, includeStartDay, includeEndDay) {
        BaseDateManager.calculateFullAgeWithInclusion(
            startDateStr = startDateInput,
            endDateStr = endDateInput,
            includeStartDay = includeStartDay,
            includeEndDay = includeEndDay
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "স্মার্ট বয়স ক্যালকুলেটর",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "লাইভ বয়স, দিন-মাস ও দিন অন্তর্ভুক্তির নির্ভুল গণনা",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_tools")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            startDateInput = "2018-05-15"
                            endDateInput = BaseDateManager.getTodayStr()
                            includeStartDay = false
                            includeEndDay = false
                            selectedStudentName = null
                            Toast.makeText(context, "রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_reset_age_calc")
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Quick Student Select Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStudentPickerSheet = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedStudentName != null) "নির্বাচিত শিক্ষার্থী: $selectedStudentName" else "শিক্ষার্থী তালিকা হতে সরাসরি জন্মতারিখ নির্বাচন করুন",
                            fontSize = 12.5.sp,
                            fontWeight = if (selectedStudentName != null) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "ক্লিক করে যেকোনো শিক্ষার্থীর জন্মতারিখ ১-ক্লিকে লোড করুন",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        Icons.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 2. Input Dates Card (Minimal & Compact)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Date / Birth Date
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "১. শুরুর তারিখ / জন্মতারিখ (Start / Birth Date):",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val bnStart = BaseDateManager.formatDateBengali(startDateInput)
                            if (bnStart.isNotBlank()) {
                                Text(
                                    text = bnStart,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = startDateInput,
                            onValueChange = {
                                startDateInput = it
                                selectedStudentName = null
                            },
                            label = { Text("শুরুর তারিখ (YYYY-MM-DD)") },
                            trailingIcon = {
                                IconButton(onClick = { showStartDatePicker = true }) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = "তারিখ বাছাই", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_calc_start_date")
                        )
                    }

                    // End Date / Target Date
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "২. শেষের তারিখ / পরিমাপের তারিখ (End / Target Date):",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val bnEnd = BaseDateManager.formatDateBengali(endDateInput)
                            if (bnEnd.isNotBlank()) {
                                Text(
                                    text = bnEnd,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = endDateInput,
                            onValueChange = { endDateInput = it },
                            label = { Text("শেষের তারিখ (YYYY-MM-DD)") },
                            trailingIcon = {
                                IconButton(onClick = { showEndDatePicker = true }) {
                                    Icon(Icons.Filled.Event, contentDescription = "তারিখ বাছাই", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_calc_end_date")
                        )

                        // Quick Presets for End Date
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { endDateInput = BaseDateManager.getTodayStr() },
                                label = { Text("আজকে (${BaseDateManager.formatDateBengali(BaseDateManager.getTodayStr())})", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { endDateInput = BaseDateManager.getYearEndStr(currentYear) },
                                label = { Text("৩১ ডিসে ($currentYear)", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { endDateInput = BaseDateManager.getYearStartStr(currentYear) },
                                label = { Text("০১ জানু ($currentYear)", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { endDateInput = BaseDateManager.getMonthEndStr() },
                                label = { Text("চলতি মাস শেষ", fontSize = 11.sp) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3. Inclusion Options (দিন যোগ বা বাদ দেওয়ার অপশন)
                    Text(
                        text = "৩. দিন অন্তর্ভুক্তি অপশন (Day Inclusion Options):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Toggle 1: Include Start Day
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (includeStartDay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (includeStartDay) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { includeStartDay = !includeStartDay }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(
                                    checked = includeStartDay,
                                    onCheckedChange = { includeStartDay = it },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "জন্মের দিন যোগ (+১ দিন)",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (includeStartDay) FontWeight.Bold else FontWeight.Normal,
                                        color = if (includeStartDay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "শুরুর দিন সহ গণনা",
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Toggle 2: Include End Day
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (includeEndDay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (includeEndDay) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { includeEndDay = !includeEndDay }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(
                                    checked = includeEndDay,
                                    onCheckedChange = { includeEndDay = it },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "শেষের দিন যোগ",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (includeEndDay) FontWeight.Bold else FontWeight.Normal,
                                        color = if (includeEndDay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "উভয় দিন অন্তর্ভুক্ত",
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. LIVE RESULT DISPLAY (Compact & Aesthetic Hero Card)
            if (ageResult.isValid) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "লাইভ বয়স ফলাফল",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Copy Button
                            FilledTonalButton(
                                onClick = {
                                    val summary = "বয়স ফলাফল:\n" +
                                            "শুরু: ${BaseDateManager.formatDateBengali(startDateInput)} ($startDateInput)\n" +
                                            "শেষ: ${BaseDateManager.formatDateBengali(endDateInput)} ($endDateInput)\n" +
                                            "বয়স: ${BanglaUtils.toBanglaDigits(ageResult.years)} বছর, ${BanglaUtils.toBanglaDigits(ageResult.months)} মাস, ${BanglaUtils.toBanglaDigits(ageResult.days)} দিন\n" +
                                            "মোট দিন: ${BanglaUtils.toBanglaDigits(ageResult.totalDays)} দিন\n" +
                                            "পরবর্তী জন্মদিন: ${ageResult.nextBirthdayBengali} (বাকি ${BanglaUtils.toBanglaDigits(ageResult.daysUntilNextBirthday)} দিন)"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Age Result", summary)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "ফলাফল ক্লিপবোর্ডে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("কপি", fontSize = 11.sp)
                            }
                        }

                        // 3 Big Highlight Pill Blocks (Years, Months, Days)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AgeValueBlock(
                                value = BanglaUtils.toBanglaDigits(ageResult.years),
                                unit = "বছর",
                                modifier = Modifier.weight(1f)
                            )
                            AgeValueBlock(
                                value = BanglaUtils.toBanglaDigits(ageResult.months),
                                unit = "মাস",
                                modifier = Modifier.weight(1f)
                            )
                            AgeValueBlock(
                                value = BanglaUtils.toBanglaDigits(ageResult.days),
                                unit = "দিন",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Summary phrase
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎯 বয়স: ${BanglaUtils.toBanglaDigits(ageResult.years)} বছর ${if (ageResult.months > 0) "${BanglaUtils.toBanglaDigits(ageResult.months)} মাস " else ""}${if (ageResult.days > 0) "${BanglaUtils.toBanglaDigits(ageResult.days)} দিন" else ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }
                    }
                }

                // 4. Secondary Detailed Breakdown Stats Grid
                Text(
                    text = "অন্যান্য এককে মোট সময়কাল (Detailed Breakdown):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "মোট দিন",
                        value = "${BanglaUtils.toBanglaDigits(ageResult.totalDays)} দিন",
                        icon = Icons.Filled.CalendarToday,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "মোট সপ্তাহ ও দিন",
                        value = "${BanglaUtils.toBanglaDigits(ageResult.totalWeeks)} সপ্তাহ ${if (ageResult.remainingDaysInWeek > 0) "${BanglaUtils.toBanglaDigits(ageResult.remainingDaysInWeek)} দিন" else ""}",
                        icon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "মোট মাস ও দিন",
                        value = "${BanglaUtils.toBanglaDigits(ageResult.totalMonthsApprox)} মাস ${if (ageResult.remainingDaysInMonth > 0) "${BanglaUtils.toBanglaDigits(ageResult.remainingDaysInMonth)} দিন" else ""}",
                        icon = Icons.Filled.CalendarMonth,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "মোট ঘণ্টা (আনুমানিক)",
                        value = "${BanglaUtils.toBanglaDigits(ageResult.totalHoursApprox)} ঘণ্টা",
                        icon = Icons.Filled.AccessTime,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 5. Next Birthday Countdown Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "পরবর্তী জন্মদিন:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${ageResult.nextBirthdayBengali} (${ageResult.nextBirthdayDayOfWeek})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "বাকি ${BanglaUtils.toBanglaDigits(ageResult.daysUntilNextBirthday)} দিন",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = "অনুগ্রহ করে সঠিক তারিখ প্রদান করুন (শেষ তারিখ শুরু তারিখের চেয়ে পরে হতে হবে)।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // Date Pickers
    if (showStartDatePicker) {
        AppDatePickerDialog(
            onDateSelected = { selected ->
                startDateInput = selected
                selectedStudentName = null
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        AppDatePickerDialog(
            onDateSelected = { selected ->
                endDateInput = selected
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    // Student Picker Dialog
    if (showStudentPickerSheet) {
        StudentPickerDialog(
            students = allStudents,
            onSelect = { student ->
                if (student.birthDate.isNotBlank()) {
                    startDateInput = student.birthDate
                    selectedStudentName = "${student.name} (রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}, শ্রেণি: ${student.studentClass})"
                } else {
                    Toast.makeText(context, "এই শিক্ষার্থীর জন্মতারিখ সংরক্ষিত নেই", Toast.LENGTH_SHORT).show()
                }
                showStudentPickerSheet = false
            },
            onDismiss = { showStudentPickerSheet = false }
        )
    }
}

@Composable
private fun AgeValueBlock(
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = unit,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Column {
                Text(text = title, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun StudentPickerDialog(
    students: List<StudentEntity>,
    onSelect: (StudentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.rollNumber.toString().contains(searchQuery) ||
                    it.studentClass.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("শিক্ষার্থী নির্বাচন করুন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("নাম, শ্রেণি বা রোল দিয়ে খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("কোনো শিক্ষার্থী পাওয়া যায়নি", fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered.size) { index ->
                            val s = filtered[index]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(s) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = s.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "শ্রেণি: ${s.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(s.rollNumber)} | জন্ম: ${if (s.birthDate.isNotBlank()) s.birthDate else "নেই"}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )
}
