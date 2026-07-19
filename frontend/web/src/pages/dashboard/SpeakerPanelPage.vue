<template lang="pug">
.speaker-panel-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")
  .speaker-header
    h2 Presentar
    .speaker-status
      span.live-dot(:class="{ connected: wsConnected }")
      span(v-if="wsConnected") 👀 {{ audienceCount }} viendo la presentación ahora
      span(v-else) Conectando...
      span.registered-count(v-if="registeredCount !== null") · 👥 {{ registeredCount }} registrados al evento
    .speaker-header-actions
      a.btn-secondary(v-if="sourceUrl" :href="sourceUrl" target="_blank" rel="noopener") Ir al sitio de origen ↗
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

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getPresentationStatus, getSlidesUrl, getPresenterWsUrl, createRemoteLinkToken } from '@/services/api/presentationsApi'
import { getConference, getRegisteredAttendeesCount } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import QrCodeModal from '@/components/QrCodeModal.vue'

type NavDirection = 'next' | 'prev'

const NAV_KEYS: Record<NavDirection, { key: string, keyCode: number }> = {
  next: { key: 'ArrowRight', keyCode: 39 },
  prev: { key: 'ArrowLeft', keyCode: 37 }
}

export default {
  name: 'SpeakerPanelPage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, QrCodeModal },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const checkedStatus = ref(false)
    const ready = ref(false)
    const slidesUrl = ref('')
    const slidesFrame = ref<HTMLIFrameElement | null>(null)
    const wsConnected = ref(false)
    const audienceCount = ref(0)
    const registeredCount = ref<number | null>(null)
    const showQr = ref(false)
    const friendlyId = ref('')
    const conferenceName = ref('')
    const showRemoteShare = ref(false)
    const remoteShareUrl = ref('')
    const sourceUrl = ref('')

    let ws: WebSocket | null = null
    let wsRetryTimer: ReturnType<typeof setTimeout> | null = null
    let wsClosedByUs = false
    let hashPollTimer: ReturnType<typeof setInterval> | null = null
    let lastHash: string | null = null

    const HASH_POLL_MS = 250

    function navigate(direction: NavDirection) {
      const spec = NAV_KEYS[direction]
      if (!spec || !slidesFrame.value) return
      try {
        const doc = slidesFrame.value.contentWindow!.document
        doc.dispatchEvent(new KeyboardEvent('keydown', { key: spec.key, keyCode: spec.keyCode, which: spec.keyCode, bubbles: true }))
      } catch (e: any) { /* same-origin esperado; si falla, no hay sync */ }
    }

    function pollHash() {
      let hash: string
      try {
        hash = slidesFrame.value?.contentWindow?.location?.hash || ''
      } catch (e: any) { return /* same-origin esperado; si falla, no hay sync */ }
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
          slidesFrame.value!.contentWindow!.location.hash = lastHash
        } catch (e: any) { /* same-origin esperado; si falla, no hay sync */ }
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
      ws.onmessage = (event: MessageEvent) => {
        try {
          const msg = JSON.parse(event.data)
          if (msg.type === 'count') audienceCount.value = msg.count
          else if (msg.type === 'nav') navigate(msg.direction)
        } catch (e: any) { /* ignorar */ }
      }
      ws.onclose = () => {
        wsConnected.value = false
        if (wsClosedByUs) return
        wsRetryTimer = setTimeout(connectPresenterWs, 3000)
      }
      ws.onerror = () => ws!.close()
    }

    async function shareRemoteControl() {
      try {
        const token = await createRemoteLinkToken(props.conferenceId as string, auth.state.token as string)
        remoteShareUrl.value = `${window.location.origin}/c/${friendlyId.value}/remote?token=${token}`
        showRemoteShare.value = true
      } catch (e: any) { /* no se pudo generar el enlace */ }
    }

    onMounted(async () => {
      try {
        const conf = await getConference(props.conferenceId as string, auth.state.token as string)
        friendlyId.value = conf?.friendlyId || ''
        sourceUrl.value = (conf?.presentationSourceUrl as string) || ''
        conferenceName.value = conf?.name || ''
      } catch (e: any) { /* el botón de QR simplemente no aparece */ }
      try {
        registeredCount.value = await getRegisteredAttendeesCount(props.conferenceId as string, auth.state.token as string)
      } catch (e: any) { /* el contador simplemente no aparece */ }
      try {
        const status = await getPresentationStatus(props.conferenceId as string)
        ready.value = !!status.ready
        if (ready.value) {
          slidesUrl.value = getSlidesUrl(props.conferenceId as string)
          connectPresenterWs()
        }
      } catch (e: any) { ready.value = false }
      finally { checkedStatus.value = true }
    })

    onBeforeUnmount(() => {
      if (wsRetryTimer) clearTimeout(wsRetryTimer)
      if (hashPollTimer) clearInterval(hashPollTimer)
      wsClosedByUs = true
      if (ws) ws.close()
    })

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Presentar' }
    ])

    return {
      checkedStatus, ready, slidesUrl, slidesFrame,
      wsConnected, audienceCount, registeredCount, showQr, friendlyId, onIframeLoad, navigate,
      showRemoteShare, remoteShareUrl, shareRemoteControl, sourceUrl, breadcrumbItems
    }
  }
}
</script>

<style scoped>
.speaker-panel-page { padding: 24px; max-width: 960px; }
.speaker-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.speaker-header-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-left: auto; }
h2 { margin: 0; color: #1e1b4b; }
.speaker-status { display: flex; align-items: center; gap: 6px; font-size: 0.9rem; color: #374151; font-weight: 600; flex-wrap: wrap; }
.registered-count { color: #6b7280; font-weight: 500; }
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
