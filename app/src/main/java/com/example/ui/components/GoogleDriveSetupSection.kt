package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackupSegmentItem
import com.example.data.model.SegmentSyncStatus
import com.example.util.AppErrorLogger
import com.example.util.BanglaUtils
import com.example.util.ConnectedDriveAccountInfo
import com.example.util.DriveSetupState
import com.example.util.GoogleDriveHelper
import com.example.viewmodel.MainViewModel
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
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
    val connectedAccount by viewModel.driveConnectedAccount.collectAsState()

    val backupSegments by viewModel.backupSegments.collectAsState()
    val isSegmentedSyncing by viewModel.isSegmentedSyncing.collectAsState()
    val segmentedSyncMsg by viewModel.segmentedSyncProgressMessage.collectAsState()
    val syncCurrent by viewModel.segmentedSyncProgressCurrent.collectAsState()
    val syncTotal by viewModel.segmentedSyncProgressTotal.collectAsState()
    val isSegmentedRestoring by viewModel.isSegmentedRestoring.collectAsState()
    val segmentedRestoreMsg by viewModel.segmentedRestoreProgressMessage.collectAsState()
    val lastSyncTs by viewModel.lastSyncTime.collectAsState()

    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var showDiagnosticLogsDialog by remember { mutableStateOf(false) }
    var showSegmentsDetailDialog by remember { mutableStateOf(false) }
    var isAppDataUploading by remember { mutableStateOf(false) }
    var lastAppDataUploadTime by remember { mutableStateOf<String?>(null) }
    var lastAppDataFileId by remember { mutableStateOf<String?>(null) }

    // Launcher for ZIP / JSON file restore
    val restoreZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreFromZipUri(uri) { success, count ->
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

    // Function to perform background upload to appDataFolder using GoogleDriveHelper
    val performAppDataUpload: (GoogleSignInAccount) -> Unit = { account ->
        coroutineScope.launch {
            isAppDataUploading = true
            AppErrorLogger.logInfo("DriveUpload", "appDataFolder-এ ডাটাবেস আপলোড শুরু হচ্ছে... অ্যাকাউন্ট: ${account.email}")
            Toast.makeText(context, "appDataFolder এ school_db.db আপলোড শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
            val uploadResult = GoogleDriveHelper.uploadDatabaseToAppDataFolder(
                context = context,
                account = account,
                databaseFileName = "school_db.db"
            )
            isAppDataUploading = false

            uploadResult.fold(
                onSuccess = { fileId ->
                    lastAppDataFileId = fileId
                    val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                    lastAppDataUploadTime = BanglaUtils.toBanglaDigits(timeStr)
                    AppErrorLogger.logInfo("DriveUpload", "school_db.db সফলভাবে appDataFolder-এ আপলোড হয়েছে (File ID: $fileId)")
                    Toast.makeText(
                        context,
                        "school_db.db সফলভাবে appDataFolder এ আপলোড হয়েছে!",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = { error ->
                    val consentIntent = (error as? UserRecoverableAuthIOException)?.intent
                        ?: (error.cause as? UserRecoverableAuthException)?.intent
                        ?: (error as? UserRecoverableAuthException)?.intent

                    if (consentIntent != null) {
                        AppErrorLogger.logWarning("DriveUpload", "OAuth Remote Consent প্রয়োজন। অনুমতি স্ক্রিন দেখানো হচ্ছে...")
                        Toast.makeText(context, "Google Drive ব্যবহারের সম্মতি প্রদান করুন...", Toast.LENGTH_LONG).show()
                        try {
                            consentLauncher.launch(consentIntent)
                        } catch (ex: Exception) {
                            AppErrorLogger.logError("DriveUpload", "Consent Intent লঞ্চ ব্যর্থ: ${ex.message}", ex)
                        }
                    } else {
                        AppErrorLogger.logError("DriveUpload", "ডাটাবেস আপলোড ব্যর্থ: ${error.localizedMessage}", error)
                        Toast.makeText(
                            context,
                            "আপলোড ত্রুটি: ${error.localizedMessage ?: "ব্যর্থ হয়েছে"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
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
                    viewModel.handleGoogleAccountSelected(account)
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
                // fallback UI handles it
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
                        text = "সেগমেন্টেড ব্যাকআপ ও ক্লাউড সিঙ্ক",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "আলাদা JSON ফাইলে বিভক্ত ও শুধুমাত্র পরিবর্তিত ডেটা ক্লাউডে সিঙ্ক",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Syncing / Restoring Progress Indicator
            AnimatedVisibility(visible = isSegmentedSyncing || isSegmentedRestoring || isAppDataUploading || setupState is DriveSetupState.Loading) {
                val statusMsg = when {
                    isSegmentedSyncing -> segmentedSyncMsg ?: "সেগমেন্ট সিঙ্ক হচ্ছে..."
                    isSegmentedRestoring -> segmentedRestoreMsg ?: "রিস্টোর হচ্ছে..."
                    isAppDataUploading -> "school_db.db আপলোড হচ্ছে..."
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

            // Segments Summary Card
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
                            text = "সেগমেন্ট স্থিতি (মোট ${backupSegments.size}টি ফাইল)",
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

            // Actions for Connected vs Non-Connected
            if (connectedAccount != null) {
                val acc = connectedAccount!!
                // Account Info Compact Line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${acc.email} (${acc.folderName})",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showDisconnectConfirmDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Primary 1-Tap Sync Button (Differential: Uploads changed files only)
                Button(
                    onClick = { viewModel.syncSegmentedBackupToDrive() },
                    enabled = !isSegmentedSyncing && !isSegmentedRestoring,
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
                        text = if (modifiedCount > 0) "ক্লাউডে $modifiedCount টি পরিবর্তিত ফাইল সিঙ্ক করুন" else "সকল ফাইল সিঙ্কড (পুনরায় সিঙ্ক করুন)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Secondary Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.restoreSegmentedBackupFromDrive() },
                        enabled = !isSegmentedSyncing && !isSegmentedRestoring,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
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
                        Text("ড্রাইভ রিস্টোর", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (!acc.folderWebViewLink.isNullOrBlank()) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(acc.folderWebViewLink)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ফোল্ডার ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_open_drive_folder"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ড্রাইভ ফোল্ডার", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                            if (lastAccount != null) performAppDataUpload(lastAccount)
                            else googleSignInLauncher.launch(viewModel.driveSetupManager.getSignInIntent())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
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
                        Text(".db ব্যাকআপ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Not Connected Button
                Button(
                    onClick = {
                        val intent = viewModel.driveSetupManager.getSignInIntent()
                        googleSignInLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("btn_select_google_account"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "জিমেইল নির্বাচন করে ক্লাউড সংযোগ করুন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                    Text("জিপ/ফাইল রিস্টোর", fontSize = 10.sp)
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

    // Segments Detail Dialog
    if (showSegmentsDetailDialog) {
        AlertDialog(
            onDismissRequest = { showSegmentsDetailDialog = false },
            title = {
                Text(
                    text = "সেগমেন্টেড ডাটাবেস ফাইল তালিকা",
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

    // Disconnect Confirmation Dialog
    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("গুগল ড্রাইভ সংযোগ বিচ্ছিন্ন করবেন?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text("সংযোগ বিচ্ছিন্ন করলে স্বয়ংক্রিয় ক্লাউড সিঙ্ক বন্ধ হবে। ড্রাইভের বর্তমান ব্যাকআপ অক্ষত থাকবে।", style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmDialog = false
                        viewModel.disconnectDriveAccount {
                            Toast.makeText(context, "সংযোগ বিচ্ছিন্ন করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("বিচ্ছিন্ন করুন", fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDisconnectConfirmDialog = false },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("বাতিল", fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun SegmentBadge(
    label: String,
    count: String,
    color: androidx.compose.ui.graphics.Color,
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
