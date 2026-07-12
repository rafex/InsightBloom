<template lang="pug">
.video-conference-page
  .loading-text(v-if="loading") Cargando videollamada...
  .unavailable(v-else-if="!conferenceId")
    p ⚠️ La videollamada no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  #jitsi-container(v-else)
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

declare global {
  interface Window {
    JitsiMeetExternalAPI?: new (domain: string, options: Record<string, unknown>) => {
      dispose: () => void
    }
  }
}

const JITSI_PUBLIC_DOMAIN = 'meet.jit.si'
const JITSI_SCRIPT_URL = `https://${JITSI_PUBLIC_DOMAIN}/external_api.js`

function loadJitsiScript(): Promise<void> {
  if (window.JitsiMeetExternalAPI) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = JITSI_SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('No se pudo cargar el script de Jitsi'))
    document.head.appendChild(script)
  })
}

export default {
  name: 'VideoConferencePage',
  props: {
    conferenceId: { type: String, default: '' }
  },
  setup(props: { conferenceId?: string }) {
    const loading = ref(true)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let api: { dispose: () => void } | null = null

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        await loadJitsiScript()
        // Sala derivada deterministicamente del uuid del evento (FR-011): todos los
        // asistentes llegan a la misma sala sin coordinacion manual.
        const roomName = `insightbloom-${props.conferenceId}`
        api = new window.JitsiMeetExternalAPI!(JITSI_PUBLIC_DOMAIN, {
          roomName,
          parentNode: document.querySelector('#jitsi-container'),
          width: '100%',
          height: '100%',
          configOverwrite: { prejoinPageEnabled: false },
          interfaceConfigOverwrite: { SHOW_JITSI_WATERMARK: false }
        })
      } catch (e: any) {
        // degrada silenciosamente: el contenedor queda vacio, no rompe el resto del evento
      } finally {
        loading.value = false
      }
    })

    onBeforeUnmount(() => {
      api?.dispose()
    })

    return { loading, conferenceId: props.conferenceId }
  }
}
</script>

<style scoped>
.video-conference-page { height: calc(100vh - 220px); min-height: 480px; display: flex; }
#jitsi-container { flex: 1; width: 100%; }
.loading-text { padding: 40px; text-align: center; color: #6b7280; }
.unavailable { margin: 40px auto; text-align: center; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: #78350f; font-size: 0.85rem; margin-top: 6px; }
</style>
