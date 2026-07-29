<template lang="pug">
.ide-page
  .ide-container
    .ide-header
      h2 🖥️ Tu workspace de código
      p.subtitle Accede a tu ambiente de desarrollo asignado para este evento.

    .ide-status
      div(v-if="loadingAvailability" class="loading-spinner") Cargando opciones...
      template(v-else-if="!chosenVariant && !sandbox")
        p.subtitle ¿Cómo querés trabajar en este evento?
        .variant-picker
          button.variant-btn(
            type="button"
            :class="{ active: chosenVariant === 'web' }"
            :disabled="availability ? !availability.web.available : false"
            @click="chooseVariant('web')"
          )
            span.variant-title 🖥️ Web
            span.variant-desc code-server en el navegador, un asiento por alumno
            span.variant-slots(v-if="availability") {{ availability.web.activeCount }}/{{ availability.web.capacity }} ocupados
          button.variant-btn(
            type="button"
            :class="{ active: chosenVariant === 'cli' }"
            :disabled="availability ? !availability.cli.available : false"
            @click="chooseVariant('cli')"
          )
            span.variant-title ⌨️ CLI
            span.variant-desc Terminal con vim/neovim, se reutiliza entre alumnos
            span.variant-slots(v-if="availability") {{ availability.cli.activeCount }}/{{ availability.cli.capacity }} ocupados
        p.hint(v-if="availability && !availability.web.available") Web está lleno -- solo queda disponible CLI.
      div(v-else-if="loading" class="loading-spinner") Cargando sandbox...
      div(v-else-if="error" class="error-message") {{ error }}
      .pending-sandbox(v-else-if="sandbox && sandbox.status === 'PENDING'")
        sandbox-loading-animation(:message="pendingMessage")
        p.hint Esto puede tardar hasta un par de minutos la primera vez.
      template(v-else-if="sandbox")
        .sandbox-info
          .info-row
            .label Modo:
            .value {{ sandbox.variant === 'cli' ? '⌨️ CLI' : '🖥️ Web' }}
          .info-row
            .label Sandbox UUID:
            .value {{ sandbox.sandboxUuid }}
          .info-row
            .label Slot asignado:
            .value {{ sandbox.sandboxSlot }}
          .info-row
            .label Vence en:
            .value {{ formattedExpiry }}

        .ide-actions
          a.link-btn.link-btn-primary(v-if="fullGatewayUrl" :href="ideSessionUrl" target="_blank" rel="noopener")
            span 🚀 Abrir IDE en navegador
          BaseButton(variant="secondary" @click="downloadWorkspace" :disabled="downloadingWorkspace")
            span(v-if="!downloadingWorkspace") 📥 Descargar workspace
            span(v-else) Descargando...
          BaseButton(variant="secondary" @click="publishPreview" :disabled="publishingPreview")
            span(v-if="!publishingPreview") 🌐 Publicar página temporal
            span(v-else) Validando y publicando...
          BaseButton(variant="secondary" @click="publishApp" :disabled="publishingApp")
            span(v-if="!publishingApp") 🔌 Publicar backend/API
            span(v-else) Publicando...
          button.btn-tertiary(@click="copyGatewayUrl" :title="`Copiar: ${fullGatewayUrl}`")
            span {{ urlCopied ? '✓ Copiado' : '📋 Copiar URL' }}
          button.btn-tertiary(@click="switchVariant")
            span 🔀 Cambiar de modo
        .preview-result(v-if="preview")
          strong Página publicada temporalmente
          p Esta URL contiene solo una copia estática validada del workspace y vence el {{ formatPreviewExpiry }}.
          .preview-actions
            a.btn-tertiary(:href="preview.url" target="_blank" rel="noopener noreferrer") Abrir página
            BaseButton(variant="secondary" @click="copyPreviewUrl") {{ previewUrlCopied ? '✓ Copiado' : '📋 Copiar URL' }}
            BaseButton(variant="danger" @click="revokePreview") Revocar
        .preview-result(v-if="appPreview")
          strong Backend/API publicada
          p Esta URL proxea tu proceso vivo (no una copia estática) y vence el {{ formatAppPreviewExpiry }}.
            |  Requiere el header "X-Preview-Token" con el token de abajo — cualquiera que lo
            |  tenga puede usar la API mientras esté vigente.
          .preview-actions
            a.btn-tertiary(:href="appPreview.url" target="_blank" rel="noopener noreferrer") Abrir URL
            BaseButton(variant="secondary" @click="copyAppPreviewUrl") {{ appPreviewUrlCopied ? '✓ Copiado' : '📋 Copiar URL' }}
            BaseButton(variant="secondary" @click="copyAppPreviewToken") {{ appPreviewTokenCopied ? '✓ Copiado' : '🔑 Copiar token' }}
            BaseButton(variant="danger" @click="revokeApp") Revocar

      div(v-else class="no-sandbox")
        p ❌ No tienes un sandbox asignado en este evento.
        p.hint Solicita acceso al organizador o intenta más tarde.
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import {
  getSandbox,
  getSandboxAvailability,
  generateWorkspaceDownloadUrl,
  publishWorkspacePreview,
  revokeWorkspacePreview,
  publishAppPreview,
  revokeAppPreview,
  streamSandboxStatus,
  AuthenticatedEventStream
} from '@/services/api/usersApi'
import type { SandboxInfo, SandboxAvailability, SandboxVariant, WorkspacePreviewInfo, AppPreviewInfo } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import SandboxLoadingAnimation from '@/components/SandboxLoadingAnimation.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

