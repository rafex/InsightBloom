<template lang="pug">
teleport(to="body")
  .toast-stack(aria-live="polite")
    transition-group(name="toast")
      .toast(v-for="t in items" :key="t.id" :class="`k-${t.kind}`" role="status")
        span.toast-msg {{ t.message }}
        button.toast-close(@click="dismiss(t.id)" aria-label="Cerrar notificación") ×
</template>

<script lang="ts">
// Se monta UNA vez en App.vue; las páginas disparan con useToasts().success()/error().
import { useToasts } from './toast'

export default {
  name: 'AppToast',
  setup() {
    const { items, dismiss } = useToasts()
    return { items, dismiss }
  }
}
</script>

<style scoped>
.toast-stack {
  position: fixed;
  bottom: var(--space-4);
  right: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  z-index: 1100;
  max-width: min(380px, calc(100vw - 32px));
}
.toast {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-modal);
  font-size: 0.92rem;
  color: #fff;
}
.k-success { background: var(--color-success); }
.k-error { background: var(--color-danger); }
.k-info { background: var(--color-primary); }
.toast-msg { flex: 1; }
.toast-close {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  padding: 0;
  opacity: 0.8;
}
.toast-close:hover { opacity: 1; }
.toast-enter-active, .toast-leave-active { transition: opacity 0.2s, transform 0.2s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }
</style>
