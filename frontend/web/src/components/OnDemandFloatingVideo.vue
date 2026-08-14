<template lang="pug">
Teleport(:to="teleportTarget" defer)
  .ondemand-floating-video(
    ref="floatingVideoRef"
    :class="{ floating: !isOnDemandTabActive, docked: isOnDemandTabActive }"
    :style="floatingVideoStyle"
  )
    .video-wrap
      .floating-toolbar(v-if="!isOnDemandTabActive" @pointerdown="startDrag" @dblclick="goToFullTab")
        span.drag-handle(title="Arrastra para mover el video") Video bajo demanda
        button.floating-expand(type="button" @pointerdown.stop @click="goToFullTab" title="Abrir video en la pestaña del evento" aria-label="Abrir video en la pestaña del evento")
          UiIcon(name="video" size="16")
        button.floating-popup(type="button" @pointerdown.stop @click="openPopup" title="Abrir video en una ventana" aria-label="Abrir video en una ventana") ↗
        button.floating-close(type="button" @pointerdown.stop @click="close" title="Cerrar video" aria-label="Cerrar video") ✕
      OnDemandVideoPlayer(:conference-id="conferenceId" :provider="provider" :video-url="videoUrl")
      button.resize-handle(
        v-if="!isOnDemandTabActive"
        type="button"
        aria-label="Redimensionar video"
        title="Redimensionar video"
        @pointerdown="startResize"
      )
</template>

<script lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { OnDemandCuePoint } from '@/services/api/types'
import OnDemandVideoPlayer from '@/components/OnDemandVideoPlayer.vue'
import UiIcon from '@/components/ui/UiIcon.vue'

interface FloatingVideoGeometry { right: number, bottom: number, width: number, height: number }

const DEFAULT_GEOMETRY: FloatingVideoGeometry = { right: 20, bottom: 20, width: 320, height: 214 }
const MIN_WIDTH = 220
const MIN_HEIGHT = 180
const TOOLBAR_HEIGHT = 34

function getStorageKey(conferenceId: string): string { return `ondemand-floating-${conferenceId}` }
function clamp(value: number, min: number, max: number): number { return Math.min(Math.max(value, min), Math.max(min, max)) }

function readGeometry(conferenceId: string): FloatingVideoGeometry {
  try {
    const parsed = JSON.parse(localStorage.getItem(getStorageKey(conferenceId)) || '')
    if (parsed && typeof parsed === 'object') {
      return {
        right: Number.isFinite(parsed.right) ? parsed.right : DEFAULT_GEOMETRY.right,
        bottom: Number.isFinite(parsed.bottom) ? parsed.bottom : DEFAULT_GEOMETRY.bottom,
        width: Number.isFinite(parsed.width) ? parsed.width : DEFAULT_GEOMETRY.width,
        height: Number.isFinite(parsed.height) ? parsed.height : DEFAULT_GEOMETRY.height
      }
    }
  } catch { /* corrupted or unavailable local storage */ }
  return { ...DEFAULT_GEOMETRY }
}

function saveGeometry(conferenceId: string, geometry: FloatingVideoGeometry) {
  try { localStorage.setItem(getStorageKey(conferenceId), JSON.stringify(geometry)) } catch { /* ignored */ }
}

