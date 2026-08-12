import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import NotificationBell from '../NotificationBell.vue'

const listeners: Record<string, ((event: unknown) => void)[]> = {}
const fakeStream = {
  addEventListener: (type: string, cb: (event: unknown) => void) => {
    listeners[type] = listeners[type] || []
    listeners[type].push(cb)
  },
  close: vi.fn(),
  onerror: null as (() => void) | null
}

vi.mock('@/services/api/usersApi', () => ({
  getNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  streamNotifications: vi.fn(() => fakeStream)
}))

// authStore.ts ejecuta código a nivel de módulo que toca localStorage al importarse (migración
// legacy de sesión) -- fuera de alcance de este test unitario de la campana, así que se stubea
// con un estado reactivo simple en vez de depender del store real.
const authState = reactive({ token: null as string | null, role: null as string | null, userUuid: null, expiresAt: null })
vi.mock('@/features/auth/authStore', () => ({
  useAuthStore: () => ({ state: authState })
}))

import { getNotifications, markNotificationRead } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/dashboard/events/:id', component: { template: '<div/>' } }
    ]
  })
  router.push('/')
  return router
}

describe('NotificationBell', () => {
  beforeEach(() => {
    Object.keys(listeners).forEach(k => delete listeners[k])
    vi.mocked(getNotifications).mockReset()
    vi.mocked(markNotificationRead).mockReset()
  })

  it('does not render for a logged-out (guest) session', async () => {
    const auth = useAuthStore()
    auth.state.token = null
    auth.state.role = null
    const router = makeRouter()
    await router.isReady()
    const wrapper = mount(NotificationBell, { global: { plugins: [router] } })
    expect(wrapper.find('.notification-bell').exists()).toBe(false)
  })

  it('shows the unread badge and lists notifications when opened', async () => {
    vi.mocked(getNotifications).mockResolvedValue({
      items: [
        { uuid: 'n1', type: 'test', title: 'Tu zip está listo', body: 'Ya podés descargarlo', linkUrl: null, createdAt: new Date().toISOString(), readAt: null }
      ],
      unreadCount: 1
    })
    const auth = useAuthStore()
    auth.state.token = 'test-token'
    auth.state.role = 'organizer'
    const router = makeRouter()
    await router.isReady()
    const wrapper = mount(NotificationBell, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.find('.bell-badge').text()).toBe('1')
    await wrapper.find('.bell-button').trigger('click')
    expect(wrapper.find('.bell-item-title').text()).toBe('Tu zip está listo')
  })

  it('marks a notification read and navigates when clicked', async () => {
    vi.mocked(getNotifications).mockResolvedValue({
      items: [{ uuid: 'n1', type: 'test', title: 'Listo', body: null, linkUrl: '/dashboard/events/conf-1', createdAt: new Date().toISOString(), readAt: null }],
      unreadCount: 1
    })
    vi.mocked(markNotificationRead).mockResolvedValue(undefined)
    const auth = useAuthStore()
    auth.state.token = 'test-token'
    auth.state.role = 'organizer'
    const router = makeRouter()
    await router.isReady()
    const wrapper = mount(NotificationBell, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.find('.bell-button').trigger('click')

    await wrapper.find('.bell-item').trigger('click')
    await flushPromises()

    expect(markNotificationRead).toHaveBeenCalledWith('n1', 'test-token')
    expect(router.currentRoute.value.fullPath).toBe('/dashboard/events/conf-1')
  })

  it('prepends new notifications received over the SSE stream', async () => {
    vi.mocked(getNotifications).mockResolvedValue({ items: [], unreadCount: 0 })
    const auth = useAuthStore()
    auth.state.token = 'test-token'
    auth.state.role = 'organizer'
    const router = makeRouter()
    await router.isReady()
    const wrapper = mount(NotificationBell, { global: { plugins: [router] } })
    await flushPromises()

    expect(listeners['notification']).toBeDefined()
    listeners['notification'][0]({
      data: JSON.stringify({ uuid: 'n2', type: 'test', title: 'Nueva', body: null, linkUrl: null, createdAt: new Date().toISOString(), readAt: null })
    })
    await flushPromises()

    expect(wrapper.find('.bell-badge').text()).toBe('1')
  })
})
