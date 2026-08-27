package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    showQuickChips: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val cleanSuggestions = remember(suggestions, value) {
        suggestions.filter { it.isNotBlank() }.distinct().filter {
            if (value.isBlank()) true else it.contains(value, ignoreCase = true)
        }
    }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = true
                },
                label = { Text(label) },
                placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            if (cleanSuggestions.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    cleanSuggestions.take(8).forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(suggestion, fontSize = 14.sp)
                                    if (value.trim() == suggestion.trim()) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onValueChange(suggestion)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }

        // Quick Suggestion Chips below field for instant mobile touch
        if (showQuickChips && suggestions.isNotEmpty()) {
            val quickList = suggestions.filter { it.isNotBlank() }.distinct().take(5)
            if (quickList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(quickList) { chipText ->
                        val isSelected = value.trim() == chipText.trim()
                        SuggestionChip(
                            onClick = { onValueChange(chipText) },
                            label = { Text(chipText, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
