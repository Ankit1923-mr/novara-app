import { useState } from 'react'
import { speakSpanish } from '../utils/audio'
import './SceneHUD.css'

const SCENE_REGISTRY = {
  airport: {
    id: 'airport',
    title: 'Arriving at the Airport',
    location: 'Aeropuerto Adolfo Suárez Madrid-Barajas · Terminal 4',
    image: '/scenes/airport.jpg',
    badge: '✈️ Airport Visual Anchor',
    cameraAngle: 'Carousel 18 · Arrival Concourse',
    hotspots: [
      { es: 'Recogida de equipajes', en: 'Baggage claim carousel', phonetics: '[re-ko-XI-da de e-ki-PA-xes]' },
      { es: '¿Dónde está la cinta de mi vuelo?', en: 'Where is the belt for my flight?', phonetics: '[DON-de es-TA la THEEN-ta...]' },
      { es: 'Salidas y llegadas', en: 'Departures and arrivals', phonetics: '[sa-LI-das i ye-GA-das]' },
      { es: 'Control de pasaportes', en: 'Passport control / customs', phonetics: '[kon-TROL de pa-sa-POR-tes]' },
      { es: 'Parada de taxis al centro', en: 'Taxi stand to city center (Tarifa fija)', phonetics: '[pa-RA-da de TAK-sis]' },
    ]
  },
  tapas: {
    id: 'tapas',
    title: 'Traditional Tapas Bar',
    location: 'Taberna La Camarilla · Barrio de La Latina, Madrid',
    image: '/scenes/tapas.jpg',
    badge: '🍷 Tapas Bar Visual Anchor',
    cameraAngle: 'Main Bar Counter & Chalkboard Menu',
    hotspots: [
      { es: 'Una caña bien fría, por favor', en: 'An ice-cold draft beer, please', phonetics: '[U-na KA-nya byen FRI-a...]' },
      { es: 'Una ración de tortilla española', en: 'A portion of Spanish potato omelette', phonetics: '[tor-TI-ya es-pa-NYO-la]' },
      { es: 'Patatas bravas con salsa picante', en: 'Spicy brava potatoes with paprika aioli', phonetics: '[pa-TA-tas BRA-vas]' },
      { es: '¿La tortilla es con o sin cebolla?', en: 'Is the omelette with or without onion?', phonetics: '[kon o seen the-BO-ya]' },
      { es: 'La cuenta cuando puedas, por favor', en: 'The bill whenever you can, please', phonetics: '[la KWEN-ta kwan-do PWE-das]' },
    ]
  },
  park: {
    id: 'park',
    title: 'Weekend Social Meetup',
    location: 'Parc de la Ciutadella · Barcelona',
    image: '/scenes/park.jpg',
    badge: '💬 Social Visual Anchor',
    cameraAngle: 'Sunny Café Terrace & Lawn',
    hotspots: [
      { es: '¡Qué buen día hace hoy!', en: 'What a beautiful sunny day it is today!', phonetics: '[ke bwen DI-a A-the oy]' },
      { es: 'Vamos a tomar algo a la terraza', en: 'Let\'s grab a cold drink on the terrace', phonetics: '[VA-mos a to-MAR AL-go...]' },
      { es: '¿Qué planes tienes para el fin de semana?', en: 'What plans do you have for the weekend?', phonetics: '[ke PLA-nes TYE-nes...]' },
      { es: 'Hay un concierto al aire libre', en: 'There is a free open-air concert', phonetics: '[kon-THYER-to al AY-re LI-bre]' },
    ]
  }
}

