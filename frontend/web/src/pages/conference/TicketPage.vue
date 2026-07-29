<template lang="pug">
.ticket-page
  .ticket-loading(v-if="loading") Cargando tu boleto...
  .ticket-empty(v-else-if="!ticketed")
    p Esta conferencia no requiere boleto, solo únete y participa.

  template(v-else-if="ticket")
    .ticket-canvas
      .ticket-wrapper
        .ticket
          .t-main
            .t-content
              .t-header
                .t-logo
                  span.logo-mark ◈
                  span INSIGHTBLOOM
                .t-type Boleto de acceso
              .t-title {{ eventName }}
              .t-subtitle {{ eventSubtitle }}
              .t-details
                .t-detail-item
                  span.t-label Asistente
                  span.t-value {{ ticketOwnerLabel }}
                .t-detail-item
                  span.t-label Fecha
                  span.t-value {{ eventDateLabel }}
                .t-detail-item
                  span.t-label Lugar
                  span.t-value {{ eventVenueLabel }}
                .t-detail-item
                  span.t-label Modalidad
                  span.t-value {{ eventModeLabel }}
            .t-perforation
              .t-perf-line
          .t-stub
            .t-qr-container
              TicketQr(:ticket-code="ticket.ticketCode" :ticket-url="ticketUrl" :show-code="false")
              span.t-qr-caption Escanea para abrir tu acceso
            .t-admit
              span.t-admit-text Estado
              strong.t-admit-num(:class="{ checked: ticket.status === 'CHECKED_IN' }") {{ ticketStatusLabel }}
              span.t-admit-hint(v-if="ticket.status === 'CHECKED_IN'") Acceso registrado
              span.t-admit-hint(v-else) Presenta este boleto en la entrada
      .ticket-manual
        span Código manual / UUID del boleto
        code {{ ticket.ticketCode }}
        small Si el escaneo falla, proporciona este código al personal de acceso.
    p.ticket-status(:class="{ checked: ticket.status === 'CHECKED_IN' }")
      span(v-if="ticket.status === 'CHECKED_IN'") ✅ Ya ingresaste al evento
      span(v-else) 🎫 Boleto reservado — muestra este QR en la entrada

  .ticket-general-cta(v-else)
    p Aún no tienes un boleto canjeado para esta conferencia.
    template(v-if="auth.state.token && auth.state.role !== 'guest'")
      input.ticket-input(v-model="ticketInput" placeholder="Pega el QR o escribe el UUID v4")
      BaseButton(type="button" @click="claim" :disabled="!ticketInput.trim()" :loading="claiming") Canjear boleto
    template(v-else)
      p Puedes canjearlo como invitado o asociarlo a una cuenta.
      input.ticket-input(v-model="guestName" placeholder="Tu nombre para entrar como invitado")
      input.ticket-input(v-model="ticketInput" placeholder="Pega el QR o escribe el UUID v4")
      BaseButton(variant="secondary" type="button" @click="toggleScanner") {{ scanning ? 'Cerrar cámara' : 'Escanear QR' }}
      .claim-scanner(v-if="scanning")
        video(ref="videoEl")
        p Apunta la cámara al QR del boleto.
      BaseButton(type="button" @click="claimAsGuest" :disabled="!ticketInput.trim()" :loading="claiming") Canjear como invitado
      .auth-links
        router-link(:to="{ path: '/login', query: { redirect: $route.fullPath, ticket: ticketInput } }") Iniciar sesión
        router-link(:to="{ path: '/register', query: { redirect: $route.fullPath, ticket: ticketInput } }") Registrarme
    p.ticket-error(v-if="error") {{ error }}

</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TicketQr from '@/components/TicketQr.vue'
import QrScanner from 'qr-scanner'
import { getConferenceByFriendlyId, getMyIssuedTicket, getMyTicket, claimTicket, claimTicketAsGuest } from '@/services/api/usersApi'
import type { Ticket, Reservation, SeatingMode } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'

