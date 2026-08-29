package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SyncViewModel
import kotlinx.coroutines.launch

enum class SetupMode {
    CREATE_SCHOOL,
    JOIN_SCHOOL,
    DEMO_OFFLINE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolSetupDecisionScreen(
    syncViewModel: SyncViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val accountEmail by syncViewModel.accountEmail.collectAsState()
    val accountName by syncViewModel.accountName.collectAsState()
    val isSignedIn by syncViewModel.isSignedIn.collectAsState()

    var selectedMode by remember { mutableStateOf(SetupMode.CREATE_SCHOOL) }

    // Create Form States
    var schoolName by remember { mutableStateOf("") }
    var eiinCode by remember { mutableStateOf("") }
    var adminEmail by remember(accountEmail) { mutableStateOf(accountEmail ?: "") }
    var headTeacherName by remember(accountName) { mutableStateOf(accountName ?: "") }

    // Join Form States
    var joinRoomCode by remember { mutableStateOf("") }
    var userEmail by remember(accountEmail) { mutableStateOf(accountEmail ?: "") }
    var userName by remember(accountName) { mutableStateOf(accountName ?: "") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedRoomCodePreview by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        syncViewModel.onGoogleSignInResult(result.data)
    }

    LaunchedEffect(accountEmail) {
        if (!accountEmail.isNullOrBlank()) {
            adminEmail = accountEmail ?: ""
            userEmail = accountEmail ?: ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .testTag("school_setup_decision_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge & Title
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "বিদ্যালয় সেটআপ ও সিঙ্ক নির্বাচন",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "প্রথমবার অ্যাপ চালু করেছেন? আপনার সুবিধা অনুযায়ী অপশন বেছে নিন",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Google Account Status Pill
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isSignedIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSignedIn) Icons.Filled.CheckCircle else Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = if (isSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isSignedIn) "Google অ্যাকাউন্ট সংযুক্ত" else "Google অ্যাকাউন্ট সংযুক্ত নেই",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSignedIn) accountEmail ?: "" else "অ্যাকাউন্ট যোগ করলে স্বয়ংক্রিয় ক্লাউড সিঙ্ক চালু হবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    if (!isSignedIn) {
                        FilledTonalButton(
                            onClick = {
                                googleSignInLauncher.launch(syncViewModel.getSignInIntent())
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("setup_connect_google_button")
                        ) {
                            Text("লগইন", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector Segmented Tabs
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedMode == SetupMode.CREATE_SCHOOL,
                    onClick = {
                        selectedMode = SetupMode.CREATE_SCHOOL
                        errorMessage = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = {
                        if (selectedMode == SetupMode.CREATE_SCHOOL) {
                            Icon(Icons.Filled.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                ) {
                    Text("নতুন বিদ্যালয়", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                SegmentedButton(
                    selected = selectedMode == SetupMode.JOIN_SCHOOL,
                    onClick = {
                        selectedMode = SetupMode.JOIN_SCHOOL
                        errorMessage = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = {
                        if (selectedMode == SetupMode.JOIN_SCHOOL) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                ) {
                    Text("যুক্ত হোন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                SegmentedButton(
                    selected = selectedMode == SetupMode.DEMO_OFFLINE,
                    onClick = {
                        selectedMode = SetupMode.DEMO_OFFLINE
                        errorMessage = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = {
                        if (selectedMode == SetupMode.DEMO_OFFLINE) {
                            Icon(Icons.Filled.OfflinePin, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                ) {
                    Text("ডেমো মোড", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Banner
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Mode Content Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    when (selectedMode) {
                        SetupMode.CREATE_SCHOOL -> {
                            Text(
                                text = "নতুন বিদ্যালয়ের অ্যাডমিন সেটআপ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "বিদ্যালয় তৈরি করার পর একটি স্বয়ংক্রিয় 'রুম কোড' তৈরি হবে যা সহকারী শিক্ষক/স্টাফদের সাথে শেয়ার করতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                            )

                            OutlinedTextField(
                                value = schoolName,
                                onValueChange = { schoolName = it },
                                label = { Text("বিদ্যালয়ের পূর্ণ নাম *") },
                                placeholder = { Text("যেমন: পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়") },
                                leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_school_name_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = adminEmail,
                                onValueChange = { adminEmail = it },
                                label = { Text("অ্যাডমিন গুগল ইমেইল *") },
                                placeholder = { Text("admin@gmail.com") },
                                leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_admin_email_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = eiinCode,
                                    onValueChange = { eiinCode = it },
                                    label = { Text("EIIN / কোড") },
                                    placeholder = { Text("134251") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_eiin_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = headTeacherName,
                                    onValueChange = { headTeacherName = it },
                                    label = { Text("প্রধান শিক্ষক") },
                                    placeholder = { Text("নাম লিখুন") },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("setup_head_teacher_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (schoolName.isBlank()) {
                                        errorMessage = "অনুগ্রহ করে বিদ্যালয়ের নাম লিখুন"
                                        return@Button
                                    }
                                    if (adminEmail.isBlank()) {
                                        errorMessage = "অনুগ্রহ করে অ্যাডমিন ইমেইল লিখুন"
                                        return@Button
                                    }

                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val res = syncViewModel.createSchool(
                                            schoolName = schoolName,
                                            adminEmail = adminEmail,
                                            adminName = headTeacherName,
                                            eiinCode = eiinCode,
                                            headTeacherName = headTeacherName
                                        )
                                        isLoading = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "বিদ্যালয় সেটআপ সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                                            onSetupComplete()
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.localizedMessage ?: "সেটআপ ব্যর্থ হয়েছে"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("setup_create_school_submit"),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("সেটআপ করা হচ্ছে...")
                                } else {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("বিদ্যালয় তৈরি করুন ও শুরু করুন")
                                }
                            }
                        }

                        SetupMode.JOIN_SCHOOL -> {
                            Text(
                                text = "বিদ্যমান বিদ্যালয়ে জয়েন করুন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "প্রধান শিক্ষক বা অ্যাডমিন প্রদত্ত 'রুম কোড' (যেমন: ROOM-2026-1024) দিয়ে বিদ্যালয়ে যুক্ত হোন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                            )

                            OutlinedTextField(
                                value = joinRoomCode,
                                onValueChange = { joinRoomCode = it.uppercase() },
                                label = { Text("স্কুল রুম কোড (Room Code) *") },
                                placeholder = { Text("যেমন: ROOM-2026-4821") },
                                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_room_code_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = userEmail,
                                onValueChange = { userEmail = it },
                                label = { Text("আপনার গুগল ইমেইল আইডি *") },
                                placeholder = { Text("teacher@gmail.com") },
                                leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_join_user_email_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                label = { Text("আপনার নাম (ঐচ্ছিক)") },
                                placeholder = { Text("মো: সহকারী শিক্ষক") },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_join_user_name_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (joinRoomCode.isBlank()) {
                                        errorMessage = "অনুগ্রহ করে রুম কোড লিখুন"
                                        return@Button
                                    }
                                    if (userEmail.isBlank()) {
                                        errorMessage = "অনুগ্রহ করে আপনার ইমেইল লিখুন"
                                        return@Button
                                    }

                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val res = syncViewModel.joinSchool(
                                            roomCode = joinRoomCode,
                                            userEmail = userEmail,
                                            userName = userName
                                        )
                                        isLoading = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "বিদ্যালয়ে সফলভাবে যুক্ত হয়েছেন!", Toast.LENGTH_SHORT).show()
                                            onSetupComplete()
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.localizedMessage ?: "যুক্ত হওয়া সম্ভব হয়নি"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("setup_join_school_submit"),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("যাচাই করা হচ্ছে...")
                                } else {
                                    Icon(Icons.Filled.Login, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("রুম কোড দিয়ে যুক্ত হোন")
                                }
                            }
                        }

                        SetupMode.DEMO_OFFLINE -> {
                            Text(
                                text = "ডেমো বিদ্যালয় বা অফলাইন মোড",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "কোনো গুগল অ্যাকাউন্ট ছাড়াই শুধু এই ডিভাইসের মেমোরিতে ডেমো ডাটা দিয়ে অ্যাপটি ব্যবহার করুন। পরবর্তীতে যেকোনো সময় সেটিংস থেকে ক্লাউড সিঙ্ক চালু করতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "নমুনা শিক্ষার্থী, উপস্থিতি ও পরীক্ষার ফলাফল স্বয়ংক্রিয়ভাবে লোড হবে।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            FilledTonalButton(
                                onClick = {
                                    isLoading = true
                                    scope.launch {
                                        val res = syncViewModel.setupDemoSchool()
                                        isLoading = false
                                        if (res.isSuccess) {
                                            onSetupComplete()
                                        } else {
                                            errorMessage = "ডেমো সেটআপ ব্যর্থ হয়েছে"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("setup_demo_school_submit")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ডেমো বিদ্যালয় দিয়ে শুরু করুন")
                            }
                        }
                    }
                }
            }
        }
    }
}