export default {
  name: 'OnDemandFloatingVideo',
  components: { OnDemandVideoPlayer, UiIcon },
  props: {
    conferenceId: { type: String, required: true },
    friendlyId: { type: String, required: true },
    provider: { type: String as () => 'YOUTUBE' | 'PEERTUBE', required: true },
    videoUrl: { type: String, required: true },
    cuePoints: { type: Array as () => OnDemandCuePoint[], default: () => [] }
  },
  emits: ['closed'],
  setup(props: { conferenceId: string, friendlyId: string, provider: 'YOUTUBE' | 'PEERTUBE', videoUrl: string, cuePoints: OnDemandCuePoint[] }, { emit }: { emit: (event: 'closed') => void }) {
    const route = useRoute()
    const router = useRouter()
    const isOnDemandTabActive = computed(() => route.path.endsWith('/on-demand'))
    const teleportTarget = computed(() => isOnDemandTabActive.value ? '#ondemand-full-slot' : '#ondemand-floating-slot')
    const floatingVideoRef = ref<HTMLDivElement | null>(null)
    const floatingRight = ref(DEFAULT_GEOMETRY.right)
    const floatingBottom = ref(DEFAULT_GEOMETRY.bottom)
    const floatingWidth = ref(DEFAULT_GEOMETRY.width)
    const floatingHeight = ref(DEFAULT_GEOMETRY.height)

    const floatingVideoStyle = computed(() => isOnDemandTabActive.value ? {} : {
      right: `${floatingRight.value}px`, bottom: `${floatingBottom.value}px`,
      width: `${floatingWidth.value}px`, height: `${floatingHeight.value}px`
    })

    let pointerMode: 'drag' | 'resize' | null = null
    let pointerId: number | null = null
    let startX = 0, startY = 0, startRight = 0, startBottom = 0, startWidth = 0

    function clampGeometry() {
      const maxWidth = Math.max(MIN_WIDTH, window.innerWidth - 24)
      const maxHeight = Math.max(MIN_HEIGHT, window.innerHeight - 24)
      floatingWidth.value = clamp(floatingWidth.value, MIN_WIDTH, maxWidth)
      floatingHeight.value = clamp(floatingHeight.value, MIN_HEIGHT, maxHeight)
      floatingRight.value = clamp(floatingRight.value, 8, Math.max(8, window.innerWidth - floatingWidth.value - 8))
      floatingBottom.value = clamp(floatingBottom.value, 8, Math.max(8, window.innerHeight - floatingHeight.value - 8))
    }

    function startDrag(event: PointerEvent) {
      if (isOnDemandTabActive.value || (event.target as HTMLElement).closest('button')) return
      pointerMode = 'drag'; pointerId = event.pointerId
      startX = event.clientX; startY = event.clientY
      startRight = floatingRight.value; startBottom = floatingBottom.value
      document.addEventListener('pointermove', onPointerMove)
      document.addEventListener('pointerup', stopPointerInteraction, { once: true })
      event.preventDefault()
    }

    function startResize(event: PointerEvent) {
      if (isOnDemandTabActive.value) return
      pointerMode = 'resize'; pointerId = event.pointerId
      startX = event.clientX; startY = event.clientY; startWidth = floatingWidth.value
      document.addEventListener('pointermove', onPointerMove)
      document.addEventListener('pointerup', stopPointerInteraction, { once: true })
      event.preventDefault()
    }

    function onPointerMove(event: PointerEvent) {
      if (!pointerMode || (pointerId !== null && event.pointerId !== pointerId)) return
      const dx = event.clientX - startX
      const dy = event.clientY - startY
      if (pointerMode === 'drag') {
        floatingRight.value = startRight - dx
        floatingBottom.value = startBottom - dy
      } else {
        const width = clamp(startWidth + dx, MIN_WIDTH, window.innerWidth - 24)
        floatingWidth.value = width
        floatingHeight.value = clamp(Math.round(width * 9 / 16 + TOOLBAR_HEIGHT), MIN_HEIGHT, window.innerHeight - 24)
      }
      clampGeometry()
    }

    function stopPointerInteraction() {
      pointerMode = null; pointerId = null
      document.removeEventListener('pointermove', onPointerMove)
      saveGeometry(props.conferenceId, { right: floatingRight.value, bottom: floatingBottom.value, width: floatingWidth.value, height: floatingHeight.value })
    }

    function goToFullTab() { void router.push(`/c/${props.friendlyId}/on-demand`) }

    function openPopup() {
      const href = router.resolve(`/on-demand-session/${encodeURIComponent(props.friendlyId)}`).href
      window.open(href, 'insightbloom-on-demand', 'popup=yes,width=720,height=520,resizable=yes,scrollbars=yes')
    }

    function close() {
      saveGeometry(props.conferenceId, { right: floatingRight.value, bottom: floatingBottom.value, width: floatingWidth.value, height: floatingHeight.value })
      emit('closed')
    }

    function handleViewportResize() {
      clampGeometry()
      saveGeometry(props.conferenceId, { right: floatingRight.value, bottom: floatingBottom.value, width: floatingWidth.value, height: floatingHeight.value })
    }

    onMounted(() => {
      const saved = readGeometry(props.conferenceId)
      floatingRight.value = saved.right; floatingBottom.value = saved.bottom
      floatingWidth.value = saved.width; floatingHeight.value = saved.height
      clampGeometry()
      window.addEventListener('resize', handleViewportResize, { passive: true })
    })

    onBeforeUnmount(() => {
      stopPointerInteraction()
      window.removeEventListener('resize', handleViewportResize)
    })

    return { isOnDemandTabActive, teleportTarget, floatingVideoRef, floatingVideoStyle, startDrag, startResize, goToFullTab, openPopup, close }
  }
}
</script>

<style scoped>
.ondemand-floating-video.docked { width: 100%; }
.ondemand-floating-video.docked .video-wrap { position: relative; width: 100%; height: min(70vh, 720px); min-height: 300px; }
.ondemand-floating-video.floating { position: fixed; z-index: 60; min-width: 220px; min-height: 180px; border-radius: 10px; box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28); }
.video-wrap { position: relative; display: flex; flex-direction: column; width: 100%; height: 100%; overflow: visible; border-radius: 10px; }
.ondemand-floating-video.docked .video-wrap { border: 1px solid var(--color-border-subtle); overflow: hidden; }
.floating-toolbar { position: relative; z-index: 2; display: flex; align-items: center; gap: 4px; height: 34px; padding: 0 6px 0 10px; color: #fff; background: rgba(15, 23, 42, 0.96); cursor: grab; touch-action: none; user-select: none; }
.floating-toolbar:active { cursor: grabbing; }
.drag-handle { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.76rem; }
.floating-expand, .floating-popup, .floating-close, .resize-handle { border: 0; color: #fff; background: transparent; cursor: pointer; }
.floating-expand, .floating-popup, .floating-close { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 5px; }
.floating-expand:hover, .floating-popup:hover, .floating-close:hover { background: rgba(255, 255, 255, 0.16); }
.resize-handle { position: absolute; right: 1px; bottom: 1px; z-index: 3; width: 18px; height: 18px; cursor: nwse-resize; touch-action: none; }
.resize-handle::after { content: ''; position: absolute; right: 2px; bottom: 2px; width: 9px; height: 9px; border-right: 2px solid rgba(255, 255, 255, 0.8); border-bottom: 2px solid rgba(255, 255, 255, 0.8); }
@media (max-width: 640px) { .ondemand-floating-video.floating { min-width: 200px; } .ondemand-floating-video.docked .video-wrap { min-height: 220px; } }
</style>
