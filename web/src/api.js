/**
 * Centralized API client for the Novara backend.
 *
 * Every request (except health check) includes the X-API-Key header.
 * The base URL and key are read from Vite env vars (.env file).
 */

const BASE = import.meta.env.VITE_API_BASE || 'https://novara-api-dnhc.onrender.com';
const KEY  = import.meta.env.VITE_API_KEY  || 'fXTFEdbxbsOE9fuU8E-xbYiwOEyRzgZBzJcwA_DaJ9c';

function headers() {
  return {
    'Content-Type': 'application/json',
    'X-API-Key': KEY,
  };
}

/**
 * Generic fetch wrapper.
 * Returns { ok, status, data } — never throws on HTTP errors,
 * so callers can handle each status code gracefully.
 */
async function request(method, path, body) {
  const opts = { method, headers: headers() };
  if (body) opts.body = JSON.stringify(body);

  const res = await fetch(BASE + path, opts);
  let data;
  try {
    data = await res.json();
  } catch {
    data = null;
  }
  return { ok: res.ok, status: res.status, data };
}

// ─── Health check (no API key needed) ───────────────────────
export async function checkHealth() {
  try {
    const res = await fetch(BASE + '/', { method: 'GET' });
    return res.ok;
  } catch {
    return false;
  }
}

// ─── POST /profile ──────────────────────────────────────────
export async function createProfile({ learnerId, language = 'spanish', level, region, purpose, interests, weakAreas }) {
  return request('POST', '/profile', {
    learner_id: learnerId,
    language,
    level,
    region,
    purpose,
    interests,
    weak_areas: weakAreas,
  });
}

// ─── GET /scenario ──────────────────────────────────────────
export async function getScenario(learnerId) {
  return request('GET', '/scenario?learner_id=' + encodeURIComponent(learnerId));
}

// ─── POST /conversation ─────────────────────────────────────
export async function sendMessage({ learnerId, scenarioId, message, turnNumber, responseTimeMs }) {
  const body = {
    learner_id: learnerId,
    scenario_id: scenarioId,
    message,
    turn_number: turnNumber,
  };
  if (responseTimeMs != null) body.response_time_ms = responseTimeMs;
  return request('POST', '/conversation', body);
}

// ─── GET /readiness ─────────────────────────────────────────
export async function getReadiness(learnerId) {
  return request('GET', '/readiness?learner_id=' + encodeURIComponent(learnerId));
}
