package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SyncUiState
import com.example.viewmodel.SyncViewModel

/**
 * Modern, Minimal & Compact First-Launch Screen:
 * "After installing first window will be login or skip now"
 */
@Composable
fun AuthOnboardingScreen(
    syncViewModel: SyncViewModel,
    onComplete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val uiState by syncViewModel.uiState.collectAsState()
    val isSignedIn by syncViewModel.isSignedIn.collectAsState()
    val accountEmail by syncViewModel.accountEmail.collectAsState()

    var showManualEmailInput by remember { mutableStateOf(false) }
    var inputEmail by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        syncViewModel.onGoogleSignInResult(result.data)
    }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            onComplete()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .testTag("auth_onboarding_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Brand & Welcome Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    // App Logo Icon Container
                    Surface(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = "School Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "বিদ্যালয় ব্যবস্থাপনা",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 24.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "লোকাল-ফার্স্ট ডাটাবেস ও সেন্ট্রাল ক্লাউড ব্যাকআপ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                // Middle Features & Sign-in Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Feature highlights (Compact minimal pills)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FeatureMiniBadge(
                            icon = Icons.Outlined.CloudSync,
                            title = "অটো ক্লাউড সিঙ্ক",
                            desc = "Google Drive"
                        )
                        FeatureMiniBadge(
                            icon = Icons.Outlined.People,
                            title = "মাল্টি-ইউজার",
                            desc = "২০+ শিক্ষক"
                        )
                        FeatureMiniBadge(
                            icon = Icons.Outlined.Security,
                            title = "অফলাইন ফার্স্ট",
                            desc = "শতভাগ নিরাপদ"
                        )
                    }

                    // Main Action Container Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_action_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "শুরু করতে Google অ্যাকাউন্টে যুক্ত হোন",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            // Native Google Sign-In Button
                            Button(
                                onClick = {
                                    try {
                                        val intent = syncViewModel.googleDriveManager.getSignInIntent()
                                        googleSignInLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        showManualEmailInput = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_google_signin"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google দিয়ে সাইন-ইন করুন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Toggle or Direct Email Input
                            if (!showManualEmailInput) {
                                TextButton(
                                    onClick = { showManualEmailInput = true },
                                    modifier = Modifier.testTag("btn_toggle_email_signin")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ইমেইল দিয়ে সরাসরি যুক্ত করুন",
                                        fontSize = 12.5.sp
                                    )
                                }
                            } else {
                                AnimatedVisibility(
                                    visible = showManualEmailInput,
                                    enter = fadeIn() + expandVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = inputEmail,
                                            onValueChange = { inputEmail = it },
                                            label = { Text("Google ইমেইল অ্যাড্রেস", fontSize = 12.sp) },
                                            placeholder = { Text("উদাহরণ: kgpsedu1973@gmail.com", fontSize = 11.sp) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Email,
                                                imeAction = ImeAction.Next
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("input_onboarding_email")
                                        )

                                        OutlinedTextField(
                                            value = inputName,
                                            onValueChange = { inputName = it },
                                            label = { Text("আপনার নাম / পদবি (ঐচ্ছিক)", fontSize = 12.sp) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    focusManager.clearFocus()
                                                    if (inputEmail.isNotBlank()) {
                                                        syncViewModel.signInWithDirectEmail(inputEmail, inputName)
                                                        onComplete()
                                                    }
                                                }
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("input_onboarding_name")
                                        )

                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                if (inputEmail.isNotBlank()) {
                                                    syncViewModel.signInWithDirectEmail(inputEmail, inputName)
                                                    onComplete()
                                                }
                                            },
                                            enabled = inputEmail.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .testTag("btn_confirm_direct_signin"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("সংযুক্ত ও সম্পন্ন করুন", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Skip Now Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            syncViewModel.completeAuthOnboarding()
                            onComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_skip_onboarding"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "এখনই স্কিপ করুন (Skip Now)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "স্কিপ করলেও অফলাইনে সব কাজ করা যাবে। পরে সেটিংসে অ্যাকাউন্ট যুক্ত করা যাবে।",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureMiniBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = desc,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
