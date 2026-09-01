package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ToolItem
import com.example.data.model.ToolStatus
import com.example.ui.screens.admitcard.AdmitCardMakerScreen
import com.example.ui.screens.seatplan.SeatPlanMakerScreen
import com.example.ui.screens.tools.AgeCalculatorScreen
import com.example.ui.screens.tools.AttendanceReportScreen
import com.example.ui.screens.tools.CertificateMakerScreen
import com.example.ui.screens.tools.DocumentScannerScreen
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(
    viewModel: MainViewModel,
    initialToolRoute: String? = null,
    onNavigateToDashboard: () -> Unit = {}
) {
    var selectedToolId by remember { mutableStateOf<String?>(initialToolRoute) }
    var infoDialogTool by remember { mutableStateOf<ToolItem?>(null) }

    BackHandler(enabled = true) {
        if (infoDialogTool != null) {
            infoDialogTool = null
        } else if (selectedToolId != null) {
            selectedToolId = null
        } else {
            onNavigateToDashboard()
        }
    }

    // If a tool is selected, render its specialized view
    if (selectedToolId == "attendance_report") {
        AttendanceReportScreen(
            viewModel = viewModel,
            onNavigateBack = { selectedToolId = null }
        )
        return
    }

    if (selectedToolId == "age_calculator") {
        AgeCalculatorScreen(
            viewModel = viewModel,
            onNavigateBack = { selectedToolId = null }
        )
        return
    }

    if (selectedToolId == "admit_card_maker") {
        AdmitCardMakerScreen(
            viewModel = viewModel,
            onNavigateBack = { selectedToolId = null }
        )
        return
    }

    if (selectedToolId == "seat_plan_maker") {
        SeatPlanMakerScreen(
            viewModel = viewModel,
            onNavigateBack = { selectedToolId = null }
        )
        return
    }

    if (selectedToolId == "certificate_maker" || selectedToolId == "testimonial_maker" || selectedToolId == "proshangshapatra_maker") {
        CertificateMakerScreen(
            viewModel = viewModel,
            onNavigateBack = { selectedToolId = null }
        )
        return
    }

    if (selectedToolId == "doc_scanner" || selectedToolId == "doc_scanner_ocr" || selectedToolId == "document_scanner") {
        DocumentScannerScreen(
            viewModel = viewModel,
            onNavigateBack = { selectedToolId = null }
        )
        return
    }

    val toolsList = remember {
        listOf(
            ToolItem(
                id = "doc_scanner",
                titleBn = "ডকুমেন্ট স্ক্যানার (Document Scanner)",
                titleEn = "Document Scanner & PDF Maker",
                descriptionBn = "ক্যামস্ক্যানারের মতো বর্ডার ক্রপ, ফিল্টার, পিডিএফ তৈরি ও শিক্ষার্থীর নথিপত্র সংরক্ষণ।",
                descriptionEn = "CamScanner style auto edge-crop, filters, PDF export & student document archive.",
                iconName = "DocumentScanner",
                status = ToolStatus.ACTIVE,
                categoryBn = "স্ক্যানার ও ইউটিলিটি"
            ),
            ToolItem(
                id = "attendance_report",
                titleBn = "হাজিরা ও মাসিক উপস্থিতি রিপোর্ট (Attendance Report)",
                titleEn = "Student Attendance & Monthly Report",
                descriptionBn = "ভর্তিভিত্তিক ছাত্র-ছাত্রী পৃথক দৈনিক হাজিরা এন্ট্রি, দিনশেষে ভিসুয়াল কার্ড ও সরকারি মাসিক সার্ভিস রিপোর্ট চার্ট।",
                descriptionEn = "Daily class & gender attendance register, visual day summary card & monthly report chart.",
                iconName = "FactCheck",
                status = ToolStatus.ACTIVE,
                categoryBn = "হিসাব ও ইউটিলিটি"
            ),
            ToolItem(
                id = "age_calculator",
                titleBn = "স্মার্ট বয়স ক্যালকুলেটর (Age Calculator)",
                titleEn = "Smart Age Calculator",
                descriptionBn = "শুরু ও শেষ তারিখ, দিন অন্তর্ভুক্তি (Include days) এবং বিভিন্ন এককে বয়স ও পরবর্তী জন্মদিনের লাইভ গণনা।",
                descriptionEn = "Precise age calculation between two dates with day inclusion toggles and live breakdown.",
                iconName = "Calculate",
                status = ToolStatus.ACTIVE,
                categoryBn = "হিসাব ও ইউটিলিটি"
            ),
            ToolItem(
                id = "admit_card_maker",
                titleBn = "স্মার্ট প্রবেশপত্র ও রুটিন মেকার",
                titleEn = "Admit Card & Routine Maker",
                descriptionBn = "পরীক্ষার প্রবেশপত্র, বিষয়ভিত্তিক রুটিন তৈরি, লাইভ প্রিভিউ ও প্রিন্ট/পিডিএফ এক্সপোর্ট।",
                descriptionEn = "Smart exam admit card with routine generator, live preview & PDF print.",
                iconName = "Badge",
                status = ToolStatus.ACTIVE,
                categoryBn = "পরীক্ষা ও মূল্যায়ন"
            ),
            ToolItem(
                id = "seat_plan_maker",
                titleBn = "সিট প্ল্যান মেকার",
                titleEn = "Seat Plan Maker",
                descriptionBn = "পরীক্ষার কক্ষভিত্তিক আসন বিন্যাস, রোল অনুযায়ী বেঞ্চ স্টিকার ও সিট চার্ট তৈরি।",
                descriptionEn = "Classroom exam seat arrangement, bench stickers & door chart generator.",
                iconName = "EventSeat",
                status = ToolStatus.ACTIVE,
                categoryBn = "পরীক্ষা ও মূল্যায়ন"
            ),
            ToolItem(
                id = "certificate_maker",
                titleBn = "প্রত্যয়নপত্র ও প্রশংসাপত্র মেকার",
                titleEn = "Certificate & Testimonial Maker",
                descriptionBn = "অফিস ও মূল প্রত্যয়নপত্র, লিগ্যাল/A4 ল্যান্ডস্কেপ, সরকারি সিল, স্বয়ংক্রিয় সিরিয়াল ও বাংলা ফন্টসহ এক্সাক্ট প্রিন্ট।",
                descriptionEn = "Primary school student testimonials & certificates with counterfoil and official seals.",
                iconName = "WorkspacePremium",
                status = ToolStatus.ACTIVE,
                categoryBn = "সার্টিফিকেট ও পরিচিতি"
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("tools_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Widgets,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "টুলস হাব (Tools Hub)",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "প্রাতিষ্ঠানিক ইউটিলিটি ও প্রোডাক্টিভিটি সেন্টার",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Text(
                            text = "বিদ্যালয়ের সকল কার্যক্রম সহজ ও স্বয়ংক্রিয় করতে প্রয়োজনীয় সব ইউটিলিটি টুলস এখানে যুক্ত রয়েছে।",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Active Tools Section Header
        item {
            Text(
                text = "উপলব্ধ ও সক্রিয় টুলস",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Active Tools (e.g. Admit Card Maker)
        val activeTools = toolsList.filter { it.status == ToolStatus.ACTIVE }
        items(activeTools) { tool ->
            ToolCard(
                tool = tool,
                onClick = {
                    if (tool.status == ToolStatus.ACTIVE) {
                        selectedToolId = tool.id
                    } else {
                        infoDialogTool = tool
                    }
                }
            )
        }

        // Upcoming / Future Tools Section Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "পরবর্তী আপডেট ও আসন্ন টুলস",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "পরিকল্পিত",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Coming Soon Tools
        val upcomingTools = toolsList.filter { it.status != ToolStatus.ACTIVE }
        items(upcomingTools) { tool ->
            ToolCard(
                tool = tool,
                onClick = { infoDialogTool = tool }
            )
        }
    }

    // Coming Soon / Future Tool Dialog
    if (infoDialogTool != null) {
        val tool = infoDialogTool!!
        AlertDialog(
            onDismissRequest = { infoDialogTool = null },
            icon = {
                Icon(
                    getToolIcon(tool.iconName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = tool.titleBn,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tool.descriptionBn,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 এই ফিচারটি বর্তমান আর্কিটেকচারের সাথে সম্পূর্ণ সামঞ্জস্য রেখে পরবর্তী আপডেটে উন্মুক্ত করা হবে।",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { infoDialogTool = null }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}

@Composable
private fun ToolCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    val isActive = tool.status == ToolStatus.ACTIVE

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(if (isActive) 2.dp else 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("tool_item_${tool.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        getToolIcon(tool.iconName),
                        contentDescription = tool.titleBn,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Text Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title and Status Badge Row with robust weighting to prevent clipping
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tool.titleBn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    )
                    StatusBadge(tool.status)
                }

                // Category & English Subtitle
                Text(
                    text = "${tool.titleEn} • ${tool.categoryBn}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Bengali Description
                Text(
                    text = tool.descriptionBn,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.5.sp
                )
            }

            // Arrow
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(status: ToolStatus) {
    val (bgColor, textColor, text) = when (status) {
        ToolStatus.ACTIVE -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "সক্রিয়")
        ToolStatus.COMING_SOON -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "শীঘ্রই আসছে")
        ToolStatus.PLANNED -> Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), "পরিকল্পিত")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            maxLines = 1
        )
    }
}

private fun getToolIcon(iconName: String): ImageVector {
    return when (iconName) {
        "FactCheck" -> Icons.Filled.FactCheck
        "Calculate" -> Icons.Filled.Calculate
        "Badge" -> Icons.Filled.Badge
        "EventSeat" -> Icons.Filled.EventSeat
        "CalendarMonth" -> Icons.Filled.CalendarMonth
        "WorkspacePremium" -> Icons.Filled.WorkspacePremium
        "DocumentScanner" -> Icons.Filled.DocumentScanner
        "ContactPage" -> Icons.Filled.ContactPage
        "ReceiptLong" -> Icons.Filled.ReceiptLong
        else -> Icons.Filled.Build
    }
}
