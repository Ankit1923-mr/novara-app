package com.example.novara.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "novara_user_prefs")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_ACTIVE_LEARNER_ID = stringPreferencesKey("active_learner_id")
        private val KEY_SAVED_LEARNER_IDS = stringPreferencesKey("saved_learner_ids")
        private val KEY_ACTIVE_LEVEL = stringPreferencesKey("active_level")
        private val KEY_ACTIVE_PURPOSE = stringPreferencesKey("active_purpose")
        private val KEY_ACTIVE_REGION = stringPreferencesKey("active_region")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
    }

    val activeLearnerIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_LEARNER_ID]
    }

    val savedLearnerIdsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SAVED_LEARNER_IDS] ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val activeLevelFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_LEVEL] ?: "A1"
    }

    val activePurposeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PURPOSE] ?: "trip"
    }

    val activeRegionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_REGION] ?: "Madrid"
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c"
    }

    val authTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTH_TOKEN]
    }

    suspend fun saveSession(
        learnerId: String,
        level: String,
        purpose: String,
        region: String,
        apiKey: String = "fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c",
        authToken: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_LEARNER_ID] = learnerId
            prefs[KEY_ACTIVE_LEVEL] = level
            prefs[KEY_ACTIVE_PURPOSE] = purpose
            prefs[KEY_ACTIVE_REGION] = region
            prefs[KEY_API_KEY] = apiKey
            if (!authToken.isNullOrBlank()) {
                prefs[KEY_AUTH_TOKEN] = authToken
            }

            val existingList = prefs[KEY_SAVED_LEARNER_IDS]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            if (!existingList.contains(learnerId)) {
                val updatedList = existingList + learnerId
                prefs[KEY_SAVED_LEARNER_IDS] = updatedList.joinToString(",")
            }
        }
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_KEY] = apiKey
        }
    }

    suspend fun saveAuthToken(authToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = authToken
        }
    }

    suspend fun switchActiveProfile(learnerId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_LEARNER_ID] = learnerId
        }
    }

    suspend fun signOut() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_LEARNER_ID)
            prefs.remove(KEY_AUTH_TOKEN)
        }
    }

    suspend fun removeProfile(learnerId: String) {
        context.dataStore.edit { prefs ->
            val existingList = prefs[KEY_SAVED_LEARNER_IDS]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val updatedList = existingList.filter { it != learnerId }
            prefs[KEY_SAVED_LEARNER_IDS] = updatedList.joinToString(",")
            if (prefs[KEY_ACTIVE_LEARNER_ID] == learnerId) {
                if (updatedList.isNotEmpty()) {
                    prefs[KEY_ACTIVE_LEARNER_ID] = updatedList.first()
                } else {
                    prefs.remove(KEY_ACTIVE_LEARNER_ID)
                }
            }
        }
    }
}
