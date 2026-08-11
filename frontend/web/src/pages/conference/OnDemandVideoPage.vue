<template lang="pug">
.on-demand-page
  .on-demand-header
    h2 Video
  EmptyState(v-if="!accessGranted" message="Regístrate y canjea tu boleto para ver el video de este evento.")
  EmptyState(v-else-if="!embedUrl" message="El organizador todavía no configuró un video para este evento.")
  template(v-else)
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
    transition(name="fade")
      .cue-banner(v-if="activeSuggestion")
        span 💡 {{ activeSuggestion.label }}
        router-link.cue-link(:to="`/c/${friendlyId}/${activeSuggestion.toolPath}`") Ir ahora →
        button.cue-dismiss(type="button" @click="dismissSuggestion" aria-label="Descartar sugerencia") ✕
    .cue-list(v-if="provider === 'PEERTUBE' && cuePoints.length")
      p.hint Sugerencias de este video:
      router-link.cue-list-item(v-for="cue in cuePoints" :key="cue.atSeconds" :to="`/c/${friendlyId}/${cue.toolPath}`")
        span {{ formatTime(cue.atSeconds) }} — {{ cue.label }}
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import type { OnDemandCuePoint } from '@/services/api/types'
import { toEmbedUrl } from '@/features/conferences/onDemandVideo'
import EmptyState from '@/components/ui/EmptyState.vue'

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

export default {
  name: 'OnDemandVideoPage',
  components: { EmptyState },
  props: {
    conferenceId: String,
    onDemandVideoProvider: { type: String, default: null },
    onDemandVideoUrl: { type: String, default: null },
    onDemandCuePoints: { type: Array as () => OnDemandCuePoint[], default: () => [] },
    accessGranted: { type: Boolean, default: false }
  },
  setup(props: {
    conferenceId?: string
    onDemandVideoProvider?: 'YOUTUBE' | 'PEERTUBE' | null
    onDemandVideoUrl?: string | null
    onDemandCuePoints?: OnDemandCuePoint[]
    accessGranted?: boolean
  }) {
    const route = useRoute()
    const friendlyId = route.params.friendlyId as string
    const provider = computed(() => props.onDemandVideoProvider || null)
    const cuePoints = computed(() => [...(props.onDemandCuePoints || [])].sort((a, b) => a.atSeconds - b.atSeconds))
    const embedUrl = computed(() => toEmbedUrl(provider.value, props.onDemandVideoUrl || null))
    const youtubeSrc = computed(() => embedUrl.value ? `${embedUrl.value}?enablejsapi=1` : '')
    const videoFrame = ref<HTMLIFrameElement | null>(null)
    const activeSuggestion = ref<OnDemandCuePoint | null>(null)
    const dismissedSeconds = ref<Set<number>>(new Set())

    let player: any = null
    let pollTimer: ReturnType<typeof setInterval> | null = null

    function dismissSuggestion() {
      if (activeSuggestion.value) dismissedSeconds.value.add(activeSuggestion.value.atSeconds)
      activeSuggestion.value = null
    }

    function checkCuePoints(currentTime: number) {
      // Ventana de 3s: el poll corre cada 1s, asi que una sugerencia puntual no se pierde entre
      // dos lecturas consecutivas si el timestamp cae justo entre medio.
      const match = cuePoints.value.find(cue =>
        currentTime >= cue.atSeconds && currentTime < cue.atSeconds + 3 && !dismissedSeconds.value.has(cue.atSeconds))
      if (match && activeSuggestion.value?.atSeconds !== match.atSeconds) {
        activeSuggestion.value = match
      } else if (!match && activeSuggestion.value) {
        activeSuggestion.value = null
      }
    }

    function startPolling() {
      if (pollTimer) return
      pollTimer = setInterval(() => {
        if (!player?.getCurrentTime) return
        checkCuePoints(Math.floor(player.getCurrentTime()))
      }, 1000)
    }

    function stopPolling() {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    }

    function formatTime(totalSeconds: number): string {
      const minutes = Math.floor(totalSeconds / 60)
      const seconds = totalSeconds % 60
      return `${minutes}:${String(seconds).padStart(2, '0')}`
    }

    onMounted(async () => {
      if (provider.value !== 'YOUTUBE' || !cuePoints.value.length) return
      await loadYoutubeApi()
      if (!videoFrame.value || !window.YT?.Player) return
      player = new window.YT.Player(videoFrame.value, {
        events: {
          onStateChange: (event: { data: number }) => {
            // 1 = playing (YT.PlayerState.PLAYING)
            if (event.data === 1) startPolling()
            else stopPolling()
          }
        }
      })
    })

    onBeforeUnmount(() => {
      stopPolling()
    })

    return {
      friendlyId, provider, cuePoints, embedUrl, youtubeSrc, videoFrame,
      activeSuggestion, dismissSuggestion, formatTime
    }
  }
}
</script>

<style scoped>
.on-demand-page { padding: 24px; }
.on-demand-header { margin-bottom: 16px; }
h2 { margin: 0; color: var(--color-heading); }
.video-wrap { position: relative; }
.video-frame {
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--color-border-subtle);
  border-radius: 12px;
  background: var(--color-surface);
}
.cue-banner {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  margin-top: 12px; padding: 10px 16px; border-radius: 8px;
  background: var(--color-primary-soft); color: var(--color-primary); font-size: 0.9rem;
}
.cue-link { font-weight: 600; text-decoration: none; color: var(--color-primary); }
.cue-link:hover { text-decoration: underline; }
.cue-dismiss {
  margin-left: auto; border: none; background: none; cursor: pointer;
  color: var(--color-primary); font-size: 0.9rem; padding: 2px 4px;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.cue-list { margin-top: 16px; }
.cue-list .hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 8px; }
.cue-list-item {
  display: block; padding: 8px 12px; border-radius: 8px; text-decoration: none;
  color: var(--color-text-secondary); font-size: 0.88rem; margin-bottom: 4px;
}
.cue-list-item:hover { background: var(--color-surface-muted); color: var(--color-primary); }

@media (max-width: 640px) {
  .on-demand-page { padding: 14px; }
}
</style>
