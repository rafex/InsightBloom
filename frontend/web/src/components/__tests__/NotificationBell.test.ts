import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import NotificationBell from '../NotificationBell.vue'
import type { NotificationItem } from '@/services/api/types'

// El stream SSE ya no lo abre/cierra este componente -- vive en un singleton
// (useNotificationStream, iniciado desde App.vue) para sobrevivir a la navegación entre rutas.
// NotificationBell.vue solo consume su estado reactivo, así que el test lo mockea directamente
// en vez de simular el fetch/EventSource subyacente.
const items = ref<NotificationItem[]>([])
const unreadCount = ref(0)
const loading = ref(false)
const markRead = vi.fn(async (n: NotificationItem) => {
  n.readAt = new Date().toISOString()
  unreadCount.value = Math.max(0, unreadCount.value - 1)
})

vi.mock('@/features/notifications/useNotificationStream', () => ({
  useNotificationStream: () => ({ items, unreadCount, loading, markRead })
}))

// authStore.ts ejecuta código a nivel de módulo que toca localStorage al importarse (migración
// legacy de sesión) -- fuera de alcance de este test unitario de la campana, así que se stubea
// con un estado reactivo simple en vez de depender del store real.
const authState = reactive({ token: null as string | null, role: null as string | null, userUuid: null, expiresAt: null })
vi.mock('@/features/auth/authStore', () => ({
  useAuthStore: () => ({ state: authState })
}))

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
    items.value = []
    unreadCount.value = 0
    loading.value = false
    markRead.mockClear()
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
    items.value = [
      { uuid: 'n1', type: 'test', title: 'Tu zip está listo', body: 'Ya podés descargarlo', linkUrl: null, createdAt: new Date().toISOString(), readAt: null }
    ]
    unreadCount.value = 1
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
    items.value = [{ uuid: 'n1', type: 'test', title: 'Listo', body: null, linkUrl: '/dashboard/events/conf-1', createdAt: new Date().toISOString(), readAt: null }]
    unreadCount.value = 1
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

    expect(markRead).toHaveBeenCalledWith(items.value[0])
    expect(router.currentRoute.value.fullPath).toBe('/dashboard/events/conf-1')
  })

  it('prepends new notifications received over the SSE stream', async () => {
    const auth = useAuthStore()
    auth.state.token = 'test-token'
    auth.state.role = 'organizer'
    const router = makeRouter()
    await router.isReady()
    const wrapper = mount(NotificationBell, { global: { plugins: [router] } })
    await flushPromises()

    // Simula lo que useNotificationStream hace al recibir un evento SSE (probado por separado);
    // acá solo se verifica que el componente reacciona al estado compartido.
    items.value = [
      { uuid: 'n2', type: 'test', title: 'Nueva', body: null, linkUrl: null, createdAt: new Date().toISOString(), readAt: null },
      ...items.value
    ]
    unreadCount.value += 1
    await flushPromises()

    expect(wrapper.find('.bell-badge').text()).toBe('1')
  })
})
