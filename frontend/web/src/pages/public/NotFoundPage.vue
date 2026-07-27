<template lang="pug">
.not-found-page
  AppHeader
  .nf-body
    h1 404
    h2 Esta página no existe
    p La dirección puede estar mal escrita o el contenido ya no está disponible.
    .nf-actions
      router-link.btn-primary(to="/") Ir a la cartelera de eventos
      router-link.btn-secondary(to="/dashboard") Ir al panel
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'

export default {
  name: 'NotFoundPage',
  components: { AppHeader }
}
</script>

<style scoped>
.not-found-page { min-height: 100vh; background: var(--color-bg); }
.nf-body { max-width: 480px; margin: 0 auto; padding: 80px 24px; text-align: center; }
h1 { font-size: 4rem; margin: 0; color: var(--color-primary); }
h2 { margin: 0 0 var(--space-3); color: var(--color-text); }
p { color: var(--color-text-muted); margin-bottom: var(--space-6); }
.nf-actions { display: flex; gap: var(--space-3); justify-content: center; flex-wrap: wrap; }
.btn-primary {
  padding: 10px 20px; border-radius: var(--radius-md); background: var(--color-primary);
  color: #fff; text-decoration: none; font-weight: 600;
}
.btn-primary:hover { background: var(--color-primary-dark); }
.btn-secondary {
  padding: 10px 20px; border-radius: var(--radius-md); background: var(--color-surface);
  color: var(--color-primary); border: 1px solid var(--color-primary-border);
  text-decoration: none; font-weight: 600;
}
.btn-secondary:hover { background: var(--color-primary-soft); }
</style>
