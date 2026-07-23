<template lang="pug">
.speaker-panel-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")
  .speaker-header
    h2 Presentar
    .speaker-status
      span.live-dot(:class="{ connected: wsConnected || offlineMode }")
      span(v-if="wsConnected") 👀 {{ audienceCount }} viendo la presentación ahora
      span(v-else-if="offlineMode") 🔒 Modo offline del moderador
      span(v-else) Conectando...
      span.registered-count(v-if="registeredCount !== null") · 👥 {{ registeredCount }} registrados al evento
    .speaker-header-actions
      .utility-controls
        a.btn-secondary(v-if="sourceUrl" :href="sourceUrl" target="_blank" rel="noopener") Ir al sitio de origen ↗
        button.btn-secondary(@click="showQr = true") Mostrar QR
        button.btn-secondary(@click="shareRemoteControl") Compartir control remoto
        button.btn-secondary(v-if="ready && !offlineMode && !offlinePreparing" @click="prepareOffline") Preparar offline
        button.btn-secondary(v-if="offlinePackage && !offlineMode" @click="openOfflineCached") Abrir offline
        button.btn-secondary(v-if="offlineMode" @click="openOnlinePresentation") Volver online
        span.offline-preparing(v-if="offlinePreparing") Cifrando paquete…
        span.offline-error(v-if="offlineError") {{ offlineError }}
      .nav-controls(v-if="ready")
        button.btn-nav(type="button" @click="navigate('prev')") ← Anterior
        button.btn-nav(type="button" @click="navigate('next')") Siguiente →

  .presentation-empty(v-if="checkedStatus && !ready")
    p Aún no hay una presentación subida para esta conferencia.
    router-link.btn-primary(:to="`/dashboard/conferences/${conferenceId}/presentation`") Subir presentación

  template(v-else)
    p.hint(v-if="!offlineMode") Navega el deck con las flechas del teclado, haciendo clic dentro, o con los controles de navegación — la audiencia te sigue automáticamente.
    p.hint(v-else) La presentación está disponible localmente hasta {{ offlinePackage?.expiresAt }}. La sincronización en vivo se reanuda al volver a estar online.
    iframe.slides-frame(ref="slidesFrame" :src="slidesUrl" title="Slides" @load="onIframeLoad")

  QrCodeModal(v-if="showQr && friendlyId" :friendlyId="friendlyId" @close="showQr = false")
  QrCodeModal(v-if="showRemoteShare" :url="remoteShareUrl" @close="showRemoteShare = false")
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getPresentationStatus, getPresenterSlidesUrl, getPresenterWsUrl, createRemoteLinkToken, primePresentationAccess } from '@/services/api/presentationsApi'
import type { PresentationProvider } from '@/services/api/presentationsApi'
import { getOfflinePackage, prepareOfflinePresentation, openOfflinePresentation } from '@/services/offline/offlinePresentation'
import type { OfflinePackageRecord } from '@/services/offline/offlinePresentation'
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
    const provider = ref<PresentationProvider>('MARP')
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
    const offlinePackage = ref<OfflinePackageRecord | undefined>()
    const offlineMode = ref(false)
    const offlinePreparing = ref(false)
    const offlineError = ref('')

    let ws: WebSocket | null = null
    let wsRetryTimer: ReturnType<typeof setTimeout> | null = null
    let wsClosedByUs = false
    let hashPollTimer: ReturnType<typeof setInterval> | null = null
    let navigationObserverCleanup: (() => void) | null = null
    let lastState: string | null = null
    let restoringState = false

    const NAVIGATION_FALLBACK_POLL_MS = 2000

    function navigate(direction: NavDirection) {
      const spec = NAV_KEYS[direction]
      if (!spec || !slidesFrame.value) return
      try {
        const doc = slidesFrame.value.contentWindow!.document
        const eventInit = {
          key: spec.key,
          code: spec.key,
          keyCode: spec.keyCode,
          which: spec.keyCode,
          bubbles: true,
          cancelable: true,
        }

        // Slidev tracks pressed keys with useMagicKeys. Sending only keydown
        // leaves the reactive ArrowLeft/ArrowRight flag enabled, so its
        // autoRepeat loop keeps navigating until the deck reaches the end.
        // A button click represents one step, therefore always close the
        // synthetic key press with the matching keyup event.
        doc.dispatchEvent(new KeyboardEvent('keydown', eventInit))
        doc.dispatchEvent(new KeyboardEvent('keyup', eventInit))
      } catch (e: any) { /* same-origin esperado; si falla, no hay sync */ }
    }

    function navigationState(): string {
      try {
        const url = new URL(slidesFrame.value!.contentWindow!.location.href)
        if (provider.value === 'MARP') return url.hash || ''
        const marker = `/api/presentations/api/v1/conferences/${props.conferenceId}/presentation/`
        let state = url.pathname.startsWith(marker) ? url.pathname.slice(marker.length) : ''
        if (state === 'presenter') return ''
        if (state.startsWith('presenter/')) state = state.slice('presenter/'.length)
        return `${state}${url.search}${url.hash}`
      } catch (e: any) { return '' /* same-origin esperado; si falla, no hay sync */ }
    }

    function navigationUrlForState(state: string): URL {
      const current = new URL(slidesUrl.value, window.location.origin)
      if (provider.value === 'MARP' || state.startsWith('#')) {
        current.hash = state
        return current
      }

      const marker = `/api/presentations/api/v1/conferences/${props.conferenceId}/presentation/`
      const stateUrl = new URL(state || '', window.location.origin + marker)
      let relativePath = stateUrl.pathname.startsWith(marker)
        ? stateUrl.pathname.slice(marker.length)
        : stateUrl.pathname.replace(/^\/+/, '')
      if (relativePath === 'presenter') relativePath = ''
      if (relativePath.startsWith('presenter/')) relativePath = relativePath.slice('presenter/'.length)
      current.pathname = `${marker}presenter${relativePath ? `/${relativePath}` : ''}`
      current.search = stateUrl.search
      current.hash = stateUrl.hash
      return current
    }

    function restoreNavigationState(): boolean {
      if (!slidesFrame.value || lastState == null) return false
      try {
        const current = new URL(slidesFrame.value.contentWindow!.location.href)
        const target = navigationUrlForState(lastState)
        if (current.href === target.href) return false
        restoringState = true
        slidesFrame.value.contentWindow!.location.replace(target.href)
        return true
      } catch (e: any) {
        restoringState = false
        return false /* same-origin esperado; si falla, el polling sigue */
      }
    }

    function pollNavigation() {
      if (restoringState) return
      const state = navigationState()
      if (state !== lastState) {
        lastState = state
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'slide', hash: state, state }))
        }
      }
    }

    function installNavigationObservers() {
      navigationObserverCleanup?.()
      navigationObserverCleanup = null
      if (!slidesFrame.value) return

      try {
        const frameWindow = slidesFrame.value.contentWindow!
        const frameHistory = frameWindow.history
        let scheduled = false
        const notifyNavigation = () => {
          if (scheduled) return
          scheduled = true
          frameWindow.setTimeout(() => {
            scheduled = false
            pollNavigation()
          }, 0)
        }
        const onHistoryChange = () => notifyNavigation()
        const originalPushState = frameHistory.pushState
        const originalReplaceState = frameHistory.replaceState
        frameHistory.pushState = function (this: History, ...args: Parameters<History['pushState']>) {
          const result = originalPushState.apply(this, args)
          notifyNavigation()
          return result
        }
        frameHistory.replaceState = function (this: History, ...args: Parameters<History['replaceState']>) {
          const result = originalReplaceState.apply(this, args)
          notifyNavigation()
          return result
        }
        frameWindow.addEventListener('popstate', onHistoryChange)
        frameWindow.addEventListener('hashchange', onHistoryChange)
        navigationObserverCleanup = () => {
          frameWindow.removeEventListener('popstate', onHistoryChange)
          frameWindow.removeEventListener('hashchange', onHistoryChange)
          frameHistory.pushState = originalPushState
          frameHistory.replaceState = originalReplaceState
        }
      } catch (e: any) {
        /* El fallback de baja frecuencia cubre engines no accesibles. */
      }
    }

    function onIframeLoad() {
      // Si el iframe se recarga solo, retoma la URL completa donde estaba.
      // Slidev guarda el estado en la ruta/query, no únicamente en el hash.
      if (restoreNavigationState()) return
      restoringState = false
      installNavigationObservers()
      // La ruta principal es event-driven. Este fallback sólo cubre engines
      // que cambian su URL sin emitir eventos observables.
      if (hashPollTimer) clearInterval(hashPollTimer)
      hashPollTimer = setInterval(pollNavigation, NAVIGATION_FALLBACK_POLL_MS)
    }

    function connectPresenterWs() {
      if (offlineMode.value || !props.conferenceId || !auth.state.token) return
      ws = new WebSocket(getPresenterWsUrl(props.conferenceId, auth.state.token))
      ws.onopen = () => {
        wsConnected.value = true
        // Resincroniza de inmediato tras reconectar (no esperar a que cambie
        // el hash), por si el pod de presentaciones perdió el estado.
        if (lastState != null && ws && ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'slide', hash: lastState, state: lastState }))
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

    function stopPresenterWs() {
      if (wsRetryTimer) clearTimeout(wsRetryTimer)
      wsRetryTimer = null
      wsClosedByUs = true
      if (ws) ws.close()
      ws = null
      wsConnected.value = false
    }

    async function shareRemoteControl() {
      try {
        const token = await createRemoteLinkToken(props.conferenceId as string, auth.state.token as string)
        remoteShareUrl.value = `${window.location.origin}/c/${friendlyId.value}/remote?token=${token}`
        showRemoteShare.value = true
      } catch (e: any) { /* no se pudo generar el enlace */ }
    }

    async function openOfflineCached() {
      if (!offlinePackage.value) return
      try {
        offlineError.value = ''
        slidesUrl.value = await openOfflinePresentation(offlinePackage.value)
        offlineMode.value = true
        ready.value = true
        stopPresenterWs()
      } catch (e: any) {
        offlineError.value = 'El paquete offline expiró o no está disponible en este dispositivo.'
      }
    }

    async function openOnlinePresentation() {
      if (!props.conferenceId || !auth.state.token) return
      try {
        offlineError.value = ''
        await primePresentationAccess(props.conferenceId, auth.state.token, true)
        offlineMode.value = false
        wsClosedByUs = false
        slidesUrl.value = getPresenterSlidesUrl(props.conferenceId)
        connectPresenterWs()
      } catch (e: any) {
        offlineError.value = 'No se pudo volver al modo online. Comprueba la conexión.'
      }
    }

    async function prepareOffline() {
      if (!props.conferenceId || !auth.state.token || !auth.state.userUuid) return
      offlinePreparing.value = true
      offlineError.value = ''
      try {
        offlinePackage.value = await prepareOfflinePresentation(
          props.conferenceId,
          auth.state.token,
          auth.state.userUuid
        )
      } catch (e: any) {
        offlineError.value = 'No se pudo preparar la presentación offline. Revisa la configuración de firma y vuelve a intentar.'
      } finally {
        offlinePreparing.value = false
      }
    }

    onMounted(async () => {
      if (!props.conferenceId) {
        checkedStatus.value = true
        return
      }
      if (auth.state.userUuid) {
        try {
          offlinePackage.value = await getOfflinePackage(props.conferenceId, auth.state.userUuid)
        } catch (e: any) { /* IndexedDB/WASM no disponibles: se mantiene el modo online */ }
      }
      if (!navigator.onLine) {
        if (offlinePackage.value) await openOfflineCached()
        else offlineError.value = auth.state.userUuid
          ? 'No hay una copia offline preparada en este dispositivo.'
          : 'No se pudo identificar la sesión del moderador para el modo offline.'
        checkedStatus.value = true
        return
      }
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
        provider.value = status.provider === 'SLIDEV' ? 'SLIDEV' : 'MARP'
        if (ready.value) {
          await primePresentationAccess(props.conferenceId as string, auth.state.token as string, true)
          slidesUrl.value = getPresenterSlidesUrl(props.conferenceId as string)
          connectPresenterWs()
        }
      } catch (e: any) { ready.value = false }
      finally { checkedStatus.value = true }
    })

    onBeforeUnmount(() => {
      if (wsRetryTimer) clearTimeout(wsRetryTimer)
      if (hashPollTimer) clearInterval(hashPollTimer)
      navigationObserverCleanup?.()
      stopPresenterWs()
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
      showRemoteShare, remoteShareUrl, shareRemoteControl, sourceUrl, breadcrumbItems,
      offlinePackage, offlineMode, offlinePreparing, offlineError, prepareOffline, openOfflineCached, openOnlinePresentation
    }
  }
}
</script>

