<template lang="pug">
.collab-notes-page
  LoadingState(v-if="loading" message="Cargando notas colaborativas...")
  template(v-else-if="isModeratorOnlyViewer")
    .published-banner
      span Estás viendo lo que va escribiendo el moderador (solo lectura).
      .format-toggle
        button.format-btn(type="button" :class="{ active: viewFormat === 'txt' }" @click="viewFormat = 'txt'") TXT
        button.format-btn(type="button" :class="{ active: viewFormat === 'markdown' }" @click="viewFormat = 'markdown'") Markdown
      span.update-state Actualización automática en {{ refreshCountdown }}s
    NoticeState(v-if="!available" title="Notas no disponibles" message="Intenta más tarde o contacta al organizador." tone="warning")
    .published-content(v-else)
      pre.published-notes(v-if="viewFormat === 'txt'") {{ noteText || 'El moderador todavía no escribió nada.' }}
      .published-notes.markdown-body(v-else-if="renderedMarkdown" v-html="renderedMarkdown")
      pre.published-notes(v-else) El moderador todavía no escribió nada.
    BaseButton.refresh-floating(
      type="button"
      :disabled="refreshing"
      :loading="refreshing"
      aria-label="Actualizar notas"
      title="Actualizar notas"
      @click="refreshLiveNotes"
    ) ↻
  NoticeState(v-else-if="!padUrl" title="Notas colaborativas no disponibles" message="Intenta más tarde o contacta al organizador." tone="warning")
  template(v-else)
    .notes-toolbar
      span(v-if="isIndividual") Notas individuales: se purgan después de vencer el evento y puedes exportarlas.
      span(v-else) Notas grupales: las notas se compartirán con los asistentes y quedarán en el ZIP de materiales.
      BaseButton(v-if="isIndividual" variant="secondary" size="sm" type="button" :loading="downloading" @click="downloadNotes") Descargar TXT
      FeedbackMessage(v-if="exportError" :message="exportError" tone="error")
    iframe.etherpad-frame(:src="padUrl" title="Notas" @load="stripSessionToken")
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { marked, Renderer } from 'marked'
import { getIntegrationConfig, getEventNotes, getEventNotesLive, exportEventNotes } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import NoticeState from '@/components/ui/NoticeState.vue'

const REFRESH_INTERVAL_SECONDS = 30

// Etherpad no tiene un modo "solo el moderador edita" real que podamos embeber para los
// asistentes (su único mecanismo de solo-lectura es una URL /p/r.XXXX distinta, vía su API
// getReadOnlyID) -- en vez de depender de eso, en MODERATOR_ONLY los asistentes ven una
// exportación del pad que se refresca sola (mismo patrón que WhiteboardPage.vue para Excalidraw),
// eligiendo verla como texto plano o como Markdown. El moderador escribe la sintaxis Markdown
// directamente como texto plano en el pad (el Etherpad desplegado no tiene el plugin ep_markdown,
// no hay formato enriquecido real que convertir), así que la vista Markdown renderiza el export de
// texto plano tal cual con `marked` en vez de re-derivarlo del HTML del pad. El moderador sigue con
// el iframe editable de siempre, sin cambios.
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
    const isModeratorOnlyViewer = computed(() => props.canvasAudienceMode === 'MODERATOR_ONLY'
      && props.canvasModerator !== true)

    const available = ref(true)
    const noteText = ref('')
    const viewFormat = ref<'txt' | 'markdown'>('txt')
    const refreshCountdown = ref(REFRESH_INTERVAL_SECONDS)
    const refreshing = ref(false)
    const markdownRenderer = new Renderer()
    markdownRenderer.html = () => ''
    const renderedMarkdown = computed(() => {
      if (!noteText.value.trim()) return ''
      const html = marked.parse(noteText.value, { async: false, breaks: true, renderer: markdownRenderer }) as string
      return html.replace(/href\s*=\s*["'](?!https?:\/\/|mailto:)[^"']*["']/gi, 'href="#"')
    })
    let refreshTimer: ReturnType<typeof setInterval> | null = null
    let countdownTimer: ReturnType<typeof setInterval> | null = null

    async function loadLiveNotes() {
      if (!props.conferenceId || !auth.state.token) return
      const live = await getEventNotesLive(props.conferenceId, auth.state.token)
      noteText.value = live.text
      available.value = true
      refreshCountdown.value = REFRESH_INTERVAL_SECONDS
    }

    // Tanto el refresh automático como el botón manual pasan por acá -- si una carga falla (ej.
    // Etherpad momentáneamente inalcanzable), marca `available = false` para mostrar el aviso,
    // pero el siguiente refresh (automático o manual) puede recuperarlo sin recargar la página.
    async function refreshLiveNotes() {
      if (refreshing.value) return
      refreshing.value = true
      try {
        await loadLiveNotes()
      } catch (e) {
        available.value = false
        console.error('CollabNotesPage: fallo actualizando las notas publicadas', e)
      } finally {
        refreshing.value = false
      }
    }

    function startLiveNotesPolling() {
      refreshTimer = setInterval(() => { void refreshLiveNotes() }, REFRESH_INTERVAL_SECONDS * 1000)
      countdownTimer = setInterval(() => {
        refreshCountdown.value = refreshCountdown.value <= 1
          ? REFRESH_INTERVAL_SECONDS : refreshCountdown.value - 1
      }, 1000)
    }

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
      if (isModeratorOnlyViewer.value) {
        // El polling arranca siempre, haya fallado o no la primera carga -- así un fallo
        // transitorio (ej. Etherpad tardando en responder al primer request tras un deploy) se
        // recupera solo en el próximo ciclo de 30s, sin depender de que el usuario haga click.
        await refreshLiveNotes()
        startLiveNotesPolling()
        loading.value = false
        return
      }
      try {
        const [config, pad] = await Promise.all([
          getIntegrationConfig(),
          getEventNotes(props.conferenceId, auth.state.token as string)
        ])
        if (config.etherpadBaseUrl) {
          // Etherpad esta detras de insightbloom-tools-gateway (exige sesion antes de
          // reenviar el request al pod real) — ib_token arranca esa sesion en el primer request.
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

    onBeforeUnmount(() => {
      if (refreshTimer) clearInterval(refreshTimer)
      if (countdownTimer) clearInterval(countdownTimer)
    })

    return {
      loading, padUrl, isIndividual, isModeratorOnlyViewer, downloading, exportError,
      downloadNotes, stripSessionToken,
      available, noteText, renderedMarkdown, viewFormat, refreshCountdown, refreshing, refreshLiveNotes
    }
  }
}
</script>

