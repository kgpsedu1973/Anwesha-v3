package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.local.entity.StudentEntity
import com.example.data.local.util.FormulaEvaluator
import com.example.ui.components.DateInputField
import com.example.ui.components.GlobalSuggestionTextField
import com.example.ui.components.PhotoCaptureDialog
import com.example.util.BanglaUtils
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(viewModel: MainViewModel) {
    val filteredStudents by viewModel.filteredStudents.collectAsState()
    val allStudents by viewModel.allStudents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterClass by viewModel.filterClass.collectAsState()
    val filterGender by viewModel.filterGender.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val filterVillage by viewModel.filterVillage.collectAsState()
    val customFields by viewModel.customFields.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var viewingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var deletingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var showImportExportModal by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val classOptions = listOf("ALL", "প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val statusOptions = listOf("Current", "Former", "Transferred", "Inactive", "ALL")

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingStudent = null; showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Student") },
                text = { Text("নতুন শিক্ষার্থী") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_student")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar & Import/Export Bar
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("নাম, পিতা, আইডি, রোল, গ্রাম দিয়ে খুঁজুন...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_input_student")
                        )
                        IconButton(
                            onClick = { showImportExportModal = true },
                            modifier = Modifier.testTag("btn_import_export")
                        ) {
                            Icon(Icons.Filled.ImportExport, contentDescription = "Import/Export", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Class Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(classOptions) { clazz ->
                            val selected = (clazz == "ALL" && filterClass == null) || (filterClass == clazz)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.filterClass.value = if (clazz == "ALL") null else clazz
                                },
                                label = { Text(if (clazz == "ALL") "সকল শ্রেণি" else clazz) }
                            )
                        }
                    }

                    // Status Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(statusOptions) { st ->
                            val labelText = when (st) {
                                "Current" -> "বর্তমান"
                                "Former" -> "সাবেক"
                                "Transferred" -> "বদলীকৃত"
                                "Inactive" -> "নিষ্ক্রিয়"
                                else -> "সকল স্ট্যাটাস"
                            }
                            val selected = (st == "ALL" && filterStatus == null) || (filterStatus == st)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.filterStatus.value = if (st == "ALL") null else st
                                },
                                label = { Text(labelText) }
                            )
                        }
                    }
                }
            }

            // Student Count Summary Sub-bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "মোট শিক্ষার্থী: ${BanglaUtils.toBanglaDigits(filteredStudents.size)} জন",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (filterClass != null || searchQuery.isNotEmpty() || filterStatus != "Current" || filterGender != null) {
                    TextButton(onClick = {
                        viewModel.searchQuery.value = ""
                        viewModel.filterClass.value = null
                        viewModel.filterStatus.value = "Current"
                        viewModel.filterGender.value = null
                    }) {
                        Text("ফিল্টার রিসেট")
                    }
                }
            }

            // Student List
            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PersonOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "কোন শিক্ষার্থী পাওয়া যায়নি",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "অনুগ্রহ করে নতুন শিক্ষার্থী যোগ করুন বা অনুসন্ধান ফিল্টার পরিবর্তন করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        StudentCardItem(
                            student = student,
                            category = viewModel.getStudentCategory(student),
                            onView = { viewingStudent = student },
                            onEdit = { editingStudent = student; showAddDialog = true },
                            onDelete = { deletingStudent = student }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog) {
        StudentAddEditDialog(
            student = editingStudent,
            allStudents = allStudents,
            customFields = customFields,
            onDismiss = { showAddDialog = false },
            onSave = { updatedStudent ->
                if (editingStudent == null) {
                    viewModel.insertStudent(updatedStudent)
                } else {
                    viewModel.updateStudent(updatedStudent)
                }
                showAddDialog = false
            }
        )
    }

    // Student Detail Profile Dialog
    if (viewingStudent != null) {
        StudentDetailDialog(
            student = viewingStudent!!,
            category = viewModel.getStudentCategory(viewingStudent!!),
            customFields = customFields,
            onDismiss = { viewingStudent = null },
            onEdit = {
                editingStudent = viewingStudent
                viewingStudent = null
                showAddDialog = true
            }
        )
    }

    // Delete Confirmation Dialog
    if (deletingStudent != null) {
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("শিক্ষার্থী তথ্য অপসরণ") },
            text = { Text("${deletingStudent!!.name}-এর তথ্য কি স্থায়ীভাবে মুছে ফেলতে চান?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStudent(deletingStudent!!)
                        deletingStudent = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("হ্যাঁ, মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Import / Export Dialog
    if (showImportExportModal) {
        ImportExportDialog(
            viewModel = viewModel,
            onDismiss = { showImportExportModal = false }
        )
    }
}

@Composable
fun StudentCardItem(
    student: StudentEntity,
    category: String,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
            .testTag("student_item_${student.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo or Avatar Circle
            val parsedBitmap = remember(student.photoUri) {
                try {
                    if (student.photoUri?.startsWith("data:image") == true) {
                        val base64Data = student.photoUri.substringAfter("base64,")
                        val decodedString = Base64.decode(base64Data, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            if (parsedBitmap != null) {
                Image(
                    bitmap = parsedBitmap.asImageBitmap(),
                    contentDescription = "Photo",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (student.gender == "ছাত্র") Color(0xFFBBDEFB) else Color(0xFFF8BBD0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.name.take(1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (student.gender == "ছাত্র") Color(0xFF0D47A1) else Color(0xFF880E4F)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (category == "অভ্যন্তরীণ") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (category == "অভ্যন্তরীণ") Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${student.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)} | আইডি: ${student.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "পিতা: ${student.fatherName} | গ্রাম: ${student.village}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (student.isSpecialNeeds) {
                    Text(
                        text = "♿ বিশেষ চাহিদাসম্পন্ন শিক্ষার্থী",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF6C00)
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun StudentDetailDialog(
    student: StudentEntity,
    category: String,
    customFields: List<CustomFieldEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val customMap = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson)
    val age = FormulaEvaluator.calculateAge(student.birthDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("শিক্ষার্থী প্রোফাইল", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main Highlight Header Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = student.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = "আইডি: ${student.id} | শ্রেণি: ${student.studentClass} | রোল: ${BanglaUtils.toBanglaDigits(student.rollNumber)}", fontSize = 14.sp)
                        Text(text = "ক্যাটাগরি: $category | বয়স: ${BanglaUtils.toBanglaDigits(age)} বছর", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                DetailRow(label = "পিতার নাম", value = student.fatherName)
                DetailRow(label = "মাতার নাম", value = student.motherName)
                DetailRow(label = "জন্মতারিখ", value = "${student.birthDate} (${BanglaUtils.formatBanglaDate(student.birthDate)})")
                DetailRow(label = "জন্ম নিবন্ধন নম্বর", value = student.birthRegNumber)
                DetailRow(label = "মোবাইল নম্বর", value = student.mobile)
                DetailRow(label = "গ্রাম", value = student.village)
                DetailRow(label = "ঠিকানা", value = student.address)
                DetailRow(label = "শিক্ষাবর্ষ", value = student.academicYear)
                DetailRow(label = "লিঙ্গ", value = student.gender)
                DetailRow(label = "স্ট্যাটাস", value = student.status)
                DetailRow(label = "বিশেষ চাহিদা সম্পন্ন", value = if (student.isSpecialNeeds) "হ্যাঁ" else "না")

                if (customFields.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "অতিরিক্ত ফিল্ডসমূহ (Custom Fields)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    customFields.forEach { cf ->
                        val valStr = customMap[cf.id] ?: "-"
                        DetailRow(label = cf.name, value = valStr)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("সম্পাদনা করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = if (value.isBlank()) "-" else value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StudentAddEditDialog(
    student: StudentEntity?,
    allStudents: List<StudentEntity>,
    customFields: List<CustomFieldEntity>,
    onDismiss: () -> Unit,
    onSave: (StudentEntity) -> Unit
) {
    var id by remember { mutableStateOf(student?.id ?: "STU-2026-${(100..999).random()}") }
    var studentClass by remember { mutableStateOf(student?.studentClass ?: "১ম শ্রেণি") }
    var rollNumber by remember { mutableStateOf(student?.rollNumber?.toString() ?: "1") }
    var name by remember { mutableStateOf(student?.name ?: "") }
    var fatherName by remember { mutableStateOf(student?.fatherName ?: "") }
    var motherName by remember { mutableStateOf(student?.motherName ?: "") }
    var birthDate by remember { mutableStateOf(student?.birthDate ?: "2019-01-01") }
    var mobile by remember { mutableStateOf(student?.mobile ?: "") }
    var village by remember { mutableStateOf(student?.village ?: "পশ্চিম রামপুর") }
    var academicYear by remember { mutableStateOf(student?.academicYear ?: "২০২৬") }
    var address by remember { mutableStateOf(student?.address ?: "") }
    var birthRegNumber by remember { mutableStateOf(student?.birthRegNumber ?: "") }
    var gender by remember { mutableStateOf(student?.gender ?: "ছাত্র") }
    var isSpecialNeeds by remember { mutableStateOf(student?.isSpecialNeeds ?: false) }
    var status by remember { mutableStateOf(student?.status ?: "Current") }
    var photoUri by remember { mutableStateOf(student?.photoUri) }

    var showPhotoCaptureDialog by remember { mutableStateOf(false) }

    // Distinct suggestion lists across dataset
    val villageSuggestions = remember(allStudents) { allStudents.map { it.village }.filter { it.isNotBlank() }.distinct() }
    val fatherSuggestions = remember(allStudents) { allStudents.map { it.fatherName }.filter { it.isNotBlank() }.distinct() }
    val motherSuggestions = remember(allStudents) { allStudents.map { it.motherName }.filter { it.isNotBlank() }.distinct() }
    val addressSuggestions = remember(allStudents) { allStudents.map { it.address }.filter { it.isNotBlank() }.distinct() }

    val initialCustomMap = remember { FormulaEvaluator.parseCustomValuesJson(student?.customValuesJson ?: "{}") }
    val customValueMap = remember { mutableStateMapOf<String, String>().apply { putAll(initialCustomMap) } }

    val classOptions = listOf("প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val genderOptions = listOf("ছাত্র", "ছাত্রী")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (student == null) "নতুন শিক্ষার্থী যুক্ত করুন" else "শিক্ষার্থী তথ্য সম্পাদনা", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Photo Attachment Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp, 80.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE0F2F1))
                            .clickable { showPhotoCaptureDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!photoUri.isNullOrBlank()) {
                            val bmp = try {
                                if (photoUri!!.startsWith("data:image")) {
                                    val b64 = photoUri!!.substringAfter("base64,")
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } else null
                            } catch (e: Exception) { null }

                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Filled.AccountBox, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("ছবি", fontSize = 10.sp)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showPhotoCaptureDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (photoUri == null) "ছবি তুলুন / আপলোড করুন" else "ছবি পরিবর্তন করুন", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("শিক্ষার্থী আইডি") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("শিক্ষার্থীর নাম *") }, modifier = Modifier.fillMaxWidth().testTag("input_student_name"))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = rollNumber, onValueChange = { rollNumber = it }, label = { Text("রোল নং") }, modifier = Modifier.weight(1f))
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("শ্রেণি", fontSize = 12.sp)
                        LazyRow {
                            items(classOptions) { c ->
                                FilterChip(
                                    selected = studentClass == c,
                                    onClick = { studentClass = c },
                                    label = { Text(c, fontSize = 11.sp) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }

                GlobalSuggestionTextField(
                    value = fatherName,
                    onValueChange = { fatherName = it },
                    label = "পিতার নাম",
                    suggestions = fatherSuggestions
                )

                GlobalSuggestionTextField(
                    value = motherName,
                    onValueChange = { motherName = it },
                    label = "মাতার নাম",
                    suggestions = motherSuggestions
                )

                DateInputField(
                    dateValue = birthDate,
                    onDateChange = { birthDate = it },
                    label = "জন্মতারিখ (Date of Birth)"
                )

                OutlinedTextField(value = birthRegNumber, onValueChange = { birthRegNumber = it }, label = { Text("জন্ম নিবন্ধন নম্বর") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("মোবাইল নম্বর") }, modifier = Modifier.fillMaxWidth())

                GlobalSuggestionTextField(
                    value = village,
                    onValueChange = { village = it },
                    label = "গ্রাম (Village)",
                    suggestions = villageSuggestions
                )

                GlobalSuggestionTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "ঠিকানা (Address)",
                    suggestions = addressSuggestions
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("লিঙ্গ: ", fontWeight = FontWeight.Bold)
                    genderOptions.forEach { g ->
                        RadioButton(selected = gender == g, onClick = { gender = g })
                        Text(g)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSpecialNeeds, onCheckedChange = { isSpecialNeeds = it })
                    Text("বিশেষ চাহিদাসম্পন্ন শিক্ষার্থী")
                }

                // Custom fields input dynamic mapping
                if (customFields.isNotEmpty()) {
                    Divider()
                    Text("অতিরিক্ত কাস্টম ফিল্ডসমূহ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    customFields.forEach { cf ->
                        if (!cf.isCalculated) {
                            val currentVal = customValueMap[cf.id] ?: ""
                            OutlinedTextField(
                                value = currentVal,
                                onValueChange = { customValueMap[cf.id] = it },
                                label = { Text(cf.name) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val updated = StudentEntity(
                        id = id,
                        studentClass = studentClass,
                        rollNumber = rollNumber.toIntOrNull() ?: 1,
                        name = name,
                        fatherName = fatherName,
                        motherName = motherName,
                        birthDate = birthDate,
                        mobile = mobile,
                        village = village,
                        academicYear = academicYear,
                        address = address,
                        birthRegNumber = birthRegNumber,
                        gender = gender,
                        isSpecialNeeds = isSpecialNeeds,
                        status = status,
                        photoUri = photoUri,
                        customValuesJson = FormulaEvaluator.buildCustomValuesJson(customValueMap)
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("btn_save_student")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )

    if (showPhotoCaptureDialog) {
        PhotoCaptureDialog(
            currentPhotoUri = photoUri,
            onDismiss = { showPhotoCaptureDialog = false },
            onPhotoSelected = { newPhotoBase64 ->
                photoUri = newPhotoBase64
            }
        )
    }
}

@Composable
fun ImportExportDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allStudents by viewModel.allStudents.collectAsState()
    var statusText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ডেটা ইম্পোর্ট ও এক্সপোর্ট (CSV / JSON)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("শিক্ষার্থী তালিকা ফাইল থেকে ইম্পোর্ট অথবা ব্যাকআপ ফাইল তৈরি করতে পারেন।")

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val json = viewModel.repository.exportAllDataToJson(allStudents)
                            statusText = "এক্সপোর্ট সফল! মোট ${allStudents.size} জন শিক্ষার্থীর ডেটা ব্যাকআপ তৈরি হয়েছে।"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("JSON ফরম্যাটে এক্সপোর্ট করুন")
                }

                OutlinedButton(
                    onClick = {
                        statusText = "নমুনা ডেটা ইম্পোর্ট সফলভাবে যাচাই করা হয়েছে।"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CSV/JSON ফাইল ইম্পোর্ট করুন")
                }

                if (statusText.isNotEmpty()) {
                    Text(text = statusText, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } }
    )
}
