package com.owner.mindbody.ui.ftu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FtuFormState(
    val gender: PolarFirstTimeUseConfig.Gender = PolarFirstTimeUseConfig.Gender.MALE,
    val birthYear: Int = 1995,
    val heightCm: String = "170",
    val weightKg: String = "65",
    val restingHr: String = "60",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false
)

class FtuViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication

    private val _form = MutableStateFlow(FtuFormState())
    val form: StateFlow<FtuFormState> = _form.asStateFlow()

    fun updateGender(gender: PolarFirstTimeUseConfig.Gender) {
        _form.value = _form.value.copy(gender = gender)
    }

    fun updateBirthYear(year: Int) {
        _form.value = _form.value.copy(birthYear = year)
    }

    fun updateHeight(value: String) {
        _form.value = _form.value.copy(heightCm = value)
    }

    fun updateWeight(value: String) {
        _form.value = _form.value.copy(weightKg = value)
    }

    fun updateRestingHr(value: String) {
        _form.value = _form.value.copy(restingHr = value)
    }

    fun submit(deviceId: String) {
        val state = _form.value
        val height = state.heightCm.toFloatOrNull()
        val weight = state.weightKg.toFloatOrNull()
        val resting = state.restingHr.toIntOrNull()

        if (height == null || weight == null || resting == null) {
            _form.value = state.copy(errorMessage = "请填写有效的身高、体重和静息心率")
            return
        }

        viewModelScope.launch {
            _form.value = state.copy(isSubmitting = true, errorMessage = null)
            val config = app.polarBleManager.buildDefaultFtuConfig(
                gender = state.gender,
                birthDate = LocalDate.of(state.birthYear, 1, 1),
                heightCm = height,
                weightKg = weight,
                restingHr = resting
            )
            val result = app.polarBleManager.performFirstTimeUse(deviceId, config)
            _form.value = if (result.isSuccess) {
                state.copy(isSubmitting = false, success = true, errorMessage = null)
            } else {
                state.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "首次配置失败"
                )
            }
        }
    }
}
