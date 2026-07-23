<template lang="pug">
.whiteboard-page
  .loading-text(v-if="loading") Cargando pizarra...
  template(v-else-if="isModeratorOnlyViewer")
    .published-banner
      span 🔒 Vista publicada por el moderador
      span.update-state(v-if="streamConnected") ● SSE en vivo
      span.update-state Actualización automática en {{ refreshCountdown }}s
      span.update-state(v-if="publishedUpdatedAt") Publicado {{ publishedUpdatedAt }}
    .published-content
      img.published-whiteboard(v-if="publishedSvg" :src="publishedSvg" alt="Pizarra publicada por el moderador")
      .published-empty(v-else)
        p El moderador todavía no ha publicado una pizarra.
        p.hint Esta vista se actualizará automáticamente cuando exista una publicación.
    button.refresh-floating(
      :class="{ 'has-update': newVersionAvailable }"
      :disabled="refreshing"
      aria-label="Actualizar pizarra publicada"
      title="Actualizar pizarra publicada"
      @click="refreshPublishedWhiteboard"
    ) {{ refreshing ? '…' : '↻' }}
  .unavailable(v-else-if="!available")
    p ⚠️ La pizarra no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  template(v-else)
    .save-banner(v-if="!canPersist" class="save-banner-info") ℹ️ Tu edición es local y no se conservará; solo el material del moderador se persiste.
    .save-banner(v-if="saveError" class="save-banner-error") ⚠️ No se pudo publicar la pizarra: {{ saveError }}
    .save-banner(v-else-if="saveStatus === 'saved'" class="save-banner-ok") ✓ Pizarra publicada
    .editor-shell(ref="editorRef")
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import {
  getEventWhiteboard,
  saveEventWhiteboard,
  streamEventWhiteboard,
  AuthenticatedEventStream
} from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import { mountExcalidrawEditor } from '@/components/ExcalidrawEditor'

const REFRESH_INTERVAL_SECONDS = 30

