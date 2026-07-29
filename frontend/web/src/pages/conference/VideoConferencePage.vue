<template lang="pug">
.video-conference-page
  .takeover-toolbar(v-if="jaasEnabled")
    button.btn-takeover(type="button" :disabled="takingControl" @click="takeControl")
      | {{ takingControl ? 'Tomando control...' : '🎥 Tomar control de la videollamada' }}
    p.takeover-hint(v-if="!sessionTakenOver") Si abriste la llamada en otro dispositivo, este botón cierra la sesión anterior.
    p.takeover-hint.takeover-warning(v-else) Esta sesión fue reemplazada por otro dispositivo.
    p.takeover-error(v-if="takeoverError") {{ takeoverError }}
  LoadingState(v-if="loading" message="Cargando videollamada...")
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
import {
  AuthenticatedEventStream,
  getIntegrationConfig,
  getJaasToken,
  streamVideoSession,
  takeOverVideoCall
} from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import LoadingState from '@/components/ui/LoadingState.vue'

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
  components: { LoadingState },
  props: {
    conferenceId: { type: String, default: '' },
    ticketed: { type: Boolean, default: false },
    inviteAlias: { type: String, default: '' }
  },
  setup(props: { conferenceId?: string; ticketed?: boolean; inviteAlias?: string }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const deviceBlocked = ref(false)
    const accessDenied = ref(false)
    const accessDeniedMessage = ref('Necesitas un boleto vigente para entrar a la videollamada.')
    const jaasEnabled = ref(false)
    const sessionTakenOver = ref(false)
    const takingControl = ref(false)
    const takeoverError = ref('')
    let api: { dispose: () => void } | null = null
    let sessionStream: AuthenticatedEventStream | null = null

    function handleSessionRevoked() {
      api?.dispose()
      api = null
      sessionTakenOver.value = true
      accessDenied.value = true
      accessDeniedMessage.value = 'Otra sesión tomó el control de esta videollamada.'
      loading.value = false
    }

    async function initialize() {
      if (!props.conferenceId) { loading.value = false; return }
      loading.value = true
      deviceBlocked.value = false
      accessDenied.value = false
      sessionTakenOver.value = false
      takeoverError.value = ''
      try {
        // JaaS (8x8.vc) requiere un JWT firmado para unirse. Si no hay credenciales
        // configuradas, solo los eventos sin boleto pueden usar meet.jit.si público.
        const config = await getIntegrationConfig()
        let jaas = null
        if (config.jaasAppId) {
          jaas = await getJaasToken(props.conferenceId as string, auth.state.token as string).catch((e: any) => {
            const code = e?.response?.data?.error
            if (code === 'device_blocked') {
              deviceBlocked.value = true
            } else {
              accessDenied.value = true
              accessDeniedMessage.value = code === 'ticket_required'
                ? 'Necesitas registrarte y contar con un boleto vigente.'
                : 'El proveedor de videollamadas seguro no pudo autorizar tu acceso.'
            }
            return null
          })
        } else if (props.ticketed) {
          accessDenied.value = true
          accessDeniedMessage.value = 'El evento requiere una videollamada segura, pero JaaS no está configurado.'
        }
        jaasEnabled.value = Boolean(jaas)
        if (deviceBlocked.value || accessDenied.value) { loading.value = false; return }

        // Es una señal de control, no una dependencia para cargar Jitsi. Así un despliegue
        // escalonado puede seguir atendiendo llamadas aunque la ruta SSE aún no exista.
        if (jaas && auth.state.token) {
          try {
            sessionStream?.close()
            sessionStream = await streamVideoSession(props.conferenceId as string, auth.state.token)
            sessionStream.addEventListener('revoked', handleSessionRevoked)
          } catch {
            sessionStream = null
          }
        }

        const domain = jaas ? JAAS_DOMAIN : JITSI_PUBLIC_DOMAIN
        const roomName = jaas ? `${jaas.appId}/${jaas.roomName}` : `insightbloom-${props.conferenceId}`
        const scriptUrl = jaas
          ? `https://${JAAS_DOMAIN}/${jaas.appId}/external_api.js`
          : `https://${JITSI_PUBLIC_DOMAIN}/external_api.js`

        await loadJitsiScript(scriptUrl)
        loading.value = false
        await nextTick()

        api = new window.JitsiMeetExternalAPI!(domain, {
          roomName,
          parentNode: document.querySelector('#jitsi-container'),
          width: '100%',
          height: '100%',
          lang: 'es',
          jwt: jaas ? jaas.token : undefined,
          configOverwrite: {
            prejoinPageEnabled: false,
            brandingRoomAlias: props.inviteAlias || undefined,
            toolbarButtons: [
              'microphone', 'camera', 'chat', 'participants-pane', 'settings', 'hangup',
              'tileview', 'fullscreen', 'raisehand', 'select-background', 'fodeviceselection',
              'videoquality', 'security', 'help'
            ]
          },
          interfaceConfigOverwrite: { SHOW_JITSI_WATERMARK: false }
        })
      } catch (e: any) {
        if (props.ticketed) {
          accessDenied.value = true
          accessDeniedMessage.value = 'No se pudo cargar la videollamada segura. Intenta nuevamente más tarde.'
        }
        loading.value = false
      }
    }

    async function takeControl() {
      if (!props.conferenceId || !auth.state.token || takingControl.value) return
      takingControl.value = true
      takeoverError.value = ''
      try {
        await takeOverVideoCall(props.conferenceId, auth.state.token)
        api?.dispose()
        api = null
        sessionStream?.close()
        sessionStream = null
        await initialize()
      } catch (e: any) {
        takeoverError.value = e?.response?.data?.error?.message
          || 'No se pudo tomar el control de la videollamada. Intenta nuevamente.'
      } finally {
        takingControl.value = false
      }
    }

    onMounted(() => { void initialize() })

    onBeforeUnmount(() => {
      api?.dispose()
      sessionStream?.close()
    })

    return {
      loading, deviceBlocked, accessDenied, accessDeniedMessage, jaasEnabled,
      sessionTakenOver, takingControl, takeoverError, takeControl
    }
  }
}
</script>

<style scoped>
.video-conference-page { position: relative; flex: 1; min-height: 480px; display: flex; }
.takeover-toolbar { position: absolute; z-index: 2; top: 12px; right: 16px; display: flex; flex-direction: column; align-items: flex-end; gap: 4px; max-width: min(420px, calc(100% - 32px)); }
.btn-takeover { border: 1px solid var(--color-primary-border); border-radius: 8px; padding: 8px 12px; color: var(--color-primary-dark); background: var(--color-primary-soft); font-weight: 600; cursor: pointer; }
.btn-takeover:hover:not(:disabled) { background: var(--color-primary-soft); }
.btn-takeover:disabled { opacity: .65; cursor: wait; }
.takeover-hint { margin: 0; font-size: .75rem; color: var(--color-text-muted); text-align: right; }
.takeover-warning { color: var(--color-warning); }
.takeover-error { margin: 0; color: var(--color-danger-dark); font-size: .78rem; }
#jitsi-container { flex: 1; width: 100%; }
.unavailable { margin: 40px auto; text-align: center; color: var(--color-warning); background: var(--color-warning-soft); border: 1px solid var(--color-warning); border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: var(--color-warning); font-size: 0.85rem; margin-top: 6px; }
</style>
