<template lang="pug">
.presentation-page
  .presentation-header
    h2 Presentación
    .live-status(v-if="wsConnected")
      span.live-dot.connected
      span En vivo · sigue al presentador automáticamente
    button.btn-presenter(v-if="presentationManager" type="button" @click="openPresenter") 🎛️ Abrir control del presentador
  .presentation-loading(v-if="loading") Verificando presentación...
  .presentation-empty(v-else-if="!ready")
    p El organizador aún no ha subido la presentación de esta conferencia.
  template(v-else)
    .preview-banner(v-if="!canParticipate")
      span Vista pública: primeras {{ previewSlideLimit }} diapositivas
      router-link(:to="`/c/${friendlyId}/ticket`") Regístrate y canjea tu boleto para continuar
    // Slidev's ES modules are loaded from the same protected presentation
    // path. `allow-same-origin` is required so the browser keeps the iframe
    // origin and sends the path-scoped HttpOnly access cookie to the module
    // and CSS requests. Without it, the sandbox gives the frame an opaque
    // origin (`null`), the assets are answered with `application/json` from
    // the access guard, and the deck stays blank with a CORS/MIME error.
    iframe.slides-frame(ref="slidesFrame" :src="slidesUrl" title="Slides" :sandbox="iframeSandbox")
    .presentation-actions
      a.btn-secondary(v-if="canParticipate && presentationSourceUrl" :href="presentationSourceUrl" target="_blank" rel="noopener") Ir al sitio de origen ↗
      router-link.btn-primary(v-if="canParticipate" :to="`/c/${friendlyId}/survey`") Dar mi opinión sobre la charla →
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getPresentationStatus, getSlidesUrl, getPresentationRootUrl, getSlidesPreviewUrl, getAudienceWsUrl, primePresentationAccess } from '@/services/api/presentationsApi'
import type { PresentationProvider } from '@/services/api/presentationsApi'
import { useAuthStore } from '@/features/auth/authStore'

const PREVIEW_SLIDE_LIMIT = 5

