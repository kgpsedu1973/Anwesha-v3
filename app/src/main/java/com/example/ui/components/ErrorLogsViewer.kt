package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.AppErrorLogger
import com.example.util.AppLogEntry
import com.example.util.BanglaUtils
import com.example.util.LogLevel

@Composable
fun ErrorLogsViewerSection(
    modifier: Modifier = Modifier,
    onOpenFullScreenDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val logs by AppErrorLogger.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs else logs.filter { it.level == filterLevel }
    }

    val errorCount = remember(logs) { logs.count { it.level == LogLevel.ERROR } }
    val warningCount = remember(logs) { logs.count { it.level == LogLevel.WARNING } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary & Action Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (errorCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (errorCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (errorCount > 0) Icons.Filled.ReportProblem else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "মোট লগ: ${BanglaUtils.toBanglaDigits(logs.size.toString())} টি",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "এরর: ${BanglaUtils.toBanglaDigits(errorCount.toString())} | সতর্কতা: ${BanglaUtils.toBanglaDigits(warningCount.toString())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Copy Report Button
                    IconButton(
                        onClick = {
                            val report = AppErrorLogger.getExportableDiagnosticReport(context)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("App Error Diagnostic", report))
                            Toast.makeText(context, "সম্পূর্ণ ডায়াগনস্টিক রিপোর্ট কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Report",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Clear Button
                    IconButton(
                        onClick = {
                            AppErrorLogger.clear()
                            Toast.makeText(context, "লগসমূহ পরিষ্কার করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterLevel == null,
                onClick = { filterLevel = null },
                label = { Text("সকল (${BanglaUtils.toBanglaDigits(logs.size.toString())})") },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = filterLevel == LogLevel.ERROR,
                onClick = { filterLevel = if (filterLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                label = { Text("এরর (${BanglaUtils.toBanglaDigits(errorCount.toString())})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = filterLevel == LogLevel.WARNING,
                onClick = { filterLevel = if (filterLevel == LogLevel.WARNING) null else LogLevel.WARNING },
                label = { Text("সতর্কতা (${BanglaUtils.toBanglaDigits(warningCount.toString())})") },
                shape = RoundedCornerShape(8.dp)
            )
        }

        // List of Logs
        if (filteredLogs.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোনো এরর বা সমস্যা পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                filteredLogs.take(15).forEach { entry ->
                    LogItemCard(entry = entry)
                }
                if (filteredLogs.size > 15) {
                    TextButton(
                        onClick = onOpenFullScreenDialog,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("সব ${BanglaUtils.toBanglaDigits(filteredLogs.size.toString())}টি লগ বিস্তারিত দেখুন...")
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(entry: AppLogEntry) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val (bgColor, borderColor, icon, iconTint) = when (entry.level) {
        LogLevel.ERROR -> Quadruple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
            Icons.Filled.ErrorOutline,
            MaterialTheme.colorScheme.error
        )
        LogLevel.WARNING -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
            Icons.Filled.WarningAmber,
            MaterialTheme.colorScheme.tertiary
        )
        LogLevel.INFO -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            Icons.Filled.Info,
            MaterialTheme.colorScheme.primary
        )
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "[${entry.tag}]",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                    if (entry.errorCode != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Code: ${entry.errorCode}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = entry.getFormattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )

            AnimatedVisibility(visible = expanded && !entry.stackTrace.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "বিস্তারিত স্ট্যাকট্রেস (Stack Trace):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Error Trace", entry.stackTrace))
                                Toast.makeText(context, "স্ট্যাকট্রেস কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Trace",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = entry.stackTrace ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun FullScreenErrorLogsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val logs by AppErrorLogger.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs else logs.filter { it.level == filterLevel }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "অ্যাপ এরর ও ডায়াগনস্টিক লগ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Action controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filterLevel == null,
                            onClick = { filterLevel = null },
                            label = { Text("সব (${logs.size})") }
                        )
                        FilterChip(
                            selected = filterLevel == LogLevel.ERROR,
                            onClick = { filterLevel = if (filterLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                            label = { Text("এরর (${logs.count { it.level == LogLevel.ERROR }})") }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val report = AppErrorLogger.getExportableDiagnosticReport(context)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("App Error Diagnostic", report))
                                Toast.makeText(context, "রিপোর্ট কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("রিপোর্ট কপি")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        LogItemCard(entry = entry)
                    }
                }
            }
        }
    }
}
