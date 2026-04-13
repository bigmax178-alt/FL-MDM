// TimeSettingsInput.kt
package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
fun OpeningTimeInput(sharedViewModel: SharedViewModel) {
    // 1. Collect all four states from the ViewModel
    val openingHour by sharedViewModel.selectedOpeningHour.collectAsState()
    val openingMinute by sharedViewModel.selectedOpeningMinute.collectAsState()
    val closeHour by sharedViewModel.selectedCloseHour.collectAsState()
    val closeMinute by sharedViewModel.selectedCloseMinute.collectAsState()

    // 2. Local states for Opening Hour input
    var openHourInput by remember(openingHour) { mutableStateOf(openingHour.toString()) }
    var isOpenHourError by remember { mutableStateOf(false) }

    // 3. Local states for Opening Minute input
    var openMinuteInput by remember(openingMinute) { mutableStateOf(openingMinute.toString()) }
    var isOpenMinuteError by remember { mutableStateOf(false) }

    // 4. Local states for Close Hour input
    var closeHourInput by remember(closeHour) { mutableStateOf(closeHour.toString()) }
    var isCloseHourError by remember { mutableStateOf(false) }

    // 5. Local states for Close Minute input
    var closeMinuteInput by remember(closeMinute) { mutableStateOf(closeMinute.toString()) }
    var isCloseMinuteError by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Opening Time Row ---
        Text("Время открытия", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = openHourInput,
                onValueChange = { newValue ->
                    openHourInput = newValue
                    val intValue = newValue.toIntOrNull()
                    if (intValue != null && intValue in 0..23) {
                        sharedViewModel.updateOpeningHour(intValue)
                        isOpenHourError = false
                    } else {
                        isOpenHourError = true
                    }
                },
                label = { Text("Час (0-23)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = isOpenHourError
            )
            OutlinedTextField(
                value = openMinuteInput,
                onValueChange = { newValue ->
                    openMinuteInput = newValue
                    val intValue = newValue.toIntOrNull()
                    if (intValue != null && intValue in 0..59) {
                        sharedViewModel.updateOpeningMinute(intValue)
                        isOpenMinuteError = false
                    } else {
                        isOpenMinuteError = true
                    }
                },
                label = { Text("Минута (0-59)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = isOpenMinuteError
            )
        }

        Spacer(Modifier.height(8.dp))

        // --- Closing Time Row ---
        Text("Время закрытия", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = closeHourInput,
                onValueChange = { newValue ->
                    closeHourInput = newValue
                    val intValue = newValue.toIntOrNull()
                    if (intValue != null && intValue in 0..23) {
                        sharedViewModel.updateCloseHour(intValue)
                        isCloseHourError = false
                    } else {
                        isCloseHourError = true
                    }
                },
                label = { Text("Час (0-23)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = isCloseHourError
            )
            OutlinedTextField(
                value = closeMinuteInput,
                onValueChange = { newValue ->
                    closeMinuteInput = newValue
                    val intValue = newValue.toIntOrNull()
                    if (intValue != null && intValue in 0..59) {
                        sharedViewModel.updateCloseMinute(intValue)
                        isCloseMinuteError = false
                    } else {
                        isCloseMinuteError = true
                    }
                },
                label = { Text("Минута (0-59)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = isCloseMinuteError
            )
        }
    }
}