package com.timedrecorder.feature.results

import com.timedrecorder.core.model.ProcessResult

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Success(
        val results: List<ProcessResult>,
        /** T5：当前搜索关键词 */
        val query: String = "",
        /** T5：按关键词过滤后的结果列表（空 query 时等于 results） */
        val filteredResults: List<ProcessResult> = results,
    ) : ResultsUiState
    data class Error(val message: String) : ResultsUiState
}
