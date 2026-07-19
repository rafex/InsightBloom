<template lang="pug">
.checkin-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  h2 Check-in

  .scanner-wrap
    video(ref="videoEl")
  p.scan-hint Apunta la cámara al código QR del boleto del asistente.

  p.scan-result(v-if="lastResult" :class="lastResult.ok ? 'ok' : 'error'") {{ lastResult.message }}

  .recent-list(v-if="recent.length")
    h3 Últimos check-ins
    .recent-item(v-for="r in recent" :key="r.uuid") {{ r.ticketCode }} — {{ r.status }}
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import QrScanner from 'qr-scanner'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import { checkInTicket, getConference } from '@/services/api/usersApi'
import type { Reservation } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'CheckInScannerPage',
  components: { DashboardBreadcrumb },
  props: { conferenceId: { type: String, default: '' } },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const videoEl = ref<HTMLVideoElement | null>(null)
    const lastResult = ref<{ ok: boolean, message: string } | null>(null)
    const recent = ref<Reservation[]>([])
    const conferenceName = ref('')
    let scanner: QrScanner | null = null
    let processing = false

    async function onDecoded(ticketCode: string) {
      if (processing || !props.conferenceId) return
      processing = true
      try {
        const reservation = await checkInTicket(props.conferenceId, ticketCode, auth.state.token as string)
        lastResult.value = { ok: true, message: '✅ Ingreso registrado' }
        recent.value = [reservation, ...recent.value].slice(0, 10)
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        lastResult.value = {
          ok: false,
          message: code === 'already_checked_in' ? '⚠️ Este boleto ya fue registrado' : '❌ Boleto no válido para esta conferencia'
        }
      } finally {
        setTimeout(() => { processing = false }, 1500)
      }
    }

    onMounted(() => {
      if (!videoEl.value) return
      scanner = new QrScanner(videoEl.value, (result) => onDecoded(result.data), {
        highlightScanRegion: true,
        highlightCodeOutline: true
      })
      scanner.start().catch(() => {
        lastResult.value = { ok: false, message: 'No se pudo acceder a la cámara.' }
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

    return { videoEl, lastResult, recent, breadcrumbItems }
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
.scan-result { text-align: center; font-weight: 600; margin-top: 16px; padding: 10px; border-radius: 8px; }
.scan-result.ok { background: #dcfce7; color: #166534; }
.scan-result.error { background: #fee2e2; color: #991b1b; }
.recent-list { margin-top: 24px; }
.recent-list h3 { color: #374151; font-size: 0.9rem; margin-bottom: 8px; }
.recent-item { font-family: monospace; font-size: 0.8rem; color: #6b7280; padding: 4px 0; border-bottom: 1px solid #f3f4f6; }
</style>
