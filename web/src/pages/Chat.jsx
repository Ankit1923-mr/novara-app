import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { sendMessage } from '../api'
import { speakSpanish, stopSpeaking, createSpeechRecognizer, analyzeAccentAndPhonetics } from '../utils/audio'
import { getItem, setItem } from '../utils/storage'
import { getMockConversationReply } from '../utils/mockData'
import { getEnglishTranslationSync, translateSpanishToEnglish, isLikelyEnglish, getSpanishSuggestionFromEnglish } from '../utils/translator'
import ThreeBackground from '../components/ThreeBackground'
import './Chat.css'

export default function Chat() {
  const navigate = useNavigate()
  const chatEndRef = useRef(null)
  const inputRef = useRef(null)
  const turnStartRef = useRef(null)
  const speechRecognizerRef = useRef(null)

  const learnerId = getItem('learner_id')
  const scenarioData = getItem('scenario')
  const nativeLanguage = getItem('native_language', 'English')
  const [useMock, setUseMock] = useState(() => getItem('use_mock') === 'true')
  const [showEnglish, setShowEnglish] = useState(true)

  const [scenario] = useState(() => {
    if (!scenarioData) return null
    try { return JSON.parse(scenarioData) } catch { return null }
  })

  const [messages, setMessages] = useState(() => {
    if (!scenarioData) return []
    try {
      const parsed = JSON.parse(scenarioData)
      return [{
        id: 'opening',
        role: 'assistant',
        text: parsed.opening_line,
        translation: getEnglishTranslationSync(parsed.opening_line),
        timestamp: new Date(),
      }]
    } catch { return [] }
  })

  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [turnNumber, setTurnNumber] = useState(0)

  // Audio / Speech states
  const [speakingId, setSpeakingId] = useState(null)
  const [speechRate, setSpeechRate] = useState(1.0)
  const [isListening, setIsListening] = useState(false)
  const [selectedScoreCard, setSelectedScoreCard] = useState(null)

  useEffect(() => {
    if (!learnerId || !scenarioData) {
      navigate('/onboarding')
      return
    }

    turnStartRef.current = Date.now()

    return () => {
      stopSpeaking()
      if (speechRecognizerRef.current) {
        try { speechRecognizerRef.current.stop() } catch {}
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Play audio for a given message
  const handlePlayAudio = (id, text, e) => {
    e?.stopPropagation()
    if (speakingId === id) {
      stopSpeaking()
      setSpeakingId(null)
    } else {
      setSpeakingId(id)
      speakSpanish(text, {
        rate: speechRate,
        onEnd: () => setSpeakingId(null),
        onError: () => setSpeakingId(null),
      })
    }
  }

  // Handle Speech-to-Text Microphone
  const toggleSpeechRecognition = () => {
    if (isListening) {
      if (speechRecognizerRef.current) {
        try { speechRecognizerRef.current.stop() } catch {}
      }
      setIsListening(false)
      return
    }

    const recognizer = createSpeechRecognizer({
      onStart: () => setIsListening(true),
      onEnd: () => setIsListening(false),
      onError: () => setIsListening(false),
      onResult: ({ finalTranscript, interimTranscript }) => {
        const text = finalTranscript || interimTranscript
        if (text) {
          setInput(prev => {
            const base = prev.trim()
            return base ? `${base} ${text}` : text
          })
        }
      }
    })

    if (!recognizer) {
      alert('Speech recognition is not supported in this browser. Please use Chrome/Edge or type directly.')
      return
    }

    speechRecognizerRef.current = recognizer
    try {
      recognizer.start()
    } catch {
      setIsListening(false)
    }
  }

  const handleSend = async () => {
    const text = input.trim()
    const userWroteEnglish = isLikelyEnglish(text)
    const spanishText = userWroteEnglish ? (getSpanishSuggestionFromEnglish(text) || text) : text

    // Calculate response time
    const responseTimeMs = turnStartRef.current ? Date.now() - turnStartRef.current : undefined

    // Analyze learner accent & pronunciation
    const phoneticAnalysis = analyzeAccentAndPhonetics(spanishText, nativeLanguage)

    // Add user message with pronunciation & accent metrics + English source note if applicable
    const userMsg = {
      id: 'u_' + Date.now(),
      role: 'user',
      text: spanishText,
      originalEnglish: userWroteEnglish ? text : null,
      timestamp: new Date(),
      phonetics: phoneticAnalysis,
    }

    setMessages(prev => [...prev, userMsg])
    setInput('')
    setSending(true)

    const newTurn = turnNumber + 1
    setTurnNumber(newTurn)

    let replyData = null

    if (useMock) {
      // Simulate natural thinking delay
      await new Promise(r => setTimeout(r, 600))
      replyData = getMockConversationReply(newTurn, spanishText)
    } else {
      const { ok, data } = await sendMessage({
        learnerId,
        scenarioId: scenario.scenario_id,
        message: spanishText,
        turnNumber: newTurn,
        responseTimeMs,
      })

      if (ok && data) {
        replyData = data
      } else {
        // Fallback to mock reply so test continues seamlessly
        console.warn('Backend unavailable, using mock response:', data?.detail || data?.error)
        replyData = getMockConversationReply(newTurn, spanishText)
      }
    }

    setSending(false)
    turnStartRef.current = Date.now()

    if (!replyData) {
      setMessages(prev => [...prev, {
        id: 'err_' + Date.now(),
        role: 'system',
        text: 'Something went wrong. Try again.',
        timestamp: new Date(),
      }])
      return
    }

    // Add AI reply with English translation
    const replyId = 'a_' + Date.now()
    const aiTranslation = getEnglishTranslationSync(replyData.reply)
    setMessages(prev => [...prev, {
      id: replyId,
      role: 'assistant',
      text: replyData.reply,
      translation: aiTranslation,
      timestamp: new Date(),
    }])

    // Auto-speak AI response if desired
    handlePlayAudio(replyId, replyData.reply)

    // Asynchronously enhance translation if not exact match
    translateSpanishToEnglish(replyData.reply).then(asyncTrans => {
      if (asyncTrans && asyncTrans !== aiTranslation) {
        setMessages(prev => prev.map(m => m.id === replyId ? { ...m, translation: asyncTrans } : m))
      }
    }).catch(() => {})

    // Add repair if triggered with English explanation (Task 5: inline repair bubble)
    if (replyData.repair_triggered && replyData.repair) {
      const repId = 'r_' + Date.now()
      const repairTranslation = getEnglishTranslationSync(replyData.repair.repair_text)
      setMessages(prev => [...prev, {
        id: repId,
        role: 'repair',
        text: replyData.repair.repair_text,
        translation: repairTranslation,
        errorType: replyData.repair.error_type,
        strategy: replyData.repair.strategy,
        timestamp: new Date(),
      }])
      translateSpanishToEnglish(replyData.repair.repair_text).then(asyncTrans => {
        if (asyncTrans && asyncTrans !== repairTranslation) {
          setMessages(prev => prev.map(m => m.id === repId ? { ...m, translation: asyncTrans } : m))
        }
      }).catch(() => {})
    }

    inputRef.current?.focus()
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="chat-page">
      <ThreeBackground variant="subtle" />

      {/* Header */}
      <header className="chat-header">
        <div className="chat-header-left">
          <button className="btn btn-ghost chat-back-btn" onClick={() => navigate('/scenario')}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
          </button>
          <div className="chat-header-info">
            <div className="chat-header-title">{scenario?.title || 'Interactive Immersion'}</div>
            <div className="chat-header-sub font-mono text-muted">
              {scenario?.setting} · Turn {turnNumber}/10 · Native: {nativeLanguage}
            </div>
          </div>
        </div>

        <div className="chat-header-actions">
          {/* English Subtitles Toggle */}
          <button
            type="button"
            className={`rate-chip font-mono ${showEnglish ? 'active' : ''}`}
            onClick={() => setShowEnglish(!showEnglish)}
            title="Toggle English translation under Spanish responses"
          >
            🇬🇧 {showEnglish ? 'ENG SUB: ON' : 'ENG SUB: OFF'}
          </button>

          {/* Mock vs Live Toggle */}
          <button
            type="button"
            className={`rate-chip font-mono ${useMock ? 'active' : ''}`}
            onClick={() => {
              const next = !useMock
              setUseMock(next)
              setItem('use_mock', next ? 'true' : 'false')
            }}
            title="Toggle between Mock and Live backend"
          >
            {useMock ? '⚡ MOCK' : '🟢 LIVE'}
          </button>

          {/* Pronunciation Rate Selector */}
          <div className="chat-rate-toggle font-mono">
            <button
              type="button"
              className={`rate-chip ${speechRate === 0.8 ? 'active' : ''}`}
              onClick={() => setSpeechRate(0.8)}
              title="Slow speed for practicing pronunciation"
            >
              0.8x Slow
            </button>
            <button
              type="button"
              className={`rate-chip ${speechRate === 1.0 ? 'active' : ''}`}
              onClick={() => setSpeechRate(1.0)}
              title="Normal native speed"
            >
              1.0x Normal
            </button>
          </div>

          <button
            className="btn btn-primary chat-score-btn"
            onClick={() => navigate('/readiness')}
            disabled={turnNumber < 1}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
            Readiness
          </button>
        </div>
      </header>

      {/* Messages Scroll Area */}
      <div className="chat-messages">
        <div className="chat-messages-inner">
          {/* Scenario Context Badge */}
          <div className="chat-context animate-in">
            <div className="chat-context-pill font-mono">
              {scenario?.situation_tags?.map(t => (
                <span key={t} className="pill pill-outline" style={{ fontSize: '0.68rem' }}>#{t}</span>
              ))}
              <span className="pill pill-accent" style={{ fontSize: '0.68rem' }}>TTS Pronunciation Enabled</span>
            </div>
            <p className="text-muted" style={{ fontSize: '0.8rem', textAlign: 'center', marginTop: '8px' }}>
              Speak or type in Spanish. Click 🔊 on any message to hear native pronunciation.
            </p>
          </div>

          {/* Message List */}
          {messages.map(msg => (
            <div key={msg.id} className={`chat-bubble chat-bubble-${msg.role} animate-in`}>
              {msg.role === 'assistant' && (
                <div className="chat-avatar">
                  <span>🇪🇸</span>
                </div>
              )}

              <div className={`chat-bubble-content ${msg.role === 'repair' ? 'chat-repair' : ''}`}>
                {/* Repair Card Header */}
                {msg.role === 'repair' && (
                  <div className="chat-repair-header font-mono">
                    <div className="chat-repair-badge">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                      </svg>
                      <span>INLINE CORRECTION: {msg.errorType?.toUpperCase()}</span>
                    </div>
                    <span className="chat-repair-strategy font-mono">Strategy: {msg.strategy}</span>
                  </div>
                )}

                <div className="chat-text-row">
                  <p className="chat-text-body">{msg.text}</p>

                  {/* Speaker Button on Assistant or Repair */}
                  {(msg.role === 'assistant' || msg.role === 'repair') && (
                    <button
                      type="button"
                      className={`bubble-speaker-btn ${speakingId === msg.id ? 'active' : ''}`}
                      onClick={(e) => handlePlayAudio(msg.id, msg.text, e)}
                      title="Hear native Spanish pronunciation"
                      aria-label="Hear pronunciation"
                    >
                      {speakingId === msg.id ? (
                        <div className="bubble-soundwave">
                          <span /><span /><span />
                        </div>
                      ) : (
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                          <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
                          <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
                        </svg>
                      )}
                    </button>
                  )}
                </div>

                {/* English Subtitle Box directly underneath Spanish */}
                {showEnglish && (msg.role === 'assistant' || msg.role === 'repair') && (
                  <div className="chat-translation-box animate-in">
                    <div className="translation-tag font-mono">
                      <span>🇬🇧 English Meaning:</span>
                    </div>
                    <p className="translation-text">
                      {msg.translation || getEnglishTranslationSync(msg.text)}
                    </p>
                  </div>
                )}

                {/* User original English note if learner typed in English */}
                {msg.role === 'user' && msg.originalEnglish && (
                  <div className="user-english-source font-mono animate-in">
                    <span className="user-english-tag">🇬🇧 You wrote in English:</span>
                    <span className="user-english-text">"{msg.originalEnglish}"</span>
                  </div>
                )}

                {/* Accent & Phonetic Score for User turns */}
                {msg.role === 'user' && msg.phonetics && (
                  <div className="chat-accent-meta">
                    <button
                      type="button"
                      className="accent-score-pill"
                      onClick={() => setSelectedScoreCard(selectedScoreCard === msg.id ? null : msg.id)}
                      title="Click to see pronunciation breakdown"
                    >
                      <span className="accent-score-num">{msg.phonetics.score}%</span>
                      <span className="accent-score-label">Accent Score</span>
                      <span className="accent-score-rating font-mono">· {msg.phonetics.rating}</span>
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <polyline points="6 9 12 15 18 9" />
                      </svg>
                    </button>

                    {/* Speaker to hear self-practice */}
                    <button
                      type="button"
                      className={`bubble-speaker-btn-user ${speakingId === msg.id ? 'active' : ''}`}
                      onClick={(e) => handlePlayAudio(msg.id, msg.text, e)}
                      title="Hear correct pronunciation of your sentence"
                    >
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                        <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
                      </svg>
                    </button>

                    {/* Expandable Accent Analysis Card */}
                    {selectedScoreCard === msg.id && (
                      <div className="accent-details-card animate-in-up card">
                        <div className="accent-details-row">
                          <div className="metric">
                            <span className="metric-label font-mono">Phoneme Match</span>
                            <span className="metric-val">{msg.phonetics.phonemeAccuracy}%</span>
                          </div>
                          <div className="metric">
                            <span className="metric-label font-mono">Fluency Cadence</span>
                            <span className="metric-val">{msg.phonetics.fluencyScore}%</span>
                          </div>
                        </div>

                        {msg.phonetics.strengths?.length > 0 && (
                          <div className="accent-strength">
                            <span className="font-mono text-secondary">Identified Strength:</span> {msg.phonetics.strengths[0]}
                          </div>
                        )}

                        {msg.phonetics.tips?.length > 0 && (
                          <div className="accent-tip">
                            <span className="tip-badge font-mono">TIP FOR {nativeLanguage.toUpperCase()} SPEAKERS:</span>
                            <p>{msg.phonetics.tips[0]}</p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}

          {/* Typing indicator */}
          {sending && (
            <div className="chat-bubble chat-bubble-assistant animate-in">
              <div className="chat-avatar"><span>🇪🇸</span></div>
              <div className="chat-bubble-content">
                <div className="chat-typing">
                  <span /><span /><span />
                </div>
              </div>
            </div>
          )}

          <div ref={chatEndRef} />
        </div>
      </div>

      {/* English Detection Helper Chip */}
      {isLikelyEnglish(input) && (
        <div className="chat-english-helper animate-in">
          <div className="chat-english-helper-content">
            <span className="helper-label">💡 English detected. In Spanish you can say:</span>
            <button
              type="button"
              className="helper-chip-btn"
              onClick={() => {
                const suggestion = getSpanishSuggestionFromEnglish(input)
                if (suggestion) setInput(suggestion)
              }}
              title="Click to use Spanish suggestion"
            >
              <span className="helper-suggestion-text">
                "{getSpanishSuggestionFromEnglish(input) || input}"
              </span>
              <span className="helper-chip-action font-mono">Click to use ➔</span>
            </button>
          </div>
        </div>
      )}

      {/* Input Bar */}
      <div className="chat-input-bar">
        <div className="chat-input-inner">
          {/* Microphone Speech Recognition Button */}
          <button
            type="button"
            className={`chat-mic-btn ${isListening ? 'listening' : ''}`}
            onClick={toggleSpeechRecognition}
            title={isListening ? 'Listening… click to stop' : 'Click to speak in Spanish'}
            aria-label="Speech to text"
          >
            {isListening ? (
              <div className="mic-pulse">
                <span />
              </div>
            ) : (
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            )}
          </button>

          <input
            ref={inputRef}
            className="chat-input"
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={isListening ? 'Hablando… (escuchando)' : 'Escribe en español o inglés… (Type in Spanish or English)'}
            disabled={sending}
            autoFocus
          />

          {/* Pronunciation Practice Preview Button */}
          {input.trim() && (
            <button
              type="button"
              className="chat-preview-audio-btn"
              onClick={(e) => handlePlayAudio('preview', input, e)}
              title="Hear how to pronounce this before sending"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
              </svg>
            </button>
          )}

          <button
            className="chat-send-btn"
            onClick={handleSend}
            disabled={!input.trim() || sending}
            aria-label="Send message"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  )
}
