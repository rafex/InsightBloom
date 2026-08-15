<template lang="pug">
main.on-demand-session#main-content(tabindex="-1")
  LoadingState(v-if="loading" message="Cargando video…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="!accessGranted" message="Regístrate y canjea tu boleto para ver el video de este evento.")
  button.session-close.session-close-fallback(v-if="loading || error || !accessGranted" type="button" @click="closeWindow") Cerrar ventana y volver al evento
  template(v-else-if="conference")
    header.session-header
      h1 {{ conference.name }}
      .session-actions
        router-link.session-link(:to="`/c/${friendlyId}/on-demand`") Volver al evento
        button.session-close(type="button" @click="closeWindow") Cerrar ventana
    OnDemandVideoPlayer(
      :conference-id="conference.conferenceId || conference.uuid"
      :provider="conference.onDemandVideoProvider"
      :video-url="conference.onDemandVideoUrl"
    )
</template>

<script lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import OnDemandVideoPlayer from '@/components/OnDemandVideoPlayer.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { getActiveEventTypes, getConferenceAccess, getConferenceByFriendlyId } from '@/services/api/usersApi'
import type { Conference } from '@/services/api/types'

export default {
  name: 'OnDemandVideoSessionPage',
  components: { EmptyState, FeedbackMessage, LoadingState, OnDemandVideoPlayer },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const friendlyId = route.params.friendlyId as string
    const conference = ref<Conference | null>(null)
    const loading = ref(true)
    const error = ref('')
    const accessGranted = ref(false)

    async function loadSession() {
      try {
        await auth.waitForSessionBridge()
        const conf = await getConferenceByFriendlyId(friendlyId)
        const [access, eventTypes] = await Promise.all([
          getConferenceAccess(conf.uuid, auth.state.token),
          getActiveEventTypes().catch(() => [])
        ])
        const active = conf.status !== 'CLOSED' && access.eventActive !== false && access.eventStatus !== 'CLOSED'
        const hasPrivateAccess = !access.ticketRequired || access.hasAccess
        const eventType = eventTypes.find((type) => type.key === conf.eventTypeKey)
        const videoCapabilityAllowed = !eventType || eventType.capabilities.includes('ON_DEMAND_VIDEO')
        conference.value = conf
        accessGranted.value = active
          && hasPrivateAccess
          && videoCapabilityAllowed
          && !!conf.onDemandVideoUrl
          && (conf.onDemandVideoProvider === 'YOUTUBE' || conf.onDemandVideoProvider === 'PEERTUBE')
        notifyOpener('insightbloom-on-demand-popup-ready')
      } catch {
        error.value = 'No se pudo cargar el video de la conferencia.'
      } finally {
        loading.value = false
      }
    }

    function closeWindow() {
      notifyOpener('insightbloom-on-demand-popup-closed')
      window.close()
    }

    function notifyOpener(type: 'insightbloom-on-demand-popup-ready' | 'insightbloom-on-demand-popup-closed') {
      if (!conference.value || !window.opener || window.opener === window) return
      window.opener.postMessage({
        type,
        conferenceId: conference.value.conferenceId || conference.value.uuid
      }, window.location.origin)
    }

    function handleBeforeUnload() {
      notifyOpener('insightbloom-on-demand-popup-closed')
    }

    onMounted(() => {
      window.addEventListener('beforeunload', handleBeforeUnload)
      void loadSession()
    })
    onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
    return { friendlyId, conference, loading, error, accessGranted, closeWindow }
  }
}
</script>

<style scoped>
.on-demand-session { min-height: 100vh; display: flex; flex-direction: column; gap: 14px; padding: 18px; color: var(--color-text); background: var(--color-bg); }
.session-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h1 { margin: 0; color: var(--color-heading); font-size: 1.1rem; }
.session-actions { display: flex; align-items: center; gap: 8px; }
.session-link, .session-close { border: 0; border-radius: 6px; padding: 6px 10px; font-size: 0.82rem; text-decoration: none; color: var(--color-text-secondary); background: var(--color-surface-muted); cursor: pointer; }
.session-link:hover, .session-close:hover { color: var(--color-primary); }
.on-demand-session :deep(.on-demand-player) { flex: 1; min-height: 0; height: calc(100vh - 76px); border-radius: 10px; overflow: hidden; }
@media (max-width: 640px) { .on-demand-session { padding: 10px; } .session-header { align-items: flex-start; flex-direction: column; } .on-demand-session :deep(.on-demand-player) { height: calc(100vh - 120px); } }
</style>