// El endpoint devuelve una instantánea; mientras el Pod/seat-agent se prepara
// usamos backoff para no golpear la API cada pocos segundos durante un cold start.
// El canal SSE es el camino principal; la consulta inicial y el polling acotado
// siguen siendo necesarios para reconexiones, proxies y despliegues escalonados.
const POLL_INITIAL_DELAY_MS = 2000
const POLL_MAX_DELAY_MS = 10000
const POLL_TIMEOUT_MS = 5 * 60_000

export default {
  name: 'IdePage',
  components: { SandboxLoadingAnimation, BaseButton },
  props: {
    conferenceId: {
      type: String,
      required: true
    }
  },
  setup(props) {
    const sandbox = ref<SandboxInfo | null>(null)
    const loading = ref(false)
    const error = ref('')
    const downloadingWorkspace = ref(false)
    const publishingPreview = ref(false)
    const preview = ref<WorkspacePreviewInfo | null>(null)
    const previewUrlCopied = ref(false)
    const publishingApp = ref(false)
    const appPreview = ref<AppPreviewInfo | null>(null)
    const appPreviewUrlCopied = ref(false)
    const appPreviewTokenCopied = ref(false)
    const urlCopied = ref(false)
    const auth = useAuthStore()
    const availability = ref<SandboxAvailability | null>(null)
    const loadingAvailability = ref(true)
    const chosenVariant = ref<SandboxVariant | null>(null)
    let pollTimer: ReturnType<typeof setTimeout> | null = null
    let pollDeadline = 0
    let pollDelayMs = POLL_INITIAL_DELAY_MS
    let sandboxStream: AuthenticatedEventStream | null = null

    function stopPolling() {
      if (pollTimer) clearTimeout(pollTimer)
      pollTimer = null
    }

    function closeSandboxStream() {
      sandboxStream?.close()
      sandboxStream = null
    }

    function resetPolling() {
      stopPolling()
      closeSandboxStream()
      pollDeadline = 0
      pollDelayMs = POLL_INITIAL_DELAY_MS
    }

    const pendingMessage = ref('🔧 Preparando tu ambiente de desarrollo...')

    const formattedExpiry = computed(() => {
      if (!sandbox.value?.expiresInSeconds) return 'N/A'
      const hours = Math.floor(sandbox.value.expiresInSeconds / 3600)
      const minutes = Math.floor((sandbox.value.expiresInSeconds % 3600) / 60)
      if (hours > 0) return `${hours}h ${minutes}m`
      return `${minutes}m`
    })

    const formatPreviewExpiry = computed(() => preview.value ? new Date(preview.value.expiresAt).toLocaleString() : '')
    const formatAppPreviewExpiry = computed(() => appPreview.value ? new Date(appPreview.value.expiresAt).toLocaleString() : '')

    // Fase 3b: el gateway (ide-insightbloom...) no tiene un target fijo — resuelve por-sesion
    // contra el sandbox del usuario a partir de ib_token + conferenceId en la query string
    // (ver AuthGateHandler.checkAuth / ResolveSandboxTargetUseCase).
    const fullGatewayUrl = computed(() => {
      if (!sandbox.value?.gatewayUrl || !auth.state.token) return ''
      const params = new URLSearchParams({ ib_token: auth.state.token, conferenceId: props.conferenceId })
      return `${sandbox.value.gatewayUrl}?${params.toString()}`
    })

    // El boton "Abrir IDE" no navega directo al gateway -- abre esta MISMA app en pestana nueva
    // (IdeSessionPage.vue), que embebe el IDE real en un iframe para poder superponer el panel
    // de ayuda de Neovim. Ruta TOP-LEVEL (/ide-session, no /c/:friendlyId/ide-session) a
    // proposito -- fuera de ConferencePage.vue, que monta AppHeader + mapa de intro antes de su
    // router-view y tapaba el panel de ayuda. "Copiar URL" (mas abajo) sigue devolviendo la URL
    // directa del gateway a proposito -- sirve para pegarla en otro dispositivo/QR sin depender
    // de esta SPA.
    const ideSessionUrl = computed(() => {
      if (!fullGatewayUrl.value) return ''
      const params = new URLSearchParams({ target: fullGatewayUrl.value })
      return `/ide-session?${params.toString()}`
    })

    async function loadAvailability() {
      loadingAvailability.value = true
      try {
        availability.value = await getSandboxAvailability(props.conferenceId, auth.state.token)
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || e.message || 'Error al consultar disponibilidad'
      } finally {
        loadingAvailability.value = false
      }
    }

    async function loadSandbox(isPoll = false) {
      if (!auth.state.token) {
        error.value = 'No autenticado'
        loading.value = false
        return
      }
      if (!chosenVariant.value) return

      if (!isPoll) resetPolling()

      try {
        if (!isPoll) loading.value = true
        sandbox.value = await getSandbox(props.conferenceId, auth.state.token, chosenVariant.value)
        if (sandbox.value?.status === 'PENDING') {
          void openSandboxStream()
        } else {
          stopPolling()
          closeSandboxStream()
        }
      } catch (e: any) {
        if (e.message?.includes('404')) {
          sandbox.value = null
        } else {
          // e.message de axios es generico ("Request failed with status code 403") -- el
          // mensaje real (ej. "device_blocked") viene en el body que arma BaseResourceHandler.
          error.value = e.response?.data?.error?.message || e.message || 'Error al cargar el sandbox'
        }
        stopPolling()
      } finally {
        loading.value = false
      }
    }

    async function openSandboxStream() {
      if (!auth.state.token || !chosenVariant.value) return
      closeSandboxStream()
      try {
        const stream = await streamSandboxStatus(
          props.conferenceId,
          auth.state.token,
          chosenVariant.value
        )
        sandboxStream = stream

        stream.addEventListener('status', (event) => {
          if (sandboxStream !== stream) return
          try {
            const next = JSON.parse((event as MessageEvent).data) as SandboxInfo
            sandbox.value = { ...(sandbox.value as SandboxInfo), ...next }
            if (next.status === 'READY') {
              stopPolling()
              closeSandboxStream()
            }
          } catch {
            // Un evento malformado no debe dejar la pantalla bloqueada: el
            // fallback de polling podrá recuperar el estado completo.
            closeSandboxStream()
            schedulePoll()
          }
        })
        stream.addEventListener('failure', (event) => {
          if (sandboxStream !== stream) return
          try {
            const failure = JSON.parse((event as MessageEvent).data) as { message?: string }
            if (failure.message) error.value = failure.message
          } catch {
            // El mensaje de fallback es suficiente si el payload no es JSON.
          }
          closeSandboxStream()
          schedulePoll()
        })
        stream.onerror = () => {
          if (sandboxStream !== stream) return
          closeSandboxStream()
          schedulePoll()
        }
      } catch {
        // Si el proxy, el navegador o una versión anterior del backend no
        // soportan el stream, seguimos con el fallback acotado.
        schedulePoll()
      }
    }

    function chooseVariant(variant: SandboxVariant) {
      chosenVariant.value = variant
      loadSandbox()
    }

    // Un alumno tiene un solo sandbox activo a la vez (ver AssignSandboxUseCase) -- volver al
    // picker no borra nada todavia, recien se libera el sandbox previo cuando efectivamente se
    // elige la otra variante (mismo GET /sandbox?variant=..., ahora con una distinta).
    function switchVariant() {
      resetPolling()
      sandbox.value = null
      chosenVariant.value = null
      loadAvailability()
    }

    function schedulePoll() {
      if (pollDeadline === 0) pollDeadline = Date.now() + POLL_TIMEOUT_MS
      if (Date.now() >= pollDeadline) {
        stopPolling()
        error.value = 'El sandbox está tardando más de lo esperado. Intenta recargar la página.'
        return
      }
      stopPolling()
      const delay = pollDelayMs
      pollDelayMs = Math.min(POLL_MAX_DELAY_MS, Math.ceil(pollDelayMs * 1.5))
      pollTimer = setTimeout(() => loadSandbox(true), delay)
    }

    async function downloadWorkspace() {
      if (!sandbox.value) return

      try {
        downloadingWorkspace.value = true
        const downloadInfo = await generateWorkspaceDownloadUrl(props.conferenceId, auth.state.token)

        // Redirigir al URL de descarga
        window.location.href = downloadInfo.downloadUrl
      } catch (e: any) {
        error.value = 'Error al generar descarga: ' + (e.message || 'Intenta más tarde')
      } finally {
        downloadingWorkspace.value = false
      }
    }

    async function publishPreview() {
      if (!auth.state.token || !sandbox.value) return
      try {
        publishingPreview.value = true
        preview.value = await publishWorkspacePreview(props.conferenceId, auth.state.token)
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || e.response?.data?.error || 'No se pudo publicar la página. Verifica que tenga un index.html y archivos estáticos permitidos.'
      } finally {
        publishingPreview.value = false
      }
    }

    async function revokePreview() {
      if (!auth.state.token || !preview.value) return
      try {
        await revokeWorkspacePreview(props.conferenceId, preview.value.publicationId, auth.state.token)
        preview.value = null
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo revocar la página'
      }
    }

    function copyPreviewUrl() {
      if (!preview.value) return
      navigator.clipboard.writeText(preview.value.url)
      previewUrlCopied.value = true
      setTimeout(() => { previewUrlCopied.value = false }, 2000)
    }

    async function publishApp() {
      if (!auth.state.token || !sandbox.value) return
      try {
        publishingApp.value = true
        appPreview.value = await publishAppPreview(props.conferenceId, auth.state.token)
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo publicar el backend. Verifica que tu proceso esté escuchando en el puerto $APP_PORT.'
      } finally {
        publishingApp.value = false
      }
    }

    async function revokeApp() {
      if (!auth.state.token || !appPreview.value) return
      try {
        await revokeAppPreview(props.conferenceId, appPreview.value.publicationId, auth.state.token)
        appPreview.value = null
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo revocar la publicación'
      }
    }

    function copyAppPreviewUrl() {
      if (!appPreview.value) return
      navigator.clipboard.writeText(appPreview.value.url)
      appPreviewUrlCopied.value = true
      setTimeout(() => { appPreviewUrlCopied.value = false }, 2000)
    }

    function copyAppPreviewToken() {
      if (!appPreview.value) return
      navigator.clipboard.writeText(appPreview.value.accessToken)
      appPreviewTokenCopied.value = true
      setTimeout(() => { appPreviewTokenCopied.value = false }, 2000)
    }

    function copyGatewayUrl() {
      if (!fullGatewayUrl.value) return
      navigator.clipboard.writeText(fullGatewayUrl.value)
      urlCopied.value = true
      setTimeout(() => { urlCopied.value = false }, 2000)
    }

    onMounted(() => {
      loadAvailability()
    })

    onBeforeUnmount(() => {
      stopPolling()
      closeSandboxStream()
    })

    return {
      sandbox, loading, error, downloadingWorkspace, urlCopied,
      availability, loadingAvailability, chosenVariant, chooseVariant, switchVariant,
      formattedExpiry, fullGatewayUrl, ideSessionUrl, downloadWorkspace, copyGatewayUrl,
      pendingMessage, publishingPreview, preview, formatPreviewExpiry, publishPreview,
      revokePreview, copyPreviewUrl, previewUrlCopied,
      publishingApp, appPreview, formatAppPreviewExpiry, publishApp, revokeApp,
      copyAppPreviewUrl, copyAppPreviewToken, appPreviewUrlCopied, appPreviewTokenCopied
    }
  }
}
</script>

