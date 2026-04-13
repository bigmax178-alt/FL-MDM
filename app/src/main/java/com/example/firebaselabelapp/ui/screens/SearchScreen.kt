package com.example.firebaselabelapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.repository.FirestoreRepository
import com.example.firebaselabelapp.ui.components.PrimaryButton
import com.example.firebaselabelapp.ui.theme.FirebaseLabelAppTheme
import com.example.firebaselabelapp.ui.viewmodel.SearchViewModel
import com.example.firebaselabelapp.ui.viewmodel.SearchViewModelFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: FirestoreRepository,
    onItemClick: (ItemButton) -> Unit,
    onBackClick: () -> Unit
) {
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(repository)
    )

    val searchResults by searchViewModel.searchResults.collectAsState()
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()

    FirebaseLabelAppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Поиск Продукта") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchViewModel.onSearchQueryChanged(it) },
                    label = { Text("Введите название продукта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Продукты не найдены")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { item ->
                                PrimaryButton(
                                    text = item.name,
                                    onClick = {
                                        FirebaseCrashlytics.getInstance().log("SearchScreen: Item clicked - ${item.name}")
                                        onItemClick(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
