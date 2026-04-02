import { reactive } from 'vue'
import axios from 'axios'

const state = reactive({
  token: localStorage.getItem('ib_token') || null,
  role: localStorage.getItem('ib_role') || null,
  userUuid: localStorage.getItem('ib_user_uuid') || null
})

export function useAuthStore() {
  async function login(username) {
    const res = await axios.post('/api/users/api/v1/auth/login', { username })
    const { token, userUuid, role } = res.data.data
    state.token = token
    state.role = role
    state.userUuid = userUuid
    localStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', role)
    localStorage.setItem('ib_user_uuid', userUuid)
    return { token, role, userUuid }
  }

  async function loginAsGuest(displayName, conferenceUuid, fingerprint) {
    const res = await axios.post('/api/users/api/v1/auth/guest', {
      displayName, conferenceUuid, deviceFingerprint: fingerprint
    })
    const { token, guestUuid } = res.data.data
    state.token = token
    state.role = 'guest'
    state.userUuid = guestUuid
    localStorage.setItem('ib_token', token)
    localStorage.setItem('ib_role', 'guest')
    return { token }
  }

  function logout() {
    state.token = null
    state.role = null
    state.userUuid = null
    localStorage.removeItem('ib_token')
    localStorage.removeItem('ib_role')
    localStorage.removeItem('ib_user_uuid')
  }

  function isAuthenticated() { return !!state.token }
  function isOrganizer() { return state.role === 'organizer' }
  function isModerator() { return ['organizer', 'moderator'].includes(state.role) }

  return { state, login, loginAsGuest, logout, isAuthenticated, isOrganizer, isModerator }
}
