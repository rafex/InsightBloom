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
  .metrics-grid(v-if="summary")
    .metric-card.metric-capacity
      strong {{ summary.remainingToIssue == null ? '∞' : summary.remainingToIssue }}
      span Boletos pendientes de emitir
      small(v-if="summary.capacity != null") {{ summary.reservedCount }} de {{ summary.capacity }} plazas ocupadas (incluye operativos)
      small(v-else) Este evento no tiene un aforo máximo configurado.
    .metric-card(v-for="metric in statusMetrics" :key="metric.key")
      strong {{ metric.count }}
      span {{ metric.label }}
      small {{ metric.description }}
  .tickets-list
    h3 Boletos emitidos ({{ tickets.length }})
    p.helper Los boletos operativos pertenecen al creador y al personal asignado al evento. Consumen aforo y no se pueden revocar.
    .ticket-group(v-for="group in ticketGroups" :key="group.key" v-show="group.tickets.length")
      h4 {{ group.label }} ({{ group.tickets.length }})
      .ticket-row(v-for="ticket in group.tickets" :key="ticket.uuid")
        .ticket-main
          strong {{ ticket.ticketCode }}
          span.status-line {{ ticket.operational ? 'Operativo · no revocable' : statusLabel(ticket.status) }}
          span.claimant(v-if="ticket.claimedByUserUuid") Reclamado por: {{ claimantLabel(ticket) }}
          span.status-line(v-else) Sin reclamar
          span.audit-line(v-if="ticket.status === 'REVOKED'") Revocado por: {{ ticket.revokedByUserUuid || 'desconocido' }} · {{ formatAuditDate(ticket.revokedAt) }}
        .row-actions
          button.btn-copy(type="button" @click="showQr(ticket)") QR
          button.btn-copy(type="button" @click="copy(ticket.ticketCode)") Copiar UUID
          button.btn-revoke(v-if="!ticket.operational && (ticket.status === 'ISSUED' || ticket.status === 'CLAIMED')" type="button" @click="revoke(ticket.uuid)") Revocar
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
import type { Ticket, TicketManagementSummary, TicketStatus } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'TicketManagementPage',
  components: { DashboardBreadcrumb, TicketQr },
  props: { conferenceId: { type: String, default: '' } },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const tickets = ref<Ticket[]>([])
    const summary = ref<TicketManagementSummary | null>(null)
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
        summary.value = list
        tickets.value = list.tickets
      } finally { loading.value = false }
    }

    async function issue() {
      if (!props.conferenceId || !auth.state.token) return
      issuing.value = true; feedback.value = ''; feedbackError.value = false
      try {
        const ticket = await issueTicket(props.conferenceId, recipientEmail.value.trim() || null, seatUuid.value.trim() || null, auth.state.token)
        await load()
        recipientEmail.value = ''
        seatUuid.value = ''
        feedback.value = `Boleto emitido: ${ticket.ticketCode}`
      } catch (e: any) {
        feedbackError.value = true
        const apiError = e.response?.data?.error || {}
        const messages: Record<string, string> = {
          capacity_exceeded: 'Se alcanzó el límite de boletos del evento. Aumenta el aforo o libera una plaza disponible.',
          capability_not_available: 'Este evento no tiene habilitada la emisión de boletos.',
          seat_required: 'Debes seleccionar un asiento para este evento.',
          seat_not_allowed: 'Este evento no utiliza asientos; elimina el UUID de asiento.',
          conference_expired: 'El evento ya terminó y no admite nuevos boletos.'
        }
        feedback.value = messages[apiError.code] || apiError.detail || 'No se pudo emitir el boleto.'
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
      try {
        await revokeTicket(props.conferenceId, uuid, auth.state.token)
        await load()
        feedback.value = 'Boleto revocado.'; feedbackError.value = false
      } catch (e: any) {
        feedbackError.value = true
        feedback.value = e.response?.data?.error?.detail || 'No se pudo revocar el boleto.'
      }
    }

    function formatAuditDate(value?: string | null) {
      if (!value) return 'fecha no disponible'
      return new Date(value).toLocaleString()
    }

    const statusLabel = (status: TicketStatus) => ({
      ISSUED: 'ISSUED · emitido, sin reclamar',
      CLAIMED: 'CLAIMED · reclamado',
      CHECKED_IN: 'CHECKED_IN · registrado en check-in',
      REVOKED: 'REVOKED · revocado',
      EXPIRED: 'EXPIRED · expirado'
    }[status] || status)

    const claimantLabel = (ticket: Ticket) => {
      const user = summary.value?.claimedUsers?.[ticket.claimedByUserUuid || '']
      if (!user) return ticket.claimedByUserUuid || 'usuario no disponible'
      return user.displayName || user.username || user.email || user.uuid
    }

    const normalTickets = (status: TicketStatus) => tickets.value.filter(ticket => !ticket.operational && ticket.status === status)
    const operationalTickets = computed(() => tickets.value.filter(ticket => Boolean(ticket.operational)))
    const statusMetrics = computed(() => [
      { key: 'operational', label: 'Operativos', description: 'Creador y personal asignado; no revocables.', count: operationalTickets.value.length },
      { key: 'issued', label: 'ISSUED', description: 'Emitidos y todavía sin reclamar.', count: normalTickets('ISSUED').length },
      { key: 'claimed', label: 'CLAIMED', description: 'Reclamados por un usuario.', count: normalTickets('CLAIMED').length },
      { key: 'checked-in', label: 'CHECKED_IN', description: 'Usuarios registrados en check-in.', count: normalTickets('CHECKED_IN').length },
      { key: 'revoked', label: 'REVOKED', description: 'Revocados; liberan una plaza.', count: normalTickets('REVOKED').length },
      { key: 'expired', label: 'EXPIRED', description: 'Expirados por fecha del evento.', count: normalTickets('EXPIRED').length }
    ])
    const ticketGroups = computed(() => [
      { key: 'operational', label: 'Boletos operativos', tickets: operationalTickets.value },
      { key: 'ISSUED', label: 'ISSUED · sin reclamar', tickets: normalTickets('ISSUED') },
      { key: 'CLAIMED', label: 'CLAIMED · reclamados', tickets: normalTickets('CLAIMED') },
      { key: 'CHECKED_IN', label: 'CHECKED_IN · registrados', tickets: normalTickets('CHECKED_IN') },
      { key: 'REVOKED', label: 'REVOKED · revocados', tickets: normalTickets('REVOKED') },
      { key: 'EXPIRED', label: 'EXPIRED · expirados', tickets: normalTickets('EXPIRED') }
    ])

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Boletos' }
    ])

    onMounted(load)
    return { tickets, summary, statusMetrics, ticketGroups, recipientEmail, seatUuid, selectedTicket, issuing, loading, feedback, feedbackError, issue, copy, showQr, share, revoke, formatAuditDate, statusLabel, claimantLabel, breadcrumbItems }
  }
}
</script>

<style scoped>
.tickets-page { padding: 24px; max-width: 900px; margin: 0 auto; }
h2 { color: #1e1b4b; }
.issue-card, .tickets-list { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; margin-top: 16px; }
.metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-top: 16px; }
.metric-card { display: flex; flex-direction: column; gap: 4px; background: #fff; border: 1px solid #e0e7ff; border-radius: 12px; padding: 16px; min-height: 92px; }
.metric-card strong { color: #4338ca; font-size: 1.65rem; }
.metric-card span { color: #1f2937; font-weight: 700; }
.metric-card small, .helper { color: #6b7280; font-size: .78rem; }
.metric-capacity { border-color: #a5b4fc; }
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
.ticket-group { margin-top: 18px; }
.ticket-group h4 { margin: 0 0 8px; color: #3730a3; }
.claimant { color: #166534 !important; overflow-wrap: anywhere; }
.audit-line { color: #b91c1c !important; overflow-wrap: anywhere; }
.feedback { margin: 12px 0 0; color: #166534; }
.feedback.error { color: #b91c1c; }
</style>
