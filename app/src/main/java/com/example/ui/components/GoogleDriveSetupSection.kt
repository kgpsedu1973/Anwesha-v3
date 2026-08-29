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
import androidx.compose.foundation.layout.*
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
import com.example.util.AppErrorLogger
import com.example.util.BanglaUtils
import com.example.util.ConnectedDriveAccountInfo
import com.example.util.DriveSetupState
import com.example.util.GoogleDriveHelper
import com.example.viewmodel.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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
    val connectedAccount by viewModel.driveConnectedAccount.collectAsState()
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var showDiagnosticLogsDialog by remember { mutableStateOf(false) }
    var isAppDataUploading by remember { mutableStateOf(false) }
    var lastAppDataUploadTime by remember { mutableStateOf<String?>(null) }
    var lastAppDataFileId by remember { mutableStateOf<String?>(null) }

    // Launcher for OAuth user consent (Google Drive permission screen)
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppErrorLogger.logInfo("DriveConsent", "ব্যবহারকারী Google Drive ব্যবহারের অনুমতি প্রদান করেছেন (RESULT_OK)")
            viewModel.retryDriveConsent()
        } else {
            val msg = "Google Drive ব্যবহারের অনুমতি প্রদান করা হয়নি (Result Code: ${result.resultCode})"
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
            Toast.makeText(context, "Google Drive appDataFolder এ school_db.db আপলোড শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
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
                        "school_db.db সফলভাবে appDataFolder এ আপলোড হয়েছে! (ID: ${fileId.take(12)}...)",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = { error ->
                    AppErrorLogger.logError("DriveUpload", "ডাটাবেস আপলোড ব্যর্থ হয়েছে: ${error.localizedMessage}", error)
                    Toast.makeText(
                        context,
                        "আপলোড ত্রুটি: ${error.localizedMessage ?: "ব্যর্থ হয়েছে"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // Dedicated ActivityResultLauncher for Google Sign-In & appDataFolder upload
    val appDataFolderSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        AppErrorLogger.logInfo("GoogleSignIn", "appDataFolderSignInLauncher Result: resultCode=${result.resultCode}, hasData=${data != null}")
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                AppErrorLogger.logInfo("GoogleSignIn", "সাইন-ইন সফল: ${account.email} (ID: ${account.id})")
                Toast.makeText(context, "নির্বাচিত অ্যাকাউন্ট: ${account.email}", Toast.LENGTH_SHORT).show()
                viewModel.handleGoogleAccountSelected(account)
                performAppDataUpload(account)
            } catch (e: ApiException) {
                val errorDetails = when (e.statusCode) {
                    10 -> "Error 10 (DEVELOPER_ERROR): Google Cloud Console-এ SHA-1 বা Package Name (${context.packageName}) কনফিগারেশন মিসিং।"
                    12500 -> "Error 12500 (SIGN_IN_FAILED): Google Play Services বা ক্লাউড কনসোলে OAuth ক্লায়েন্ট অনুমোদন সমস্যা।"
                    12501 -> "Error 12501 (SIGN_IN_CANCELLED): ব্যবহারকারী সাইন-ইন বাতিল করেছেন।"
                    7 -> "Error 7 (NETWORK_ERROR): ইন্টারনেট সংযোগ পাওয়া যায়নি।"
                    else -> "সাইন-ইন ত্রুটি কোড: ${e.statusCode} (${e.localizedMessage ?: "ত্রুটি"})"
                }
                AppErrorLogger.logError("GoogleSignIn", errorDetails, e, e.statusCode)
                Log.e("GoogleSignIn", "SignIn failed: statusCode=${e.statusCode}, message=${e.localizedMessage}", e)
                Toast.makeText(context, errorDetails, Toast.LENGTH_LONG).show()
            }
        } else {
            val msg = "কোনো জিমেইল নির্বাচন করা হয়নি (Result Code: ${result.resultCode})"
            AppErrorLogger.logWarning("GoogleSignIn", msg)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for initial Google Sign In / Account Picker
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        AppErrorLogger.logInfo("GoogleSignIn", "googleSignInLauncher Result: resultCode=${result.resultCode}, hasData=${data != null}")
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    AppErrorLogger.logInfo("GoogleSignIn", "অ্যাকাউন্ট সিলেক্টেড: ${account.email}")
                    Toast.makeText(context, "অ্যাকাউন্ট: ${account.email}", Toast.LENGTH_SHORT).show()
                    viewModel.handleGoogleAccountSelected(account)
                } else {
                    AppErrorLogger.logWarning("GoogleSignIn", "অ্যাকাউন্ট নাল পাওয়া গেছে")
                    Toast.makeText(context, "কোনো অ্যাকাউন্ট নির্বাচন করা হয়নি", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                val errorDetails = when (e.statusCode) {
                    10 -> "Error 10 (DEVELOPER_ERROR): Google Cloud Console-এ SHA-1 বা Package Name (${context.packageName}) কনফিগারেশন মিসিং।"
                    12500 -> "Error 12500 (SIGN_IN_FAILED): Google Play Services বা ক্লাউড কনসোলে OAuth ক্লায়েন্ট অনুমোদন সমস্যা।"
                    12501 -> "Error 12501 (SIGN_IN_CANCELLED): ব্যবহারকারী সাইন-ইন বাতিল করেছেন।"
                    7 -> "Error 7 (NETWORK_ERROR): ইন্টারনেট সংযোগ পাওয়া যায়নি।"
                    else -> "অ্যাকাউন্ট নির্বাচন ব্যর্থ (Error ${e.statusCode}): ${e.localizedMessage ?: "ত্রুটি"}"
                }
                AppErrorLogger.logError("GoogleSignIn", errorDetails, e, e.statusCode)
                Log.e("GoogleSignIn", "Account selection failed: statusCode=${e.statusCode}, message=${e.localizedMessage}", e)
                Toast.makeText(context, errorDetails, Toast.LENGTH_LONG).show()
            }
        } else {
            val msg = "কোনো জিমেইল নির্বাচন করা হয়নি (Result Code: ${result.resultCode})"
            AppErrorLogger.logWarning("GoogleSignIn", msg)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-launch consent screen when required
    LaunchedEffect(setupState) {
        val state = setupState
        if (state is DriveSetupState.NeedsUserConsent) {
            try {
                consentLauncher.launch(state.consentIntent)
            } catch (e: Exception) {
                // If direct launch fails, fallback button will be shown
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_google_drive_setup"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = "Google Drive",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "গুগল ড্রাইভ ও জিমেইল সংযোগ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "appDataFolder ও ক্লাউডে ডাটাবেস (school_db.db) স্বয়ংক্রিয় ব্যাকআপ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Loading / In-Progress State
            AnimatedVisibility(visible = setupState is DriveSetupState.Loading || isAppDataUploading) {
                val loadingMessage = if (isAppDataUploading) {
                    "Google Drive appDataFolder এ school_db.db আপলোড হচ্ছে..."
                } else {
                    (setupState as? DriveSetupState.Loading)?.message ?: "প্রক্রিয়াধীন..."
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = loadingMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Needs Consent Action Banner
            AnimatedVisibility(visible = setupState is DriveSetupState.NeedsUserConsent) {
                val consentState = setupState as? DriveSetupState.NeedsUserConsent
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "গুগল ড্রাইভ অনুমতি প্রয়োজন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "আপনার জিমেইল (${consentState?.email}) এ স্কুলের জন্য ফোল্ডার তৈরি করতে ড্রাইভ অনুমতি প্রদান করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Button(
                            onClick = {
                                consentState?.consentIntent?.let { intent ->
                                    consentLauncher.launch(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("গুগল ড্রাইভ অ্যাক্সেস অনুমোদন করুন")
                        }
                    }
                }
            }

            // Error State
            AnimatedVisibility(visible = setupState is DriveSetupState.Error) {
                val errorState = setupState as? DriveSetupState.Error
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ত্রুটি ঘটেছে",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorState?.errorMessage ?: "অজানা সমস্যা হয়েছে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearDriveSetupStatus() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Success Message State
            AnimatedVisibility(visible = setupState is DriveSetupState.Success) {
                val successState = setupState as? DriveSetupState.Success
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = successState?.message ?: "সফলভাবে ফোল্ডার তৈরি হয়েছে!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearDriveSetupStatus() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Body Content: Connected vs Not Connected
            if (connectedAccount != null) {
                ConnectedAccountCard(
                    account = connectedAccount!!,
                    isUploading = isAppDataUploading,
                    lastUploadTime = lastAppDataUploadTime,
                    lastFileId = lastAppDataFileId,
                    onUploadDbToAppDataFolder = {
                        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                        if (lastAccount != null) {
                            performAppDataUpload(lastAccount)
                        } else {
                            val intent = GoogleDriveHelper.getSignInIntent(context)
                            appDataFolderSignInLauncher.launch(intent)
                        }
                    },
                    onSwitchAccount = {
                        val intent = viewModel.driveSetupManager.getSignInIntent()
                        googleSignInLauncher.launch(intent)
                    },
                    onDisconnect = {
                        showDisconnectConfirmDialog = true
                    }
                )
            } else {
                NotConnectedSection(
                    onSelectGmailClick = {
                        val intent = viewModel.driveSetupManager.getSignInIntent()
                        googleSignInLauncher.launch(intent)
                    },
                    onDirectAppDataBackupClick = {
                        val intent = GoogleDriveHelper.getSignInIntent(context)
                        appDataFolderSignInLauncher.launch(intent)
                    }
                )
            }

            // Quick Diagnostic / Error Log Button inside Google Drive card
            OutlinedButton(
                onClick = { showDiagnosticLogsDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_open_drive_diagnostic_logs"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "সাইন-ইন ও ড্রাইভ এরর লগ দেখুন (Error Logs)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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
            title = { Text("গুগল ড্রাইভ সংযোগ বিচ্ছিন্ন করবেন?") },
            text = {
                Text("সংযোগ বিচ্ছিন্ন করলে এই অ্যাপ থেকে সরাসরি ড্রাইভে ব্যাকআপ হবে না। তবে পূর্বের সংরক্ষিত ফোল্ডার ড্রাইভে অক্ষত থাকবে।")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmDialog = false
                        viewModel.disconnectDriveAccount {
                            Toast.makeText(context, "গুগল ড্রাইভ সংযোগ বিচ্ছিন্ন করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("বিচ্ছিন্ন করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDisconnectConfirmDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
private fun NotConnectedSection(
    onSelectGmailClick: () -> Unit,
    onDirectAppDataBackupClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "আপনার ফোনের Gmail সিলেক্ট করুন। গুগল ড্রাইভের লুকায়িত appDataFolder এবং ডেডিকেটেড ক্লাউড ফোল্ডারে Room ডাটাবেস (school_db.db) স্বয়ংক্রিয়ভাবে সংরক্ষিত থাকবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Button(
            onClick = onSelectGmailClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_select_google_account"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "ফোনের Gmail অ্যাকাউন্ট নির্বাচন ও সংযোগ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onDirectAppDataBackupClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_direct_appdata_backup"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Google Sign-In করে appDataFolder এ ব্যাকআপ করুন",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConnectedAccountCard(
    account: ConnectedDriveAccountInfo,
    isUploading: Boolean = false,
    lastUploadTime: String? = null,
    lastFileId: String? = null,
    onUploadDbToAppDataFolder: () -> Unit,
    onSwitchAccount: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(account.connectedAt) {
        try {
            val sdf = SimpleDateFormat("dd MMMM, yyyy - hh:mm a", Locale.getDefault())
            BanglaUtils.toBanglaDigits(sdf.format(Date(account.connectedAt)))
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Connected Account Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // User info row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (account.displayName.firstOrNull() ?: account.email.firstOrNull() ?: 'U').uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = account.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "সংযুক্ত",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Folder & AppData Info Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "গুগল ড্রাইভ ফোল্ডার:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = account.folderName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "লুকায়িত স্পেস: appDataFolder (school_db.db)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        if (lastUploadTime != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "সর্বশেষ appData ব্যাকআপ: $lastUploadTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (formattedDate.isNotBlank()) {
                            Text(
                                text = "সংযোগের সময়: $formattedDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Primary appDataFolder Upload Button
                Button(
                    onClick = onUploadDbToAppDataFolder,
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_upload_db_appdata_folder"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("appDataFolder এ ব্যাকআপ আপলোড হচ্ছে...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Room ডাটাবেস (school_db.db) appDataFolder এ ব্যাকআপ করুন",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!account.folderWebViewLink.isNullOrBlank()) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(account.folderWebViewLink))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ড্রাইভ লিংক ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("btn_open_drive_folder"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ফোল্ডার দেখুন", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    OutlinedButton(
                        onClick = onSwitchAccount,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_switch_google_account"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("অ্যাকাউন্ট পরিবর্তন", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_disconnect_google_account")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


