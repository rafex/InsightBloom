<template lang="pug">
.presentation-page
  .presentation-header
    h2 Presentación
    .live-status(v-if="wsConnected")
      span.live-dot.connected
      span En vivo · sigue al presentador automáticamente
  .presentation-loading(v-if="loading") Verificando presentación...
  .presentation-empty(v-else-if="!ready")
    p El organizador aún no ha subido la presentación de esta conferencia.
  template(v-else)
    .preview-banner(v-if="!canParticipate")
      span Vista pública: primeras {{ previewSlideLimit }} diapositivas
      router-link(:to="`/c/${friendlyId}/ticket`") Regístrate y canjea tu boleto para continuar
    iframe.slides-frame(ref="slidesFrame" :src="slidesUrl" title="Slides")
    .presentation-actions
      a.btn-secondary(v-if="canParticipate && presentationSourceUrl" :href="presentationSourceUrl" target="_blank" rel="noopener") Ir al sitio de origen ↗
      router-link.btn-primary(v-if="canParticipate" :to="`/c/${friendlyId}/survey`") Dar mi opinión sobre la charla →
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getPresentationStatus, getSlidesUrl, getSlidesPreviewUrl, getAudienceWsUrl } from '@/services/api/presentationsApi'
import { useAuthStore } from '@/features/auth/authStore'

const PREVIEW_SLIDE_LIMIT = 5

export default {
  name: 'PresentationPage',
  props: {
    conferenceId: String,
    presentationSourceUrl: String,
    accessGranted: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, presentationSourceUrl?: string, accessGranted?: boolean }) {
    const route = useRoute()
    const auth = useAuthStore()
    const canParticipate = computed(() => props.accessGranted === true)
    const friendlyId = route.params.friendlyId as string
    const loading = ref(true)
    const ready = ref(false)
    const slidesUrl = ref('')
    const slidesFrame = ref<HTMLIFrameElement | null>(null)
    const wsConnected = ref(false)
    let ws: WebSocket | null = null
    let wsRetryTimer: ReturnType<typeof setTimeout> | null = null
    let wsClosedByUs = false

    function connectAudienceWs() {
      if (!props.conferenceId) return
      ws = new WebSocket(getAudienceWsUrl(props.conferenceId, auth.state.token))
      ws.onopen = () => { wsConnected.value = true }
      ws.onmessage = (event: MessageEvent) => {
        try {
          const msg = JSON.parse(event.data)
          if (msg.type === 'slide' && typeof msg.hash === 'string' && slidesFrame.value) {
            slidesFrame.value.contentWindow!.location.hash = msg.hash
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

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const status = await getPresentationStatus(props.conferenceId)
        ready.value = !!status.ready
        if (ready.value) {
          slidesUrl.value = canParticipate.value
            ? getSlidesUrl(props.conferenceId, auth.state.token)
            : getSlidesPreviewUrl(props.conferenceId)
          if (canParticipate.value) connectAudienceWs()
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
      friendlyId, loading, ready, slidesUrl, canParticipate, slidesFrame, wsConnected,
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
