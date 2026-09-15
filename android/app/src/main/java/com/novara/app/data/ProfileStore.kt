package com.novara.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.novara.app.model.LearnerProfile

/**
 * Task 3: Local Profile Store
 * Persists learner profile and state across app restarts, process kills, and device reboots.
 * Provides schema validation matching the API contract.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "novara_profile_store"
        private const val KEY_PROFILE = "learner_profile_json"
        private const val KEY_USE_MOCK = "use_mock_backend"
        private const val KEY_ACTIVE_SCENARIO = "active_scenario_json"

        /**
         * Validates learner profile against the contract rules:
         * - learner_id: 1..100 chars
         * - language: "spanish"
         * - level: "A1" | "A2" | "B1" | "B2"
         * - region: 1..200 chars
         * - purpose: "trip" | "casual" (or extended "exam" | "relocation")
         * - interests: 1..20 items
         * - weak_areas: 1..20 items
         */
        fun isSchemaValid(profile: LearnerProfile?): Boolean {
            if (profile == null) return false
            if (profile.learnerId.isBlank() || profile.learnerId.length > 100) return false
            if (profile.language != "spanish") return false
            if (profile.level !in listOf("A1", "A2", "B1", "B2")) return false
            if (profile.region.isBlank() || profile.region.length > 200) return false
            if (profile.purpose !in listOf("trip", "casual", "exam", "relocation")) return false
            if (profile.interests.isEmpty() || profile.interests.size > 20) return false
            if (profile.weakAreas.isEmpty() || profile.weakAreas.size > 20) return false
            return true
        }
    }

    /**
     * Saves learner profile to disk (commit synchronously for guarantee or apply).
     */
    fun saveProfile(profile: LearnerProfile): Boolean {
        return try {
            val json = gson.toJson(profile)
            prefs.edit().putString(KEY_PROFILE, json).commit()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads learner profile, returning null if absent or schema-invalid.
     */
    fun loadProfile(): LearnerProfile? {
        return try {
            val json = prefs.getString(KEY_PROFILE, null) ?: return null
            val profile = gson.fromJson(json, LearnerProfile::class.java)
            if (isSchemaValid(profile)) profile else null
        } catch (e: Exception) {
            null
        }
    }

    fun hasValidProfile(): Boolean = loadProfile() != null

    fun clear() {
        prefs.edit().clear().commit()
    }

    var useMock: Boolean
        get() = prefs.getBoolean(KEY_USE_MOCK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_USE_MOCK, value).apply()
        }

    fun saveActiveScenario(scenarioJson: String) {
        prefs.edit().putString(KEY_ACTIVE_SCENARIO, scenarioJson).apply()
    }

    fun getActiveScenario(): String? = prefs.getString(KEY_ACTIVE_SCENARIO, null)
}
