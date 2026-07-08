<template lang="pug">
.conference-sub-nav
  router-link.crumb(to="/dashboard") ← Mis conferencias
  span.sep /
  span.crumb-current {{ conferenceName || conferenceId }}
  nav.sub-links
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/moderation/messages`") Moderación (mensajes)
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/moderation/words`") Moderación (palabras)
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/presentation`") Presentación
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/speaker`") Presentar
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/survey`") Encuesta
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'ConferenceSubNav',
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const conferenceName = ref('')

    onMounted(async () => {
      if (!props.conferenceId) return
      try {
        const c = await getConference(props.conferenceId, auth.state.token as string)
        conferenceName.value = c?.name || ''
      } catch (e: any) { conferenceName.value = '' }
    })

    return { conferenceName }
  }
}
</script>

<style scoped>
.conference-sub-nav { margin-bottom: 20px; }
.crumb { color: #4f46e5; text-decoration: none; font-size: 0.85rem; font-weight: 500; }
.crumb:hover { text-decoration: underline; }
.sep { margin: 0 6px; color: #9ca3af; }
.crumb-current { color: #6b7280; font-size: 0.85rem; }
.sub-links { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.sub-link {
  padding: 6px 14px; border: 1.5px solid #e5e7eb; border-radius: 20px; text-decoration: none;
  color: #374151; font-size: 0.82rem; font-weight: 500; transition: all 0.15s;
}
.sub-link:hover { border-color: #a5b4fc; color: #4f46e5; }
.sub-link.router-link-active { background: #4f46e5; color: #fff; border-color: #4f46e5; }
</style>
