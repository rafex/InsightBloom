<template lang="pug">
.checkin-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  h2 Check-in

  .scanner-wrap
    video(ref="videoEl")
  p.scan-hint Apunta la cámara al código QR del boleto del asistente.
  p.scanner-status(:class="{ ready: scannerReady, error: scannerError }") {{ scannerStatus }}

  .manual-checkin
    label(for="manual-ticket-code") Si la cámara no puede leerlo, captura el UUID del boleto
    .manual-checkin-row
      input#manual-ticket-code(v-model="manualCode" type="text" autocomplete="off" spellcheck="false" placeholder="UUID del boleto" @keyup.enter="submitManualCode")
      button.btn-primary(type="button" :disabled="processing || !manualCode.trim()" @click="submitManualCode") Registrar

  p.scan-result(v-if="lastResult" :class="lastResult.ok ? 'ok' : 'error'") {{ lastResult.message }}

  .recent-list(v-if="recent.length")
    h3 Últimos check-ins
    .recent-item(v-for="r in recent" :key="r.uuid") {{ r.ticketCode }} — {{ r.status }}
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import QrScanner from 'qr-scanner'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import { checkInIssuedTicket, checkInTicket, getConference } from '@/services/api/usersApi'
import type { Ticket, Reservation } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'CheckInScannerPage',
  components: { DashboardBreadcrumb },
  props: { conferenceId: { type: String, default: '' } },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const videoEl = ref<HTMLVideoElement | null>(null)
    const lastResult = ref<{ ok: boolean, message: string } | null>(null)
    const recent = ref<Array<Ticket | Reservation>>([])
    const conferenceName = ref('')
    const manualCode = ref('')
    const scannerReady = ref(false)
    const scannerError = ref(false)
    let scanner: QrScanner | null = null
    let processing = false

    function extractTicketCode(rawValue: string): string {
      const value = rawValue.trim()
      if (!value) return value
      try {
        const url = new URL(value, window.location.origin)
        return url.searchParams.get('ticket') || url.searchParams.get('t') || value
      } catch (_error) {
        return value
      }
    }

    function handleScanResult(result: unknown) {
      // qr-scanner returns a string by default; with detailed results enabled it
      // returns { data, ... }. Supporting both keeps the scanner compatible with
      // the installed library and with older cached bundles.
      const rawValue = typeof result === 'string'
        ? result
        : (result as { data?: unknown } | null)?.data
      if (typeof rawValue === 'string' && rawValue.trim()) void onDecoded(rawValue)
    }

    function errorMessage(error: any): string {
      const code = error.response?.data?.error?.code
      switch (code) {
        case 'already_checked_in': return '⚠️ Este boleto ya fue registrado'
        case 'ticket_not_claimed': return '⚠️ El boleto aún no ha sido reclamado por un asistente'
        case 'ticket_expired': return '❌ El boleto ya expiró'
        case 'ticket_revoked': return '❌ El boleto fue revocado'
        case 'forbidden': return '❌ No tienes permiso para hacer check-in en este evento'
        case 'ticket_not_found': return '❌ El boleto no pertenece a esta conferencia'
        case 'ticket_invalid_format': return '❌ El UUID del boleto no tiene un formato válido'
        default: return '❌ No se pudo registrar el boleto'
      }
    }

    async function onDecoded(rawValue: string) {
      if (processing || !props.conferenceId) return
      processing = true
      const ticketCode = extractTicketCode(rawValue)
      try {
        let reservation: Ticket | Reservation
        try {
          reservation = await checkInIssuedTicket(props.conferenceId, ticketCode, auth.state.token as string)
        } catch (ticketError: any) {
          const code = ticketError.response?.data?.error?.code
          const status = ticketError.response?.status
          // Compatibilidad con boletos de reservas emitidos antes de la migración,
          // pero no ocultes errores reales del boleto actual (no reclamado, expirado,
          // revocado o ya registrado).
          if (status !== 404 && code !== 'ticket_not_found') throw ticketError
          reservation = await checkInTicket(props.conferenceId, ticketCode, auth.state.token as string)
        }
        lastResult.value = { ok: true, message: '✅ Ingreso registrado' }
        recent.value = [reservation, ...recent.value].slice(0, 10)
      } catch (e: any) {
        lastResult.value = { ok: false, message: errorMessage(e) }
      } finally {
        manualCode.value = ''
        setTimeout(() => { processing = false }, 1500)
      }
    }

    async function submitManualCode() {
      if (!manualCode.value.trim()) return
      await onDecoded(manualCode.value)
    }

    onMounted(() => {
      if (!videoEl.value) return
      scanner = new QrScanner(videoEl.value, handleScanResult, {
        highlightScanRegion: true,
        highlightCodeOutline: true,
        returnDetailedScanResult: true
      })
      scanner.start().then(() => {
        scannerReady.value = true
        scannerError.value = false
      }).catch(() => {
        scannerReady.value = false
        scannerError.value = true
        lastResult.value = { ok: false, message: 'No se pudo acceder a la cámara. Usa el UUID manual.' }
      })
    })

    onBeforeUnmount(() => {
      if (scanner) { scanner.stop(); scanner.destroy(); scanner = null }
    })

    onMounted(async () => {
      if (!props.conferenceId) return
      try {
        const conf = await getConference(props.conferenceId, auth.state.token as string)
        conferenceName.value = conf?.name || ''
      } catch (e: any) { conferenceName.value = '' }
    })

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Check-in' }
    ])

    const scannerStatus = computed(() => {
      if (scannerReady.value) return 'Cámara activa. Cuando se detecte un QR se registrará automáticamente.'
      if (scannerError.value) return 'Cámara no disponible. Puedes usar el UUID manual.'
      return 'Iniciando cámara...'
    })

    return {
      videoEl, lastResult, recent, breadcrumbItems, manualCode, submitManualCode,
      scannerReady, scannerError, scannerStatus
    }
  }
}
</script>

