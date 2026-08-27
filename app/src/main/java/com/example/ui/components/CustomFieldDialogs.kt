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
fun CustomFieldAddDialog(
    onDismiss: () -> Unit,
    onSave: (CustomFieldEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var fieldType by remember { mutableStateOf("Text") }
    var optionsJson by remember { mutableStateOf("") }

    val types = listOf("Text", "Number", "Date", "Phone", "Dropdown", "Yes/No", "Multiple choice", "Long text", "Calculated")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন কাস্টম ফিল্ড তৈরি", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ফিল্ডের নাম (Label)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_custom_field_name")
                )
                Text("ফিল্ডের ধরন নির্বাচন করুন:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                    items(types) { t ->
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
                        id = "cf_${System.currentTimeMillis()}",
                        name = name,
                        fieldType = fieldType,
                        optionsJson = if (optionsJson.isBlank()) null else optionsJson,
                        isCalculated = fieldType == "Calculated"
                    )
                    onSave(cf)
                }
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun FormulaRuleAddDialog(
    onDismiss: () -> Unit,
    onSave: (FormulaRuleEntity) -> Unit
) {
    var ruleName by remember { mutableStateOf("") }
    var targetField by remember { mutableStateOf("শিক্ষার্থীর ধরণ") }
    var sourceField by remember { mutableStateOf("village") }
    var operator by remember { mutableStateOf("IN_LIST") }
    var conditionValue by remember { mutableStateOf("পশ্চিম রামপুর,আমতলী") }
    var resultIfTrue by remember { mutableStateOf("অভ্যন্তরীণ") }
    var resultIfFalse by remember { mutableStateOf("বহিরাগত") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন সূত্র / নিয়ম যুক্ত করুন", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = ruleName, onValueChange = { ruleName = it }, label = { Text("নিয়মের নাম") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = targetField, onValueChange = { targetField = it }, label = { Text("টার্গেট ফিল্ডের নাম") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sourceField, onValueChange = { sourceField = it }, label = { Text("উৎস ফিল্ড (Source Field: e.g. village)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = operator, onValueChange = { operator = it }, label = { Text("অপারেটর (IN_LIST, EQUALS, CONTAINS)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = conditionValue, onValueChange = { conditionValue = it }, label = { Text("শর্তের মান (Condition Value)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = resultIfTrue, onValueChange = { resultIfTrue = it }, label = { Text("সত্য হলে আউটপুট (IF True)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = resultIfFalse, onValueChange = { resultIfFalse = it }, label = { Text("মিথ্যা হলে আউটপুট (IF False)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ruleName.isBlank()) return@Button
                    val rule = FormulaRuleEntity(
                        id = "rule_${System.currentTimeMillis()}",
                        ruleName = ruleName,
                        targetFieldName = targetField,
                        sourceField = sourceField,
                        operator = operator,
                        conditionValue = conditionValue,
                        resultIfTrue = resultIfTrue,
                        resultIfFalse = resultIfFalse
                    )
                    onSave(rule)
                }
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}