export default {
  name: 'TicketPage',
  components: { TicketQr, BaseButton },
  props: {
    conferenceId: { type: String, default: '' },
    seatingMode: { type: String as () => SeatingMode | undefined, default: undefined },
    ticketed: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, seatingMode?: SeatingMode, ticketed?: boolean }) {
    const auth = useAuthStore()
    const route = useRoute()
    const loading = ref(true)
    const ticket = ref<Ticket | Reservation | null>(null)
    const claiming = ref(false)
    const ticketInput = ref('')
    const guestName = ref('')
    const error = ref('')
    const scanning = ref(false)
    const videoEl = ref<HTMLVideoElement | null>(null)
    let scanner: QrScanner | null = null

    const eventName = ref('Tu evento')
    const eventDate = ref<string | null>(null)
    const eventStartTime = ref<string | null>(null)
    const eventEndTime = ref<string | null>(null)
    const eventVenue = ref<string | null>(null)
    const eventFriendlyId = String(route.params.friendlyId || '')
    const eventModeLabel = ref('Acceso general')
    const ticketUrl = computed(() => {
      if (!ticket.value || !eventFriendlyId) return ''
      return `${window.location.origin}/c/${encodeURIComponent(eventFriendlyId)}/ticket?t=${encodeURIComponent(ticket.value.ticketCode)}`
    })
    const eventSubtitle = computed(() => {
      const times = [eventStartTime.value, eventEndTime.value].filter(Boolean).join(' – ')
      return [times, eventVenue.value].filter(Boolean).join(' · ') || 'Entrada para participar en este evento'
    })
    const eventDateLabel = computed(() => eventDate.value || 'Por confirmar')
    const eventVenueLabel = computed(() => eventVenue.value || 'Por confirmar')
    const ticketOwnerLabel = computed(() => {
      if (auth.state.role === 'guest') return 'Invitado'
      return 'Asistente registrado'
    })
    const ticketStatusLabel = computed(() => ticket.value?.status === 'CHECKED_IN' ? 'LISTO' : 'RESERVADO')

    async function load() {
      if (!props.conferenceId || !props.ticketed) {
        loading.value = false
        return
      }
      try {
        const publicConference = await getConferenceByFriendlyId(eventFriendlyId).catch(() => null)
        if (publicConference) {
          eventName.value = publicConference.displayName || publicConference.name
          eventDate.value = publicConference.eventDate || null
          eventStartTime.value = publicConference.startTime || null
          eventEndTime.value = publicConference.endTime || null
          eventVenue.value = publicConference.venue || null
          eventModeLabel.value = publicConference.visibility === 'HYBRID' ? 'Híbrido' : publicConference.visibility === 'PUBLIC' ? 'Público' : 'Privado'
        }
        // Accept both the compact QR parameter and older tickets already in
        // circulation.
        const query = new URLSearchParams(location.search)
        const routeTicket = query.get('t') || query.get('ticket')
        if (routeTicket) ticketInput.value = routeTicket
        if (auth.state.token && auth.state.role !== 'guest') {
          ticket.value = await getMyIssuedTicket(props.conferenceId, auth.state.token)
          if (!ticket.value) ticket.value = await getMyTicket(props.conferenceId, auth.state.token)
          if (routeTicket) { ticketInput.value = routeTicket; if (!ticket.value) await claim() }
        }
      } catch (e: any) {
        // Distinguir causas (mismo criterio que CheckInScannerPage): red caida, sesion vencida
        // y "todavia no tenes boleto" requieren acciones distintas del usuario.
        const status = e?.response?.status
        if (!e?.response) {
          error.value = 'No se pudo conectar con el servidor. Verificá tu conexión y reintentá.'
        } else if (status === 401) {
          error.value = 'Tu sesión expiró. Volvé a iniciar sesión para ver tu boleto.'
        } else if (status === 404) {
          error.value = 'Todavía no tenés un boleto para este evento.'
        } else {
          error.value = `No se pudo cargar el boleto (${e?.response?.data?.error?.message || status}). Reintentá en unos segundos.`
        }
      }
      finally { loading.value = false }
    }

    async function claim() {
      if (!ticketInput.value.trim() || !auth.state.token) return
      claiming.value = true; error.value = ''
      try {
        ticket.value = await claimTicket(props.conferenceId as string, ticketInput.value.trim(), auth.state.token)
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        const messages: Record<string, string> = {
          ticket_already_claimed: 'Este boleto ya fue canjeado por otra persona.',
          user_already_has_ticket: 'Ya tenés un boleto para este evento. No se puede reclamar más de uno por persona.'
        }
        error.value = messages[code] || 'El QR o UUID no corresponde a esta conferencia.'
      } finally {
        claiming.value = false
      }
    }

    async function claimAsGuest() {
      if (!ticketInput.value.trim()) return
      claiming.value = true; error.value = ''
      try {
        const result = await claimTicketAsGuest(props.conferenceId as string, ticketInput.value.trim(), guestName.value.trim() || 'Invitado')
        auth.setSession({ token: result.token, role: 'guest', userUuid: result.guestUuid, expiresAt: result.expiresAt })
        location.reload()
      } catch (e: any) {
        error.value = e.response?.data?.error?.detail || 'No se pudo canjear el boleto.'
      } finally { claiming.value = false }
    }

    function stopScanner() {
      if (scanner) { scanner.stop(); scanner.destroy(); scanner = null }
      scanning.value = false
    }

    function toggleScanner() {
      if (scanning.value) { stopScanner(); return }
      scanning.value = true
      setTimeout(() => {
        if (!videoEl.value) return
        scanner = new QrScanner(videoEl.value, (result) => {
          const rawValue = typeof result === 'string'
            ? result
            : (result as { data?: unknown, rawValue?: unknown } | null)?.data
              || (result as { data?: unknown, rawValue?: unknown } | null)?.rawValue
          if (typeof rawValue !== 'string' || !rawValue.trim()) return
          ticketInput.value = rawValue
          stopScanner()
          if (auth.state.token && auth.state.role !== 'guest') claim()
          else claimAsGuest()
        }, {
          highlightScanRegion: true,
          highlightCodeOutline: true,
          preferredCamera: 'environment',
          maxScansPerSecond: 10,
          returnDetailedScanResult: true
        })
        // Some cameras apply contrast/inversion processing to screens. Trying
        // both modes makes the attendee-facing QR work across laptop and
        // phone displays while retaining the normal fast path.
        scanner.setInversionMode('both')
        scanner.start().catch(() => { error.value = 'No se pudo acceder a la cámara.'; stopScanner() })
      }, 0)
    }

    onMounted(load)
    onBeforeUnmount(stopScanner)

    return {
      loading, ticket, claiming, ticketInput, guestName, error, claim, claimAsGuest, auth,
      scanning, videoEl, toggleScanner, ticketUrl, eventName, eventSubtitle, eventDateLabel,
      eventVenueLabel, eventModeLabel, ticketOwnerLabel, ticketStatusLabel
    }
  }
}
</script>

