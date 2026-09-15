package com.novara.app.data

import com.novara.app.model.ConversationResponse
import com.novara.app.model.ReadinessResponse
import com.novara.app.model.RepairDetail
import com.novara.app.model.ScenarioResponse

/**
 * Mock data provider fulfilling:
 * - Task 4: Scenarios across 4 purposes (Trip, Casual, Exam, Relocation)
 * - Task 5: 10-turn conversation script with inline repair triggers
 * - Task 6: 3 sample payloads for Readiness Dashboard (High, Mid, Low)
 */
object MockDataProvider {

    // ─── Task 4: 4 Purposes Scenarios ────────────────────────────────
    val scenarios: Map<String, ScenarioResponse> = mapOf(
        "trip" to ScenarioResponse(
            scenarioId = "mock_trip_01",
            purpose = "trip",
            title = "Ordering Tapas & Drinks in Madrid",
            setting = "A lively taberna in La Latina, Madrid",
            situationTags = listOf("food", "ordering", "social", "travel"),
            openingLine = "¡Buenas tardes! ¿Qué os pongo de beber? Con cada caña tenéis una tapa."
        ),
        "casual" to ScenarioResponse(
            scenarioId = "mock_casual_01",
            purpose = "casual",
            title = "Catching Up with a Friend in Barcelona",
            setting = "A sunny terrace in Gràcia, Barcelona",
            situationTags = listOf("friendship", "culture", "weekend", "casual"),
            openingLine = "¡Hola! Qué alegría verte. ¿Qué planes tienes para el fin de semana?"
        ),
        "exam" to ScenarioResponse(
            scenarioId = "mock_exam_01",
            purpose = "exam",
            title = "DELE B1 Oral Task: Describing an Experience",
            setting = "Instituto Cervantes Examination Room",
            situationTags = listOf("formal", "interview", "past_tense", "exam"),
            openingLine = "Buenos días. Para comenzar, hábleme de un viaje reciente y qué aprendió."
        ),
        "relocation" to ScenarioResponse(
            scenarioId = "mock_relocation_01",
            purpose = "relocation",
            title = "Residency Registration (Padrón) at Town Hall",
            setting = "Oficina de Empadronamiento, Valencia",
            situationTags = listOf("bureaucracy", "housing", "formal", "registration"),
            openingLine = "Buenos días, pase. ¿Trae su contrato de alquiler y documento de identidad?"
        )
    )

    fun getScenarioForPurpose(purpose: String): ScenarioResponse {
        return scenarios[purpose.lowercase()] ?: scenarios["trip"]!!
    }

    // ─── Task 5: 10-Turn Scripted Dialogue with Repairs ──────────────
    fun getReplyForTurn(turnNumber: Int, userUtterance: String): ConversationResponse {
        val lower = userUtterance.lowercase()

        // Turn-specific or trigger-specific repair
        if (lower.contains("no entiendo")) {
            return ConversationResponse(
                reply = "¿Le gustaría que le recomiende la especialidad de la casa?",
                repairTriggered = true,
                repair = RepairDetail(
                    errorType = "comprehension",
                    strategy = "clarify",
                    repairText = "No hay problema: le pregunto si quiere una sugerencia para comer."
                )
            )
        }

        if (lower.contains("para llevo") || lower.contains("quiero para llevo")) {
            return ConversationResponse(
                reply = "Claro, se lo preparo para llevar enseguida.",
                repairTriggered = true,
                repair = RepairDetail(
                    errorType = "grammar",
                    strategy = "rephrase",
                    repairText = "Se dice 'para llevar', no 'para llevo'."
                )
            )
        }

        return when (turnNumber) {
            1 -> ConversationResponse(
                reply = "Perfecto. Tenemos tortilla de patatas recién hecha y croquetas caseras.",
                repairTriggered = false,
                repair = null
            )
            2 -> ConversationResponse(
                reply = "Marchando una ración de tortilla. ¿La prefieres con o sin cebolla?",
                repairTriggered = false,
                repair = null
            )
            3 -> ConversationResponse(
                reply = "Excelente elección. Aquí en Madrid siempre hay debate con la cebolla.",
                repairTriggered = false,
                repair = null
            )
            4 -> ConversationResponse(
                reply = "Aquí tienes tu caña y la tapa. ¡Que aproveche! ¿Te traigo un poco de agua?",
                repairTriggered = false,
                repair = null
            )
            5 -> ConversationResponse(
                reply = "Aquí está el agua fría. ¿Vas a querer algún postre casero después?",
                repairTriggered = false,
                repair = null
            )
            6 -> ConversationResponse(
                reply = "Tenemos tarta de queso al horno y flan con nata.",
                repairTriggered = false,
                repair = null
            )
            7 -> ConversationResponse(
                reply = "La tarta de queso está espectacular hoy. Enseguida te la marcho.",
                repairTriggered = false,
                repair = null
            )
            8 -> ConversationResponse(
                reply = "¿Qué tal estaba todo? Espero que te haya gustado.",
                repairTriggered = false,
                repair = null
            )
            9 -> ConversationResponse(
                reply = "Me alegro mucho. ¿Te traigo la cuenta a la mesa?",
                repairTriggered = false,
                repair = null
            )
            10 -> ConversationResponse(
                reply = "Son doce euros con cincuenta. Puedes pagar con tarjeta o efectivo. ¡Muchas gracias y buen viaje!",
                repairTriggered = false,
                repair = null
            )
            else -> ConversationResponse(
                reply = "Entendido perfectamente. Sigamos practicando en español.",
                repairTriggered = false,
                repair = null
            )
        }
    }

    // ─── Task 6: 3 Sample Readiness Payloads (High, Mid, Low) ─────────
    val readinessPayloads: Map<String, ReadinessResponse> = mapOf(
        "high" to ReadinessResponse(
            learnerId = "sample_high_01",
            aggregateScore = 0.88,
            purpose = "trip",
            breakdown = mapOf(
                "language_accuracy" to 0.92,
                "repair_success_rate" to 0.86,
                "register_appropriateness" to 0.90,
                "transfer_success" to 0.84
            ),
            weightsUsed = mapOf(
                "language_accuracy" to 0.3,
                "repair_success_rate" to 0.3,
                "register_appropriateness" to 0.2,
                "transfer_success" to 0.2
            )
        ),
        "mid" to ReadinessResponse(
            learnerId = "sample_mid_01",
            aggregateScore = 0.62,
            purpose = "casual",
            breakdown = mapOf(
                "language_accuracy" to 0.68,
                "repair_success_rate" to 0.58,
                "register_appropriateness" to 0.65,
                "transfer_success" to 0.55
            ),
            weightsUsed = mapOf(
                "language_accuracy" to 0.3,
                "repair_success_rate" to 0.3,
                "register_appropriateness" to 0.2,
                "transfer_success" to 0.2
            )
        ),
        "low" to ReadinessResponse(
            learnerId = "sample_low_01",
            aggregateScore = 0.34,
            purpose = "trip",
            breakdown = mapOf(
                "language_accuracy" to 0.40,
                "repair_success_rate" to 0.28,
                "register_appropriateness" to 0.38,
                "transfer_success" to 0.30
            ),
            weightsUsed = mapOf(
                "language_accuracy" to 0.3,
                "repair_success_rate" to 0.3,
                "register_appropriateness" to 0.2,
                "transfer_success" to 0.2
            )
        )
    )
}
