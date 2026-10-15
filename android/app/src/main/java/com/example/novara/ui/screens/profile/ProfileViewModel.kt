package com.example.novara.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.api.RetrofitClient
import com.example.novara.data.model.LoginRequest
import com.example.novara.data.model.ProfileRequest
import com.example.novara.data.model.ProfileResponse
import com.example.novara.data.model.SignupRequest
import com.example.novara.data.repository.NovaraRepository
import com.example.novara.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class ProfileUiState(
    val learnerId: String = "sakshi_test_1",
    val level: String = "A1",
    val region: String = "Madrid",
    val purpose: String = "trip",
    val interests: Set<String> = setOf("food", "travel"),
    val weakAreas: Set<String> = setOf("listening", "grammar"),
    val apiKey: String = "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c",
    val baseUrl: String = "https://novara-api-dnhc.onrender.com/",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val profileResponse: ProfileResponse? = null,
    val isSavedSuccessfully: Boolean = false
)

class ProfileViewModel(
    private val repository: NovaraRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val savedProfilesFlow = prefsRepository.savedLearnerIdsFlow
    val activeLearnerIdFlow = prefsRepository.activeLearnerIdFlow

    init {
        loadExistingProfile()
    }

    fun loadExistingProfile() {
        viewModelScope.launch {
            val activeId = prefsRepository.activeLearnerIdFlow.firstOrNull()
            val level = prefsRepository.activeLevelFlow.firstOrNull() ?: "A1"
            val purpose = prefsRepository.activePurposeFlow.firstOrNull() ?: "trip"
            val region = prefsRepository.activeRegionFlow.firstOrNull() ?: "Madrid"
            val key = prefsRepository.apiKeyFlow.firstOrNull() ?: "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c"
            val url = prefsRepository.baseUrlFlow.firstOrNull() ?: "https://novara-api-dnhc.onrender.com/"
            val token = prefsRepository.authTokenFlow.firstOrNull()

            RetrofitClient.apiKey = key
            RetrofitClient.updateBaseUrl(url)
            RetrofitClient.authToken = token

            _uiState.value = _uiState.value.copy(
                learnerId = activeId ?: _uiState.value.learnerId,
                level = level,
                purpose = purpose,
                region = region,
                apiKey = key,
                baseUrl = url
            )
        }
    }

    fun onLearnerIdChanged(id: String) {
        _uiState.value = _uiState.value.copy(learnerId = id)
    }

    fun generateNewLearnerId() {
        _uiState.value = _uiState.value.copy(
            learnerId = "sakshi_test_${(1..99).random()}"
        )
    }

    fun onLevelSelected(level: String) {
        _uiState.value = _uiState.value.copy(level = level)
    }

    fun onRegionChanged(region: String) {
        _uiState.value = _uiState.value.copy(region = region)
    }

    fun onApiKeyChanged(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
        RetrofitClient.apiKey = apiKey
    }

    fun onBaseUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(baseUrl = url)
        RetrofitClient.updateBaseUrl(url)
    }

    fun onPurposeSelected(purpose: String) {
        _uiState.value = _uiState.value.copy(purpose = purpose)
    }

    fun toggleInterest(interest: String) {
        val current = _uiState.value.interests.toMutableSet()
        if (current.contains(interest)) {
            if (current.size > 1) current.remove(interest)
        } else {
            current.add(interest)
        }
        _uiState.value = _uiState.value.copy(interests = current)
    }

    fun toggleWeakArea(weakArea: String) {
        val current = _uiState.value.weakAreas.toMutableSet()
        if (current.contains(weakArea)) {
            if (current.size > 1) current.remove(weakArea)
        } else {
            current.add(weakArea)
        }
        _uiState.value = _uiState.value.copy(weakAreas = current)
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val currentState = _uiState.value

            val apiKey = currentState.apiKey.ifBlank { "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c" }
            RetrofitClient.apiKey = apiKey
            prefsRepository.saveApiKey(apiKey)

            val baseUrl = currentState.baseUrl.ifBlank { "https://novara-api-dnhc.onrender.com/" }
            RetrofitClient.updateBaseUrl(baseUrl)
            prefsRepository.saveBaseUrl(baseUrl)

            // Step 1: Ensure Auth Session (Signup / Login)
            val learnerIdInput = currentState.learnerId.trim().ifEmpty { "sakshi_test_1" }
            val email = if (learnerIdInput.contains("@")) learnerIdInput else "$learnerIdInput@example.com"

            val signupResult = repository.signup(SignupRequest(email = email, password = "password123"))
            var authResponse = signupResult.getOrNull()

            if (authResponse == null) {
                val loginResult = repository.login(LoginRequest(email = email, password = "password123"))
                authResponse = loginResult.getOrNull()
            }

            val token = authResponse?.token
            val targetLearnerId = authResponse?.learnerId ?: learnerIdInput

            if (!token.isNullOrBlank()) {
                RetrofitClient.authToken = token
                prefsRepository.saveAuthToken(token)
            }

            // Step 2: Create Profile
            val request = ProfileRequest(
                learnerId = targetLearnerId,
                language = "spanish",
                level = currentState.level,
                region = currentState.region.trim().ifEmpty { "Madrid" },
                purpose = currentState.purpose,
                interests = currentState.interests.toList(),
                weakAreas = currentState.weakAreas.toList()
            )

            val result = repository.createProfile(request)
            result.onSuccess { response ->
                prefsRepository.saveSession(
                    learnerId = response.learnerId,
                    level = currentState.level,
                    purpose = currentState.purpose,
                    region = currentState.region,
                    apiKey = apiKey,
                    authToken = token
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    learnerId = response.learnerId,
                    profileResponse = response,
                    isSavedSuccessfully = true
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.localizedMessage ?: "Failed to save profile. Please check connection."
                )
            }
        }
    }

    fun resetSavedState() {
        _uiState.value = _uiState.value.copy(isSavedSuccessfully = false)
    }

    fun switchProfile(learnerId: String) {
        viewModelScope.launch {
            prefsRepository.switchActiveProfile(learnerId)
            loadExistingProfile()
        }
    }

    fun removeProfile(learnerId: String) {
        viewModelScope.launch {
            prefsRepository.removeProfile(learnerId)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            prefsRepository.signOut()
            _uiState.value = ProfileUiState()
        }
    }
}
