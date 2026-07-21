<template lang="pug">
.ticket-page
  .ticket-loading(v-if="loading") Cargando tu boleto...
  .ticket-empty(v-else-if="!seatingMode || seatingMode === 'NONE'")
    p Esta conferencia no requiere boleto, solo únete y participa.

  template(v-else-if="ticket")
    .ticket-card
      h2 Tu boleto
      TicketQr(:ticket-code="ticket.ticketCode")
      p.ticket-status(:class="{ checked: ticket.status === 'CHECKED_IN' }")
        span(v-if="ticket.status === 'CHECKED_IN'") ✅ Ya ingresaste al evento
        span(v-else) 🎫 Reservado — muestra este QR en la entrada
      button.btn-cancel(v-if="ticket.status === 'RESERVED'" type="button" @click="cancel" :disabled="cancelling")
        span(v-if="cancelling") Cancelando...
        span(v-else) Cancelar mi reserva

  .ticket-general-cta(v-else-if="seatingMode === 'GENERAL'")
    p Aún no tienes un boleto para esta conferencia.
    button.btn-primary(type="button" @click="reserve" :disabled="reserving")
      span(v-if="reserving") Reservando...
      span(v-else) Reservar mi lugar
    p.ticket-error(v-if="error") {{ error }}

  .ticket-seated-picker(v-else-if="seatingMode === 'SEATED'")
    p(v-if="!venueMapUrl") El organizador aún no ha publicado el mapa de asientos.
    template(v-else)
      p Elige tu asiento en el mapa.
      SeatMapPicker(:image-url="venueMapUrl" :seats="seatMap" v-model="selectedSeat")
      button.btn-primary(type="button" @click="reserveSelectedSeat" :disabled="!selectedSeat || reserving")
        span(v-if="reserving") Reservando...
        span(v-else) Reservar este asiento
      p.ticket-error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import TicketQr from '@/components/TicketQr.vue'
import SeatMapPicker from '@/components/SeatMapPicker.vue'
import {
  getMyTicket, reserveGeneral, cancelReservation, getConference, getConferenceSeatMap, reserveSeat
} from '@/services/api/usersApi'
import type { Reservation, SeatingMode, VenueSeat } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'TicketPage',
  components: { TicketQr, SeatMapPicker },
  props: {
    conferenceId: { type: String, default: '' },
    seatingMode: { type: String as () => SeatingMode | undefined, default: undefined }
  },
  setup(props: { conferenceId?: string, seatingMode?: SeatingMode }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const ticket = ref<Reservation | null>(null)
    const reserving = ref(false)
    const cancelling = ref(false)
    const error = ref('')
    const venueMapUrl = ref('')
    const seatMap = ref<VenueSeat[]>([])
    const selectedSeat = ref<string | null>(null)

    async function load() {
      if (!props.conferenceId || !props.seatingMode || props.seatingMode === 'NONE') {
        loading.value = false
        return
      }
      try {
        ticket.value = await getMyTicket(props.conferenceId, auth.state.token as string)
        if (!ticket.value && props.seatingMode === 'SEATED') {
          const [conf, seats] = await Promise.all([
            getConference(props.conferenceId, auth.state.token as string),
            getConferenceSeatMap(props.conferenceId, auth.state.token as string)
          ])
          venueMapUrl.value = conf.venueMapBase64 || ''
          seatMap.value = seats
        }
      } catch (e: any) { /* se muestra la opción de reservar de todas formas */ }
      finally { loading.value = false }
    }

    async function reserve() {
      reserving.value = true; error.value = ''
      try {
        ticket.value = await reserveGeneral(props.conferenceId as string, auth.state.token as string)
      } catch (e: any) {
        if (e.response?.data?.error?.code === 'staff_exempt_no_ticket_needed') {
          error.value = 'Como organizador, admin o moderador ya tenés acceso garantizado -- no necesitás boleto.'
        } else if (e.response?.status === 409) {
          error.value = 'Ya no hay cupo disponible para esta conferencia.'
        } else {
          error.value = 'No se pudo reservar tu lugar. Intenta de nuevo.'
        }
      } finally {
        reserving.value = false
      }
    }

    async function reserveSelectedSeat() {
      if (!selectedSeat.value) return
      reserving.value = true; error.value = ''
      try {
        ticket.value = await reserveSeat(props.conferenceId as string, selectedSeat.value, auth.state.token as string)
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        if (code === 'staff_exempt_no_ticket_needed') {
          error.value = 'Como organizador, admin o moderador ya tenés acceso garantizado -- no necesitás boleto.'
        } else {
          error.value = e.response?.status === 409
            ? 'Ese asiento ya fue tomado, elige otro.'
            : 'No se pudo reservar el asiento. Intenta de nuevo.'
        }
        if (e.response?.status === 409 && code !== 'staff_exempt_no_ticket_needed' && props.conferenceId) {
          seatMap.value = await getConferenceSeatMap(props.conferenceId, auth.state.token as string)
          selectedSeat.value = null
        }
      } finally {
        reserving.value = false
      }
    }

    async function cancel() {
      cancelling.value = true
      try {
        await cancelReservation(props.conferenceId as string, auth.state.token as string)
        ticket.value = null
      } finally {
        cancelling.value = false
      }
    }

    onMounted(load)

    return {
      loading, ticket, reserving, cancelling, error, reserve, cancel,
      venueMapUrl, seatMap, selectedSeat, reserveSelectedSeat
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
.ticket-error { color: #dc2626; margin-top: 12px; font-size: 0.9rem; }
.ticket-seated-picker { padding: 20px 0; text-align: center; }
.ticket-seated-picker .btn-primary { margin-top: 16px; }
</style>
