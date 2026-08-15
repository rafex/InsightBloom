<template lang="pug">
Teleport(:to="teleportTarget" defer)
  .ondemand-floating-video(
    v-if="!popupOpen"
    ref="floatingVideoRef"
    :class="{ floating: !isOnDemandTabActive, docked: isOnDemandTabActive }"
    :style="floatingVideoStyle"
  )
    .video-wrap
      .floating-toolbar(
        v-if="!isOnDemandTabActive"
        role="button"
        tabindex="0"
        :aria-grabbed="pointerMode === 'drag'"
        title="Arrastra para mover el video"
        @pointerdown="startDrag"
        @pointermove="onPointerMove"
        @pointerup="finishPointerInteraction"
        @pointercancel="cancelPointerInteraction"
        @lostpointercapture="finishPointerInteraction"
        @dblclick="goToFullTab"
        @keydown.enter.prevent="goToFullTab"
        @keydown.space.prevent="goToFullTab"
      )
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
        @pointermove="onPointerMove"
        @pointerup="finishPointerInteraction"
        @pointercancel="cancelPointerInteraction"
        @lostpointercapture="finishPointerInteraction"
      )
</template>

<script lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { OnDemandCuePoint } from '@/services/api/types'
import OnDemandVideoPlayer from '@/components/OnDemandVideoPlayer.vue'
import UiIcon from '@/components/ui/UiIcon.vue'
import {
  DEFAULT_FLOATING_VIDEO_GEOMETRY,
  moveFloatingVideo,
  resizeFloatingVideo,
  sanitizeFloatingVideoGeometry,
  type FloatingVideoGeometry
} from '@/features/conferences/floatingVideoGeometry'

function getStorageKey(conferenceId: string): string { return `ondemand-floating-${conferenceId}` }

