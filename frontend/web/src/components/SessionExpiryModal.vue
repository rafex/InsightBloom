<template lang="pug">
.session-modal-overlay(v-if="show")
  .session-modal
    h3 Tu sesión está por expirar
    p Se cerrará automáticamente en #[strong {{ seconds }}s] por inactividad.
    button.btn-primary(type="button" @click="$emit('keep-connected')") Seguir conectado
</template>

<script lang="ts">
export default {
  name: 'SessionExpiryModal',
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
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.session-modal {
  background: #fff;
  border-radius: 16px;
  padding: 28px 32px;
  max-width: 360px;
  width: 90%;
  text-align: center;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.25);
}
.session-modal h3 { margin: 0 0 12px; color: #1e1b4b; }
.session-modal p { margin: 0 0 20px; color: #374151; font-size: 0.95rem; }
.session-modal strong { color: #dc2626; }
.btn-primary {
  padding: 10px 24px;
  background: #4f46e5;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  font-size: 0.95rem;
  cursor: pointer;
}
.btn-primary:hover { background: #4338ca; }
</style>
