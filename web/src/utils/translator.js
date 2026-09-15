/**
 * Intelligent Spanish <-> English Translation Utility for NOVARA
 * Provides:
 * 1. Instant offline dictionary for all common conversational Spanish phrases & scenario dialogues
 * 2. Asynchronous API translation fallback with local cache
 * 3. English input detection & smart Spanish translation suggestion
 */

const TRANSLATION_CACHE = new Map()

// Curated high-fidelity dictionary for scenario and conversational Spanish
const ES_TO_EN_DICTIONARY = {
  // Scenario Opening Lines
  '¡hola! bienvenido a novara. vamos a practicar español con pronunciación real.': 'Hello! Welcome to Novara. Let\'s practice Spanish with real pronunciation.',
  'hola, ¿qué le pongo?': 'Hello, what can I get for you?',
  '¡buenas tardes! ¿qué os pongo de beber? con cada caña tenéis una tapa.': 'Good afternoon! What can I get you to drink? Each beer comes with a free tapa.',
  '¡buenas tardes! ¿qué os pongo de beber? con la caña viene una tapa de la casa.': 'Good afternoon! What can I get you to drink? A house tapa comes with your beer.',
  '¡hola! qué bien verte. ¿qué planes tienes para este fin de semana? hay un concierto en el parque.': 'Hello! Great to see you. What plans do you have for this weekend? There\'s a concert in the park.',
  '¡hola! qué alegría verte. ¿qué planes tienes para el fin de semana?': 'Hello! So great to see you. What plans do you have for the weekend?',
  'buenos días. en esta primera tarea, por favor hábleme de un viaje reciente y qué le sorprendió más.': 'Good morning. In this first task, please tell me about a recent trip and what surprised you most.',
  'buenos días. para comenzar, hábleme de un viaje reciente y qué aprendió.': 'Good morning. To start, tell me about a recent trip and what you learned.',
  'buenos días, pase al mostrador 4. ¿trae el contrato de alquiler y su documento de identidad?': 'Good morning, please step up to counter 4. Did you bring your rental contract and identity document?',
  'buenos días, pase. ¿trae su contrato de alquiler y documento de identidad?': 'Good morning, please come in. Did you bring your rental contract and identity document?',
  'quiero un café, por favor.': 'I want a coffee, please.',
  'quiero un café': 'I want a coffee',

  // Common Conversation Replies
  '¿para aquí o para llevar?': 'For here or to go?',
  '¿qué tipo de café le gustaría pedir?': 'What kind of coffee would you like to order?',
  'claro, un café con leche. ¿desea algo más?': 'Sure, a coffee with milk. Would you like anything else?',
  'perfecto, una caña bien fría. ¿y de tapa prefieres patatas bravas o tortilla española?': 'Perfect, an ice-cold draft beer. And for your tapa do you prefer spicy brava potatoes or Spanish omelette?',
  'marchando una ración de tortilla. ¿la prefieres con o sin cebolla?': 'One portion of Spanish omelette coming right up. Do you prefer it with or without onion?',
  'excelente elección. aquí en madrid siempre hay debate con la cebolla.': 'Excellent choice. Here in Madrid there is always a debate about the onion.',
  'aquí tienes todo. que aproveche. avísame si necesitas más pan o agua.': 'Here is everything. Enjoy your meal! Let me know if you need more bread or water.',
  'aquí tienes tu caña y la tapa. ¡que aproveche! ¿te traigo un poco de agua?': 'Here is your draft beer and tapa. Enjoy! Can I bring you some water?',
  'cuesta dos euros, por favor.': 'It costs two euros, please.',
  'son doce euros con cincuenta. puedes pagar con tarjeta o efectivo. ¡muchas gracias y buen viaje!': 'That\'s twelve euros and fifty cents. You can pay by card or cash. Thank you very much and have a great trip!',
  'por supuesto, enseguida te traigo la cuenta. ¿pagarás con tarjeta o en efectivo?': 'Of course, I\'ll bring you the bill right away. Will you be paying with card or cash?',
  'aceptamos tarjeta contactless sin problema. pasa cuando quieras por la caja.': 'We accept contactless card without any problem. Drop by the register whenever you like.',
  'ha sido un placer atenderte. ¿qué tal te ha parecido la tortilla?': 'It\'s been a pleasure serving you. How did you find the omelette?',
  '¡me alegro mucho! vuelve cuando quieras por aquí.': 'I\'m so glad! Come back anytime.',
  'hasta pronto y que disfrutes de tu estancia en la ciudad.': 'See you soon and enjoy your stay in the city.',
  '¡adiós! buen viaje y cuídate mucho.': 'Goodbye! Safe travels and take care.',

  // Additional dialogue patterns
  'hola': 'Hello!',
  'buenos días': 'Good morning.',
  'buenas tardes': 'Good afternoon.',
  'buenas noches': 'Good evening.',
  'muchas gracias': 'Thank you very much.',
  'de nada': 'You\'re welcome.',
  '¿algo más?': 'Anything else?',
  '¿desea algo más?': 'Would you like anything else?',
  '¿qué quieres tomar?': 'What would you like to drink?',
  '¿qué le pongo de beber?': 'What can I get you to drink?',
  '¿con o sin cebolla?': 'With or without onion?',
  'con cebolla': 'With onion.',
  'sin cebolla': 'Without onion.',
  'para tomar aquí': 'For here.',
  'para llevar': 'To go (take away).',
  'la cuenta, por favor': 'The bill, please.',
  '¿se puede pagar con tarjeta?': 'Can I pay with card?',
  '¿tarjeta o efectivo?': 'Card or cash?',
  'con tarjeta': 'By card.',
  'en efectivo': 'In cash.',

  // Common Repair Texts
  "se dice 'para llevar', no 'para llevo'.": "You say 'para llevar' (to take away), not 'para llevo'.",
  "¿puede repetir, por favor?": "Could you please repeat that?",
  "no hay problema: le pregunto si quiere una sugerencia para comer.": "No problem: I am asking if you would like a food suggestion.",
  "¿prefieres raciones grandes para cenar, o tapas pequeñas?": "Do you prefer large dinner platters, or small tapas?",
  "recuerda usar 'por favor' y 'gracias' en situaciones cotidianas.": "Remember to use 'please' and 'thank you' in everyday situations.",
  "en españa solemos decir 'una caña' para pedir cerveza de grifo.": "In Spain we usually say 'una caña' to order draft beer.",
  "intenta decir 'quisiera' o 'me pones' para sonar más natural.": "Try saying 'quisiera' (I would like) or 'me pones' (could you get me) to sound more natural."
}

