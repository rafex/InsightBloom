import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const mockAuth = {
  state: { token: null, expiresAt: null },
  refresh: vi.fn(),
  logout: vi.fn()
}

vi.mock('../authStore', () => ({
  useAuthStore: () => mockAuth
}))

describe('useSessionManager', () => {
  let useSessionManager
  let windowStub

  beforeEach(async () => {
    vi.resetModules()
    vi.useFakeTimers()
    mockAuth.state.token = null
    mockAuth.state.expiresAt = null
    mockAuth.refresh.mockReset().mockResolvedValue(true)
    mockAuth.logout.mockReset().mockResolvedValue()

    windowStub = {
      location: { pathname: '/c/conf-1/doubts', href: '' },
      addEventListener: vi.fn()
    }
    vi.stubGlobal('window', windowStub)
    ;({ useSessionManager } = await import('../useSessionManager'))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('does nothing on tick when there is no active session', async () => {
    const { start, showWarning } = useSessionManager()
    start()
    await vi.runOnlyPendingTimersAsync()
    expect(showWarning.value).toBe(false)
    expect(mockAuth.refresh).not.toHaveBeenCalled()
  })

  it('silently refreshes when the token is close to expiry and the user was recently active', async () => {
    mockAuth.state.token = 'tok'
    mockAuth.state.expiresAt = new Date(Date.now() + 90_000).toISOString() // 90s left, inside the silent-refresh window

    const { start } = useSessionManager()
    start()
    await vi.runOnlyPendingTimersAsync()

    expect(mockAuth.refresh).toHaveBeenCalled()
  })

  it('shows the expiry warning and starts a countdown when under the warning threshold', async () => {
    mockAuth.state.token = 'tok'
    mockAuth.state.expiresAt = new Date(Date.now() + 30_000).toISOString() // 30s left

    const { start, showWarning, secondsRemaining } = useSessionManager()
    start()
    // Only flush the synchronous part of the initial tick() the countdown's own
    // 1s interval must not fire yet, or forceLogoutAndRedirect would flip this back.
    await vi.advanceTimersByTimeAsync(0)

    expect(showWarning.value).toBe(true)
    expect(secondsRemaining.value).toBeGreaterThan(0)
    expect(secondsRemaining.value).toBeLessThanOrEqual(30)
  })

  it('force-logs-out and redirects when the countdown reaches zero without user action', async () => {
    mockAuth.state.token = 'tok'
    mockAuth.state.expiresAt = new Date(Date.now() + 30_000).toISOString()

    const { start } = useSessionManager()
    start()
    await vi.runOnlyPendingTimersAsync()

    await vi.advanceTimersByTimeAsync(31_000)

    expect(mockAuth.logout).toHaveBeenCalled()
    expect(windowStub.location.href).toBe('/login')
  })

  it('does not redirect again if already on the login page', async () => {
    windowStub.location.pathname = '/login'
    mockAuth.state.token = 'tok'
    mockAuth.state.expiresAt = new Date(Date.now() + 30_000).toISOString()

    const { start } = useSessionManager()
    start()
    await vi.runOnlyPendingTimersAsync()
    await vi.advanceTimersByTimeAsync(31_000)

    expect(mockAuth.logout).toHaveBeenCalled()
    expect(windowStub.location.href).toBe('')
  })

  it('force-logs-out immediately when the token has already expired', async () => {
    mockAuth.state.token = 'tok'
    mockAuth.state.expiresAt = new Date(Date.now() - 1_000).toISOString()

    const { start } = useSessionManager()
    start()
    await vi.runOnlyPendingTimersAsync()

    expect(mockAuth.logout).toHaveBeenCalled()
    expect(windowStub.location.href).toBe('/login')
  })

  it('keepConnected refreshes the session, clears the warning, and does not log out on success', async () => {
    mockAuth.refresh.mockResolvedValue(true)
    const { keepConnected, showWarning } = useSessionManager()
    showWarning.value = true

    await keepConnected()

    expect(mockAuth.refresh).toHaveBeenCalled()
    expect(showWarning.value).toBe(false)
    expect(mockAuth.logout).not.toHaveBeenCalled()
  })

  it('keepConnected forces logout when the refresh itself fails', async () => {
    mockAuth.refresh.mockResolvedValue(false)
    const { keepConnected } = useSessionManager()

    await keepConnected()

    expect(mockAuth.logout).toHaveBeenCalled()
  })

  it('stop() clears the polling interval so tick no longer fires', async () => {
    mockAuth.state.token = 'tok'
    mockAuth.state.expiresAt = new Date(Date.now() + 90_000).toISOString()

    const { start, stop } = useSessionManager()
    start()
    await vi.runOnlyPendingTimersAsync()
    stop()

    mockAuth.refresh.mockClear()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockAuth.refresh).not.toHaveBeenCalled()
  })
})
