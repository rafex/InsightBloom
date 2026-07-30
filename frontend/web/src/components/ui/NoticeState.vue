<template lang="pug">
.notice-state(:class="`tone-${tone}`" :role="tone === 'danger' ? 'alert' : 'status'" aria-live="polite")
  h2(v-if="title") {{ title }}
  p {{ message }}
  slot
</template>

<script lang="ts">
// Aviso contextual para una herramienta no disponible o con acceso restringido.
// Centraliza el patrón que antes se repetía en superficies embebidas.
export default {
  name: 'NoticeState',
  props: {
    title: { type: String, default: '' },
    message: { type: String, required: true },
    tone: { type: String, default: 'warning' } // warning | danger | info
  }
}
</script>

<style scoped>
.notice-state {
  max-width: 420px;
  margin: var(--space-10) auto;
  padding: var(--space-6);
  border: 1px solid currentColor;
  border-radius: var(--radius-lg);
  text-align: center;
}
.notice-state h2 { margin: 0 0 var(--space-2); font-size: 1.05rem; }
.notice-state p { margin: 0; font-size: 0.85rem; line-height: 1.5; }
.tone-warning { color: var(--color-warning); background: var(--color-warning-soft); }
.tone-danger { color: var(--color-danger); background: var(--color-danger-soft); }
.tone-info { color: var(--color-info); background: var(--color-primary-soft); }
</style>
