<template lang="pug">
Teleport(:to="teleportTarget" defer)
  .ondemand-floating-video(:class="{ floating: !isOnDemandTabActive, docked: isOnDemandTabActive }")
    .video-wrap
      iframe.video-frame(
        v-if="provider === 'YOUTUBE'"
        ref="videoFrame"
        :src="youtubeSrc"
        title="Video"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowfullscreen
      )
      iframe.video-frame(v-else :src="embedUrl" title="Video" allowfullscreen)
      template(v-if="!isOnDemandTabActive")
        button.floating-expand(type="button" @click="goToFullTab" title="Volver al video" aria-label="Volver al video")
          UiIcon(name="video" size="16")
        button.floating-close(type="button" @click="close" title="Cerrar video" aria-label="Cerrar video") ✕
    .notification-panel(v-if="!isOnDemandTabActive && notifications.length")
      transition-group.notification-list(name="notif" tag="ul")
        li.notification-item(v-for="n in pagedNotifications" :key="n.id")
          .notification-meta
            span.notification-time {{ formatTime(n.cue.atSeconds) }}
            span.notification-tool {{ toolLabel(n.cue.toolPath) }}
          span.notification-label {{ n.cue.label }}
          a.notification-link(:href="`/c/${friendlyId}/${n.cue.toolPath}`" target="_blank" rel="noopener") Ir ahora →
          button.notification-dismiss(type="button" @click="dismissNotification(n.id)" aria-label="Descartar sugerencia") ✕
      .notification-pagination(v-if="totalPages > 1")
        button(type="button" :disabled="page === 0" @click="page--") ← Más nuevas
        span {{ page + 1 }} / {{ totalPages }}
        button(type="button" :disabled="page >= totalPages - 1" @click="page++") Más viejas →
</template>

<script lang="ts">
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { OnDemandCuePoint } from '@/services/api/types'
import { toEmbedUrl, toolLabelForPath, findCuePointToTrigger } from '@/features/conferences/onDemandVideo'
import { getSavedProgress, saveProgress } from '@/features/conferences/useOnDemandProgress'
import UiIcon from '@/components/ui/UiIcon.vue'

declare global {
  interface Window { onYouTubeIframeAPIReady?: () => void, YT?: any }
}

let youtubeApiPromise: Promise<void> | null = null
function loadYoutubeApi(): Promise<void> {
  if (window.YT?.Player) return Promise.resolve()
  if (youtubeApiPromise) return youtubeApiPromise
  youtubeApiPromise = new Promise((resolve) => {
    const previous = window.onYouTubeIframeAPIReady
    window.onYouTubeIframeAPIReady = () => { previous?.(); resolve() }
    const script = document.createElement('script')
    script.src = 'https://www.youtube.com/iframe_api'
    document.head.appendChild(script)
  })
  return youtubeApiPromise
}

