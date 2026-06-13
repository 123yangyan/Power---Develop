package com.timedrecorder.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    preferencesDataSource: PreferencesDataSource,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean> = preferencesDataSource.userPreferences
        .map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** T6：当前主题模式，供 MainActivity 动态决定 darkTheme 参数 */
    val themeMode: StateFlow<ThemeMode> = preferencesDataSource.userPreferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
}
