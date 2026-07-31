<template lang="pug">
.diagramming-page
  LoadingState(v-if="loading" message="Cargando pizarra de diagramas...")
  template(v-else-if="isModeratorOnlyViewer")
    .published-banner
      StatusBadge(status="VISIBLE" label="Vista publicada")
      StatusBadge(v-if="streamConnected" status="ACTIVE" label="Actualización en vivo")
      span.update-state Actualización automática en {{ refreshCountdown }}s
      span.update-state(v-if="publishedUpdatedAt") Publicado {{ publishedUpdatedAt }}
    .published-content
      img.published-diagram(v-if="publishedSvg" :src="publishedSvg" alt="Diagrama publicado por el moderador")
      .published-empty(v-else)
        p El moderador todavía no ha publicado un diagrama.
        p.hint Esta vista se actualizará automáticamente cuando exista una publicación.
    BaseButton.refresh-floating(
      type="button"
      :class="{ 'has-update': newVersionAvailable }"
      :disabled="refreshing"
      :loading="refreshing"
      aria-label="Actualizar diagrama publicado"
      title="Actualizar diagrama publicado"
      @click="refreshPublishedDiagram"
    ) ↻
  NoticeState(v-else-if="!drawioUrl" title="Diagramas no disponibles" message="Intenta más tarde o contacta al organizador." tone="warning")
  template(v-else)
    .save-banner(v-if="!canPersist" class="save-banner-info") Tu edición es local y no se conservará; solo el material del moderador se persiste.
    FeedbackMessage.save-banner(v-if="saveError" :message="`No se pudo publicar el diagrama: ${saveError}`" tone="error")
    FeedbackMessage.save-banner(v-else-if="saveStatus === 'saved'" message="Diagrama publicado" tone="success")
    iframe.drawio-frame(ref="frameRef" :src="drawioUrl" title="Diagramas" allow="clipboard-write" @load="stripSessionToken")
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getIntegrationConfig, getEventDiagram, saveEventDiagram, streamEventDiagram, AuthenticatedEventStream } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import NoticeState from '@/components/ui/NoticeState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

const REFRESH_INTERVAL_SECONDS = 30

