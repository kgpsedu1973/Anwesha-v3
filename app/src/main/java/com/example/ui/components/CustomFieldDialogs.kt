package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity

@Composable
fun CustomFieldAddEditDialog(
    initialField: CustomFieldEntity? = null,
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit
) {
    var name by remember(initialField) { mutableStateOf(initialField?.name ?: "") }
    var fieldType by remember(initialField) { mutableStateOf(initialField?.fieldType ?: "Text") }
    var optionsJson by remember(initialField) { mutableStateOf(initialField?.optionsJson ?: "") }
    var groupName by remember(initialField) { mutableStateOf(initialField?.groupName ?: "কাস্টম তথ্য") }

    val types = listOf("Text", "Number", "Date", "Phone", "Dropdown", "Yes/No", "Multiple choice", "Long text", "Calculated")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialField == null) "নতুন কাস্টম ফিল্ড তৈরি" else "কাস্টম ফিল্ড সম্পাদনা / এডিট",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ফিল্ডের নাম (Label)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_custom_field_name")
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("গ্রুপের নাম (Group)") },
                    placeholder = { Text("যেমন: কাস্টম তথ্য / স্বাস্থ্য তথ্য") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("ফিল্ডের ধরন নির্বাচন করুন:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Column(modifier = Modifier.fillMaxWidth()) {
                    types.forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = fieldType == t, onClick = { fieldType = t })
                            Text(t, fontSize = 13.sp)
                        }
                    }
                }

                if (fieldType == "Dropdown" || fieldType == "Multiple choice") {
                    OutlinedTextField(
                        value = optionsJson,
                        onValueChange = { optionsJson = it },
                        label = { Text("অপশনসমূহ (কমা দিয়ে লিখুন)") },
                        placeholder = { Text("যেমন: A+, B+, AB+, O+") },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        formulaRuleId = initialField?.formulaRuleId,
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
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit
) {
    CustomFieldAddEditDialog(
        initialField = null,
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

    val operators = listOf("IN_LIST", "EQUALS", "NOT_EQUALS", "CONTAINS", "GREATER_THAN", "LESS_THAN")

    // Target Field Suggestions
    val defaultTargetSuggestions = listOf(
        "শিক্ষার্থীর ধরণ",
        "উপবৃত্তি যোগ্যতা",
        "ফি ছাড় / স্কলারশিপ",
        "হাউস / দল",
        "স্বাস্থ্য স্ট্যাটাস",
        "রক্তের গ্রুপ",
        "বিশেষ সুযোগ"
    )
    val customTargetSuggestions = availableCustomFields.map { it.name }
    val allTargetSuggestions = (defaultTargetSuggestions + customTargetSuggestions).distinct()

    // Source Field Suggestions (key to display name)
    val sourceFieldSuggestions = listOf(
        "village" to "গ্রাম (Village)",
        "studentClass" to "শ্রেণি (Class)",
        "gender" to "লিঙ্গ (Gender)",
        "rollNumber" to "রোল নং (Roll)",
        "academicYear" to "শিক্ষাবর্ষ (Year)",
        "isSpecialNeeds" to "বিশেষ চাহিদা (Special Needs)",
        "birthDate" to "জন্মতারিখ (DOB)",
        "mobile" to "মোবাইল (Mobile)",
        "address" to "ঠিকানা (Address)",
        "fatherName" to "পিতার নাম (Father)",
        "motherName" to "মাতার নাম (Mother)"
    ) + availableCustomFields.filter { !it.isCalculated }.map { it.id to "${it.name} (Custom)" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialRule == null) "নতুন সূত্র / ফাংশন যুক্ত করুন" else "সূত্র / ফাংশন সম্পাদনা (Edit Formula)",
                fontWeight = FontWeight.Bold
            )
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
                    label = { Text("ফাংশন / নিয়মের নাম (Rule Name)") },
                    placeholder = { Text("যেমন: গ্রাম ভিত্তিক অভ্যন্তরীণ শিক্ষার্থী নির্ণয়") },
                    modifier = Modifier.fillMaxWidth().testTag("input_formula_rule_name")
                )

                // Target Field with Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = targetField,
                        onValueChange = { targetField = it },
                        label = { Text("টার্গেট ফিল্ডের নাম (Target Field)") },
                        placeholder = { Text("যেমন: শিক্ষার্থীর ধরণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("টার্গেট ফিল্ড সাজেশন (ক্লিক করে নির্বাচন করুন):", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allTargetSuggestions.size) { idx ->
                            val s = allTargetSuggestions[idx]
                            FilterChip(
                                selected = targetField == s,
                                onClick = { targetField = s },
                                label = { Text(s, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                // Source Field with Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = sourceField,
                        onValueChange = { sourceField = it },
                        label = { Text("উৎস ফিল্ড (Source Field: e.g. village)") },
                        placeholder = { Text("যেমন: village বা studentClass") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("উৎস ফিল্ড সাজেশন (ক্লিক করে নির্বাচন করুন):", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(sourceFieldSuggestions.size) { idx ->
                            val (key, label) = sourceFieldSuggestions[idx]
                            FilterChip(
                                selected = sourceField == key,
                                onClick = { sourceField = key },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                Text("অপারেটর / শর্তের ধরণ:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    operators.take(3).forEach { op ->
                        FilterChip(
                            selected = operator == op,
                            onClick = { operator = op },
                            label = { Text(op, fontSize = 10.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    operators.drop(3).forEach { op ->
                        FilterChip(
                            selected = operator == op,
                            onClick = { operator = op },
                            label = { Text(op, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = conditionValue,
                    onValueChange = { conditionValue = it },
                    label = { Text("শর্তের মান (Condition Value)") },
                    placeholder = { Text("যেমন: পশ্চিম রামপুর,আমতলী") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = resultIfTrue,
                    onValueChange = { resultIfTrue = it },
                    label = { Text("শর্ত সত্য হলে আউটপুট (IF True)") },
                    placeholder = { Text("যেমন: অভ্যন্তরীণ") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = resultIfFalse,
                    onValueChange = { resultIfFalse = it },
                    label = { Text("শর্ত মিথ্যা হলে আউটপুট (IF False)") },
                    placeholder = { Text("যেমন: বহিরাগত") },
                    modifier = Modifier.fillMaxWidth()
                )
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
