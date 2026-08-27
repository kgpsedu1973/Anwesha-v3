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
import com.example.data.local.util.FormulaEvaluator

@Composable
fun CustomFieldAddEditDialog(
    initialField: CustomFieldEntity? = null,
    availableCustomFields: List<CustomFieldEntity> = emptyList(),
    formulaRules: List<FormulaRuleEntity> = emptyList(),
    existingGroups: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember(initialField) { mutableStateOf(initialField?.name ?: "") }
    var fieldType by remember(initialField) { mutableStateOf(initialField?.fieldType ?: "Text") }
    var optionsJson by remember(initialField) { mutableStateOf(initialField?.optionsJson ?: "") }
    var groupName by remember(initialField) { mutableStateOf(initialField?.groupName ?: "কাস্টম তথ্য") }
    var selectedFormulaRuleId by remember(initialField) { mutableStateOf(initialField?.formulaRuleId ?: "") }
    var isCreatingNewGroup by remember { mutableStateOf(false) }

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
                    placeholder = { Text("যেমন: রক্তের গ্রুপ / উপবৃত্তি যোগ্যতা / ডিসকাউন্ট") },
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
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ক্যালকুলেটেড ফিল্ড কনফিগারেশন", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = "এই ফিল্ডের মান স্বয়ংক্রিয়ভাবে সূত্র ও শর্ত (Formula Rules) অনুযায়ী হিসাব করা হবে।",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (formulaRules.isNotEmpty()) {
                                Text("সংযুক্ত সূত্র নির্বাচন করুন (ঐচ্ছিক):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val cf = CustomFieldEntity(
                        id = initialField?.id ?: "cf_${System.currentTimeMillis()}",
                        name = name.trim(),
                        fieldType = fieldType,
                        optionsJson = if (optionsJson.isBlank()) null else optionsJson.trim(),
                        isCalculated = fieldType == "Calculated",
                        formulaRuleId = if (fieldType == "Calculated" && selectedFormulaRuleId.isNotBlank()) selectedFormulaRuleId else null,
                        groupName = if (groupName.isBlank()) "কাস্টম তথ্য" else groupName.trim(),
                        orderIndex = initialField?.orderIndex ?: 0
                    )
                    onSave(cf)
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
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit
) {
    CustomFieldAddEditDialog(
        initialField = null,
        availableCustomFields = availableCustomFields,
        formulaRules = formulaRules,
        existingGroups = existingGroups,
        onDismiss = onDismiss,
        onSave = onSave
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
