package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AuthorizedUserEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.SyncConflictEntity
import com.example.util.BanglaUtils
import com.example.util.SyncState
import com.example.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncStatusPill(
    syncViewModel: SyncViewModel,
    modifier: Modifier = Modifier,
    onOpenSyncSheet: () -> Unit
) {
    val syncState by syncViewModel.syncState.collectAsState()
    val pendingCount by syncViewModel.pendingCount.collectAsState()
    val isOnline by syncViewModel.isOnline.collectAsState()

    val (bgColor, contentColor, iconVector, labelText) = when {
        !isOnline || syncState == SyncState.OFFLINE -> {
            Quadruple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Outlined.CloudOff,
                "অফলাইন"
            )
        }
        syncState == SyncState.SYNCING -> {
            Quadruple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primary,
                Icons.Filled.Sync,
                "সিঙ্ক হচ্ছে..."
            )
        }
        syncState == SyncState.PENDING_CHANGES || pendingCount > 0 -> {
            Quadruple(
                Color(0xFFFFF3E0),
                Color(0xFFE65100),
                Icons.Filled.CloudUpload,
                "${BanglaUtils.toBanglaDigits(pendingCount)}টি পেন্ডিং"
            )
        }
        syncState == SyncState.ERROR -> {
            Quadruple(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                MaterialTheme.colorScheme.error,
                Icons.Filled.ErrorOutline,
                "সিঙ্ক ত্রুটি"
            )
        }
        else -> {
            Quadruple(
                Color(0xFFE8F5E9),
                Color(0xFF2E7D32),
                Icons.Filled.CloudDone,
                "সিঙ্কড"
            )
        }
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, contentColor.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenSyncSheet() }
            .testTag("top_sync_status_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = labelText,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = labelText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSyncBottomSheet(
    syncViewModel: SyncViewModel,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val syncState by syncViewModel.syncState.collectAsState()
    val pendingCount by syncViewModel.pendingCount.collectAsState()
    val isOnline by syncViewModel.isOnline.collectAsState()
    val accountEmail by syncViewModel.accountEmail.collectAsState()
    val accountName by syncViewModel.accountName.collectAsState()
    val currentUserRole by syncViewModel.currentUserRole.collectAsState()
    val lastSyncTime by syncViewModel.syncManager.lastSyncTime.collectAsState()
    val statusMessage by syncViewModel.statusMessage.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "ক্লাউড সিঙ্ক ও মাল্টি-ইউজার স্ট্যাটাস",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // User Profile Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountName ?: "বিদ্যালয় ব্যবহারকারী",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = accountEmail ?: "কোনো Google অ্যাকাউন্ট যুক্ত নেই",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = currentUserRole,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Status Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pending Count Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (pendingCount > 0) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("পেন্ডিং কিউ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${BanglaUtils.toBanglaDigits(pendingCount)} টি রেকর্ড",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingCount > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Network & Last Sync Card
                Card(
                    modifier = Modifier.weight(1.3f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("সর্বশেষ সিঙ্ক", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (lastSyncTime > 0) SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(lastSyncTime)) else "কখনই নয়",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Status message
            if (statusMessage.isNotBlank()) {
                Text(
                    text = "• $statusMessage",
                    fontSize = 12.sp,
                    color = if (syncState == SyncState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { syncViewModel.syncNow() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_quick_sync_now"),
                    enabled = isOnline && syncState != SyncState.SYNCING
                ) {
                    if (syncState == SyncState.SYNCING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("এখনই সিঙ্ক করুন", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        syncViewModel.backupNow("কুইক ব্যাকআপ")
                        Toast.makeText(context, "ব্যাকআপ সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ব্যাকআপ নিন", fontSize = 13.sp)
                }
            }

            // Full Settings Link
            TextButton(
                onClick = {
                    onDismiss()
                    onNavigateToSettings()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("মাল্টি-ইউজার পারমিশন ও ব্যাকঅ্যান্ড সেটিংস")
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Dedicated Multi-User Cloud Sync & Role Management Section inside Settings Screen
 */
@Composable
fun CloudSyncManagementSection(
    syncViewModel: SyncViewModel,
    onSignInClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOnline by syncViewModel.isOnline.collectAsState()
    val syncState by syncViewModel.syncState.collectAsState()
    val isSignedIn by syncViewModel.isSignedIn.collectAsState()
    val accountEmail by syncViewModel.accountEmail.collectAsState()
    val scriptUrl by syncViewModel.scriptUrl.collectAsState()
    val pendingCount by syncViewModel.pendingCount.collectAsState()
    val authorizedUsers by syncViewModel.authorizedUsers.collectAsState()
    val conflicts by syncViewModel.conflicts.collectAsState()
    val backupHistory by syncViewModel.backupHistory.collectAsState()
    val currentUserRole by syncViewModel.currentUserRole.collectAsState()

    var editingScriptUrl by remember(scriptUrl) { mutableStateOf(scriptUrl) }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<AuthorizedUserEntity?>(null) }
    var showScriptCodeDialog by remember { mutableStateOf(false) }
    var showConflictsDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Google Account Status
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Google সাইন-ইন স্ট্যাটাস", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = if (isSignedIn && !accountEmail.isNullOrBlank()) "সংযুক্ত: $accountEmail (${currentUserRole})" else "কোনো Google অ্যাকাউন্ট যুক্ত নেই",
                            fontSize = 12.sp,
                            color = if (isSignedIn) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    if (isSignedIn) {
                        OutlinedButton(
                            onClick = { syncViewModel.signOut() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("লগআউট", fontSize = 11.sp)
                        }
                    } else {
                        Button(onClick = onSignInClick) {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সাইন-ইন", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Google Apps Script Backend URL (Zero hardcoded secrets, lightweight & secure)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Google Apps Script ব্যাকএন্ড API URL", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    TextButton(onClick = { showScriptCodeDialog = true }) {
                        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("কোড দেখুন", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = editingScriptUrl,
                    onValueChange = { editingScriptUrl = it },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            syncViewModel.setScriptUrl(editingScriptUrl)
                            Toast.makeText(context, "URL সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সংরক্ষণ", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { syncViewModel.syncNow() },
                        modifier = Modifier.weight(1f),
                        enabled = isOnline
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("টেস্ট সিঙ্ক", fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Multi-User Access Management (Admin Role & Permissions for 20+ accounts)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("অনুমোদিত শিক্ষক ও ব্যবহারকারী (${BanglaUtils.toBanglaDigits(authorizedUsers.size)} জন)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("২০+ জন শিক্ষকের ইমেইল ভিত্তিক রোল ও অনুমতি", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showAddUserDialog = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add User", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (authorizedUsers.isEmpty()) {
                    Text("এখনো কোনো অতিরিক্ত ব্যবহারকারী যোগ করা হয়নি।", fontSize = 12.sp, color = Color.Gray)
                } else {
                    authorizedUsers.forEach { user ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(user.displayName.ifEmpty { user.email }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Surface(
                                            color = if (user.role == "Admin") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = user.role,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (user.role == "Admin") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(user.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "পারমিশন: ${if (user.canEditStudents) "সম্পাদনা" else "শুধু দর্শন"}${if (user.canDeleteStudents) ", মোছা" else ""}${if (user.canBackupRestore) ", ব্যাকআপ" else ""}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingUser = user }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { syncViewModel.deleteAuthorizedUser(user.email) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Google Drive Backup & Restore Center
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Google Drive সেন্ট্রাল ব্যাকআপ ও রিস্টোর হাব", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("রিস্টোর করার আগে স্বয়ংক্রিয় সেফটি স্ন্যাপশট সংরক্ষিত হয়।", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            syncViewModel.backupNow("ম্যানুয়াল ক্লাউড ব্যাকআপ")
                            Toast.makeText(context, "Google Drive এ ব্যাকআপ সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("নতুন ব্যাকআপ নিন", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showRestoreConfirmDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("রিস্টোর (Restore)", fontSize = 12.sp)
                    }
                }

                if (conflicts.isNotEmpty()) {
                    TextButton(
                        onClick = { showConflictsDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("কনফ্লিক্ট রেজোলিউশন লগ দেখুন (${BanglaUtils.toBanglaDigits(conflicts.size)} টি)", color = Color(0xFFE65100), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Dialog: Add / Edit User Permissions
    if (showAddUserDialog || editingUser != null) {
        val targetUser = editingUser
        var email by remember { mutableStateOf(targetUser?.email ?: "") }
        var name by remember { mutableStateOf(targetUser?.displayName ?: "") }
        var role by remember { mutableStateOf(targetUser?.role ?: "Teacher") }
        var canEditStudents by remember { mutableStateOf(targetUser?.canEditStudents ?: true) }
        var canDeleteStudents by remember { mutableStateOf(targetUser?.canDeleteStudents ?: false) }
        var canEditAttendance by remember { mutableStateOf(targetUser?.canEditAttendance ?: true) }
        var canEditResults by remember { mutableStateOf(targetUser?.canEditExamResults ?: true) }
        var canBackupRestore by remember { mutableStateOf(targetUser?.canBackupRestore ?: false) }

        AlertDialog(
            onDismissRequest = {
                showAddUserDialog = false
                editingUser = null
            },
            title = { Text(if (targetUser != null) "ব্যবহারকারী পারমিশন পরিবর্তন" else "নতুন ব্যবহারকারী যোগ") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Google ইমেইল অ্যাকাউন্ট") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("শিক্ষক/স্টাফের নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Role selector
                    Text("ব্যবহারকারীর ভূমিকা (Role):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Teacher", "Editor", "Admin", "Viewer").forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("অনুমতিসমূহ (Permissions):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canEditStudents, onCheckedChange = { canEditStudents = it })
                        Text("শিক্ষার্থী তথ্য যোগ/সম্পাদনা", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canDeleteStudents, onCheckedChange = { canDeleteStudents = it })
                        Text("শিক্ষার্থী মুছে ফেলার অনুমতি", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canEditAttendance, onCheckedChange = { canEditAttendance = it })
                        Text("উপস্থিতি রেকর্ড আপডেট", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canEditResults, onCheckedChange = { canEditResults = it })
                        Text("পরীক্ষার ফলাফল এন্ট্রি", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canBackupRestore, onCheckedChange = { canBackupRestore = it })
                        Text("ক্লাউড ব্যাকআপ ও রিস্টোর", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (email.isNotBlank()) {
                            val entity = AuthorizedUserEntity(
                                email = email.trim().lowercase(),
                                displayName = name.trim(),
                                role = role,
                                status = "Active",
                                canViewStudents = true,
                                canEditStudents = canEditStudents,
                                canDeleteStudents = canDeleteStudents,
                                canViewAttendance = true,
                                canEditAttendance = canEditAttendance,
                                canViewExamResults = true,
                                canEditExamResults = canEditResults,
                                canManageSettings = role == "Admin",
                                canManageUsers = role == "Admin",
                                canBackupRestore = canBackupRestore || role == "Admin",
                                addedBy = accountEmail ?: "admin"
                            )
                            syncViewModel.saveAuthorizedUser(entity)
                            showAddUserDialog = false
                            editingUser = null
                        }
                    }
                ) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddUserDialog = false
                    editingUser = null
                }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Dialog: Google Apps Script Backend Code
    if (showScriptCodeDialog) {
        val scriptCode = remember { syncViewModel.getSampleBackendScript() }
        AlertDialog(
            onDismissRequest = { showScriptCodeDialog = false },
            title = { Text("Google Apps Script ব্যাকএন্ড কোড") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "১. গুগল ড্রাইভে গিয়ে New -> More -> Google Apps Script খুলুন।\n" +
                        "২. নিচের কোডটি পেস্ট করে Deploy as Web App করুন।\n" +
                        "৩. Web App URL টি অ্যাপের সেটিংসে সেভ করুন।",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = scriptCode,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AppsScript", scriptCode))
                    Toast.makeText(context, "কোড কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("কোড কপি করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScriptCodeDialog = false }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }

    // Dialog: Restore confirmation
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Google Drive ব্যাকআপ রিস্টোর?") },
            text = {
                Text("ক্লাউড থেকে সর্বশেষ ব্যাকআপ রিস্টোর করা হবে। আপনার বর্তমান ডেটার একটি সেফটি ব্যাকআপ স্বয়ংক্রিয়ভাবে সংরক্ষিত হবে। আপনি কি নিশ্চিত?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        syncViewModel.restoreBackup()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("হ্যাঁ, রিস্টোর করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Dialog: Conflicts Viewer
    if (showConflictsDialog) {
        AlertDialog(
            onDismissRequest = { showConflictsDialog = false },
            title = { Text("কনফ্লিক্ট রেজোলিউশন হিস্ট্রি") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(conflicts) { c ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("${c.entityType}: ${c.entityLabel}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("সমাধান: ${c.resolutionNote}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Text("ইউজার: ${c.remoteUpdatedBy}", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConflictsDialog = false }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}
