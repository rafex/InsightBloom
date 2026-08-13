<template lang="pug">
.collab-notes-page
  LoadingState(v-if="loading" message="Cargando notas colaborativas...")
  NoticeState(v-else-if="!padUrl" title="Notas colaborativas no disponibles" message="Intenta más tarde o contacta al organizador." tone="warning")
  template(v-else)
    .notes-toolbar
      span(v-if="isIndividual") Notas individuales: se purgan después de vencer el evento y puedes exportarlas.
      span(v-else-if="readOnly") Notas del moderador: estás viendo la publicación en modo lectura.
      span(v-else) Notas grupales: las notas se compartirán con los asistentes y quedarán en el ZIP de materiales.
      BaseButton(v-if="isIndividual" variant="secondary" size="sm" type="button" :loading="downloading" @click="downloadNotes") Descargar TXT
      FeedbackMessage(v-if="exportError" :message="exportError" tone="error")
    iframe.etherpad-frame(:src="padUrl" title="Notas" @load="stripSessionToken")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getIntegrationConfig, getEventNotes, exportEventNotes } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import NoticeState from '@/components/ui/NoticeState.vue'

export default {
  name: 'CollabNotesPage',
  components: { BaseButton, FeedbackMessage, LoadingState, NoticeState },
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
    // El backend es quien decide y garantiza el modo lectura (ver GetOrCreateEventPadUseCase):
    // en MODERATOR_ONLY, a los no-moderadores les devuelve el ID de solo-lectura real de
    // Etherpad (getReadOnlyID), no el pad editable -- un query param "readonly" en la URL nunca
    // funcionó porque Etherpad no lo interpreta (bug reportado 2026-08-13). `readOnly` acá solo
    // se usa para el texto informativo de la toolbar.
    const readOnly = ref(false)

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
        readOnly.value = pad.readOnly
        if (config.etherpadBaseUrl) {
          // Etherpad esta detras de insightbloom-tools-gateway (exige sesion antes de
          // reenviar el request al pod real) — ib_token arranca esa sesion en el primer request.
          // pad.padId ya viene resuelto por el backend: el UUID real y editable del evento, o el
          // ID de solo-lectura de Etherpad cuando corresponde -- no hace falta ningún parámetro
          // extra acá.
          const params = new URLSearchParams()
          if (auth.state.token) params.append('ib_token', auth.state.token)
          const query = params.toString()
          padUrl.value = `${config.etherpadBaseUrl}/p/${pad.padId}${query ? `?${query}` : ''}`
        }
      } catch (e: any) {
        padUrl.value = ''
      } finally {
        loading.value = false
      }
    })

    return { loading, padUrl, isIndividual, readOnly, downloading, exportError, downloadNotes, stripSessionToken }
  }
}
</script>

<style scoped>
.collab-notes-page { flex: 1; min-height: 480px; display: flex; flex-direction: column; }
.notes-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 8px 12px; color: var(--color-text-secondary); font-size: .85rem; background: var(--color-surface-muted); border-bottom: 1px solid var(--color-border-subtle); }
.etherpad-frame { flex: 1; border: none; width: 100%; }
</style>
