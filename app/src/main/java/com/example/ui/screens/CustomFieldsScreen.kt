package com.example.ui.screens

import androidx.compose.animation.*
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
import com.example.ui.components.CustomFieldAddDialog
import com.example.ui.components.CustomFieldAddEditDialog
import com.example.ui.components.FormulaRuleAddDialog
import com.example.ui.components.FormulaRuleAddEditDialog
import com.example.util.BanglaUtils
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsScreen(
    viewModel: MainViewModel,
    onNavigateToStudents: () -> Unit = {}
) {
    val customFields by viewModel.customFields.collectAsState()
    val formulaRules by viewModel.formulaRules.collectAsState()
    val students by viewModel.allStudents.collectAsState()

    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<CustomFieldEntity?>(null) }
    var editingRule by remember { mutableStateOf<FormulaRuleEntity?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: কাস্টম ফিল্ড, 1: সূত্র ও শর্ত

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddFieldDialog = true
                    else showAddRuleDialog = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (selectedTab == 0) "নতুন ফিল্ড যোগ করুন" else "নতুন সূত্র যোগ করুন") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_custom_field_or_formula")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                text = "শিক্ষার্থী কাস্টম এন্ট্রি ফিল্ড ও সূত্র (Formula Engine)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "প্রয়োজনমতো নিজস্ব এন্ট্রি ফিল্ড ও স্বয়ংক্রিয় সূত্র সম্পাদনা ও তৈরি করুন",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "কাস্টম ফিল্ড (${BanglaUtils.toBanglaDigits(customFields.size)})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "সূত্র ও শর্ত (${BanglaUtils.toBanglaDigits(formulaRules.size)})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Filled.Calculate, contentDescription = null) }
                )
            }

            // Tab Content
            if (selectedTab == 0) {
                // Custom Fields List
                if (customFields.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.DynamicForm,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "কোনো কাস্টম ফিল্ড তৈরি করা নেই",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "শিক্ষার্থী ভর্তির ফর্মে অতিরিক্ত ফিল্ড (যেমন: রক্তের গ্রুপ, উপবৃত্তি, অভিভাবকের পেশা ইত্যাদি) যুক্ত করতে নিচের বাটনে ট্যাপ করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Button(
                                onClick = { showAddFieldDialog = true },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("কাস্টম ফিল্ড তৈরি করুন")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "বিদ্যমান কাস্টম ফিল্ডসমূহ (সম্পাদনা করতে কলম আইকনে চাপুন):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(customFields, key = { it.id }) { field ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (field.fieldType) {
                                                    "Number" -> Icons.Filled.Pin
                                                    "Date" -> Icons.Filled.CalendarToday
                                                    "Phone" -> Icons.Filled.Phone
                                                    "Dropdown", "Multiple choice" -> Icons.Filled.ArrowDropDownCircle
                                                    "Yes/No" -> Icons.Filled.CheckCircle
                                                    "Calculated" -> Icons.Filled.Calculate
                                                    else -> Icons.Filled.TextFields
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = field.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "টাইপ: ${field.fieldType}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (field.isCalculated) {
                                                    Surface(
                                                        color = Color(0xFFE8F5E9),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "স্বয়ংক্রিয় সূত্র",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2E7D32),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (!field.optionsJson.isNullOrBlank()) {
                                                Text(
                                                    text = "অপশন: ${field.optionsJson}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (field.groupName.isNotBlank()) {
                                                Text(
                                                    text = "গ্রুপ: ${field.groupName}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { editingField = field }
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "Edit Custom Field",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteCustomField(field) }
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            } else {
                // Formula & Rules List
                if (formulaRules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Functions,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "কোনো স্বয়ংক্রিয় সূত্র বা শর্ত তৈরি করা নেই",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "যেমন: গ্রামের নাম অনুযায়ী শিক্ষার্থী 'অভ্যন্তরীণ' নাকি 'বহিরাগত', বা জন্মতারিখ থেকে 'বয়স' স্বয়ংক্রিয় গণনা করার নিয়ম তৈরি করতে পারেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Button(
                                onClick = { showAddRuleDialog = true },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("নতুন সূত্র যোগ করুন")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "সক্রিয় সূত্র ও লজিক রুলসমূহ (সম্পাদনা করতে কলম আইকনে চাপুন):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(formulaRules, key = { it.id }) { rule ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text(
                                                text = rule.ruleName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { editingRule = rule }) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit Rule", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { viewModel.deleteFormulaRule(rule) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "টার্গেট ফিল্ড: ${rule.targetFieldName}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "IF [${rule.sourceField}] ${rule.operator} '${rule.conditionValue}'",
                                                fontSize = 12.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                            Text(
                                                text = "THEN → \"${rule.resultIfTrue}\"",
                                                fontSize = 12.sp,
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "ELSE → \"${rule.resultIfFalse}\"",
                                                fontSize = 12.sp,
                                                color = Color(0xFFC2185B),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddFieldDialog) {
        CustomFieldAddDialog(
            availableCustomFields = customFields,
            formulaRules = formulaRules,
            onDismiss = { showAddFieldDialog = false },
            onSave = { field ->
                viewModel.insertCustomField(field)
                showAddFieldDialog = false
            },
            onSaveWithCalculation = { field, rule, calcAll ->
                viewModel.insertCustomFieldWithCalculation(field, rule, calcAll)
                showAddFieldDialog = false
            }
        )
    }

    if (editingField != null) {
        CustomFieldAddEditDialog(
            initialField = editingField,
            availableCustomFields = customFields,
            formulaRules = formulaRules,
            onDismiss = { editingField = null },
            onSave = { field ->
                viewModel.insertCustomField(field)
                editingField = null
            },
            onSaveWithCalculation = { field, rule, calcAll ->
                viewModel.insertCustomFieldWithCalculation(field, rule, calcAll)
                editingField = null
            }
        )
    }

    if (showAddRuleDialog) {
        FormulaRuleAddDialog(
            availableCustomFields = customFields,
            onDismiss = { showAddRuleDialog = false },
            onSave = { rule ->
                viewModel.insertFormulaRule(rule)
                showAddRuleDialog = false
            }
        )
    }

    if (editingRule != null) {
        FormulaRuleAddEditDialog(
            initialRule = editingRule,
            availableCustomFields = customFields,
            onDismiss = { editingRule = null },
            onSave = { rule ->
                viewModel.insertFormulaRule(rule)
                editingRule = null
            }
        )
    }
}
