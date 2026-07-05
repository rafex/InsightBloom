import { reactive } from 'vue'
import axios from 'axios'

const state = reactive({
  token: localStorage.getItem('ib_token') || null,
  role: localStorage.getItem('ib_role') || null,
  userUuid: localStorage.getItem('ib_user_uuid') || null,
  expiresAt: localStorage.getItem('ib_expires_at') || null
})

function persistExpiresAt(expiresAt) {
  state.expiresAt = expiresAt || null
  if (expiresAt) localStorage.setItem('ib_expires_at', expiresAt)
  else localStorage.removeItem('ib_expires_at')
}

export function useAuthStore() {
  async function login(username, password) {
    const res = await axios.post('/api/users/api/v1/auth/login', { username, password })
    const { token, userUuid, role, expiresAt } = res.data.data
    state.token = token
    state.role = role
    state.userUuid = userUuid
    localStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', role)
    localStorage.setItem('ib_user_uuid', userUuid)
    persistExpiresAt(expiresAt)
    return { token, role, userUuid }
  }

  async function loginAsGuest(displayName, conferenceUuid, fingerprint) {
    const res = await axios.post('/api/users/api/v1/auth/guest', {
      displayName, conferenceUuid, deviceFingerprint: fingerprint
    })
    const { token, guestUuid, expiresAt } = res.data.data
    state.token = token
    state.role = 'guest'
    state.userUuid = guestUuid
    localStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', 'guest')
    persistExpiresAt(expiresAt)
    return { token }
  }

  function setSession({ token, role, userUuid, expiresAt }) {
    state.token = token
    state.role = role
    state.userUuid = userUuid
    localStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', role)
    localStorage.setItem('ib_user_uuid', userUuid)
    persistExpiresAt(expiresAt)
  }

  /** Renueva el token actual de forma silenciosa; no lanza si falla (llamada best-effort). */
  async function refresh() {
    if (!state.token) return false
    try {
      const res = await axios.post('/api/users/api/v1/auth/refresh', {}, {
        headers: { Authorization: `Bearer ${state.token}` }
      })
      const { token, role, expiresAt } = res.data.data
      state.token = token
      if (role) state.role = role
      localStorage.setItem('ib_token', token)
      if (role) localStorage.setItem('ib_role', role)
      persistExpiresAt(expiresAt)
      return true
    } catch {
      return false
    }
  }

  async function logout() {
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
    localStorage.removeItem('ib_token')
    localStorage.removeItem('ib_role')
    localStorage.removeItem('ib_user_uuid')
    persistExpiresAt(null)
  }

  function roleList() { return (state.role || '').split(',').map((r) => r.trim()).filter(Boolean) }
  function isAuthenticated() { return !!state.token }
  function isOrganizer() { return roleList().some((r) => r === 'organizer' || r === 'admin') }
  function isModerator() { return roleList().some((r) => r === 'organizer' || r === 'moderator' || r === 'admin') }
  function isAdmin() { return roleList().includes('admin') }

  return {
    state, login, loginAsGuest, logout, setSession, refresh,
    isAuthenticated, isOrganizer, isModerator, isAdmin
  }
}
