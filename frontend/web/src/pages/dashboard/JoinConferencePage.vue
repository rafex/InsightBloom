<template lang="pug">
.join-page
  h1 Unirse a un evento
  p.hint Elige cómo quieres acceder al evento.
  .access-methods(role="tablist" aria-label="Forma de acceso")
    button.method-button(type="button" :class="{ active: mode === 'friendly' }" @click="selectMode('friendly')") Nombre amigable
    button.method-button(type="button" :class="{ active: mode === 'qr' }" @click="selectMode('qr')") QR del boleto
    button.method-button(type="button" :class="{ active: mode === 'uuid' }" @click="selectMode('uuid')") UUID del boleto
  .access-card
    label(for="event-access-input") {{ mode === 'friendly' ? 'Nombre amigable del evento' : mode === 'qr' ? 'Contenido del QR del boleto' : 'UUID v4 del boleto' }}
    .form-row
      input#event-access-input(v-model="code" :placeholder="placeholder" autocomplete="off" @keyup.enter="doJoin")
      BaseButton(variant="secondary" v-if="mode === 'qr'" type="button" @click="toggleScanner") {{ scanning ? 'Cerrar cámara' : 'Escanear QR' }}
      BaseButton(type="button" :disabled="!code.trim() || loading" @click="doJoin") {{ loading ? (mode === 'friendly' ? 'Uniendo...' : 'Canjeando...') : (mode === 'friendly' ? 'Unirme' : 'Canjear boleto') }}
    .claim-scanner(v-if="scanning")
      video(ref="videoEl")
      p Apunta la cámara al QR del boleto.
  p.error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import QrScanner from 'qr-scanner'
import { claimTicketByCode, getConference, joinConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'

type AccessMode = 'friendly' | 'qr' | 'uuid'

export default {
  name: 'JoinConferencePage',
  components: { BaseButton },
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

    return { mode, code, loading, error, scanning, videoEl, placeholder, selectMode, doJoin, toggleScanner }
  }
}
</script>

<style scoped>
.join-page { max-width: 680px; }
h1 { color: var(--color-heading); margin: 0 0 8px; font-size: 1.5rem; }
.hint { color: var(--color-text-muted); font-size: 0.9rem; margin-bottom: 20px; }
.access-methods { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.method-button {
  padding: 10px 14px; border: 1px solid var(--color-primary-border); border-radius: 8px; background: var(--color-surface);
  color: var(--color-primary-dark); cursor: pointer; font-weight: 600;
}
.method-button.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-text-inverse); }
.access-card { background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 18px; }
label { display: block; color: var(--color-text-secondary); font-size: 0.9rem; font-weight: 600; margin-bottom: 8px; }
.form-row { display: flex; gap: 8px; align-items: stretch; }
input {
  flex: 1; min-width: 0; padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 1rem;
}
input:focus { outline: none; border-color: var(--color-primary); }
.claim-scanner { margin: 14px auto 0; max-width: 320px; text-align: center; }
.claim-scanner video { width: 100%; border-radius: 10px; background: var(--color-heading); }
.claim-scanner p { color: var(--color-text-muted); font-size: 0.85rem; }
.error { color: var(--color-danger); margin-top: 12px; font-size: 0.9rem; }

@media (max-width: 620px) {
  .form-row { flex-direction: column; }
}
</style>
