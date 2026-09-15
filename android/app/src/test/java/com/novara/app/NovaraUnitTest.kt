package com.novara.app

import com.google.gson.Gson
import com.novara.app.data.MockDataProvider
import com.novara.app.data.ProfileStore
import com.novara.app.model.LearnerProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Automated Verification Suite for Tasks 1 to 7
 */
class NovaraUnitTest {

    private val gson = Gson()

    // ─── Task 2 & 3: Schema Validation & Contract Matching ───────────
    @Test
    fun testProfileSchemaValidation_validProfile() {
        val valid = LearnerProfile(
            learnerId = "test_user_01",
            language = "spanish",
            level = "B1",
            region = "Madrid",
            purpose = "trip",
            interests = listOf("food", "travel"),
            weakAreas = listOf("listening")
        )
        assertTrue("Valid profile should pass schema check", ProfileStore.isSchemaValid(valid))
    }

    @Test
    fun testProfileSchemaValidation_invalidLevelFails() {
        val invalidLevel = LearnerProfile(
            learnerId = "test_user_02",
            language = "spanish",
            level = "C2", // Not in A1..B2 scope
            region = "Madrid",
            purpose = "trip",
            interests = listOf("food"),
            weakAreas = listOf("listening")
        )
        assertFalse("Invalid level C2 must fail schema check", ProfileStore.isSchemaValid(invalidLevel))
    }

    @Test
    fun testProfileSerializationContractMatch() {
        val profile = LearnerProfile(
            learnerId = "u_100",
            language = "spanish",
            level = "A2",
            region = "Barcelona",
            purpose = "casual",
            interests = listOf("culture"),
            weakAreas = listOf("speaking")
        )
        val json = gson.toJson(profile)
        assertTrue(json.contains("\"learner_id\":\"u_100\""))
        assertTrue(json.contains("\"language\":\"spanish\""))
        assertTrue(json.contains("\"level\":\"A2\""))
        assertTrue(json.contains("\"region\":\"Barcelona\""))
        assertTrue(json.contains("\"purpose\":\"casual\""))
    }

    // ─── Task 4: Situational Scenario 4 Purposes ────────────────────
    @Test
    fun testFourPurposesRenderCleanly() {
        val purposes = listOf("trip", "casual", "exam", "relocation")
        purposes.forEach { p ->
            val sc = MockDataProvider.getScenarioForPurpose(p)
            assertNotNull("Scenario for $p should exist", sc)
            assertTrue("Scenario title should not be blank", sc.title.isNotBlank())
            assertTrue("Scenario setting should not be blank", sc.setting.isNotBlank())
            assertTrue("Scenario opening line should not be blank", sc.openingLine.isNotBlank())
            assertTrue("Scenario tags should not be empty", sc.situationTags.isNotEmpty())
        }
    }

    // ─── Task 5: 10-Turn Manual Run & Inline Repair Bubble ───────────
    @Test
    fun testTenTurnConversationProgression() {
        for (turn in 1..10) {
            val reply = MockDataProvider.getReplyForTurn(turn, "Hola")
            assertNotNull("Turn $turn must return reply", reply)
            assertTrue("Reply text cannot be blank", reply.reply.isNotBlank())
        }
    }

    @Test
    fun testInlineRepairTriggerOnComprehension() {
        val reply = MockDataProvider.getReplyForTurn(2, "no entiendo nada")
        assertTrue("Repair must trigger on comprehension issue", reply.repairTriggered)
        assertNotNull("Repair object must be populated", reply.repair)
        assertEquals("comprehension", reply.repair?.errorType)
        assertEquals("clarify", reply.repair?.strategy)
    }

    @Test
    fun testInlineRepairTriggerOnGrammar() {
        val reply = MockDataProvider.getReplyForTurn(2, "quiero para llevo")
        assertTrue("Repair must trigger on grammar error", reply.repairTriggered)
        assertNotNull("Repair object must be populated", reply.repair)
        assertEquals("grammar", reply.repair?.errorType)
        assertEquals("rephrase", reply.repair?.strategy)
    }

    // ─── Task 6: 3 Sample Readiness Payloads (High, Mid, Low) ─────────
    @Test
    fun testThreeReadinessPayloads() {
        val high = MockDataProvider.readinessPayloads["high"]
        val mid = MockDataProvider.readinessPayloads["mid"]
        val low = MockDataProvider.readinessPayloads["low"]

        assertNotNull(high)
        assertNotNull(mid)
        assertNotNull(low)

        assertEquals(0.88, high!!.aggregateScore, 0.01)
        assertEquals(0.62, mid!!.aggregateScore, 0.01)
        assertEquals(0.34, low!!.aggregateScore, 0.01)

        // Verify all 4 required breakdown dimensions exist
        val requiredDims = listOf("language_accuracy", "repair_success_rate", "register_appropriateness", "transfer_success")
        requiredDims.forEach { dim ->
            assertTrue("High payload has $dim", high.breakdown.containsKey(dim))
            assertTrue("Mid payload has $dim", mid.breakdown.containsKey(dim))
            assertTrue("Low payload has $dim", low.breakdown.containsKey(dim))
        }
    }
}
