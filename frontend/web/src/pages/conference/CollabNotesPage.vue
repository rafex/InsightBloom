<template lang="pug">
.collab-notes-page
  .loading-text(v-if="loading") Cargando notas colaborativas...
  .unavailable(v-else-if="!padUrl")
    p ⚠️ Las notas colaborativas no están disponibles en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  iframe.etherpad-frame(v-else :src="padUrl" title="Notas")
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getIntegrationConfig, getEventNotes } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'CollabNotesPage',
  props: {
    conferenceId: { type: String, default: '' }
  },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const padUrl = ref('')

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const [config, pad] = await Promise.all([
          getIntegrationConfig(),
          getEventNotes(props.conferenceId, auth.state.token as string)
        ])
        if (config.etherpadBaseUrl) {
          padUrl.value = `${config.etherpadBaseUrl}/p/${pad.padId}`
        }
      } catch (e: any) {
        padUrl.value = ''
      } finally {
        loading.value = false
      }
    })

    return { loading, padUrl }
  }
}
</script>

<style scoped>
.collab-notes-page { height: calc(100vh - 220px); min-height: 480px; display: flex; }
.etherpad-frame { flex: 1; border: none; width: 100%; }
.loading-text { padding: 40px; text-align: center; color: #6b7280; }
.unavailable { margin: 40px auto; text-align: center; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: #78350f; font-size: 0.85rem; margin-top: 6px; }
</style>
