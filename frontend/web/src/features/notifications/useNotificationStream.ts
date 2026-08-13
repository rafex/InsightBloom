import { ref, watch } from 'vue'
import { useAuthStore } from '@/features/auth/authStore'
import { getNotifications, markNotificationRead, streamNotifications, AuthenticatedEventStream } from '@/services/api/usersApi'
import type { NotificationItem } from '@/services/api/types'
import { notifyBrowserIfHidden } from '@/composables/useBrowserNotifications'

const RECONNECT_INITIAL_DELAY_MS = 2000
const RECONNECT_MAX_DELAY_MS = 10000

const items = ref<NotificationItem[]>([])
const unreadCount = ref(0)
const loading = ref(true)

let stream: AuthenticatedEventStream | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectDelay = RECONNECT_INITIAL_DELAY_MS
let started = false

async function loadInitial(token: string) {
  loading.value = true
  try {
    const result = await getNotifications(token)
    items.value = result.items
    unreadCount.value = result.unreadCount
  } catch {
    // Se reintenta implícitamente la próxima vez que se abra la campana.
  } finally {
    loading.value = false
  }
}

function connectStream(token: string) {
  stream = streamNotifications(token)
  stream.addEventListener('notification', (event) => {
    reconnectDelay = RECONNECT_INITIAL_DELAY_MS
    const messageEvent = event as MessageEvent
    const notification = JSON.parse(messageEvent.data) as NotificationItem
    items.value = [notification, ...items.value]
    unreadCount.value += 1
    void notifyBrowserIfHidden(notification.title, { body: notification.body || undefined })
  })
  stream.onerror = () => {
    stream?.close()
    stream = null
    reconnectTimer = setTimeout(() => {
      reconnectDelay = Math.min(reconnectDelay * 1.5, RECONNECT_MAX_DELAY_MS)
      const token = useAuthStore().state.token
      if (token) connectStream(token)
    }, reconnectDelay)
  }
}

function disconnect() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  stream?.close()
  stream = null
  items.value = []
  unreadCount.value = 0
}

/**
 * Singleton de la campana de notificaciones: antes NotificationBell.vue abría y cerraba el
 * stream SSE cada vez que se montaba (AppHeader no vive en un layout persistente, se incluye
 * por página) -- cada navegación abortaba la conexión, visible como NS_BINDING_ABORTED en el
 * Network tab (bug reportado 2026-08-13). Al inicializarse una sola vez desde App.vue (que nunca
 * se desmonta durante la navegación) el stream sobrevive a los cambios de ruta.
 */
export function useNotificationStream() {
  const auth = useAuthStore()
  if (!started) {
    started = true
    watch(() => auth.state.token, (token) => {
      disconnect()
      if (token) {
        void loadInitial(token)
        connectStream(token)
      }
    }, { immediate: true })
  }

  async function markRead(notification: NotificationItem) {
    if (notification.readAt || !auth.state.token) return
    notification.readAt = new Date().toISOString()
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    try { await markNotificationRead(notification.uuid, auth.state.token) } catch { /* ya se optimizó visualmente */ }
  }

  return { items, unreadCount, loading, markRead }
}