export default {
  name: 'PresentationPage',
  props: {
    conferenceId: String,
    presentationSourceUrl: String,
    accessGranted: { type: Boolean, default: false },
    presentationManager: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, presentationSourceUrl?: string, accessGranted?: boolean, presentationManager?: boolean }) {
    const route = useRoute()
    const auth = useAuthStore()
    const canParticipate = computed(() => props.accessGranted === true)
    const friendlyId = route.params.friendlyId as string
    const loading = ref(true)
    const ready = ref(false)
    const provider = ref<PresentationProvider>('MARP')
    const slidesUrl = ref('')
    const slidesFrame = ref<HTMLIFrameElement | null>(null)
    const iframeSandbox = computed(() => provider.value === 'MARP'
      ? 'allow-forms allow-presentation allow-same-origin'
      : 'allow-scripts allow-forms allow-presentation allow-same-origin')
    const wsConnected = ref(false)
    let ws: WebSocket | null = null
    let wsRetryTimer: ReturnType<typeof setTimeout> | null = null
    let wsClosedByUs = false

    function openPresenter() {
      if (!props.conferenceId || props.presentationManager !== true) return
      const url = `/dashboard/conferences/${props.conferenceId}/speaker`
      const child = window.open(url, '_blank')
      if (!child) window.location.assign(url)
    }

    function connectAudienceWs() {
      if (!props.conferenceId) return
      ws = new WebSocket(getAudienceWsUrl(props.conferenceId, auth.state.token))
      ws.onopen = () => { wsConnected.value = true }
      ws.onmessage = (event: MessageEvent) => {
        try {
          const msg = JSON.parse(event.data)
          const state = typeof msg.state === 'string' ? msg.state : msg.hash
          if (msg.type === 'slide' && typeof state === 'string' && slidesFrame.value) {
            applyNavigationState(state)
          }
        } catch (e: any) { /* ignorar mensajes malformados */ }
      }
      ws.onclose = () => {
        wsConnected.value = false
        if (wsClosedByUs) return
        wsRetryTimer = setTimeout(connectAudienceWs, 3000)
      }
      ws.onerror = () => ws!.close()
    }

    function applyNavigationState(state: string) {
      if (!slidesFrame.value) return
      try {
        const current = new URL(slidesUrl.value, window.location.origin)
        if (provider.value === 'MARP' || state.startsWith('#')) {
          current.hash = state
        } else {
          const marker = `/api/presentations/api/v1/conferences/${props.conferenceId}/presentation/`
          const target = new URL(state, window.location.origin + marker)
          if (target.pathname.startsWith(`${marker}presenter`)) {
            target.pathname = target.pathname.replace(`${marker}presenter`, marker.slice(0, -1))
          }
          current.pathname = target.pathname
          current.search = target.search
          current.hash = target.hash
        }

        // The WebSocket can replay the same state after a reconnect or when a
        // second presentation pod joins the room. Reassigning src for an
        // identical URL reloads the deck and makes Slidev jump to slide one.
        let frameUrl = slidesFrame.value.src
        try { frameUrl = slidesFrame.value.contentWindow?.location.href || frameUrl } catch { /* same-origin expected */ }
        if (frameUrl === current.href) return
        slidesFrame.value.src = current.href
      } catch (e: any) { /* same-origin esperado; si falla, la audiencia puede refrescar */ }
    }

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const status = await getPresentationStatus(props.conferenceId)
        ready.value = !!status.ready
        provider.value = status.provider === 'SLIDEV' ? 'SLIDEV' : 'MARP'
        if (ready.value) {
          if (canParticipate.value) {
            await primePresentationAccess(props.conferenceId, auth.state.token as string)
            slidesUrl.value = provider.value === 'SLIDEV'
              ? getPresentationRootUrl(props.conferenceId)
              : getSlidesUrl(props.conferenceId)
            connectAudienceWs()
          } else {
            slidesUrl.value = getSlidesPreviewUrl(props.conferenceId)
          }
        }
      } catch (e: any) { ready.value = false }
      finally { loading.value = false }

    })
    onBeforeUnmount(() => {
      if (wsRetryTimer) clearTimeout(wsRetryTimer)
      wsClosedByUs = true
      if (ws) ws.close()
    })

    return {
      friendlyId, loading, ready, slidesUrl, canParticipate, slidesFrame, iframeSandbox, wsConnected,
      openPresenter,
      previewSlideLimit: PREVIEW_SLIDE_LIMIT
    }
  }
}
</script>

<style scoped>
.presentation-page { padding: 24px; }
.presentation-header { margin-bottom: 16px; display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
h2 { margin: 0; color: #1e1b4b; }
.live-status { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; color: #6b7280; }
.btn-presenter { margin-left: auto; padding: 8px 14px; border: 2px solid #c7d2fe; border-radius: 8px; background: #eef2ff; color: #4f46e5; font-weight: 700; cursor: pointer; }
.btn-presenter:hover { background: #e0e7ff; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: #d1d5db; }
.live-dot.connected { background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,0.2); }
.presentation-loading, .presentation-empty { text-align: center; color: #6b7280; padding: 60px; }
.slides-frame {
  width: 100%;
  height: 70vh;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}
.presentation-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.btn-primary, .btn-secondary {
  padding: 10px 20px;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
}
.btn-primary { background: #4f46e5; color: #fff; }
.btn-primary:hover { background: #4338ca; }
.btn-secondary { background: #eef2ff; color: #4f46e5; border: 2px solid #c7d2fe; }
.btn-secondary:hover { background: #e0e7ff; }

.preview-banner {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  background: #fef3c7; color: #92400e; border-radius: 8px; padding: 8px 14px;
  font-size: 0.85rem; margin-bottom: 10px; flex-wrap: wrap;
}
.preview-banner a { color: #4f46e5; font-weight: 600; text-decoration: none; }
.preview-banner a:hover { text-decoration: underline; }

.preview-expired { text-align: center; padding: 60px 24px; }
.preview-expired p { color: #6b7280; margin-bottom: 24px; }
.login-actions { display: flex; gap: 10px; justify-content: center; }

@media (max-width: 640px) {
  .presentation-page { padding: 14px; }
  .slides-frame { height: 45vh; }
  .presentation-actions { flex-direction: column; }
  .presentation-actions a { text-align: center; }
}
</style>
