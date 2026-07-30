<template lang="pug">
.join-page
  h1 Unirse a un evento
  p.hint Elige cómo quieres acceder al evento.
  .access-methods(role="tablist" aria-label="Forma de acceso")
    BaseButton.method-tab(type="button" :variant="mode === 'friendly' ? 'primary' : 'secondary'" role="tab" aria-controls="event-access-panel" :aria-selected="mode === 'friendly'" @click="selectMode('friendly')") Nombre amigable
    BaseButton.method-tab(type="button" :variant="mode === 'qr' ? 'primary' : 'secondary'" role="tab" aria-controls="event-access-panel" :aria-selected="mode === 'qr'" @click="selectMode('qr')") QR del boleto
    BaseButton.method-tab(type="button" :variant="mode === 'uuid' ? 'primary' : 'secondary'" role="tab" aria-controls="event-access-panel" :aria-selected="mode === 'uuid'" @click="selectMode('uuid')") UUID del boleto
  .access-card#event-access-panel(role="tabpanel")
    FormField(:label="accessLabel")
      template(#default="{ id, describedBy }")
        .form-row
          input(:id="id" :aria-describedby="describedBy" v-model="code" :placeholder="placeholder" autocomplete="off" @keyup.enter="doJoin")
          BaseButton(variant="secondary" v-if="mode === 'qr'" type="button" @click="toggleScanner") {{ scanning ? 'Cerrar cámara' : 'Escanear QR' }}
          BaseButton(type="button" :disabled="!code.trim()" :loading="loading" @click="doJoin") {{ mode === 'friendly' ? 'Unirme' : 'Canjear boleto' }}
    .claim-scanner(v-if="scanning")
      video(ref="videoEl")
      p Apunta la cámara al QR del boleto.
  FeedbackMessage(v-if="error" :message="error" tone="error")
</template>

<script lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import QrScanner from 'qr-scanner'
import { claimTicketByCode, getConference, joinConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import FormField from '@/components/ui/FormField.vue'

type AccessMode = 'friendly' | 'qr' | 'uuid'

export default {
  name: 'JoinConferencePage',
  components: { BaseButton, FeedbackMessage, FormField },
  setup() {
    const router = useRouter()
    const auth = useAuthStore()
    const mode = ref<AccessMode>('friendly')
    const code = ref('')
    const loading = ref(false)
    const error = ref('')
    const scanning = ref(false)
    const videoEl = ref<HTMLVideoElement | null>(null)
    let scanner: QrScanner | null = null

    const placeholder = computed(() => mode.value === 'friendly'
      ? 'ej. taller-uptlax'
      : mode.value === 'qr'
        ? 'Pega el contenido del QR o escanéalo'
        : 'ej. 123e4567-e89b-42d3-a456-426614174000')
    const accessLabel = computed(() => mode.value === 'friendly'
      ? 'Nombre amigable del evento'
      : mode.value === 'qr' ? 'Contenido del QR del boleto' : 'UUID v4 del boleto')

    function stopScanner() {
      if (scanner) { scanner.stop(); scanner.destroy(); scanner = null }
      scanning.value = false
    }

    function selectMode(nextMode: AccessMode) {
      stopScanner()
      mode.value = nextMode
      code.value = ''
      error.value = ''
    }

    async function doJoin() {
      if (!code.value.trim() || !auth.state.token) return
      error.value = ''
      loading.value = true
      try {
        if (mode.value === 'friendly') {
          const conference = await joinConference(code.value.trim(), auth.state.token)
          router.push(`/c/${conference.friendlyId}/doubts`)
        } else {
          const ticket = await claimTicketByCode(code.value.trim(), auth.state.token)
          const conference = await getConference(ticket.conferenceUuid, auth.state.token)
          router.push(`/c/${conference.friendlyId}/doubts`)
        }
      } catch (e: any) {
        const status = e.response?.data?.error?.code
        if (status === 'ticket_required') {
          error.value = 'Este evento requiere un boleto. Usa el QR o UUID del boleto para canjearlo.'
        } else if (status === 'ticket_revoked') {
          error.value = 'Este boleto fue revocado. Solicita otro boleto al moderador del evento.'
        } else if (e.response?.status === 404) {
          error.value = mode.value === 'friendly'
            ? 'Este evento no se encuentra disponible o el nombre amigable es incorrecto.'
            : 'El QR o UUID no corresponde a un boleto válido.'
        } else {
          error.value = mode.value === 'friendly'
            ? 'No se pudo unir al evento. Intenta de nuevo.'
            : 'No se pudo canjear el boleto. Intenta de nuevo.'
        }
      } finally {
        loading.value = false
      }
    }

    function toggleScanner() {
      if (scanning.value) { stopScanner(); return }
      scanning.value = true
      setTimeout(() => {
        if (!videoEl.value) return
        scanner = new QrScanner(videoEl.value, (result) => {
          const rawValue = typeof result === 'string'
            ? result
            : (result as { data?: unknown } | null)?.data
          if (typeof rawValue !== 'string' || !rawValue.trim()) return
          code.value = rawValue
          stopScanner()
          doJoin()
        }, {
          highlightScanRegion: true,
          highlightCodeOutline: true,
          returnDetailedScanResult: true
        })
        scanner.start().catch(() => {
          error.value = 'No se pudo acceder a la cámara.'
          stopScanner()
        })
      }, 0)
    }

    onBeforeUnmount(stopScanner)

    return { mode, code, loading, error, scanning, videoEl, placeholder, accessLabel, selectMode, doJoin, toggleScanner }
  }
}
</script>

<style scoped>
.join-page { max-width: 680px; }
h1 { color: var(--color-heading); margin: 0 0 8px; font-size: 1.5rem; }
.hint { color: var(--color-text-muted); font-size: 0.9rem; margin-bottom: 20px; }
.access-methods { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.method-tab { flex: 1 1 150px; }
.access-card { background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 18px; }
.form-row { display: flex; gap: 8px; align-items: stretch; }
input {
  flex: 1; min-width: 0; font-size: 1rem;
}
.claim-scanner { margin: 14px auto 0; max-width: 320px; text-align: center; }
.claim-scanner video { width: 100%; border-radius: 10px; background: var(--color-heading); }
.claim-scanner p { color: var(--color-text-muted); font-size: 0.85rem; }

@media (max-width: 620px) {
  .form-row { flex-direction: column; }
}
</style>
