import { describe, it, expect, vi, afterEach } from 'vitest'
import { shallowMount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import ConferencePage from '../ConferencePage.vue'
import { getConferenceAccess, getConferenceByFriendlyId } from '@/services/api/usersApi'

vi.mock('vue-router', async (original) => ({
  ...(await original<object>()),
  useRoute: () => ({ params: { friendlyId: 'evento' }, path: '/c/evento/ticket' }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() })
}))
vi.mock('@/features/auth/authStore', () => ({
  useAuthStore: () => ({
    state: { token: 'tok', role: 'guest', userUuid: 'invitado' },
    waitForSessionBridge: () => Promise.resolve(),
    isAuthenticated: () => true,
    isAdmin: () => false
  })
}))
vi.mock('@/services/api/usersApi', () => ({
  getConferenceByFriendlyId: vi.fn(() => Promise.resolve({ uuid: 'c1', friendlyId: 'evento', name: 'Evento', status: 'ACTIVE' })),
  getConferenceAccess: vi.fn(),
  getTimezones: vi.fn(() => Promise.resolve([])),
  getActiveEventTypes: vi.fn(() => Promise.resolve([])),
  getPresentationManagementAccess: vi.fn(() => Promise.resolve(false)),
  getToolAccess: vi.fn(() => Promise.resolve({})),
  joinConference: vi.fn(),
  createChatSsoExchange: vi.fn()
}))

describe('ConferencePage', () => {
  afterEach(() => vi.useRealTimers())

  it('asks for the event again, with the session, once access is granted', async () => {
    vi.useFakeTimers()
    const access = (hasAccess: boolean) => ({ eventActive: true, eventStatus: 'ACTIVE', ticketRequired: true, hasAccess })
    vi.mocked(getConferenceAccess).mockResolvedValue(access(false) as never)

    const wrapper = shallowMount(ConferencePage, {
      global: {
        stubs: { RouterLink: RouterLinkStub, RouterView: true },
        mocks: { $route: { path: '/c/evento/ticket', params: { friendlyId: 'evento' } } }
      }
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)

    expect(getConferenceByFriendlyId).toHaveBeenCalledTimes(1)
    expect(getConferenceByFriendlyId).toHaveBeenCalledWith('evento', 'tok')

    vi.mocked(getConferenceAccess).mockResolvedValue(access(true) as never)
    await vi.advanceTimersByTimeAsync(20000)

    // Una sola vez al obtener el acceso, no en cada comprobacion.
    expect(getConferenceByFriendlyId).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
