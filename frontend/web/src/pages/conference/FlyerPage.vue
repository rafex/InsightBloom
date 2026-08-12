<template lang="pug">
.flyer-page
  img.flyer-image(v-if="flyerBase64" :src="flyerBase64" :alt="eventName")
  .flyer-fallback(v-else)
    img.flyer-fallback-logo(src="@/assets/logo.svg" alt="InsightBloom")
    h2 {{ eventName || 'Evento' }}
    .flyer-fallback-meta(v-if="venue || formattedDateTime")
      p.flyer-fallback-meta-item(v-if="venue")
        UiIcon(name="pin" size="16")
        span {{ venue }}
      p.flyer-fallback-meta-item(v-if="formattedDateTime")
        UiIcon(name="calendar" size="16")
        span {{ formattedDateTime }}
    ConferenceMap.flyer-fallback-map(v-if="hasCoordinates" :latitude="latitude" :longitude="longitude" :label="venue || eventName")
    p.flyer-fallback-description(v-if="eventDescription") {{ eventDescription }}
    EmptyState(v-else message="El organizador todavía no cargó una descripción para este evento.")
</template>

<script lang="ts">
import { computed } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import UiIcon from '@/components/ui/UiIcon.vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'

// Pestaña siempre visible (junto con "Mi boleto"), sin candado de moderador -- ver
// ConferencePage.vue: mientras el resto de las herramientas está bloqueado, esto es lo único
// que un asistente nuevo ve además de su boleto. Sin flyer cargado, se arma una vista de
// respaldo con los datos que el organizador sí completó (logo + nombre + sede + mapa + fecha)
// en vez de dejar la pantalla casi vacía.
export default {
  name: 'FlyerPage',
  components: { EmptyState, UiIcon, ConferenceMap },
  props: {
    eventName: { type: String, default: '' },
    eventDescription: { type: String, default: null },
    flyerBase64: { type: String, default: null },
    venue: { type: String, default: null },
    latitude: { type: Number, default: null },
    longitude: { type: Number, default: null },
    eventDate: { type: String, default: null },
    startTime: { type: String, default: null },
    endTime: { type: String, default: null }
  },
  setup(props: { venue: string | null, latitude: number | null, longitude: number | null,
                 eventDate: string | null, startTime: string | null, endTime: string | null }) {
    const hasCoordinates = computed(() => props.latitude != null && props.longitude != null)

    const formattedDateTime = computed(() => {
      if (!props.eventDate) return ''
      const datePart = new Date(`${props.eventDate}T00:00:00`)
          .toLocaleDateString('es-MX', { day: '2-digit', month: 'long', year: 'numeric' })
      if (!props.startTime) return datePart
      const timePart = props.endTime ? `${props.startTime}–${props.endTime}` : props.startTime
      return `${datePart} · ${timePart}`
    })

    return { hasCoordinates, formattedDateTime }
  }
}
</script>

<style scoped>
.flyer-page { max-width: 720px; margin: 0 auto; padding: 24px; text-align: center; }
.flyer-image { max-width: 100%; border-radius: var(--radius-lg); box-shadow: var(--shadow-card); }
.flyer-fallback { padding: 48px 24px; }
.flyer-fallback-logo { width: 64px; height: 64px; margin-bottom: 16px; }
.flyer-fallback h2 { margin: 0 0 12px; color: var(--color-heading); }
.flyer-fallback-meta { display: flex; flex-direction: column; gap: 4px; align-items: center; margin-bottom: 20px; }
.flyer-fallback-meta-item { display: flex; align-items: center; gap: 6px; margin: 0; color: var(--color-text-secondary); }
.flyer-fallback-map { margin-bottom: 20px; text-align: left; }
.flyer-fallback-description { color: var(--color-text-secondary); line-height: 1.5; }
</style>
