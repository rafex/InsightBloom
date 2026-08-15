<template lang="pug">
.on-demand-player
  .video-stage
    iframe.video-frame(
      v-if="provider === 'YOUTUBE'"
      ref="videoFrame"
      :src="youtubeSrc"
      title="Video bajo demanda"
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
      allowfullscreen
    )
    iframe.video-frame(
      v-else-if="provider === 'PEERTUBE'"
      ref="videoFrame"
      :src="embedUrl || undefined"
      title="Video bajo demanda"
      allow="autoplay; fullscreen; picture-in-picture"
      allowfullscreen
    )
  .video-controls(aria-label="Controles del video")
    button.video-control(type="button" :disabled="!ready" :aria-label="isPlaying ? 'Pausar video' : 'Reproducir video'" @click="togglePlayback")
      | {{ isPlaying ? '❚❚' : '▶' }}
    input.video-progress(
      type="range"
      min="0"
      :max="duration || 0"
      step="0.1"
      :value="currentTime"
      :disabled="!ready || !duration"
      aria-label="Posición del video"
      @input="seekFromSlider"
    )
    span.video-time {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
    label.volume-control(title="Volumen")
      span.sr-only Volumen
      input(type="range" min="0" max="100" step="1" :value="volume" :disabled="!ready" @input="setVolumeFromSlider")
    select.rate-control(:value="playbackRate" :disabled="!ready" aria-label="Velocidad de reproducción" @change="setRateFromSelect")
      option(v-for="rate in playbackRates" :key="rate" :value="rate") {{ rate }}×
    button.video-control(type="button" :disabled="!ready" aria-label="Pantalla completa" @click="toggleFullscreen") ⛶
</template>

<script lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { PeerTubePlayer } from '@peertube/embed-api'
import { getSavedProgress, saveProgress } from '@/features/conferences/useOnDemandProgress'
import { toEmbedUrl } from '@/features/conferences/onDemandVideo'
import {
  useOnDemandVideoChannel,
  type OnDemandVideoMessage
} from '@/features/conferences/useOnDemandVideoChannel'

declare global {
  interface Window {
    onYouTubeIframeAPIReady?: () => void
    YT?: any
  }
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

const playbackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const PLAYING = 1

function formatTime(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return '0:00'
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = Math.floor(totalSeconds % 60)
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export default {
  name: 'OnDemandVideoPlayer',
  props: {
    conferenceId: { type: String, required: true },
    provider: { type: String as () => 'YOUTUBE' | 'PEERTUBE', required: true },
    videoUrl: { type: String, required: true }
  },
  setup(props: { conferenceId: string, provider: 'YOUTUBE' | 'PEERTUBE', videoUrl: string }) {
    const videoFrame = ref<HTMLIFrameElement | null>(null)
    const ready = ref(false)
    const isPlaying = ref(false)
    const currentTime = ref(0)
    const duration = ref(0)
    const volume = ref(100)
    const playbackRate = ref(1)
    const userStarted = ref(false)

    let player: any = null
    let pollTimer: ReturnType<typeof setInterval> | null = null
    let suppressProviderEvent = false
    let remoteActionUntil = 0
    const lastSequenceBySource = new Map<string, number>()

    const embedUrl = computed(() => toEmbedUrl(props.provider, props.videoUrl))
    const youtubeSrc = computed(() => {
      if (!embedUrl.value) return ''
      const url = new URL(embedUrl.value)
      url.searchParams.set('enablejsapi', '1')
      url.searchParams.set('autoplay', '0')
      url.searchParams.set('controls', '1')
      url.searchParams.set('playsinline', '1')
      return url.toString()
    })

    function readCurrentTime() {
      if (!player?.getCurrentTime) return
      const value = Number(player.getCurrentTime())
      if (Number.isFinite(value)) currentTime.value = Math.max(0, value)
    }

    function saveCurrentTime() {
      readCurrentTime()
      saveProgress(props.conferenceId, currentTime.value)
    }

    function startPolling() {
      if (pollTimer) return
      pollTimer = setInterval(() => {
        readCurrentTime()
        if (isPlaying.value) sendState()
        if (Math.floor(currentTime.value) % 5 === 0) saveProgress(props.conferenceId, currentTime.value)
      }, 1000)
    }

    function stopPolling() {
      if (!pollTimer) return
      clearInterval(pollTimer)
      pollTimer = null
      saveCurrentTime()
    }

    function setPlayingState(playing: boolean, broadcast = true) {
      isPlaying.value = playing
      if (playing) startPolling()
      else stopPolling()
      if (broadcast) send(playing ? 'play' : 'pause', { currentTime: currentTime.value })
    }

    function send(type: Parameters<typeof channel.send>[0], payload: Record<string, unknown> = {}) {
      channel.send(type, payload)
    }

    function sendState() {
      send('state', {
        currentTime: currentTime.value,
        playing: isPlaying.value,
        volume: volume.value,
        playbackRate: playbackRate.value
      })
    }

    function performRemote(action: () => void) {
      suppressProviderEvent = true
      remoteActionUntil = Date.now() + 1000
      action()
      window.setTimeout(() => { suppressProviderEvent = false }, 250)
    }

    function providerPlay() {
      if (player?.playVideo) player.playVideo()
      else if (player?.play) void player.play()
    }

    function providerPause() {
      if (player?.pauseVideo) player.pauseVideo()
      else if (player?.pause) void player.pause()
    }

    function providerSeek(time: number) {
      if (player?.seekTo) player.seekTo(time, true)
      else if (player?.seek) void player.seek(time)
    }

    function providerSetVolume(value: number) {
      if (player?.setVolume) void player.setVolume(value)
    }

    function providerSetRate(value: number) {
      if (player?.setPlaybackRate) void player.setPlaybackRate(value)
    }

    function applyRemoteMessage(message: OnDemandVideoMessage) {
      const previousSequence = lastSequenceBySource.get(message.sourceId) || 0
      if (message.sequence <= previousSequence) return
      lastSequenceBySource.set(message.sourceId, message.sequence)
      const payload = message.payload

      if (message.type === 'play') {
        if (!userStarted.value) return
        const time = Number(payload.currentTime)
        if (Number.isFinite(time)) performRemote(() => providerSeek(time))
        performRemote(providerPlay)
      } else if (message.type === 'pause') {
        performRemote(providerPause)
      } else if (message.type === 'seek') {
        const time = Number(payload.currentTime)
        if (Number.isFinite(time)) {
          currentTime.value = time
          performRemote(() => providerSeek(time))
        }
      } else if (message.type === 'volume') {
        const value = Number(payload.volume)
        if (Number.isFinite(value)) {
          volume.value = Math.min(100, Math.max(0, value))
          performRemote(() => providerSetVolume(volume.value))
        }
      } else if (message.type === 'rate') {
        const value = Number(payload.playbackRate)
        if (Number.isFinite(value)) {
          playbackRate.value = value
          performRemote(() => providerSetRate(value))
        }
      } else if (message.type === 'fullscreen') {
        if (payload.active === true) {
          // Browsers may reject fullscreen without a local user gesture. The local control remains
          // available and the synchronized intent is deliberately best-effort.
          void requestFullscreen(false)
        }
      } else if (message.type === 'state') {
        const time = Number(payload.currentTime)
        if (Number.isFinite(time)) {
          const drift = Math.abs(time - currentTime.value)
          currentTime.value = time
          if (drift > 1) performRemote(() => providerSeek(time))
        }
        const remoteVolume = Number(payload.volume)
        if (Number.isFinite(remoteVolume)) {
          volume.value = remoteVolume
          performRemote(() => providerSetVolume(remoteVolume))
        }
        const remoteRate = Number(payload.playbackRate)
        if (Number.isFinite(remoteRate)) {
          playbackRate.value = remoteRate
          performRemote(() => providerSetRate(remoteRate))
        }
        if (payload.playing === true && userStarted.value) performRemote(providerPlay)
        if (payload.playing === false) performRemote(providerPause)
      } else if (message.type === 'ready') {
        sendState()
      }
    }

    const channel = useOnDemandVideoChannel(props.conferenceId, applyRemoteMessage)

    function onProviderState(playing: boolean) {
      const remoteEvent = suppressProviderEvent || Date.now() < remoteActionUntil
      if (playing && !remoteEvent) userStarted.value = true
      setPlayingState(playing, !remoteEvent)
    }

    function onProviderReady() {
      ready.value = true
      const savedProgress = getSavedProgress(props.conferenceId)
      if (typeof savedProgress === 'number' && savedProgress > 0) {
        currentTime.value = savedProgress
        providerSeek(savedProgress)
      }
      if (player?.getDuration) {
        const value = Number(player.getDuration())
        if (Number.isFinite(value)) duration.value = value
      }
      providerSetVolume(volume.value)
      providerSetRate(playbackRate.value)
      channel.send('ready')
    }

    async function createYoutubePlayer() {
      await loadYoutubeApi()
      if (!videoFrame.value || !window.YT?.Player || player) return
      player = new window.YT.Player(videoFrame.value, {
        events: {
          onReady: onProviderReady,
          onStateChange: (event: { data: number }) => onProviderState(event.data === PLAYING)
        }
      })
    }

    async function createPeerTubePlayer() {
      if (!videoFrame.value || player) return
      player = new PeerTubePlayer(videoFrame.value)
      await player.ready
      player.addEventListener?.('playbackStatusUpdate', (event: any) => {
        const status = event?.status || event
        if (typeof status?.currentTime === 'number') currentTime.value = status.currentTime
        if (typeof status?.duration === 'number') duration.value = status.duration
        if (typeof status?.playing === 'boolean') onProviderState(status.playing)
      })
      onProviderReady()
    }

    watch(videoFrame, () => {
      if (props.provider === 'YOUTUBE') void createYoutubePlayer()
      else if (props.provider === 'PEERTUBE') void createPeerTubePlayer()
    }, { flush: 'post' })

    function togglePlayback() {
      userStarted.value = true
      if (isPlaying.value) providerPause()
      else providerPlay()
    }

    function seekFromSlider(event: Event) {
      const time = Number((event.target as HTMLInputElement).value)
      if (!Number.isFinite(time)) return
      currentTime.value = time
      providerSeek(time)
      send('seek', { currentTime: time })
    }

    function setVolumeFromSlider(event: Event) {
      const value = Number((event.target as HTMLInputElement).value)
      if (!Number.isFinite(value)) return
      volume.value = value
      providerSetVolume(value)
      send('volume', { volume: value })
    }

    function setRateFromSelect(event: Event) {
      const value = Number((event.target as HTMLSelectElement).value)
      if (!Number.isFinite(value)) return
      playbackRate.value = value
      providerSetRate(value)
      send('rate', { playbackRate: value })
    }

    async function requestFullscreen(broadcast: boolean) {
      const element = videoFrame.value?.parentElement
      if (!element) return
      try { await element.requestFullscreen?.() } catch { /* browser denied the gesture */ }
      if (broadcast) send('fullscreen', { active: document.fullscreenElement === element })
    }

    function toggleFullscreen() {
      if (document.fullscreenElement) {
        void document.exitFullscreen?.()
        send('fullscreen', { active: false })
      } else {
        void requestFullscreen(true)
      }
    }

    function handleVisibilityChange() {
      if (document.visibilityState === 'hidden') saveCurrentTime()
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('beforeunload', saveCurrentTime)

    onBeforeUnmount(() => {
      stopPolling()
      player?.destroy?.()
      player = null
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('beforeunload', saveCurrentTime)
    })

    return {
      videoFrame, embedUrl, youtubeSrc, ready, isPlaying, currentTime, duration, volume, playbackRate,
      playbackRates, formatTime, togglePlayback, seekFromSlider, setVolumeFromSlider, setRateFromSelect,
      toggleFullscreen
    }
  }
}
</script>

<style scoped>
.on-demand-player { display: flex; flex-direction: column; width: 100%; height: 100%; min-width: 0; min-height: 0; background: var(--color-video-background); }
.video-stage { position: relative; flex: 1; min-height: 0; background: var(--color-video-background); }
.video-frame { display: block; width: 100%; height: 100%; border: 0; background: var(--color-video-background); }
.video-controls { display: flex; align-items: center; gap: 8px; min-height: 36px; padding: 5px 8px; color: var(--color-text-inverse); background: var(--color-video-controls); font-size: 0.75rem; }
.video-control { border: 0; border-radius: 5px; padding: 4px 7px; color: var(--color-text-inverse); background: transparent; cursor: pointer; }
.video-control:hover:not(:disabled) { background: rgba(255, 255, 255, 0.15); }
.video-control:disabled, .video-progress:disabled { opacity: 0.45; cursor: default; }
.video-progress { flex: 1; min-width: 40px; accent-color: var(--color-primary); }
.video-time { min-width: 68px; text-align: center; font-variant-numeric: tabular-nums; }
.volume-control input { width: 62px; accent-color: var(--color-primary); }
.rate-control { color: var(--color-text-inverse); border: 1px solid var(--color-video-control-border); border-radius: 4px; background: var(--color-video-control-surface); font-size: 0.75rem; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 640px) { .video-controls { gap: 4px; padding-inline: 4px; } .video-time { display: none; } .volume-control { display: none; } }
</style>
