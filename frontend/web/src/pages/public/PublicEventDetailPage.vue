<template lang="pug">
  .public-event-page(:class="`theme-${(event?.publicTheme || 'CLASSIC').toLowerCase()}`")
    AppHeader
    main.public-detail(v-if="event")
      router-link.back(to="/events") ← Volver a la cartelera
      .detail-hero(:class="{ reverse: event.scheduleLayout === 'LEFT' }")
        .detail-copy
          .badges
            span.badge {{ event.visibility === 'HYBRID' ? 'Evento híbrido' : 'Evento público' }}
            span.badge.badge-ticket(v-if="event.ticketRequired") 🎟️ Boleto requerido
          h1 {{ event.name }}
          .organizer-card
            img.organizer-avatar(v-if="event.organizerPhotoBase64" :src="event.organizerPhotoBase64" alt="")
            .organizer-copy
              span.organizer-label Organizado por
              strong.organizer {{ event.organizer }}
          p.description(v-if="event.description") {{ event.description }}
          dl.event-facts
            template(v-if="event.eventDate")
              dt Fecha
              dd {{ event.eventDate }}{{ event.startTime ? ` · ${event.startTime}` : '' }}{{ event.endTime ? ` – ${event.endTime}` : '' }}
            template(v-if="event.venue")
              dt Lugar
              dd {{ event.venue }}
            template(v-if="event.capacity != null")
              dt Aforo
              dd {{ event.remainingSeats }} disponibles de {{ event.capacity }}
            template(v-if="event.ticketRequired")
              dt Precio
              dd {{ Number(event.ticketPrice || 0) > 0 ? `${event.ticketPrice} ${event.ticketCurrency || 'MXN'}` : 'Gratis' }}
          .actions
            router-link.btn-primary(v-if="event.hasTicket || !event.ticketRequired" :to="`/c/${event.friendlyId}`") Entrar
            router-link.btn-primary(v-else-if="event.ticketPurchaseEnabled" :to="`/events/${event.friendlyId}/checkout`") Adquirir boleto
            span.btn-outline(v-else) Boletos no disponibles
        img.detail-flyer(v-if="event.flyerBase64" :src="event.flyerBase64" alt="Flyer del evento")
        .detail-flyer.placeholder(v-else aria-hidden="true") 🎟️
      section.schedule(v-if="renderedSchedule")
        h2 Cronograma
        .markdown-body(v-html="renderedSchedule")
      section.map-section(v-if="event.latitude != null && event.longitude != null")
        h2 Ubicación
        ConferenceMap(:latitude="event.latitude" :longitude="event.longitude" :label="event.venue || event.name")
        a.map-link(:href="osmUrl" target="_blank" rel="noopener noreferrer") Abrir en OpenStreetMap ↗
    .state(v-else-if="loading") Cargando evento...
    .state.error(v-else) {{ error }}
</template>

<script lang="ts">
import { computed, onMounted, ref } from 'vue'
import { marked, Renderer } from 'marked'
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import { getPublicConference } from '@/services/api/usersApi'
import type { PublicConference } from '@/services/api/types'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'PublicEventDetailPage',
  components: { AppHeader, ConferenceMap },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const event = ref<PublicConference | null>(null); const loading = ref(true); const error = ref('')
    const renderer = new Renderer(); renderer.html = () => ''
    const renderedSchedule = computed(() => {
      if (!event.value?.scheduleMarkdown) return ''
      const html = marked.parse(event.value.scheduleMarkdown, { async: false, renderer }) as string
      return html.replace(/href\s*=\s*["'](?!https?:\/\/|mailto:)[^"']*["']/gi, 'href="#"')
    })
    const osmUrl = computed(() => event.value && event.value.latitude != null && event.value.longitude != null
      ? `https://www.openstreetmap.org/?mlat=${event.value.latitude}&mlon=${event.value.longitude}#map=16/${event.value.latitude}/${event.value.longitude}` : '#')
    onMounted(async () => {
      const token = auth.isAuthenticated() && auth.state.role !== 'guest' ? auth.state.token : null
      try { event.value = await getPublicConference(route.params.friendlyId as string, token) }
      catch { error.value = 'No se encontró el evento público.' }
      finally { loading.value = false }
    })
    return { event, loading, error, renderedSchedule, osmUrl }
  }
}
</script>

