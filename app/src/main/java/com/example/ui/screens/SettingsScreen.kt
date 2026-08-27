package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.SchoolCustomInfoHelper
import com.example.data.local.entity.SchoolCustomInfoItem
import com.example.data.local.entity.SchoolInfoEntity
import com.example.data.local.entity.UserEntity
import com.example.ui.components.CustomFieldAddDialog
import com.example.ui.components.CustomFieldAddEditDialog
import com.example.ui.components.DataImportExportDialog
import com.example.ui.components.FormulaRuleAddDialog
import com.example.ui.components.FormulaRuleAddEditDialog
import com.example.ui.components.PhotoCaptureDialog
import com.example.ui.components.SettingsGroupCard
import com.example.ui.components.SettingsInfoRow
import com.example.util.BanglaUtils
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToCustomFields: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val formulaRules by viewModel.formulaRules.collectAsState()
    val allStudents by viewModel.allStudents.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()

    // Expanded state for Settings Group Cards
    var expandedGroupSchoolInfo by remember { mutableStateOf(true) }  // বিদ্যালয়ের মৌলিক তথ্য
    var expandedGroupCustomFields by remember { mutableStateOf(true) } // কাস্টম ফিল্ড ও ফর্মুলা
    var expandedGroupUsers by remember { mutableStateOf(false) } // ব্যবহারকারী ও অ্যাক্সেস
    var expandedGroupData by remember { mutableStateOf(false) } // ডেটা ব্যাকআপ ও এক্সপোর্ট
    var expandedGroupPreferences by remember { mutableStateOf(false) } // অ্যাপ পছন্দসমূহ

    // School info editable states
    var schoolName by remember(schoolInfo) { mutableStateOf(schoolInfo?.schoolName ?: "১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়") }
    var address by remember(schoolInfo) { mutableStateOf(schoolInfo?.address ?: "ডাকঘর: রামপুর, উপজেলা: সদর, জেলা: কুমিল্লা") }
    var emisCode by remember(schoolInfo) { mutableStateOf(schoolInfo?.emisCode ?: "134251") }
    var phone by remember(schoolInfo) { mutableStateOf(schoolInfo?.phone ?: "01711223344") }
    var email by remember(schoolInfo) { mutableStateOf(schoolInfo?.email ?: "") }
    var headTeacher by remember(schoolInfo) { mutableStateOf(schoolInfo?.headTeacherName ?: "মো: রফিকুল ইসলাম") }
    var internalVillages by remember(schoolInfo) { mutableStateOf(schoolInfo?.internalVillages ?: "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর") }
    var tagline by remember(schoolInfo) { mutableStateOf(schoolInfo?.tagline ?: "জ্ঞান, মনন ও স্বপ্নের সোপান") }
    var logoUri by remember(schoolInfo) { mutableStateOf(schoolInfo?.logoUri) }

    val customSchoolInfoItems = remember(schoolInfo?.customSchoolInfoJson) {
        SchoolCustomInfoHelper.parse(schoolInfo?.customSchoolInfoJson)
    }

    // Dialog controllers
    var showEditSchoolDialog by remember { mutableStateOf(false) }
    var showSchoolPhotoDialog by remember { mutableStateOf(false) }
    var showCustomSchoolInfoManagerDialog by remember { mutableStateOf(false) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<CustomFieldEntity?>(null) }
    var editingRule by remember { mutableStateOf<FormulaRuleEntity?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showExportJsonDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showClearDataConfirmDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showDataImportExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }

    // Preferences
    val currentLanguage by viewModel.appLanguage.collectAsState()
    var selectedTheme by remember { mutableStateOf("সিস্টেম ডিফল্ট") }

    // School Profile Logo bitmap decode
    val schoolLogoBitmap = remember(logoUri) {
        try {
            if (!logoUri.isNullOrBlank() && logoUri!!.startsWith("data:image")) {
                val b64 = logoUri!!.substringAfter("base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "বিদ্যালয় তথ্য ও সেটিংস",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "মৌলিক তথ্য, কাস্টম এন্ট্রি ফিল্ড ও ফর্মুলা কনফিগারেশন",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "v3.0",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // ==========================================
        // GROUP 1: বিদ্যালয়ের মৌলিক তথ্য (SCHOOL BASIC INFO)
        // ==========================================
        SettingsGroupCard(
            title = "বিদ্যালয়ের মৌলিক তথ্য (School Basic Info)",
            subtitle = "নাম, EMIS কোড, প্রোফাইল ছবি, ইমেইল, প্রধান শিক্ষক ও কাস্টম তথ্য",
            icon = Icons.Filled.School,
            isExpanded = expandedGroupSchoolInfo,
            onToggle = { expandedGroupSchoolInfo = !expandedGroupSchoolInfo },
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // School Profile Photo Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { showSchoolPhotoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (schoolLogoBitmap != null) {
                            Image(
                                bitmap = schoolLogoBitmap.asImageBitmap(),
                                contentDescription = "বিদ্যালয়ের প্রোফাইল ছবি",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = "School Logo",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "বিদ্যালয়ের প্রোফাইল ছবি / লোগো",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "পাসপোর্ট সাইজ, সোজা করা ও ব্যাকগ্রাউন্ড পরিবর্তন সুবিধা যুক্ত",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showSchoolPhotoDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (logoUri == null) "ছবি আপলোড / প্রসেসিং" else "ছবি পরিবর্তন ও এডিট", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsInfoRow("বিদ্যালয়ের নাম", schoolInfo?.schoolName ?: schoolName)
                SettingsInfoRow("স্লোগান / ট্যাগলাইন", schoolInfo?.tagline ?: tagline)
                SettingsInfoRow("EMIS Code / বিদ্যালয় কোড", schoolInfo?.emisCode ?: emisCode)
                SettingsInfoRow("প্রধান শিক্ষক", schoolInfo?.headTeacherName ?: headTeacher)
                SettingsInfoRow("যোগাযোগের ফোন", schoolInfo?.phone ?: phone)
                SettingsInfoRow("ইমেইল (Email)", (schoolInfo?.email ?: email).ifEmpty { "দেওয়া হয়নি" })
                SettingsInfoRow("ঠিকানা", schoolInfo?.address ?: address)
                SettingsInfoRow("অভ্যন্তরীণ গ্রামসমূহ", schoolInfo?.internalVillages ?: internalVillages)

                // Custom School Info Items Display
                if (customSchoolInfoItems.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("অতিরিক্ত কাস্টম তথ্যসমূহ:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    customSchoolInfoItems.forEach { item ->
                        SettingsInfoRow(item.key, item.value)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCustomSchoolInfoManagerDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("কাস্টম তথ্য সাজান", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showEditSchoolDialog = true },
                        modifier = Modifier.weight(1.2f).testTag("btn_edit_school_info")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("তথ্য সম্পাদনা", fontSize = 12.sp)
                    }
                }
            }
        }

        // ==========================================
        // GROUP 2: শিক্ষার্থী কাস্টম ফিল্ড ও ফর্মুলা (CUSTOM FIELDS & FORMULA)
        // ==========================================
        SettingsGroupCard(
            title = "শিক্ষার্থী কাস্টম এন্ট্রি ফিল্ড ও সূত্র (Formula Engine)",
            subtitle = "ভর্তি ফর্মে অতিরিক্ত ফিল্ড ও শর্তমূলক লজিক রুল কনফিগার করুন",
            icon = Icons.Filled.Tune,
            isExpanded = expandedGroupCustomFields,
            onToggle = { expandedGroupCustomFields = !expandedGroupCustomFields },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Custom Fields Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "কাস্টম ফিল্ডসমূহ (${BanglaUtils.toBanglaDigits(customFields.size)} টি)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showAddFieldDialog = true }, modifier = Modifier.testTag("btn_add_custom_field")) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Add Custom Field", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (customFields.isEmpty()) {
                    Text("কোন কাস্টম ফিল্ড তৈরি করা হয়নি। নতুন ফিল্ড যোগ করতে + বাটনে চাপুন।", fontSize = 12.sp, color = Color.Gray)
                } else {
                    customFields.forEach { cf ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cf.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("টাইপ: ${cf.fieldType}${if (cf.isCalculated) " • স্বয়ংক্রিয় সূত্র" else ""}${if (cf.groupName.isNotBlank()) " • গ্রুপ: ${cf.groupName}" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingField = cf }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteCustomField(cf) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Formula Rules Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "শর্তমূলক সূত্র ও নিয়মাবলী (${BanglaUtils.toBanglaDigits(formulaRules.size)} টি)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showAddRuleDialog = true }, modifier = Modifier.testTag("btn_add_formula_rule")) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "Add Rule", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (formulaRules.isEmpty()) {
                    Text("কোন শর্তমূলক নিয়ম তৈরি করা হয়নি।", fontSize = 12.sp, color = Color.Gray)
                } else {
                    formulaRules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.ruleName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("IF [${rule.sourceField}] ${rule.operator} '${rule.conditionValue}' THEN '${rule.resultIfTrue}' ELSE '${rule.resultIfFalse}'", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingRule = rule }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteFormulaRule(rule) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onNavigateToCustomFields,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("পূর্ণাঙ্গ ফিল্ড ও সূত্র পেজ খুলুন")
                }
            }
        }

        // ==========================================
        // GROUP 3: ব্যবহারকারী ও অ্যাক্সেস (USERS & SECURITY)
        // ==========================================
        SettingsGroupCard(
            title = "ব্যবহারকারী ও অ্যাক্সেস নিয়ন্ত্রণ",
            subtitle = "শিক্ষক ও অ্যাডমিনদের ভূমিকা এবং সিকিউরিটি পিন",
            icon = Icons.Filled.SupervisorAccount,
            isExpanded = expandedGroupUsers,
            onToggle = { expandedGroupUsers = !expandedGroupUsers },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("বর্তমান ভূমিকা (Role):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    var roleDropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { roleDropdownExpanded = true },
                            label = { Text(currentUserRole) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                        )
                        DropdownMenu(expanded = roleDropdownExpanded, onDismissRequest = { roleDropdownExpanded = false }) {
                            listOf("School Admin", "Teacher", "Staff").forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role) },
                                    onClick = {
                                        viewModel.setCurrentUserRole(role)
                                        roleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("নিবন্ধিত ব্যবহারকারী:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddUserDialog = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ব্যবহারকারী যোগ")
                    }
                }

                if (allUsers.isEmpty()) {
                    Text("প্রধান শিক্ষক ডিফল্ট অ্যাডমিন হিসেবে সেট করা আছে।", fontSize = 12.sp, color = Color.Gray)
                } else {
                    allUsers.forEach { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${user.role} • ${user.phone.ifEmpty { user.email.ifEmpty { "N/A" } }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteUser(user) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showPinChangeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("অ্যাডমিন সিকিউরিটি পিন পরিবর্তন করুন", fontSize = 12.sp)
                }
            }
        }

        // ==========================================
        // GROUP 4: শিক্ষার্থী ও অন্যান্য ডেটা এক্সপোর্ট ও ইম্পোর্ট
        // ==========================================
        SettingsGroupCard(
            title = "ডেটা এক্সপোর্ট ও ইম্পোর্ট (CSV / PDF / JSON)",
            subtitle = "শিক্ষার্থী, শিক্ষক, উপস্থিতি ও রুটিন ডেটা আদান-প্রদান",
            icon = Icons.Filled.ImportExport,
            isExpanded = expandedGroupData,
            onToggle = { expandedGroupData = !expandedGroupData },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsInfoRow("মোট নিবন্ধিত শিক্ষার্থী", "${allStudents.size} জন")
                SettingsInfoRow("মোট শিক্ষক ও স্টাফ", "${allUsers.size} জন")

                Button(
                    onClick = { showDataImportExportDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📊 CSV, Excel ও PDF ডেটা সেন্টার")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                exportedJsonText = viewModel.repository.exportAllDataToJson(allStudents)
                                showExportJsonDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup (JSON)", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showImportJsonDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore (JSON)", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Button(
                    onClick = { showClearDataConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("btn_reset_local_database"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সকল শিক্ষার্থী ডেটা মুছে ফেলুন (Reset)")
                }
            }
        }

        // ==========================================
        // GROUP 5: অ্যাপ পছন্দসমূহ ও তথ্য
        // ==========================================
        SettingsGroupCard(
            title = "অ্যাপ পছন্দসমূহ ও তথ্য (App Info)",
            subtitle = "ভাষা, থিম ও সংস্করণ বিবরণ",
            icon = Icons.Filled.Info,
            isExpanded = expandedGroupPreferences,
            onToggle = { expandedGroupPreferences = !expandedGroupPreferences },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Language
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ভাষা (Language)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = currentLanguage == com.example.util.Language.BANGLA,
                            onClick = { viewModel.setAppLanguage(com.example.util.Language.BANGLA) },
                            label = { Text("বাংলা") }
                        )
                        FilterChip(
                            selected = currentLanguage == com.example.util.Language.ENGLISH,
                            onClick = { viewModel.setAppLanguage(com.example.util.Language.ENGLISH) },
                            label = { Text("English") }
                        )
                    }
                }

                // Theme
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("থিম (App Theme)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedTheme == "সিস্টেম ডিফল্ট", onClick = { selectedTheme = "সিস্টেম ডিফল্ট" }, label = { Text("ডিফল্ট") })
                        FilterChip(selected = selectedTheme == "লাইট", onClick = { selectedTheme = "লাইট" }, label = { Text("লাইট") })
                        FilterChip(selected = selectedTheme == "ডার্ক", onClick = { selectedTheme = "ডার্ক" }, label = { Text("ডার্ক") })
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsInfoRow("অ্যাপ সংস্করণ", "ANWESHA School Platform v3.0")
                SettingsInfoRow("বিল্ড আইডি", "Android SDK 36 • Standalone Edition")
                SettingsInfoRow("মডেল", "অফলাইন-ফার্স্ট লোকাল SQLite Room")

                Text(
                    text = "© 2026 ANWESHA School Management Platform. জ্ঞান, মনন ও স্বপ্নের সোপান।",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ==========================================
    // DIALOG IMPLEMENTATIONS
    // ==========================================

    // 1. Edit School Info Dialog
    if (showEditSchoolDialog) {
        var tempName by remember { mutableStateOf(schoolName) }
        var tempTagline by remember { mutableStateOf(tagline) }
        var tempAddr by remember { mutableStateOf(address) }
        var tempEmis by remember { mutableStateOf(emisCode) }
        var tempPhone by remember { mutableStateOf(phone) }
        var tempEmail by remember { mutableStateOf(email) }
        var tempHead by remember { mutableStateOf(headTeacher) }
        var tempVill by remember { mutableStateOf(internalVillages) }

        AlertDialog(
            onDismissRequest = { showEditSchoolDialog = false },
            title = { Text("বিদ্যালয়ের মৌলিক তথ্য সম্পাদনা", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("বিদ্যালয়ের নাম *") }, modifier = Modifier.fillMaxWidth().testTag("input_school_name"))
                    OutlinedTextField(value = tempTagline, onValueChange = { tempTagline = it }, label = { Text("ট্যাগলাইন / স্লোগান") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tempAddr, onValueChange = { tempAddr = it }, label = { Text("ঠিকানা") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = tempEmis, onValueChange = { tempEmis = it }, label = { Text("EMIS Code / কোড") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = tempPhone, onValueChange = { tempPhone = it }, label = { Text("ফোন নম্বর") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = tempEmail, onValueChange = { tempEmail = it }, label = { Text("ইমেইল (Email)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tempHead, onValueChange = { tempHead = it }, label = { Text("প্রধান শিক্ষক") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tempVill, onValueChange = { tempVill = it }, label = { Text("অভ্যন্তরীণ গ্রামসমূহ (কমা দিয়ে লিখুন)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        schoolName = tempName
                        tagline = tempTagline
                        address = tempAddr
                        emisCode = tempEmis
                        phone = tempPhone
                        email = tempEmail
                        headTeacher = tempHead
                        internalVillages = tempVill

                        val info = SchoolInfoEntity(
                            id = 1,
                            schoolName = tempName,
                            tagline = tempTagline,
                            address = tempAddr,
                            eiinCode = tempEmis,
                            logoUri = logoUri,
                            phone = tempPhone,
                            email = tempEmail,
                            headTeacherName = tempHead,
                            internalVillages = tempVill,
                            customSchoolInfoJson = schoolInfo?.customSchoolInfoJson ?: "[]"
                        )
                        viewModel.updateSchoolInfo(info)
                        showEditSchoolDialog = false
                        Toast.makeText(context, "বিদ্যালয়ের তথ্য সফলভাবে হালনাগাদ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("btn_save_school_info")
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = { TextButton(onClick = { showEditSchoolDialog = false }) { Text("বাতিল") } }
        )
    }

    // 1b. School Photo Dialog
    if (showSchoolPhotoDialog) {
        PhotoCaptureDialog(
            currentPhotoUri = logoUri,
            title = "বিদ্যালয়ের প্রোফাইল ছবি / লোগো",
            onDismiss = { showSchoolPhotoDialog = false },
            onPhotoSelected = { newPhotoBase64 ->
                logoUri = newPhotoBase64
                val current = schoolInfo ?: SchoolInfoEntity(schoolName = schoolName)
                val updated = current.copy(logoUri = newPhotoBase64)
                viewModel.updateSchoolInfo(updated)
                Toast.makeText(context, "বিদ্যালয়ের প্রোফাইল ছবি সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 1c. Custom School Info Manager Dialog (Add, Edit, Delete, Rearrange)
    if (showCustomSchoolInfoManagerDialog) {
        SchoolCustomInfoManagerDialog(
            currentItems = customSchoolInfoItems,
            onDismiss = { showCustomSchoolInfoManagerDialog = false },
            onSave = { updatedList ->
                val json = SchoolCustomInfoHelper.toJson(updatedList)
                val current = schoolInfo ?: SchoolInfoEntity(schoolName = schoolName)
                val updated = current.copy(customSchoolInfoJson = json)
                viewModel.updateSchoolInfo(updated)
                showCustomSchoolInfoManagerDialog = false
                Toast.makeText(context, "কাস্টম তথ্য সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. Add User Dialog
    if (showAddUserDialog) {
        var uName by remember { mutableStateOf("") }
        var uPhone by remember { mutableStateOf("") }
        var uRole by remember { mutableStateOf("Teacher") }
        var uPin by remember { mutableStateOf("1234") }

        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("নতুন ব্যবহারকারী যোগ করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uName, onValueChange = { uName = it }, label = { Text("ব্যবহারকারীর নাম") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uPhone, onValueChange = { uPhone = it }, label = { Text("মোবাইল / ইমেইল") }, modifier = Modifier.fillMaxWidth())
                    Text("ব্যবহারকারীর ভূমিকা (Role):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Teacher", "Staff", "Admin").forEach { r ->
                            FilterChip(selected = uRole == r, onClick = { uRole = r }, label = { Text(r) })
                        }
                    }
                    OutlinedTextField(value = uPin, onValueChange = { uPin = it }, label = { Text("সিকিউরিটি পিন (PIN)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uName.isNotBlank()) {
                            val user = UserEntity(
                                userId = "user_${System.currentTimeMillis()}",
                                name = uName,
                                phone = uPhone,
                                role = uRole,
                                status = "Active",
                                securityPinHash = uPin,
                                createdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            )
                            viewModel.insertUser(user)
                            showAddUserDialog = false
                            Toast.makeText(context, "ব্যবহারকারী যুক্ত হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("যোগ করুন")
                }
            },
            dismissButton = { TextButton(onClick = { showAddUserDialog = false }) { Text("বাতিল") } }
        )
    }

    // 3. Export JSON Dialog
    if (showExportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showExportJsonDialog = false },
            title = { Text("ডেটা এক্সপোর্ট (JSON)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("শিক্ষার্থী তালিকা JSON ফরম্যাটে তৈরি হয়েছে:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = exportedJsonText.take(500) + if (exportedJsonText.length > 500) "\n\n... [মোট ${exportedJsonText.length} অক্ষর]" else "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("ANWESHA School Backup", exportedJsonText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "JSON ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    showExportJsonDialog = false
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("কপি করুন")
                }
            },
            dismissButton = { TextButton(onClick = { showExportJsonDialog = false }) { Text("বন্ধ করুন") } }
        )
    }

    // 4. Import JSON Dialog
    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("JSON ব্যাকআপ ইম্পোর্ট", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("পূর্বে এক্সপোর্ট করা Master JSON টেক্সট এখানে পেস্ট করুন:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("JSON পেস্ট করুন...") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.isNotBlank()) {
                            viewModel.importJsonBackup(importJsonInput) { success, count ->
                                if (success) {
                                    Toast.makeText(context, "সফলভাবে $count জন শিক্ষার্থীর তথ্য রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                    showImportJsonDialog = false
                                } else {
                                    Toast.makeText(context, "JSON ফরম্যাট ত্রুটিপূর্ণ", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("ইম্পোর্ট করুন")
                }
            },
            dismissButton = { TextButton(onClick = { showImportJsonDialog = false }) { Text("বাতিল") } }
        )
    }

    // 5. Clear Data Confirmation Dialog
    if (showClearDataConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirmDialog = false },
            title = { Text("⚠️ সকল শিক্ষার্থী ডেটা মুছে ফেলা", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "আপনি কি নিশ্চিত যে সকল শিক্ষার্থী তথ্য ডাটাবেস থেকে মুছে ফেলতে চান? এই পরিবর্তনটি পুনরুদ্ধার করা যাবে না।",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLocalData {
                            Toast.makeText(context, "সকল শিক্ষার্থীর ডেটা মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            showClearDataConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("হ্যাঁ, মুছে ফেলুন")
                }
            },
            dismissButton = { TextButton(onClick = { showClearDataConfirmDialog = false }) { Text("বাতিল") } }
        )
    }

    // 6. PIN Change Dialog
    if (showPinChangeDialog) {
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPinChangeDialog = false },
            title = { Text("অ্যাডমিন সিকিউরিটি পিন পরিবর্তন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newPin, onValueChange = { newPin = it }, label = { Text("নতুন ৪-ডিজিট পিন") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it }, label = { Text("পিন পুনরায় লিখুন") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.length >= 4 && newPin == confirmPin) {
                        Toast.makeText(context, "সিকিউরিটি পিন সফলভাবে আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                        showPinChangeDialog = false
                    } else {
                        Toast.makeText(context, "উভয় পিন এক হতে হবে (কমপক্ষে ৪ সংখ্যা)", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("সংরক্ষণ")
                }
            },
            dismissButton = { TextButton(onClick = { showPinChangeDialog = false }) { Text("বাতিল") } }
        )
    }

    // 7. Custom Field Add Dialog
    if (showAddFieldDialog) {
        CustomFieldAddDialog(
            availableCustomFields = customFields,
            onDismiss = { showAddFieldDialog = false },
            onSave = { field ->
                viewModel.insertCustomField(field)
                showAddFieldDialog = false
                Toast.makeText(context, "কাস্টম ফিল্ড তৈরি হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 7b. Custom Field Edit Dialog
    if (editingField != null) {
        CustomFieldAddEditDialog(
            initialField = editingField,
            availableCustomFields = customFields,
            onDismiss = { editingField = null },
            onSave = { field ->
                viewModel.insertCustomField(field)
                editingField = null
                Toast.makeText(context, "কাস্টম ফিল্ড হালনাগাদ করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 8. Formula Rule Add Dialog
    if (showAddRuleDialog) {
        FormulaRuleAddDialog(
            availableCustomFields = customFields,
            onDismiss = { showAddRuleDialog = false },
            onSave = { rule ->
                viewModel.insertFormulaRule(rule)
                showAddRuleDialog = false
                Toast.makeText(context, "নতুন সূত্র সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 8b. Formula Rule Edit Dialog
    if (editingRule != null) {
        FormulaRuleAddEditDialog(
            initialRule = editingRule,
            availableCustomFields = customFields,
            onDismiss = { editingRule = null },
            onSave = { rule ->
                viewModel.insertFormulaRule(rule)
                editingRule = null
                Toast.makeText(context, "সূত্র হালনাগাদ করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 8. Data Import Export Dialog
    if (showDataImportExportDialog) {
        DataImportExportDialog(
            viewModel = viewModel,
            onDismiss = { showDataImportExportDialog = false }
        )
    }
}

@Composable
fun SchoolCustomInfoManagerDialog(
    currentItems: List<SchoolCustomInfoItem>,
    onDismiss: () -> Unit,
    onSave: (List<SchoolCustomInfoItem>) -> Unit
) {
    val items = remember { mutableStateListOf<SchoolCustomInfoItem>().apply { addAll(currentItems) } }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("বিদ্যালয়ের কাস্টম তথ্য সাজান", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "নতুন তথ্য যুক্ত করুন এবং তীর চিহ্নের সাহায্যে ইচ্ছেমতো ক্রম সাজান:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Add new item input
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = newKey,
                                onValueChange = { newKey = it },
                                label = { Text("শিরোনাম (Key)", fontSize = 11.sp) },
                                placeholder = { Text("যেমন: প্রতিষ্ঠা সাল") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newValue,
                                onValueChange = { newValue = it },
                                label = { Text("মান (Value)", fontSize = 11.sp) },
                                placeholder = { Text("যেমন: ১৯৭২") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                    items.add(SchoolCustomInfoItem(key = newKey.trim(), value = newValue.trim()))
                                    newKey = ""
                                    newValue = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("তথ্য যোগ করুন", fontSize = 12.sp)
                        }
                    }
                }

                if (items.isEmpty()) {
                    Text("কোনো কাস্টম তথ্য যোগ করা হয়নি।", fontSize = 12.sp, color = Color.Gray)
                } else {
                    items.forEachIndexed { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.key, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(item.value, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val prev = items[index - 1]
                                                items[index - 1] = item
                                                items[index] = prev
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (index < items.size - 1) {
                                                val next = items[index + 1]
                                                items[index + 1] = item
                                                items[index] = next
                                            }
                                        },
                                        enabled = index < items.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                                    }

                                    // Delete
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(items.toList()) }) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

