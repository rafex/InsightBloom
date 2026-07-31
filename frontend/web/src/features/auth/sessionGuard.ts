import type { AxiosError } from 'axios'

const SESSION_TOKEN_KEY = 'ib_token'
const LOGIN_PATH = '/login'

let redirecting = false
let invalidateInMemory: (() => void) | null = null

/** Permite que el guard borre también el estado reactivo del auth store. */
export function registerSessionInvalidationHandler(handler: () => void): void {
  invalidateInMemory = handler
}

export function hasStoredSession(): boolean {
  return typeof localStorage !== 'undefined' && Boolean(localStorage.getItem(SESSION_TOKEN_KEY))
}

export function clearStoredSession(): void {
  if (typeof localStorage !== 'undefined') {
    localStorage.removeItem('ib_token')
    localStorage.removeItem('ib_role')
    localStorage.removeItem('ib_user_uuid')
    localStorage.removeItem('ib_expires_at')
  }
  // Limpia también el almacenamiento de pestaña usado por versiones previas.
  if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(SESSION_TOKEN_KEY)
  invalidateInMemory?.()
}

function responseCode(payload: unknown): string {
  if (!payload || typeof payload !== 'object') return ''
  const record = payload as Record<string, unknown>
  for (const key of ['error', 'code', 'reason', 'status']) {
    if (typeof record[key] === 'string') return record[key].toLowerCase()
  }
  return ''
}

function responseMessage(payload: unknown): string {
  if (!payload || typeof payload !== 'object') return ''
  const message = (payload as Record<string, unknown>).message
  return typeof message === 'string' ? message.toLowerCase() : ''
}

/**
 * 403 no siempre significa que la sesión murió: también representa permisos
 * insuficientes. Sólo los códigos/mensajes de revocación o bloqueo expulsan.
 */
export function isInvalidSessionResponse(status: number, payload?: unknown): boolean {
  if (status === 401) return true
  if (status !== 403) return false

  const code = responseCode(payload)
  const message = responseMessage(payload)
  const invalidCodes = new Set([
    'account_banned', 'account_blocked', 'account_disabled', 'banned',
    'device_blocked', 'platform_device_blocked', 'session_expired',
    'session_revoked', 'token_expired', 'token_invalid', 'token_revoked',
    'user_banned', 'user_blocked'
  ])
  if (invalidCodes.has(code)) return true

  return /(account|user|session|token|device|dispositivo|cuenta|usuario|sesión|sesion).*(ban|block|disable|expir|revok|revoc|invalid|bloque|suspend|agotad)/i.test(message)
}

function isAuthenticationEndpoint(url?: string): boolean {
  if (!url) return false
  return /\/api\/users\/api\/v1\/auth\/(login|register|guest|otp\/send|otp\/verify)(?:$|[/?])/.test(url)
}

function redirectToLogin(): void {
  if (typeof window === 'undefined' || redirecting || window.location.pathname === LOGIN_PATH) return
  redirecting = true
  // No copiamos query parameters: algunas rutas contienen tokens de acceso.
  const redirect = encodeURIComponent(window.location.pathname)
  window.location.href = `${LOGIN_PATH}?redirect=${redirect}`
}

/** Procesa una respuesta de fetch (SSE/streams) que no pasa por Axios. */
export function handleSessionResponse(status: number, payload?: unknown): boolean {
  if (!hasStoredSession() || !isInvalidSessionResponse(status, payload)) return false
  clearStoredSession()
  redirectToLogin()
  return true
}

/** Procesa un error Axios de cualquier servicio frontend. */
export function handleSessionAxiosError(error: AxiosError): boolean {
  if (!hasStoredSession() || isAuthenticationEndpoint(error.config?.url)) return false
  const response = error.response
  if (!response || !isInvalidSessionResponse(response.status, response.data)) return false
  clearStoredSession()
  redirectToLogin()
  return true
}

/** Utilidad para el guard de rutas y para pruebas. */
export function redirectExpiredRoute(): string | null {
  if (!hasStoredSession() || typeof window === 'undefined') return null
  clearStoredSession()
  if (window.location.pathname === LOGIN_PATH) return null
  const redirect = encodeURIComponent(window.location.pathname)
  return `${LOGIN_PATH}?redirect=${redirect}`
}
