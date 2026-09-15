import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createProfile } from '../api'
import { saveProfile, setItem } from '../utils/storage'
import ThreeBackground from '../components/ThreeBackground'
import './Onboarding.css'

const NATIVE_LANGUAGES = [
  { code: 'en', label: 'English', flag: '🇬🇧', tip: 'Focus on pure vowels & avoiding schwas' },
  { code: 'hi', label: 'Hindi / हिन्दी', flag: '🇮🇳', tip: 'Focus on dental Spanish "t/d" and rolling "rr"' },
  { code: 'fr', label: 'French', flag: '🇫🇷', tip: 'Focus on front-of-mouth "r" & distinct "b/v"' },
  { code: 'de', label: 'German', flag: '🇩🇪', tip: 'Focus on softer consonants & Spanish cadence' },
  { code: 'zh', label: 'Mandarin', flag: '🇨🇳', tip: 'Focus on polysyllabic stress & multiconsonants' },
  { code: 'pt', label: 'Portuguese', flag: '🇵🇹', tip: 'Watch false cognates & crisp Spanish vowels' },
  { code: 'ar', label: 'Arabic', flag: '🇸🇦', tip: 'Natural affinity with Spanish "j" (jota)!' },
  { code: 'other', label: 'Other', flag: '🌐', tip: 'Adaptive phonetic calibration' },
]

const ACCENT_TARGETS = [
  { id: 'es-ES', label: 'Castilian (Spain)', desc: 'Madrid / Barcelona with distinction (ceceo/distinción)' },
  { id: 'es-LA', label: 'Latin American', desc: 'Mexico, Colombia, Argentina (seseo, melodic cadence)' },
]

const LEVELS = [
  { value: 'A1', label: 'Beginner', desc: 'I know a few basic words & phrases' },
  { value: 'A2', label: 'Elementary', desc: 'I can handle basic everyday interactions' },
  { value: 'B1', label: 'Intermediate', desc: 'I can carry spontaneous conversations' },
  { value: 'B2', label: 'Upper Intermediate', desc: 'I speak comfortably with native cadence' },
]

const PURPOSES = [
  { value: 'trip', label: '✈️ Travel & Trip', desc: 'Airports, tapas bars, hotel check-ins, local directions' },
  { value: 'casual', label: '💬 Social & Casual', desc: 'Everyday banter, meeting friends, culture, storytelling' },
  { value: 'exam', label: '🎓 DELE Exam', desc: 'Structured oral tasks, past-tense narration, formal defense' },
  { value: 'relocation', label: '🏡 Relocation', desc: 'Town hall registration (padrón), apartment lease, utilities' },
]

const INTEREST_OPTIONS = ['food', 'culture', 'music', 'sports', 'travel', 'history', 'art', 'technology', 'nature', 'nightlife']
const WEAK_AREA_OPTIONS = ['pronunciation', 'listening', 'speaking', 'grammar', 'vocabulary', 'accent confidence']

