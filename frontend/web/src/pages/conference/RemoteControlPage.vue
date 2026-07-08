<template lang="pug">
.remote-control-page
  .remote-status
    span.live-dot(:class="{ connected: wsConnected }")
    span(v-if="wsConnected") Conectado
    span(v-else-if="invalid") Enlace inválido o expirado — pide uno nuevo al organizador
    span(v-else) Conectando...

  .remote-buttons(v-if="!invalid")
    button.btn-nav(:disabled="!wsConnected" @click="send('prev')") ← Anterior
    button.btn-nav(:disabled="!wsConnected" @click="send('next')") Siguiente →
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getRemoteWsUrl } from '@/services/api/presentationsApi'

export default {
  name: 'RemoteControlPage',
  props: { conferenceId: String },
  setup(props) {
    const route = useRoute()
    const token = route.query.token as string | null
    const wsConnected = ref(false)
    const invalid = ref(false)

    let ws = null
    let wsRetryTimer = null
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
      ws.onerror = () => ws.close()
    }

    function send(direction) {
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
.remote-status { display: flex; align-items: center; gap: 8px; justify-content: center; color: #374151; font-size: 0.95rem; font-weight: 600; text-align: center; }
.live-dot { width: 10px; height: 10px; border-radius: 50%; background: #d1d5db; flex-shrink: 0; }
.live-dot.connected { background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.remote-buttons { display: flex; flex-direction: column; gap: 16px; flex: 1; }
.btn-nav {
  flex: 1; min-height: 120px; border-radius: 16px; border: 2px solid #c7d2fe; background: #eef2ff;
  color: #4f46e5; font-weight: 700; font-size: 1.4rem; cursor: pointer;
}
.btn-nav:active { background: #e0e7ff; }
.btn-nav:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
