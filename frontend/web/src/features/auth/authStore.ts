import { reactive } from 'vue'
import axios from 'axios'
import { getFingerprint } from '@/services/auth/fingerprint'
import { clearStoredSession, registerSessionInvalidationHandler } from './sessionGuard'

interface AuthState {
  token: string | null
  role: string | null
  userUuid: string | null
  expiresAt: string | null
}

// La sesión se comparte entre pestañas para que el usuario pueda abrir las
// herramientas del evento sin volver a autenticarse. La duración efectiva la
// controla el backend (una hora) y useSessionManager sólo la renueva cuando
// detecta actividad reciente.
const tokenStorage = localStorage

function migrateLegacyToken(): void {
  // Las versiones anteriores guardaban el token únicamente por pestaña. Lo
  // promovemos una vez a localStorage para no cerrar sesiones durante el
  // despliegue del cambio.
  const tabToken = sessionStorage.getItem('ib_token')
  if (!tokenStorage.getItem('ib_token') && tabToken) tokenStorage.setItem('ib_token', tabToken)
  if (tabToken) sessionStorage.removeItem('ib_token')
}

migrateLegacyToken()

const state: AuthState = reactive({
  token: tokenStorage.getItem('ib_token') || null,
  role: localStorage.getItem('ib_role') || null,
  userUuid: localStorage.getItem('ib_user_uuid') || null,
  expiresAt: localStorage.getItem('ib_expires_at') || null
})

function clearInMemorySession(): void {
  state.token = null
  state.role = null
  state.userUuid = null
  state.expiresAt = null
}

registerSessionInvalidationHandler(clearInMemorySession)

function persistExpiresAt(expiresAt?: string | null) {
  state.expiresAt = expiresAt || null
  if (expiresAt) localStorage.setItem('ib_expires_at', expiresAt)
  else localStorage.removeItem('ib_expires_at')
}

const REFRESH_LOCK_KEY = 'ib_session_refresh_lock'
const REFRESH_LOCK_TTL_MS = 15_000
const REFRESH_WAIT_MS = 250
const REFRESH_WAIT_ATTEMPTS = 20
const tabId = randomTabId()

function randomTabId(): string {
  try {
    return crypto.randomUUID()
  } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

function acquireRefreshLock(): boolean {
  const now = Date.now()
  try {
    const current = localStorage.getItem(REFRESH_LOCK_KEY)
    if (current) {
      const lock = JSON.parse(current) as { owner?: string, expiresAt?: number }
      if (lock.owner && lock.owner !== tabId && Number(lock.expiresAt) > now) return false
    }
    localStorage.setItem(REFRESH_LOCK_KEY, JSON.stringify({ owner: tabId, expiresAt: now + REFRESH_LOCK_TTL_MS }))
    const stored = localStorage.getItem(REFRESH_LOCK_KEY)
    if (!stored) return true
    const acquired = JSON.parse(stored) as { owner?: string }
    return acquired.owner === tabId
  } catch {
    // Si el navegador bloquea localStorage, no impedimos que la sesión funcione.
    return true
  }
}

function releaseRefreshLock(): void {
  try {
    const current = JSON.parse(localStorage.getItem(REFRESH_LOCK_KEY) || '{}') as { owner?: string }
    if (current.owner === tabId) localStorage.removeItem(REFRESH_LOCK_KEY)
  } catch {
    // best-effort
  }
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function syncFromStorage(): void {
  const token = localStorage.getItem('ib_token')
  if (!token) {
    clearInMemorySession()
    return
  }
  state.token = token
  state.role = localStorage.getItem('ib_role')
  state.userUuid = localStorage.getItem('ib_user_uuid')
  state.expiresAt = localStorage.getItem('ib_expires_at')
}

async function waitForRefreshFromAnotherTab(previousToken: string): Promise<boolean> {
  for (let attempt = 0; attempt < REFRESH_WAIT_ATTEMPTS; attempt += 1) {
    await wait(REFRESH_WAIT_MS)
    const sharedToken = localStorage.getItem('ib_token')
    if (sharedToken && sharedToken !== previousToken) {
      syncFromStorage()
      return true
    }
    try {
      const lock = JSON.parse(localStorage.getItem(REFRESH_LOCK_KEY) || '{}') as { expiresAt?: number }
      if (!lock.expiresAt || Number(lock.expiresAt) <= Date.now()) break
    } catch {
      break
    }
  }
  return false
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

  // Compatibilidad con pestañas abiertas desde versiones anteriores, antes de
  // que la sesión se compartiera mediante localStorage.
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
  window.addEventListener('storage', (event) => {
    if (event.key === 'ib_token' || event.key === 'ib_role' || event.key === 'ib_user_uuid' || event.key === 'ib_expires_at') {
      syncFromStorage()
    }
  })
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
    const previousToken = state.token
    // Sólo una pestaña rota el token vigente. Sin este lock, dos pestañas que
    // llegan juntas al umbral de renovación podrían revocar mutuamente sus
    // tokens y expulsar al usuario aunque siga activo.
    if (!acquireRefreshLock()) return waitForRefreshFromAnotherTab(previousToken)
    try {
      const res = await axios.post('/api/users/api/v1/auth/refresh', {}, {
        headers: { Authorization: `Bearer ${previousToken}` }
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
    } finally {
      releaseRefreshLock()
    }
  }

  /** Comprueba que la sesión siga activa sin rotar el token. */
  async function validate(): Promise<boolean> {
    if (!state.token) return false
    try {
      await axios.get('/api/users/api/v1/auth/validate', {
        headers: { Authorization: `Bearer ${state.token}` }
      })
      return true
    } catch {
      // Un 401/403 de revocación ya fue procesado por el interceptor global;
      // un error de red no debe expulsar a un usuario que aún puede volver.
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
    clearInMemorySession()
    clearStoredSession()
  }

  function roleList(): string[] { return (state.role || '').split(',').map((r) => r.trim()).filter(Boolean) }
  function isAuthenticated(): boolean { return !!state.token }
  function isOrganizer(): boolean { return roleList().some((r) => r === 'organizer' || r === 'admin') }
  function isModerator(): boolean { return roleList().some((r) => r === 'organizer' || r === 'moderator' || r === 'admin') }
  function isAdmin(): boolean { return roleList().includes('admin') }

  return {
    state, login, loginAsGuest, logout, setSession, refresh, validate,
    waitForSessionBridge,
    isAuthenticated, isOrganizer, isModerator, isAdmin
  }
}