// Dueño unico del reproductor: se monta siempre dentro de ConferencePage.vue (fuera del
// router-view, ver ConferencePage.vue), asi que nunca se desmonta al navegar entre pestanas de la
// conferencia -- el <Teleport> de arriba es lo unico que mueve el iframe entre la caja flotante
// (su posicion por defecto) y el slot de la pestana completa (#ondemand-full-slot, provisto por
// OnDemandVideoPage.vue solo mientras esa pestana esta montada). Mover un nodo DOM via Teleport no
// reinicia su src ni su estado de reproduccion -- a diferencia de desmontar/remontar el
// componente, que es lo que pasaba antes de esto.
export default {
  name: 'OnDemandFloatingVideo',
  components: { UiIcon },
  props: {
    conferenceId: { type: String, required: true },
    friendlyId: { type: String, required: true },
    provider: { type: String as () => 'YOUTUBE' | 'PEERTUBE' | null, default: null },
    videoUrl: { type: String, default: null },
    cuePoints: { type: Array as () => OnDemandCuePoint[], default: () => [] }
  },
  emits: ['closed'],
  setup(props: {
    conferenceId: string
    friendlyId: string
    provider: 'YOUTUBE' | 'PEERTUBE' | null
    videoUrl: string | null
    cuePoints: OnDemandCuePoint[]
  }, { emit }: { emit: (event: 'closed') => void }) {
    const route = useRoute()
    const router = useRouter()

    const isOnDemandTabActive = computed(() => route.path.endsWith('/on-demand'))
    const teleportTarget = computed(() => isOnDemandTabActive.value ? '#ondemand-full-slot' : '#ondemand-floating-slot')

    const embedUrl = computed(() => toEmbedUrl(props.provider, props.videoUrl))
    const youtubeSrc = computed(() => embedUrl.value ? `${embedUrl.value}?enablejsapi=1` : '')
    const videoFrame = ref<HTMLIFrameElement | null>(null)

    const sortedCuePoints = computed(() => [...props.cuePoints].sort((a, b) => a.atSeconds - b.atSeconds))

    // Panel de notificaciones: se acumulan (no se auto-ocultan por tiempo, solo el usuario las
    // saca con X) mientras el video esta flotando -- si estuviera en la pestana completa ya se ve
    // la lista completa de sugerencias ahi (ver OnDemandVideoPage.vue), no hace falta duplicar.
    // Orden: la mas nueva entra arriba (unshift), empujando las anteriores hacia abajo. `firedSeconds`
    // es aparte de `notifications` a proposito -- descartar una notificacion no debe hacer que la
    // misma sugerencia vuelva a aparecer si el usuario rebobina el video y pasa por ahi de nuevo.
    let notifSeq = 0
    const notifications = ref<Array<{ id: number, cue: OnDemandCuePoint }>>([])
    const firedSeconds = ref<Set<number>>(new Set())
    const PAGE_SIZE = 5
    const page = ref(0)

    let player: any = null
    let pollTimer: ReturnType<typeof setInterval> | null = null
    let pollTicks = 0
    const totalPages = computed(() => Math.max(1, Math.ceil(notifications.value.length / PAGE_SIZE)))
    const pagedNotifications = computed(() =>
      notifications.value.slice(page.value * PAGE_SIZE, page.value * PAGE_SIZE + PAGE_SIZE))

    function toolLabel(toolPath: string): string {
      return toolLabelForPath(toolPath)
    }

    function formatTime(totalSeconds: number): string {
      const minutes = Math.floor(totalSeconds / 60)
      const seconds = totalSeconds % 60
      return `${minutes}:${String(seconds).padStart(2, '0')}`
    }

    function dismissNotification(id: number) {
      notifications.value = notifications.value.filter(n => n.id !== id)
      if (page.value >= totalPages.value) page.value = Math.max(0, totalPages.value - 1)
    }

    function checkCuePoints(currentTime: number) {
      const match = findCuePointToTrigger(sortedCuePoints.value, currentTime, firedSeconds.value)
      if (match) {
        firedSeconds.value.add(match.atSeconds)
        notifications.value = [{ id: ++notifSeq, cue: match }, ...notifications.value]
        page.value = 0
      }
    }

    function startPolling() {
      if (pollTimer) return
      pollTimer = setInterval(() => {
        if (!player?.getCurrentTime) return
        const currentTime = player.getCurrentTime()
        checkCuePoints(Math.floor(currentTime))
        // Guardar cada ~5s (no en cada tick de 1s) -- suficiente resolucion para "retomar donde
        // quedo" sin escribir a localStorage constantemente.
        pollTicks += 1
        if (pollTicks % 5 === 0) saveProgress(props.conferenceId, currentTime)
      }, 1000)
    }

    function stopPolling() {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    }

    function goToFullTab() {
      router.push(`/c/${props.friendlyId}/on-demand`)
    }

    function persistCurrentTime() {
      if (player?.getCurrentTime) saveProgress(props.conferenceId, player.getCurrentTime())
    }

    function close() {
      stopPolling()
      persistCurrentTime()
      if (player?.destroy) player.destroy()
      player = null
      emit('closed')
    }

    async function ensurePlayerCreated() {
      if (player || props.provider !== 'YOUTUBE' || !embedUrl.value) return
      await loadYoutubeApi()
      if (!videoFrame.value || !window.YT?.Player) return
      const savedProgress = getSavedProgress(props.conferenceId)
      player = new window.YT.Player(videoFrame.value, {
        events: {
          onReady: () => {
            if (savedProgress && savedProgress > 0) player.seekTo(savedProgress, true)
          },
          onStateChange: (event: { data: number }) => {
            // 1 = playing (YT.PlayerState.PLAYING)
            if (event.data === 1) startPolling()
            else { stopPolling(); persistCurrentTime() }
          }
        }
      })
    }

    // El iframe de YouTube solo existe en el DOM despues de que este componente decide montarlo
    // (ver template: v-if="provider === 'YOUTUBE'" siempre true si hay provider, ya que el
    // widget solo se monta con onDemandVideoUrl presente) -- se crea el player la primera vez
    // que hay embedUrl disponible.
    watch(embedUrl, (url) => { if (url) void ensurePlayerCreated() }, { immediate: true })

    function handleVisibilityChange() {
      if (document.visibilityState === 'hidden') persistCurrentTime()
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('beforeunload', persistCurrentTime)

    onBeforeUnmount(() => {
      stopPolling()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('beforeunload', persistCurrentTime)
    })

    return {
      isOnDemandTabActive, teleportTarget,
      embedUrl, youtubeSrc, videoFrame,
      notifications, pagedNotifications, totalPages, page, toolLabel, formatTime, dismissNotification,
      goToFullTab, close
    }
  }
}
</script>

<style scoped>
.ondemand-floating-video.docked {
  width: 100%;
}
.ondemand-floating-video.docked .video-wrap {
  position: relative;
}
.ondemand-floating-video.docked .video-frame {
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--color-border-subtle);
  border-radius: 12px;
  background: var(--color-surface);
}

