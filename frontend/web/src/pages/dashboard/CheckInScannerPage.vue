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
      BaseButton(type="button" :disabled="processing || !manualCode.trim()" :loading="processing" @click="submitManualCode") Registrar

  .image-checkin
    p Si la cámara en vivo no lo detecta, toma una foto o selecciona una captura del QR.
    BaseButton(variant="secondary" size="sm" type="button" :disabled="!scannerReady || imageProcessing || processing" :loading="imageProcessing" @click="takePhotoAndScan")
      span(v-if="imageProcessing") Procesando foto...
      span(v-else) 📸 Tomar foto y leer QR
    span.image-or O selecciona una imagen:
    label.link-btn.link-btn-secondary.link-btn-sm.image-picker(:for="'qr-image-input'")
      | 📷 Leer QR desde imagen
    input#qr-image-input(type="file" accept="image/*" capture="environment" @change="scanQrImage")

  p.scan-result(v-if="lastResult" :class="lastResult.ok ? 'ok' : 'error'") {{ lastResult.message }}

  .recent-list(v-if="recent.length")
    h3 Últimos check-ins
    .recent-item(v-for="r in recent" :key="r.uuid") {{ r.ticketCode }} — {{ formatStatusLabel(r.status) }}
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import QrScanner from 'qr-scanner'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { checkInIssuedTicket, checkInTicket, getConference } from '@/services/api/usersApi'
import type { Ticket, Reservation } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { formatStatusLabel } from '@/utils/status'

export default {
  name: 'CheckInScannerPage',
  components: { DashboardBreadcrumb, BaseButton },
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
    const imageProcessing = ref(false)
    let scanner: QrScanner | null = null
    const processing = ref(false)

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

    function scanResultValue(result: unknown): string {
      // qr-scanner returns a string by default; with detailed results enabled it
      // returns { data, ... }. Supporting both keeps the scanner compatible with
      // the installed library and with older cached bundles.
      const rawValue = typeof result === 'string'
        ? result
        : (result as { data?: unknown, rawValue?: unknown } | null)?.data
          || (result as { data?: unknown, rawValue?: unknown } | null)?.rawValue
      return typeof rawValue === 'string' ? rawValue : ''
    }

    function handleScanResult(result: unknown) {
      const rawValue = scanResultValue(result)
      if (rawValue.trim()) void onDecoded(rawValue)
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
      if (processing.value || !props.conferenceId) return
      processing.value = true
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
        setTimeout(() => { processing.value = false }, 1500)
      }
    }

    async function submitManualCode() {
      if (!manualCode.value.trim()) return
      await onDecoded(manualCode.value)
    }

    async function scanQrImage(event: Event) {
      const input = event.target as HTMLInputElement
      const file = input.files?.[0]
      // Reset the input so the same screenshot can be selected again after a
      // failed attempt or after correcting the lighting/focus.
      input.value = ''
      if (!file || imageProcessing.value || processing.value) return

      imageProcessing.value = true
      lastResult.value = null
      try {
        const result = await QrScanner.scanImage(file, {
          returnDetailedScanResult: true,
          alsoTryWithoutScanRegion: true
        })
        const rawValue = scanResultValue(result)
        if (!rawValue.trim()) throw new Error('QR vacío')
        await onDecoded(rawValue)
      } catch (_error) {
        lastResult.value = {
          ok: false,
          message: '❌ No se encontró un QR legible en la imagen. Usa una foto más nítida o el UUID manual.'
        }
      } finally {
        imageProcessing.value = false
      }
    }

    async function takePhotoAndScan() {
      const video = videoEl.value
      if (!video || !video.videoWidth || !video.videoHeight || imageProcessing.value || processing.value) return

      imageProcessing.value = true
      lastResult.value = null
      try {
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        const context = canvas.getContext('2d')
        if (!context) throw new Error('No se pudo capturar la imagen')
        context.drawImage(video, 0, 0, canvas.width, canvas.height)

        const result = await QrScanner.scanImage(canvas, {
          returnDetailedScanResult: true,
          alsoTryWithoutScanRegion: true
        })
        const rawValue = scanResultValue(result)
        if (!rawValue.trim()) throw new Error('QR vacío')
        await onDecoded(rawValue)
      } catch (_error) {
        lastResult.value = {
          ok: false,
          message: '❌ La foto no contiene un QR legible. Acerca el boleto, mejora el enfoque y vuelve a intentarlo.'
        }
      } finally {
        imageProcessing.value = false
      }
    }

    onMounted(() => {
      if (!videoEl.value) return
      scanner = new QrScanner(videoEl.value, handleScanResult, {
        highlightScanRegion: true,
        highlightCodeOutline: true,
        preferredCamera: 'environment',
        maxScansPerSecond: 10,
        returnDetailedScanResult: true
      })
      // QR tickets are normally black on white, but the scanner can also see
      // tickets rendered by displays with inverted/processed contrast.
      scanner.setInversionMode('both')
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
      videoEl, lastResult, recent, breadcrumbItems, manualCode, submitManualCode, formatStatusLabel,
      scannerReady, scannerError, scannerStatus, scanQrImage, takePhotoAndScan, imageProcessing, processing
    }
  }
}
</script>

