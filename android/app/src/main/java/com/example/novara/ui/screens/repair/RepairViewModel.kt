package com.example.novara.ui.screens.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.model.RepairDetails
import com.example.novara.data.model.RepairRequest
import com.example.novara.data.repository.NovaraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RepairPracticeUiState(
    val learnerUtterance: String = "Quiero un café para llevo",
    val expectedPattern: String = "para llevar",
    val isLoading: Boolean = false,
    val repairDetails: RepairDetails? = null,
    val errorMessage: String? = null
)

class RepairViewModel(
    private val repository: NovaraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairPracticeUiState())
    val uiState: StateFlow<RepairPracticeUiState> = _uiState.asStateFlow()

    fun onUtteranceChanged(text: String) {
        _uiState.value = _uiState.value.copy(learnerUtterance = text)
    }

    fun onExpectedPatternChanged(text: String) {
        _uiState.value = _uiState.value.copy(expectedPattern = text)
    }

    fun analyzeRepair() {
        val utterance = _uiState.value.learnerUtterance.trim()
        val pattern = _uiState.value.expectedPattern.trim()

        if (utterance.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val request = RepairRequest(learnerUtterance = utterance, expectedPattern = pattern)
            val result = repository.submitRepair(request)

            result.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    repairDetails = response
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.localizedMessage ?: "Failed to classify repair."
                )
            }
        }
    }
}
