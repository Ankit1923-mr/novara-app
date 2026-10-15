package com.example.novara.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.model.ChatMessage
import com.example.novara.data.model.ConversationRequest
import com.example.novara.data.model.Sender
import com.example.novara.data.repository.NovaraRepository
import com.example.novara.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class ChatUiState(
    val learnerId: String = "",
    val scenarioId: String = "",
    val turnNumber: Int = 1,
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val currentPacePreference: Float? = null,
    val lastPaceChangeReason: String? = null
)

class ChatViewModel(
    private val repository: NovaraRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageStartTimeMs: Long = System.currentTimeMillis()

    fun initializeChat(learnerId: String, scenarioId: String, openingLine: String) {
        viewModelScope.launch {
            val activeId = if (learnerId.isNotBlank()) learnerId else prefsRepository.activeLearnerIdFlow.firstOrNull() ?: ""
            val activeScenarioId = if (scenarioId.isNotBlank()) scenarioId else "scenario_default"

            val initialMessages = if (openingLine.isNotBlank()) {
                listOf(
                    ChatMessage(
                        sender = Sender.AI,
                        text = openingLine,
                        turnNumber = 1
                    )
                )
            } else emptyList()

            _uiState.value = ChatUiState(
                learnerId = activeId,
                scenarioId = activeScenarioId,
                turnNumber = 1,
                messages = initialMessages
            )

            messageStartTimeMs = System.currentTimeMillis()
        }
    }

    fun sendMessage(text: String) {
        val messageText = text.trim()
        if (messageText.isBlank() || _uiState.value.isSending) return

        val responseTimeMs = System.currentTimeMillis() - messageStartTimeMs
        val currentTurn = _uiState.value.turnNumber

        val learnerMessage = ChatMessage(
            sender = Sender.LEARNER,
            text = messageText,
            turnNumber = currentTurn
        )

        val updatedMessages = _uiState.value.messages + learnerMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            isSending = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val request = ConversationRequest(
                learnerId = _uiState.value.learnerId,
                scenarioId = _uiState.value.scenarioId,
                message = messageText,
                turnNumber = currentTurn,
                responseTimeMs = responseTimeMs
            )

            val result = repository.sendConversation(request)
            result.onSuccess { response ->
                val aiMessage = ChatMessage(
                    sender = Sender.AI,
                    text = response.reply,
                    turnNumber = currentTurn + 1,
                    repairDetails = if (response.repairTriggered) response.repair else null,
                    paceChangeReason = if (response.paceChanged == true) response.paceChangeReason else null,
                    pacePreference = response.pacePreference
                )

                _uiState.value = _uiState.value.copy(
                    turnNumber = currentTurn + 1,
                    messages = _uiState.value.messages + aiMessage,
                    isSending = false,
                    currentPacePreference = response.pacePreference ?: _uiState.value.currentPacePreference,
                    lastPaceChangeReason = response.paceChangeReason ?: _uiState.value.lastPaceChangeReason
                )
                messageStartTimeMs = System.currentTimeMillis()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = throwable.localizedMessage ?: "Failed to receive AI partner reply."
                )
            }
        }
    }
}
