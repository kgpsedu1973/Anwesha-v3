package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.SchoolInfoEntity
import com.example.data.model.DriveRestoreMode
import com.example.data.model.DriveSyncTarget
import com.example.util.BanglaUtils
import com.example.util.CloudBackupDiscoveryResult
import com.example.viewmodel.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

enum class SetupOptionTab {
    CREATE_NEW,
    GOOGLE_RESTORE,
    LOCAL_RESTORE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialSchoolSetupWindow(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(SetupOptionTab.CREATE_NEW) }

    // Form fields for New School
    var schoolName by remember { mutableStateOf("") }
    var eiinCode by remember { mutableStateOf("") }
    var headTeacherName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var internalVillages by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Google Drive Scan / Restore States
    var isScanningDrive by remember { mutableStateOf(false) }
    var driveDiscoveryResult by remember { mutableStateOf<CloudBackupDiscoveryResult?>(null) }
    var isRestoringFromDrive by remember { mutableStateOf(false) }
    var driveRestoreProgressMessage by remember { mutableStateOf<String?>(null) }
    var selectedGoogleEmail by remember { mutableStateOf<String?>(null) }

    // Consent Launcher
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                isScanningDrive = true
                val retryRes = viewModel.driveSetupManager.retryPendingConsent()
                if (retryRes.isSuccess) {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        val discovery = viewModel.driveSetupManager.searchExistingSchoolBackups(account)
                        driveDiscoveryResult = discovery
                    }
                }
                isScanningDrive = false
            }
        }
    }

    // Google Sign-In Launcher for Drive Search
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                selectedGoogleEmail = account.email
                scope.launch {
                    isScanningDrive = true
                    driveDiscoveryResult = null
                    val authResult = viewModel.driveSetupManager.handleSignInAccount(account, isSecondary = false)
                    if (authResult.isSuccess) {
                        val discovery = viewModel.driveSetupManager.searchExistingSchoolBackups(account)
                        driveDiscoveryResult = discovery
                    } else {
                        val err = authResult.exceptionOrNull()
                        if (err is com.google.android.gms.auth.UserRecoverableAuthException) {
                            err.intent?.let { consentLauncher.launch(it) }
                        } else {
                            Toast.makeText(context, "গুগল অ্যাকাউন্টে অ্যাক্সেস পেতে ত্রুটি: ${err?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                    isScanningDrive = false
                }
            }
        } catch (e: ApiException) {
            isScanningDrive = false
            Toast.makeText(context, "গুগল সাইন-ইন বাতিল বা ব্যর্থ হয়েছে (${e.statusCode})", Toast.LENGTH_SHORT).show()
        }
    }

    // Local File Picker Launcher (Supports .db, .zip, .json)
    val localFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val contentResolver = context.contentResolver
                    val headerBytes = ByteArray(16)
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.read(headerBytes)
                    }
                    val headerStr = String(headerBytes, Charsets.US_ASCII)

                    when {
                        headerStr.startsWith("SQLite format 3") -> {
                            // Direct SQLite Database File
                            viewModel.restoreDirectDatabaseFromUri(uri) { success, msg ->
                                if (success) {
                                    viewModel.setInitialSetupCompleted(true)
                                    Toast.makeText(context, "ডাটাবেস (.db) সফলভাবে রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                    onSetupComplete()
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        headerBytes[0] == 'P'.code.toByte() && headerBytes[1] == 'K'.code.toByte() -> {
                            // ZIP Backup File
                            viewModel.restoreFromZipUri(uri, DriveRestoreMode.EXCLUDE_OFFLINE) { success, count ->
                                if (success) {
                                    viewModel.setInitialSetupCompleted(true)
                                    Toast.makeText(context, "জিপ ফাইল থেকে $count টি রেকর্ড সফলভাবে রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                    onSetupComplete()
                                } else {
                                    Toast.makeText(context, "জিপ ফাইল থেকে রিস্টোর ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        else -> {
                            // JSON Backup File
                            val jsonStr = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (!jsonStr.isNullOrBlank()) {
                                viewModel.importFullDatabaseFromJson(jsonStr) { parsed ->
                                    if (parsed) {
                                        viewModel.setInitialSetupCompleted(true)
                                        Toast.makeText(context, "JSON ফাইল থেকে ডাটাবেস সফলভাবে রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                        onSetupComplete()
                                    } else {
                                        Toast.makeText(context, "ফাইলের ফরম্যাট সঠিক নয় বা ডাটা রিস্টোর ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ফাইল পড়তে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 1. Beautiful Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "স্বাগতম অন্বেষা বিদ্যালয় ব্যবস্থাপনায়",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "আপনার বিদ্যালয়ের তথ্য প্রস্তুত করতে নিচের যে কোনো একটি অপশন বেছে নিন",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Choice Option Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SetupTabButton(
                        selected = selectedTab == SetupOptionTab.CREATE_NEW,
                        title = "নতুন বিদ্যালয়",
                        icon = Icons.Filled.AddBusiness,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = SetupOptionTab.CREATE_NEW }
                    )
                    SetupTabButton(
                        selected = selectedTab == SetupOptionTab.GOOGLE_RESTORE,
                        title = "গুগল ড্রাইভ রিস্টোর",
                        icon = Icons.Filled.CloudDownload,
                        modifier = Modifier.weight(1.1f),
                        onClick = { selectedTab = SetupOptionTab.GOOGLE_RESTORE }
                    )
                    SetupTabButton(
                        selected = selectedTab == SetupOptionTab.LOCAL_RESTORE,
                        title = "ফাইল রিস্টোর",
                        icon = Icons.Filled.FolderOpen,
                        modifier = Modifier.weight(0.9f),
                        onClick = { selectedTab = SetupOptionTab.LOCAL_RESTORE }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. Tab Content
                when (selectedTab) {
                    SetupOptionTab.CREATE_NEW -> {
                        // Create New School Form
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "নতুন বিদ্যালয়ের তথ্য পূরণ করুন",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "আপনার বিদ্যালয়ের নাম ও প্রাথমিক তথ্য দিয়ে শুরু করুন। পরে যে কোনো সময় সেটিংস থেকে পরিবর্তন করা যাবে।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = schoolName,
                                    onValueChange = { schoolName = it },
                                    label = { Text("বিদ্যালয়ের নাম *") },
                                    placeholder = { Text("উদা: রামপুর সরকারি প্রাথমিক বিদ্যালয়") },
                                    leadingIcon = { Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("initial_setup_school_name_input")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = eiinCode,
                                        onValueChange = { eiinCode = it },
                                        label = { Text("ইআইআইএন / কোড") },
                                        placeholder = { Text("১৩৪২৫১") },
                                        leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = mobileNumber,
                                        onValueChange = { mobileNumber = it },
                                        label = { Text("মোবাইল নম্বর") },
                                        placeholder = { Text("০১৭১১২২৩৩৪৪") },
                                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = headTeacherName,
                                    onValueChange = { headTeacherName = it },
                                    label = { Text("প্রধান শিক্ষকের নাম") },
                                    placeholder = { Text("উদা: মো: রফিকুল ইসলাম") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    label = { Text("ঠিকানা / উপজেলা / জেলা") },
                                    placeholder = { Text("ডাকঘর: রামপুর, সদর, কুমিল্লা") },
                                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = internalVillages,
                                    onValueChange = { internalVillages = it },
                                    label = { Text("ক্যাচমেন্ট এলাকা / গ্রামসমূহ (কমা দিয়ে লিখুন)") },
                                    placeholder = { Text("রামপুর,আমতলী,কৃষ্ণপুর") },
                                    leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = {
                                        if (schoolName.isBlank()) {
                                            Toast.makeText(context, "বিদ্যালয়ের নাম পূরণ করা বাধ্যতামূলক", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isSubmitting = true
                                        scope.launch {
                                            val newSchool = SchoolInfoEntity(
                                                id = 1,
                                                schoolName = schoolName.trim(),
                                                eiinCode = eiinCode.trim().ifBlank { "123456" },
                                                phone = mobileNumber.trim(),
                                                headTeacherName = headTeacherName.trim(),
                                                address = address.trim(),
                                                internalVillages = internalVillages.trim().ifBlank { "রামপুর,আমতলী,কৃষ্ণপুর" },
                                                tagline = "জ্ঞান, মনন ও স্বপ্নের সোপান"
                                            )
                                            viewModel.updateSchoolInfo(newSchool)
                                            viewModel.setInitialSetupCompleted(true)
                                            viewModel.saveInternalAutoBackupSnapshot()
                                            Toast.makeText(context, "'${schoolName}' ডাটাবেস সফলভাবে তৈরি হয়েছে!", Toast.LENGTH_LONG).show()
                                            isSubmitting = false
                                            onSetupComplete()
                                        }
                                    },
                                    enabled = !isSubmitting && schoolName.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("initial_setup_submit_new_school_btn")
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("সেটআপ হচ্ছে...")
                                    } else {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("বিদ্যালয় তৈরি সম্পন্ন করুন", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }

                    SetupOptionTab.GOOGLE_RESTORE -> {
                        // Google Drive Scan & Restore
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "গুগল ড্রাইভ ক্লাউড রিস্টোর",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "আপনার জিমেইল/গুগল ড্রাইভ অ্যাকাউন্টে পূর্বের তৈরি করা বিদ্যালয়ের ব্যাকআপ থাকলে তা স্বয়ংক্রিয়ভাবে খুঁজে রিস্টোর করুন।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Google Sign-In / Account Selector Button
                                Button(
                                    onClick = {
                                        val intent = viewModel.driveSetupManager.getSignInIntent(forSecondary = false)
                                        googleSignInLauncher.launch(intent)
                                    },
                                    enabled = !isScanningDrive && !isRestoringFromDrive,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("initial_setup_google_signin_btn")
                                ) {
                                    Icon(
                                        Icons.Filled.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (selectedGoogleEmail != null) "অন্য গুগল অ্যাকাউন্ট নির্বাচন করুন" else "গুগল অ্যাকাউন্ট নির্বাচন করে খুঁজুন",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }

                                if (selectedGoogleEmail != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "নির্বাচিত অ্যাকাউন্ট: $selectedGoogleEmail",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Scanning Spinner
                                if (isScanningDrive) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "আপনার গুগল ড্রাইভে পূর্ববর্তী ব্যাকআপ খোঁজা হচ্ছে...",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Discovery Result Card
                                driveDiscoveryResult?.let { result ->
                                    if (result.found) {
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF2E7D32),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "🎉 পূর্ববর্তী ব্যাকআপ পাওয়া গেছে!",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))
                                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.height(10.dp))

                                                InfoBadgeRow(icon = Icons.Filled.School, label = "বিদ্যালয়ের নাম", value = result.schoolName)
                                                if (result.eiinCode.isNotBlank()) {
                                                    InfoBadgeRow(icon = Icons.Filled.Numbers, label = "ইআইআইএন", value = result.eiinCode)
                                                }
                                                if (result.studentCount > 0) {
                                                    InfoBadgeRow(icon = Icons.Filled.People, label = "শিক্ষার্থী সংখ্যা", value = "${BanglaUtils.toBanglaDigits(result.studentCount.toString())} জন")
                                                }
                                                InfoBadgeRow(icon = Icons.Filled.AccessTime, label = "সর্বশেষ ব্যাকআপ", value = result.backupDateFormatted)

                                                if (result.hasDbBackup) {
                                                    InfoBadgeRow(icon = Icons.Filled.Storage, label = "ডাটাবেস ব্যাকআপ", value = "সরাসরি SQLite .db ফাইল উপলব্ধ")
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Primary Smart Restore Button
                                                Button(
                                                    onClick = {
                                                        isRestoringFromDrive = true
                                                        driveRestoreProgressMessage = "ড্রাইভ থেকে তথ্য ডাউনলোড ও রিস্টোর করা হচ্ছে..."
                                                        scope.launch {
                                                             viewModel.restoreSegmentedBackupFromDrive(
                                                                target = DriveSyncTarget.PRIMARY_ONLY,
                                                                mode = DriveRestoreMode.EXCLUDE_OFFLINE
                                                            ) { success, msg ->
                                                                isRestoringFromDrive = false
                                                                if (success) {
                                                                    viewModel.setInitialSetupCompleted(true)
                                                                    viewModel.saveInternalAutoBackupSnapshot()
                                                                    Toast.makeText(context, "গুগল ড্রাইভ থেকে সফলভাবে তথ্য রিস্টোর সম্পন্ন হয়েছে!", Toast.LENGTH_LONG).show()
                                                                    onSetupComplete()
                                                                } else {
                                                                    Toast.makeText(context, "রিস্টোর ব্যর্থ: $msg", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !isRestoringFromDrive,
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("initial_setup_restore_confirm_btn")
                                                ) {
                                                    if (isRestoringFromDrive) {
                                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("রিস্টোর হচ্ছে...", color = Color.White)
                                                    } else {
                                                        Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("হ্যাঁ, এই তথ্য রিস্টোর করব", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                                    }
                                                }

                                                if (result.hasDbBackup || result.dbFileId != null) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedButton(
                                                        onClick = {
                                                            isRestoringFromDrive = true
                                                            driveRestoreProgressMessage = "সরাসরি ডাটাবেস (.db) ডাউনলোড হচ্ছে..."
                                                            scope.launch {
                                                                viewModel.restoreDirectDatabaseFromDrive(
                                                                    target = DriveSyncTarget.PRIMARY_ONLY,
                                                                    targetFileId = result.dbFileId
                                                                ) { success, msg ->
                                                                    isRestoringFromDrive = false
                                                                    if (success) {
                                                                        viewModel.setInitialSetupCompleted(true)
                                                                        viewModel.saveInternalAutoBackupSnapshot()
                                                                        Toast.makeText(context, "সরাসরি ডাটাবেস সফলভাবে রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                                                        onSetupComplete()
                                                                    } else {
                                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        enabled = !isRestoringFromDrive,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(44.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("সরাসরি .db ফাইল রিস্টোর", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Backup Not Found Card
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "কোনো ব্যাকআপ পাওয়া যায়নি",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = result.message.ifBlank { "এই গুগল ড্রাইভ অ্যাকাউন্টে পূর্বের কোনো সংরক্ষিত স্কুলের তথ্য পাওয়া যায়নি।" },
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                OutlinedButton(
                                                    onClick = { selectedTab = SetupOptionTab.CREATE_NEW },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Filled.AddBusiness, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("নতুন বিদ্যালয় তৈরি অপশনে যান")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SetupOptionTab.LOCAL_RESTORE -> {
                        // Local File Restore
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "লোকাল ফাইল (.db / .zip / .json) থেকে রিস্টোর",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "আপনার ফোনের মেমোরি বা পেনড্রাইভে সংরক্ষিত SQLite .db ফাইল, ZIP ব্যাকআপ অথবা JSON ফাইল সিলেক্ট করে সম্পূর্ণ ডাটাবেস রিস্টোর করুন।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        localFilePickerLauncher.launch("*/*")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("initial_setup_local_file_picker_btn")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ব্যাকআপ ফাইল নির্বাচন করুন (.db / .zip / .json)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Secondary Option: Skip / Test with sample data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.setInitialSetupCompleted(true)
                            onDismiss()
                        }
                    ) {
                        Text("পরে সেটআপ করব", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }

                    TextButton(
                        onClick = {
                            viewModel.setInitialSetupCompleted(true)
                            viewModel.saveInternalAutoBackupSnapshot()
                            Toast.makeText(context, "ডেমো বিদ্যালয় দিয়ে শুরু করা হয়েছে", Toast.LENGTH_SHORT).show()
                            onSetupComplete()
                        }
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ডেমো তথ্য দিয়ে পরীক্ষা করুন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupTabButton(
    selected: Boolean,
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .padding(2.dp)
            .height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoBadgeRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$label: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
