<template lang="pug">
.collab-notes-page
  LoadingState(v-if="loading" message="Cargando notas colaborativas...")
  .unavailable(v-else-if="!padUrl")
    p ⚠️ Las notas colaborativas no están disponibles en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  template(v-else)
    .notes-toolbar
      span(v-if="isIndividual") Notas individuales: se purgan después de vencer el evento y puedes exportarlas.
      span(v-else) Notas grupales: las notas se compartirán con los asistentes y quedarán en el ZIP de materiales.
      button.btn-outline(v-if="isIndividual" type="button" @click="downloadNotes" :disabled="downloading")
        span(v-if="downloading") Preparando...
        span(v-else) Descargar TXT
      span.error(v-if="exportError") {{ exportError }}
    iframe.etherpad-frame(:src="padUrl" title="Notas" @load="stripSessionToken")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getIntegrationConfig, getEventNotes, exportEventNotes } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import LoadingState from '@/components/ui/LoadingState.vue'

export default {
  name: 'CollabNotesPage',
  components: { LoadingState },
  props: {
    conferenceId: { type: String, default: '' },
    canvasAudienceMode: { type: String, default: '' },
    canvasModerator: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, canvasAudienceMode?: string, canvasModerator?: boolean }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const padUrl = ref('')
    const downloading = ref(false)
    const exportError = ref('')
    const isIndividual = computed(() => props.canvasAudienceMode === 'INDEPENDENT')

    async function downloadNotes() {
      downloading.value = true
      exportError.value = ''
      try {
        const blob = await exportEventNotes(props.conferenceId as string, auth.state.token as string, 'txt')
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = 'mis-notas.txt'
        link.click()
        URL.revokeObjectURL(url)
      } catch (e: any) {
        exportError.value = 'No se pudieron exportar tus notas.'
      } finally {
        downloading.value = false
      }
    }

    function stripSessionToken(event: Event) {
      const frame = event.currentTarget as HTMLIFrameElement | null
      if (!frame?.src) return
      const url = new URL(frame.src)
      if (!url.searchParams.has('ib_token')) return
      url.searchParams.delete('ib_token')
      frame.src = url.toString()
    }

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const [config, pad] = await Promise.all([
          getIntegrationConfig(),
          getEventNotes(props.conferenceId, auth.state.token as string)
        ])
        if (config.etherpadBaseUrl) {
          // Etherpad esta detras de insightbloom-tools-gateway (exige sesion antes de
          // reenviar el request al pod real) — ib_token arranca esa sesion en el primer request.
          const token = auth.state.token ? `?ib_token=${encodeURIComponent(auth.state.token)}` : ''
          padUrl.value = `${config.etherpadBaseUrl}/p/${pad.padId}${token}`
        }
      } catch (e: any) {
        padUrl.value = ''
      } finally {
        loading.value = false
      }
    })

    return { loading, padUrl, isIndividual, downloading, exportError, downloadNotes, stripSessionToken }
  }
}
</script>

<style scoped>
.collab-notes-page { flex: 1; min-height: 480px; display: flex; flex-direction: column; }
.notes-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 8px 12px; color: var(--color-text-secondary); font-size: .85rem; background: var(--color-surface-muted); border-bottom: 1px solid var(--color-border-subtle); }
.notes-toolbar .error { color: var(--color-danger-dark); }
.etherpad-frame { flex: 1; border: none; width: 100%; }
.unavailable { margin: 40px auto; text-align: center; color: var(--color-warning); background: var(--color-warning-soft); border: 1px solid var(--color-warning); border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: var(--color-warning); font-size: 0.85rem; margin-top: 6px; }
</style>