<style scoped>
.speaker-panel-page { padding: 24px; max-width: 960px; }
.speaker-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.speaker-header-actions { display: flex; flex-direction: column; gap: 8px; align-items: stretch; margin-left: auto; }
.utility-controls { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
h2 { margin: 0; color: #1e1b4b; }
.speaker-status { display: flex; align-items: center; gap: 6px; font-size: 0.9rem; color: #374151; font-weight: 600; flex-wrap: wrap; }
.registered-count { color: #6b7280; font-weight: 500; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: #d1d5db; }
.live-dot.connected { background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 10px; }
.presentation-empty { text-align: center; color: #6b7280; padding: 60px; }
.slides-frame { width: 100%; height: 70vh; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.nav-controls { display: flex; gap: 10px; }
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
.offline-preparing { color: #4f46e5; font-weight: 600; font-size: 0.85rem; }
.offline-error { color: #b91c1c; font-size: 0.85rem; font-weight: 600; }

@media (max-width: 640px) {
  .speaker-panel-page { padding: 14px; }
  .speaker-header { flex-direction: column; align-items: stretch; }
  .speaker-header-actions { margin-left: 0; }
  .utility-controls { justify-content: stretch; }
  .utility-controls .btn-secondary { flex: 1; }
  .slides-frame { height: 45vh; }
  .nav-controls { gap: 8px; }
  .btn-nav { padding: 16px; font-size: 1.05rem; min-height: 48px; }
}
</style>
