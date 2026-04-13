package com.example.firebaselabelapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Note: The app's theme currently uses the 'Typography' object from 'Type.kt'.
// This file is updated to match those definitions for consistency.
val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // Provide defaults for all other required styles
    displayLarge = TextStyle.Default,
    displayMedium = TextStyle.Default,
    displaySmall = TextStyle.Default,
    headlineLarge = TextStyle.Default,
    headlineMedium = TextStyle.Default,
    headlineSmall = TextStyle.Default,
    titleSmall = TextStyle.Default,
    labelLarge = TextStyle.Default,
    labelMedium = TextStyle.Default,
    labelSmall = TextStyle.Default
)