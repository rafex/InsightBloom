<template lang="pug">
.video-conference-page(ref="pageRoot")
  .takeover-toolbar(
    v-if="jaasEnabled && takeoverPanelOpen"
    ref="takeoverPanel"
    :class="{ 'is-dragging': dragging }"
    :style="panelStyle"
  )
    .takeover-header(@pointerdown="startDrag")
      span.takeover-drag-handle(title="Arrastra para mover este panel") ⋮⋮ Control de videollamada
      BaseButton.takeover-close(variant="ghost" size="sm" type="button" aria-label="Ocultar controles de videollamada" title="Ocultar controles" @pointerdown.stop @click="closeTakeoverPanel") ×
    BaseButton(variant="secondary" size="sm" type="button" :loading="takingControl" @click="takeControl") 🎥 Tomar control de la videollamada
    p.takeover-hint(v-if="!sessionTakenOver") Si abriste la llamada en otro dispositivo, este botón cierra la sesión anterior.
    p.takeover-hint.takeover-warning(v-else) Esta sesión fue reemplazada por otro dispositivo.
    FeedbackMessage(v-if="takeoverError" :message="takeoverError" tone="error")
  BaseButton.takeover-reopen(
    v-if="jaasEnabled && !takeoverPanelOpen"
    variant="secondary"
    size="sm"
    type="button"
    aria-label="Mostrar controles de videollamada"
    title="Mostrar controles de videollamada"
    @click="openTakeoverPanel"
  ) 🎥
  LoadingState(v-if="loading" message="Cargando videollamada...")
  NoticeState(v-else-if="!conferenceId" title="Videollamada no disponible" message="Intenta más tarde o contacta al organizador." tone="warning")
  NoticeState(v-else-if="deviceBlocked" title="Dispositivo bloqueado" message="Este dispositivo fue bloqueado por uso con múltiples cuentas. Contacta al organizador si crees que esto es un error." tone="danger")
  NoticeState(v-else-if="accessDenied" title="Acceso requerido" :message="accessDeniedMessage" tone="warning")
  #jitsi-container(v-else)
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import {
  AuthenticatedEventStream,
  getIntegrationConfig,
  getJaasToken,
  streamVideoSession,
  takeOverVideoCall
} from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import NoticeState from '@/components/ui/NoticeState.vue'

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
  components: { BaseButton, FeedbackMessage, LoadingState, NoticeState },
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
    const takeoverPanelOpen = ref(true)
    const dragging = ref(false)
    const pageRoot = ref<HTMLElement | null>(null)
    const takeoverPanel = ref<HTMLElement | null>(null)
    const dragPosition = ref<{ x: number; y: number } | null>(null)
    const dragOffset = ref({ x: 0, y: 0 })
    const panelStyle = computed(() => dragPosition.value
      ? { left: `${dragPosition.value.x}px`, top: `${dragPosition.value.y}px`, right: 'auto' }
      : {})
    let api: { dispose: () => void } | null = null
    let sessionStream: AuthenticatedEventStream | null = null

    function startDrag(event: PointerEvent) {
      if (event.button !== 0 || !pageRoot.value || !takeoverPanel.value) return
      const panelRect = takeoverPanel.value.getBoundingClientRect()
      const rootRect = pageRoot.value.getBoundingClientRect()
      dragPosition.value = { x: panelRect.left - rootRect.left, y: panelRect.top - rootRect.top }
      dragOffset.value = { x: event.clientX - panelRect.left, y: event.clientY - panelRect.top }
      dragging.value = true
      window.addEventListener('pointermove', moveDrag)
      window.addEventListener('pointerup', stopDrag)
    }

    function moveDrag(event: PointerEvent) {
      if (!dragging.value || !pageRoot.value || !takeoverPanel.value) return
      const rootRect = pageRoot.value.getBoundingClientRect()
      const panel = takeoverPanel.value
      const maxX = Math.max(8, rootRect.width - panel.offsetWidth - 8)
      const maxY = Math.max(8, rootRect.height - panel.offsetHeight - 8)
      dragPosition.value = {
        x: Math.min(maxX, Math.max(8, event.clientX - rootRect.left - dragOffset.value.x)),
        y: Math.min(maxY, Math.max(8, event.clientY - rootRect.top - dragOffset.value.y))
      }
    }

    function stopDrag() {
      dragging.value = false
      window.removeEventListener('pointermove', moveDrag)
      window.removeEventListener('pointerup', stopDrag)
    }

    function closeTakeoverPanel() {
      stopDrag()
      takeoverPanelOpen.value = false
    }

    function openTakeoverPanel() {
      takeoverPanelOpen.value = true
    }

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
      stopDrag()
      api?.dispose()
      sessionStream?.close()
    })

    return {
      pageRoot, takeoverPanel, panelStyle, dragging, takeoverPanelOpen,
      loading, deviceBlocked, accessDenied, accessDeniedMessage, jaasEnabled,
      sessionTakenOver, takingControl, takeoverError, takeControl,
      startDrag, closeTakeoverPanel, openTakeoverPanel
    }
  }
}
</script>

<style scoped>
.video-conference-page { position: relative; flex: 1; min-height: 480px; display: flex; }
.takeover-toolbar { position: absolute; z-index: 2; top: 12px; right: 16px; display: flex; flex-direction: column; align-items: stretch; gap: 6px; padding: 10px; max-width: min(420px, calc(100% - 32px)); background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: var(--radius-md); box-shadow: var(--shadow-overlay); }
.takeover-toolbar.is-dragging { user-select: none; }
.takeover-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; cursor: grab; touch-action: none; }
.is-dragging .takeover-header { cursor: grabbing; }
.takeover-drag-handle { color: var(--color-text-secondary); font-size: .78rem; font-weight: 700; letter-spacing: .02em; }
.takeover-close { width: 30px; height: 30px; padding: 0; font-size: 1.15rem; line-height: 1; }
.takeover-hint { margin: 0; font-size: .75rem; color: var(--color-text-muted); text-align: right; }
.takeover-warning { color: var(--color-warning); }
.takeover-reopen { position: absolute; z-index: 2; top: 12px; right: 16px; min-width: 40px; padding-inline: 10px; }
#jitsi-container { flex: 1; width: 100%; }
</style>
