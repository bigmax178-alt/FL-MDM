package com.example.firebaselabelapp.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.repository.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ItemButton>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var searchJob: Job? = null
    private var allItems: List<ItemButton> = emptyList()

    init {
        // Load all items once when the ViewModel is created
        loadAllItems()
    }

    private fun loadAllItems() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Items are already sorted alphabetically from the database
                allItems = repository.getAllItems()
                Log.d("SearchViewModel", "Loaded ${allItems.size} total items for search.")
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Error loading all items", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            if (query.isNotBlank()) {
                performSearch(query)
            } else {
                _searchResults.value = emptyList()
            }
        }
    }

    private fun performSearch(query: String) {
        // Filter items but don't sort again since they're already alphabetically ordered
        _searchResults.value = allItems.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }
}