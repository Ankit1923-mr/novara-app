package com.example.novara.ui.screens.scenario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.model.ScenarioResponse
import com.example.novara.data.repository.NovaraRepository
import com.example.novara.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class ScenarioUiState(
    val learnerId: String = "",
    val isLoading: Boolean = false,
    val scenario: ScenarioResponse? = null,
    val errorMessage: String? = null
)

class ScenarioViewModel(
    private val repository: NovaraRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScenarioUiState())
    val uiState: StateFlow<ScenarioUiState> = _uiState.asStateFlow()

    fun loadScenario(id: String? = null) {
        viewModelScope.launch {
            val targetId = id ?: prefsRepository.activeLearnerIdFlow.firstOrNull() ?: ""
            if (targetId.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No active learner profile found. Please create a profile first."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                learnerId = targetId,
                isLoading = true,
                errorMessage = null
            )

            val result = repository.getScenario(targetId)
            result.onSuccess { scenarioResponse ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scenario = scenarioResponse
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.localizedMessage ?: "Failed to generate scenario. Please try again."
                )
            }
        }
    }
}
