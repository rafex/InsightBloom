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
    EmptyState.public-empty(v-else-if="events.length === 0" message="No hay eventos públicos publicados por ahora.")
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
import EmptyState from '@/components/ui/EmptyState.vue'
import { getPublicConferences } from '@/services/api/usersApi'
import type { PublicConference } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'PublicEventsPage',
  components: { AppHeader, EmptyState },
  setup() {
    const events = ref<PublicConference[]>([])
    const loading = ref(true)
    const error = ref('')
    const auth = useAuthStore()
    const eventActionLabel = (event: PublicConference) =>
      event.ticketRequired && !event.hasTicket
        ? (event.ticketPurchaseEnabled ? 'Adquirir boleto' : 'Boletos no disponibles')
        : 'Entrar'
    const eventActionPath = (event: PublicConference) =>
      event.ticketRequired && !event.hasTicket && event.ticketPurchaseEnabled
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
.public-events-page { min-height: 100vh; background: var(--color-bg); }
.public-main { max-width: 1160px; margin: 0 auto; padding: 64px 24px; }
.hero { margin-bottom: 30px; color: var(--color-text-muted); }
.eyebrow { color: var(--color-primary); font-weight: 800; text-transform: uppercase; letter-spacing: .12em; margin: 0 0 8px; }
h1 { color: var(--color-heading); margin: 0 0 8px; font-size: clamp(2rem, 5vw, 3.2rem); }
.hero p:last-child { margin: 0; font-size: 1.1rem; }
.event-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 22px; }
.event-card { color: inherit; text-decoration: none; background: var(--color-surface); border-radius: 18px; overflow: hidden; box-shadow: 0 8px 28px rgba(30,27,75,.1); transition: transform .18s, box-shadow .18s; }
.event-card:hover { transform: translateY(-3px); box-shadow: 0 14px 34px rgba(30,27,75,.16); }
.event-card-main { display: block; color: inherit; text-decoration: none; }
.event-flyer { width: 100%; height: 170px; object-fit: cover; display: block; background: var(--color-primary-soft); }
.placeholder { display: grid; place-items: center; font-size: 3rem; }
.event-card-body { padding: 20px; }
.badges { display: flex; flex-wrap: wrap; gap: 7px; margin-bottom: 12px; }
.badge { border-radius: 999px; padding: 4px 9px; background: var(--color-primary-soft); color: var(--color-primary-dark); font-size: .75rem; font-weight: 700; }
.badge-ticket { background: var(--color-warning-soft); color: var(--color-warning); }
.badge-free { background: var(--color-success-soft); color: var(--color-success); }
.badge-price { background: var(--color-primary-soft); color: var(--color-primary-dark); }
h2 { color: var(--color-heading); margin: 0 0 8px; font-size: 1.3rem; }
.description { color: var(--color-text-muted); line-height: 1.5; margin: 0 0 16px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.event-facts { display: grid; grid-template-columns: auto 1fr; gap: 5px 10px; margin: 0; font-size: .88rem; }
.event-facts dt { color: var(--color-text-muted); font-weight: 700; }
.event-facts dd { color: var(--color-text-secondary); margin: 0; }
.event-card-footer { border-top: 1px solid var(--color-border-subtle); display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 20px; padding: 14px 0; color: var(--color-text-muted); font-size: .8rem; }
.organizer-name { display: inline-flex; align-items: center; gap: 7px; min-width: 0; }.organizer-avatar { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }
.event-action { color: var(--color-primary); font-weight: 800; white-space: nowrap; text-decoration: none; }
.state { background: var(--color-surface); padding: 30px; border-radius: 16px; color: var(--color-text-muted); }
.public-empty { background: var(--color-surface); padding: 30px; border-radius: 16px; color: var(--color-text-muted); }
.error { color: var(--color-danger-dark); }
.event-card.theme-editorial { border-radius: 4px; background: var(--color-editorial-page); color: var(--color-warning-soft); box-shadow: 0 12px 30px rgba(23,21,45,.2); }
.event-card.theme-editorial .event-card-body { padding: 24px; }.event-card.theme-editorial h2 { color: var(--color-editorial-heading); font-family: Georgia, serif; font-size: 1.5rem; }.event-card.theme-editorial .description, .event-card.theme-editorial .event-facts dd, .event-card.theme-editorial .event-card-footer { color: var(--color-editorial-muted); }.event-card.theme-editorial .event-facts dt { color: var(--color-editorial-accent); }.event-card.theme-editorial .event-card-footer { border-color: var(--color-editorial-border); }.event-card.theme-editorial .event-action { color: var(--color-editorial-accent); }
.event-card.theme-minimal { border-radius: 4px; box-shadow: none; border: 1px solid var(--color-border); }.event-card.theme-minimal .event-card-body { padding: 18px; }.event-card.theme-minimal .event-flyer { height: 130px; filter: saturate(.6); }
@media (max-width: 560px) { .public-main { padding: 38px 16px; } }
</style>
