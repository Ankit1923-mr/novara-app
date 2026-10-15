package com.example.novara.ui.screens.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.model.ReadinessResponse
import com.example.novara.data.repository.NovaraRepository
import com.example.novara.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class ReadinessUiState(
    val learnerId: String = "",
    val isLoading: Boolean = false,
    val readiness: ReadinessResponse? = null,
    val errorMessage: String? = null
)

class ReadinessViewModel(
    private val repository: NovaraRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadinessUiState())
    val uiState: StateFlow<ReadinessUiState> = _uiState.asStateFlow()

    fun loadReadiness(id: String? = null) {
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

            val result = repository.getReadiness(targetId)
            result.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    readiness = response
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.localizedMessage ?: "Failed to fetch readiness score."
                )
            }
        }
    }
}
