package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.firebaselabelapp.ui.viewmodel.SharedViewModel

@Composable
fun AddItemDialog(
    sharedViewModel: SharedViewModel,
    onAdd: (String, Int?, Int?, Int?, Int?, String?) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var years by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // --- Collect close time from ViewModel ---
    val closeHour by sharedViewModel.selectedCloseHour.collectAsState()
    val closeMinute by sharedViewModel.selectedCloseMinute.collectAsState()

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Добавить продукт") }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название продукта") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Срок годности:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = years,
                    onValueChange = { years = it },
                    label = { Text("Годы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it },
                    label = { Text("Месяцы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Дни") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Часы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            if (name.contains("Распр", ignoreCase = true) || name.contains("Биз", ignoreCase = true)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Время Закрытия(в Дополнительных настройках):",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = closeHour.toString(),
                        onValueChange = { },
                        label = { Text("Часы (24ч)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                    OutlinedTextField(
                        value = closeMinute.toString(),
                        onValueChange = { },
                        label = { Text("Минуты") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }
            }
        }
    }, confirmButton = {
        TextButton(
            onClick = {
                if (name.isNotBlank()) {
                    onAdd(
                        name.trim(),
                        years.toIntOrNull(),
                        months.toIntOrNull(),
                        days.toIntOrNull(),
                        hours.toIntOrNull(),
                        description.takeIf { it.isNotBlank() }?.trim(),
                    )
                }
            }) {
            Text("Добавить")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Отмена")
        }
    })
}