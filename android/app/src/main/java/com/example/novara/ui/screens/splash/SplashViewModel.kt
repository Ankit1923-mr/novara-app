package com.example.novara.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novara.data.repository.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class SplashNavigationState {
    object Loading : SplashNavigationState()
    data class Authenticated(val learnerId: String) : SplashNavigationState()
    object Unauthenticated : SplashNavigationState()
}

class SplashViewModel(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _navigationState = MutableStateFlow<SplashNavigationState>(SplashNavigationState.Loading)
    val navigationState: StateFlow<SplashNavigationState> = _navigationState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            _navigationState.value = SplashNavigationState.Loading
            delay(1200) // Brief delay for splash branding visibility
            val activeId = prefsRepository.activeLearnerIdFlow.firstOrNull()
            if (!activeId.isNullOrBlank()) {
                _navigationState.value = SplashNavigationState.Authenticated(activeId)
            } else {
                _navigationState.value = SplashNavigationState.Unauthenticated
            }
        }
    }
}
