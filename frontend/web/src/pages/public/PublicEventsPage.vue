<template lang="pug">
.public-events-page
  AppHeader
  main.public-main
    .hero
      p.eyebrow InsightBloom
      h1 Cartelera de eventos
      p Descubre eventos públicos, revisa sus detalles y solicita tu boleto.
    .state(v-if="loading") Cargando eventos...
    .state.error(v-else-if="error") {{ error }}
    .empty(v-else-if="events.length === 0") No hay eventos públicos publicados por ahora.
    .event-grid(v-else)
      article.event-card(v-for="event in events" :key="event.friendlyId" :class="`theme-${(event.publicTheme || 'CLASSIC').toLowerCase()}`")
        router-link.event-card-main(:to="`/events/${event.friendlyId}`")
          img.event-flyer(v-if="event.flyerBase64" :src="event.flyerBase64" alt="Flyer del evento")
          .event-flyer.placeholder(v-else aria-hidden="true") 🎟️
          .event-card-body
            .badges
              span.badge {{ event.visibility === 'HYBRID' ? 'Híbrido' : 'Público' }}
              span.badge.badge-ticket(v-if="event.ticketRequired") 🎟️ Boleto requerido
              span.badge.badge-free(v-else) Acceso libre
              span.badge.badge-price(v-if="event.ticketRequired") {{ Number(event.ticketPrice || 0) > 0 ? `${event.ticketPrice} ${event.ticketCurrency || 'MXN'}` : 'Gratis' }}
            h2 {{ event.name }}
            p.description(v-if="event.description") {{ event.description }}
            dl.event-facts
              template(v-if="event.eventDate")
                dt Fecha
                dd {{ event.eventDate }}{{ event.startTime ? ` · ${event.startTime}` : '' }}
              template(v-if="event.venue")
                dt Lugar
                dd {{ event.venue }}
              template(v-if="event.capacity != null")
                dt Aforo
                dd {{ event.remainingSeats }} disponibles de {{ event.capacity }}
        .event-card-footer
          span.organizer-name
            img.organizer-avatar(v-if="event.organizerPhotoBase64" :src="event.organizerPhotoBase64" alt="")
            span {{ event.organizer }}
          router-link.event-action(:to="eventActionPath(event)") {{ eventActionLabel(event) }} →
</template>

<script lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '@/app/layout/AppHeader.vue'
import { getPublicConferences } from '@/services/api/usersApi'
import type { PublicConference } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'PublicEventsPage',
  components: { AppHeader },
  setup() {
    const events = ref<PublicConference[]>([])
    const loading = ref(true)
    const error = ref('')
    const auth = useAuthStore()
    const eventActionLabel = (event: PublicConference) =>
      event.ticketRequired && !event.hasTicket ? 'Adquirir boleto' : 'Entrar'
    const eventActionPath = (event: PublicConference) =>
      event.ticketRequired && !event.hasTicket
        ? `/events/${event.friendlyId}/checkout`
        : `/c/${event.friendlyId}`
    onMounted(async () => {
      const token = auth.isAuthenticated() && auth.state.role !== 'guest' ? auth.state.token : null
      try { events.value = await getPublicConferences(token) }
      catch { error.value = 'No se pudo cargar la cartelera.' }
      finally { loading.value = false }
    })
    return { events, loading, error, eventActionLabel, eventActionPath }
  }
}
</script>

<style scoped>
.public-events-page { min-height: 100vh; background: #f5f3ff; }
.public-main { max-width: 1160px; margin: 0 auto; padding: 64px 24px; }
.hero { margin-bottom: 30px; color: #6b7280; }
.eyebrow { color: #4f46e5; font-weight: 800; text-transform: uppercase; letter-spacing: .12em; margin: 0 0 8px; }
h1 { color: #1e1b4b; margin: 0 0 8px; font-size: clamp(2rem, 5vw, 3.2rem); }
.hero p:last-child { margin: 0; font-size: 1.1rem; }
.event-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 22px; }
.event-card { color: inherit; text-decoration: none; background: #fff; border-radius: 18px; overflow: hidden; box-shadow: 0 8px 28px rgba(30,27,75,.1); transition: transform .18s, box-shadow .18s; }
.event-card:hover { transform: translateY(-3px); box-shadow: 0 14px 34px rgba(30,27,75,.16); }
.event-card-main { display: block; color: inherit; text-decoration: none; }
.event-flyer { width: 100%; height: 170px; object-fit: cover; display: block; background: #e0e7ff; }
.placeholder { display: grid; place-items: center; font-size: 3rem; }
.event-card-body { padding: 20px; }
.badges { display: flex; flex-wrap: wrap; gap: 7px; margin-bottom: 12px; }
.badge { border-radius: 999px; padding: 4px 9px; background: #e0e7ff; color: #3730a3; font-size: .75rem; font-weight: 700; }
.badge-ticket { background: #fef3c7; color: #92400e; }
.badge-free { background: #dcfce7; color: #166534; }
.badge-price { background: #eef2ff; color: #3730a3; }
h2 { color: #1e1b4b; margin: 0 0 8px; font-size: 1.3rem; }
.description { color: #6b7280; line-height: 1.5; margin: 0 0 16px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.event-facts { display: grid; grid-template-columns: auto 1fr; gap: 5px 10px; margin: 0; font-size: .88rem; }
.event-facts dt { color: #9ca3af; font-weight: 700; }
.event-facts dd { color: #374151; margin: 0; }
.event-card-footer { border-top: 1px solid #eef0f5; display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 20px; padding: 14px 0; color: #6b7280; font-size: .8rem; }
.organizer-name { display: inline-flex; align-items: center; gap: 7px; min-width: 0; }.organizer-avatar { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }
.event-action { color: #4f46e5; font-weight: 800; white-space: nowrap; text-decoration: none; }
.state, .empty { background: #fff; padding: 30px; border-radius: 16px; color: #6b7280; }
.error { color: #b91c1c; }
.event-card.theme-editorial { border-radius: 4px; background: #17152d; color: #fef3c7; box-shadow: 0 12px 30px rgba(23,21,45,.2); }
.event-card.theme-editorial .event-card-body { padding: 24px; }.event-card.theme-editorial h2 { color: #fff7ed; font-family: Georgia, serif; font-size: 1.5rem; }.event-card.theme-editorial .description, .event-card.theme-editorial .event-facts dd, .event-card.theme-editorial .event-card-footer { color: #d6d3d1; }.event-card.theme-editorial .event-facts dt { color: #fbbf24; }.event-card.theme-editorial .event-card-footer { border-color: #3f3a5d; }.event-card.theme-editorial .event-action { color: #fbbf24; }
.event-card.theme-minimal { border-radius: 4px; box-shadow: none; border: 1px solid #d1d5db; }.event-card.theme-minimal .event-card-body { padding: 18px; }.event-card.theme-minimal .event-flyer { height: 130px; filter: saturate(.6); }
@media (max-width: 560px) { .public-main { padding: 38px 16px; } }
</style>
