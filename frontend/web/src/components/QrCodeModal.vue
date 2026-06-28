<template lang="pug">
.qr-overlay(@click.self="$emit('close')")
  .qr-dialog
    h3 Escanea para entrar
    canvas(ref="qrCanvas")
    p.qr-url {{ publicUrl }}
    button.btn-secondary(@click="$emit('close')") Cerrar
</template>

<script>
import { ref, onMounted } from 'vue'
import QRCode from 'qrcode'

export default {
  name: 'QrCodeModal',
  props: {
    friendlyId: { type: String, default: '' },
    url: { type: String, default: '' }
  },
  emits: ['close'],
  setup(props) {
    const qrCanvas = ref(null)
    const publicUrl = ref(props.url || `${window.location.origin}/c/${props.friendlyId}/doubts`)

    onMounted(async () => {
      try {
        await QRCode.toCanvas(qrCanvas.value, publicUrl.value, { width: 220 })
      } catch (e) { /* sin QR si falla */ }
    })

    return { qrCanvas, publicUrl }
  }
}
</script>

<style scoped>
.qr-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 100;
}
.qr-dialog {
  background: #fff; border-radius: 16px; padding: 28px 32px; text-align: center;
  box-shadow: 0 8px 40px rgba(0,0,0,0.2);
}
.qr-dialog h3 { margin: 0 0 16px; color: #1e1b4b; }
.qr-url { color: #6b7280; font-size: 0.82rem; margin: 12px 0 16px; word-break: break-all; }
.btn-secondary {
  padding: 8px 16px; border-radius: 8px; background: #eef2ff; color: #4f46e5; border: 2px solid #c7d2fe;
  font-weight: 600; font-size: 0.88rem; cursor: pointer;
}
</style>
