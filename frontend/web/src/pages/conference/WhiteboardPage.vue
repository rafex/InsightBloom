<template lang="pug">
.whiteboard-page
  LoadingState(v-if="loading" message="Cargando pizarra...")
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
    BaseButton.refresh-floating(
      type="button"
      :class="{ 'has-update': newVersionAvailable }"
      :disabled="refreshing"
      :loading="refreshing"
      aria-label="Actualizar pizarra publicada"
      title="Actualizar pizarra publicada"
      @click="refreshPublishedWhiteboard"
    ) ↻
  NoticeState(v-else-if="!available" title="Pizarra no disponible" message="Intenta más tarde o contacta al organizador." tone="warning")
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
import BaseButton from '@/components/ui/BaseButton.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import NoticeState from '@/components/ui/NoticeState.vue'

const REFRESH_INTERVAL_SECONDS = 30

export default {
  name: 'WhiteboardPage',
  components: { BaseButton, LoadingState, NoticeState },
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
.published-banner, .save-banner { padding: 10px 16px; font-size: 0.86rem; }
.published-banner { display: flex; gap: 16px; flex-wrap: wrap; align-items: center; background: var(--color-primary-soft); color: var(--color-primary-dark); }
.update-state { color: var(--color-text-secondary); }
.published-content { flex: 1; min-height: 480px; display: flex; align-items: center; justify-content: center; padding: 24px; background: var(--color-surface-muted); }
.published-whiteboard { max-width: 100%; max-height: 72vh; background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; box-shadow: 0 8px 24px rgba(15, 23, 42, .08); }
.published-empty { text-align: center; color: var(--color-text-secondary); border: 1px dashed var(--color-border); border-radius: 12px; padding: 28px; background: var(--color-surface); }
.published-empty .hint { color: var(--color-text-muted); font-size: .86rem; }
.save-banner-info { background: var(--color-primary-soft); color: var(--color-primary-dark); }
.save-banner-error { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.save-banner-ok { background: var(--color-success-soft); color: var(--color-success); }
.refresh-floating { position: fixed; right: 24px; bottom: 24px; width: 44px; height: 44px; padding: 0; border-radius: 999px; font-size: 1.45rem; box-shadow: 0 8px 18px rgba(79, 70, 229, .3); }
.refresh-floating.has-update { background: var(--color-success); }
.refresh-floating:disabled { opacity: .65; cursor: wait; }
</style>