export default function Onboarding() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  // Form state
  const [nativeLanguage, setNativeLanguage] = useState('English')
  const [customNative, setCustomNative] = useState('')
  const [accentTarget, setAccentTarget] = useState('es-ES')
  const [level, setLevel] = useState('')
  const [purpose, setPurpose] = useState('')
  const [region, setRegion] = useState('Madrid')
  const [interests, setInterests] = useState([])
  const [weakAreas, setWeakAreas] = useState(['pronunciation'])

  const toggleItem = (list, setList, item) => {
    setList(prev => prev.includes(item) ? prev.filter(i => i !== item) : [...prev, item])
  }

  const effectiveNative = nativeLanguage === 'Other' && customNative.trim() ? customNative.trim() : nativeLanguage

  const canNext = () => {
    if (step === 1) return !!effectiveNative
    if (step === 2) return !!level
    if (step === 3) return !!purpose && region.trim().length > 0
    if (step === 4) return interests.length > 0
    return false
  }

  const handleSubmit = async () => {
    setLoading(true)
    setError(null)

    const learnerId = 'web_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 6)
    const apiWeakAreas = weakAreas.map(w => w === 'pronunciation' ? 'speaking' : w)

    // Backend contract strictly supports 'trip' | 'casual'; map preview purposes for live backend
    const backendPurpose = (purpose === 'exam' || purpose === 'casual') ? 'casual' : 'trip'

    const { ok, status, data } = await createProfile({
      learnerId,
      level,
      purpose: backendPurpose,
      region: region.trim(),
      interests,
      weakAreas: apiWeakAreas.length > 0 ? apiWeakAreas : ['listening'],
    })

    setLoading(false)

    if (!ok) {
      if (status === 422 && data?.detail?.[0]?.msg) {
        setError(data.detail[0].msg)
      } else {
        setError(data?.detail || 'Something went wrong connecting to backend. Please retry.')
      }
      return
    }

    // Persist learner configuration into localStorage (survives restart) + sessionStorage
    saveProfile({
      learner_id: learnerId,
      language: 'spanish',
      level,
      purpose, // keeps actual chosen purpose (trip, casual, exam, relocation)
      region: region.trim(),
      interests,
      weak_areas: apiWeakAreas,
      native_language: effectiveNative,
      accent_target: accentTarget,
    })

    setItem('accent_target', accentTarget)
    setItem('weak_areas', JSON.stringify(weakAreas))

    navigate('/scenario')
  }

  return (
    <div className="onboarding-page">
      <ThreeBackground variant="subtle" />

      <div className="container onboarding-container">
        {/* Header with back navigation & steps */}
        <header className="onboarding-header animate-in">
          <button className="btn btn-ghost" onClick={() => step > 1 ? setStep(step - 1) : navigate('/')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
            {step > 1 ? 'Back' : 'Home'}
          </button>
          <div className="onboarding-progress">
            <div className="onboarding-step-label font-mono text-secondary">
              Step {step} of 4 · {step === 1 ? 'Language & Accent' : step === 2 ? 'Proficiency' : step === 3 ? 'Goal & City' : 'Interests'}
            </div>
            <div className="progress-track" style={{ width: '180px' }}>
              <div className="progress-fill" style={{ width: `${(step / 4) * 100}%` }} />
            </div>
          </div>
        </header>

        {/* STEP 1: Native Language & Target Dialect */}
        {step === 1 && (
          <div className="onboarding-step animate-in-up" key="step1">
            <div className="step-badge font-mono">PHONETIC CALIBRATION</div>
            <h2>What is your native language?</h2>
            <p className="text-secondary" style={{ marginBottom: '24px' }}>
              We customize speech recognition and pronunciation scoring based on your native phonetic habits.
            </p>

            <div className="native-grid stagger">
              {NATIVE_LANGUAGES.map(lang => (
                <button
                  type="button"
                  key={lang.code}
                  className={`native-card card ${nativeLanguage === lang.label ? 'active' : ''}`}
                  onClick={() => setNativeLanguage(lang.label)}
                >
                  <div className="native-flag">{lang.flag}</div>
                  <div className="native-info">
                    <div className="native-name">{lang.label}</div>
                    <div className="native-tip text-muted">{lang.tip}</div>
                  </div>
                </button>
              ))}
            </div>

            {nativeLanguage === 'Other' && (
              <div className="custom-native-field animate-in" style={{ marginTop: '16px' }}>
                <input
                  className="input"
                  type="text"
                  placeholder="Enter your native language (e.g. Japanese, Russian, Italian)"
                  value={customNative}
                  onChange={e => setCustomNative(e.target.value)}
                />
              </div>
            )}

            <div className="accent-target-section" style={{ marginTop: '32px' }}>
              <h3>Target Spanish Dialect</h3>
              <p className="text-secondary" style={{ fontSize: '0.88rem', marginBottom: '14px' }}>
                Choose which regional pronunciation voice to prioritize:
              </p>
              <div className="accent-target-grid">
                {ACCENT_TARGETS.map(acc => (
                  <button
                    type="button"
                    key={acc.id}
                    className={`accent-target-card card ${accentTarget === acc.id ? 'active' : ''}`}
                    onClick={() => setAccentTarget(acc.id)}
                  >
                    <div className="accent-name">{acc.label}</div>
                    <div className="accent-desc text-muted">{acc.desc}</div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* STEP 2: Spanish Level */}
        {step === 2 && (
          <div className="onboarding-step animate-in-up" key="step2">
            <div className="step-badge font-mono">ADAPTIVE DIFFICULTY</div>
            <h2>What's your current Spanish level?</h2>
            <p className="text-secondary" style={{ marginBottom: '28px' }}>
              The AI dynamically calibrates sentence speed, lexical complexity, and grammar expectations.
            </p>
            <div className="level-grid stagger">
              {LEVELS.map(l => (
                <button
                  type="button"
                  key={l.value}
                  className={`level-card card ${level === l.value ? 'level-card-active' : ''}`}
                  onClick={() => setLevel(l.value)}
                >
                  <div className="level-code font-mono">{l.value}</div>
                  <div className="level-label">{l.label}</div>
                  <div className="level-desc text-secondary">{l.desc}</div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* STEP 3: Purpose + Region */}
        {step === 3 && (
          <div className="onboarding-step animate-in-up" key="step3">
            <div className="step-badge font-mono">SITUATIONAL SCOPE</div>
            <h2>Why are you practicing Spanish?</h2>
            <p className="text-secondary" style={{ marginBottom: '28px' }}>
              Your purpose shapes the real-world scenarios, conversational register, and readiness scoring.
            </p>
            <div className="purpose-grid stagger">
              {PURPOSES.map(p => (
                <button
                  type="button"
                  key={p.value}
                  className={`purpose-card card ${purpose === p.value ? 'purpose-card-active' : ''}`}
                  onClick={() => setPurpose(p.value)}
                >
                  <div className="purpose-emoji">{p.label.split(' ')[0]}</div>
                  <div className="purpose-label">{p.label.split(' ').slice(1).join(' ')}</div>
                  <div className="purpose-desc text-secondary">{p.desc}</div>
                </button>
              ))}
            </div>

            <div className="region-field" style={{ marginTop: '28px' }}>
              <label>Target City or Region</label>
              <input
                className="input"
                type="text"
                value={region}
                onChange={e => setRegion(e.target.value)}
                placeholder="e.g. Madrid, Barcelona, Mexico City, Medellín"
              />
              <span className="text-muted font-mono" style={{ fontSize: '0.75rem', marginTop: '6px', display: 'block' }}>
                Used by the scenario generator to localize context and colloquialisms.
              </span>
            </div>
          </div>
        )}

        {/* STEP 4: Interests + Weak areas */}
        {step === 4 && (
          <div className="onboarding-step animate-in-up" key="step4">
            <div className="step-badge font-mono">PERSONALIZATION</div>
            <h2>What topics excite you?</h2>
            <p className="text-secondary" style={{ marginBottom: '20px' }}>
              Select your interests so scenarios match things you actually care about.
            </p>
            <div className="chip-group">
              {INTEREST_OPTIONS.map(item => (
                <button
                  type="button"
                  key={item}
                  className={`chip ${interests.includes(item) ? 'chip-active' : ''}`}
                  onClick={() => toggleItem(interests, setInterests, item)}
                >
                  {item}
                </button>
              ))}
            </div>

            <h3 style={{ marginTop: '36px', marginBottom: '8px' }}>Skills & Weak Areas to Hone</h3>
            <p className="text-secondary" style={{ marginBottom: '16px', fontSize: '0.9rem' }}>
              The AI will offer inline repairs and audio pronunciation focus on these areas.
            </p>
            <div className="chip-group">
              {WEAK_AREA_OPTIONS.map(item => (
                <button
                  type="button"
                  key={item}
                  className={`chip chip-gold ${weakAreas.includes(item) ? 'chip-active' : ''}`}
                  onClick={() => toggleItem(weakAreas, setWeakAreas, item)}
                >
                  {item === 'pronunciation' ? '🔊 Pronunciation & Accent' : item}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Error Notification */}
        {error && (
          <div className="onboarding-error animate-in">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--error)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="onboarding-actions animate-in">
          {step < 4 ? (
            <button
              className="btn btn-primary btn-lg"
              disabled={!canNext()}
              onClick={() => setStep(step + 1)}
            >
              Next Step
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </button>
          ) : (
            <button
              className="btn btn-primary btn-lg"
              disabled={!canNext() || loading}
              onClick={handleSubmit}
            >
              {loading ? (
                <>
                  <span className="spinner spinner-sm" />
                  Generating AI Profile & Neural Voice…
                </>
              ) : (
                'Start Immersion Scenario'
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
