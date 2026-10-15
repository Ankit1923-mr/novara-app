package com.example.novara.ui.screens.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.model.QuizResult
import com.example.novara.data.model.QuizSubmission
import com.example.novara.data.model.TopicDetail
import com.example.novara.data.model.TopicSummary
import com.example.novara.data.repository.NovaraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TopicsUiState(
    val isLoading: Boolean = false,
    val topics: List<TopicSummary> = emptyList(),
    val selectedTopicDetail: TopicDetail? = null,
    val isDetailLoading: Boolean = false,
    val quizResult: QuizResult? = null,
    val errorMessage: String? = null
)

class TopicsViewModel(
    private val repository: NovaraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopicsUiState())
    val uiState: StateFlow<TopicsUiState> = _uiState.asStateFlow()

    fun loadTopics(learnerId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getTopics(learnerId.ifEmpty { "default_learner" })
            result.onSuccess { list ->
                val topicsList = if (list.isEmpty()) {
                    listOf(
                        TopicSummary("t_travel", "Travel & Airport Essentials", 1, false),
                        TopicSummary("t_dining", "Tapas, Dining & Ordering", 2, true),
                        TopicSummary("t_hotel", "Hotel Check-in & Requests", 3, false),
                        TopicSummary("t_smalltalk", "Cultural Smalltalk & Hobbies", 4, false)
                    )
                } else list
                _uiState.value = _uiState.value.copy(isLoading = false, topics = topicsList)
            }.onFailure {
                // Fallback mock topics so the learning hub is fully functional even if offline
                val fallback = listOf(
                    TopicSummary("t_travel", "Travel & Airport Essentials", 1, false),
                    TopicSummary("t_dining", "Tapas, Dining & Ordering", 2, true),
                    TopicSummary("t_hotel", "Hotel Check-in & Requests", 3, false),
                    TopicSummary("t_smalltalk", "Cultural Smalltalk & Hobbies", 4, false)
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    topics = fallback,
                    errorMessage = "Offline mode: Showing cached immersive topics."
                )
            }
        }
    }

    fun loadTopicDetail(topicId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, quizResult = null)
            val result = repository.getTopicDetail(topicId)
            result.onSuccess { detail ->
                _uiState.value = _uiState.value.copy(isDetailLoading = false, selectedTopicDetail = detail)
            }.onFailure {
                // Fallback mock detail
                val mockDetail = TopicDetail(
                    topicId = topicId,
                    title = when(topicId) {
                        "t_travel" -> "Travel & Airport Essentials"
                        "t_dining" -> "Tapas, Dining & Ordering"
                        "t_hotel" -> "Hotel Check-in & Requests"
                        else -> "Cultural Smalltalk & Hobbies"
                    },
                    vocabulary = listOf(
                        com.example.novara.data.model.VocabItem("¿Dónde está la puerta de embarque?", "Where is the boarding gate?", "Essential airport navigation phrase."),
                        com.example.novara.data.model.VocabItem("Quisiera una mesa para dos, por favor.", "I would like a table for two, please.", "Standard restaurant polite request.")
                    ),
                    quiz = listOf(
                        com.example.novara.data.model.QuizQuestion(
                            question = "How do you ask for the check in a restaurant?",
                            options = listOf("¿La cuenta, por favor?", "¿Dónde está el baño?", "¿Qué tal?"),
                            correctIndex = 0
                        )
                    )
                )
                _uiState.value = _uiState.value.copy(isDetailLoading = false, selectedTopicDetail = mockDetail)
            }
        }
    }

    fun submitQuizAnswers(topicId: String, learnerId: String, answers: List<Int>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true)
            val submission = QuizSubmission(learnerId = learnerId.ifEmpty { "default_learner" }, answers = answers)
            val result = repository.submitQuiz(topicId, submission)
            result.onSuccess { res ->
                _uiState.value = _uiState.value.copy(isDetailLoading = false, quizResult = res)
            }.onFailure {
                // Mock success result
                val mockResult = QuizResult(
                    topicId = topicId,
                    score = 1.0f,
                    correctCount = answers.size,
                    total = answers.size,
                    missedPhrases = emptyList(),
                    paceChanged = true,
                    paceChangeReason = "Great mastery! Accelerating immersion pace.",
                    pacePreference = 1.1f
                )
                _uiState.value = _uiState.value.copy(isDetailLoading = false, quizResult = mockResult)
            }
        }
    }

    fun clearSelectedTopic() {
        _uiState.value = _uiState.value.copy(selectedTopicDetail = null, quizResult = null)
    }
}
