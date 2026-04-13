package com.example.firebaselabelapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.firebaselabelapp.subscription.SubscriptionManager
import kotlinx.coroutines.delay

/**
 * Small yellow banner at the top showing warning when subscription is unpaid
 * Shows when time until deactivation is between 12-36 hours
 */
@Composable
fun YellowWarningBanner(
    timeUntilDeactivation: Long,
    modifier: Modifier = Modifier
) {
    val hoursRemaining = (timeUntilDeactivation / (1000 * 60 * 60)).toInt()
    val minutesRemaining = ((timeUntilDeactivation / (1000 * 60)) % 60).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Требуется оплата подписки! Деактивация через ${hoursRemaining}ч ${minutesRemaining}м",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Full-screen red banner overlay that appears periodically
 * Shows when time until deactivation is less than 12 hours
 * Appears every 10 seconds and can be dismissed with X button
 */
@Composable
fun RedCriticalBanner(
    timeUntilDeactivation: Long,
    onDismiss: () -> Unit
) {
    val hoursRemaining = (timeUntilDeactivation / (1000 * 60 * 60)).toInt()
    val minutesRemaining = ((timeUntilDeactivation / (1000 * 60)) % 60).toInt()
    val secondsRemaining = ((timeUntilDeactivation / 1000) % 60).toInt()


    Dialog(
        onDismissRequest = { }, // Prevent dismissing by clicking outside
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Red.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Critical Warning",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "КРИТИЧЕСКОЕ ПРЕДУПРЕЖДЕНИЕ",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Подписка не оплачена!",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Приложение деактивируется через:",
                    color = Color.White,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Show seconds in the countdown for more precise testing feedback
                val countdownText = if (hoursRemaining > 0) {
                    "${hoursRemaining} часов ${minutesRemaining} минут"
                } else {
                    "${minutesRemaining} минут ${secondsRemaining} секунд"
                }

                Text(
                    text = countdownText,
                    color = Color.Yellow,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Пожалуйста, внесите оплату немедленно, чтобы продолжить использование приложения.",
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Close button in top-right corner
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Manager composable that handles showing banners based on subscription status
 * Yellow banner: Shows for the initial grace period.
 * Red banner: Shows for the final critical duration, popping up every 10 seconds.
 */
@Composable
fun PaymentBannerManager(
    redBanner: Boolean,
    timeUntilDeactivation: Long,
    content: @Composable (showYellowBanner: Boolean) -> Unit
) {
    var showRedBanner by remember { mutableStateOf(false) }
    var lastRedBannerTime by remember { mutableStateOf(0L) }

    // Use the constant from SubscriptionManager to decide which banner to show.
    val RED_BANNER_THRESHOLD = SubscriptionManager.RED_BANNER_DURATION

    // Yellow banner: Active when time remaining is MORE than the red banner's duration.
    val showYellowBanner = redBanner && timeUntilDeactivation > RED_BANNER_THRESHOLD

    // Red banner: Active when time remaining is LESS than or equal to the red banner's duration.
    val shouldShowRedBanner = redBanner && timeUntilDeactivation <= RED_BANNER_THRESHOLD && timeUntilDeactivation > 0

    // Show red banner every 10 seconds if in the critical period.
    LaunchedEffect(shouldShowRedBanner, timeUntilDeactivation) {
        if (shouldShowRedBanner) {
            while (true) {
                val currentTime = System.currentTimeMillis()
                // Show banner immediately on first load or every 10 seconds
                if (lastRedBannerTime == 0L || currentTime - lastRedBannerTime >= 10000) {
                    showRedBanner = true
                    lastRedBannerTime = currentTime
                }
                delay(1000) // Check every second
            }
        } else {
            showRedBanner = false
            lastRedBannerTime = 0L
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        content(showYellowBanner)

        // Red critical banner overlay
        if (showRedBanner) {
            RedCriticalBanner(
                timeUntilDeactivation = timeUntilDeactivation,
                onDismiss = { showRedBanner = false }
            )
        }
    }
}