<style scoped>
.checkin-page { padding: 24px; max-width: 480px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 16px; }
.scanner-wrap {
  border-radius: 12px; overflow: hidden; background: #000; aspect-ratio: 1; max-width: 360px; margin: 0 auto;
}
.scanner-wrap video { width: 100%; height: 100%; object-fit: cover; }
.scan-hint { text-align: center; color: var(--color-text-muted); font-size: 0.85rem; margin-top: 10px; }
.scanner-status { text-align: center; color: var(--color-text-muted); font-size: 0.82rem; margin: 8px 0 18px; }
.scanner-status.ready { color: var(--color-success); }
.scanner-status.error { color: var(--color-danger-dark); }
.manual-checkin { margin-top: 18px; padding: 14px; border: 1px solid var(--color-border-subtle); border-radius: 10px; background: var(--color-surface); }
.manual-checkin label { display: block; color: var(--color-text-secondary); font-size: 0.82rem; font-weight: 600; margin-bottom: 8px; }
.manual-checkin-row { display: flex; gap: 8px; }
.manual-checkin input { min-width: 0; flex: 1; border: 1px solid var(--color-border); border-radius: 7px; padding: 9px 10px; font: 0.8rem monospace; }
.image-checkin { margin-top: 10px; padding: 12px 14px; border: 1px dashed var(--color-primary-border); border-radius: 10px; background: var(--color-primary-soft); text-align: center; }
.image-checkin p { margin: 0 0 9px; color: var(--color-text-secondary); font-size: 0.78rem; }
.image-picker { display: inline-flex; align-items: center; justify-content: center; min-height: 36px; padding: 0 13px; border-radius: 7px; background: var(--color-surface); color: var(--color-primary-dark); font-size: 0.8rem; font-weight: 700; cursor: pointer; }
.image-picker:disabled { cursor: not-allowed; opacity: .55; }
.image-or { display: inline-block; margin: 0 8px; color: var(--color-text-muted); font-size: 0.75rem; }
.image-checkin input[type="file"] { display: none; }
.scan-result { text-align: center; font-weight: 600; margin-top: 16px; padding: 10px; border-radius: 8px; }
.scan-result.ok { background: var(--color-success-soft); color: var(--color-success); }
.scan-result.error { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.recent-list { margin-top: 24px; }
.recent-list h3 { color: var(--color-text-secondary); font-size: 0.9rem; margin-bottom: 8px; }
.recent-item { font-family: monospace; font-size: 0.8rem; color: var(--color-text-muted); padding: 4px 0; border-bottom: 1px solid var(--color-surface-muted); }
</style>