<style scoped>
.checkin-page { padding: 24px; max-width: 480px; margin: 0 auto; }
h2 { color: #1e1b4b; margin-bottom: 16px; }
.scanner-wrap {
  border-radius: 12px; overflow: hidden; background: #000; aspect-ratio: 1; max-width: 360px; margin: 0 auto;
}
.scanner-wrap video { width: 100%; height: 100%; object-fit: cover; }
.scan-hint { text-align: center; color: #6b7280; font-size: 0.85rem; margin-top: 10px; }
.scanner-status { text-align: center; color: #6b7280; font-size: 0.82rem; margin: 8px 0 18px; }
.scanner-status.ready { color: #166534; }
.scanner-status.error { color: #991b1b; }
.manual-checkin { margin-top: 18px; padding: 14px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; }
.manual-checkin label { display: block; color: #374151; font-size: 0.82rem; font-weight: 600; margin-bottom: 8px; }
.manual-checkin-row { display: flex; gap: 8px; }
.manual-checkin input { min-width: 0; flex: 1; border: 1px solid #d1d5db; border-radius: 7px; padding: 9px 10px; font: 0.8rem monospace; }
.manual-checkin button { border: 0; border-radius: 7px; padding: 9px 12px; cursor: pointer; }
.manual-checkin button:disabled { opacity: 0.55; cursor: not-allowed; }
.scan-result { text-align: center; font-weight: 600; margin-top: 16px; padding: 10px; border-radius: 8px; }
.scan-result.ok { background: #dcfce7; color: #166534; }
.scan-result.error { background: #fee2e2; color: #991b1b; }
.recent-list { margin-top: 24px; }
.recent-list h3 { color: #374151; font-size: 0.9rem; margin-bottom: 8px; }
.recent-item { font-family: monospace; font-size: 0.8rem; color: #6b7280; padding: 4px 0; border-bottom: 1px solid #f3f4f6; }
</style>
