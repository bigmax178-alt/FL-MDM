package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.firebaselabelapp.ui.viewmodel.MenuViewModel

@Composable
fun DatabaseResetButton(
    menuViewModel: MenuViewModel,
    isEnabled: Boolean
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showConfirmDialog = true },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = Modifier.fillMaxWidth(),
        enabled = isEnabled
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Reset Database",
            modifier = Modifier.padding(end = 8.dp)
        )
        Text("Сбросить базу данных (Исправить ошибки)")
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Сброс базы данных") },
            text = {
                Text(
                    "Эта функция удалит ВСЕ локальные данные и загрузит их заново из облака.\n\n" +
                            "Используйте это, если кнопки не загружаются или приложение работает некорректно после обновления."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        menuViewModel.hardResetDatabase()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}