.ondemand-floating-video.floating {
  position: fixed;
  right: 20px;
  bottom: 20px;
  width: 240px;
  z-index: 60;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28);
  border-radius: 10px;
  overflow: visible;
}
.ondemand-floating-video.floating .video-wrap {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
}
.ondemand-floating-video.floating .video-frame {
  display: block;
  width: 240px;
  aspect-ratio: 16 / 9;
  border: none;
  background: #000;
}
.floating-expand, .floating-close {
  position: absolute;
  top: 6px;
  border: none;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 0.8rem;
}
.floating-expand { left: 6px; }
.floating-close { right: 6px; }
.floating-expand:hover, .floating-close:hover { background: rgba(0, 0, 0, 0.75); }

.notification-panel {
  margin-top: 8px;
  width: 240px;
}
.notification-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.notification-item {
  display: flex; flex-direction: column; gap: 2px;
  padding: 8px 10px; border-radius: 8px;
  background: var(--color-primary-soft); color: var(--color-primary); font-size: 0.8rem;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.18);
  position: relative;
}
.notification-meta { display: flex; align-items: center; gap: 8px; font-size: 0.72rem; font-weight: 700; }
.notification-time { font-family: monospace; }
.notification-tool { text-transform: uppercase; letter-spacing: 0.02em; opacity: 0.85; }
.notification-label { padding-right: 18px; }
.notification-link { font-weight: 600; text-decoration: none; color: var(--color-primary); align-self: flex-start; }
.notification-link:hover { text-decoration: underline; }
.notification-dismiss {
  position: absolute; top: 6px; right: 6px;
  border: none; background: none; cursor: pointer;
  color: var(--color-primary); font-size: 0.78rem; padding: 2px 4px; line-height: 1;
}
.notification-pagination {
  display: flex; align-items: center; justify-content: space-between; gap: 6px;
  margin-top: 6px; font-size: 0.72rem; color: var(--color-text-muted);
}
.notification-pagination button {
  border: none; background: var(--color-surface); border-radius: 6px; padding: 3px 6px;
  cursor: pointer; font-size: 0.72rem; color: var(--color-primary);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}
.notification-pagination button:disabled { opacity: 0.4; cursor: default; }

.notif-enter-active, .notif-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.notif-enter-from { opacity: 0; transform: translateY(-6px); }
.notif-leave-to { opacity: 0; transform: translateX(12px); }

@media (max-width: 640px) {
  .ondemand-floating-video.floating,
  .ondemand-floating-video.floating .video-frame,
  .notification-panel {
    width: 168px;
  }
  .ondemand-floating-video.floating { right: 10px; bottom: 10px; }
}
</style>
