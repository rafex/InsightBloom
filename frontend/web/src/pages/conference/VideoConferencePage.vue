<template lang="pug">
.video-conference-page
  .loading-text(v-if="loading") Cargando videollamada...
  .unavailable(v-else-if="!conferenceId")
    p ⚠️ La videollamada no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  #jitsi-container(v-else)
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getIntegrationConfig, getJaasToken } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

declare global {
  interface Window {
    JitsiMeetExternalAPI?: new (domain: string, options: Record<string, unknown>) => {
      dispose: () => void
    }
  }
}

const JITSI_PUBLIC_DOMAIN = 'meet.jit.si'
const JAAS_DOMAIN = '8x8.vc'

function loadJitsiScript(scriptUrl: string): Promise<void> {
  if (window.JitsiMeetExternalAPI) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = scriptUrl
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
    const auth = useAuthStore()
    const loading = ref(true)
    let api: { dispose: () => void } | null = null

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        // JaaS (8x8.vc) requiere un JWT firmado para unirse — a cambio no tiene el limite de
        // 5 minutos que meet.jit.si impone a integraciones embebidas de terceros (ver
        // DEC-0020/TASK-0041). Si no hay credenciales de JaaS configuradas en este despliegue,
        // se recae en meet.jit.si publico sin token (con ese limite conocido).
        const config = await getIntegrationConfig()
        const jaas = config.jaasAppId
          ? await getJaasToken(props.conferenceId as string, auth.state.token as string).catch(() => null)
          : null

        const domain = jaas ? JAAS_DOMAIN : JITSI_PUBLIC_DOMAIN
        const roomName = jaas ? `${jaas.appId}/${jaas.roomName}` : `insightbloom-${props.conferenceId}`
        const scriptUrl = jaas
          ? `https://${JAAS_DOMAIN}/${jaas.appId}/external_api.js`
          : `https://${JITSI_PUBLIC_DOMAIN}/external_api.js`

        await loadJitsiScript(scriptUrl)
        // El contenedor #jitsi-container solo existe en el DOM una vez loading=false
        // (v-else en el template) — hay que esperar el siguiente tick del render antes
        // de buscarlo, o parentNode llega null y Jitsi no se adjunta a nada.
        loading.value = false
        await nextTick()

        api = new window.JitsiMeetExternalAPI!(domain, {
          roomName,
          parentNode: document.querySelector('#jitsi-container'),
          width: '100%',
          height: '100%',
          jwt: jaas ? jaas.token : undefined,
          configOverwrite: { prejoinPageEnabled: false },
          interfaceConfigOverwrite: { SHOW_JITSI_WATERMARK: false }
        })
      } catch (e: any) {
        // degrada silenciosamente: el contenedor queda vacio, no rompe el resto del evento
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
.video-conference-page { flex: 1; min-height: 480px; display: flex; }
#jitsi-container { flex: 1; width: 100%; }
.loading-text { padding: 40px; text-align: center; color: #6b7280; }
.unavailable { margin: 40px auto; text-align: center; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: #78350f; font-size: 0.85rem; margin-top: 6px; }
</style>
