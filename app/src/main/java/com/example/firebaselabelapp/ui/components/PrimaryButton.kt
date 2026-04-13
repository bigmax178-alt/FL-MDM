package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    // --- NEW: Configurable padding. Default is standard button padding.
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    // --- NEW: Configurable max lines. Default is 2.
    maxLines: Int = 2
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        ),
        // --- USE PADDING PARAMETER ---
        contentPadding = contentPadding
    ) {
        Text(
            text = text.uppercase(),
            textAlign = TextAlign.Center,
            // --- USE MAX LINES PARAMETER ---
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            // --- APPLY IMPROVED STYLE ---
            // Merge the incoming style with a new lineBreak strategy.
            // LineBreak.Paragraph is better at breaking long words that don't fit.
            style = textStyle.copy(
                lineBreak = LineBreak.Paragraph
            )
        )
    }
}
