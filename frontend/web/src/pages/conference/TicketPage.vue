<template lang="pug">
.ticket-page
  .ticket-loading(v-if="loading") Cargando tu boleto...
  .ticket-empty(v-else-if="!ticketed")
    p Esta conferencia no requiere boleto, solo únete y participa.

  template(v-else-if="ticket")
    .ticket-card
      h2 Tu boleto
      TicketQr(:ticket-code="ticket.ticketCode")
      p.ticket-status(:class="{ checked: ticket.status === 'CHECKED_IN' }")
        span(v-if="ticket.status === 'CHECKED_IN'") ✅ Ya ingresaste al evento
        span(v-else) 🎫 Reservado — muestra este QR en la entrada

  .ticket-general-cta(v-else)
    p Aún no tienes un boleto canjeado para esta conferencia.
    template(v-if="auth.state.token && auth.state.role !== 'guest'")
      input.ticket-input(v-model="ticketInput" placeholder="Pega el QR o escribe el UUID v4")
      button.btn-primary(type="button" @click="claim" :disabled="claiming || !ticketInput.trim()")
        span(v-if="claiming") Canjeando...
        span(v-else) Canjear boleto
    template(v-else)
      p Puedes canjearlo como invitado o asociarlo a una cuenta.
      input.ticket-input(v-model="guestName" placeholder="Tu nombre para entrar como invitado")
      input.ticket-input(v-model="ticketInput" placeholder="Pega el QR o escribe el UUID v4")
      button.btn-secondary(type="button" @click="toggleScanner") {{ scanning ? 'Cerrar cámara' : 'Escanear QR' }}
      .claim-scanner(v-if="scanning")
        video(ref="videoEl")
        p Apunta la cámara al QR del boleto.
      button.btn-primary(type="button" @click="claimAsGuest" :disabled="claiming || !ticketInput.trim()") Canjear como invitado
      .auth-links
        router-link(:to="{ path: '/login', query: { redirect: $route.fullPath, ticket: ticketInput } }") Iniciar sesión
        router-link(:to="{ path: '/register', query: { redirect: $route.fullPath, ticket: ticketInput } }") Registrarme
    p.ticket-error(v-if="error") {{ error }}

</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import TicketQr from '@/components/TicketQr.vue'
import QrScanner from 'qr-scanner'
import { getMyIssuedTicket, getMyTicket, claimTicket, claimTicketAsGuest } from '@/services/api/usersApi'
import type { Ticket, Reservation, SeatingMode } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'TicketPage',
  components: { TicketQr },
  props: {
    conferenceId: { type: String, default: '' },
    seatingMode: { type: String as () => SeatingMode | undefined, default: undefined },
    ticketed: { type: Boolean, default: false }
  },
  setup(props: { conferenceId?: string, seatingMode?: SeatingMode, ticketed?: boolean }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const ticket = ref<Ticket | Reservation | null>(null)
    const claiming = ref(false)
    const ticketInput = ref('')
    const guestName = ref('')
    const error = ref('')
    const scanning = ref(false)
    const videoEl = ref<HTMLVideoElement | null>(null)
    let scanner: QrScanner | null = null

    async function load() {
      if (!props.conferenceId || !props.ticketed) {
        loading.value = false
        return
      }
      try {
        const routeTicket = new URLSearchParams(location.search).get('ticket')
        if (routeTicket) ticketInput.value = routeTicket
        if (auth.state.token && auth.state.role !== 'guest') {
          ticket.value = await getMyIssuedTicket(props.conferenceId, auth.state.token)
          if (!ticket.value) ticket.value = await getMyTicket(props.conferenceId, auth.state.token)
          if (routeTicket) { ticketInput.value = routeTicket; if (!ticket.value) await claim() }
        }
      } catch (e: any) { error.value = 'No se pudo cargar el boleto.' }
      finally { loading.value = false }
    }

    async function claim() {
      if (!ticketInput.value.trim() || !auth.state.token) return
      claiming.value = true; error.value = ''
      try {
        ticket.value = await claimTicket(props.conferenceId as string, ticketInput.value.trim(), auth.state.token)
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        error.value = code === 'ticket_already_claimed'
          ? 'Este boleto ya fue canjeado por otra persona.'
          : 'El QR o UUID no corresponde a esta conferencia.'
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
          ticketInput.value = result.data
          stopScanner()
          if (auth.state.token && auth.state.role !== 'guest') claim()
          else claimAsGuest()
        }, { highlightScanRegion: true, highlightCodeOutline: true })
        scanner.start().catch(() => { error.value = 'No se pudo acceder a la cámara.'; stopScanner() })
      }, 0)
    }

    onMounted(load)
    onBeforeUnmount(stopScanner)

    return {
      loading, ticket, claiming, ticketInput, guestName, error, claim, claimAsGuest, auth,
      scanning, videoEl, toggleScanner
    }
  }
}
</script>

<style scoped>
.ticket-page { padding: 24px; max-width: 480px; margin: 0 auto; }
.ticket-loading, .ticket-empty { text-align: center; color: #6b7280; padding: 60px 24px; }
.ticket-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 16px; padding: 32px 24px;
  display: flex; flex-direction: column; align-items: center; gap: 16px;
}
.ticket-card h2 { margin: 0; color: #1e1b4b; }
.ticket-status { font-size: 0.95rem; font-weight: 600; color: #4f46e5; text-align: center; }
.ticket-status.checked { color: #166534; }
.btn-cancel {
  padding: 8px 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #dc2626;
  cursor: pointer; font-size: 0.85rem;
}
.btn-cancel:hover { background: #fee2e2; }
.btn-cancel:disabled { opacity: 0.5; cursor: not-allowed; }

.ticket-general-cta { text-align: center; padding: 40px 24px; }
.btn-primary {
  padding: 12px 24px; background: #4f46e5; color: #fff; border: none; border-radius: 8px;
  font-weight: 600; cursor: pointer; font-size: 1rem;
}
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary { padding: 10px 16px; border: 1px solid #c7d2fe; border-radius: 8px; background: #eef2ff; color: #4338ca; cursor: pointer; }
.claim-scanner { margin: 14px auto; max-width: 320px; }
.claim-scanner video { width: 100%; border-radius: 10px; background: #111827; }
.claim-scanner p { color: #6b7280; font-size: 0.85rem; }
.ticket-error { color: #dc2626; margin-top: 12px; font-size: 0.9rem; }
.ticket-seated-picker { padding: 20px 0; text-align: center; }
.ticket-seated-picker .btn-primary { margin-top: 16px; }
</style>
