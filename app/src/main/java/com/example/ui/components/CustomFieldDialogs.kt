package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.util.FormulaEvaluator
import com.example.util.BanglaUtils

@Composable
fun CustomFieldAddEditDialog(
    initialField: CustomFieldEntity? = null,
    availableCustomFields: List<CustomFieldEntity> = emptyList(),
    formulaRules: List<FormulaRuleEntity> = emptyList(),
    existingGroups: List<String> = emptyList(),
    sampleStudent: StudentEntity? = null,
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit,
    onSaveWithCalculation: ((CustomFieldEntity, FormulaRuleEntity?, Boolean) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember(initialField) { mutableStateOf(initialField?.name ?: "") }
    var fieldType by remember(initialField) { mutableStateOf(initialField?.fieldType ?: "Text") }
    var optionsJson by remember(initialField) { mutableStateOf(initialField?.optionsJson ?: "") }
    var groupName by remember(initialField) { mutableStateOf(initialField?.groupName ?: "কাস্টম তথ্য") }
    var selectedFormulaRuleId by remember(initialField) { mutableStateOf(initialField?.formulaRuleId ?: "") }
    var isCreatingNewGroup by remember { mutableStateOf(false) }

    // Direct inline formula creation for Calculated fields
    var formulaSourceField by remember { mutableStateOf("village") }
    var formulaOperator by remember { mutableStateOf("IN_LIST") }
    var formulaConditionValue by remember { mutableStateOf("পশ্চিম রামপুর,আমতলী") }
    var formulaResultIfTrue by remember { mutableStateOf("অভ্যন্তরীণ") }
    var formulaResultIfFalse by remember { mutableStateOf("বহিরাগত") }
    var autoFillExistingStudents by remember { mutableStateOf(true) }
    var formulaMode by remember { mutableStateOf(if (formulaRules.isNotEmpty()) "SELECT" else "CREATE") } // "SELECT" or "CREATE"

    // Live test in sandbox
    var testSourceInput by remember { mutableStateOf("পশ্চিম রামপুর") }
    val testCalculatedOutput = remember(testSourceInput, formulaOperator, formulaConditionValue, formulaResultIfTrue, formulaResultIfFalse) {
        val matched = FormulaEvaluator.checkCondition(testSourceInput, formulaOperator, formulaConditionValue)
        if (matched) formulaResultIfTrue else formulaResultIfFalse
    }

    // Aggregate all existing group names
    val allGroupSuggestions = remember(availableCustomFields, existingGroups) {
        val defaultList = listOf("কাস্টম তথ্য", "মৌলিক তথ্য", "পারিবারিক তথ্য", "ঠিকানা ও যোগাযোগ", "স্বাস্থ্য তথ্য", "একাডেমিক তথ্য", "হিসাব ও ফলাফল")
        val fromLayout = try {
            com.example.util.FormLayoutManager.loadGroups(context, availableCustomFields).map { it.title }
        } catch (e: Exception) {
            emptyList()
        }
        val fromFields = availableCustomFields.map { it.groupName }
        (defaultList + fromLayout + fromFields + existingGroups)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val types = listOf(
        "Text" to "টেক্সট (Text)",
        "Number" to "সংখ্যা (Number)",
        "Date" to "তারিখ (Date)",
        "Phone" to "মোবাইল/ফোন (Phone)",
        "Dropdown" to "ড্রপডাউন তালিকা (Dropdown)",
        "Yes/No" to "হ্যাঁ / না (Yes/No)",
        "Multiple choice" to "মাল্টিপল চয়েস (Multiple choice)",
        "Long text" to "বড় বিবরণ (Long text)",
        "Calculated" to "হিসাবকৃত / শর্তভিত্তিক (Calculated / Formula)"
    )

    val operators = listOf(
        "EQUALS" to "সমান (=)",
        "NOT_EQUALS" to "অসমান (!=)",
        "IN_LIST" to "তালিকায় আছে (IN_LIST)",
        "CONTAINS" to "শব্দ ধারণ করে (CONTAINS)",
        "GREATER_THAN" to "বড় (>)",
        "LESS_THAN" to "ছোট (<)",
        "STARTS_WITH" to "শুরু হয়",
        "ENDS_WITH" to "শেষ হয়"
    )

    val sourceFieldSuggestions = listOf(
        "village" to "গ্রাম (Village)",
        "studentClass" to "শ্রেণি (Class)",
        "gender" to "লিঙ্গ (Gender)",
        "rollNumber" to "রোল নং (Roll)",
        "academicYear" to "শিক্ষাবর্ষ (Year)",
        "isSpecialNeeds" to "বিশেষ চাহিদা",
        "birthDate" to "জন্মতারিখ",
        "age" to "বয়স (Age)",
        "mobile" to "মোবাইল",
        "status" to "স্ট্যাটাস"
    ) + availableCustomFields.filter { it.id != initialField?.id }.map {
        it.id to "${it.name} (Custom)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (fieldType == "Calculated") Icons.Filled.Calculate else Icons.Filled.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (initialField == null) "নতুন কাস্টম ফিল্ড তৈরি" else "কাস্টম ফিল্ড সম্পাদনা",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ফিল্ডের নাম (Label) *") },
                    placeholder = { Text("যেমন: রক্তের গ্রুপ / উপবৃত্তি যোগ্যতা / ফি ছাড়") },
                    modifier = Modifier.fillMaxWidth().testTag("input_custom_field_name"),
                    singleLine = true
                )

                // Group Selection with Suggestions & Add New Group
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "গ্রুপের নাম (Group):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { 
                            groupName = it
                            isCreatingNewGroup = it.isNotBlank() && !allGroupSuggestions.contains(it)
                        },
                        label = { Text("গ্রুপের নাম") },
                        placeholder = { Text("যেমন: কাস্টম তথ্য / স্বাস্থ্য তথ্য") },
                        modifier = Modifier.fillMaxWidth().testTag("input_custom_field_group")
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        items(allGroupSuggestions) { g ->
                            val isSelected = groupName == g
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    groupName = g
                                    isCreatingNewGroup = false
                                },
                                label = { Text(g, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Field Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ফিল্ডের ধরন নির্বাচন করুন:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            types.forEach { (tKey, tLabel) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { fieldType = tKey }
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = fieldType == tKey,
                                        onClick = { fieldType = tKey }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tLabel,
                                        fontSize = 13.sp,
                                        fontWeight = if (fieldType == tKey) FontWeight.Bold else FontWeight.Normal,
                                        color = if (tKey == "Calculated") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Conditional UI for Dropdown / Multi-choice
                if (fieldType == "Dropdown" || fieldType == "Multiple choice") {
                    OutlinedTextField(
                        value = optionsJson,
                        onValueChange = { optionsJson = it },
                        label = { Text("অপশনসমূহ (কমা দিয়ে আলাদা করুন)") },
                        placeholder = { Text("যেমন: A+, B+, AB+, O+, জানা নেই") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Conditional UI for Calculated Field
                if (fieldType == "Calculated") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("তাৎক্ষণিক ক্যালকুলেটর ও সূত্র কনফিগারেশন", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = "এই ফিল্ডের জন্য স্বয়ংক্রিয় হিসাবের শর্ত বা সূত্র এখনই সেট করুন এবং তাৎক্ষণিক ফলাফল টেস্ট করুন:",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (formulaRules.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = formulaMode == "CREATE",
                                        onClick = { formulaMode = "CREATE" },
                                        label = { Text("নতুন সূত্র তৈরি করুন", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = formulaMode == "SELECT",
                                        onClick = { formulaMode = "SELECT" },
                                        label = { Text("বিদ্যমান সূত্র নির্বাচন (${BanglaUtils.toBanglaDigits(formulaRules.size)})", fontSize = 11.sp) }
                                    )
                                }

                                if (formulaMode == "SELECT") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(formulaRules) { rule ->
                                            FilterChip(
                                                selected = selectedFormulaRuleId == rule.id,
                                                onClick = {
                                                    selectedFormulaRuleId = if (selectedFormulaRuleId == rule.id) "" else rule.id
                                                },
                                                label = { Text(rule.ruleName, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (formulaMode == "CREATE" || formulaRules.isEmpty()) {
                                // Source Field Selection
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("উৎস ফিল্ড (কার উপর ভিত্তি করে হিসাব হবে):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(sourceFieldSuggestions) { (sKey, sLabel) ->
                                            FilterChip(
                                                selected = formulaSourceField == sKey,
                                                onClick = { formulaSourceField = sKey },
                                                label = { Text(sLabel, fontSize = 10.5.sp) }
                                            )
                                        }
                                    }
                                }

                                // Operator Selection
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("শর্ত বা অপারেটর:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(operators) { (opKey, opLabel) ->
                                            FilterChip(
                                                selected = formulaOperator == opKey,
                                                onClick = { formulaOperator = opKey },
                                                label = { Text(opLabel, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = formulaConditionValue,
                                    onValueChange = { formulaConditionValue = it },
                                    label = { Text("শর্তের মান (যেমন: পশ্চিম রামপুর,আমতলী বা 2018)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = formulaResultIfTrue,
                                        onValueChange = { formulaResultIfTrue = it },
                                        label = { Text("শর্ত মিললে মান (IF True)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = formulaResultIfFalse,
                                        onValueChange = { formulaResultIfFalse = it },
                                        label = { Text("না মিললে মান (IF False)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                // Live Sandbox Test
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("🧪 তাৎক্ষণিক লাইভ টেস্ট প্রিভিউ:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = testSourceInput,
                                                onValueChange = { testSourceInput = it },
                                                label = { Text("পরীক্ষামূলক ইনপুট") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text(
                                                    text = "ফলাফল: $testCalculatedOutput",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Auto Fill Existing Students Checkbox
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { autoFillExistingStudents = !autoFillExistingStudents }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = autoFillExistingStudents,
                                    onCheckedChange = { autoFillExistingStudents = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "বিদ্যমান সকল শিক্ষার্থীর জন্য এখনই হিসাব করে ডেটা পূরণ করুন",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "সংরক্ষণের সাথে সাথে ডাটাবেসে প্রতিটি শিক্ষার্থীর এই ফিল্ড আপডেট হয়ে যাবে",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                    if (name.isBlank()) return@Button
                    val generatedRuleId = if (fieldType == "Calculated") {
                        if (formulaMode == "SELECT" && selectedFormulaRuleId.isNotBlank()) selectedFormulaRuleId
                        else "rule_${System.currentTimeMillis()}"
                    } else null

                    val cf = CustomFieldEntity(
                        id = initialField?.id ?: "cf_${System.currentTimeMillis()}",
                        name = name.trim(),
                        fieldType = fieldType,
                        optionsJson = if (optionsJson.isBlank()) null else optionsJson.trim(),
                        isCalculated = fieldType == "Calculated",
                        formulaRuleId = generatedRuleId,
                        groupName = if (groupName.isBlank()) "কাস্টম তথ্য" else groupName.trim(),
                        orderIndex = initialField?.orderIndex ?: 0
                    )

                    val createdRule = if (fieldType == "Calculated" && (formulaMode == "CREATE" || selectedFormulaRuleId.isBlank())) {
                        FormulaRuleEntity(
                            id = generatedRuleId ?: "rule_${System.currentTimeMillis()}",
                            ruleName = "${name.trim()} হিসাবের নিয়ম",
                            targetFieldName = name.trim(),
                            sourceField = formulaSourceField,
                            operator = formulaOperator,
                            conditionValue = formulaConditionValue.trim(),
                            resultIfTrue = formulaResultIfTrue.trim(),
                            resultIfFalse = formulaResultIfFalse.trim()
                        )
                    } else null

                    if (onSaveWithCalculation != null) {
                        onSaveWithCalculation(cf, createdRule, fieldType == "Calculated" && autoFillExistingStudents)
                    } else {
                        onSave(cf)
                    }
                },
                modifier = Modifier.testTag("btn_save_custom_field")
            ) {
                Text(if (initialField == null) "সংরক্ষণ করুন" else "হালনাগাদ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun CustomFieldAddDialog(
    availableCustomFields: List<CustomFieldEntity> = emptyList(),
    formulaRules: List<FormulaRuleEntity> = emptyList(),
    existingGroups: List<String> = emptyList(),
    sampleStudent: StudentEntity? = null,
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit,
    onSaveWithCalculation: ((CustomFieldEntity, FormulaRuleEntity?, Boolean) -> Unit)? = null
) {
    CustomFieldAddEditDialog(
        initialField = null,
        availableCustomFields = availableCustomFields,
        formulaRules = formulaRules,
        existingGroups = existingGroups,
        sampleStudent = sampleStudent,
        onDismiss = onDismiss,
        onSave = onSave,
        onSaveWithCalculation = onSaveWithCalculation
    )
}

@Composable
fun FormulaRuleAddEditDialog(
    initialRule: FormulaRuleEntity? = null,
    availableCustomFields: List<CustomFieldEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (FormulaRuleEntity) -> Unit
) {
    var ruleName by remember(initialRule) { mutableStateOf(initialRule?.ruleName ?: "") }
    var targetField by remember(initialRule) { mutableStateOf(initialRule?.targetFieldName ?: "শিক্ষার্থীর ধরণ") }
    var sourceField by remember(initialRule) { mutableStateOf(initialRule?.sourceField ?: "village") }
    var operator by remember(initialRule) { mutableStateOf(initialRule?.operator ?: "IN_LIST") }
    var conditionValue by remember(initialRule) { mutableStateOf(initialRule?.conditionValue ?: "পশ্চিম রামপুর,আমতলী") }
    var resultIfTrue by remember(initialRule) { mutableStateOf(initialRule?.resultIfTrue ?: "অভ্যন্তরীণ") }
    var resultIfFalse by remember(initialRule) { mutableStateOf(initialRule?.resultIfFalse ?: "বহিরাগত") }

    // Test preview calculation
    var testInputValue by remember { mutableStateOf("") }
    val testOutput = remember(testInputValue, operator, conditionValue, resultIfTrue, resultIfFalse) {
        if (testInputValue.isBlank()) ""
        else {
            val met = FormulaEvaluator.checkCondition(testInputValue, operator, conditionValue)
            if (met) resultIfTrue else resultIfFalse
        }
    }

    val operators = listOf(
        "IN_LIST" to "তালিকায় অন্তর্ভুক্ত (IN_LIST)",
        "EQUALS" to "হুবহু সমান (EQUALS)",
        "NOT_EQUALS" to "অসমান (NOT_EQUALS)",
        "CONTAINS" to "শব্দ ধারণ করে (CONTAINS)",
        "GREATER_THAN" to "বড় (GREATER_THAN)",
        "LESS_THAN" to "ছোট (LESS_THAN)",
        "STARTS_WITH" to "শুরু হয় (STARTS_WITH)",
        "ENDS_WITH" to "শেষ হয় (ENDS_WITH)"
    )

    // Target Field Suggestions (Standard + All Custom Fields including Calculated)
    val defaultTargetSuggestions = listOf(
        "শিক্ষার্থীর ধরণ",
        "উপবৃত্তি যোগ্যতা",
        "ফি ছাড় / স্কলারশিপ",
        "হাউস / দল",
        "স্বাস্থ্য স্ট্যাটাস",
        "রক্তের গ্রুপ",
        "বিশেষ সুযোগ",
        "বয়স স্ট্যাটাস"
    )
    val customTargetSuggestions = availableCustomFields.map { it.name }
    val allTargetSuggestions = (defaultTargetSuggestions + customTargetSuggestions).distinct()

    // Source Field Suggestions (Standard + All Custom Fields including Calculated!)
    val sourceFieldSuggestions = listOf(
        "village" to "গ্রাম (Village)",
        "studentClass" to "শ্রেণি (Class)",
        "gender" to "লিঙ্গ (Gender)",
        "rollNumber" to "রোল নং (Roll)",
        "academicYear" to "শিক্ষাবর্ষ (Year)",
        "isSpecialNeeds" to "বিশেষ চাহিদা (Special Needs)",
        "birthDate" to "জন্মতারিখ (DOB)",
        "age" to "বয়স (Age)",
        "mobile" to "মোবাইল (Mobile)",
        "address" to "ঠিকানা (Address)",
        "fatherName" to "পিতার নাম (Father)",
        "motherName" to "মাতার নাম (Mother)",
        "status" to "স্ট্যাটাস (Status)"
    ) + availableCustomFields.map { 
        it.id to (if (it.isCalculated) "${it.name} (Calculated)" else "${it.name} (Custom)")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Functions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (initialRule == null) "নতুন সূত্র / ফাংশন যুক্ত করুন" else "সূত্র / ফাংশন সম্পাদনা",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("ফাংশন / নিয়মের নাম (Rule Name) *") },
                    placeholder = { Text("যেমন: গ্রাম ভিত্তিক অভ্যন্তরীণ শিক্ষার্থী নির্ণয়") },
                    modifier = Modifier.fillMaxWidth().testTag("input_formula_rule_name"),
                    singleLine = true
                )

                // Target Field with Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = targetField,
                        onValueChange = { targetField = it },
                        label = { Text("টার্গেট ফিল্ডের নাম (Target Field) *") },
                        placeholder = { Text("যেমন: শিক্ষার্থীর ধরণ বা কাস্টম ফিল্ডের নাম") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("টার্গেট ফিল্ড সাজেশন (ক্লিক করে নির্বাচন করুন):", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allTargetSuggestions) { s ->
                            FilterChip(
                                selected = targetField == s,
                                onClick = { targetField = s },
                                label = { Text(s, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                // Source Field with Suggestions (Includes Calculated Fields!)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = sourceField,
                        onValueChange = { sourceField = it },
                        label = { Text("উৎস ফিল্ড (Source Field: e.g. village বা age) *") },
                        placeholder = { Text("যেমন: village, age, studentClass") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("উৎস ফিল্ড সাজেশন (ক্লিক করে নির্বাচন করুন):", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(sourceFieldSuggestions) { (key, label) ->
                            FilterChip(
                                selected = sourceField == key,
                                onClick = { sourceField = key },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                Text("অপারেটর / শর্তের ধরণ:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(operators) { (opKey, opLabel) ->
                        FilterChip(
                            selected = operator == opKey,
                            onClick = { operator = opKey },
                            label = { Text(opLabel, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = conditionValue,
                    onValueChange = { conditionValue = it },
                    label = { Text("শর্তের মান (Condition Value) *") },
                    placeholder = { Text("যেমন: পশ্চিম রামপুর,আমতলী বা 2018") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = resultIfTrue,
                    onValueChange = { resultIfTrue = it },
                    label = { Text("শর্ত সত্য হলে আউটপুট (IF True) *") },
                    placeholder = { Text("যেমন: অভ্যন্তরীণ বা যোগ্য") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = resultIfFalse,
                    onValueChange = { resultIfFalse = it },
                    label = { Text("শর্ত মিথ্যা হলে আউটপুট (IF False) *") },
                    placeholder = { Text("যেমন: বহিরাগত বা অযোগ্য") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Formula Test Sandbox
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🧪 লাইভ সূত্র টেস্ট (Live Preview)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        OutlinedTextField(
                            value = testInputValue,
                            onValueChange = { testInputValue = it },
                            label = { Text("পরীক্ষামূলক মান ইনপুট দিন") },
                            placeholder = { Text("যেমন: পশ্চিম রামপুর") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (testInputValue.isNotBlank()) {
                            Text(
                                text = "ফলাফল: $testOutput",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ruleName.isBlank()) return@Button
                    val rule = FormulaRuleEntity(
                        id = initialRule?.id ?: "rule_${System.currentTimeMillis()}",
                        ruleName = ruleName.trim(),
                        targetFieldName = targetField.trim(),
                        sourceField = sourceField.trim(),
                        operator = operator,
                        conditionValue = conditionValue.trim(),
                        resultIfTrue = resultIfTrue.trim(),
                        resultIfFalse = resultIfFalse.trim()
                    )
                    onSave(rule)
                },
                modifier = Modifier.testTag("btn_save_formula_rule")
            ) {
                Text(if (initialRule == null) "সংরক্ষণ করুন" else "হালনাগাদ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun FormulaRuleAddDialog(
    availableCustomFields: List<CustomFieldEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (FormulaRuleEntity) -> Unit
) {
    FormulaRuleAddEditDialog(
        initialRule = null,
        availableCustomFields = availableCustomFields,
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentBulkEditFieldDialog(
    selectedStudents: List<StudentEntity>,
    customFields: List<CustomFieldEntity> = emptyList(),
    formulaRules: List<FormulaRuleEntity> = emptyList(),
    onDismiss: () -> Unit,
    onApplyBulkUpdate: (fieldKey: String, newValue: String, isCustomField: Boolean) -> Unit,
    onRecalculateFormulas: () -> Unit
) {
    // List of standard fields available for bulk updating
    val standardFields = listOf(
        "studentClass" to "শ্রেণি (Class)",
        "section" to "শাখা / বিভাগ (Section)",
        "academicYear" to "শিক্ষাবর্ষ (Academic Year)",
        "status" to "স্ট্যাটাস (Status)",
        "village" to "গ্রাম (Village)",
        "gender" to "লিঙ্গ (Gender)",
        "isSpecialNeeds" to "বিশেষ চাহিদা (Special Needs)",
        "address" to "ঠিকানা (Address)"
    )

    var selectedFieldKey by remember { mutableStateOf("studentClass") }
    var isCustomFieldSelected by remember { mutableStateOf(false) }
    var newValue by remember { mutableStateOf("১ম শ্রেণি") }

    val classOptions = listOf("প্রাক-প্রাথমিক ৪+", "প্রাক-প্রাথমিক ৫+", "১ম শ্রেণি", "২য় শ্রেণি", "৩য় শ্রেণি", "৪র্থ শ্রেণি", "৫ম শ্রেণি")
    val sectionOptions = listOf("ক", "খ", "গ", "ঘ", "A", "B", "মেঘনা", "পদ্মা", "যমুনা")
    val statusOptions = listOf("Current" to "বর্তমান (Current)", "Former" to "সাবেক (Former)", "Transferred" to "বদলীকৃত (Transferred)", "Inactive" to "নিষ্ক্রিয় (Inactive)")
    val genderOptions = listOf("ছাত্র", "ছাত্রী")
    val yesNoOptions = listOf("হ্যাঁ", "না")
    val yearOptions = listOf("২০২৬", "২০২৫", "২০২৭", "২০২৪")

    // Suggestions from existing students
    val villageSuggestions = remember(selectedStudents) {
        selectedStudents.map { it.village }.filter { it.isNotBlank() }.distinct()
    }

    val selectedCustomField = remember(selectedFieldKey, customFields) {
        customFields.find { it.id == selectedFieldKey || it.name == selectedFieldKey }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("মাল্টি-সিলেক্ট বাল্ক মান পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${BanglaUtils.toBanglaDigits(selectedStudents.size)} জন শিক্ষার্থীর তথ্য একসাথে আপডেট করুন",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Step 1: Select Field
                Text(
                    "১. কোন ফিল্ডের মান পরিবর্তন করতে চান?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("মৌলিক ফিল্ডসমূহ:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(standardFields) { (fKey, fLabel) ->
                                FilterChip(
                                    selected = !isCustomFieldSelected && selectedFieldKey == fKey,
                                    onClick = {
                                        selectedFieldKey = fKey
                                        isCustomFieldSelected = false
                                        newValue = when (fKey) {
                                            "studentClass" -> classOptions.first()
                                            "section" -> sectionOptions.first()
                                            "status" -> "Current"
                                            "gender" -> "ছাত্র"
                                            "isSpecialNeeds" -> "না"
                                            "academicYear" -> "২০২৬"
                                            else -> ""
                                        }
                                    },
                                    label = { Text(fLabel, fontSize = 11.sp) }
                                )
                            }
                        }

                        if (customFields.isNotEmpty()) {
                            Text("কাস্টম ফিল্ডসমূহ:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(customFields) { cf ->
                                    FilterChip(
                                        selected = isCustomFieldSelected && selectedFieldKey == cf.id,
                                        onClick = {
                                            selectedFieldKey = cf.id
                                            isCustomFieldSelected = true
                                            newValue = if (cf.optionsList.isNotEmpty()) cf.optionsList.first() else ""
                                        },
                                        label = { Text("${cf.name} (${cf.fieldType})", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 2: New Value Input
                Text(
                    "২. নতুন মান প্রদান বা নির্বাচন করুন:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (!isCustomFieldSelected) {
                    when (selectedFieldKey) {
                        "studentClass" -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(classOptions) { cls ->
                                    FilterChip(
                                        selected = newValue == cls,
                                        onClick = { newValue = cls },
                                        label = { Text(cls, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                        "section" -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(sectionOptions) { sec ->
                                    FilterChip(
                                        selected = newValue == sec,
                                        onClick = { newValue = sec },
                                        label = { Text(sec, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                        "status" -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(statusOptions) { (sKey, sLabel) ->
                                    FilterChip(
                                        selected = newValue == sKey,
                                        onClick = { newValue = sKey },
                                        label = { Text(sLabel, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                        "gender" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                genderOptions.forEach { g ->
                                    FilterChip(
                                        selected = newValue == g,
                                        onClick = { newValue = g },
                                        label = { Text(g, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        "isSpecialNeeds" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                yesNoOptions.forEach { yn ->
                                    FilterChip(
                                        selected = newValue == yn,
                                        onClick = { newValue = yn },
                                        label = { Text(yn, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        "academicYear" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                yearOptions.forEach { yr ->
                                    FilterChip(
                                        selected = newValue == yr,
                                        onClick = { newValue = yr },
                                        label = { Text(yr, fontSize = 12.sp) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = newValue,
                                onValueChange = { newValue = it },
                                label = { Text("শিক্ষাবর্ষ") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "village" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = newValue,
                                    onValueChange = { newValue = it },
                                    label = { Text("গ্রামের নাম") },
                                    placeholder = { Text("যেমন: পশ্চিম রামপুর") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (villageSuggestions.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(villageSuggestions) { v ->
                                            FilterChip(
                                                selected = newValue == v,
                                                onClick = { newValue = v },
                                                label = { Text(v, fontSize = 10.5.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = newValue,
                                onValueChange = { newValue = it },
                                label = { Text("নতুন মান") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    val cf = selectedCustomField
                    if (cf != null) {
                        if (cf.isCalculated) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("⚡ এটি একটি ক্যালকুলেটেড ফিল্ড", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "আপনি চাইলে নির্বাচিত শিক্ষার্থীদের জন্য সূত্র অনুযায়ী এখনই পুনর্গণনা করতে পারেন অথবা একটি নির্দিষ্ট মান দিয়ে ওভাররাইড করতে পারেন।",
                                        fontSize = 11.sp
                                    )
                                    Button(
                                        onClick = {
                                            onRecalculateFormulas()
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("সূত্র অনুযায়ী স্বয়ংক্রিয়ভাবে হিসাব করুন", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else if (cf.optionsList.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(cf.optionsList) { opt ->
                                    FilterChip(
                                        selected = newValue == opt,
                                        onClick = { newValue = opt },
                                        label = { Text(opt, fontSize = 11.sp) }
                                    )
                                }
                            }
                        } else if (cf.fieldType == "Yes/No") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("হ্যাঁ", "না").forEach { yn ->
                                    FilterChip(
                                        selected = newValue == yn,
                                        onClick = { newValue = yn },
                                        label = { Text(yn, fontSize = 12.sp) }
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = newValue,
                                onValueChange = { newValue = it },
                                label = { Text("${cf.name} এর নতুন মান") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = newValue,
                            onValueChange = { newValue = it },
                            label = { Text("নতুন মান") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Step 3: Quick Preview of Selected Students
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📋 পরিবর্তনের নমুনা পূর্বরূপ:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        selectedStudents.take(3).forEach { st ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${st.name} (রোল: ${BanglaUtils.toBanglaDigits(st.rollNumber)})", fontSize = 11.5.sp)
                                Text("→ \"$newValue\"", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.5.sp)
                            }
                        }
                        if (selectedStudents.size > 3) {
                            Text("... এবং আরও ${BanglaUtils.toBanglaDigits(selectedStudents.size - 3)} জন শিক্ষার্থী", fontSize = 10.5.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplyBulkUpdate(selectedFieldKey, newValue, isCustomFieldSelected)
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_confirm_bulk_edit")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("সকল ${BanglaUtils.toBanglaDigits(selectedStudents.size)} জনের মান পরিবর্তন করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

