package com.timedrecorder.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.repository.ResultRepository
import com.timedrecorder.core.model.ProcessResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    resultRepository: ResultRepository,
) : ViewModel() {

    /** T5：搜索关键词，空字符串表示不过滤 */
    private val _query = MutableStateFlow("")

    val uiState: StateFlow<ResultsUiState> = combine(
        resultRepository.observeAllResults(),
        _query,
    ) { allResults, query ->
        val filtered = if (query.isBlank()) {
            allResults
        } else {
            allResults.filter { it.matchesQuery(query) }
        }
        ResultsUiState.Success(
            results = allResults,
            query = query,
            filteredResults = filtered,
        ) as ResultsUiState
    }.catch { emit(ResultsUiState.Error(it.message ?: "加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultsUiState.Loading)

    /**
     * T5：更新搜索关键词，触发实时过滤。
     * 搜索忽略大小写，匹配 summary 或 keywords 中任意一项。
     */
    fun setQuery(query: String) {
        _query.value = query
    }

    /** 判断结果是否匹配关键词 */
    private fun ProcessResult.matchesQuery(q: String): Boolean {
        val lower = q.lowercase()
        return summary?.lowercase()?.contains(lower) == true ||
            keywords.any { it.lowercase().contains(lower) }
    }
}
