/**
 * Mock Data Provider for NOVARA.
 * Fulfills:
 * - Task 4: Scenarios across 4 purposes (Trip, Casual, Exam, Relocation)
 * - Task 5: 10-turn conversation sequence with inline repair
 * - Task 6: 3 sample payloads for Readiness Dashboard (High, Mid, Low)
 */

export const MOCK_SCENARIOS = {
  trip: {
    scenario_id: 'mock_trip_01',
    purpose: 'trip',
    title: 'Ordering at a Traditional Tapas Bar',
    setting: 'El Tigre, Barrio de Chueca, Madrid',
    situation_tags: ['food', 'ordering', 'social', 'travel'],
    opening_line: '¡Buenas tardes! ¿Qué os pongo de beber? Con la caña viene una tapa de la casa.'
  },
  casual: {
    scenario_id: 'mock_casual_01',
    purpose: 'casual',
    title: 'Weekend Plans with a Local Friend',
    setting: 'Plaza del Sol, Gràcia, Barcelona',
    situation_tags: ['friendship', 'culture', 'weekend', 'casual'],
    opening_line: '¡Hola! Qué bien verte. ¿Qué planes tienes para este fin de semana? Hay un concierto en el parque.'
  },
  exam: {
    scenario_id: 'mock_exam_01',
    purpose: 'exam',
    title: 'DELE B1 Oral Interview: Travel & Culture',
    setting: 'Instituto Cervantes Examination Hall',
    situation_tags: ['formal', 'interview', 'past_tense', 'exam'],
    opening_line: 'Buenos días. En esta primera tarea, por favor hábleme de un viaje reciente y qué le sorprendió más.'
  },
  relocation: {
    scenario_id: 'mock_relocation_01',
    purpose: 'relocation',
    title: 'Town Hall Residency Registration (Padrón)',
    setting: 'Oficina de Atención Ciudadana, Valencia',
    situation_tags: ['bureaucracy', 'housing', 'registration', 'formal'],
    opening_line: 'Buenos días, pase al mostrador 4. ¿Trae el contrato de alquiler y su documento de identidad?'
  }
}

export const MOCK_READINESS_PAYLOADS = {
  high: {
    learner_id: 'eval_high_01',
    aggregate_score: 0.88,
    purpose: 'trip',
    breakdown: {
      language_accuracy: 0.92,
      repair_success_rate: 0.86,
      register_appropriateness: 0.90,
      transfer_success: 0.84,
    },
    weights_used: {
      language_accuracy: 0.3,
      repair_success_rate: 0.3,
      register_appropriateness: 0.2,
      transfer_success: 0.2,
    }
  },
  mid: {
    learner_id: 'eval_mid_01',
    aggregate_score: 0.62,
    purpose: 'casual',
    breakdown: {
      language_accuracy: 0.68,
      repair_success_rate: 0.58,
      register_appropriateness: 0.65,
      transfer_success: 0.55,
    },
    weights_used: {
      language_accuracy: 0.3,
      repair_success_rate: 0.3,
      register_appropriateness: 0.2,
      transfer_success: 0.2,
    }
  },
  low: {
    learner_id: 'eval_low_01',
    aggregate_score: 0.34,
    purpose: 'trip',
    breakdown: {
      language_accuracy: 0.40,
      repair_success_rate: 0.28,
      register_appropriateness: 0.38,
      transfer_success: 0.30,
    },
    weights_used: {
      language_accuracy: 0.3,
      repair_success_rate: 0.3,
      register_appropriateness: 0.2,
      transfer_success: 0.2,
    }
  }
}

/**
 * Generates scripted 10-turn conversation responses
 */
export function getMockConversationReply(turnNumber, userMessage = '') {
  const replies = [
    {
      reply: 'Perfecto, una caña bien fría. ¿Y de tapa prefieres patatas bravas o tortilla española?',
      repair_triggered: false,
      repair: null
    },
    {
      reply: 'Marchando la tortilla. Está recién hecha. ¿Vas a querer algo más de comer o solo picar?',
      repair_triggered: userMessage.toLowerCase().includes('no entiendo') || userMessage.toLowerCase().includes('quiero para llevo'),
      repair: userMessage.toLowerCase().includes('quiero para llevo') ? {
        error_type: 'grammar',
        strategy: 'rephrase',
        repair_text: "Se dice 'para llevar', no 'para llevo'."
      } : (userMessage.toLowerCase().includes('no entiendo') ? {
        error_type: 'comprehension',
        strategy: 'clarify',
        repair_text: "¿Prefieres raciones grandes para cenar, o tapas pequeñas?"
      } : null)
    },
    {
      reply: 'Muy bien. La ración de croquetas de jamón ibérico está riquísima hoy.',
      repair_triggered: false,
      repair: null
    },
    {
      reply: 'Aquí tienes todo. Que aproveche. Avísame si necesitas más pan o agua.',
      repair_triggered: false,
      repair: null
    },
    {
      reply: 'Por supuesto, enseguida te traigo la cuenta. ¿Pagarás con tarjeta o en efectivo?',
      repair_triggered: false,
      repair: null
    },
    {
      reply: 'Aceptamos tarjeta contactless sin problema. Pasa cuando quieras por la caja.',
      repair_triggered: false,
      repair: null
    },
    {
      reply: 'Ha sido un placer atenderte. ¿Qué tal te ha parecido la tortilla?',
      repair_triggered: false,
      repair: null
    },
    {
      reply: '¡Me alegro mucho! Vuelve cuando quieras por aquí.',
      repair_triggered: false,
      repair: null
    },
    {
      reply: 'Hasta pronto y que disfrutes de tu estancia en la ciudad.',
      repair_triggered: false,
      repair: null
    },
    {
      reply: '¡Adiós! Buen viaje y cuídate mucho.',
      repair_triggered: false,
      repair: null
    }
  ]

  const index = Math.min(turnNumber - 1, replies.length - 1)
  return replies[index] || {
    reply: 'Entendido perfectamente. Sigamos conversando en español.',
    repair_triggered: false,
    repair: null
  }
}
