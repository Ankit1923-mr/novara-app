import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getScenario } from '../api'
import { speakSpanish, stopSpeaking } from '../utils/audio'
import { getItem, setItem } from '../utils/storage'
import { MOCK_SCENARIOS } from '../utils/mockData'
import ThreeBackground from '../components/ThreeBackground'
import './Scenario.css'

export default function Scenario() {
  const navigate = useNavigate()
  const [scenario, setScenario] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [isPlayingOpening, setIsPlayingOpening] = useState(false)
  const [useMock, setUseMock] = useState(() => getItem('use_mock') === 'true')

  const learnerId = getItem('learner_id')
  const nativeLanguage = getItem('native_language', 'English')
  const [selectedPurpose, setSelectedPurpose] = useState(() => getItem('purpose', 'trip'))

  const loadScenarioData = async (mockMode, purposeKey) => {
    setLoading(true)
    setError(null)

    if (mockMode || purposeKey === 'exam' || purposeKey === 'relocation') {
      const mock = MOCK_SCENARIOS[purposeKey] || MOCK_SCENARIOS.trip
      setScenario(mock)
      setItem('scenario', JSON.stringify(mock))
      setItem('use_mock', mockMode ? 'true' : 'false')
      setLoading(false)
      return
    }

    // Live backend call
    const { ok, status, data } = await getScenario(learnerId)
    setLoading(false)

    if (!ok) {
      if (status === 404) {
        setError('Profile not found. Please create a profile first.')
        setTimeout(() => navigate('/onboarding'), 2000)
      } else {
        // Fallback to mock with clear prompt
        setError(data?.detail || 'Failed to load scenario from live backend. Switching to mock scenario.')
        const fallback = MOCK_SCENARIOS[purposeKey] || MOCK_SCENARIOS.trip
        setScenario(fallback)
        setItem('scenario', JSON.stringify(fallback))
      }
      return
    }

    setScenario(data)
    setItem('scenario', JSON.stringify(data))
    setItem('use_mock', 'false')
  }

  useEffect(() => {
    if (!learnerId) {
      navigate('/onboarding')
      return
    }
    loadScenarioData(useMock, selectedPurpose)
    return () => stopSpeaking()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handlePurposeChange = (purp) => {
    setSelectedPurpose(purp)
    setItem('purpose', purp)
    loadScenarioData(useMock, purp)
  }

  const handleToggleMock = () => {
    const nextMock = !useMock
    setUseMock(nextMock)
    setItem('use_mock', nextMock ? 'true' : 'false')
    loadScenarioData(nextMock, selectedPurpose)
  }

  const handlePlayOpening = (e) => {
    e.stopPropagation()
    if (!scenario?.opening_line) return

    if (isPlayingOpening) {
      stopSpeaking()
      setIsPlayingOpening(false)
    } else {
      setIsPlayingOpening(true)
      speakSpanish(scenario.opening_line, {
        rate: 0.95,
        onEnd: () => setIsPlayingOpening(false),
        onError: () => setIsPlayingOpening(false),
      })
    }
  }

  if (loading) {
    return (
      <div className="scenario-page">
        <ThreeBackground variant="subtle" />
        <div className="container scenario-container">
          <div className="scenario-loading animate-in card">
            <div className="spinner" style={{ width: '36px', height: '36px' }} />
            <h2>Synthesizing Immersive Scenario…</h2>
            <p className="text-secondary">
              Personalizing dialogue context, colloquialisms, and difficulty for your profile.
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="scenario-page">
      <ThreeBackground variant="subtle" />

      <div className="container scenario-container">
        <header className="scenario-header animate-in">
          <button className="btn btn-ghost" onClick={() => navigate('/onboarding')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
            Edit Profile
          </button>
          <div className="scenario-header-pills">
            {/* Mock vs Live Toggle */}
            <button
              type="button"
              className={`pill font-mono ${useMock ? 'pill-accent' : 'pill-outline'}`}
              onClick={handleToggleMock}
              title="Click to toggle between Mock and Live backend scenario"
              style={{ cursor: 'pointer' }}
            >
              {useMock ? '⚡ MOCK MODE' : '🟢 LIVE BACKEND'}
            </button>
            <span className="pill pill-outline font-mono">
              Native: {nativeLanguage}
            </span>
          </div>
        </header>

        {/* Purpose Selector Chips (Task 4: 4 purposes test) */}
        <div className="scenario-purpose-selector animate-in">
          <span className="font-mono text-secondary" style={{ fontSize: '0.75rem', marginRight: '8px' }}>
            PURPOSE (TASK 4):
          </span>
          {['trip', 'casual', 'exam', 'relocation'].map(p => (
            <button
              type="button"
              key={p}
              className={`purpose-chip ${selectedPurpose === p ? 'active' : ''}`}
              onClick={() => handlePurposeChange(p)}
            >
              {p === 'trip' ? '✈️ Trip' : p === 'casual' ? '💬 Casual' : p === 'exam' ? '🎓 Exam' : '🏡 Relocation'}
            </button>
          ))}
        </div>

        {error && (
          <div className="scenario-notice animate-in" style={{ margin: '14px 0', padding: '10px 14px', background: 'rgba(245, 158, 11, 0.1)', border: '1px solid rgba(245, 158, 11, 0.3)', borderRadius: '8px', fontSize: '0.82rem', color: 'var(--gold)' }}>
            ℹ️ {error}
          </div>
        )}

        <div className="scenario-content animate-in-up">
          <div className="scenario-badge font-mono">
            {useMock ? 'MOCK SCENARIO LOADED' : 'LIVE BACKEND SCENARIO'}
          </div>
          <h1 className="scenario-title">{scenario?.title}</h1>
          <p className="scenario-setting">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/>
            </svg>
            <span>{scenario?.setting}</span>
          </p>

          <div className="scenario-tags">
            {scenario?.situation_tags?.map(tag => (
              <span key={tag} className="pill pill-outline font-mono">#{tag}</span>
            ))}
          </div>

          {/* Opening Line Card with Audio Speaker */}
          <div className="scenario-opening card">
            <div className="scenario-opening-header">
              <div className="scenario-opening-label font-mono text-muted">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                AI PARTNER OPENING LINE
              </div>

              <button
                type="button"
                className={`scenario-speaker-btn ${isPlayingOpening ? 'active' : ''}`}
                onClick={handlePlayOpening}
                title="Hear opening line pronunciation"
              >
                {isPlayingOpening ? (
                  <div className="sound-wave">
                    <span /><span /><span />
                  </div>
                ) : (
                  <>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                      <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
                    </svg>
                    <span>Hear Audio</span>
                  </>
                )}
              </button>
            </div>

            <p className="scenario-opening-text">"{scenario?.opening_line}"</p>
          </div>

          <div className="scenario-actions stagger">
            <button className="btn btn-primary btn-lg" onClick={() => navigate('/chat')}>
              Begin Conversation
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </button>
            <button className="btn btn-secondary" onClick={() => loadScenarioData(useMock, selectedPurpose)}>
              Refresh Scenario
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
