<template lang="pug">
.not-found-page
  AppHeader
  .nf-body#main-content(tabindex="-1")
    h1 404
    h2 Esta página no existe
    p La dirección puede estar mal escrita o el contenido ya no está disponible.
    .nf-actions
      BaseLink(to="/") Ir a la cartelera de eventos
      BaseLink(to="/dashboard" variant="secondary") Ir al panel
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import BaseLink from '@/components/ui/BaseLink.vue'

export default {
  name: 'NotFoundPage',
  components: { AppHeader, BaseLink }
}
</script>

<style scoped>
.not-found-page { min-height: 100vh; background: var(--color-bg); }
.nf-body { max-width: 480px; margin: 0 auto; padding: 80px 24px; text-align: center; }
h1 { font-size: 4rem; margin: 0; color: var(--color-primary); }
h2 { margin: 0 0 var(--space-3); color: var(--color-text); }
p { color: var(--color-text-muted); margin-bottom: var(--space-6); }
.nf-actions { display: flex; gap: var(--space-3); justify-content: center; flex-wrap: wrap; }
</style>
