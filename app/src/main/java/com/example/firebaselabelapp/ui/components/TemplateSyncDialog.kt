package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.firebaselabelapp.data.local.entities.ItemButtonEntity
import com.example.firebaselabelapp.data.local.entities.MenuButtonEntity
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.model.MenuButton
import com.example.firebaselabelapp.repository.ChangeType
import com.example.firebaselabelapp.repository.SuggestedChange
import com.example.firebaselabelapp.ui.viewmodel.MenuViewModel

// Helper extension property to get the display name for a change
private val SuggestedChange.displayName: String
    get() = when (type) {
        ChangeType.ADD -> (templateEntity as? MenuButton)?.name ?: (templateEntity as? ItemButton)?.name
        ChangeType.UPDATE, ChangeType.DELETE -> (userEntity as? MenuButtonEntity)?.name ?: (userEntity as? ItemButtonEntity)?.name
    } ?: "N/A"

// Helper extension property to get a stable unique key for a change
private val SuggestedChange.stableKey: String
    get() {
        // We assume the underlying entity models (MenuButton, ItemButton) and
        // entities (MenuButtonEntity, ItemButtonEntity) all have a unique 'id' property.
        val entityId = when (type) {
            ChangeType.ADD -> (templateEntity as? MenuButton)?.id ?: (templateEntity as? ItemButton)?.id
            ChangeType.UPDATE, ChangeType.DELETE -> (userEntity as? MenuButtonEntity)?.id ?: (userEntity as? ItemButtonEntity)?.id
        }
        // The key must be unique for the *change*, so we combine the type and the entity's ID.
        // We fallback to displayName if ID is somehow null, though ID is strongly preferred.
        return "${type}_${entityId?.toString() ?: displayName}"
    }


@Composable
fun TemplateSyncDialog(
    menuViewModel: MenuViewModel,
    onDismiss: () -> Unit
) {
    val syncPlan by menuViewModel.templateSyncPlan.collectAsState()
    val isChecking by menuViewModel.isCheckingSyncPlan.collectAsState()
    val isApplying by menuViewModel.isApplyingSyncPlan.collectAsState()

    var selectedChanges by remember { mutableStateOf<Set<SuggestedChange>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    // When the dialog is shown, load the plan
    LaunchedEffect(Unit) {
        menuViewModel.loadTemplateSyncPlan()
    }

    // When the sync plan updates, update the selection (and clear search)
    LaunchedEffect(syncPlan) {
        // --- MODIFIED: Uncheck DELETE items by default ---
        selectedChanges = syncPlan.filter { it.type != ChangeType.DELETE }.toSet()
        searchQuery = "" // Clear search when plan reloads
    }

    // --- Define a sort order for ChangeType ---
    val changeTypeOrder = mapOf(
        ChangeType.DELETE to 0,
        ChangeType.ADD to 1,
        ChangeType.UPDATE to 2
    )

    // --- Create a filtered and sorted list based on the syncPlan and search query ---
    val filteredAndSortedChanges by remember(syncPlan, searchQuery) {
        derivedStateOf {
            syncPlan
                .filter { change ->
                    change.displayName.contains(searchQuery, ignoreCase = true)
                }
                .sortedWith(
                    // Primary sort: Type
                    compareBy<SuggestedChange> { changeTypeOrder[it.type] }
                        // Secondary sort: Alphabetical by name
                        .thenBy { it.displayName }
                )
        }
    }

    Dialog(onDismissRequest = {
        menuViewModel.clearTemplateSyncPlan()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Проверка сроков",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isChecking) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                        Text("Проверка...", modifier = Modifier.padding(top = 60.dp))
                    }
                } else if (syncPlan.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Изменения не найдены. Все сроки актуальны.")
                    }
                } else {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Поиск по имени...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            // Select all *filtered* results
                            selectedChanges = selectedChanges + filteredAndSortedChanges.toSet()
                        }) {
                            Text("Выбрать все")
                        }
                        TextButton(onClick = {
                            // Deselect all *filtered* results
                            selectedChanges = selectedChanges - filteredAndSortedChanges.toSet()
                        }) {
                            Text("Снять все")
                        }
                    }

                    // List of changes
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredAndSortedChanges, key = { it.stableKey }) { change ->
                            TemplateChangeItem(
                                change = change,
                                isSelected = change in selectedChanges,
                                onToggle = { isChecked ->
                                    selectedChanges = if (isChecked) {
                                        selectedChanges + change
                                    } else {
                                        selectedChanges - change
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            menuViewModel.clearTemplateSyncPlan()
                            onDismiss()
                        },
                        enabled = !isApplying
                    ) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            menuViewModel.applyTemplateSyncChanges(selectedChanges.toList())
                        },
                        enabled = selectedChanges.isNotEmpty() && !isApplying && !isChecking
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Применить (${selectedChanges.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateChangeItem(
    change: SuggestedChange,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val (color, prefix) = when (change.type) {
        ChangeType.ADD -> Pair(Color.Green.copy(alpha = 0.2f), "[ + ]")
        ChangeType.UPDATE -> Pair(Color.Yellow.copy(alpha = 0.2f), "[ ~ ]")
        ChangeType.DELETE -> Pair(Color.Red.copy(alpha = 0.2f), "[ - ]")
    }

    val entityName = change.displayName
    val entityTypeString = if (change.entityType == "Menu") "Меню" else "Товар"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$prefix $entityTypeString: $entityName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    change.reason,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}