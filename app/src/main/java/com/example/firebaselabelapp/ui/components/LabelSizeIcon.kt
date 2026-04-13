package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LabelSizeIcon(width: Int, height: Int, isSelected: Boolean, onClick: () -> Unit, size: String) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val backgroundColor =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .height(65.dp) // Fixed height to prevent resizing
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(4.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = size, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Fixed height container for the status text
        Box(
            modifier = Modifier.height(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(
                    text = "Выбрано",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}