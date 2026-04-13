package com.example.firebaselabelapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.firebaselabelapp.R

@Composable
fun PinEntryDialog(
    title: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onPinEntered: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String = ""
) {
    var currentPin by remember { mutableStateOf("") }

    if (isVisible) {
        Dialog(
            onDismissRequest = { /* Prevent dismissing by clicking outside */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with close button (only show for setup, not for unlock)
//                    if (title.contains("установ", ignoreCase = true)) {
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceEvenly,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Text(
//                                text = title,
//                                style = MaterialTheme.typography.headlineSmall,
//                                fontWeight = FontWeight.Bold
//                            )
//
//                            IconButton(onClick = onDismiss) {
//                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
//                            }
//
//                        }
//                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss,
                                modifier = Modifier.align(Alignment.Top)) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        }



                    Spacer(modifier = Modifier.height(32.dp))

                    // PIN display dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(6) { index ->
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = if (index < currentPin.length) {
                                            if (isError) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Error message
                    if (isError && errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Number pad
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Rows 1-3
                        repeat(3) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                repeat(3) { col ->
                                    val number = (row * 3 + col + 1).toString()
                                    NumberButton(
                                        text = number,
                                        onClick = {
                                            if (currentPin.length < 6) {
                                                currentPin += number
                                                if (currentPin.length == 6) {
                                                    onPinEntered(currentPin)
                                                    currentPin = ""
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Bottom row with 0 and backspace
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
//                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Empty space
                            Box(modifier = Modifier.size(64.dp))

                            // Zero button
                            NumberButton(
                                text = "0",
                                onClick = {
                                    if (currentPin.length < 6) {
                                        currentPin += "0"
                                        if (currentPin.length == 6) {
                                            onPinEntered(currentPin)
                                            currentPin = ""
                                        }
                                    }
                                }
                            )

                            // Backspace button
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (currentPin.isNotEmpty()) {
                                            currentPin = currentPin.dropLast(1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.backspace_24px),
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}