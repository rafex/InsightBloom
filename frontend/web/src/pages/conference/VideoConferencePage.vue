<template lang="pug">
.video-conference-page
  .loading-text(v-if="loading") Cargando videollamada...
  .unavailable(v-else-if="!conferenceId")
    p ⚠️ La videollamada no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  .unavailable(v-else-if="deviceBlocked")
    p 🚫 Este dispositivo fue bloqueado por uso con múltiples cuentas.
    p.hint Contacta al organizador si crees que esto es un error.
  .unavailable(v-else-if="accessDenied")
    p 🔒 Esta videollamada requiere acceso al evento.
    p.hint {{ accessDeniedMessage }}
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
    conferenceId: { type: String, default: '' },
    ticketed: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string; ticketed?: boolean }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const deviceBlocked = ref(false)
    const accessDenied = ref(false)
    const accessDeniedMessage = ref('Necesitas un boleto vigente para entrar a la videollamada.')
    let api: { dispose: () => void } | null = null

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        // JaaS (8x8.vc) requiere un JWT firmado para unirse — a cambio no tiene el limite de
        // 5 minutos que meet.jit.si impone a integraciones embebidas de terceros (ver
        // DEC-0020/TASK-0041). Si no hay credenciales de JaaS configuradas en este despliegue,
        // se recae en meet.jit.si publico sin token solo para eventos sin boletos. Un evento
        // ticketed nunca debe caer a una sala publica: copiar su URL no puede convertirse en una
        // forma de evadir el control de boletos.
        const config = await getIntegrationConfig()
        let jaas = null
        if (config.jaasAppId) {
          jaas = await getJaasToken(props.conferenceId as string, auth.state.token as string).catch((e: any) => {
            const code = e?.response?.data?.error
            if (code === 'device_blocked') {
              deviceBlocked.value = true
            } else {
              accessDenied.value = true
              if (code === 'ticket_required') {
                accessDeniedMessage.value = 'Necesitas registrarte y contar con un boleto vigente.'
              } else {
                accessDeniedMessage.value = 'El proveedor de videollamadas seguro no pudo autorizar tu acceso.'
              }
            }
            return null
          })
        } else if (props.ticketed) {
          accessDenied.value = true
          accessDeniedMessage.value = 'El evento requiere una videollamada segura, pero JaaS no está configurado.'
        }
        if (deviceBlocked.value || accessDenied.value) { loading.value = false; return }

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
        if (props.ticketed) {
          accessDenied.value = true
          accessDeniedMessage.value = 'No se pudo cargar la videollamada segura. Intenta nuevamente más tarde.'
        }
        loading.value = false
      }
    })

    onBeforeUnmount(() => {
      api?.dispose()
    })

    return { loading, deviceBlocked, accessDenied, accessDeniedMessage }
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
