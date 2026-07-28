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
      BaseButton(variant="primary" type="button" :loading="issuing" @click="issue") {{ issuing ? 'Emitiendo...' : 'Emitir boleto' }}
    p.feedback(v-if="feedback" :class="{ error: feedbackError }") {{ feedback }}
    template(v-if="canIssueBatch")
      .issue-divider o
      .issue-row
        input(v-model.number="batchQuantity" type="number" min="2" max="200" placeholder="Cantidad")
        BaseButton(variant="secondary" type="button" :disabled="issuingBatch || !batchQuantity || batchQuantity < 2" @click="issueBatch") {{ issuingBatch ? 'Emitiendo...' : `Emitir ${batchQuantity || ''} boletos anónimos` }}
      p.field-hint Genera varios boletos sin destinatario de una sola vez. Podés compartir el QR/UUID de cada uno a mano con invitados puntuales; los que no repartas quedan disponibles igual para que cualquiera los reclame solo desde la cartelera pública (botón "Adquirir boleto"), hasta agotarse. No exceden el aforo restante: si pedís más de lo que queda, no se emite ninguno.
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
    .list-header
      h3 Boletos emitidos ({{ tickets.length }})
      BaseButton(variant="secondary" type="button" :loading="resendingAll" :disabled="!tickets.length" @click="resendAll") {{ resendingAll ? 'Reenviando...' : 'Reenviar todos por correo' }}
    p.helper Los boletos operativos pertenecen al creador y al personal asignado al evento. Consumen aforo y no se pueden revocar.
    p.helper Reenviar por correo busca primero el destinatario con el que se emitió el boleto y, si no hay, el correo de la cuenta que lo reclamó. Boletos sin ningún correo asociado no se pueden reenviar.
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
          BaseButton(variant="ghost" size="sm" type="button" @click="showQr(ticket)") QR
          BaseButton(variant="ghost" size="sm" type="button" @click="copy(ticket.ticketCode)") Copiar UUID
          BaseButton(variant="ghost" size="sm" type="button" :loading="resendingUuid === ticket.uuid" :disabled="resendingUuid === ticket.uuid" @click="resendOne(ticket)") {{ resendingUuid === ticket.uuid ? 'Enviando...' : 'Reenviar' }}
          BaseButton(variant="danger" size="sm" type="button" @click="revoke(ticket.uuid)") Revocar
    .qr-preview(v-if="selectedTicket")
      TicketQr(:ticket-code="selectedTicket.ticketCode" :ticket-url="ticketUrl(selectedTicket)" :show-code="false")
      BaseButton(variant="ghost" size="sm" type="button" @click="share(selectedTicket)") Compartir QR
  p.empty(v-if="!loading && !tickets.length") Aún no hay boletos emitidos.
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import TicketQr from '@/components/TicketQr.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { issueTicket, issueTicketBatch, listTickets, getConference, revokeTicket, resendTicket, resendAllTickets } from '@/services/api/usersApi'
import type { Ticket, TicketManagementSummary, TicketStatus } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'TicketManagementPage',
  components: { DashboardBreadcrumb, TicketQr, BaseButton },
  props: { conferenceId: { type: String, default: '' } },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const tickets = ref<Ticket[]>([])
    const summary = ref<TicketManagementSummary | null>(null)
    const selectedTicket = ref<Ticket | null>(null)
    const recipientEmail = ref('')
    const seatUuid = ref('')
    const conferenceName = ref('')
    const conferenceFriendlyId = ref('')
    const seatingMode = ref('')
    const loading = ref(true)
    const issuing = ref(false)
    const feedback = ref('')
    const feedbackError = ref(false)
    const batchQuantity = ref<number | null>(10)
    const issuingBatch = ref(false)
    const resendingUuid = ref<string | null>(null)
    const resendingAll = ref(false)
    const canIssueBatch = computed(() => seatingMode.value !== 'SEATED')

    async function load() {
      if (!props.conferenceId || !auth.state.token) return
      try {
        const [conf, list] = await Promise.all([
          getConference(props.conferenceId, auth.state.token),
          listTickets(props.conferenceId, auth.state.token)
        ])
        conferenceName.value = conf.name
        conferenceFriendlyId.value = conf.friendlyId
        seatingMode.value = conf.seatingMode || ''
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

    async function issueBatch() {
      if (!props.conferenceId || !auth.state.token || !batchQuantity.value) return
      issuingBatch.value = true; feedback.value = ''; feedbackError.value = false
      try {
        const issued = await issueTicketBatch(props.conferenceId, batchQuantity.value, auth.state.token)
        await load()
        feedback.value = `${issued.length} boletos emitidos.`
      } catch (e: any) {
        feedbackError.value = true
        const apiError = e.response?.data?.error || {}
        const messages: Record<string, string> = {
          capacity_exceeded: 'Esa cantidad excede el aforo restante del evento. No se emitió ningún boleto.',
          capability_not_available: 'Este evento no tiene habilitada la emisión de boletos.',
          seat_required: 'Este evento usa asientos; emite los boletos de a uno con su UUID de asiento.',
          conference_expired: 'El evento ya terminó y no admite nuevos boletos.',
          quantity_invalid: 'La cantidad debe ser un número entre 2 y 200.'
        }
        feedback.value = messages[apiError.code] || apiError.detail || 'No se pudieron emitir los boletos.'
      } finally { issuingBatch.value = false }
    }

    async function copy(code: string) {
      await navigator.clipboard?.writeText(code)
      feedbackError.value = false; feedback.value = 'UUID copiado.'
    }

    function showQr(ticket: Ticket) { selectedTicket.value = ticket }

    async function share(ticket: Ticket) {
      const url = ticketUrl(ticket)
      if (navigator.share) await navigator.share({ title: 'Boleto del evento', text: ticket.ticketCode, url })
      else await navigator.clipboard?.writeText(url)
      feedback.value = 'Enlace del boleto listo para compartir.'; feedbackError.value = false
    }

    function ticketUrl(ticket: Ticket) {
      const friendlyId = conferenceFriendlyId.value || props.conferenceId || ''
      return `${location.origin}/c/${encodeURIComponent(friendlyId)}/ticket?t=${encodeURIComponent(ticket.ticketCode)}`
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

    async function resendOne(ticket: Ticket) {
      if (!props.conferenceId || !auth.state.token) return
      resendingUuid.value = ticket.uuid
      try {
        await resendTicket(props.conferenceId, ticket.uuid, auth.state.token)
        feedback.value = `Boleto ${ticket.ticketCode} reenviado por correo.`; feedbackError.value = false
      } catch (e: any) {
        feedbackError.value = true
        const apiError = e.response?.data?.error || {}
        const messages: Record<string, string> = {
          no_email_available: 'Este boleto no tiene un correo asociado (ni de emisión ni de la cuenta que lo reclamó).',
          email_provider_not_configured: 'El envío de correo no está configurado en la plataforma.',
          ticket_revoked: 'El boleto fue revocado y no se puede reenviar.',
          ticket_expired: 'El boleto expiró y no se puede reenviar.'
        }
        feedback.value = messages[apiError.code] || apiError.detail || 'No se pudo reenviar el boleto.'
      } finally { resendingUuid.value = null }
    }

    async function resendAll() {
      if (!props.conferenceId || !auth.state.token) return
      resendingAll.value = true
      try {
        const result = await resendAllTickets(props.conferenceId, auth.state.token)
        feedback.value = `${result.sent} boletos reenviados por correo${result.skipped ? `, ${result.skipped} sin correo asociado o no reenviables` : ''}.`
        feedbackError.value = false
      } catch (e: any) {
        feedbackError.value = true
        feedback.value = e.response?.data?.error?.detail || 'No se pudieron reenviar los boletos.'
      } finally { resendingAll.value = false }
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
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Boletos' }
    ])

    onMounted(load)
    return { tickets, summary, statusMetrics, ticketGroups, recipientEmail, seatUuid, selectedTicket, issuing, loading, feedback, feedbackError, issue, copy, showQr, share, revoke, ticketUrl, formatAuditDate, statusLabel, claimantLabel, breadcrumbItems,
      batchQuantity, issuingBatch, canIssueBatch, issueBatch, resendingUuid, resendingAll, resendOne, resendAll }
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
.issue-divider { text-align: center; color: var(--color-text-muted); font-size: .78rem; margin: 12px 0; text-transform: uppercase; letter-spacing: .04em; }
.field-hint { margin: 8px 0 0; font-size: .8rem; color: var(--color-text-muted); }
.row-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.ticket-row { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid #f3f4f6; }
.ticket-main strong { font: 0.8rem monospace; overflow-wrap: anywhere; }
.ticket-main span, .empty, .issue-card p { color: #6b7280; font-size: 0.9rem; }
.list-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.list-header h3 { margin: 0; }
.ticket-group { margin-top: 18px; }
.ticket-group h4 { margin: 0 0 8px; color: #3730a3; }
.claimant { color: #166534 !important; overflow-wrap: anywhere; }
.audit-line { color: #b91c1c !important; overflow-wrap: anywhere; }
.feedback { margin: 12px 0 0; color: #166534; }
.feedback.error { color: #b91c1c; }
</style>
