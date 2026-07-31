<template lang="pug">
.video-conference-standalone
  LoadingState(v-if="loading" message="Cargando videollamada...")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  VideoConferencePage(
    v-else-if="conference"
    :conference-id="conference.uuid"
    :ticketed="ticketed"
    :invite-alias="friendlyId"
  )
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import VideoConferencePage from '@/pages/conference/VideoConferencePage.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import { getActiveEventTypes, getConferenceByFriendlyId } from '@/services/api/usersApi'
import { eventTypeHasCapability } from '@/features/conferences/capabilities'
import type { Conference } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'VideoConferenceEntryPage',
  components: { FeedbackMessage, LoadingState, VideoConferencePage },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const friendlyId = String(route.params.friendlyId || '')
    const conference = ref<Conference | null>(null)
    const eventTypes = ref<Awaited<ReturnType<typeof getActiveEventTypes>>>([])
    const loading = ref(true)
    const error = ref('')

    const ticketed = computed(() => {
      if (!conference.value) return false
      return conference.value.seatingMode !== 'NONE'
        || eventTypeHasCapability(eventTypes.value, conference.value.eventTypeKey, 'TICKETING_GENERAL')
        || eventTypeHasCapability(eventTypes.value, conference.value.eventTypeKey, 'TICKETING_SEATED')
    })

    onMounted(async () => {
      await auth.waitForSessionBridge()
      try {
        const [loadedConference, loadedEventTypes] = await Promise.all([
          getConferenceByFriendlyId(friendlyId),
          getActiveEventTypes().catch(() => [])
        ])
        conference.value = loadedConference
        eventTypes.value = loadedEventTypes
      } catch {
        error.value = 'No se pudo cargar el evento. Intenta nuevamente más tarde.'
      } finally {
        loading.value = false
      }
    })

    return { conference, error, friendlyId, loading, ticketed }
  }
}
</script>

<style scoped>
.video-conference-standalone { min-height: 100vh; display: flex; flex-direction: column; background: var(--color-bg); }
.video-conference-standalone > .feedback-message { margin: 24px; }
</style>
