<template lang="pug">
.on-demand-manage-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")
  h2 Video on-demand

  LoadingState(v-if="loading" message="Cargando configuración…")
  .on-demand-content(v-else)
    .config-card
      h3 Video
      .form-group
        label Proveedor
        select.provider-select(v-model="provider")
          option(value="") Ninguno (desactivado)
          option(value="YOUTUBE") YouTube
          option(value="PEERTUBE") PeerTube (nodo federado público)
      template(v-if="provider")
        .form-group
          label URL del video
          input.url-input(
            v-model="url"
            type="url"
            :placeholder="provider === 'YOUTUBE' ? 'https://www.youtube.com/watch?v=...' : 'https://tu-nodo-peertube.example/w/...'"
          )
          p.field-hint Pegá la URL normal del video (no hace falta que sea de embed).
        .preview(v-if="embedUrl")
          iframe.preview-frame(:src="embedUrl" title="Vista previa del video" allowfullscreen)
        p.field-hint.error(v-else-if="url.trim()") No se reconoce el formato de esa URL de {{ provider === 'YOUTUBE' ? 'YouTube' : 'PeerTube' }}.

    .config-card(v-if="provider")
      h3 Sugerencias por momento del video
      p.hint Marcá en qué minuto del video conviene sugerirle al asistente abrir otra herramienta (encuesta, IDE, etc.). Es solo una sugerencia — no interrumpe el video.
      .cue-point-row(v-for="(cue, index) in cuePoints" :key="index")
        input.cue-time(v-model.number="cue.minutes" type="number" min="0" placeholder="Min")
        input.cue-time(v-model.number="cue.seconds" type="number" min="0" max="59" placeholder="Seg")
        input.cue-label(v-model="cue.label" type="text" placeholder="Ej: Abrí la encuesta ahora")
        select.cue-tool(v-model="cue.toolPath")
          option(v-for="opt in toolOptions" :key="opt.path" :value="opt.path") {{ opt.label }}
        BaseButton(variant="ghost" size="sm" type="button" @click="removeCuePoint(index)") Quitar
      .cue-point-actions
        BaseButton(variant="secondary" size="sm" type="button" @click="addCuePoint") + Agregar sugerencia
        BaseButton(variant="ghost" size="sm" type="button" @click="showMarkdownLoader = !showMarkdownLoader") {{ showMarkdownLoader ? 'Ocultar carga por Markdown' : 'Cargar como Markdown' }}

      .markdown-loader(v-if="showMarkdownLoader")
        p.hint Pegá una lista, una sugerencia por línea, formato #[code - M:SS texto → herramienta] (también aceptás #[code ->] en vez de la flecha, y el nombre de la herramienta en vez del identificador).
        textarea.markdown-textarea(
          v-model="markdownInput"
          rows="6"
          :placeholder="markdownPlaceholder"
        )
        BaseButton(variant="secondary" size="sm" type="button" @click="loadFromMarkdown") Cargar y reemplazar sugerencias
        ul.markdown-errors(v-if="markdownErrors.length")
          li(v-for="(err, i) in markdownErrors" :key="i") {{ err }}

    BaseButton(:disabled="saving" :loading="saving" @click="save") Guardar
    FeedbackMessage(v-if="error" :message="error" tone="error")
    FeedbackMessage(v-if="success" message="¡Configuración guardada!" tone="success")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getConference, getActiveEventTypes, setOnDemandVideo } from '@/services/api/usersApi'
import type { EventType, EventCapability } from '@/services/api/types'
import { eventTypeHasCapability } from '@/features/conferences/capabilities'
import { toEmbedUrl, parseCuePointsMarkdown, type CuePointRow } from '@/features/conferences/onDemandVideo'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

// Catalogo estatico de pestañas publicas a las que puede apuntar una sugerencia -- filtrado por
// las capabilities habilitadas para el tipo de evento de esta conferencia (misma fuente de
// verdad que ConferenceToolsNav, ver features/conferences/capabilities.ts).
const TOOL_CATALOG: Array<{ path: string, label: string, capability: EventCapability }> = [
  { path: 'doubts', label: 'Nube de dudas', capability: 'WORD_CLOUD' },
  { path: 'topics', label: 'Nube de temas', capability: 'WORD_CLOUD' },
  { path: 'presentation', label: 'Presentación', capability: 'PRESENTATION' },
  { path: 'survey', label: 'Encuesta', capability: 'SURVEY' },
  { path: 'ide', label: 'IDE', capability: 'CODE_IDE' },
  { path: 'diagrams', label: 'Diagramas', capability: 'DIAGRAMMING' },
  { path: 'notes', label: 'Notas colaborativas', capability: 'COLLAB_NOTES' },
  { path: 'whiteboard', label: 'Pizarra', capability: 'WHITEBOARD' }
]

