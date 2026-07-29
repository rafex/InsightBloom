<template lang="pug">
.session-modal-overlay(v-if="show")
  .session-modal(role="alertdialog" aria-modal="true" aria-label="Aviso de expiración de sesión")
    h3 Tu sesión está por expirar
    p Se cerrará automáticamente en #[strong {{ seconds }}s] por inactividad.
    BaseButton(type="button" @click="$emit('keep-connected')") Seguir conectado
</template>

<script lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'

export default {
  name: 'SessionExpiryModal',
  components: { BaseButton },
  props: {
    show: { type: Boolean, default: false },
    seconds: { type: Number, default: 0 }
  },
  emits: ['keep-connected']
}
</script>

<style scoped>
.session-modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.session-modal {
  background: var(--color-surface);
  border-radius: 16px;
  padding: 28px 32px;
  max-width: 360px;
  width: 90%;
  text-align: center;
  box-shadow: var(--shadow-overlay);
}
.session-modal h3 { margin: 0 0 12px; color: var(--color-heading); }
.session-modal p { margin: 0 0 20px; color: var(--color-text-secondary); font-size: 0.95rem; }
.session-modal strong { color: var(--color-danger); }
</style>
