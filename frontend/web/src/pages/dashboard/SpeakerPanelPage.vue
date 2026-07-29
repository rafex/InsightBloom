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
        a.link-btn.link-btn-secondary(v-if="sourceUrl" :href="sourceUrl" target="_blank" rel="noopener") Ir al sitio de origen ↗
        BaseButton(variant="secondary" @click="showQr = true") Mostrar QR
        BaseButton(variant="secondary" @click="shareRemoteControl") Compartir control remoto
        BaseButton(variant="secondary" v-if="ready && !offlineMode && !offlinePreparing" @click="prepareOffline") Preparar offline
        BaseButton(variant="secondary" v-if="offlinePackage && !offlineMode" @click="openOfflineCached") Abrir offline
        BaseButton(variant="secondary" v-if="offlineMode" @click="openOnlinePresentation") Volver online
        span.offline-preparing(v-if="offlinePreparing") Cifrando paquete…
        span.offline-error(v-if="offlineError") {{ offlineError }}
      .nav-controls(v-if="ready")
        button.btn-nav(type="button" @click="navigate('prev')") ← Anterior
        button.btn-nav(type="button" @click="navigate('next')") Siguiente →

  .presentation-empty(v-if="checkedStatus && !ready")
    p Aún no hay una presentación subida para esta conferencia.
    router-link.link-btn.link-btn-primary(:to="`/dashboard/conferences/${conferenceId}/presentation`") Subir presentación

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
import BaseButton from '@/components/ui/BaseButton.vue'

type NavDirection = 'next' | 'prev'

const NAV_KEYS: Record<NavDirection, { key: string, keyCode: number }> = {
  next: { key: 'ArrowRight', keyCode: 39 },
  prev: { key: 'ArrowLeft', keyCode: 37 }
}

export default {
  name: 'SpeakerPanelPage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, QrCodeModal, BaseButton },
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
        if (state === 'moderator/presenter') return ''
        if (state.startsWith('moderator/presenter/')) state = state.slice('moderator/presenter/'.length)
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
      if (relativePath === 'moderator/presenter') relativePath = ''
      if (relativePath.startsWith('moderator/presenter/')) relativePath = relativePath.slice('moderator/presenter/'.length)
      current.pathname = `${marker}moderator/presenter${relativePath ? `/${relativePath}` : ''}`
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
.speaker-panel-page { padding: 24px; max-width: 1400px; margin: 0 auto; }
.speaker-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
/* Un solo renglón de herramienta: utilidades (QR/control remoto/offline) + navegación
 * comparten fila y se acomodan una junto a otra -- antes iban en columna, cada cluster
 * ocupando su propio renglón a lo ancho completo, lo que dejaba "Anterior"/"Siguiente"
 * estirados y separados sin necesidad (reportado 2026-07-28 al ver el modo presentador
 * ya funcionando: "los botones... están mal distribuidos"). */
.speaker-header-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-left: auto; }
.utility-controls { display: flex; gap: 8px; flex-wrap: wrap; }
h2 { margin: 0; color: var(--color-heading); }
/* flex-basis 100% + min-height: el conteo de audiencia y el total de registrados llegan
 * async (WS y fetch aparte) y su texto entra y sale/cambia de largo. Sin esto, la fila
 * de speaker-status a veces cabía junto al título y a veces no, y ese cambio de wrap
 * corría hacia arriba/abajo la fila de botones debajo (reportado 2026-07-28: "genera un
 * brinco en la pantalla" entre "Presentar" y los botones). Forzarla a su propio renglón
 * de ancho completo, con una altura mínima fija, hace que los botones nunca se muevan. */
.speaker-status { display: flex; align-items: center; gap: 6px; font-size: 0.9rem; color: var(--color-text-secondary); font-weight: 600; flex-wrap: wrap; flex: 1 1 100%; min-height: 22px; }
.registered-count { color: var(--color-text-muted); font-weight: 500; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--color-border); }
.live-dot.connected { background: var(--color-success); box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 10px; }
.presentation-empty { text-align: center; color: var(--color-text-muted); padding: 60px; }
/* El presentador de Slidev muestra diapositiva actual + siguiente + notas en un layout
 * de varias columnas propio -- necesita más alto Y más ancho que el visor público de
 * una sola diapositiva para que las notas no queden cortadas con scroll horizontal
 * (visto en vivo 2026-07-28: la columna de notas se recortaba contra el borde). Por
 * eso esta página ya no limita el ancho a 960px (ver .speaker-panel-page) y el iframe
 * sube de 70vh a 82vh. */
.slides-frame { width: 100%; height: 82vh; border: 1px solid var(--color-border-subtle); border-radius: 12px; background: var(--color-surface); }
.nav-controls { display: flex; gap: 8px; }
.btn-nav {
  flex: 0 0 auto; padding: 10px 18px; border-radius: 10px; border: 2px solid var(--color-primary-border); background: var(--color-primary-soft);
  color: var(--color-primary); font-weight: 700; font-size: 0.95rem; cursor: pointer; white-space: nowrap;
}
.btn-nav:hover { background: var(--color-primary-soft); }
.offline-preparing { color: var(--color-primary); font-weight: 600; font-size: 0.85rem; }
.offline-error { color: var(--color-danger-dark); font-size: 0.85rem; font-weight: 600; }

@media (max-width: 640px) {
  .speaker-panel-page { padding: 14px; }
  .speaker-header { flex-direction: column; align-items: stretch; }
  .speaker-header-actions { flex-direction: column; align-items: stretch; margin-left: 0; }
  .utility-controls { justify-content: stretch; }
  .utility-controls .link-btn-secondary, .utility-controls .base-btn { flex: 1; }
  .nav-controls .btn-nav { flex: 1; }
  .slides-frame { height: 45vh; }
  .nav-controls { gap: 8px; }
  .btn-nav { padding: 16px; font-size: 1.05rem; min-height: 48px; }
}
</style>
