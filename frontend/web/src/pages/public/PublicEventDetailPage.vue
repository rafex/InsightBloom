<template lang="pug">
  .public-event-page(:class="`theme-${(event?.publicTheme || 'CLASSIC').toLowerCase()}`")
    AppHeader
    main#main-content.public-detail(v-if="event" tabindex="-1")
      BaseLink.back(size="sm" variant="ghost" to="/events") ← Volver a la cartelera
      .detail-hero(:class="{ reverse: event.scheduleLayout === 'LEFT' }")
        .detail-copy
          .badges
            StatusBadge.event-badge(
              :status="event.visibility === 'HYBRID' ? 'HYBRID' : 'PUBLIC'"
              :label="event.visibility === 'HYBRID' ? 'Evento híbrido' : 'Evento público'"
              tone="info"
              pill
            )
            StatusBadge.event-badge(v-if="event.ticketRequired" status="TICKET_REQUIRED" label="🎟️ Boleto requerido" tone="warning" pill)
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
            BaseLink(v-if="event.hasTicket || !event.ticketRequired" :to="`/c/${event.friendlyId}`") Entrar
            BaseLink(v-else-if="event.ticketPurchaseEnabled" :to="`/events/${event.friendlyId}/checkout`") Adquirir boleto
            StatusBadge(v-else status="INACTIVE" label="Boletos no disponibles")
        img.detail-flyer(v-if="event.flyerBase64" :src="event.flyerBase64" :alt="`Flyer de ${event.name}`")
        .detail-flyer.placeholder(v-else aria-hidden="true") 🎟️
      section.schedule(v-if="renderedSchedule")
        h2 Cronograma
        .markdown-body(v-html="renderedSchedule")
      section.map-section(v-if="event.latitude != null && event.longitude != null")
        h2 Ubicación
        ConferenceMap(:latitude="event.latitude" :longitude="event.longitude" :label="event.venue || event.name")
        BaseAnchor.map-link(variant="ghost" size="sm" :href="osmUrl" target="_blank" rel="noopener noreferrer") Abrir en OpenStreetMap ↗
    LoadingState.state(v-else-if="loading" message="Cargando evento…")
    FeedbackMessage.state(v-else :message="error" tone="error")
</template>

<script lang="ts">
import { computed, onMounted, ref } from 'vue'
import { marked, Renderer } from 'marked'
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import BaseAnchor from '@/components/ui/BaseAnchor.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { getPublicConference } from '@/services/api/usersApi'
import type { PublicConference } from '@/services/api/types'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'PublicEventDetailPage',
  components: { AppHeader, BaseAnchor, BaseLink, ConferenceMap, FeedbackMessage, LoadingState, StatusBadge },
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
.public-event-page { min-height: 100vh; background: var(--color-bg); }
.public-detail { max-width: 1050px; margin: 0 auto; padding: 42px 24px 70px; }
.back { margin-left: -12px; color: var(--color-primary); }
.detail-hero { display: grid; grid-template-columns: 1.1fr .9fr; gap: 34px; align-items: center; margin-top: 26px; }
.detail-hero.reverse { direction: rtl; }.detail-hero.reverse > * { direction: ltr; }
.detail-copy { background: var(--color-surface); padding: 28px; border-radius: 18px; box-shadow: var(--shadow-card); }
h1 { color: var(--color-heading); font-size: clamp(2rem, 5vw, 3.4rem); margin: 12px 0 8px; }.organizer-card { display: flex; align-items: center; gap: 10px; margin: 12px 0 4px; }.organizer-avatar { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }.organizer-copy { display: flex; flex-direction: column; gap: 2px; }.organizer-label { color: var(--color-text-muted); font-size: .78rem; }.organizer { color: var(--color-text-muted); font-weight: 700; }
.description { color: var(--color-text-secondary); line-height: 1.65; white-space: pre-wrap; }.detail-flyer { width: 100%; max-height: 440px; object-fit: cover; border-radius: var(--radius-lg); box-shadow: var(--shadow-card); }.placeholder { display: grid; place-items: center; min-height: 300px; background: var(--color-primary-soft); font-size: 5rem; }
.badges { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; }
.event-facts { display: grid; grid-template-columns: auto 1fr; gap: 8px 12px; margin: 22px 0; }.event-facts dt { color: var(--color-text-muted); font-weight: 700; }.event-facts dd { margin: 0; color: var(--color-text-secondary); }
.actions { display: flex; gap: 10px; flex-wrap: wrap; }.action-message { color: var(--color-success); }.action-error { color: var(--color-danger-dark); }
.schedule, .map-section { margin-top: 28px; background: var(--color-surface); padding: 28px; border-radius: 18px; }.schedule h2, .map-section h2 { color: var(--color-heading); }.markdown-body :deep(h2) { color: var(--color-warning); border-bottom: 2px dotted var(--color-border); padding-bottom: 8px; }.markdown-body :deep(p) { color: var(--color-text-secondary); line-height: 1.6; }.markdown-body :deep(a) { color: var(--color-primary); }
.map-section :deep(.conference-map) { min-height: 360px; }.map-link { justify-content: flex-start; padding: 0; border-radius: 0; margin-top: 10px; color: var(--color-primary); font-weight: 700; }.state { max-width: 1050px; margin: 50px auto; padding: 30px; }
.public-event-page.theme-editorial { background: var(--color-editorial-page); color: var(--color-editorial-text); }.theme-editorial .back { color: var(--color-editorial-accent); }.theme-editorial .detail-copy, .theme-editorial .schedule, .theme-editorial .map-section { background: var(--color-editorial-surface); box-shadow: none; }.theme-editorial h1, .theme-editorial .schedule h2, .theme-editorial .map-section h2 { color: var(--color-editorial-heading); font-family: Georgia, serif; }.theme-editorial .description, .theme-editorial .event-facts dd, .theme-editorial .organizer { color: var(--color-editorial-muted); }.theme-editorial .event-facts dt, .theme-editorial .organizer-label { color: var(--color-editorial-accent); }.theme-editorial .detail-flyer { border-radius: 4px; }
.public-event-page.theme-minimal { background: var(--color-surface); }.theme-minimal .detail-copy, .theme-minimal .schedule, .theme-minimal .map-section { border-radius: 4px; box-shadow: none; border: 1px solid var(--color-border-subtle); }.theme-minimal .detail-flyer { border-radius: 4px; }
@media (max-width: 720px) { .public-detail { padding: 28px 16px 50px; }.detail-hero { grid-template-columns: 1fr; }.detail-hero.reverse { direction: ltr; } }
</style>