// English to Spanish quick mapping for learner assistance
const EN_TO_ES_DICTIONARY = {
  'hello': '¡Hola!',
  'hi': '¡Hola!',
  'hey': '¡Hola!',
  'good morning': 'Buenos días',
  'good afternoon': 'Buenas tardes',
  'good evening': 'Buenas noches',
  'i want a coffee': 'Quiero un café, por favor',
  'i want coffee': 'Quiero un café, por favor',
  'coffee': 'Un café, por favor',
  'coffee please': 'Un café, por favor',
  'i would like a coffee with milk': 'Quisiera un café con leche, por favor',
  'coffee with milk': 'Un café con leche, por favor',
  'can i have a beer': '¿Me pones una caña, por favor?',
  'beer': 'Una cerveza, por favor',
  'beer please': 'Una caña, por favor',
  'can i have a draft beer': '¿Me pones una caña, por favor?',
  'water': 'Un vaso de agua, por favor',
  'water please': 'Un vaso de agua, por favor',
  'tapas': 'Unas tapas, por favor',
  'spanish omelette': 'Tortilla española, por favor',
  'omelette': 'Una porción de tortilla, por favor',
  'with onion': 'Con cebolla, por favor',
  'without onion': 'Sin cebolla, por favor',
  'how much is it': '¿Cuánto cuesta?',
  'how much': '¿Cuánto cuesta?',
  'the bill please': 'La cuenta, por favor',
  'check please': 'La cuenta, por favor',
  'can i get the bill': 'La cuenta, por favor',
  'can i get the check': 'La cuenta, por favor',
  'i don\'t understand': 'No entiendo, ¿puede repetir?',
  'i dont understand': 'No entiendo, ¿puede repetir?',
  'please speak slower': 'Hable más despacio, por favor',
  'speak slower': 'Más despacio, por favor',
  'can you repeat please': '¿Puede repetir, por favor?',
  'repeat please': '¿Puede repetir, por favor?',
  'thank you': 'Muchas gracias',
  'thanks': 'Muchas gracias',
  'where is the bathroom': '¿Dónde está el baño?',
  'bathroom': '¿Dónde está el baño?',
  'yes please': 'Sí, por favor',
  'yes': 'Sí, por favor',
  'no thank you': 'No, gracias',
  'no thanks': 'No, gracias',
  'for here or to go': '¿Para aquí o para llevar?',
  'to go': 'Para llevar',
  'take away': 'Para llevar',
  'for here': 'Para tomar aquí',
  'card': 'Con tarjeta, por favor',
  'by card': 'Con tarjeta, por favor',
  'can i pay with card': '¿Puedo pagar con tarjeta?',
  'cash': 'En efectivo, por favor',
  'what do you recommend': '¿Qué me recomienda?',
  'how are you': '¿Cómo estás?',
  'my name is': 'Me llamo...',
  'nice to meet you': 'Mucho gusto'
}

