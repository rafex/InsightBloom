import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock localStorage
const localStorageMock = { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
vi.stubGlobal('localStorage', localStorageMock)

describe('authStore', () => {
  it('isAuthenticated returns false when no token', async () => {
    localStorageMock.getItem.mockReturnValue(null)
    const { useAuthStore } = await import('../authStore')
    const auth = useAuthStore()
    expect(auth.isAuthenticated()).toBe(false)
  })
})
