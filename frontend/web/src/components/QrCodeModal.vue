<template lang="pug">
.qr-overlay(@click.self="$emit('close')")
  .qr-dialog(role="dialog" aria-modal="true" aria-label="Código QR para entrar al evento")
    h3 Escanea para entrar
    canvas(ref="qrCanvas")
    p.qr-url {{ publicUrl }}
    BaseButton(variant="secondary" size="sm" @click="$emit('close')") Cerrar
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import QRCode from 'qrcode'
import BaseButton from '@/components/ui/BaseButton.vue'

export default {
  name: 'QrCodeModal',
  components: { BaseButton },
  props: {
    friendlyId: { type: String, default: '' },
    url: { type: String, default: '' }
  },
  emits: ['close'],
  setup(props, { emit }) {
    const qrCanvas = ref(null)
    const publicUrl = ref(props.url || `${window.location.origin}/c/${props.friendlyId}/doubts`)

    const onKeydown = (e: KeyboardEvent) => { if (e.key === 'Escape') emit('close') }

    onMounted(async () => {
      document.addEventListener('keydown', onKeydown)
      try {
        await QRCode.toCanvas(qrCanvas.value, publicUrl.value, { width: 220 })
      } catch (e: any) { /* sin QR si falla */ }
    })
    onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

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
  background: var(--color-surface); border-radius: 16px; padding: 28px 32px; text-align: center;
  box-shadow: 0 8px 40px rgba(0,0,0,0.2);
}
.qr-dialog h3 { margin: 0 0 16px; color: var(--color-heading); }
.qr-url { color: var(--color-text-muted); font-size: 0.82rem; margin: 12px 0 16px; word-break: break-all; }
</style>
