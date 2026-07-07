import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'

vi.mock('axios')

// authStore keeps its state in a module-level singleton, so localStorage must be
// stubbed before the first import and every test must clean up after itself.
const localStorageMock = { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
vi.stubGlobal('localStorage', localStorageMock)

describe('authStore', () => {
  let useAuthStore

  beforeEach(async () => {
    vi.resetAllMocks()
    localStorageMock.getItem.mockReturnValue(null)
    ;({ useAuthStore } = await import('../authStore'))
    // The store is a module-level singleton, so state leaks across tests unless reset here.
    useAuthStore().setSession({ token: null, role: null, userUuid: null, expiresAt: null })
  })

  it('isAuthenticated returns false when no token', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated()).toBe(false)
  })

  it('login stores token/role/userUuid/expiresAt in localStorage and state', async () => {
    axios.post.mockResolvedValue({
      data: { data: { token: 'tok', userUuid: 'u1', role: 'organizer', expiresAt: '2026-07-08T00:00:00Z' } }
    })
    const auth = useAuthStore()
    const result = await auth.login('a@x.com', 'secret')

    expect(axios.post).toHaveBeenCalledWith('/api/users/api/v1/auth/login', { username: 'a@x.com', password: 'secret' })
    expect(result).toEqual({ token: 'tok', role: 'organizer', userUuid: 'u1' })
    expect(auth.state.token).toBe('tok')
    expect(auth.isAuthenticated()).toBe(true)
    expect(localStorageMock.setItem).toHaveBeenCalledWith('ib_token', 'tok')
    expect(localStorageMock.setItem).toHaveBeenCalledWith('ib_expires_at', '2026-07-08T00:00:00Z')
  })

  it('loginAsGuest sets role to "guest" regardless of what the backend returns', async () => {
    axios.post.mockResolvedValue({ data: { data: { token: 'gtok', guestUuid: 'g1', expiresAt: null } } })
    const auth = useAuthStore()
    await auth.loginAsGuest('Invitado', 'conf-1', 'fp1')

    expect(axios.post).toHaveBeenCalledWith('/api/users/api/v1/auth/guest', {
      displayName: 'Invitado', conferenceUuid: 'conf-1', deviceFingerprint: 'fp1'
    })
    expect(auth.state.role).toBe('guest')
    expect(auth.state.userUuid).toBe('g1')
  })

  it('refresh swaps the token and returns true on success, without throwing', async () => {
    axios.post.mockResolvedValue({ data: { data: { token: 'new-tok', role: 'organizer', expiresAt: '2026-07-08T01:00:00Z' } } })
    const auth = useAuthStore()
    auth.setSession({ token: 'old-tok', role: 'organizer', userUuid: 'u1', expiresAt: '2026-07-08T00:00:00Z' })

    const ok = await auth.refresh()
    expect(ok).toBe(true)
    expect(auth.state.token).toBe('new-tok')
  })

  it('refresh returns false (best-effort) when the backend call fails, and does not throw', async () => {
    axios.post.mockRejectedValue(new Error('network error'))
    const auth = useAuthStore()
    auth.setSession({ token: 'old-tok', role: 'organizer', userUuid: 'u1', expiresAt: '2026-07-08T00:00:00Z' })

    await expect(auth.refresh()).resolves.toBe(false)
  })

  it('refresh is a no-op returning false when there is no active session', async () => {
    const auth = useAuthStore()
    const ok = await auth.refresh()
    expect(ok).toBe(false)
    expect(axios.post).not.toHaveBeenCalled()
  })

  it('logout clears local state even if the server call fails (best-effort)', async () => {
    axios.post.mockRejectedValue(new Error('network error'))
    const auth = useAuthStore()
    auth.setSession({ token: 'tok', role: 'organizer', userUuid: 'u1', expiresAt: '2026-07-08T00:00:00Z' })

    await auth.logout()

    expect(auth.state.token).toBeNull()
    expect(auth.isAuthenticated()).toBe(false)
    expect(localStorageMock.removeItem).toHaveBeenCalledWith('ib_token')
    expect(localStorageMock.removeItem).toHaveBeenCalledWith('ib_expires_at')
  })

  it('logout does not call the backend when there is no token to revoke', async () => {
    const auth = useAuthStore()
    await auth.logout()
    expect(axios.post).not.toHaveBeenCalled()
  })

  describe('role helpers', () => {
    it('isOrganizer / isModerator / isAdmin read from a comma-separated role list', () => {
      const auth = useAuthStore()
      auth.setSession({ token: 't', role: 'organizer,admin', userUuid: 'u1', expiresAt: null })
      expect(auth.isOrganizer()).toBe(true)
      expect(auth.isModerator()).toBe(true)
      expect(auth.isAdmin()).toBe(true)
    })

    it('a plain "moderator" role is a moderator but not an organizer or admin', () => {
      const auth = useAuthStore()
      auth.setSession({ token: 't', role: 'moderator', userUuid: 'u1', expiresAt: null })
      expect(auth.isModerator()).toBe(true)
      expect(auth.isOrganizer()).toBe(false)
      expect(auth.isAdmin()).toBe(false)
    })

    it('a guest role is none of organizer/moderator/admin', () => {
      const auth = useAuthStore()
      auth.setSession({ token: 't', role: 'guest', userUuid: 'u1', expiresAt: null })
      expect(auth.isOrganizer()).toBe(false)
      expect(auth.isModerator()).toBe(false)
      expect(auth.isAdmin()).toBe(false)
    })
  })
})
