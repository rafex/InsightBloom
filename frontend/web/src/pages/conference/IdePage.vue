<template lang="pug">
.ide-page
  .ide-container
    .ide-header
      h2 🖥️ Tu workspace de código
      p.subtitle Accede a tu ambiente de desarrollo asignado para este evento.

    .ide-status
      div(v-if="loading" class="loading-spinner") Cargando sandbox...
      div(v-else-if="error" class="error-message") {{ error }}
      div(v-else-if="sandbox && sandbox.status === 'PENDING'" class="loading-spinner")
        p 🔧 Preparando tu ambiente de desarrollo...
        p.hint Esto puede tardar hasta un par de minutos la primera vez.
      template(v-else-if="sandbox")
        .sandbox-info
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
          a.btn-primary(v-if="sandbox.gatewayUrl" :href="sandbox.gatewayUrl" target="_blank" rel="noopener")
            span 🚀 Abrir IDE en navegador
          button.btn-secondary(@click="downloadWorkspace" :disabled="downloadingWorkspace")
            span(v-if="!downloadingWorkspace") 📥 Descargar workspace
            span(v-else) Descargando...
          button.btn-tertiary(@click="copyGatewayUrl" :title="`Copiar: ${sandbox.gatewayUrl}`")
            span {{ urlCopied ? '✓ Copiado' : '📋 Copiar URL' }}

      div(v-else class="no-sandbox")
        p ❌ No tienes un sandbox asignado en este evento.
        p.hint Solicita acceso al organizador o intenta más tarde.
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getSandbox, generateWorkspaceDownloadUrl } from '@/services/api/usersApi'
import type { SandboxInfo } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

const POLL_INTERVAL_MS = 3000
const POLL_TIMEOUT_MS = 5 * 60_000

export default {
  name: 'IdePage',
  props: {
    conferenceId: {
      type: String,
      required: true
    }
  },
  setup(props) {
    const sandbox = ref<SandboxInfo | null>(null)
    const loading = ref(true)
    const error = ref('')
    const downloadingWorkspace = ref(false)
    const urlCopied = ref(false)
    const auth = useAuthStore()
    let pollTimer: ReturnType<typeof setTimeout> | null = null
    let pollDeadline = 0

    function stopPolling() {
      if (pollTimer) clearTimeout(pollTimer)
      pollTimer = null
    }

    const formattedExpiry = computed(() => {
      if (!sandbox.value?.expiresInSeconds) return 'N/A'
      const hours = Math.floor(sandbox.value.expiresInSeconds / 3600)
      const minutes = Math.floor((sandbox.value.expiresInSeconds % 3600) / 60)
      if (hours > 0) return `${hours}h ${minutes}m`
      return `${minutes}m`
    })

    async function loadSandbox(isPoll = false) {
      if (!auth.state.token) {
        error.value = 'No autenticado'
        loading.value = false
        return
      }

      try {
        if (!isPoll) loading.value = true
        sandbox.value = await getSandbox(props.conferenceId, auth.state.token)
        if (sandbox.value?.status === 'PENDING') {
          schedulePoll()
        } else {
          stopPolling()
        }
      } catch (e: any) {
        if (e.message?.includes('404')) {
          sandbox.value = null
        } else {
          error.value = e.message || 'Error al cargar el sandbox'
        }
        stopPolling()
      } finally {
        loading.value = false
      }
    }

    function schedulePoll() {
      if (pollDeadline === 0) pollDeadline = Date.now() + POLL_TIMEOUT_MS
      if (Date.now() >= pollDeadline) {
        stopPolling()
        error.value = 'El sandbox está tardando más de lo esperado. Intenta recargar la página.'
        return
      }
      stopPolling()
      pollTimer = setTimeout(() => loadSandbox(true), POLL_INTERVAL_MS)
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

    function copyGatewayUrl() {
      if (!sandbox.value?.gatewayUrl) return
      navigator.clipboard.writeText(sandbox.value.gatewayUrl)
      urlCopied.value = true
      setTimeout(() => { urlCopied.value = false }, 2000)
    }

    onMounted(() => {
      loadSandbox()
    })

    onBeforeUnmount(() => {
      stopPolling()
    })

    return {
      sandbox, loading, error, downloadingWorkspace, urlCopied,
      formattedExpiry, downloadWorkspace, copyGatewayUrl
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
  background: white;
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
  color: #1e1b4b;
}

.subtitle {
  margin: 0;
  font-size: 0.95rem;
  color: #6b7280;
}

.ide-status {
  margin-top: 24px;
}

.loading-spinner {
  text-align: center;
  padding: 40px;
  color: #6b7280;
  font-size: 1.1rem;
}

.error-message {
  background: #fee2e2;
  color: #991b1b;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #dc2626;
  margin-bottom: 16px;
}

.sandbox-info {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
}

.info-row {
  display: flex;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  font-weight: 600;
  color: #374151;
  min-width: 140px;
}

.value {
  color: #6b7280;
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 0.9rem;
}

.ide-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.btn-primary, .btn-secondary, .btn-tertiary {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-primary {
  background: #4f46e5;
  color: white;
  text-decoration: none;
}

.btn-primary:hover {
  background: #4338ca;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.btn-secondary {
  background: #f3f4f6;
  color: #1f2937;
  border: 1px solid #e5e7eb;
}

.btn-secondary:hover:not(:disabled) {
  background: #e5e7eb;
  border-color: #d1d5db;
}

.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-tertiary {
  background: white;
  color: #4f46e5;
  border: 1.5px solid #c7d2fe;
}

.btn-tertiary:hover {
  background: #eef2ff;
  border-color: #a5b4fc;
}

.no-sandbox {
  text-align: center;
  padding: 40px 24px;
  color: #6b7280;
}

.no-sandbox p {
  margin: 12px 0;
  font-size: 1rem;
}

.hint {
  font-size: 0.9rem;
  color: #9ca3af;
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

  .btn-primary, .btn-secondary, .btn-tertiary {
    width: 100%;
    justify-content: center;
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
