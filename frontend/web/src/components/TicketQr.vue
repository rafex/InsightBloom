<template lang="pug">
.ticket-qr
  canvas(ref="qrCanvas")
  p.ticket-code(v-if="showCode") {{ ticketCode }}
</template>

<script lang="ts">
import { ref, onMounted, watch } from 'vue'
import QRCode from 'qrcode'

export default {
  name: 'TicketQr',
  props: {
    ticketCode: { type: String, required: true },
    // The attendee-facing QR opens the ticket page. Check-in remains protected
    // by the dashboard API and accepts the UUID extracted from that URL.
    ticketUrl: { type: String, default: '' },
    showCode: { type: Boolean, default: true }
  },
  setup(props: { ticketCode: string, ticketUrl?: string, showCode?: boolean }) {
    const qrCanvas = ref<HTMLCanvasElement | null>(null)

    async function render() {
      if (!qrCanvas.value) return
      try {
        const value = props.ticketUrl || props.ticketCode
        await QRCode.toCanvas(qrCanvas.value, value, {
          // Keep enough physical modules for camera scanners when the ticket is
          // shown on another screen. The check-in page also accepts the UUID.
          // Keep the QR readable when it is shown on a phone and scanned from
          // another screen. The ticket UUID is still printed next to the QR as
          // the manual fallback, so lower redundancy gives us a substantially
          // less dense code without reducing the recovery path.
          width: 400,
          margin: 6,
          errorCorrectionLevel: 'L'
        })
      } catch (e: any) { /* sin QR si falla */ }
    }

    onMounted(render)
    watch(() => [props.ticketCode, props.ticketUrl], render)

    return { qrCanvas }
  }
}
</script>

<style scoped>
.ticket-qr { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.ticket-code { font-family: monospace; font-size: 0.75rem; color: var(--color-text-muted); word-break: break-all; text-align: center; }
</style>
