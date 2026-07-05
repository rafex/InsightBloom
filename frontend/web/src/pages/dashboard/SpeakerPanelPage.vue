<template lang="pug">
.speaker-panel-page
  ConferenceSubNav(:conferenceId="conferenceId")
  .speaker-header
    h2 Presentar
    .speaker-status
      span.live-dot(:class="{ connected: wsConnected }")
      span(v-if="wsConnected") 👀 {{ audienceCount }} conectados
      span(v-else) Conectando...
    .speaker-header-actions
      button.btn-secondary(@click="showQr = true") Mostrar QR
      button.btn-secondary(@click="shareRemoteControl") Compartir control remoto

  .presentation-empty(v-if="checkedStatus && !ready")
    p Aún no hay una presentación subida para esta conferencia.
    router-link.btn-primary(:to="`/dashboard/conferences/${conferenceId}/presentation`") Subir presentación

  template(v-else)
    p.hint Navega el deck con las flechas del teclado, haciendo clic dentro, o con los botones de abajo — la audiencia te sigue automáticamente.
    iframe.slides-frame(ref="slidesFrame" :src="slidesUrl" title="Slides" @load="onIframeLoad")
    .nav-controls
      button.btn-nav(@click="navigate('prev')") ← Anterior
      button.btn-nav(@click="navigate('next')") Siguiente →

  QrCodeModal(v-if="showQr && friendlyId" :friendlyId="friendlyId" @close="showQr = false")
  QrCodeModal(v-if="showRemoteShare" :url="remoteShareUrl" @close="showRemoteShare = false")
</template>

<script>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getPresentationStatus, getSlidesUrl, getPresenterWsUrl, createRemoteLinkToken } from '@/services/api/presentationsApi'
import { getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import ConferenceSubNav from './ConferenceSubNav.vue'
import QrCodeModal from '@/components/QrCodeModal.vue'

const NAV_KEYS = {
  next: { key: 'ArrowRight', keyCode: 39 },
  prev: { key: 'ArrowLeft', keyCode: 37 }
}

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
    const showRemoteShare = ref(false)
    const remoteShareUrl = ref('')

    let ws = null
    let wsRetryTimer = null
    let wsClosedByUs = false
    let hashPollTimer = null
    let lastHash = null

    const HASH_POLL_MS = 250

    function navigate(direction) {
      const spec = NAV_KEYS[direction]
      if (!spec || !slidesFrame.value) return
      try {
        const doc = slidesFrame.value.contentWindow.document
        doc.dispatchEvent(new KeyboardEvent('keydown', { key: spec.key, keyCode: spec.keyCode, which: spec.keyCode, bubbles: true }))
      } catch (e) { /* same-origin esperado; si falla, no hay sync */ }
    }

    function pollHash() {
      let hash
      try {
        hash = slidesFrame.value?.contentWindow?.location?.hash || ''
      } catch (e) { return /* same-origin esperado; si falla, no hay sync */ }
      if (hash !== lastHash) {
        lastHash = hash
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'slide', hash }))
        }
      }
    }

    function onIframeLoad() {
      // Si el iframe se recarga solo (hiccup de red, bespoke.js no persiste la
      // posición al recargar), retoma la diapositiva donde estaba en vez de
      // dejar que el deck vuelva al inicio y esa "vuelta al inicio" se
      // propague a toda la audiencia en el próximo poll.
      if (lastHash) {
        try {
          slidesFrame.value.contentWindow.location.hash = lastHash
        } catch (e) { /* same-origin esperado; si falla, no hay sync */ }
      }
      // bespoke.js (motor de Marp) navega con history.pushState/replaceState,
      // que no disparan 'hashchange' — por eso se hace polling del hash.
      if (hashPollTimer) return
      hashPollTimer = setInterval(pollHash, HASH_POLL_MS)
    }

    function connectPresenterWs() {
      if (!props.conferenceId || !auth.state.token) return
      ws = new WebSocket(getPresenterWsUrl(props.conferenceId, auth.state.token))
      ws.onopen = () => {
        wsConnected.value = true
        // Resincroniza de inmediato tras reconectar (no esperar a que cambie
        // el hash), por si el pod de presentaciones perdió el estado.
        if (lastHash != null && ws && ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'slide', hash: lastHash }))
        }
      }
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data)
          if (msg.type === 'count') audienceCount.value = msg.count
          else if (msg.type === 'nav') navigate(msg.direction)
        } catch (e) { /* ignorar */ }
      }
      ws.onclose = () => {
        wsConnected.value = false
        if (wsClosedByUs) return
        wsRetryTimer = setTimeout(connectPresenterWs, 3000)
      }
      ws.onerror = () => ws.close()
    }

    async function shareRemoteControl() {
      try {
        const token = await createRemoteLinkToken(props.conferenceId, auth.state.token)
        remoteShareUrl.value = `${window.location.origin}/c/${friendlyId.value}/remote?token=${token}`
        showRemoteShare.value = true
      } catch (e) { /* no se pudo generar el enlace */ }
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
      if (hashPollTimer) clearInterval(hashPollTimer)
      wsClosedByUs = true
      if (ws) ws.close()
    })

    return {
      conferenceId: props.conferenceId, checkedStatus, ready, slidesUrl, slidesFrame,
      wsConnected, audienceCount, showQr, friendlyId, onIframeLoad, navigate,
      showRemoteShare, remoteShareUrl, shareRemoteControl
    }
  }
}
</script>

<style scoped>
.speaker-panel-page { padding: 24px; max-width: 960px; }
.speaker-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.speaker-header-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-left: auto; }
h2 { margin: 0; color: #1e1b4b; }
.speaker-status { display: flex; align-items: center; gap: 6px; font-size: 0.9rem; color: #374151; font-weight: 600; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: #d1d5db; }
.live-dot.connected { background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 10px; }
.presentation-empty { text-align: center; color: #6b7280; padding: 60px; }
.slides-frame { width: 100%; height: 70vh; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.nav-controls { display: flex; gap: 10px; margin-top: 12px; }
.btn-nav {
  flex: 1; padding: 10px 16px; border-radius: 10px; border: 2px solid #c7d2fe; background: #eef2ff;
  color: #4f46e5; font-weight: 700; font-size: 0.95rem; cursor: pointer;
}
.btn-nav:hover { background: #e0e7ff; }
.btn-primary, .btn-secondary {
  padding: 8px 16px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 0.88rem;
  border: none; cursor: pointer;
}
.btn-primary { background: #4f46e5; color: #fff; }
.btn-secondary { background: #eef2ff; color: #4f46e5; border: 2px solid #c7d2fe; }

@media (max-width: 640px) {
  .speaker-panel-page { padding: 14px; }
  .speaker-header { flex-direction: column; align-items: stretch; }
  .speaker-header-actions { margin-left: 0; }
  .speaker-header-actions .btn-secondary { flex: 1; }
  .slides-frame { height: 45vh; }
  .nav-controls { gap: 8px; }
  .btn-nav { padding: 16px; font-size: 1.05rem; min-height: 48px; }
}
</style>
