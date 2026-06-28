<template lang="pug">
.speaker-panel-page
  ConferenceSubNav(:conferenceId="conferenceId")
  .speaker-header
    h2 Presentar
    .speaker-status
      span.live-dot(:class="{ connected: wsConnected }")
      span(v-if="wsConnected") 👀 {{ audienceCount }} conectados
      span(v-else) Conectando...
    button.btn-secondary(@click="showQr = true") Mostrar QR

  .presentation-empty(v-if="checkedStatus && !ready")
    p Aún no hay una presentación subida para esta conferencia.
    router-link.btn-primary(:to="`/dashboard/conferences/${conferenceId}/presentation`") Subir presentación

  template(v-else)
    p.hint Navega el deck con las flechas del teclado o haciendo clic dentro — la audiencia te sigue automáticamente.
    iframe.slides-frame(ref="slidesFrame" :src="slidesUrl" title="Slides" @load="onIframeLoad")

  QrCodeModal(v-if="showQr && friendlyId" :friendlyId="friendlyId" @close="showQr = false")
</template>

<script>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getPresentationStatus, getSlidesUrl, getPresenterWsUrl } from '@/services/api/presentationsApi'
import { getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import ConferenceSubNav from './ConferenceSubNav.vue'
import QrCodeModal from '@/components/QrCodeModal.vue'

export default {
  name: 'SpeakerPanelPage',
  components: { ConferenceSubNav, QrCodeModal },
  props: { conferenceId: String },
  setup(props) {
    const auth = useAuthStore()
    const checkedStatus = ref(false)
    const ready = ref(false)
    const slidesUrl = ref('')
    const slidesFrame = ref(null)
    const wsConnected = ref(false)
    const audienceCount = ref(0)
    const showQr = ref(false)
    const friendlyId = ref('')

    let ws = null
    let wsRetryTimer = null
    let wsClosedByUs = false
    let hashListenerAttached = false

    function onHashChange() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const hash = slidesFrame.value?.contentWindow?.location?.hash || ''
        ws.send(JSON.stringify({ type: 'slide', hash }))
      }
    }

    function onIframeLoad() {
      if (hashListenerAttached || !slidesFrame.value) return
      try {
        slidesFrame.value.contentWindow.addEventListener('hashchange', onHashChange)
        hashListenerAttached = true
      } catch (e) { /* same-origin esperado; si falla, no hay sync */ }
    }

    function connectPresenterWs() {
      if (!props.conferenceId || !auth.state.token) return
      ws = new WebSocket(getPresenterWsUrl(props.conferenceId, auth.state.token))
      ws.onopen = () => { wsConnected.value = true }
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data)
          if (msg.type === 'count') audienceCount.value = msg.count
        } catch (e) { /* ignorar */ }
      }
      ws.onclose = () => {
        wsConnected.value = false
        if (wsClosedByUs) return
        wsRetryTimer = setTimeout(connectPresenterWs, 3000)
      }
      ws.onerror = () => ws.close()
    }

    onMounted(async () => {
      try {
        const conf = await getConference(props.conferenceId, auth.state.token)
        friendlyId.value = conf?.friendlyId || ''
      } catch (e) { /* el botón de QR simplemente no aparece */ }
      try {
        const status = await getPresentationStatus(props.conferenceId)
        ready.value = !!status.ready
        if (ready.value) {
          slidesUrl.value = getSlidesUrl(props.conferenceId)
          connectPresenterWs()
        }
      } catch (e) { ready.value = false }
      finally { checkedStatus.value = true }
    })

    onBeforeUnmount(() => {
      if (wsRetryTimer) clearTimeout(wsRetryTimer)
      wsClosedByUs = true
      if (ws) ws.close()
    })

    return {
      conferenceId: props.conferenceId, checkedStatus, ready, slidesUrl, slidesFrame,
      wsConnected, audienceCount, showQr, friendlyId, onIframeLoad
    }
  }
}
</script>

<style scoped>
.speaker-panel-page { padding: 24px; max-width: 960px; }
.speaker-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
h2 { margin: 0; color: #1e1b4b; }
.speaker-status { display: flex; align-items: center; gap: 6px; font-size: 0.9rem; color: #374151; font-weight: 600; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: #d1d5db; }
.live-dot.connected { background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 10px; }
.presentation-empty { text-align: center; color: #6b7280; padding: 60px; }
.slides-frame { width: 100%; height: 70vh; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.btn-primary, .btn-secondary {
  padding: 8px 16px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 0.88rem;
  border: none; cursor: pointer;
}
.btn-primary { background: #4f46e5; color: #fff; }
.btn-secondary { background: #eef2ff; color: #4f46e5; border: 2px solid #c7d2fe; }
</style>
