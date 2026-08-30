package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackupSegmentItem
import com.example.data.model.DriveRestoreMode
import com.example.data.model.DriveSyncTarget
import com.example.data.model.SegmentSyncStatus
import com.example.util.AppErrorLogger
import com.example.util.AppSecurityManager
import com.example.util.BanglaUtils
import com.example.util.ConnectedDriveAccountInfo
import com.example.util.DirectDbUploadResult
import com.example.util.DriveSetupState
import com.example.viewmodel.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GoogleDriveSetupSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val setupState by viewModel.driveSetupState.collectAsState()
    val primaryAccount by viewModel.primaryDriveAccount.collectAsState()
    val secondaryAccount by viewModel.secondaryDriveAccount.collectAsState()
    val lastDbUploadInfo by viewModel.lastDbUploadInfo.collectAsState()

    val isDirectDbUploading by viewModel.isDirectDbUploading.collectAsState()
    val directDbProgressMsg by viewModel.directDbUploadProgressMessage.collectAsState()

    val backupSegments by viewModel.backupSegments.collectAsState()
    val isSegmentedSyncing by viewModel.isSegmentedSyncing.collectAsState()
    val segmentedSyncMsg by viewModel.segmentedSyncProgressMessage.collectAsState()
    val syncCurrent by viewModel.segmentedSyncProgressCurrent.collectAsState()
    val syncTotal by viewModel.segmentedSyncProgressTotal.collectAsState()
    val isSegmentedRestoring by viewModel.isSegmentedRestoring.collectAsState()
    val segmentedRestoreMsg by viewModel.segmentedRestoreProgressMessage.collectAsState()
    val lastSyncTs by viewModel.lastSyncTime.collectAsState()

    val autoSyncMode by viewModel.autoSyncMode.collectAsState()
    val syncImagesEnabled by viewModel.syncImagesEnabled.collectAsState()
    val syncPdfsEnabled by viewModel.syncPdfsEnabled.collectAsState()
    val isAutoSyncing by viewModel.isAutoSyncing.collectAsState()
    val autoSyncStatusMsg by viewModel.autoSyncStatusMessage.collectAsState()
    val lastAutoSyncTs by viewModel.lastAutoSyncTimestamp.collectAsState()

    // Flag for which account is being connected via GoogleSignInLauncher
    var connectingSecondaryAccount by remember { mutableStateOf(false) }

    // Warning Dialog States
    var showSyncConfirmDialog by remember { mutableStateOf(false) }
    var showDbUploadConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreModeDialog by remember { mutableStateOf(false) }
    var showUnlinkPinDialog by remember { mutableStateOf<Boolean?>(null) } // null = closed, false = primary, true = secondary
    var unlinkPinInput by remember { mutableStateOf("") }
    var unlinkPinError by remember { mutableStateOf<String?>(null) }

    // Restore Options State
    var selectedRestoreMode by remember { mutableStateOf(DriveRestoreMode.MERGE) }
    var selectedRestoreTarget by remember { mutableStateOf(DriveSyncTarget.PRIMARY_ONLY) }

    // Sync Target Selection State
    var selectedSyncTarget by remember { mutableStateOf(DriveSyncTarget.BOTH) }
    var selectedDbUploadTarget by remember { mutableStateOf(DriveSyncTarget.PRIMARY_ONLY) }

    var showDiagnosticLogsDialog by remember { mutableStateOf(false) }
    var showSegmentsDetailDialog by remember { mutableStateOf(false) }

    // Launcher for ZIP / JSON file restore
    val restoreZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreFromZipUri(uri, selectedRestoreMode) { success, count ->
                if (success) {
                    Toast.makeText(context, "সফলভাবে $count টি রেকর্ড ব্যাকআপ থেকে রিস্টোর সম্পন্ন হয়েছে", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "ব্যাকআপ রিস্টোর ব্যর্থ হয়েছে", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Launcher for OAuth user consent (Google Drive permission screen)
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppErrorLogger.logInfo("DriveConsent", "ব্যবহারকারী Google Drive ব্যবহারের অনুমতি প্রদান করেছেন (RESULT_OK)")
            viewModel.retryDriveConsent()
        } else {
            val msg = "Google Drive ব্যবহারের অনুমতি প্রদান করা হয়নি"
            AppErrorLogger.logWarning("DriveConsent", msg)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearDriveSetupStatus()
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    Toast.makeText(context, "অ্যাকাউন্ট: ${account.email}", Toast.LENGTH_SHORT).show()
                    viewModel.handleGoogleAccountSelected(account, isSecondary = connectingSecondaryAccount)
                }
            } catch (e: ApiException) {
                val errorDetails = when (e.statusCode) {
                    10 -> "Google Cloud Console-এ SHA-1 বা Package Name (${context.packageName}) কনফিগারেশন মিসিং।"
                    12500 -> "Google Play Services বা ক্লাউড কনসোলে OAuth অনুমোদন সমস্যা।"
                    12501 -> "সাইন-ইন বাতিল করা হয়েছে।"
                    7 -> "ইন্টারনেট সংযোগ পাওয়া যায়নি।"
                    else -> "সাইন-ইন ত্রুটি কোড: ${e.statusCode}"
                }
                AppErrorLogger.logError("GoogleSignIn", errorDetails, e, e.statusCode)
                Toast.makeText(context, errorDetails, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Auto-launch consent screen when required
    LaunchedEffect(setupState) {
        val state = setupState
        if (state is DriveSetupState.NeedsUserConsent) {
            try {
                consentLauncher.launch(state.consentIntent)
            } catch (e: Exception) {
                // fallback handled
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBackupSegments()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_google_drive_setup"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = "Cloud Sync",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "গুগল ড্রাইভ ও দ্বৈত জিমেইল ক্লাউড সিঙ্ক",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "সেটিংস, ডাটাবেস (.db) ও সেগমেন্টেড রেকর্ড স্বয়ংক্রিয় সিঙ্ক ও রিস্টোর",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Syncing / Restoring / Direct Upload Progress Indicator
            AnimatedVisibility(visible = isSegmentedSyncing || isSegmentedRestoring || isDirectDbUploading || setupState is DriveSetupState.Loading) {
                val statusMsg = when {
                    isSegmentedSyncing -> segmentedSyncMsg ?: "সেগমেন্ট সিঙ্ক হচ্ছে..."
                    isSegmentedRestoring -> segmentedRestoreMsg ?: "রিস্টোর হচ্ছে..."
                    isDirectDbUploading -> directDbProgressMsg ?: "ডাটাবেস .db আপলোড হচ্ছে..."
                    else -> (setupState as? DriveSetupState.Loading)?.message ?: "প্রক্রিয়াধীন..."
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = statusMsg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isSegmentedSyncing && syncTotal > 0) {
                            LinearProgressIndicator(
                                progress = { (syncCurrent.toFloat() / syncTotal.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Consent Banner
            AnimatedVisibility(visible = setupState is DriveSetupState.NeedsUserConsent) {
                val consentState = setupState as? DriveSetupState.NeedsUserConsent
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "গুগল ড্রাইভ ব্যবহারের সম্মতি প্রয়োজন (${consentState?.email})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Button(
                            onClick = { consentState?.consentIntent?.let { consentLauncher.launch(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("অনুমোদন দিন", fontSize = 12.sp)
                        }
                    }
                }
            }

            // =========================================================================
            // ACCOUNT SLOTS: PRIMARY & SECONDARY GMAIL
            // =========================================================================
            Text(
                text = "সংযুক্ত ড্রাইভ অ্যাকাউন্টসমূহ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Slot 1: Primary Drive Account
            DriveAccountSlotCard(
                slotTitle = "মূল জিমেইল (Primary Drive)",
                accountInfo = primaryAccount,
                isSecondary = false,
                onConnectClick = {
                    connectingSecondaryAccount = false
                    val intent = viewModel.driveSetupManager.getSignInIntent()
                    googleSignInLauncher.launch(intent)
                },
                onUnlinkClick = {
                    unlinkPinInput = ""
                    unlinkPinError = null
                    showUnlinkPinDialog = false
                },
                onOpenFolderClick = { link ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "ফোল্ডার ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Slot 2: Secondary Drive Account
            DriveAccountSlotCard(
                slotTitle = "দ্বিতীয় জিমেইল (Secondary Backup Drive)",
                accountInfo = secondaryAccount,
                isSecondary = true,
                onConnectClick = {
                    connectingSecondaryAccount = true
                    val intent = viewModel.driveSetupManager.getSignInIntent()
                    googleSignInLauncher.launch(intent)
                },
                onUnlinkClick = {
                    unlinkPinInput = ""
                    unlinkPinError = null
                    showUnlinkPinDialog = true
                },
                onOpenFolderClick = { link ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "ফোল্ডার ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // =========================================================================
            // AUTO SYNC & MEDIA PREFERENCES (MODERN COMPACT CARD)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("card_auto_sync_preferences")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timelapse,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "স্বয়ংক্রিয় ক্লাউড সিঙ্ক ও মিডিয়া অপশন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ডেটা পরিবর্তনে বা নির্ধারিত বিরতিতে স্বয়ংক্রিয় ব্যাকআপ",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Auto-Sync Trigger Selector
                    Text(
                        text = "সিঙ্ক ট্রিগার ও ব্যবধান (Interval):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val modes = listOf(
                        com.example.data.model.AutoSyncMode.ON_DATA_CHANGE,
                        com.example.data.model.AutoSyncMode.INTERVAL_15_MIN,
                        com.example.data.model.AutoSyncMode.INTERVAL_30_MIN,
                        com.example.data.model.AutoSyncMode.INTERVAL_1_HOUR,
                        com.example.data.model.AutoSyncMode.INTERVAL_6_HOURS,
                        com.example.data.model.AutoSyncMode.INTERVAL_DAILY,
                        com.example.data.model.AutoSyncMode.MANUAL
                    )

                    // Horizontally scrollable chips for zero truncation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modes.forEach { mode ->
                            val isSelected = autoSyncMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setAutoSyncMode(mode) },
                                label = {
                                    Text(
                                        text = mode.titleBn,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Live auto sync status or message
                    if (isAutoSyncing || !autoSyncStatusMsg.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isAutoSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                } else {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = autoSyncStatusMsg ?: "অটো-সিঙ্ক সক্রিয়",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Media & Document Sync Toggles
                    Text(
                        text = "মিডিয়া ও ডকুমেন্ট কন্টেন্ট সিঙ্ক:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Toggle 1: Images
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.setSyncImagesEnabled(!syncImagesEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "শিক্ষার্থীর ছবি ও লোগো (Images)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "শিক্ষার্থীদের প্রোফাইল ফটো এবং বিদ্যালয়ের লোগো সিঙ্ক",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Switch(
                            checked = syncImagesEnabled,
                            onCheckedChange = { viewModel.setSyncImagesEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    // Toggle 2: PDFs & Templates
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.setSyncPdfsEnabled(!syncPdfsEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "এডমিট, সিটপ্ল্যান ও প্রত্যয়ন টেমপ্লেট (PDFs)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "প্রিন্ট ও ডকুমেন্ট ফরমেট ক্লাউডে নিরাপদ সংরক্ষণ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Switch(
                            checked = syncPdfsEnabled,
                            onCheckedChange = { viewModel.setSyncPdfsEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }
            }

            // =========================================================================
            // DIRECT .DB BACKUP STATUS & VERIFICATION BANNER
            // =========================================================================
            if (lastDbUploadInfo != null || primaryAccount != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "SQLite .db ব্যাকআপ স্থিতি",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (lastDbUploadInfo?.success == true) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "সফলভাবে আপলোড সম্পন্ন",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (lastDbUploadInfo != null) {
                            val info = lastDbUploadInfo!!
                            Text(
                                text = "ফাইল: ${info.fileName} (${info.fileSizeFormatted})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (info.uploadedAtFormatted.isNotBlank()) {
                                Text(
                                    text = "আপলোড সময়: ${info.uploadedAtFormatted}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (info.fileId.isNotBlank()) {
                                Text(
                                    text = "File ID: ${info.fileId.take(18)}...",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            if (!info.webViewLink.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.webViewLink)))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ড্রাইভ ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ড্রাইভে .db ফাইলটি সরাসরি দেখুন", fontSize = 11.sp)
                                }
                            }
                        } else {
                            Text(
                                text = "আপনার দৃশ্যমান Google Drive ফোল্ডারে সম্পূর্ণ anwesha_school_db.db ফাইল আপলোড করে নিশ্চিত হতে পারবেন।",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // SEGMENTS STATUS BADGES
            // =========================================================================
            val modifiedCount = backupSegments.count { it.status == SegmentSyncStatus.MODIFIED_LOCALLY || it.status == SegmentSyncStatus.NEW_PENDING }
            val syncedCount = backupSegments.count { it.status == SegmentSyncStatus.SYNCED }
            val totalRecords = backupSegments.sumOf { it.recordCount }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
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
                            text = "সেগমেন্ট স্থিতি (মোট ${backupSegments.size}টি ফাইল, সেটিংস অন্তর্ভুক্ত)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showSegmentsDetailDialog = true },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("বিস্তারিত (${backupSegments.size})", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SegmentBadge(
                            label = "সিঙ্কড",
                            count = "$syncedCount",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SegmentBadge(
                            label = "পরিবর্তিত",
                            count = "$modifiedCount",
                            color = if (modifiedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f)
                        )
                        SegmentBadge(
                            label = "মোট রেকর্ড",
                            count = "$totalRecords",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (lastSyncTs > 0L) {
                        val formattedLastSync = remember(lastSyncTs) {
                            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                            BanglaUtils.toBanglaDigits(sdf.format(Date(lastSyncTs)))
                        }
                        Text(
                            text = "সর্বশেষ ক্লাউড সিঙ্ক: $formattedLastSync",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // =========================================================================
            // ACTION BUTTONS (PROTECTED BY WARNING DIALOGS)
            // =========================================================================
            val hasAnyDriveAccount = (primaryAccount != null || secondaryAccount != null)

            if (hasAnyDriveAccount) {
                // Primary 1-Tap Sync Button
                Button(
                    onClick = { showSyncConfirmDialog = true },
                    enabled = !isSegmentedSyncing && !isSegmentedRestoring && !isDirectDbUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("btn_sync_segmented_drive"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (modifiedCount > 0) "ক্লাউডে $modifiedCount টি ফাইল সিঙ্ক করুন (সতর্কবার্তা সহ)" else "সকল ডাটা ক্লাউডে সিঙ্ক করুন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row for Restore & Direct DB Upload
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { showRestoreModeDialog = true },
                        enabled = !isSegmentedSyncing && !isSegmentedRestoring && !isDirectDbUploading,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_restore_segmented_drive"),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ড্রাইভ রিস্টোর (৩ মোড)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = { showDbUploadConfirmDialog = true },
                        enabled = !isSegmentedSyncing && !isSegmentedRestoring && !isDirectDbUploading,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_upload_db_direct"),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সরাসরি .db আপলোড", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Local ZIP / Offline Backup Utilities (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.exportSegmentedZip { intent ->
                            if (intent != null) context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("btn_export_segmented_zip"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderZip,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("জিপ ব্যাকআপ শেয়ার", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = { restoreZipLauncher.launch("*/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("btn_import_segmented_zip"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("জিপ ফাইল রিস্টোর", fontSize = 10.sp)
                }

                IconButton(
                    onClick = { showDiagnosticLogsDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = "Logs",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // =========================================================================
    // 1. WARNING DIALOG: SYNC CONFIRMATION
    // =========================================================================
    if (showSyncConfirmDialog) {
        val bothConnected = (primaryAccount != null && secondaryAccount != null)
        AlertDialog(
            onDismissRequest = { showSyncConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("ক্লাউড সিঙ্ক নিশ্চিতকরণ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "আপনার স্থানীয় ডেটাবেস এবং অ্যাপ সেটিংস গুগল ড্রাইভে আপলোড করা হবে। ড্রাইভের পুরাতন ফাইলের চেয়ে নতুন সংস্করণ সেখানে প্রতিস্থাপিত হবে।",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (bothConnected) {
                        Text(
                            text = "সিঙ্ক গন্তব্য নির্বাচন করুন:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedSyncTarget == DriveSyncTarget.BOTH,
                                onClick = { selectedSyncTarget = DriveSyncTarget.BOTH },
                                label = { Text("উভয় ড্রাইভ", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedSyncTarget == DriveSyncTarget.PRIMARY_ONLY,
                                onClick = { selectedSyncTarget = DriveSyncTarget.PRIMARY_ONLY },
                                label = { Text("মূল ড্রাইভ", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedSyncTarget == DriveSyncTarget.SECONDARY_ONLY,
                                onClick = { selectedSyncTarget = DriveSyncTarget.SECONDARY_ONLY },
                                label = { Text("২য় ড্রাইভ", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSyncConfirmDialog = false
                        viewModel.syncSegmentedBackupToDrive(selectedSyncTarget)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("সিঙ্ক শুরু করুন", fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSyncConfirmDialog = false },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("বাতিল", fontSize = 12.sp)
                }
            }
        )
    }

    // =========================================================================
    // 2. WARNING DIALOG: DIRECT .DB UPLOAD CONFIRMATION
    // =========================================================================
    if (showDbUploadConfirmDialog) {
        val bothConnected = (primaryAccount != null && secondaryAccount != null)
        AlertDialog(
            onDismissRequest = { showDbUploadConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("সরাসরি .db ফাইল আপলোড", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "এটি সম্পূর্ণ SQLite ডাটাবেসের একটি কপি ('anwesha_school_db.db') তৈরি করে আপনার দৃশ্যমান গুগল ড্রাইভ ফোল্ডারে আপলোড করবে। আপনি ফোল্ডার ওপেন করে সরাসরি ফাইলটি দেখতে ও ডাউনলোড করতে পারবেন।",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (bothConnected) {
                        Text(
                            text = "আপলোড গন্তব্য নির্বাচন করুন:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedDbUploadTarget == DriveSyncTarget.PRIMARY_ONLY,
                                onClick = { selectedDbUploadTarget = DriveSyncTarget.PRIMARY_ONLY },
                                label = { Text("মূল ড্রাইভ", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedDbUploadTarget == DriveSyncTarget.SECONDARY_ONLY,
                                onClick = { selectedDbUploadTarget = DriveSyncTarget.SECONDARY_ONLY },
                                label = { Text("২য় ড্রাইভ", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedDbUploadTarget == DriveSyncTarget.BOTH,
                                onClick = { selectedDbUploadTarget = DriveSyncTarget.BOTH },
                                label = { Text("উভয় ড্রাইভ", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDbUploadConfirmDialog = false
                        viewModel.uploadDirectDatabaseToDrive(selectedDbUploadTarget)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(".db আপলোড করুন", fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDbUploadConfirmDialog = false },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("বাতিল", fontSize = 12.sp)
                }
            }
        )
    }

    // =========================================================================
    // 3. WARNING & MODE PICKER DIALOG: RESTORE MODES
    // =========================================================================
    if (showRestoreModeDialog) {
        val bothConnected = (primaryAccount != null && secondaryAccount != null)
        AlertDialog(
            onDismissRequest = { showRestoreModeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("ড্রাইভ রিস্টোর ও মোড নির্বাচন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "গুগল ড্রাইভ থেকে ডেটা ফিরিয়ে আনার জন্য মোড নির্বাচন করুন। এটি সতর্কতার সাথে নির্বাচন করুন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (bothConnected) {
                        Text(
                            text = "কোন ড্রাইভ থেকে রিস্টোর করবেন:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedRestoreTarget == DriveSyncTarget.PRIMARY_ONLY,
                                onClick = { selectedRestoreTarget = DriveSyncTarget.PRIMARY_ONLY },
                                label = { Text("মূল ড্রাইভ", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = selectedRestoreTarget == DriveSyncTarget.SECONDARY_ONLY,
                                onClick = { selectedRestoreTarget = DriveSyncTarget.SECONDARY_ONLY },
                                label = { Text("২য় ড্রাইভ", fontSize = 10.sp) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Mode 1: Clean Replace (Exclude Offline)
                    RestoreModeSelectionCard(
                        title = "১. অফলাইন মুছে সম্পূর্ণ প্রতিস্থাপন (Exclude Offline Data)",
                        subtitle = "স্থানীয় সকল বর্তমান ডেটাবেস টেবিল মুছে দিয়ে ড্রাইভে যা আছে হুবহু তা রিস্টোর করবে।",
                        badge = "ক্লিন রিসেট (সতর্কতা!)",
                        badgeColor = MaterialTheme.colorScheme.error,
                        isSelected = selectedRestoreMode == DriveRestoreMode.EXCLUDE_OFFLINE,
                        onClick = { selectedRestoreMode = DriveRestoreMode.EXCLUDE_OFFLINE }
                    )

                    // Mode 2: Merge (Standard)
                    RestoreModeSelectionCard(
                        title = "২. মার্জ রিস্টোর (Restore and Merge)",
                        subtitle = "ড্রাইভের রেকর্ডের সাথে স্থানীয় রেকর্ড একত্র করবে। একই আইডি থাকলে ক্লাউড সংস্করণ দিয়ে আপডেট হবে।",
                        badge = "প্রস্তাবিত (Safe Merge)",
                        badgeColor = MaterialTheme.colorScheme.primary,
                        isSelected = selectedRestoreMode == DriveRestoreMode.MERGE,
                        onClick = { selectedRestoreMode = DriveRestoreMode.MERGE }
                    )

                    // Mode 3: Smart Merge (Include Offline Data)
                    RestoreModeSelectionCard(
                        title = "৩. অফলাইন ডেটা অক্ষত রেখে রিস্টোর (Included Offline Data)",
                        subtitle = "স্থানীয় কোন নতুন আনসিঙ্কড রেকর্ড অক্ষত রাখবে এবং ক্লাউডের বাকি ডেটা যোগ করবে।",
                        badge = "স্মার্ট মার্জ",
                        badgeColor = MaterialTheme.colorScheme.tertiary,
                        isSelected = selectedRestoreMode == DriveRestoreMode.INCLUDE_OFFLINE,
                        onClick = { selectedRestoreMode = DriveRestoreMode.INCLUDE_OFFLINE }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreModeDialog = false
                        viewModel.restoreSegmentedBackupFromDrive(
                            target = selectedRestoreTarget,
                            mode = selectedRestoreMode
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedRestoreMode == DriveRestoreMode.EXCLUDE_OFFLINE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("রিস্টোর নিশ্চিত করুন", fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRestoreModeDialog = false },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("বাতিল", fontSize = 12.sp)
                }
            }
        )
    }

    // =========================================================================
    // 4. UNLINK WARNING & SECURITY PIN VERIFICATION DIALOG
    // =========================================================================
    if (showUnlinkPinDialog != null) {
        val isSecondary = showUnlinkPinDialog!!
        val isPinActive = AppSecurityManager.isPinConfigured(context)
        val targetName = if (isSecondary) "দ্বিতীয় গুগল ড্রাইভ অ্যাকাউন্ট" else "মূল গুগল ড্রাইভ অ্যাকাউন্ট"

        AlertDialog(
            onDismissRequest = { showUnlinkPinDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "$targetName সংযোগ বিচ্ছিন্ন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "সতর্কতা: সংযোগ বিচ্ছিন্ন করলে ক্লাউড ব্যাকআপ ও স্বয়ংক্রিয় সিঙ্ক বন্ধ হবে। ড্রাইভের বর্তমান ফাইল অক্ষত থাকবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isPinActive) {
                        Text(
                            text = "নিরাপত্তা নিশ্চিত করতে ৪-সংখ্যার অ্যাডমিন পিন (PIN) লিখুন:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        OutlinedTextField(
                            value = unlinkPinInput,
                            onValueChange = {
                                if (it.length <= 6) {
                                    unlinkPinInput = it
                                    unlinkPinError = null
                                }
                            },
                            label = { Text("সিকিউরিটি পিন") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = unlinkPinError != null,
                            supportingText = unlinkPinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tf_unlink_pin"),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredPin = if (isPinActive) unlinkPinInput.trim() else null
                        if (isSecondary) {
                            viewModel.disconnectSecondaryDrive(enteredPin) { success, msg ->
                                if (success) {
                                    showUnlinkPinDialog = null
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    unlinkPinError = msg
                                }
                            }
                        } else {
                            viewModel.disconnectPrimaryDrive(enteredPin) { success, msg ->
                                if (success) {
                                    showUnlinkPinDialog = null
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    unlinkPinError = msg
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("নিশ্চিত ও বিচ্ছিন্ন করুন", fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showUnlinkPinDialog = null },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("বাতিল", fontSize = 12.sp)
                }
            }
        )
    }

    // Segments Detail Dialog
    if (showSegmentsDetailDialog) {
        AlertDialog(
            onDismissRequest = { showSegmentsDetailDialog = false },
            title = {
                Text(
                    text = "সেগমেন্টেড ডাটাবেস ও সেটিংস ফাইল তালিকা",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(backupSegments) { seg ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = seg.titleBn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${seg.fileName} • ${seg.recordCount}টি রেকর্ড",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val statusText = when (seg.status) {
                                    SegmentSyncStatus.SYNCED -> "সিঙ্কড"
                                    SegmentSyncStatus.MODIFIED_LOCALLY -> "পরিবর্তিত"
                                    SegmentSyncStatus.NEW_PENDING -> "নতুন"
                                    SegmentSyncStatus.SYNCING -> "সিঙ্ক হচ্ছে"
                                    SegmentSyncStatus.SKIPPED_UNCHANGED -> "অপরিবর্তিত"
                                    SegmentSyncStatus.ERROR -> "ত্রুটি"
                                }
                                val statusColor = when (seg.status) {
                                    SegmentSyncStatus.SYNCED, SegmentSyncStatus.SKIPPED_UNCHANGED -> MaterialTheme.colorScheme.primary
                                    SegmentSyncStatus.MODIFIED_LOCALLY, SegmentSyncStatus.NEW_PENDING -> MaterialTheme.colorScheme.error
                                    SegmentSyncStatus.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = statusColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSegmentsDetailDialog = false }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }

    // Diagnostic Logs Fullscreen Dialog
    if (showDiagnosticLogsDialog) {
        FullScreenErrorLogsDialog(
            onDismissRequest = { showDiagnosticLogsDialog = false }
        )
    }
}

@Composable
private fun DriveAccountSlotCard(
    slotTitle: String,
    accountInfo: ConnectedDriveAccountInfo?,
    isSecondary: Boolean,
    onConnectClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    onOpenFolderClick: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (accountInfo != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = slotTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (accountInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "সংযুক্ত (Active)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (accountInfo != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountInfo.email,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "ফোল্ডার: ${accountInfo.folderName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!accountInfo.folderWebViewLink.isNullOrBlank()) {
                            IconButton(
                                onClick = { onOpenFolderClick(accountInfo.folderWebViewLink) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = "Open Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onUnlinkClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = "Unlink with PIN",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isSecondary) "২য় ড্রাইভ ব্যাকআপের জন্য যুক্ত করুন" else "মূল গুগল ড্রাইভ সংযুক্ত করা হয়নি",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSecondary) "যুক্ত করুন" else "সংযোগ করুন", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreModeSelectionCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) badgeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, if (isSelected) badgeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SegmentBadge(
    label: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = color
            )
        }
    }
}