export default {
  name: 'OnDemandVideoManagePage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, BaseButton, FeedbackMessage, LoadingState },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const saving = ref(false)
    const error = ref('')
    const success = ref(false)
    const conferenceName = ref('')
    const provider = ref<'' | 'YOUTUBE' | 'PEERTUBE'>('')
    const url = ref('')
    const cuePoints = ref<CuePointRow[]>([])
    const eventTypes = ref<EventType[]>([])
    const eventTypeKey = ref('')
    const showMarkdownLoader = ref(false)
    const markdownInput = ref('')
    const markdownErrors = ref<string[]>([])

    const embedUrl = computed(() => toEmbedUrl(provider.value || null, url.value.trim() || null))

    const toolOptions = computed(() => TOOL_CATALOG.filter(opt =>
      eventTypeHasCapability(eventTypes.value, eventTypeKey.value, opt.capability)))

    function addCuePoint() {
      cuePoints.value.push({ minutes: 0, seconds: 0, label: '', toolPath: toolOptions.value[0]?.path || 'survey' })
    }

    function removeCuePoint(index: number) {
      cuePoints.value.splice(index, 1)
    }

    const markdownPlaceholder = [
      '- 0:15 Abrí la encuesta ahora → survey',
      '- 2:30 Mirá la nube de dudas → doubts',
      '- 5:00 Probá el IDE → ide'
    ].join('\n')

    function loadFromMarkdown() {
      const result = parseCuePointsMarkdown(markdownInput.value, toolOptions.value)
      if ('errors' in result) {
        markdownErrors.value = result.errors
        return
      }
      markdownErrors.value = []
      cuePoints.value = result.cuePoints
    }

    async function load() {
      loading.value = true
      try {
        const [conference, types] = await Promise.all([
          getConference(props.conferenceId as string, auth.state.token as string),
          getActiveEventTypes()
        ])
        conferenceName.value = conference.name || ''
        eventTypeKey.value = conference.eventTypeKey || ''
        eventTypes.value = types
        provider.value = conference.onDemandVideoProvider || ''
        url.value = conference.onDemandVideoUrl || ''
        cuePoints.value = (conference.onDemandCuePoints || []).map(cue => ({
          minutes: Math.floor(cue.atSeconds / 60),
          seconds: cue.atSeconds % 60,
          label: cue.label,
          toolPath: cue.toolPath
        }))
      } catch (e: any) {
        error.value = 'No se pudo cargar la configuración.'
      } finally {
        loading.value = false
      }
    }

    async function save() {
      saving.value = true
      error.value = ''
      success.value = false
      try {
        const payload = cuePoints.value
          .filter(cue => cue.label.trim() && cue.toolPath)
          .map(cue => ({
            atSeconds: Math.max(0, (cue.minutes || 0) * 60 + (cue.seconds || 0)),
            label: cue.label.trim(),
            toolPath: cue.toolPath
          }))
        await setOnDemandVideo(
          props.conferenceId as string,
          provider.value || null,
          provider.value ? (url.value.trim() || null) : null,
          payload,
          auth.state.token as string
        )
        success.value = true
      } catch (e: any) {
        const apiError = e.response?.data?.error || {}
        const messages: Record<string, string> = {
          provider_invalid: 'Proveedor inválido.',
          url_invalid: 'La URL del video es obligatoria y debe empezar con https://.',
          url_too_long: 'La URL es demasiado larga.',
          cue_point_label_invalid: 'Cada sugerencia necesita un texto.',
          cue_point_tool_path_invalid: 'Cada sugerencia necesita una herramienta destino.',
          cue_point_at_seconds_invalid: 'El momento del video no puede ser negativo.',
          too_many_cue_points: 'Hay demasiadas sugerencias configuradas.'
        }
        error.value = messages[apiError.code] || 'No se pudo guardar la configuración.'
      } finally {
        saving.value = false
      }
    }

    onMounted(load)

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Video on-demand' }
    ])

    return {
      loading, saving, error, success, provider, url, embedUrl, cuePoints, toolOptions,
      addCuePoint, removeCuePoint, save, breadcrumbItems,
      showMarkdownLoader, markdownInput, markdownErrors, markdownPlaceholder, loadFromMarkdown
    }
  }
}
</script>

<style scoped>
.on-demand-manage-page { padding: 24px; max-width: 680px; }
h2 { color: var(--color-heading); margin-bottom: 20px; }
.config-card {
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
h3 { margin: 0 0 8px; color: var(--color-heading); }
.hint { color: var(--color-text-muted); font-size: 0.88rem; margin-bottom: 12px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-weight: 600; font-size: 0.88rem; color: var(--color-text-secondary); margin-bottom: 6px; }
.provider-select, .url-input {
  width: 100%; padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px;
  font-size: 0.95rem; box-sizing: border-box; background: var(--color-surface);
}
.url-input:focus-visible, .provider-select:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; border-color: var(--color-primary); }
.field-hint { margin: 6px 0 0; font-size: 0.8rem; color: var(--color-text-muted); }
.field-hint.error { color: var(--color-danger); }
.preview { margin-top: 12px; }
.preview-frame { width: 100%; aspect-ratio: 16 / 9; border: 1px solid var(--color-border-subtle); border-radius: 8px; }
.cue-point-row {
  display: grid; grid-template-columns: 64px 64px 1fr auto auto; gap: 8px; align-items: center; margin-bottom: 10px;
}
.cue-time, .cue-label, .cue-tool {
  padding: 8px 10px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 0.9rem; box-sizing: border-box;
}
.cue-point-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.markdown-loader { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--color-border-subtle); }
.markdown-textarea {
  width: 100%; padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px;
  font-size: 0.85rem; font-family: monospace; box-sizing: border-box; background: var(--color-surface);
  resize: vertical; margin-bottom: 10px;
}
.markdown-textarea:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; border-color: var(--color-primary); }
.markdown-errors {
  margin: 10px 0 0; padding-left: 20px; color: var(--color-danger); font-size: 0.82rem;
}
.markdown-errors li { margin-bottom: 4px; }

@media (max-width: 600px) {
  .on-demand-manage-page { padding: 14px; }
  .cue-point-row { grid-template-columns: 1fr 1fr; grid-template-areas: "min sec" "label label" "tool tool" "remove remove"; }
  .cue-point-row > :nth-child(1) { grid-area: min; }
  .cue-point-row > :nth-child(2) { grid-area: sec; }
  .cue-point-row > :nth-child(3) { grid-area: label; }
  .cue-point-row > :nth-child(4) { grid-area: tool; }
  .cue-point-row > :nth-child(5) { grid-area: remove; }
}
</style>
