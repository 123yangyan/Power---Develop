package com.owner.mindbody.ui.mood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.local.MoodEntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

const val MOOD_HISTORY_PAGE_SIZE = 10
private const val MAX_PAGER_BUTTONS = 9

data class MoodHistoryListRow(
    val entry: MoodEntryEntity,
    val view: MoodHistoryRowView,
    val dailyIndex: DailyEntryIndexMeta?
)

class MoodHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val moodRepository = app.storage.mood
    private val zoneId = ZoneId.systemDefault()

    val entries = moodRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _editingEntry = MutableStateFlow<MoodEntryEntity?>(null)
    val editingEntry: StateFlow<MoodEntryEntity?> = _editingEntry.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun buildRows(list: List<MoodEntryEntity>): List<MoodHistoryListRow> {
        val indexMap = buildDailyIndexMap(list, zoneId)
        return list.map { entry ->
            MoodHistoryListRow(
                entry = entry,
                view = buildHistoryRowView(entry, zoneId),
                dailyIndex = indexMap[entry.id]
            )
        }
    }

    fun totalPages(totalItems: Int): Int {
        return maxOf(1, (totalItems + MOOD_HISTORY_PAGE_SIZE - 1) / MOOD_HISTORY_PAGE_SIZE)
    }

    fun pageRows(allRows: List<MoodHistoryListRow>, page: Int): List<MoodHistoryListRow> {
        val start = (page - 1) * MOOD_HISTORY_PAGE_SIZE
        return allRows.drop(start).take(MOOD_HISTORY_PAGE_SIZE)
    }

    fun pagerButtons(currentPage: Int, totalPages: Int): List<Int> {
        if (totalPages <= MAX_PAGER_BUTTONS) {
            return (1..totalPages).toList()
        }
        val half = MAX_PAGER_BUTTONS / 2
        var start = maxOf(1, currentPage - half)
        var end = start + MAX_PAGER_BUTTONS - 1
        if (end > totalPages) {
            end = totalPages
            start = maxOf(1, end - MAX_PAGER_BUTTONS + 1)
        }
        return (start..end).toList()
    }

    fun goToPage(page: Int, totalPages: Int) {
        _currentPage.value = page.coerceIn(1, totalPages)
    }

    fun toggleSelect(id: Long) {
        _selectedIds.value = _selectedIds.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    fun togglePageSelect(pageIds: List<Long>) {
        val current = _selectedIds.value
        val allSelected = pageIds.isNotEmpty() && pageIds.all { it in current }
        _selectedIds.value = if (allSelected) current - pageIds.toSet() else current + pageIds.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun startEdit(entry: MoodEntryEntity) {
        _editingEntry.value = entry
    }

    fun closeEdit() {
        _editingEntry.value = null
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            moodRepository.delete(id)
            _selectedIds.value = _selectedIds.value - id
            _toastMessage.value = "已删除"
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            moodRepository.deleteMany(ids)
            _selectedIds.value = emptySet()
            _toastMessage.value = "已删除 ${ids.size} 条"
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun onEntryUpdated() {
        _editingEntry.value = null
        _toastMessage.value = "已更新"
    }
}
