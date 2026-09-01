package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val genderTerminology by viewModel.genderTerminology.collectAsState()

    // Calculated Dynamic Values
    val totalStudents = students.size
    val currentStudents = students.filter { it.status == "Current" }
    val totalBoys = currentStudents.count { com.example.util.GenderUtils.isBoy(it.gender) }
    val totalGirls = currentStudents.count { com.example.util.GenderUtils.isGirl(it.gender) }
    val specialNeedsCount = currentStudents.count { it.isSpecialNeeds }

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

    val boysRatio = if (currentStudents.isNotEmpty()) totalBoys.toFloat() / currentStudents.size else 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 1. MODERN SCHOOL HERO CARD
        // =========================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_header_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // School Logo with circle badge border
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (schoolLogoBitmap != null) {
                                Image(
                                    bitmap = schoolLogoBitmap.asImageBitmap(),
                                    contentDescription = "বিদ্যালয়ের ছবি",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.School,
                                    contentDescription = "School Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = schoolInfo?.schoolName ?: "১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!schoolInfo?.tagline.isNullOrBlank()) {
                                Text(
                                    text = schoolInfo?.tagline ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "প্রধান শিক্ষক: ${schoolInfo?.headTeacherName ?: "মো: রফিকুল ইসলাম"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onNavigateToSection("settings") },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .testTag("btn_dashboard_edit_school")
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit School Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Metadata Pill Badges (EMIS, Mobile/Email)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "EMIS: ${schoolInfo?.emisCode ?: "134251"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (!schoolInfo?.phone.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        text = schoolInfo?.phone ?: "",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. HERO METRICS CARD: TOTAL STUDENTS & GENDER RATIO BAR
        // =========================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.filterClass.value = null
                    viewModel.filterGender.value = null
                    viewModel.filterStatus.value = "ALL"
                    onNavigateToSection("students")
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "মোট শিক্ষার্থী সংখ্যা",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = BanglaUtils.toBanglaDigits(totalStudents),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "জন (বর্তমান: ${BanglaUtils.toBanglaDigits(currentStudents.size)})",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = "Students",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Gender Distribution Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ছাত্র: ${BanglaUtils.toBanglaDigits(totalBoys)} জন",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            text = "ছাত্রী: ${BanglaUtils.toBanglaDigits(totalGirls)} জন",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2185B)
                        )
                    }

                    // Dual Color Progress Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (currentStudents.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(boysRatio.coerceIn(0.01f, 0.99f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF1E88E5))
                            )
                            Box(
                                modifier = Modifier
                                    .weight((1f - boysRatio).coerceIn(0.01f, 0.99f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFE91E63))
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. SECONDARY METRICS GRID (4 TILES)
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = genderTerminology.boyLabel,
                value = BanglaUtils.toBanglaDigits(totalBoys),
                subText = "ছেলে শিক্ষার্থী",
                icon = Icons.Filled.Male,
                accentColor = Color(0xFF1976D2),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterGender.value = "ছাত্র"
                    viewModel.filterClass.value = null
                    viewModel.filterStatus.value = "Current"
                    onNavigateToSection("students")
                }
            )
            MetricTile(
                title = genderTerminology.girlLabel,
                value = BanglaUtils.toBanglaDigits(totalGirls),
                subText = "মেয়ে শিক্ষার্থী",
                icon = Icons.Filled.Female,
                accentColor = Color(0xFFD81B60),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterGender.value = "ছাত্রী"
                    viewModel.filterClass.value = null
                    viewModel.filterStatus.value = "Current"
                    onNavigateToSection("students")
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "অভ্যন্তরীণ",
                value = BanglaUtils.toBanglaDigits(internalCount),
                subText = "স্থানীয় শিক্ষার্থী",
                icon = Icons.Filled.Home,
                accentColor = Color(0xFF00897B),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterClass.value = null
                    onNavigateToSection("students")
                }
            )
            MetricTile(
                title = "বহিরাগত",
                value = BanglaUtils.toBanglaDigits(externalCount),
                subText = "বাইরের গ্রামসমূহ",
                icon = Icons.Filled.DirectionsBus,
                accentColor = Color(0xFF7E57C2),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterClass.value = null
                    onNavigateToSection("students")
                }
            )
            MetricTile(
                title = "বিশেষ চাহিদা",
                value = BanglaUtils.toBanglaDigits(specialNeedsCount),
                subText = "বিশেষ সুবিধাপ্রাপ্ত",
                icon = Icons.Filled.Accessibility,
                accentColor = Color(0xFFF57C00),
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.filterSpecialNeeds.value = true
                    viewModel.filterClass.value = null
                    onNavigateToSection("students")
                }
            )
        }

        // =========================================================================
        // 4. CLASS DISTRIBUTION SECTION (WITH VISUAL BARS)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "শ্রেণিভিত্তিক শিক্ষার্থী বিন্যাস",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ফিল্টার করতে ট্যাপ করুন",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val defaultClasses = listOf("প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
                val maxClassCount = defaultClasses.maxOfOrNull { classCounts[it] ?: 0 }?.coerceAtLeast(1) ?: 1

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    defaultClasses.forEach { className ->
                        val count = classCounts[className] ?: 0
                        val ratio = count.toFloat() / maxClassCount

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.filterClass.value = className
                                    viewModel.filterGender.value = null
                                    onNavigateToSection("students")
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = className,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${BanglaUtils.toBanglaDigits(count)} জন",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                ) {
                                    if (count > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(ratio.coerceIn(0.02f, 1f))
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        if (ratio < 1f) {
                                            Spacer(modifier = Modifier.weight(1f - ratio))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 5. QUICK ACTIONS HUB
        // =========================================================================
        Text(
            text = "দ্রুত কার্যক্রম (Quick Actions)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val quickActions = listOf(
            QuickActionItem("শিক্ষার্থী তালিকা", Icons.Filled.People, "students", Color(0xFF00695C)),
            QuickActionItem("টুলস ও আইডি কার্ড", Icons.Filled.Badge, "tools_hub", Color(0xFF4F46E5)),
            QuickActionItem("বিদ্যালয়ের তথ্য", Icons.Filled.School, "settings", Color(0xFFD81B60)),
            QuickActionItem("সেটিংস ও ব্যাকআপ", Icons.Filled.Settings, "settings", Color(0xFF1565C0))
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

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    subText: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("metric_tile_${title}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subText,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            .height(76.dp)
            .clickable { onClick() }
            .testTag("btn_quick_action_${item.targetRoute}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(item.accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = item.accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = item.label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