export default {
  name: 'WhiteboardPage',
  props: {
    conferenceId: { type: String, default: '' },
    canvasAudienceMode: { type: String, default: '' },
    canvasModerator: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, canvasAudienceMode?: string, canvasModerator?: boolean }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const available = ref(true)
    const editorRef = ref<HTMLElement | null>(null)
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
    let savedSceneJson = ''
    let savedPublishedSvg: string | null = null
    let eventSource: AuthenticatedEventStream | null = null
    let refreshTimer: ReturnType<typeof setInterval> | null = null
    let countdownTimer: ReturnType<typeof setInterval> | null = null
    let disposeEditor: (() => void) | null = null

    function resetRefreshCountdown() {
      refreshCountdown.value = REFRESH_INTERVAL_SECONDS
    }

    function applyWhiteboard(whiteboard: {
      sceneJson?: string, publishedSvg?: string | null, updatedAt?: string | null, version?: number
    }) {
      savedSceneJson = whiteboard.sceneJson || ''
      publishedSvg.value = whiteboard.publishedSvg || null
      savedPublishedSvg = publishedSvg.value
      publishedVersion.value = whiteboard.version || 0
      publishedUpdatedAt.value = whiteboard.updatedAt || null
      newVersionAvailable.value = false
      resetRefreshCountdown()
    }

    async function loadPublishedWhiteboard() {
      if (!props.conferenceId || !auth.state.token) return
      const whiteboard = await getEventWhiteboard(props.conferenceId, auth.state.token as string)
      if ((whiteboard.version || 0) >= publishedVersion.value) applyWhiteboard(whiteboard)
    }

    async function refreshPublishedWhiteboard() {
      if (refreshing.value) return
      refreshing.value = true
      try {
        await loadPublishedWhiteboard()
      } catch (e) {
        console.error('WhiteboardPage: fallo actualizando la publicación', e)
      } finally {
        refreshing.value = false
      }
    }

    function onPublishedEvent(event: Event) {
      try {
        const data = JSON.parse((event as MessageEvent).data || '{}') as { version?: number }
        if ((data.version || 0) > publishedVersion.value) {
          newVersionAvailable.value = true
          void refreshPublishedWhiteboard()
        }
      } catch (e) {
        console.warn('WhiteboardPage: evento de publicación inválido', e)
      }
    }

    function startPublishedUpdates() {
      if (!props.conferenceId || !auth.state.token) return
      eventSource = streamEventWhiteboard(props.conferenceId, auth.state.token as string)
      eventSource.addEventListener('open', () => { streamConnected.value = true })
      eventSource.addEventListener('snapshot', onPublishedEvent)
      eventSource.addEventListener('update', onPublishedEvent)
      eventSource.onerror = () => { streamConnected.value = false }
      refreshTimer = setInterval(() => { void refreshPublishedWhiteboard() }, REFRESH_INTERVAL_SECONDS * 1000)
      countdownTimer = setInterval(() => {
        refreshCountdown.value = refreshCountdown.value <= 1
          ? REFRESH_INTERVAL_SECONDS : refreshCountdown.value - 1
      }, 1000)
    }

    async function persist(sceneJson: string, svg: string) {
      if (!canPersist.value || !props.conferenceId || !auth.state.token) return
      if (sceneJson === savedSceneJson && svg === savedPublishedSvg) return
      const previousScene = savedSceneJson
      const previousSvg = savedPublishedSvg
      savedSceneJson = sceneJson
      savedPublishedSvg = svg
      try {
        await saveEventWhiteboard(props.conferenceId, sceneJson, auth.state.token as string, svg)
        publishedSvg.value = svg
        publishedVersion.value += 1
        saveError.value = ''
        saveStatus.value = 'saved'
        setTimeout(() => { if (saveStatus.value === 'saved') saveStatus.value = '' }, 3000)
      } catch (e: any) {
        savedSceneJson = previousScene
        savedPublishedSvg = previousSvg
        saveStatus.value = 'error'
        saveError.value = e?.response?.status
          ? `${e.response.status} ${e.response.data?.error?.message || e.message}`
          : (e?.message || 'error de red')
        console.error('WhiteboardPage: fallo publicando la pizarra', e)
      }
    }

    onMounted(async () => {
      try {
        if (!props.conferenceId || !auth.state.token) throw new Error('missing_session')
        const whiteboard = await getEventWhiteboard(props.conferenceId, auth.state.token as string)
        applyWhiteboard(whiteboard)
        if (isModeratorOnlyViewer.value) {
          startPublishedUpdates()
        }
      } catch (e) {
        available.value = false
        console.error('WhiteboardPage: no se pudo cargar la pizarra', e)
      } finally {
        loading.value = false
        if (!isModeratorOnlyViewer.value && available.value) {
          await nextTick()
          if (editorRef.value) {
            disposeEditor = mountExcalidrawEditor(editorRef.value, savedSceneJson, (scene, svg) => {
              void persist(scene, svg)
            })
          }
        }
      }
    })

    onBeforeUnmount(() => {
      disposeEditor?.()
      eventSource?.close()
      if (refreshTimer) clearInterval(refreshTimer)
      if (countdownTimer) clearInterval(countdownTimer)
    })

    return {
      loading, available, editorRef, saveError, saveStatus, publishedSvg, publishedUpdatedAt,
      newVersionAvailable, refreshing, refreshCountdown, streamConnected,
      isModeratorOnlyViewer, canPersist, refreshPublishedWhiteboard
    }
  }
}
</script>

<style scoped>
.whiteboard-page { flex: 1; min-height: 480px; display: flex; flex-direction: column; position: relative; }
.editor-shell { flex: 1 1 auto; height: calc(100vh - 112px); min-height: 480px; width: 100%; overflow: hidden; }
.editor-shell :deep(.excalidraw) { width: 100%; height: 100%; min-height: 480px; }
.loading-text { padding: 40px; text-align: center; color: #6b7280; }
.unavailable { margin: 40px auto; text-align: center; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: #78350f; font-size: 0.85rem; margin-top: 6px; }
.published-banner, .save-banner { padding: 10px 16px; font-size: 0.86rem; }
.published-banner { display: flex; gap: 16px; flex-wrap: wrap; align-items: center; background: #eef2ff; color: #3730a3; }
.update-state { color: #4b5563; }
.published-content { flex: 1; min-height: 480px; display: flex; align-items: center; justify-content: center; padding: 24px; background: #f8fafc; }
.published-whiteboard { max-width: 100%; max-height: 72vh; background: white; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 8px 24px rgba(15, 23, 42, .08); }
.published-empty { text-align: center; color: #475569; border: 1px dashed #cbd5e1; border-radius: 12px; padding: 28px; background: white; }
.published-empty .hint { color: #64748b; font-size: .86rem; }
.save-banner-info { background: #eef2ff; color: #3730a3; }
.save-banner-error { background: #fee2e2; color: #991b1b; }
.save-banner-ok { background: #dcfce7; color: #166534; }
.refresh-floating { position: fixed; right: 24px; bottom: 24px; border: none; border-radius: 999px; width: 44px; height: 44px; background: #4f46e5; color: white; font-size: 1.45rem; cursor: pointer; box-shadow: 0 8px 18px rgba(79, 70, 229, .3); }
.refresh-floating.has-update { background: #059669; }
.refresh-floating:disabled { opacity: .65; cursor: wait; }
</style>