<style scoped>
.collab-notes-page { flex: 1; min-height: 480px; display: flex; flex-direction: column; position: relative; }
.notes-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 8px 12px; color: var(--color-text-secondary); font-size: .85rem; background: var(--color-surface-muted); border-bottom: 1px solid var(--color-border-subtle); }
.etherpad-frame { flex: 1; border: none; width: 100%; }

.published-banner { display: flex; gap: 16px; flex-wrap: wrap; align-items: center; padding: 10px 16px; font-size: 0.86rem; background: var(--color-primary-soft); color: var(--color-primary-dark); }
.update-state { color: var(--color-text-secondary); }
.format-toggle { display: flex; gap: 4px; }
.format-btn {
  padding: 4px 12px; border: 1.5px solid var(--color-border); border-radius: 999px;
  background: var(--color-surface); color: var(--color-text-secondary); cursor: pointer; font-size: .8rem; font-weight: 600;
}
.format-btn.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-on-primary); }
.published-content { flex: 1; min-height: 480px; overflow-y: auto; padding: 24px; background: var(--color-surface-muted); }
.published-notes {
  max-width: 860px; margin: 0 auto; padding: 24px; background: var(--color-surface);
  border: 1px solid var(--color-border-subtle); border-radius: 12px; box-shadow: 0 8px 24px rgba(15, 23, 42, .08);
  white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, monospace; font-size: .88rem;
  color: var(--color-text);
}
.markdown-body { white-space: normal; font-family: inherit; }
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) { margin: 1.2em 0 .5em; line-height: 1.3; }
.markdown-body :deep(p) { margin: 0 0 1em; line-height: 1.55; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 0 0 1em; padding-left: 1.4em; }
.markdown-body :deep(code) { font-family: ui-monospace, SFMono-Regular, monospace; background: var(--color-surface-muted); padding: 1px 5px; border-radius: 4px; font-size: .88em; }
.markdown-body :deep(pre) { background: var(--color-surface-muted); padding: 12px; border-radius: 8px; overflow-x: auto; }
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(blockquote) { margin: 0 0 1em; padding-left: 12px; border-left: 3px solid var(--color-border-subtle); color: var(--color-text-secondary); }
.markdown-body :deep(a) { color: var(--color-primary); }
.refresh-floating { position: fixed; right: 24px; bottom: 24px; width: 44px; height: 44px; padding: 0; border-radius: 999px; font-size: 1.45rem; box-shadow: 0 8px 18px rgba(79, 70, 229, .3); }
.refresh-floating:disabled { opacity: .65; cursor: wait; }
</style>
