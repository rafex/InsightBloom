<template lang="pug">
.tickets-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  h2 Boletos del evento
  .issue-card
    h3 Emitir boleto
    p Envía el QR por correo o copia el UUID para compartirlo directamente.
    .issue-row
      input(v-model="recipientEmail" type="email" placeholder="Correo (opcional)")
      input(v-model="seatUuid" type="text" placeholder="UUID de asiento (opcional)")
      button.btn-primary(type="button" @click="issue" :disabled="issuing") {{ issuing ? 'Emitiendo...' : 'Emitir boleto' }}
    p.feedback(v-if="feedback" :class="{ error: feedbackError }") {{ feedback }}
  .tickets-list
    h3 Boletos emitidos ({{ tickets.length }})
    .ticket-row(v-for="ticket in tickets" :key="ticket.uuid")
      .ticket-main
        strong {{ ticket.ticketCode }}
        span {{ ticket.status }} · {{ ticket.claimedByUserUuid ? 'Canjeado' : 'Sin canjear' }}
      .row-actions
        button.btn-copy(type="button" @click="showQr(ticket)") QR
        button.btn-copy(type="button" @click="copy(ticket.ticketCode)") Copiar UUID
        button.btn-revoke(v-if="ticket.status === 'ISSUED' || ticket.status === 'CLAIMED'" type="button" @click="revoke(ticket.uuid)") Revocar
    .qr-preview(v-if="selectedTicket")
      TicketQr(:ticket-code="selectedTicket.ticketCode")
      button.btn-copy(type="button" @click="share(selectedTicket)") Compartir QR
  p.empty(v-if="!loading && !tickets.length") Aún no hay boletos emitidos.
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import TicketQr from '@/components/TicketQr.vue'
import { issueTicket, listTickets, getConference, revokeTicket } from '@/services/api/usersApi'
import type { Ticket } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'TicketManagementPage',
  components: { DashboardBreadcrumb, TicketQr },
  props: { conferenceId: { type: String, default: '' } },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const tickets = ref<Ticket[]>([])
    const selectedTicket = ref<Ticket | null>(null)
    const recipientEmail = ref('')
    const seatUuid = ref('')
    const conferenceName = ref('')
    const loading = ref(true)
    const issuing = ref(false)
    const feedback = ref('')
    const feedbackError = ref(false)

    async function load() {
      if (!props.conferenceId || !auth.state.token) return
      try {
        const [conf, list] = await Promise.all([
          getConference(props.conferenceId, auth.state.token),
          listTickets(props.conferenceId, auth.state.token)
        ])
        conferenceName.value = conf.name
        tickets.value = list
      } finally { loading.value = false }
    }

    async function issue() {
      if (!props.conferenceId || !auth.state.token) return
      issuing.value = true; feedback.value = ''; feedbackError.value = false
      try {
        const ticket = await issueTicket(props.conferenceId, recipientEmail.value.trim() || null, seatUuid.value.trim() || null, auth.state.token)
        tickets.value = [ticket, ...tickets.value]
        recipientEmail.value = ''
        seatUuid.value = ''
        feedback.value = `Boleto emitido: ${ticket.ticketCode}`
      } catch (e: any) {
        feedbackError.value = true
        feedback.value = e.response?.data?.error?.detail || 'No se pudo emitir el boleto.'
      } finally { issuing.value = false }
    }

    async function copy(code: string) {
      await navigator.clipboard?.writeText(code)
      feedbackError.value = false; feedback.value = 'UUID copiado.'
    }

    function showQr(ticket: Ticket) { selectedTicket.value = ticket }

    async function share(ticket: Ticket) {
      const url = `${location.origin}/c/${props.conferenceId}/ticket?ticket=${ticket.ticketCode}`
      if (navigator.share) await navigator.share({ title: 'Boleto del evento', text: ticket.ticketCode, url })
      else await navigator.clipboard?.writeText(url)
      feedback.value = 'Enlace del boleto listo para compartir.'; feedbackError.value = false
    }

    async function revoke(uuid: string) {
      if (!props.conferenceId || !auth.state.token) return
      const updated = await revokeTicket(props.conferenceId, uuid, auth.state.token)
      const index = tickets.value.findIndex(t => t.uuid === uuid)
      if (index >= 0) tickets.value[index] = updated
      feedback.value = 'Boleto revocado.'; feedbackError.value = false
    }

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Boletos' }
    ])

    onMounted(load)
    return { tickets, recipientEmail, seatUuid, selectedTicket, issuing, loading, feedback, feedbackError, issue, copy, showQr, share, revoke, breadcrumbItems }
  }
}
</script>

<style scoped>
.tickets-page { padding: 24px; max-width: 900px; margin: 0 auto; }
h2 { color: #1e1b4b; }
.issue-card, .tickets-list { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; margin-top: 16px; }
.issue-row { display: flex; gap: 10px; flex-wrap: wrap; }
input { flex: 1; min-width: 240px; padding: 10px; border: 1px solid #d1d5db; border-radius: 8px; }
.btn-primary, .btn-copy { padding: 10px 16px; border-radius: 8px; cursor: pointer; font-weight: 600; }
.btn-primary { border: 0; background: #4f46e5; color: white; }
.btn-copy { border: 1px solid #c7d2fe; background: #eef2ff; color: #4338ca; }
.btn-revoke { border: 1px solid #fecaca; background: #fef2f2; color: #b91c1c; padding: 10px 16px; border-radius: 8px; cursor: pointer; }
.row-actions { display: flex; gap: 8px; }
.qr-preview { margin: 18px 0; padding: 18px; border: 1px dashed #c7d2fe; border-radius: 10px; display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.ticket-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid #f3f4f6; }
.ticket-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.ticket-main strong { font: 0.8rem monospace; overflow-wrap: anywhere; }
.ticket-main span, .empty, .issue-card p { color: #6b7280; font-size: 0.9rem; }
.feedback { margin: 12px 0 0; color: #166534; }
.feedback.error { color: #b91c1c; }
</style>