/**
 * Normalizes text for dictionary matching
 */
function normalizeText(text) {
  if (!text) return ''
  return text
    .replace(/\[offline\]\s*/i, '')
    .trim()
    .toLowerCase()
    .replace(/[¿¡.,!?;:"']/g, '')
    .replace(/\s+/g, ' ')
}

/**
 * Synchronously translates Spanish text to English using dictionary and smart heuristics
 */
export function getEnglishTranslationSync(spanishText) {
  if (!spanishText || !spanishText.trim()) return ''

  const clean = spanishText.replace(/\[offline\]\s*/i, '').trim()
  const normalized = normalizeText(clean)

  if (TRANSLATION_CACHE.has(normalized)) {
    return TRANSLATION_CACHE.get(normalized)
  }

  // Direct lookup
  if (ES_TO_EN_DICTIONARY[normalized]) {
    return ES_TO_EN_DICTIONARY[normalized]
  }

  // Check partial key matches
  for (const [key, translation] of Object.entries(ES_TO_EN_DICTIONARY)) {
    if (normalized.includes(key) || (key.length > 8 && key.includes(normalized))) {
      return translation
    }
  }

  // Smart conversational heuristics for immediate understanding
  if (normalized.includes('cafe con leche')) return 'A coffee with milk. Would you like anything else?'
  if (normalized.includes('para aqui') || normalized.includes('para llevar')) return 'Asking if it is for here or to go (takeaway).'
  if (normalized.includes('cuenta')) return 'Offering or asking about the bill/check.'
  if (normalized.includes('tarjeta') || normalized.includes('efectivo')) return 'Asking whether you want to pay with credit card or cash.'
  if (normalized.includes('tapa') || normalized.includes('beber')) return 'Asking what you want to drink and offering a house tapa.'
  if (normalized.includes('que le pongo') || normalized.includes('desea algo')) return 'Asking what they can get for you or if you need anything else.'
  if (normalized.includes('cebolla')) return 'Asking whether you prefer the Spanish omelette with or without onion.'
  if (normalized.includes('no entiendo') || normalized.includes('repetir')) return 'Asking to repeat or clarify more simply.'
  if (normalized.includes('cuanto') || normalized.includes('cuesta') || normalized.includes('euros')) return 'Stating the price in euros.'
  if (normalized.includes('buenos dias') || normalized.includes('buenas tardes')) return 'Greeting you warmly and starting the interaction.'
  if (normalized.includes('contrato') || normalized.includes('identidad')) return 'Asking for your rental contract and identity document at the counter.'
  if (normalized.includes('viaje') || normalized.includes('sorprendio')) return 'Asking you to speak about a recent trip and what surprised you most.'
  if (normalized.includes('concierto') || normalized.includes('fin de semana')) return 'Asking about your weekend plans and mentioning a concert in the park.'
  if (normalized.includes('placer') || normalized.includes('vuelve')) return 'Thanking you, saying it was a pleasure, and welcoming you back.'

  return clean
}

/**
 * Asynchronously translates Spanish to English with online fallback & cache
 */
export async function translateSpanishToEnglish(spanishText) {
  const syncResult = getEnglishTranslationSync(spanishText)
  const clean = spanishText.replace(/\[offline\]\s*/i, '').trim()
  const normalized = normalizeText(clean)

  if (syncResult && syncResult !== clean) {
    return syncResult
  }

  try {
    const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(clean)}&langpair=es|en`
    const res = await fetch(url)
    if (res.ok) {
      const data = await res.json()
      if (data?.responseData?.translatedText) {
        const result = data.responseData.translatedText
        TRANSLATION_CACHE.set(normalized, result)
        return result
      }
    }
  } catch {
    // Network fallback
  }

  return syncResult || clean
}

/**
 * Checks if user typed English instead of Spanish
 */
export function isLikelyEnglish(text) {
  if (!text || !text.trim()) return false
  const lower = text.toLowerCase()
  const englishSignals = [
    /\b(the|is|are|am|i|you|he|she|we|they|my|your|can|could|want|would|please|how|what|where|when|why|who|coffee|bill|check|water|beer|much|thanks|thank|need|give|like|take|go|here)\b/i,
    /i('m|’m|\s+want|\s+would|\s+need|\s+like)/i,
    /\b(how\s+much|where\s+is|can\s+i|give\s+me|i\s+would\s+like|i\s+want|for\s+here|to\s+go)\b/i
  ]
  const spanishSignals = [
    /[áéíóúñ¿¡]/i,
    /\b(hola|por\s+favor|gracias|quiero|café|caña|cerveza|dónde|cuánto|para|llevar|buenos|días|tardes|noches|cuenta|tapa|vale|claro|sí)\b/i
  ]

  const hasEnglish = englishSignals.some(regex => regex.test(lower))
  const hasSpanish = spanishSignals.some(regex => regex.test(lower))

  return hasEnglish && !hasSpanish
}

/**
 * Suggests Spanish translation if user typed in English
 */
export function getSpanishSuggestionFromEnglish(englishText) {
  if (!englishText) return null
  const normalized = normalizeText(englishText)

  if (EN_TO_ES_DICTIONARY[normalized]) {
    return EN_TO_ES_DICTIONARY[normalized]
  }

  for (const [key, es] of Object.entries(EN_TO_ES_DICTIONARY)) {
    if (normalized.includes(key) || (key.length > 5 && key.includes(normalized))) {
      return es
    }
  }

  // Conversational sentence transforms
  if (/^(i want|i'd like|i would like|can i get|give me)\s+(a\s+)?coffee/i.test(englishText)) {
    return 'Quiero un café, por favor'
  }
  if (/^(i want|i'd like|i would like|can i get)\s+(a\s+)?beer/i.test(englishText)) {
    return '¿Me pones una caña, por favor?'
  }
  if (/^(i want|i'd like|can i get)\s+(a\s+)?water/i.test(englishText)) {
    return 'Un vaso de agua, por favor'
  }
  if (/^(how much|what does it cost)/i.test(englishText)) {
    return '¿Cuánto cuesta?'
  }
  if (/^(bill|check|the check|the bill)/i.test(englishText)) {
    return 'La cuenta, por favor'
  }
  if (/^where is/i.test(englishText)) {
    return '¿Dónde está el baño, por favor?'
  }

  return null
}
