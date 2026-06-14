package com.owner.mindbody.ui.developer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.StorageTableCategory
import com.owner.mindbody.data.TableStorageStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeveloperStorageViewModel(application: Application) : AndroidViewModel(application) {

    private val storageStats = (application as MindBodyApplication).storage.storageStats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _stats = MutableStateFlow<List<TableStorageStat>>(emptyList())
    val stats: StateFlow<List<TableStorageStat>> = _stats.asStateFlow()

    private val _lastRefreshedAtMs = MutableStateFlow<Long?>(null)
    val lastRefreshedAtMs: StateFlow<Long?> = _lastRefreshedAtMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    storageStats.loadStats()
                }
            }.onSuccess { result ->
                _stats.value = result
                _lastRefreshedAtMs.value = System.currentTimeMillis()
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "读取失败"
            }
            _isLoading.value = false
        }
    }

    fun statsByCategory(category: StorageTableCategory): List<TableStorageStat> {
        return _stats.value.filter { it.category == category }
    }

    fun totalRowCount(): Long {
        return _stats.value.sumOf { it.rowCount }
    }

    fun consumeError() {
        _errorMessage.value = null
    }
}
