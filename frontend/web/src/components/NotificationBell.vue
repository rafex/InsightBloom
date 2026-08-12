<template lang="pug">
.notification-bell(v-if="auth.state.token && auth.state.role !== 'guest'")
  button.bell-button(type="button" @click="toggleOpen" :aria-expanded="open" aria-label="Notificaciones")
    UiIcon(name="bell" size="20")
    span.bell-badge(v-if="unreadCount > 0") {{ unreadCount > 9 ? '9+' : unreadCount }}
  .bell-dropdown(v-if="open" ref="dropdownRef")
    .bell-header
      strong Notificaciones
    LoadingState(v-if="loading" message="Cargando…")
    EmptyState(v-else-if="pagedItems.length === 0" message="No tenés notificaciones.")
    ul.bell-list(v-else)
      li.bell-item(v-for="n in pagedItems" :key="n.uuid" :class="{ unread: !n.readAt }" @click="onClickItem(n)")
        .bell-item-title {{ n.title }}
        p.bell-item-body(v-if="n.body") {{ n.body }}
        span.bell-item-time {{ formatRelative(n.createdAt) }}
    .bell-pagination(v-if="totalPages > 1")
      button(type="button" :disabled="page === 0" @click="page--") ← Más nuevas
      span {{ page + 1 }} / {{ totalPages }}
      button(type="button" :disabled="page >= totalPages - 1" @click="page++") Más viejas →
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
import { getNotifications, markNotificationRead, streamNotifications, AuthenticatedEventStream } from '@/services/api/usersApi'
import type { NotificationItem } from '@/services/api/types'
import { notifyBrowserIfHidden } from '@/composables/useBrowserNotifications'
import UiIcon from '@/components/ui/UiIcon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

const PAGE_SIZE = 5
const RECONNECT_INITIAL_DELAY_MS = 2000
const RECONNECT_MAX_DELAY_MS = 10000

// Campana de notificaciones del portal: primero trae la lista por REST, después mantiene un
// stream SSE (mismo patrón de reconexión con backoff que ya usa IdePage.vue para el sandbox)
// para que las nuevas aparezcan sin recargar. Cierra/reconecta solo mientras la sesión sigue
// activa -- si el usuario cierra sesión, el propio auth.state.token deja de existir y el bloque
// raíz del template ni siquiera monta el botón.
export default {
  name: 'NotificationBell',
  components: { UiIcon, EmptyState, LoadingState },
  setup() {
    const auth = useAuthStore()
    const router = useRouter()
    const items = ref<NotificationItem[]>([])
    const unreadCount = ref(0)
    const loading = ref(true)
    const open = ref(false)
    const page = ref(0)
    const dropdownRef = ref<HTMLElement | null>(null)

    let stream: AuthenticatedEventStream | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null
    let reconnectDelay = RECONNECT_INITIAL_DELAY_MS

    const pagedItems = computed(() => items.value.slice(page.value * PAGE_SIZE, page.value * PAGE_SIZE + PAGE_SIZE))
    const totalPages = computed(() => Math.max(1, Math.ceil(items.value.length / PAGE_SIZE)))

    function formatRelative(iso: string): string {
      return new Date(iso).toLocaleString('es-MX', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
    }

    async function loadInitial() {
      if (!auth.state.token) return
      loading.value = true
      try {
        const result = await getNotifications(auth.state.token)
        items.value = result.items
        unreadCount.value = result.unreadCount
      } catch {
        // Se reintenta implícitamente la próxima vez que se abra la campana.
      } finally {
        loading.value = false
      }
    }

    function connectStream() {
      if (!auth.state.token) return
      stream = streamNotifications(auth.state.token)
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
          connectStream()
        }, reconnectDelay)
      }
    }

    function toggleOpen() { open.value = !open.value }

    async function onClickItem(n: NotificationItem) {
      if (!n.readAt && auth.state.token) {
        n.readAt = new Date().toISOString()
        unreadCount.value = Math.max(0, unreadCount.value - 1)
        try { await markNotificationRead(n.uuid, auth.state.token) } catch { /* ya se optimizó visualmente */ }
      }
      open.value = false
      if (n.linkUrl) router.push(n.linkUrl)
    }

    function onDocumentClick(e: MouseEvent) {
      if (!open.value) return
      const target = e.target as Node
      if (dropdownRef.value && !dropdownRef.value.contains(target)
          && !(e.target as HTMLElement).closest('.bell-button')) {
        open.value = false
      }
    }

    onMounted(() => {
      document.addEventListener('click', onDocumentClick)
      if (auth.state.token) {
        void loadInitial()
        connectStream()
      }
    })

    onBeforeUnmount(() => {
      document.removeEventListener('click', onDocumentClick)
      if (reconnectTimer) clearTimeout(reconnectTimer)
      stream?.close()
    })

    return {
      auth, items, unreadCount, loading, open, page, dropdownRef,
      pagedItems, totalPages, formatRelative, toggleOpen, onClickItem
    }
  }
}
</script>

<style scoped>
.notification-bell { position: relative; }
.bell-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 0;
  color: var(--color-header-link);
  cursor: pointer;
  padding: 4px;
  /* Corrección óptica: el ícono de campana concentra su trazo en la mitad superior del
     viewBox, así que centrado por bounding-box (align-items:center del header-nav) igual se ve
     más arriba que la línea base de "Panel"/"Salir". Bajarlo 2px lo empareja visualmente. */
  position: relative;
  top: 2px;
}
.bell-button:hover { color: var(--color-text-inverse); }
.bell-badge {
  position: absolute;
  top: -2px;
  right: -4px;
  background: var(--color-danger);
  color: var(--color-text-inverse);
  font-size: 0.65rem;
  font-weight: 700;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 9999px;
  min-width: 16px;
  text-align: center;
}
.bell-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  max-width: 90vw;
  background: var(--color-surface);
  color: var(--color-text);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-dropdown);
  z-index: 200;
  overflow: hidden;
}
.bell-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.9rem;
}
.bell-list { list-style: none; margin: 0; padding: 0; max-height: 360px; overflow-y: auto; }
.bell-item {
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}
.bell-item:hover { background: var(--color-surface-muted); }
.bell-item.unread { background: var(--color-primary-soft); }
.bell-item-title { font-weight: 600; font-size: 0.88rem; }
.bell-item-body { margin: 2px 0 4px; font-size: 0.82rem; color: var(--color-text-secondary); }
.bell-item-time { font-size: 0.72rem; color: var(--color-text-muted); }
.bell-pagination {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px; font-size: 0.78rem; color: var(--color-text-secondary);
}
.bell-pagination button {
  background: transparent; border: 0; color: var(--color-primary); cursor: pointer; font-size: 0.78rem;
}
.bell-pagination button:disabled { opacity: 0.4; cursor: default; }
</style>
