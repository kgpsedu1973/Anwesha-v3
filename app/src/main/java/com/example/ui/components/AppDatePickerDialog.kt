package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BanglaUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initialDateMillis: Long? = null,
    onDateSelected: (String) -> Unit, // returns YYYY-MM-DD
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        onDateSelected(sdf.format(cal.time))
                    }
                    onDismiss()
                }
            ) {
                Text("নিশ্চিত করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = "তারিখ নির্বাচন করুন (Select Date)",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        )
    }
}

@Composable
fun DateInputField(
    dateValue: String, // YYYY-MM-DD
    onDateChange: (String) -> Unit,
    label: String = "তারিখ",
    modifier: Modifier = Modifier,
    testTag: String = "date_input_field"
) {
    var showDialog by remember { mutableStateOf(false) }

    val formattedBangla = remember(dateValue) {
        if (dateValue.isNotBlank()) BanglaUtils.formatBanglaDate(dateValue) else ""
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = if (dateValue.isNotBlank()) "$dateValue  ($formattedBangla)" else "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = "Open Calendar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .clickable { showDialog = true }
        )

        // Invisible overlay to capture taps cleanly on the full input
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true }
        )
    }

    if (showDialog) {
        val initialMillis = remember(dateValue) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(dateValue)?.time
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        AppDatePickerDialog(
            initialDateMillis = initialMillis,
            onDateSelected = { selected ->
                onDateChange(selected)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}
