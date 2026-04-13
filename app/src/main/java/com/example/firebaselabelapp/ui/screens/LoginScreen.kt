package com.example.firebaselabelapp.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.ui.components.PrimaryButton
import com.example.firebaselabelapp.ui.theme.FirebaseLabelAppTheme
import com.google.firebase.crashlytics.FirebaseCrashlytics

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    activity: Activity
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var subscriptionMessage by remember { mutableStateOf("") }

//    val activity = LocalContext.current as Activity
    val focusManager = LocalFocusManager.current


    FirebaseLabelAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sign In",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    error = null // Clear error when user starts typing
                },
                label = { Text("Email", color = MaterialTheme.colorScheme.onBackground) },
                modifier = Modifier.widthIn(max = 400.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null // Clear error when user starts typing
                },
                label = { Text("Password", color = MaterialTheme.colorScheme.onBackground) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.widthIn(max = 400.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                enabled = !isLoading,
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (!isLoading) {
                        performLogin(activity, email, password) { result ->
                            handleAuthResult(
                                result = result,
                                onSuccess = onLoginSuccess,
                                onError = { error = it },
                                onSubscriptionIssue = { message ->
                                    subscriptionMessage = message
                                    showSubscriptionDialog = true
                                },
                                onLoadingChange = { isLoading = it }
                            )
                        }
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                )
            )

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = if (isLoading) "Проверка..." else "Войти",
                onClick = {
                    focusManager.clearFocus()
                    if (!isLoading) {
                        performLogin(activity, email, password) { result ->
                            handleAuthResult(
                                result = result,
                                onSuccess = onLoginSuccess,
                                onError = { error = it },
                                onSubscriptionIssue = { message ->
                                    subscriptionMessage = message
                                    showSubscriptionDialog = true
                                },
                                onLoadingChange = { isLoading = it }
                            )
                        }
                    }
                },
                modifier = Modifier.widthIn(max = 400.dp),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
            )

            // Loading indicator
            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Error message
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Subscription Dialog
    if (showSubscriptionDialog) {
        AlertDialog(
            onDismissRequest = { showSubscriptionDialog = false },
            title = {
                Text(
                    "Требуется подписка",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    subscriptionMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubscriptionDialog = false
                        // TODO: Navigate to subscription purchase screen
                        // or open external payment link
                    }
                ) {
                    Text("Ок")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSubscriptionDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun performLogin(
    activity: android.app.Activity,
    email: String,
    password: String,
    onResult: (AuthManager.AuthResult) -> Unit
) {
    FirebaseCrashlytics.getInstance().log("LoginScreen: performLogin called")
    AuthManager.loginWithSubscriptionCheck(activity, email, password, onResult)
}

private fun handleAuthResult(
    result: AuthManager.AuthResult,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onSubscriptionIssue: (String) -> Unit,
    onLoadingChange: (Boolean) -> Unit
) {
    onLoadingChange(false)

    when {
        result.success -> {
            FirebaseCrashlytics.getInstance().log("LoginScreen: handleAuthResult - Success")
            result.subscriptionStatus?.let { status ->
                if (status.daysUntilExpiry <= 7 && status.daysUntilExpiry > 0) {
                    // Show warning but proceed
                    onError("Внимание: подписка истекает через ${status.daysUntilExpiry} дн.")
                }
            }
            onSuccess()
        }

        result.needsSubscription -> {
            val message = result.subscriptionStatus?.let { status ->
                when {
                    !status.isDeviceBound -> "Аккаунт привязан к другому устройству. Обратитесь в службу поддержки."
                    status.daysUntilExpiry <= 0 -> "Ваша подписка истекла. Продлите подписку для продолжения работы."
                    else -> result.message ?: "Требуется активная подписка"
                }
            } ?: (result.message ?: "Требуется активная подписка")
            FirebaseCrashlytics.getInstance().log("LoginScreen: handleAuthResult - NeedsSubscription: $message")
            onSubscriptionIssue(message)
        }

        else -> {
            FirebaseCrashlytics.getInstance().log("LoginScreen: handleAuthResult - Failure: ${result.message}")
            onError(result.message ?: "Ошибка входа")
        }
    }
}