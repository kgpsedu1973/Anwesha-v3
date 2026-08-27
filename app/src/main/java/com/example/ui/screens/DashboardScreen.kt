package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BanglaUtils
import com.example.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSection: (String) -> Unit
) {
    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val students by viewModel.allStudents.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val formulaRules by viewModel.formulaRules.collectAsState()

    // Calculated Dynamic Values
    val totalStudents = students.size
    val currentStudents = students.filter { it.status == "Current" }
    val totalBoys = currentStudents.count { it.gender == "ছাত্র" }
    val totalGirls = currentStudents.count { it.gender == "ছাত্রী" }
    val specialNeedsCount = currentStudents.count { it.isSpecialNeeds }

    // Internal vs External calculation based on dynamic formula / village
    val internalCount = currentStudents.count { viewModel.getStudentCategory(it) == "অভ্যন্তরীণ" }
    val externalCount = currentStudents.count { viewModel.getStudentCategory(it) == "বহিরাগত" }

    // Classwise count map
    val classCounts = currentStudents.groupBy { it.studentClass }
        .mapValues { it.value.size }

    // School Profile Logo
    val schoolLogoBitmap = remember(schoolInfo?.logoUri) {
        try {
            val uriStr = schoolInfo?.logoUri
            if (!uriStr.isNullOrBlank() && uriStr.startsWith("data:image")) {
                val b64 = uriStr.substringAfter("base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // School Banner Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_header_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (schoolLogoBitmap != null) {
                        Image(
                            bitmap = schoolLogoBitmap.asImageBitmap(),
                            contentDescription = "বিদ্যালয়ের প্রোফাইল ছবি",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = "School Logo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schoolInfo?.schoolName ?: "১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = schoolInfo?.tagline ?: "জ্ঞান, মনন ও স্বপ্নের সোপান",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "EMIS Code: ${schoolInfo?.emisCode ?: "134251"} • প্রধান শিক্ষক: ${schoolInfo?.headTeacherName ?: "মো: রফিকুল ইসলাম"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!schoolInfo?.email.isNullOrBlank()) {
                        Text(
                            text = "ইমেইল: ${schoolInfo?.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(
                    onClick = { onNavigateToSection("settings") },
                    modifier = Modifier.testTag("btn_dashboard_edit_school")
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit School Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Section Title: Interactive Drill-Down Overview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "শিক্ষার্থী সারসংক্ষেপ ও ডেটা বিশ্লেষণ",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "ট্যাপ করে বিস্তারিত দেখুন ➔",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Summary Grid Row 1: Total, Boys, Girls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "মোট শিক্ষার্থী",
                value = BanglaUtils.toBanglaDigits(totalStudents),
                subText = "সকল নথিভুক্ত",
                icon = Icons.Filled.Groups,
                containerColor = Color(0xFFE8F5E9),
                contentColor = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterClass.value = null
                    viewModel.filterGender.value = null
                    viewModel.filterStatus.value = "ALL"
                    onNavigateToSection("students")
                }
            )
            SummaryCard(
                title = "মোট ছাত্র",
                value = BanglaUtils.toBanglaDigits(totalBoys),
                subText = "ছেলে শিক্ষার্থী",
                icon = Icons.Filled.Male,
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF1565C0),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterGender.value = "ছাত্র"
                    viewModel.filterClass.value = null
                    viewModel.filterStatus.value = "Current"
                    onNavigateToSection("students")
                }
            )
            SummaryCard(
                title = "মোট ছাত্রী",
                value = BanglaUtils.toBanglaDigits(totalGirls),
                subText = "মেয়ে শিক্ষার্থী",
                icon = Icons.Filled.Female,
                containerColor = Color(0xFFFCE4EC),
                contentColor = Color(0xFFC2185B),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterGender.value = "ছাত্রী"
                    viewModel.filterClass.value = null
                    viewModel.filterStatus.value = "Current"
                    onNavigateToSection("students")
                }
            )
        }

        // Summary Grid Row 2: Special Needs, Internal, External
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "বিশেষ চাহিদা",
                value = BanglaUtils.toBanglaDigits(specialNeedsCount),
                subText = "বিশেষ সুবিধাপ্রাপ্ত",
                icon = Icons.Filled.Accessibility,
                containerColor = Color(0xFFFFF3E0),
                contentColor = Color(0xFFEF6C00),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.searchQuery.value = ""
                    viewModel.filterClass.value = null
                    onNavigateToSection("students")
                }
            )
            SummaryCard(
                title = "অভ্যন্তরীণ শিক্ষার্থী",
                value = BanglaUtils.toBanglaDigits(internalCount),
                subText = "স্থানীয় গ্রামসমূহ",
                icon = Icons.Filled.Home,
                containerColor = Color(0xFFF3E5F5),
                contentColor = Color(0xFF6A1B9A),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterClass.value = null
                    onNavigateToSection("students")
                }
            )
            SummaryCard(
                title = "বহিরাগত শিক্ষার্থী",
                value = BanglaUtils.toBanglaDigits(externalCount),
                subText = "বাইরের গ্রামসমূহ",
                icon = Icons.Filled.DirectionsBus,
                containerColor = Color(0xFFEDE7F6),
                contentColor = Color(0xFF4527A0),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterClass.value = null
                    onNavigateToSection("students")
                }
            )
        }

        // Classwise breakdown chips (with instant drill-down filter)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "শ্রেণিভিত্তিক শিক্ষার্থী (ফিল্টার করতে ট্যাপ করুন):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val defaultClasses = listOf("প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
                    items(defaultClasses) { className ->
                        val count = classCounts[className] ?: 0
                        AssistChip(
                            onClick = {
                                viewModel.filterClass.value = className
                                viewModel.filterGender.value = null
                                onNavigateToSection("students")
                            },
                            label = { Text("$className: ${BanglaUtils.toBanglaDigits(count)} জন", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.Class, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        )
                    }
                }
            }
        }

        // Quick Actions Section Title
        Text(
            text = "দ্রুত কার্যক্রম (Quick Actions)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Clean, Focused Quick Actions Grid
        val quickActions = listOf(
            QuickActionItem("শিক্ষার্থী তালিকা", Icons.Filled.People, "students", Color(0xFF00695C)),
            QuickActionItem("টুলস ও প্রবেশপত্র মেকার", Icons.Filled.Widgets, "tools_hub", Color(0xFF4F46E5)),
            QuickActionItem("বিদ্যালয়ের তথ্য ও প্রোফাইল", Icons.Filled.School, "settings", Color(0xFFD81B60)),
            QuickActionItem("ডেটা রিপোর্ট ও এক্সপোর্ট", Icons.Filled.FileDownload, "settings", Color(0xFF1565C0))
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quickActions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { action ->
                        QuickActionButton(
                            item = action,
                            onClick = { onNavigateToSection(action.targetRoute) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    subText: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag("summary_card_${title}"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.9f)
            )
            Text(
                text = subText,
                fontSize = 10.sp,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}

data class QuickActionItem(
    val label: String,
    val icon: ImageVector,
    val targetRoute: String,
    val accentColor: Color
)

@Composable
fun QuickActionButton(
    item: QuickActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable { onClick() }
            .testTag("btn_quick_action_${item.targetRoute}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(item.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = item.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = item.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