export default {
  name: 'DiagrammingPage',
  components: { BaseButton, FeedbackMessage, LoadingState, NoticeState, StatusBadge },
  props: {
    conferenceId: { type: String, default: '' },
    canvasAudienceMode: { type: String, default: '' },
    canvasModerator: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, canvasAudienceMode?: string, canvasModerator?: boolean }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const drawioBaseUrl = ref('')
    const frameRef = ref<HTMLIFrameElement | null>(null)
    const saveError = ref('')
    const saveStatus = ref<'' | 'saved' | 'error'>('')
    const publishedSvg = ref<string | null>(null)
    const publishedVersion = ref(0)
    const publishedUpdatedAt = ref<string | null>(null)
    const newVersionAvailable = ref(false)
    const refreshing = ref(false)
    const refreshCountdown = ref(REFRESH_INTERVAL_SECONDS)
    const streamConnected = ref(false)
    const isModeratorOnlyViewer = computed(() => props.canvasAudienceMode === 'MODERATOR_ONLY'
      && props.canvasModerator !== true)
    const canPersist = computed(() => props.canvasAudienceMode !== 'INDEPENDENT'
      && props.canvasAudienceMode !== 'MODERATOR_ONLY' || props.canvasModerator === true)
    let savedXml = ''
    let savedPublishedSvg: string | null = null
    let pendingXml = ''
    let exportTimeout: ReturnType<typeof setTimeout> | null = null
    let eventSource: AuthenticatedEventStream | null = null
    let refreshTimer: ReturnType<typeof setInterval> | null = null
    let countdownTimer: ReturnType<typeof setInterval> | null = null

    function applyDiagram(diagram: {
      xml?: string, publishedSvg?: string | null, updatedAt?: string | null, version?: number
    }) {
      savedXml = diagram.xml || ''
      publishedSvg.value = diagram.publishedSvg || null
      savedPublishedSvg = publishedSvg.value
      publishedVersion.value = diagram.version || 0
      publishedUpdatedAt.value = diagram.updatedAt || null
      newVersionAvailable.value = false
      resetRefreshCountdown()
    }

    async function loadPublishedDiagram() {
      if (!props.conferenceId || !auth.state.token) return
      const diagram = await getEventDiagram(props.conferenceId, auth.state.token as string)
      if ((diagram.version || 0) >= publishedVersion.value) applyDiagram(diagram)
    }

    async function refreshPublishedDiagram() {
      if (refreshing.value) return
      refreshing.value = true
      try {
        await loadPublishedDiagram()
      } catch (e) {
        console.error('DiagrammingPage: fallo actualizando la publicación', e)
      } finally {
        refreshing.value = false
      }
    }

    function resetRefreshCountdown() {
      refreshCountdown.value = REFRESH_INTERVAL_SECONDS
    }

    function startPublishedUpdates() {
      if (!props.conferenceId || !auth.state.token) return
      eventSource = streamEventDiagram(props.conferenceId, auth.state.token as string)
      eventSource.addEventListener('open', () => { streamConnected.value = true })
      eventSource.addEventListener('snapshot', onPublishedEvent)
      eventSource.addEventListener('update', onPublishedEvent)
      eventSource.onerror = () => { streamConnected.value = false }
      refreshTimer = setInterval(() => { void refreshPublishedDiagram() }, REFRESH_INTERVAL_SECONDS * 1000)
      countdownTimer = setInterval(() => {
        refreshCountdown.value = refreshCountdown.value <= 1
          ? REFRESH_INTERVAL_SECONDS : refreshCountdown.value - 1
      }, 1000)
    }

    function onPublishedEvent(event: Event) {
      try {
        const data = JSON.parse((event as MessageEvent).data || '{}') as { version?: number }
        if ((data.version || 0) > publishedVersion.value) {
          newVersionAvailable.value = true
          void refreshPublishedDiagram()
        }
      } catch (e) {
        console.warn('DiagrammingPage: evento de publicación inválido', e)
      }
    }

    onMounted(async () => {
      try {
        if (props.conferenceId && auth.state.token) {
          const diagram = await getEventDiagram(props.conferenceId, auth.state.token as string)
          applyDiagram(diagram)
        }
        if (!isModeratorOnlyViewer.value) {
          const config = await getIntegrationConfig()
          drawioBaseUrl.value = config.drawioBaseUrl || ''
        } else {
          startPublishedUpdates()
        }
      } catch (e: any) {
        if (!isModeratorOnlyViewer.value) drawioBaseUrl.value = ''
        else console.error('DiagrammingPage: no se pudo cargar la publicación', e)
      } finally {
        loading.value = false
      }
    })

    const drawioUrl = computed(() => {
      if (!drawioBaseUrl.value || isModeratorOnlyViewer.value) return ''
      const token = auth.state.token ? `&ib_token=${encodeURIComponent(auth.state.token)}` : ''
      return `${drawioBaseUrl.value}/?embed=1&proto=json&spin=1&noSaveBtn=0&saveAndExit=0${token}`
    })

    function stripSessionToken(event: Event) {
      const frame = event.currentTarget as HTMLIFrameElement | null
      if (!frame?.src) return
      const url = new URL(frame.src)
      if (!url.searchParams.has('ib_token')) return
      url.searchParams.delete('ib_token')
      frame.src = url.toString()
    }

    function postToFrame(message: Record<string, unknown>) {
      frameRef.value?.contentWindow?.postMessage(JSON.stringify(message), '*')
    }

    function normalizeSvg(value: string): string {
      const trimmed = value.trim()
      if (trimmed.startsWith('data:image/')) return trimmed
      if (trimmed.startsWith('<svg')) return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(trimmed)}`
      return trimmed
    }

    async function persist(xml: string, svg: string | null) {
      if (!canPersist.value || !props.conferenceId) return
      const nextSvg = svg || publishedSvg.value
      if (xml === savedXml && nextSvg === savedPublishedSvg) return
      const previousXml = savedXml
      const previousSvg = savedPublishedSvg
      savedXml = xml
      savedPublishedSvg = nextSvg
      try {
        await saveEventDiagram(props.conferenceId, xml, auth.state.token as string, nextSvg)
        publishedSvg.value = nextSvg
        publishedVersion.value += 1
        saveError.value = ''
        saveStatus.value = 'saved'
        setTimeout(() => { if (saveStatus.value === 'saved') saveStatus.value = '' }, 3000)
      } catch (e: any) {
        savedXml = previousXml
        savedPublishedSvg = previousSvg
        saveStatus.value = 'error'
        saveError.value = e?.response?.status
          ? `${e.response.status} ${e.response.data?.error?.message || e.message}`
          : (e?.message || 'error de red')
        console.error('DiagrammingPage: fallo publicando el diagrama', e)
      }
    }

    function requestSvgExport(xml: string) {
      pendingXml = xml
      if (exportTimeout) clearTimeout(exportTimeout)
      // Drawio 24.7.17 implements `snapshot` as the reliable embed-mode SVG
      // export. The generic `export` action opens the file-export path in this
      // build and can fail with "Not a diagram file" / `substring` errors.
      postToFrame({ action: 'snapshot' })
      // Si una versión antigua de Drawio no responde al export, al menos conserva el XML y
      // la última imagen publicada en lugar de perder el guardado completo.
      exportTimeout = setTimeout(() => {
        if (pendingXml === xml) {
          pendingXml = ''
          void persist(xml, publishedSvg.value)
        }
      }, 5000)
    }

    function onMessage(evt: MessageEvent) {
      if (!frameRef.value || evt.source !== frameRef.value.contentWindow) return
      let msg: any
      try {
        msg = typeof evt.data === 'string' ? JSON.parse(evt.data) : evt.data
      } catch (e) {
        return
      }
      if (!msg || !msg.event) return
      if (msg.event === 'init' || msg.event === 'ready') {
        postToFrame({ action: 'load', xml: savedXml, autosave: 1, exportProtocol: true })
      } else if (msg.event === 'autosave' || msg.event === 'save') {
        if (typeof msg.xml === 'string' && canPersist.value) requestSvgExport(msg.xml)
      } else if (msg.event === 'export' && typeof msg.data === 'string' && pendingXml) {
        if (exportTimeout) clearTimeout(exportTimeout)
        const xml = pendingXml
        pendingXml = ''
        void persist(xml, normalizeSvg(msg.data))
      }
    }

    onMounted(() => window.addEventListener('message', onMessage))
    onBeforeUnmount(() => {
      window.removeEventListener('message', onMessage)
      eventSource?.close()
      if (refreshTimer) clearInterval(refreshTimer)
      if (countdownTimer) clearInterval(countdownTimer)
      if (exportTimeout) clearTimeout(exportTimeout)
    })

    return {
      loading, drawioUrl, frameRef, saveError, saveStatus, canPersist,
      isModeratorOnlyViewer, publishedSvg, publishedUpdatedAt, newVersionAvailable,
      refreshing, refreshCountdown, streamConnected, refreshPublishedDiagram, stripSessionToken
    }
  }
}
</script>

<style scoped>
.diagramming-page { flex: 1; min-height: 480px; display: flex; flex-direction: column; position: relative; }
.drawio-frame { flex: 1; border: none; width: 100%; }
.save-banner { flex: 0 0 auto; padding: 8px 16px; font-size: 0.85rem; text-align: center; }
.save-banner-info { background: var(--color-info-soft); color: var(--color-info); }
.save-banner.tone-success { background: var(--color-success-soft); }
.save-banner.tone-error { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.published-banner { display: flex; gap: 12px; align-items: center; justify-content: center; padding: 10px 16px; background: var(--color-primary-soft); color: var(--color-primary-dark); font-size: 0.88rem; }
.update-state { color: var(--color-text-muted); font-size: 0.8rem; }
.published-content { flex: 1; min-height: 420px; display: flex; align-items: center; justify-content: center; padding: 24px; background: var(--color-surface-muted); overflow: auto; }
.published-diagram { display: block; max-width: 100%; max-height: 72vh; object-fit: contain; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 10px; box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08); }
.published-empty { text-align: center; color: var(--color-text-secondary); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: 12px; padding: 32px; }
.published-empty .hint { color: var(--color-text-muted); font-size: 0.85rem; margin-top: 6px; }
.refresh-floating { position: absolute; right: 22px; bottom: 22px; width: 46px; height: 46px; padding: 0; border-radius: 50%; font-size: 1.6rem; line-height: 1; box-shadow: 0 6px 18px rgba(30, 27, 75, 0.28); z-index: 2; }
.refresh-floating:hover { background: var(--color-primary-dark); }
.refresh-floating:disabled { opacity: 0.65; cursor: wait; }
.refresh-floating.has-update { background: var(--color-success); box-shadow: 0 0 0 5px rgba(16, 185, 129, 0.18), 0 6px 18px rgba(6, 95, 70, 0.3); }
</style>
