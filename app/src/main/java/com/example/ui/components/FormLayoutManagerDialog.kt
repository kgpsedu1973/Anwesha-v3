package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CustomFieldEntity
import com.example.util.FormFieldItem
import com.example.util.FormGroupItem
import com.example.util.FormLayoutManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormLayoutManagerDialog(
    customFields: List<CustomFieldEntity>,
    onDismiss: () -> Unit,
    onLayoutSaved: () -> Unit
) {
    val context = LocalContext.current
    var groups by remember {
        mutableStateOf(FormLayoutManager.loadGroups(context, customFields))
    }

    var showAddGroupDialog by remember { mutableStateOf(false) }
    var editingGroupIndex by remember { mutableStateOf<Int?>(null) }
    var groupTitleInput by remember { mutableStateOf("") }

    var editingFieldCoordinates by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (gIndex, fIndex)
    var fieldLabelInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.92f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "ফর্ম ফিল্ড ও গ্রুপ ম্যানেজমেন্ট (Edit Layout)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "গ্রুপ ও ফিল্ডসমূহ প্রয়োজনমতো সম্পাদনা (Edit), ডিলিট (Delete), স্থানান্তর এবং নতুন গ্রুপ যুক্ত করুন।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Top Quick Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            groupTitleInput = ""
                            editingGroupIndex = null
                            showAddGroupDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_add_form_group")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("নতুন গ্রুপ তৈরি", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            FormLayoutManager.resetToDefaults(context)
                            groups = FormLayoutManager.loadGroups(context, customFields)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ডিফল্ট রিসেট", fontSize = 12.sp)
                    }
                }

                // Groups & Fields List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(groups, key = { _, g -> g.id }) { gIndex, group ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Group Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = group.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "${group.fields.size} টি ফিল্ড",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Move Group Up
                                        IconButton(
                                            onClick = {
                                                if (gIndex > 0) {
                                                    val mutable = groups.toMutableList()
                                                    val temp = mutable[gIndex]
                                                    mutable[gIndex] = mutable[gIndex - 1]
                                                    mutable[gIndex - 1] = temp
                                                    groups = mutable
                                                }
                                            },
                                            enabled = gIndex > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Group Up", modifier = Modifier.size(18.dp))
                                        }

                                        // Move Group Down
                                        IconButton(
                                            onClick = {
                                                if (gIndex < groups.size - 1) {
                                                    val mutable = groups.toMutableList()
                                                    val temp = mutable[gIndex]
                                                    mutable[gIndex] = mutable[gIndex + 1]
                                                    mutable[gIndex + 1] = temp
                                                    groups = mutable
                                                }
                                            },
                                            enabled = gIndex < groups.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Group Down", modifier = Modifier.size(18.dp))
                                        }

                                        // Edit / Rename Group
                                        IconButton(
                                            onClick = {
                                                editingGroupIndex = gIndex
                                                groupTitleInput = group.title
                                                showAddGroupDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Rename Group", modifier = Modifier.size(16.dp))
                                        }

                                        // Delete Group (Moves fields to adjacent group or deletes)
                                        if (groups.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val targetIdx = if (gIndex > 0) gIndex - 1 else 1
                                                    val mutable = groups.toMutableList()
                                                    val donor = mutable.removeAt(gIndex)
                                                    if (donor.fields.isNotEmpty()) {
                                                        val receiver = mutable[if (targetIdx >= mutable.size) mutable.size - 1 else targetIdx]
                                                        val combined = receiver.fields.toMutableList().apply { addAll(donor.fields) }
                                                        val receiverIdx = mutable.indexOfFirst { it.id == receiver.id }
                                                        if (receiverIdx >= 0) {
                                                            mutable[receiverIdx] = receiver.copy(fields = combined)
                                                        }
                                                    }
                                                    groups = mutable
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete Group", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Fields List inside Group
                                if (group.fields.isEmpty()) {
                                    Text(
                                        "এই গ্রুপে কোনো ফিল্ড নেই (অন্য গ্রুপ থেকে ফিল্ড স্থানান্তর করুন)",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        group.fields.forEachIndexed { fIndex, field ->
                                            var showMoveToMenu by remember { mutableStateOf(false) }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (field.isCustom) Icons.Filled.Extension else Icons.Filled.DragHandle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = if (field.isCustom) Color(0xFF2E7D32) else Color.Gray
                                                        )
                                                        Text(
                                                            text = field.label,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        if (field.isCustom) {
                                                            Surface(
                                                                color = Color(0xFFE8F5E9),
                                                                shape = RoundedCornerShape(3.dp)
                                                            ) {
                                                                Text(
                                                                    "কাস্টম",
                                                                    fontSize = 9.sp,
                                                                    color = Color(0xFF2E7D32),
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Field Actions (Edit, Delete, Move, Transfer)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        // Edit Field Label
                                                        IconButton(
                                                            onClick = {
                                                                editingFieldCoordinates = Pair(gIndex, fIndex)
                                                                fieldLabelInput = field.label
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Field", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                        }

                                                        // Move Field Up
                                                        IconButton(
                                                            onClick = {
                                                                if (fIndex > 0) {
                                                                    val mutableFields = group.fields.toMutableList()
                                                                    val temp = mutableFields[fIndex]
                                                                    mutableFields[fIndex] = mutableFields[fIndex - 1]
                                                                    mutableFields[fIndex - 1] = temp
                                                                    val mutableGroups = groups.toMutableList()
                                                                    mutableGroups[gIndex] = group.copy(fields = mutableFields)
                                                                    groups = mutableGroups
                                                                }
                                                            },
                                                            enabled = fIndex > 0,
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(14.dp))
                                                        }

                                                        // Move Field Down
                                                        IconButton(
                                                            onClick = {
                                                                if (fIndex < group.fields.size - 1) {
                                                                    val mutableFields = group.fields.toMutableList()
                                                                    val temp = mutableFields[fIndex]
                                                                    mutableFields[fIndex] = mutableFields[fIndex + 1]
                                                                    mutableFields[fIndex + 1] = temp
                                                                    val mutableGroups = groups.toMutableList()
                                                                    mutableGroups[gIndex] = group.copy(fields = mutableFields)
                                                                    groups = mutableGroups
                                                                }
                                                            },
                                                            enabled = fIndex < group.fields.size - 1,
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(14.dp))
                                                        }

                                                        // Transfer to another group
                                                        Box {
                                                            IconButton(
                                                                onClick = { showMoveToMenu = true },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Filled.DriveFileMove, contentDescription = "Move to Group", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                                            }

                                                            DropdownMenu(
                                                                expanded = showMoveToMenu,
                                                                onDismissRequest = { showMoveToMenu = false }
                                                            ) {
                                                                Text(
                                                                    "গ্রুপ পরিবর্তন করুন:",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                                )
                                                                groups.forEachIndexed { destIdx, destGroup ->
                                                                    if (destIdx != gIndex) {
                                                                        DropdownMenuItem(
                                                                            text = { Text(destGroup.title, fontSize = 12.sp) },
                                                                            onClick = {
                                                                                val donorFields = group.fields.toMutableList()
                                                                                val itemToMove = donorFields.removeAt(fIndex)
                                                                                val destFields = destGroup.fields.toMutableList().apply { add(itemToMove) }

                                                                                val mutableGroups = groups.toMutableList()
                                                                                mutableGroups[gIndex] = group.copy(fields = donorFields)
                                                                                mutableGroups[destIdx] = destGroup.copy(fields = destFields)
                                                                                groups = mutableGroups
                                                                                showMoveToMenu = false
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Delete Field
                                                        IconButton(
                                                            onClick = {
                                                                val mutableFields = group.fields.toMutableList()
                                                                mutableFields.removeAt(fIndex)
                                                                val mutableGroups = groups.toMutableList()
                                                                mutableGroups[gIndex] = group.copy(fields = mutableFields)
                                                                groups = mutableGroups
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Field", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    FormLayoutManager.saveGroups(context, groups)
                    onLayoutSaved()
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_save_form_layout")
            ) {
                Text("লেআউট সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )

    // Add or Edit Group Title Dialog
    if (showAddGroupDialog) {
        AlertDialog(
            onDismissRequest = { showAddGroupDialog = false },
            title = { Text(if (editingGroupIndex == null) "নতুন গ্রুপ তৈরি" else "গ্রুপের নাম পরিবর্তন", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = groupTitleInput,
                    onValueChange = { groupTitleInput = it },
                    label = { Text("গ্রুপের শিরোনাম / নাম") },
                    placeholder = { Text("যেমন: স্বাস্থ্য ও সুযোগ-সুবিধা") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupTitleInput.isNotBlank()) {
                            val mutable = groups.toMutableList()
                            if (editingGroupIndex == null) {
                                mutable.add(
                                    FormGroupItem(
                                        id = "grp_${System.currentTimeMillis()}",
                                        title = groupTitleInput.trim(),
                                        fields = emptyList()
                                    )
                                )
                            } else {
                                val idx = editingGroupIndex!!
                                mutable[idx] = mutable[idx].copy(title = groupTitleInput.trim())
                            }
                            groups = mutable
                            showAddGroupDialog = false
                        }
                    }
                ) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGroupDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Edit Field Label Dialog
    if (editingFieldCoordinates != null) {
        val (gIdx, fIdx) = editingFieldCoordinates!!
        AlertDialog(
            onDismissRequest = { editingFieldCoordinates = null },
            title = { Text("ফিল্ডের নাম পরিবর্তন (Edit Field Label)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = fieldLabelInput,
                    onValueChange = { fieldLabelInput = it },
                    label = { Text("ফিল্ডের নাম") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fieldLabelInput.isNotBlank() && gIdx < groups.size && fIdx < groups[gIdx].fields.size) {
                            val mutableGroups = groups.toMutableList()
                            val mutableFields = mutableGroups[gIdx].fields.toMutableList()
                            mutableFields[fIdx] = mutableFields[fIdx].copy(label = fieldLabelInput.trim())
                            mutableGroups[gIdx] = mutableGroups[gIdx].copy(fields = mutableFields)
                            groups = mutableGroups
                            editingFieldCoordinates = null
                        }
                    }
                ) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFieldCoordinates = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
