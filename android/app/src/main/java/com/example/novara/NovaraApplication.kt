package com.example.novara

import android.app.Application
import com.example.novara.data.repository.NovaraRepository
import com.example.novara.data.repository.UserPreferencesRepository

class NovaraApplication : Application() {

    lateinit var repository: NovaraRepository
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = NovaraRepository()
        userPreferencesRepository = UserPreferencesRepository(this)
    }
}
