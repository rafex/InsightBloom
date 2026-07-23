import { reactive } from 'vue'
import axios from 'axios'
import { getFingerprint } from '@/services/auth/fingerprint'

interface AuthState {
  token: string | null
  role: string | null
  userUuid: string | null
  expiresAt: string | null
}

// Access tokens are session material, not application preferences. Keep them
// out of persistent localStorage so a shared browser profile or an old
// persisted XSS payload cannot recover a token after the tab/session ends.
// The backend also issues a scoped HttpOnly cookie for presentation assets.
const tokenStorage = sessionStorage

function migrateLegacyToken(): void {
  const legacyToken = localStorage.getItem('ib_token')
  if (legacyToken && !sessionStorage.getItem('ib_token')) sessionStorage.setItem('ib_token', legacyToken)
  if (legacyToken) localStorage.removeItem('ib_token')
}

migrateLegacyToken()

const state: AuthState = reactive({
  token: tokenStorage.getItem('ib_token') || null,
  role: localStorage.getItem('ib_role') || null,
  userUuid: localStorage.getItem('ib_user_uuid') || null,
  expiresAt: localStorage.getItem('ib_expires_at') || null
})

function persistExpiresAt(expiresAt?: string | null) {
  state.expiresAt = expiresAt || null
  if (expiresAt) localStorage.setItem('ib_expires_at', expiresAt)
  else localStorage.removeItem('ib_expires_at')
}

export interface SessionInfo {
  token: string
  role: string
  userUuid: string
  expiresAt?: string | null
}

const SESSION_BRIDGE_REQUEST = 'insightbloom.session.request'
const SESSION_BRIDGE_RESPONSE = 'insightbloom.session.response'

let resolveSessionBridge: () => void = () => {}
let sessionBridgeSettled = false
const sessionBridgeReady = new Promise<void>((resolve) => {
  resolveSessionBridge = resolve
})

function settleSessionBridge() {
  if (sessionBridgeSettled) return
  sessionBridgeSettled = true
  resolveSessionBridge()
}

