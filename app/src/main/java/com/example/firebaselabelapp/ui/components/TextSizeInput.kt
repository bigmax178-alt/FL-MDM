// TextSizeInput.kt
package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState // Import this
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.firebaselabelapp.ui.viewmodel.SharedViewModel

@Composable
fun TextSizeInput(sharedViewModel: SharedViewModel) {
    // 1. CORRECT: Collect the state from the ViewModel to get the actual Float value.
    val textSizeValue by sharedViewModel.textSize.collectAsState()

    // 2. Use `remember(key)` to reset the local text state if the ViewModel's value changes.
    var textInput by remember(textSizeValue) { mutableStateOf(textSizeValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = textInput,
        onValueChange = { newValue ->
            // Update the local state as the user types
            textInput = newValue
            val floatValue = newValue.toFloatOrNull()

            // Validate the new value
            if (floatValue != null && floatValue in 20f..30f) {
                // If valid, update the ViewModel and clear the error
                sharedViewModel.updateTextSize(floatValue)
                isError = false
            } else {
                // If invalid (not a float, out of range, or empty), set an error
                isError = true
            }
        },
        label = { Text("Размер текста (от 20 до 30)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        supportingText = {
            if (isError) {
                Text("Введите число от 20 до 30")
            }
        }
    )
}