export default function SceneHUD({ scenario, onUsePhrase }) {
  const [isExpanded, setIsExpanded] = useState(true)
  const [activePhrase, setActivePhrase] = useState(null)
  const [isVideoModalOpen, setIsVideoModalOpen] = useState(false)
  const [videoSpeed, setVideoSpeed] = useState(1.0)
  const [videoPlaying, setVideoPlaying] = useState(false)

  // Identify matching scene from scenario title or setting
  const title = (scenario?.title || '').toLowerCase()
  const setting = (scenario?.setting || '').toLowerCase()

  let currentScene = SCENE_REGISTRY.airport
  if (title.includes('tapa') || setting.includes('bar') || title.includes('food')) {
    currentScene = SCENE_REGISTRY.tapas
  } else if (title.includes('friend') || title.includes('park') || setting.includes('barcelona')) {
    currentScene = SCENE_REGISTRY.park
  } else if (title.includes('airport') || setting.includes('barajas') || setting.includes('aeropuerto')) {
    currentScene = SCENE_REGISTRY.airport
  }

  const handleHotspotClick = (item) => {
    setActivePhrase(item)
    speakSpanish(item.es, { rate: 0.95 })
    if (onUsePhrase) {
      onUsePhrase(item.es)
    }
  }

  const playVideoClip = () => {
    setVideoPlaying(true)
    speakSpanish(activePhrase ? activePhrase.es : currentScene.hotspots[0].es, {
      rate: videoSpeed,
      onEnd: () => setVideoPlaying(false),
      onError: () => setVideoPlaying(false)
    })
  }

  return (
    <div className={`scene-hud ${isExpanded ? 'expanded' : 'collapsed'}`}>
      {/* Header Bar */}
      <div className="scene-hud-bar">
        <div className="scene-hud-left">
          <span className="scene-status-dot pulse" />
          <span className="scene-badge font-mono">{currentScene.badge}</span>
          <span className="scene-location">{currentScene.location}</span>
        </div>

        <div className="scene-hud-actions">
          <button
            type="button"
            className="scene-video-btn font-mono"
            onClick={() => setIsVideoModalOpen(true)}
            title="Watch real-world POV video & mouth phonetics"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <polygon points="5 3 19 12 5 21 5 3" />
            </svg>
            <span>🎬 POV Video & Phonetics</span>
          </button>

          <button
            type="button"
            className="scene-toggle-btn"
            onClick={() => setIsExpanded(!isExpanded)}
            title={isExpanded ? 'Collapse Scene HUD' : 'Expand Scene HUD'}
            aria-label="Toggle Scene HUD"
          >
            <span className="font-mono" style={{ fontSize: '0.72rem' }}>
              {isExpanded ? 'Hide Scene ▲' : 'Show Scene 👁️ ▼'}
            </span>
          </button>
        </div>
      </div>

      {/* Expandable Visual Banner */}
      {isExpanded && (
        <div className="scene-hud-body animate-in">
          <div className="scene-image-container">
            <img
              src={currentScene.image}
              alt={currentScene.title}
              className="scene-banner-img"
              loading="eager"
            />
            <div className="scene-overlay-gradient" />

            {/* Camera Tag */}
            <div className="scene-camera-tag font-mono">
              <span className="camera-rec-dot" /> LIVE CAMERA: {currentScene.cameraAngle}
            </div>

            {/* Hotspots Container */}
            <div className="scene-hotspots-drawer">
              <div className="scene-hotspots-title font-mono">
                <span>📍 SITUATIONAL VOCABULARY (CLICK TO HEAR & USE IN CHAT):</span>
              </div>
              <div className="scene-chips-row">
                {currentScene.hotspots.map((h, i) => (
                  <button
                    key={i}
                    type="button"
                    className={`scene-chip ${activePhrase?.es === h.es ? 'active' : ''}`}
                    onClick={() => handleHotspotClick(h)}
                    title={`Hear pronunciation: "${h.es}" -> ${h.en}`}
                  >
                    <span className="chip-es">{h.es}</span>
                    <span className="chip-en font-mono">({h.en})</span>
                    <span className="chip-sound-icon">🔊</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          {activePhrase && (
            <div className="scene-active-banner animate-in">
              <span className="font-mono text-cyan">🔊 Pronouncing:</span>
              <span className="active-es">"{activePhrase.es}"</span>
              <span className="active-phonetic font-mono">{activePhrase.phonetics}</span>
              <span className="active-used font-mono">➔ Pasted to chat input!</span>
            </div>
          )}
        </div>
      )}

      {/* Video & Lip-Sync Phonetics Modal */}
      {isVideoModalOpen && (
        <div className="video-modal-backdrop" onClick={() => setIsVideoModalOpen(false)}>
          <div className="video-modal-card animate-in-up" onClick={e => e.stopPropagation()}>
            <div className="video-modal-header">
              <div className="video-modal-title">
                <span className="modal-icon">🎬</span>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1rem', color: '#fff' }}>
                    Situational POV Immersion: {currentScene.title}
                  </h3>
                  <p style={{ margin: 0, fontSize: '0.75rem', color: '#94a3b8' }} className="font-mono">
                    Native Mouth Mechanics, Lip Cadence & First-Person Immersion
                  </p>
                </div>
              </div>
              <button
                type="button"
                className="video-modal-close"
                onClick={() => setIsVideoModalOpen(false)}
              >
                ✕
              </button>
            </div>

            <div className="video-player-frame">
              <img
                src={currentScene.image}
                alt="POV Scene Simulation"
                className="video-sim-background"
              />
              <div className="video-sim-overlay" />

              {/* Animated Phonetic Visualizer */}
              <div className="video-phonetic-hud">
                <div className="phonetic-mouth-guide">
                  <span className="font-mono lip-badge">👄 Native Lip Placement</span>
                  <p className="lip-instruction">
                    Keep Spanish vowels crisp and tense without diphthong gliding.
                  </p>
                </div>

                <div className={`video-sound-bars ${videoPlaying ? 'active' : ''}`}>
                  <span /><span /><span /><span /><span /><span /><span />
                </div>
              </div>

              {/* Central Play / Trigger Button */}
              <button
                type="button"
                className={`video-play-center-btn ${videoPlaying ? 'playing' : ''}`}
                onClick={playVideoClip}
                title="Play native audio & pronunciation"
              >
                {videoPlaying ? (
                  <div className="pause-bars">
                    <span /><span />
                  </div>
                ) : (
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
                    <polygon points="5 3 19 12 5 21 5 3" />
                  </svg>
                )}
              </button>
            </div>

            {/* Video Controls Bar */}
            <div className="video-controls-bar">
              <div className="video-speed-controls font-mono">
                <span className="speed-label">Playback Speed:</span>
                <button
                  type="button"
                  className={`speed-pill ${videoSpeed === 0.8 ? 'active' : ''}`}
                  onClick={() => setVideoSpeed(0.8)}
                >
                  0.8x Slow Phonetics
                </button>
                <button
                  type="button"
                  className={`speed-pill ${videoSpeed === 1.0 ? 'active' : ''}`}
                  onClick={() => setVideoSpeed(1.0)}
                >
                  1.0x Real Cadence
                </button>
              </div>

              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={playVideoClip}
              >
                🔊 {videoPlaying ? 'Playing Audio…' : 'Replay Native Audio'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
