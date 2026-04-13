package com.example.firebaselabelapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Custom color palette based on the screenshot
private val backgroundBeige = Color(0xFFFDF8F0)
private val cardHeaderBeige = Color(0xFFFAEFE1)
// UPDATED: Made the button color richer and less pale
private val productButtonPeach = Color(0xFFF9A873)
private val selectedCategoryBeige = Color(0xFFF2E6D8)
private val unselectedCategoryWhite = Color(0xFFFFFFFF)
private val textBrown = Color(0xFF4B3F36)

private val RefinedColorScheme = lightColorScheme(
    // Primary colors are used for the product buttons and TopAppBar
    primary = productButtonPeach,
    onPrimary = textBrown,

    // Primary container is used for the selected category
    primaryContainer = selectedCategoryBeige,
    onPrimaryContainer = textBrown,

    // Secondary container is used for the header cards ("Категории")
    secondaryContainer = cardHeaderBeige,
    onSecondaryContainer = textBrown,

    // Background color of the screen
    background = backgroundBeige,
    onBackground = textBrown,

    // Surface color is used for unselected category cards
    surface = unselectedCategoryWhite,
    onSurface = textBrown,

    // Default error colors
    error = Color(0xFFB00020),
    onError = Color.White,

    // Other colors filled in for a complete theme
    secondary = productButtonPeach,
    onSecondary = textBrown,
    tertiary = productButtonPeach,
    onTertiary = textBrown,
    tertiaryContainer = productButtonPeach,
    onTertiaryContainer = textBrown,
    surfaceVariant = cardHeaderBeige,
    onSurfaceVariant = textBrown,
    outline = Color(0xFFE0D5C8),
    outlineVariant = Color(0xFFF1E9DE),
    inverseSurface = textBrown,
    inverseOnSurface = backgroundBeige,
    inversePrimary = backgroundBeige,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = textBrown,
    scrim = Color(0x80000000)
)


@Composable
fun FirebaseLabelAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RefinedColorScheme,
        typography = Typography, // This variable is defined in Type.kt
        content = content
    )
}