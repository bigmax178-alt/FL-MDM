package com.example.firebaselabelapp.ui.screens

import android.app.DatePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.firebaselabelapp.R
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.ui.components.PrimaryButton
import com.example.firebaselabelapp.ui.components.SimpleTimePickerDialog
import com.example.firebaselabelapp.ui.theme.FirebaseLabelAppTheme
import com.example.firebaselabelapp.ui.viewmodel.PrintUiState
import com.example.firebaselabelapp.ui.viewmodel.SharedViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun PrintLabelScreen(
    item: ItemButton?,
    sharedViewModel: SharedViewModel,
    onBackClick: () -> Unit
) {

    var showTimeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val printState by sharedViewModel.printUiState.collectAsState()


    // Collect the StateFlow into a State that Compose can observe.
    val isManufactureDateLocked by sharedViewModel.isManufactureDateLocked.collectAsState()

    // --- Collect opening time from ViewModel ---
    val openingHour by sharedViewModel.selectedOpeningHour.collectAsState()
    val openingMinute by sharedViewModel.selectedOpeningMinute.collectAsState()

    // --- Collect close time from ViewModel ---
    val closeHour by sharedViewModel.selectedCloseHour.collectAsState()
    val closeMinute by sharedViewModel.selectedCloseMinute.collectAsState()


    // This effect will run whenever the printState changes
    LaunchedEffect(printState) {
        when (val state = printState) {
            is PrintUiState.Success -> {
                FirebaseCrashlytics.getInstance().log("PrintLabelScreen: PrintUiState.Success")
                Toast.makeText(context, "Print successful!", Toast.LENGTH_LONG).show()
                sharedViewModel.resetPrintState() // Reset state after showing
            }

            is PrintUiState.Error -> {
                FirebaseCrashlytics.getInstance()
                    .log("PrintLabelScreen: PrintUiState.Error - ${state.message}")

                // 1. Define a regex to find an IP address and port
                // This looks for four groups of numbers (IP) followed by a colon and more numbers (port)
                val ipPortRegex = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+)""")

                // 2. Try to find a match in the error message
                val matchResult = ipPortRegex.find(state.message)

                // 3. Get the generic reboot instruction string
                val rebootInstruction = context.getString(R.string.toast_please_reboot_printer)
                val noConnectionWithPrinterMsg = context.getString(R.string.toast_no_connection_with_printer)

                if (matchResult != null) {
                    // --- SUCCESS: We found an IP:Port ---
                    val ipAndPort = matchResult.value // This will be "192.168.1.254:9100"

                    // Create the combined message as requested in the
                    val combinedMessage = "$noConnectionWithPrinterMsg: $ipAndPort. $rebootInstruction"

                    // Show a single, long toast with the combined message
                    Toast.makeText(context, combinedMessage, Toast.LENGTH_LONG).show()

                } else {
                    // --- FAILED: No IP:Port found, use the old behavior ---
                    // This handles other errors (e.g., "USB permission denied", "Bluetooth not found")

                    // Show the original error message
                    Toast.makeText(context, "Printing Error: ${state.message}", Toast.LENGTH_SHORT)
                        .show()

                    // Show the generic reboot instruction
                    Toast.makeText(
                        context,
                        rebootInstruction,
                        Toast.LENGTH_LONG
                    ).show()
                }

                sharedViewModel.resetPrintState() // Reset state after showing
            }

            is PrintUiState.Printing -> {
                FirebaseCrashlytics.getInstance().log("PrintLabelScreen: PrintUiState.Printing")
                // Show a loading indicator if you want
            }

            is PrintUiState.Idle -> {
                // Do nothing
            }
        }
    }

    if (item == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Error: Item data not loaded. Cannot display label information.",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = onBackClick) { // Updated to navigate back correctly
                Text("Go Back")
            }
        }
        return
    }

    Log.d("PrintLabelScreen", "Item Name: ${item.name}")
    Log.d("PrintLabelScreen", "expDurationYears: ${item.expDurationYears}")
    Log.d("PrintLabelScreen", "expDurationMonths: ${item.expDurationMonths}")
    Log.d("PrintLabelScreen", "expDurationDays: ${item.expDurationDays}")
    Log.d("PrintLabelScreen", "expDurationHours: ${item.expDurationHours}")
//    Log.d("PrintLabelScreen", "expDurationMinutes: ${item.expDurationMinutes}")

    var copies by remember { mutableStateOf("1") }

    // Check if item name contains "Дата" to determine manual date requirement
    val requiresManualDate = item.name.contains("Дата", ignoreCase = true)

    // FIXED: Use the collected state variable here.
    val canChangeDate = !isManufactureDateLocked || requiresManualDate

    // Track if date has been manually selected (only relevant for "Дата" items)
    var isDateManuallySelected by remember { mutableStateOf(!requiresManualDate) }

    // State for user-modifiable manufacture date with initial time constraint logic
    var manufactureDate by remember(openingHour, openingMinute, closeHour, closeMinute) {
        mutableStateOf(
            run {
                val now = LocalDateTime.now()
                val currentHour = now.hour
                val currentMinute = now.minute

                if (item.name.contains("Распр", ignoreCase = true)) {
                    now.withHour(closeHour).withMinute(closeMinute)
                        .withSecond(0).withNano(0)
                } else if (requiresManualDate) {
                    // For "Дата" items, set to current time but mark as not manually selected
                    now
                } else {
                    when {
                        currentHour < openingHour || (currentHour == openingHour && currentMinute < openingMinute) -> {
                            // If it's before 8 AM, set to 8:00 AM today
                            now.withHour(openingHour).withMinute(openingMinute).withSecond(0)
                                .withNano(0)
                        }

                        currentHour >= closeHour || (currentHour == closeHour && currentMinute > closeMinute) -> {
                            // If it's after 10 PM, set to 8:00 AM next day
                            now.plusDays(1).withHour(openingHour).withMinute(openingMinute)
                                .withSecond(0).withNano(0)
                        }

                        else -> {
                            // If it's between 8 AM and 10 PM, use current time
                            now
                        }
                    }
                }
            }
        )
    }

    // States for user-modifiable expiry duration
    var expDurationYears by remember { mutableStateOf(item.expDurationYears?.toString() ?: "0") }
    var expDurationMonths by remember { mutableStateOf(item.expDurationMonths?.toString() ?: "0") }
    var expDurationDays by remember { mutableStateOf(item.expDurationDays?.toString() ?: "0") }
    var expDurationHours by remember { mutableStateOf(item.expDurationHours?.toString() ?: "0") }


    val years = expDurationYears.toIntOrNull() ?: 0
    val months = expDurationMonths.toIntOrNull() ?: 0
    val days = expDurationDays.toIntOrNull() ?: 0
    val hours = expDurationHours.toIntOrNull() ?: 0

    val expiryDate = if (item.name.contains("Биз", ignoreCase = true)) {
        LocalDateTime.now().withHour(closeHour).withMinute(closeMinute).withNano(0)
    } else if (item.name.contains("Распр", ignoreCase = true)) {
        manufactureDate
            .plusDays(1)
    } else {
        manufactureDate
            .plusYears(years.toLong())
            .plusMonths(months.toLong())
            .plusDays(days.toLong())
            .plusHours(hours.toLong())
    }

    val formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")

    var itemDescription by remember { mutableStateOf(item.description ?: "") }

    FirebaseLabelAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Header with Back Button and Centered Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                    Text(
                        text = "Товар: ${item.name}",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
//                    IconButton( onClick = {
//                        FirebaseCrashlytics.getInstance().log("My test breadcrumb for the crash")
//                        FirebaseCrashlytics.getInstance().recordException(Exception("Test Non-Fatal Event"))
//                        throw RuntimeException("Test Crash")
//                    },
//                        modifier = Modifier.align(Alignment.CenterEnd)) {
//                        Icon(
//                            Icons.Filled.BugReport,
//                            contentDescription = "Test Crash",
//                            tint = Color.Red
//                        )
//                    }
                }

                // Manufactured Date Display and Picker
                Text(text = "Дата Изготовления:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))

                // Show warning text for "Дата" items if date hasn't been manually selected
                if (requiresManualDate && !isDateManuallySelected) {
                    Text(
                        text = "⚠️ Необходимо выбрать дату вручную",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = manufactureDate.format(formatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (requiresManualDate && !isDateManuallySelected) Color.Red else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(enabled = canChangeDate) {
                        val calendar = Calendar.getInstance().apply {
                            set(
                                manufactureDate.year,
                                manufactureDate.monthValue - 1,
                                manufactureDate.dayOfMonth
                            )
                        }

                        DatePickerDialog(
                            context,
                            { _, year, monthOfYear, dayOfMonth ->
                                manufactureDate = manufactureDate
                                    .withYear(year)
                                    .withMonth(monthOfYear + 1)
                                    .withDayOfMonth(dayOfMonth)

                                showTimeDialog = true
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()

                    }
                )
                if (showTimeDialog && !requiresManualDate) {
                    SimpleTimePickerDialog(
                        initialHour = manufactureDate.hour,
                        initialMinute = manufactureDate.minute,
                        onDismiss = { showTimeDialog = false },
                        onTimeSelected = { h, m ->
                            manufactureDate = manufactureDate.withHour(h).withMinute(m)
                        }
                    )
                } else if (showTimeDialog) {
                    SimpleTimePickerDialog(
                        initialHour = 0,
                        initialMinute = 0,
                        onDismiss = { showTimeDialog = false },
                        onTimeSelected = { h, m ->
                            manufactureDate = manufactureDate.withHour(h).withMinute(m)
                        }
                    )
                    isDateManuallySelected = true
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Expiry Duration Input Fields
                Text(text = "Срок хранения:", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = expDurationYears,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                                expDurationYears = newValue
                            }
                        },
                        label = { Text("Годы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                    OutlinedTextField(
                        value = expDurationMonths,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                                expDurationMonths = newValue
                            }
                        },
                        label = { Text("Месяцы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                    OutlinedTextField(
                        value = expDurationDays,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                                expDurationDays = newValue
                            }
                        },
                        label = { Text("Дни") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                    OutlinedTextField(
                        value = expDurationHours,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                                expDurationHours = newValue
                            }
                        },
                        label = { Text("Часы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                }
                if (requiresManualDate) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Время от(ДАТА):",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = LocalDateTime.now().format(formatter),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Expiry Date Display
                Text(
                    text = "Рассчитанный срок годности до:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = expiryDate.format(formatter),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Copies Number Picker (only this remains)
                OutlinedTextField(
                    value = copies,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                            copies = newValue
                        }
                    },
                    label = { Text("Копий") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description text field
                OutlinedTextField(
                    value = itemDescription,
                    onValueChange = { newValue ->
                        itemDescription = newValue
                    },
                    label = { Text("Описание: ") },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(120.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Print Button
                PrimaryButton(
                    text = "Напечатать Этикетку",
                    enabled = printState !is PrintUiState.Printing && isDateManuallySelected,
                    onClick = {
                        val numCopies = copies.toIntOrNull() ?: 1
                        if (numCopies > 0) {
                            if (requiresManualDate && !isDateManuallySelected) {
                                Toast.makeText(
                                    context,
                                    "Необходимо выбрать дату изготовления вручную",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (requiresManualDate) {
                                sharedViewModel.executePrintJob(
                                    labelName = item.name,
                                    description = itemDescription,
                                    manufactureTime = LocalDateTime.now(),
                                    expiryDateTime = expiryDate,
                                    labelCount = numCopies
                                )
                            } else {
                                sharedViewModel.executePrintJob(
                                    labelName = item.name,
                                    description = itemDescription,
                                    manufactureTime = manufactureDate,
                                    expiryDateTime = expiryDate,
                                    labelCount = numCopies
                                )
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Number of copies must be at least 1",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                )

                // Show additional message for "Дата" items
                if (requiresManualDate && !isDateManuallySelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Нажмите на дату изготовления выше, чтобы выбрать её вручную",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Add bottom padding to ensure button is fully visible when scrolled
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}