function readGeometry(conferenceId: string): FloatingVideoGeometry {
  try {
    const parsed = JSON.parse(localStorage.getItem(getStorageKey(conferenceId)) || '')
    if (parsed && typeof parsed === 'object') return parsed as FloatingVideoGeometry
  } catch { /* corrupted or unavailable local storage */ }
  return { ...DEFAULT_FLOATING_VIDEO_GEOMETRY }
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
    const floatingRight = ref(DEFAULT_FLOATING_VIDEO_GEOMETRY.right)
    const floatingBottom = ref(DEFAULT_FLOATING_VIDEO_GEOMETRY.bottom)
    const floatingWidth = ref(DEFAULT_FLOATING_VIDEO_GEOMETRY.width)
    const floatingHeight = ref(DEFAULT_FLOATING_VIDEO_GEOMETRY.height)
    const pointerMode = ref<'drag' | 'resize' | null>(null)
    const popupOpen = ref(false)

    const floatingVideoStyle = computed(() => isOnDemandTabActive.value ? {} : {
      right: `${floatingRight.value}px`, bottom: `${floatingBottom.value}px`,
      width: `${floatingWidth.value}px`, height: `${floatingHeight.value}px`
    })

    let pointerId: number | null = null
    let popupWindow: Window | null = null
    let popupPollTimer: number | null = null
    let startX = 0, startY = 0
    let startGeometry: FloatingVideoGeometry = { ...DEFAULT_FLOATING_VIDEO_GEOMETRY }

    function currentGeometry(): FloatingVideoGeometry {
      return {
        right: floatingRight.value,
        bottom: floatingBottom.value,
        width: floatingWidth.value,
        height: floatingHeight.value
      }
    }

    function applyGeometry(value: FloatingVideoGeometry) {
      floatingRight.value = value.right
      floatingBottom.value = value.bottom
      floatingWidth.value = value.width
      floatingHeight.value = value.height
    }

    function clampGeometry() {
      applyGeometry(sanitizeFloatingVideoGeometry(currentGeometry(), {
        width: window.innerWidth,
        height: window.innerHeight
      }))
    }

    function startDrag(event: PointerEvent) {
      if (isOnDemandTabActive.value || (event.target as HTMLElement).closest('button')) return
      pointerMode.value = 'drag'; pointerId = event.pointerId
      startX = event.clientX; startY = event.clientY
      startGeometry = currentGeometry()
      capturePointer(event)
      event.preventDefault()
    }

    function startResize(event: PointerEvent) {
      if (isOnDemandTabActive.value) return
      pointerMode.value = 'resize'; pointerId = event.pointerId
      startX = event.clientX; startY = event.clientY
      startGeometry = currentGeometry()
      capturePointer(event)
      event.preventDefault()
    }

    function capturePointer(event: PointerEvent) {
      const target = event.currentTarget as HTMLElement | null
      try { target?.setPointerCapture?.(event.pointerId) } catch { /* pointer may already be released */ }
    }

    function onPointerMove(event: PointerEvent) {
      if (!pointerMode.value || (pointerId !== null && event.pointerId !== pointerId)) return
      const dx = event.clientX - startX
      const dy = event.clientY - startY
      const viewport = { width: window.innerWidth, height: window.innerHeight }
      applyGeometry(pointerMode.value === 'drag'
        ? moveFloatingVideo(startGeometry, dx, dy, viewport)
        : resizeFloatingVideo(startGeometry, dx, viewport))
    }

    function finishPointerInteraction() {
      if (!pointerMode.value) return
      pointerMode.value = null
      pointerId = null
      saveGeometry(props.conferenceId, currentGeometry())
    }

    function cancelPointerInteraction() {
      finishPointerInteraction()
    }

    function goToFullTab() { void router.push(`/c/${props.friendlyId}/on-demand`) }

    function openPopup() {
      const href = router.resolve(`/on-demand-session/${encodeURIComponent(props.friendlyId)}`).href
      const opened = window.open(href, 'insightbloom-on-demand', 'popup=yes,width=720,height=520,resizable=yes,scrollbars=yes')
      if (!opened) return
      popupWindow = opened
      popupOpen.value = true
      startPopupClosePolling()
    }

    function startPopupClosePolling() {
      if (popupPollTimer) window.clearInterval(popupPollTimer)
      popupPollTimer = window.setInterval(() => {
        if (popupWindow?.closed) handlePopupClosed()
      }, 400)
    }

    function handlePopupClosed() {
      if (popupPollTimer) window.clearInterval(popupPollTimer)
      popupPollTimer = null
      popupWindow = null
      popupOpen.value = false
    }

    function handlePopupMessage(event: MessageEvent) {
      if (event.origin !== window.location.origin) return
      const message = event.data as { type?: unknown, conferenceId?: unknown } | null
      if (!message || message.conferenceId !== props.conferenceId) return
      if (message.type === 'insightbloom-on-demand-popup-ready') {
        if (!popupWindow || event.source === popupWindow) {
          popupWindow = event.source as Window | null
          popupOpen.value = true
          startPopupClosePolling()
        }
      } else if (message.type === 'insightbloom-on-demand-popup-closed'
        && (!popupWindow || event.source === popupWindow)) {
        handlePopupClosed()
      }
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
      applyGeometry(sanitizeFloatingVideoGeometry(saved, { width: window.innerWidth, height: window.innerHeight }))
      saveGeometry(props.conferenceId, currentGeometry())
      window.addEventListener('resize', handleViewportResize, { passive: true })
      window.addEventListener('message', handlePopupMessage)
    })

    onBeforeUnmount(() => {
      finishPointerInteraction()
      window.removeEventListener('resize', handleViewportResize)
      window.removeEventListener('message', handlePopupMessage)
      if (popupPollTimer) window.clearInterval(popupPollTimer)
    })

    return { isOnDemandTabActive, teleportTarget, floatingVideoRef, floatingVideoStyle, pointerMode, popupOpen, startDrag, startResize, onPointerMove, finishPointerInteraction, cancelPointerInteraction, goToFullTab, openPopup, close }
  }
}
</script>

<style scoped>
.ondemand-floating-video.docked { width: 100%; }
.ondemand-floating-video.docked .video-wrap { position: relative; width: 100%; height: min(70vh, 720px); min-height: 300px; }
.ondemand-floating-video.floating { position: fixed; z-index: 60; min-width: 220px; min-height: 190px; border-radius: 10px; box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28); }
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
@media (max-width: 640px) { .ondemand-floating-video.floating { min-width: 220px; } .ondemand-floating-video.docked .video-wrap { min-height: 220px; } }
</style>
