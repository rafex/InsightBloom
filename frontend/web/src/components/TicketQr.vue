<template lang="pug">
.ticket-qr
  canvas(ref="qrCanvas")
  p.ticket-code {{ ticketCode }}
</template>

<script lang="ts">
import { ref, onMounted, watch } from 'vue'
import QRCode from 'qrcode'

export default {
  name: 'TicketQr',
  props: {
    ticketCode: { type: String, required: true }
  },
  setup(props: { ticketCode: string }) {
    const qrCanvas = ref<HTMLCanvasElement | null>(null)

    async function render() {
      if (!qrCanvas.value) return
      try {
        // El QR codifica el ticketCode opaco directamente (no una URL pública) — lo
        // interpreta la propia página de check-in del organizador, ya autenticada.
        await QRCode.toCanvas(qrCanvas.value, props.ticketCode, { width: 220 })
      } catch (e: any) { /* sin QR si falla */ }
    }

    onMounted(render)
    watch(() => props.ticketCode, render)

    return { qrCanvas }
  }
}
</script>

<style scoped>
.ticket-qr { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.ticket-code { font-family: monospace; font-size: 0.75rem; color: #9ca3af; word-break: break-all; text-align: center; }
</style>
