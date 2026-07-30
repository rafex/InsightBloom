<template lang="pug">
.remote-control-page
  .remote-status
    span.live-dot(:class="{ connected: wsConnected }")
    span(v-if="wsConnected") Conectado
    span(v-else-if="invalid") Enlace inválido o expirado — pide uno nuevo al organizador
    span(v-else) Conectando...

  .remote-buttons(v-if="!invalid")
    BaseButton.remote-nav-button(variant="secondary" size="lg" :disabled="!wsConnected" @click="send('prev')") ← Anterior
    BaseButton.remote-nav-button(variant="secondary" size="lg" :disabled="!wsConnected" @click="send('next')") Siguiente →
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getRemoteWsUrl } from '@/services/api/presentationsApi'
import BaseButton from '@/components/ui/BaseButton.vue'

export default {
  name: 'RemoteControlPage',
  components: { BaseButton },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const route = useRoute()
    const token = route.query.token as string | null
    const wsConnected = ref(false)
    const invalid = ref(false)

    let ws: WebSocket | null = null
    let wsRetryTimer: ReturnType<typeof setTimeout> | null = null
    let wsClosedByUs = false
    let everConnected = false

    function connect() {
      if (!props.conferenceId || !token) { invalid.value = true; return }
      ws = new WebSocket(getRemoteWsUrl(props.conferenceId, token))
      ws.onopen = () => { wsConnected.value = true; everConnected = true }
      ws.onclose = () => {
        wsConnected.value = false
        if (wsClosedByUs) return
        if (!everConnected) { invalid.value = true; return }
        wsRetryTimer = setTimeout(connect, 3000)
      }
      ws.onerror = () => ws!.close()
    }

    function send(direction: 'prev' | 'next') {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'nav', direction }))
      }
    }

    onMounted(connect)

    onBeforeUnmount(() => {
      if (wsRetryTimer) clearTimeout(wsRetryTimer)
      wsClosedByUs = true
      if (ws) ws.close()
    })

    return { wsConnected, invalid, send }
  }
}
</script>

<style scoped>
.remote-control-page { padding: 24px; max-width: 480px; margin: 0 auto; min-height: 80vh; display: flex; flex-direction: column; gap: 24px; }
.remote-status { display: flex; align-items: center; gap: 8px; justify-content: center; color: var(--color-text-secondary); font-size: 0.95rem; font-weight: 600; text-align: center; }
.live-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--color-border); flex-shrink: 0; }
.live-dot.connected { background: var(--color-success); box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.remote-buttons { display: flex; flex-direction: column; gap: 16px; flex: 1; }
.remote-nav-button { flex: 1; min-height: 120px; border-width: 2px; border-radius: var(--radius-lg); font-size: 1.4rem; }
</style>