<style scoped>
.ide-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.ide-container {
  background: var(--color-surface);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  padding: 32px;
}

.ide-header {
  margin-bottom: 28px;
  text-align: center;
}

.ide-header h2 {
  margin: 0 0 8px;
  font-size: 1.5rem;
  color: var(--color-heading);
}

.subtitle {
  margin: 0;
  font-size: 0.95rem;
  color: var(--color-text-muted);
}

.ide-status {
  margin-top: 24px;
}

.variant-picker {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.variant-btn {
  flex: 1;
  min-width: 200px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  text-align: left;
  padding: 16px 20px;
  border: 1.5px solid var(--color-border-subtle);
  border-radius: 10px;
  background: var(--color-surface);
  cursor: pointer;
  transition: all 0.2s ease;
}

.variant-btn:hover:not(:disabled) {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
}

.variant-btn.active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.variant-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.variant-title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-heading);
}

.variant-desc {
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.variant-slots {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin-top: 4px;
}

.pending-sandbox {
  text-align: center;
  padding: 8px 0 24px;
}

.pending-sandbox .hint {
  margin-top: 4px;
}

.loading-spinner {
  text-align: center;
  padding: 40px;
  color: var(--color-text-muted);
  font-size: 1.1rem;
}

.error-message {
  background: var(--color-danger-soft);
  color: var(--color-danger-dark);
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid var(--color-danger);
  margin-bottom: 16px;
}

.sandbox-info {
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
}

.info-row {
  display: flex;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  font-weight: 600;
  color: var(--color-text-secondary);
  min-width: 140px;
}

.value {
  color: var(--color-text-muted);
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 0.9rem;
}

.ide-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.preview-result {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--color-success);
  border-radius: 10px;
  background: var(--color-success-soft);
  color: var(--color-success);
}

.preview-result p {
  margin: 6px 0 12px;
  font-size: 0.9rem;
}

.preview-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

a.btn-tertiary {
  padding: 10px 20px;
  border: 1.5px solid var(--color-primary-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-primary);
  font-size: 0.95rem;
  font-weight: 600;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 6px;
}

a.btn-tertiary:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-border);
}

.no-sandbox {
  text-align: center;
  padding: 40px 24px;
  color: var(--color-text-muted);
}

.no-sandbox p {
  margin: 12px 0;
  font-size: 1rem;
}

.hint {
  font-size: 0.9rem;
  color: var(--color-text-muted);
}

@media (max-width: 600px) {
  .ide-page {
    padding: 16px;
  }

  .ide-container {
    padding: 20px;
  }

  .ide-actions {
    flex-direction: column;
  }

  .sandbox-info {
    padding: 16px;
  }

  .info-row {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
