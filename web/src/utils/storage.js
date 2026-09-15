/**
 * Task 3: Local Profile Store
 * Persists learner profile and state across browser restart / kill / tab close.
 * Includes schema validation matching the NOVARA API contract.
 */

const STORAGE_KEY_PREFIX = 'novara_'

export const ProfileSchema = {
  isValid(profile) {
    if (!profile || typeof profile !== 'object') return false
    if (!profile.learner_id || typeof profile.learner_id !== 'string') return false
    if (profile.language !== 'spanish') return false
    if (!['A1', 'A2', 'B1', 'B2'].includes(profile.level)) return false
    if (!['trip', 'casual', 'exam', 'relocation'].includes(profile.purpose)) return false
    if (!profile.region || typeof profile.region !== 'string') return false
    if (!Array.isArray(profile.interests) || profile.interests.length === 0) return false
    return true
  }
}

export function saveProfile(profile) {
  try {
    const serialized = JSON.stringify(profile)
    localStorage.setItem(STORAGE_KEY_PREFIX + 'profile', serialized)
    sessionStorage.setItem(STORAGE_KEY_PREFIX + 'profile', serialized)
    // Also save convenience keys
    localStorage.setItem(STORAGE_KEY_PREFIX + 'learner_id', profile.learner_id)
    sessionStorage.setItem(STORAGE_KEY_PREFIX + 'learner_id', profile.learner_id)
    localStorage.setItem(STORAGE_KEY_PREFIX + 'purpose', profile.purpose)
    sessionStorage.setItem(STORAGE_KEY_PREFIX + 'purpose', profile.purpose)
    localStorage.setItem(STORAGE_KEY_PREFIX + 'level', profile.level)
    sessionStorage.setItem(STORAGE_KEY_PREFIX + 'level', profile.level)
    if (profile.native_language) {
      localStorage.setItem(STORAGE_KEY_PREFIX + 'native_language', profile.native_language)
      sessionStorage.setItem(STORAGE_KEY_PREFIX + 'native_language', profile.native_language)
    }
    return true
  } catch (e) {
    console.warn('Failed to save to localStorage:', e)
    return false
  }
}

export function loadProfile() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_PREFIX + 'profile') ||
                sessionStorage.getItem(STORAGE_KEY_PREFIX + 'profile')
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (ProfileSchema.isValid(parsed)) {
      return parsed
    }
    return null
  } catch {
    return null
  }
}

export function getItem(key, fallback = null) {
  try {
    return localStorage.getItem(STORAGE_KEY_PREFIX + key) ??
           sessionStorage.getItem(STORAGE_KEY_PREFIX + key) ??
           fallback
  } catch {
    return fallback
  }
}

export function setItem(key, val) {
  try {
    const str = typeof val === 'string' ? val : JSON.stringify(val)
    localStorage.setItem(STORAGE_KEY_PREFIX + key, str)
    sessionStorage.setItem(STORAGE_KEY_PREFIX + key, str)
  } catch (e) {
    console.warn('Failed to set storage item:', e)
  }
}

export function clearProfile() {
  try {
    const keysToRemove = [
      'profile', 'learner_id', 'purpose', 'level', 'region',
      'scenario', 'native_language', 'accent_target', 'use_mock'
    ]
    keysToRemove.forEach(k => {
      localStorage.removeItem(STORAGE_KEY_PREFIX + k)
      sessionStorage.removeItem(STORAGE_KEY_PREFIX + k)
    })
  } catch (e) {
    console.warn('Failed to clear storage:', e)
  }
}
