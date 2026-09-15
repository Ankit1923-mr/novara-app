import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { checkHealth } from '../api'
import { speakSpanish, stopSpeaking } from '../utils/audio'
import ThreeBackground from '../components/ThreeBackground'
import './Welcome.css'

export default function Welcome() {
  const navigate = useNavigate()
  const [status, setStatus] = useState('pending') // pending | up | down
  const [isPlayingDemo, setIsPlayingDemo] = useState(false)
  const [speechRate, setSpeechRate] = useState(1.0)

  useEffect(() => {
    checkHealth().then(ok => setStatus(ok ? 'up' : 'down'))
    return () => stopSpeaking()
  }, [])

  const handleDemoAudio = (e) => {
    e.stopPropagation()
    if (isPlayingDemo) {
      stopSpeaking()
      setIsPlayingDemo(false)
    } else {
      setIsPlayingDemo(true)
      speakSpanish('¡Hola! Bienvenido a Novara. Vamos a practicar español con pronunciación real.', {
        rate: speechRate,
        onEnd: () => setIsPlayingDemo(false),
        onError: () => setIsPlayingDemo(false),
      })
    }
  }

  return (
    <div className="welcome-page">
      {/* 3D Interactive Three.js Neural Constellation Background */}
      <ThreeBackground variant="hero" />

      {/* Subtle overlay gradient */}
      <div className="welcome-overlay" />

      <div className="welcome-content animate-in">
        <div className="welcome-eyebrow font-mono">
          <span className="welcome-flag">🇪🇸</span>
          <span>Next-Gen Language Immersion</span>
          <span className="welcome-badge">3D & Audio Powered</span>
        </div>

        <h1 className="welcome-title">
          <span className="welcome-title-n">N</span>OVARA
        </h1>

        <p className="welcome-subtitle">
          Master spoken Spanish through dynamic situational conversations. Adaptive AI partner, instant speech pronunciation, accent readiness analysis, and smart real-time feedback.
        </p>

        {/* Pronunciation Preview Card */}
        <div className="welcome-audio-preview card">
          <div className="preview-header">
            <span className="preview-tag font-mono">AUDIO ENGINE READY</span>
            <div className="rate-toggle">
              <button
                type="button"
                className={`rate-btn ${speechRate === 0.8 ? 'active' : ''}`}
                onClick={() => setSpeechRate(0.8)}
              >
                0.8x Slow
              </button>
              <button
                type="button"
                className={`rate-btn ${speechRate === 1.0 ? 'active' : ''}`}
                onClick={() => setSpeechRate(1.0)}
              >
                1.0x Normal
              </button>
            </div>
          </div>
          <div className="preview-body">
            <button
              type="button"
              className={`preview-speaker-btn ${isPlayingDemo ? 'speaking' : ''}`}
              onClick={handleDemoAudio}
              title="Click to hear native pronunciation"
              aria-label="Play pronunciation audio"
            >
              {isPlayingDemo ? (
                <div className="sound-wave">
                  <span />
                  <span />
                  <span />
                  <span />
                </div>
              ) : (
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                  <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
                  <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
                </svg>
              )}
            </button>
            <div className="preview-text-col">
              <div className="preview-sample">"¡Hola! Bienvenido a Novara."</div>
              <div className="preview-meta font-mono text-secondary">
                Castilian & Latin Accent · Click speaker to preview audio
              </div>
            </div>
          </div>
        </div>

        {/* Primary CTA */}
        <div className="welcome-cta-group">
          <button
            className="btn btn-primary btn-lg welcome-cta"
            onClick={() => navigate('/onboarding')}
          >
            Start Learning Experience
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </button>
        </div>

        {/* Live Backend Indicator */}
        <div className="welcome-status font-mono">
          <span className={`status-dot ${status}`} />
          <span className="text-secondary">
            {status === 'pending' && 'Connecting to Novara API…'}
            {status === 'up' && 'Live Neural Backend Connected'}
            {status === 'down' && 'Backend waking up (free tier spins up in ~35s)'}
          </span>
        </div>

        {/* Stats */}
        <div className="welcome-stats stagger">
          <div className="welcome-stat card">
            <div className="welcome-stat-n">6</div>
            <div className="welcome-stat-l">AI Engines</div>
          </div>
          <div className="welcome-stat card">
            <div className="welcome-stat-n">3D</div>
            <div className="welcome-stat-l">Three.js Visuals</div>
          </div>
          <div className="welcome-stat card">
            <div className="welcome-stat-n">TTS</div>
            <div className="welcome-stat-l">Speech Pronunciation</div>
          </div>
          <div className="welcome-stat card">
            <div className="welcome-stat-n">4</div>
            <div className="welcome-stat-l">Readiness Vectors</div>
          </div>
        </div>
      </div>

      <footer className="welcome-footer font-mono text-muted">
        <span>B.Tech Final Year Project · Ankit & Sakshi</span>
        <span className="footer-divider">•</span>
        <span>Interactive 3D Speech Simulator</span>
      </footer>
    </div>
  )
}