function randomBridgeNonce(): string {
  try {
    return crypto.randomUUID()
  } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

function applySession(session: SessionInfo) {
  state.token = session.token || null
  state.role = session.role || null
  state.userUuid = session.userUuid || null
  if (session.token) tokenStorage.setItem('ib_token', session.token)
  else tokenStorage.removeItem('ib_token')
  if (session.role) localStorage.setItem('ib_role', session.role)
  else localStorage.removeItem('ib_role')
  if (session.userUuid) localStorage.setItem('ib_user_uuid', session.userUuid)
  else localStorage.removeItem('ib_user_uuid')
  persistExpiresAt(session.expiresAt)
}

function handleSessionBridgeMessage(event: MessageEvent) {
  if (typeof window === 'undefined' || event.origin !== window.location.origin) return
  const message = event.data
  if (!message || typeof message.type !== 'string') return

  // A newly opened same-origin tab asks its opener for the current session.
  // The token never travels through the URL or persistent storage.
  if (message.type === SESSION_BRIDGE_REQUEST && event.source && event.source !== window) {
    if (!state.token || typeof (event.source as WindowProxy).postMessage !== 'function') return
    ;(event.source as WindowProxy).postMessage({
      type: SESSION_BRIDGE_RESPONSE,
      nonce: message.nonce,
      session: {
        token: state.token,
        role: state.role || '',
        userUuid: state.userUuid || '',
        expiresAt: state.expiresAt
      }
    }, event.origin)
    return
  }

  // Only accept a response from the window that opened this tab.
  if (message.type === SESSION_BRIDGE_RESPONSE && window.opener && event.source === window.opener) {
    const session = message.session
    if (typeof session?.token === 'string' && session.token &&
        typeof session.role === 'string' && typeof session.userUuid === 'string') {
      applySession(session)
    }
    settleSessionBridge()
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('message', handleSessionBridgeMessage)
  if (window.opener && window.opener !== window) {
    window.opener.postMessage({
      type: SESSION_BRIDGE_REQUEST,
      nonce: randomBridgeNonce()
    }, window.location.origin)
    // Direct public URLs and blocked/closed openers still fall back to the
    // normal anonymous preview after a bounded wait.
    window.setTimeout(settleSessionBridge, 1500)
  } else {
    settleSessionBridge()
  }
} else {
  settleSessionBridge()
}

export function useAuthStore() {
  async function login(username: string, password: string): Promise<{ token: string, role: string, userUuid: string }> {
    // La huella (ThumbmarkJS, ver services/auth/fingerprint.ts) viaja desde el login para que
    // PlatformDeviceGuard pueda controlar abuso a nivel plataforma (multicuenta, sesiones
    // simultaneas) desde el primer momento, no solo dentro de Jitsi/IDE de un evento puntual.
    const deviceFingerprint = await getFingerprint()
    const res = await axios.post('/api/users/api/v1/auth/login', { username, password, deviceFingerprint })
    const { token, userUuid, role, expiresAt } = res.data.data
    state.token = token
    state.role = role
    state.userUuid = userUuid
    tokenStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', role)
    localStorage.setItem('ib_user_uuid', userUuid)
    persistExpiresAt(expiresAt)
    return { token, role, userUuid }
  }

  async function loginAsGuest(displayName: string, conferenceUuid: string, fingerprint: string): Promise<{ token: string }> {
    const res = await axios.post('/api/users/api/v1/auth/guest', {
      displayName, conferenceUuid, deviceFingerprint: fingerprint
    })
    const { token, guestUuid, expiresAt } = res.data.data
    state.token = token
    state.role = 'guest'
    state.userUuid = guestUuid
    tokenStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', 'guest')
    persistExpiresAt(expiresAt)
    return { token }
  }

  function setSession({ token, role, userUuid, expiresAt }: SessionInfo) {
    applySession({ token, role, userUuid, expiresAt })
  }

  function waitForSessionBridge(): Promise<void> {
    return sessionBridgeReady
  }

  /** Renueva el token actual de forma silenciosa; no lanza si falla (llamada best-effort). */
  async function refresh(): Promise<boolean> {
    if (!state.token) return false
    try {
      const res = await axios.post('/api/users/api/v1/auth/refresh', {}, {
        headers: { Authorization: `Bearer ${state.token}` }
      })
      const { token, role, expiresAt } = res.data.data
      state.token = token
      if (role) state.role = role
      tokenStorage.setItem('ib_token', token)
      if (role) localStorage.setItem('ib_role', role)
      persistExpiresAt(expiresAt)
      return true
    } catch {
      return false
    }
  }

  async function logout(): Promise<void> {
    if (state.token) {
      try {
        await axios.post('/api/users/api/v1/auth/logout', {}, {
          headers: { Authorization: `Bearer ${state.token}` }
        })
      } catch {
        // best-effort: clear local state regardless of server response
      }
    }
    state.token = null
    state.role = null
    state.userUuid = null
    tokenStorage.removeItem('ib_token')
    localStorage.removeItem('ib_token')
    localStorage.removeItem('ib_role')
    localStorage.removeItem('ib_user_uuid')
    persistExpiresAt(null)
  }

  function roleList(): string[] { return (state.role || '').split(',').map((r) => r.trim()).filter(Boolean) }
  function isAuthenticated(): boolean { return !!state.token }
  function isOrganizer(): boolean { return roleList().some((r) => r === 'organizer' || r === 'admin') }
  function isModerator(): boolean { return roleList().some((r) => r === 'organizer' || r === 'moderator' || r === 'admin') }
  function isAdmin(): boolean { return roleList().includes('admin') }

  return {
    state, login, loginAsGuest, logout, setSession, refresh,
    waitForSessionBridge,
    isAuthenticated, isOrganizer, isModerator, isAdmin
  }
}
