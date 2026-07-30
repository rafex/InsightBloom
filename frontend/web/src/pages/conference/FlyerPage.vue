<template lang="pug">
.flyer-page
  img.flyer-image(v-if="flyerBase64" :src="flyerBase64" :alt="eventName")
  .flyer-fallback(v-else)
    h2 {{ eventName || 'Evento' }}
    p(v-if="eventDescription") {{ eventDescription }}
    EmptyState(v-else message="El organizador todavía no cargó una descripción para este evento.")
</template>

<script lang="ts">
import EmptyState from '@/components/ui/EmptyState.vue'

// Pestaña siempre visible (junto con "Mi boleto"), sin candado de moderador -- ver
// ConferencePage.vue: mientras el resto de las herramientas está bloqueado, esto es lo único
// que un asistente nuevo ve además de su boleto.
export default {
  name: 'FlyerPage',
  components: { EmptyState },
  props: {
    eventName: { type: String, default: '' },
    eventDescription: { type: String, default: null },
    flyerBase64: { type: String, default: null }
  }
}
</script>

<style scoped>
.flyer-page { max-width: 720px; margin: 0 auto; padding: 24px; text-align: center; }
.flyer-image { max-width: 100%; border-radius: var(--radius-lg); box-shadow: var(--shadow-card); }
.flyer-fallback { padding: 48px 24px; }
.flyer-fallback h2 { margin: 0 0 12px; color: var(--color-heading); }
.flyer-fallback p { color: var(--color-text-secondary); line-height: 1.5; }
</style>