<style scoped>
.public-event-page { min-height: 100vh; background: #f5f3ff; }
.public-detail { max-width: 1050px; margin: 0 auto; padding: 42px 24px 70px; }
.back { color: #4f46e5; text-decoration: none; font-weight: 700; }
.detail-hero { display: grid; grid-template-columns: 1.1fr .9fr; gap: 34px; align-items: center; margin-top: 26px; }
.detail-hero.reverse { direction: rtl; }.detail-hero.reverse > * { direction: ltr; }
.detail-copy { background: #fff; padding: 28px; border-radius: 18px; box-shadow: 0 8px 25px rgba(30,27,75,.08); }
h1 { color: #1e1b4b; font-size: clamp(2rem, 5vw, 3.4rem); margin: 12px 0 8px; }.organizer-card { display: flex; align-items: center; gap: 10px; margin: 12px 0 4px; }.organizer-avatar { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }.organizer-copy { display: flex; flex-direction: column; gap: 2px; }.organizer-label { color: #9ca3af; font-size: .78rem; }.organizer { color: #6b7280; font-weight: 700; }
.description { color: #4b5563; line-height: 1.65; white-space: pre-wrap; }.detail-flyer { width: 100%; max-height: 440px; object-fit: cover; border-radius: 18px; box-shadow: 0 8px 25px rgba(30,27,75,.12); }.placeholder { display: grid; place-items: center; min-height: 300px; background: #e0e7ff; font-size: 5rem; }
.badges { display: flex; flex-wrap: wrap; gap: 7px; }.badge { border-radius: 999px; padding: 5px 10px; background: #e0e7ff; color: #3730a3; font-size: .8rem; font-weight: 700; }.badge-ticket { background: #fef3c7; color: #92400e; }
.event-facts { display: grid; grid-template-columns: auto 1fr; gap: 8px 12px; margin: 22px 0; }.event-facts dt { color: #9ca3af; font-weight: 700; }.event-facts dd { margin: 0; color: #374151; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; }.btn-primary, .btn-outline { border-radius: 9px; padding: 11px 16px; font-weight: 700; text-decoration: none; cursor: pointer; }.btn-primary { border: 0; background: #4f46e5; color: white; }.btn-outline { border: 1px solid #4f46e5; color: #4f46e5; background: white; }.action-message { color: #166534; }.action-error, .error { color: #b91c1c; }
.schedule, .map-section { margin-top: 28px; background: #fff; padding: 28px; border-radius: 18px; }.schedule h2, .map-section h2 { color: #1e1b4b; }.markdown-body :deep(h2) { color: #a16207; border-bottom: 2px dotted #d1d5db; padding-bottom: 8px; }.markdown-body :deep(p) { color: #4b5563; line-height: 1.6; }.markdown-body :deep(a) { color: #4f46e5; }
.map-section :deep(.conference-map) { min-height: 360px; }.map-link { display: inline-block; margin-top: 10px; color: #4f46e5; font-weight: 700; }.state { max-width: 1050px; margin: 50px auto; padding: 30px; color: #6b7280; }.state.error { color: #b91c1c; }
.public-event-page.theme-editorial { background: #17152d; color: #f5f5f4; }.theme-editorial .back { color: #fbbf24; }.theme-editorial .detail-copy, .theme-editorial .schedule, .theme-editorial .map-section { background: #24213f; box-shadow: none; }.theme-editorial h1, .theme-editorial .schedule h2, .theme-editorial .map-section h2 { color: #fff7ed; font-family: Georgia, serif; }.theme-editorial .description, .theme-editorial .event-facts dd, .theme-editorial .organizer { color: #d6d3d1; }.theme-editorial .event-facts dt, .theme-editorial .organizer-label { color: #fbbf24; }.theme-editorial .detail-flyer { border-radius: 4px; }.theme-editorial .btn-outline { border-color: #fbbf24; color: #fbbf24; background: transparent; }
.public-event-page.theme-minimal { background: #fafafa; }.theme-minimal .detail-copy, .theme-minimal .schedule, .theme-minimal .map-section { border-radius: 4px; box-shadow: none; border: 1px solid #e5e7eb; }.theme-minimal .detail-flyer { border-radius: 4px; }
@media (max-width: 720px) { .public-detail { padding: 28px 16px 50px; }.detail-hero { grid-template-columns: 1fr; }.detail-hero.reverse { direction: ltr; } }
</style>
