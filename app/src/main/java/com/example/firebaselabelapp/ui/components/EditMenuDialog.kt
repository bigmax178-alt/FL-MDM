package com.example.firebaselabelapp.ui.components

import android.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firebaselabelapp.model.MenuButton
import androidx.compose.material3.AlertDialog


@Composable
fun EditMenuDialog(
    menuButton: MenuButton, onEdit: (MenuButton) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(menuButton.name) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Редактировать категорию") }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)

        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название категории") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }, confirmButton = {
        TextButton(
            onClick = {
                if (name.isNotBlank()) onEdit(
                    menuButton.copy(name = name.trim())
                )

            }) {
            Text("Сохранить")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Отмена")
        }
    }


    )
}