<style scoped>
.ticket-page { padding: 32px 24px 48px; max-width: 820px; margin: 0 auto; }
.ticket-loading, .ticket-empty { text-align: center; color: var(--color-text-muted); padding: 60px 24px; }
.ticket-status { font-size: 0.95rem; font-weight: 600; color: var(--color-primary); text-align: center; }
.ticket-status.checked { color: var(--color-success); }
.ticket-canvas { display: flex; align-items: center; justify-content: center; padding: 8px; }
.ticket-wrapper {
  --t-bg: var(--ticket-bg);
  --t-bg-light: var(--ticket-bg-light);
  --t-accent: var(--ticket-accent);
  --t-accent-glow: var(--ticket-accent-glow);
  --t-text-main: var(--ticket-text-main);
  --t-text-muted: var(--ticket-text-muted);
  width: min(100%, 720px);
  perspective: 1000px;
}
.ticket {
  position: relative; color: var(--t-text-main); font-family: "Space Grotesk", "Segoe UI", system-ui, sans-serif;
  transform-style: preserve-3d; transition: transform .35s ease, box-shadow .35s ease;
  box-shadow: 0 20px 40px rgba(0,0,0,.28), 0 0 0 1px rgba(255,255,255,.08);
  border-radius: 16px; overflow: hidden; filter: drop-shadow(0 0 10px rgba(0,0,0,.2));
}
.ticket:hover { transform: rotateX(2deg) rotateY(-2deg) scale(1.01); box-shadow: 12px 18px 35px rgba(0,0,0,.32), 0 0 24px var(--t-accent-glow); }
.t-main {
  padding: clamp(24px, 5vw, 42px); position: relative; overflow: hidden;
  background: radial-gradient(circle at bottom left, transparent 1.2em, var(--t-bg) 1.25em), radial-gradient(circle at bottom right, transparent 1.2em, var(--t-bg) 1.25em);
  background-size: 51% 100%; background-position: bottom left, bottom right; background-repeat: no-repeat;
}
.t-main::after {
  content: ""; position: absolute; inset: 0; background-image: linear-gradient(rgba(124,58,237,.14) 1px, transparent 1px), linear-gradient(90deg, rgba(124,58,237,.14) 1px, transparent 1px);
  background-size: 28px 28px; opacity: .6; z-index: 0; pointer-events: none; transform: perspective(500px) rotateX(20deg) scale(1.5);
}
.t-content { position: relative; z-index: 1; }
.t-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: clamp(24px, 4vw, 38px); gap: 16px; }
.t-logo { display: flex; align-items: center; gap: 8px; font-weight: 900; font-size: .95rem; letter-spacing: .04em; color: #fff; }
.logo-mark { display: inline-grid; place-items: center; width: 28px; height: 28px; border-radius: 9px; color: #fff; background: var(--t-accent); box-shadow: 0 0 12px var(--t-accent-glow); font-size: 1.4rem; }
.t-type { font-size: .68rem; text-transform: uppercase; letter-spacing: .16em; color: var(--ticket-accent-soft); border: 1px solid var(--ticket-accent-border); padding: 6px 10px; border-radius: 999px; font-weight: 700; white-space: nowrap; }
.t-title { font-size: clamp(1.8rem, 5vw, 3.15rem); font-weight: 900; line-height: 1.08; margin-bottom: 8px; text-transform: uppercase; overflow-wrap: anywhere; background: linear-gradient(135deg, var(--color-text-inverse) 0%, var(--ticket-title-soft) 100%); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.t-subtitle { color: var(--t-text-muted); font-size: .95rem; margin-bottom: 32px; }
.t-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px 28px; margin-bottom: 12px; }
.t-detail-item { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.t-label { font-size: .65rem; text-transform: uppercase; letter-spacing: .12em; color: var(--t-text-muted); }
.t-value { font-size: 1rem; font-weight: 700; color: var(--t-text-main); overflow-wrap: anywhere; }
.t-perforation { display: flex; justify-content: space-between; height: 14px; align-items: center; position: relative; z-index: 2; background: var(--t-bg); }
.t-perf-line { flex-grow: 1; height: 0; border-top: 2px dashed rgba(255,255,255,.25); margin: 0 24px; }
.t-stub { padding: clamp(22px, 4vw, 34px); background: radial-gradient(circle at top left, transparent 1.2em, var(--t-bg-light) 1.25em), radial-gradient(circle at top right, transparent 1.2em, var(--t-bg-light) 1.25em); background-size: 51% 100%; background-position: top left, top right; background-repeat: no-repeat; display: flex; justify-content: space-between; align-items: center; gap: 24px; position: relative; }
.t-qr-container { display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 220px; }
.t-qr-container :deep(.ticket-qr) { display: flex; flex-direction: column; align-items: center; }
.t-qr-container :deep(canvas) { display: block; background: #fff; border: 8px solid #fff; border-radius: 5px; width: min(220px, 42vw); height: auto; }
.t-qr-caption { color: var(--t-text-muted); font-size: .72rem; text-align: center; }
.t-admit { text-align: right; min-width: 150px; }
.t-admit-text { display: block; font-size: .7rem; text-transform: uppercase; letter-spacing: .12em; color: var(--t-text-muted); }
.t-admit-num { display: block; font-size: clamp(1.35rem, 3vw, 2.1rem); font-weight: 900; line-height: 1.15; color: var(--ticket-number); text-shadow: 0 0 15px var(--t-accent-glow); }
.t-admit-num.checked { color: var(--ticket-success); text-shadow: none; }
.t-admit-hint { display: block; margin-top: 8px; color: var(--t-text-muted); font-size: .72rem; }
.ticket-manual { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 18px 16px 0; text-align: center; }
.ticket-manual span { color: var(--ticket-manual); font-size: .78rem; font-weight: 800; text-transform: uppercase; letter-spacing: .08em; }
.ticket-manual code { color: var(--ticket-manual-code); font: 700 .84rem/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
.ticket-manual small { color: var(--color-text-muted); font-size: .78rem; }
.ticket-general-cta { text-align: center; padding: 40px 24px; }
.claim-scanner { margin: 14px auto; max-width: 320px; }
.claim-scanner video { width: 100%; border-radius: 10px; background: var(--color-heading); }
.claim-scanner p { color: var(--color-text-muted); font-size: 0.85rem; }
.ticket-error { color: var(--color-danger); margin-top: 12px; font-size: 0.9rem; }
.ticket-seated-picker { padding: 20px 0; text-align: center; }
@media (max-width: 560px) {
  .ticket-page { padding: 20px 12px 36px; }
  .ticket-canvas { padding: 0; }
  .t-header { flex-direction: column; }
  .t-type { align-self: flex-start; }
  .t-details { grid-template-columns: 1fr; gap: 14px; }
  .t-stub { flex-direction: column; align-items: stretch; }
  .t-qr-container { min-width: 0; }
  .t-qr-container :deep(canvas) { width: 190px; }
  .t-admit { text-align: center; }
}
</style>
