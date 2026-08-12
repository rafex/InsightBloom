<template lang="pug">
.tickets-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  h2 Boletos del evento
  .issue-card
    h3 Emitir boleto
    p Envía el QR por correo o copia el UUID para compartirlo directamente.
    .issue-row
      input(v-model="recipientEmail" type="email" aria-label="Correo del destinatario" placeholder="Correo (opcional)")
      input(v-model="seatUuid" type="text" aria-label="UUID del asiento" placeholder="UUID de asiento (opcional)")
      BaseButton(variant="primary" type="button" :loading="issuing" @click="issue") Emitir boleto
    FeedbackMessage(v-if="feedback" :message="feedback" :tone="feedbackError ? 'error' : 'success'")
    template(v-if="canIssueBatch")
      .issue-divider o
      .issue-row
        input(v-model.number="batchQuantity" type="number" min="2" max="200" aria-label="Cantidad de boletos anónimos" placeholder="Cantidad")
        BaseButton(variant="secondary" type="button" :loading="issuingBatch" :disabled="!batchQuantity || batchQuantity < 2" @click="issueBatch") Emitir {{ batchQuantity || '' }} boletos anónimos
      p.field-hint Genera varios boletos sin destinatario de una sola vez. Podés compartir el QR/UUID de cada uno a mano con invitados puntuales; los que no repartas quedan disponibles igual para que cualquiera los reclame solo desde la cartelera pública (botón "Adquirir boleto"), hasta agotarse. No exceden el aforo restante: si pedís más de lo que queda, no se emite ninguno.
  .compose-card#compose-card
    h3 Comunicarse con inscritos
    p.helper(v-if="!composeTarget") Para: todos los inscritos
    p.helper(v-else) Para: {{ composeTarget.label }} #[a.link-inline(href="#" @click.prevent="clearComposeTarget") (enviar a todos en su lugar)]
    input(v-model="composeSubject" type="text" aria-label="Asunto" placeholder="Asunto")
    AiEmailAssistant(:conference-id="conferenceId" :visible="showAiAssistant" @close="showAiAssistant = false" @use-draft="draft => composeMessage = draft")
    EmailComposeEditor(v-model="composeMessage" v-model:format="composeFormat" v-model:show-ai-assistant="showAiAssistant")
    BaseButton(variant="primary" type="button" :loading="sendingEmail" :disabled="!composeSubject.trim() || !composeMessage.trim()" @click="sendEmail") Enviar
    FeedbackMessage(v-if="emailFeedback" :message="emailFeedback" :tone="emailFeedbackError ? 'error' : 'success'")
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
  LoadingState(v-if="loading" message="Cargando boletos…")
  .tickets-list(v-else)
    .list-header
      h3 Boletos emitidos ({{ tickets.length }})
      BaseButton(variant="secondary" type="button" :loading="resendingAll" :disabled="!tickets.length" @click="resendAll") Reenviar todos por correo
    p.helper Los boletos operativos pertenecen al creador y al personal asignado al evento. Consumen aforo y no se pueden revocar.
    p.helper Reenviar por correo busca primero el destinatario con el que se emitió el boleto y, si no hay, el correo de la cuenta que lo reclamó. Boletos sin ningún correo asociado no se pueden reenviar.
    .ticket-group(v-for="group in ticketGroups" :key="group.key" v-show="group.tickets.length")
      h4 {{ group.label }} ({{ group.tickets.length }})
      .ticket-row(v-for="ticket in group.tickets" :key="ticket.uuid")
        .ticket-main
          strong {{ ticket.ticketCode }}
          StatusBadge(v-if="!ticket.operational" :status="ticket.status" :label="formatTicketStatusLabel(ticket.status)")
          StatusBadge(v-if="ticket.operational" status="ACTIVE" label="Operativo · no revocable")
          span.claimant(v-if="!ticket.operational && ticket.claimedByUserUuid")
            | Reclamado por:
            span.audit-actor(:title="actorUuid(ticket.claimedByUserUuid)") {{ claimantLabel(ticket) }}
          StatusBadge(v-if="!ticket.operational && !ticket.claimedByUserUuid" status="PENDING" label="Sin reclamar")
          span.audit-line(v-if="ticket.status === 'REVOKED'")
            | Revocado por:
            span.audit-actor(:title="actorUuid(ticket.revokedByUserUuid)") {{ actorLabel(ticket.revokedByUserUuid) }}
            |  · {{ formatAuditDate(ticket.revokedAt) }}
        .row-actions
          BaseButton(variant="ghost" size="sm" type="button" @click="showQr(ticket)") QR
          BaseButton(variant="ghost" size="sm" type="button" @click="copy(ticket.ticketCode)") Copiar UUID
          BaseButton(variant="ghost" size="sm" type="button" :loading="resendingUuid === ticket.uuid" :disabled="resendingUuid === ticket.uuid" @click="resendOne(ticket)") Reenviar
          BaseButton(variant="ghost" size="sm" type="button" v-if="ticket.claimedByUserUuid" @click="writeToAttendee(ticket)") Escribir
          BaseButton(variant="danger" size="sm" type="button" @click="revoke(ticket.uuid)") Revocar
    .qr-preview(v-if="selectedTicket")
      TicketQr(:ticket-code="selectedTicket.ticketCode" :ticket-url="ticketUrl(selectedTicket)" :show-code="false")
      BaseButton(variant="ghost" size="sm" type="button" @click="share(selectedTicket)") Compartir QR
  EmptyState(v-if="!loading && !tickets.length" message="Aún no hay boletos emitidos.")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Marked } from 'marked'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import TicketQr from '@/components/TicketQr.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmailComposeEditor from '@/components/EmailComposeEditor.vue'
import AiEmailAssistant from '@/components/AiEmailAssistant.vue'
import { issueTicket, issueTicketBatch, listTickets, getConference, revokeTicket, resendTicket, resendAllTickets, sendAttendeeEmail } from '@/services/api/usersApi'
import type { Ticket, TicketManagementSummary, TicketStatus } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { formatTicketStatusLabel } from '@/utils/status'

const sendMarked = new Marked()

export default {
  name: 'TicketManagementPage',
  components: { DashboardBreadcrumb, TicketQr, BaseButton, EmptyState, FeedbackMessage, LoadingState, StatusBadge, EmailComposeEditor, AiEmailAssistant },
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
    const composeSubject = ref('')
    const composeMessage = ref('')
    const composeFormat = ref<'markdown' | 'html' | 'text'>('markdown')
    const showAiAssistant = ref(false)
    const composeTarget = ref<{ uuid: string, label: string } | null>(null)
    const sendingEmail = ref(false)
    const emailFeedback = ref('')
    const emailFeedbackError = ref(false)
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

    function writeToAttendee(ticket: Ticket) {
      composeTarget.value = { uuid: ticket.claimedByUserUuid as string, label: claimantLabel(ticket) }
      document.getElementById('compose-card')?.scrollIntoView({ behavior: 'smooth' })
    }

    function clearComposeTarget() {
      composeTarget.value = null
    }

    async function sendEmail() {
      if (!props.conferenceId || !auth.state.token) return
      sendingEmail.value = true
      try {
        let processedMessage = composeMessage.value.trim()
        if (composeFormat.value === 'markdown') {
          processedMessage = sendMarked.parse(processedMessage, { async: false }) as string
        }
        const result = await sendAttendeeEmail(props.conferenceId, {
          subject: composeSubject.value.trim(),
          message: processedMessage,
          format: 'html',
          recipientUuids: composeTarget.value ? [composeTarget.value.uuid] : undefined
        }, auth.state.token)
        emailFeedback.value = `${result.sent} correo(s) enviado(s)${result.skipped ? `, ${result.skipped} sin entregar` : ''}.`
        emailFeedbackError.value = false
        composeSubject.value = ''
        composeMessage.value = ''
        composeFormat.value = 'markdown'
      } catch (e: any) {
        emailFeedbackError.value = true
        const apiError = e.response?.data?.error || {}
        const messages: Record<string, string> = {
          email_provider_not_configured: 'El envío de correo no está configurado en la plataforma.',
          no_recipients: 'No hay destinatarios válidos para este envío.',
          subject_invalid: 'El asunto es obligatorio.',
          message_invalid: 'El mensaje es obligatorio.'
        }
        emailFeedback.value = messages[apiError.code] || apiError.detail || 'No se pudo enviar el correo.'
      } finally { sendingEmail.value = false }
    }

    function formatAuditDate(value?: string | null) {
      if (!value) return 'fecha no disponible'
      return new Date(value).toLocaleString()
    }

    const actorLabel = (uuid?: string | null) => {
      if (!uuid) return 'usuario no disponible'
      const user = summary.value?.ticketActors?.[uuid]
      if (!user) return 'usuario no disponible'
      return user.displayName || user.username || user.email || user.uuid
    }

    const actorUuid = (uuid?: string | null) => uuid ? `UUID: ${uuid}` : 'UUID no disponible'
    const claimantLabel = (ticket: Ticket) => actorLabel(ticket.claimedByUserUuid)

    const normalTickets = (status: TicketStatus) => tickets.value.filter(ticket => !ticket.operational && ticket.status === status)
    const operationalTickets = computed(() => tickets.value.filter(ticket => Boolean(ticket.operational)))
    const statusMetrics = computed(() => [
      { key: 'operational', label: 'Operativos', description: 'Creador y personal asignado; no revocables.', count: operationalTickets.value.length },
      { key: 'issued', label: 'Emitidos', description: 'Emitidos y todavía sin reclamar.', count: normalTickets('ISSUED').length },
      { key: 'claimed', label: 'Reclamados', description: 'Reclamados por un usuario.', count: normalTickets('CLAIMED').length },
      { key: 'checked-in', label: 'Registrados en check-in', description: 'Usuarios registrados en check-in.', count: normalTickets('CHECKED_IN').length },
      { key: 'revoked', label: 'Revocados', description: 'Revocados; liberan una plaza.', count: normalTickets('REVOKED').length },
      { key: 'expired', label: 'Expirados', description: 'Expirados por fecha del evento.', count: normalTickets('EXPIRED').length }
    ])
    const ticketGroups = computed(() => [
      { key: 'operational', label: 'Boletos operativos', tickets: operationalTickets.value },
      { key: 'ISSUED', label: 'Emitidos · sin reclamar', tickets: normalTickets('ISSUED') },
      { key: 'CLAIMED', label: 'Reclamados', tickets: normalTickets('CLAIMED') },
      { key: 'CHECKED_IN', label: 'Registrados en check-in', tickets: normalTickets('CHECKED_IN') },
      { key: 'REVOKED', label: 'Revocados', tickets: normalTickets('REVOKED') },
      { key: 'EXPIRED', label: 'Expirados', tickets: normalTickets('EXPIRED') }
    ])

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/events' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Boletos' }
    ])

    onMounted(load)
    return { tickets, summary, statusMetrics, ticketGroups, recipientEmail, seatUuid, selectedTicket, issuing, loading, feedback, feedbackError, issue, copy, showQr, share, revoke, ticketUrl, formatAuditDate, formatTicketStatusLabel, claimantLabel, actorLabel, actorUuid, breadcrumbItems,
      batchQuantity, issuingBatch, canIssueBatch, issueBatch, resendingUuid, resendingAll, resendOne, resendAll,
      composeSubject, composeMessage, composeFormat, showAiAssistant,
      composeTarget, sendingEmail, emailFeedback, emailFeedbackError, sendEmail, writeToAttendee, clearComposeTarget }
  }
}
</script>

<style scoped>
.tickets-page { padding: 24px; max-width: 900px; margin: 0 auto; }
h2 { color: var(--color-heading); }
.issue-card, .tickets-list, .compose-card { background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; margin-top: 16px; }
.compose-card textarea { width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid var(--color-border); border-radius: 8px; font: inherit; resize: vertical; margin: 10px 0; }
.compose-card input { width: 100%; box-sizing: border-box; margin-bottom: 0; }
.link-inline { color: var(--color-primary); font-size: 0.8rem; }
.metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-top: 16px; }
.metric-card { display: flex; flex-direction: column; gap: 4px; background: var(--color-surface); border: 1px solid var(--color-primary-soft); border-radius: 12px; padding: 16px; min-height: 92px; }
.metric-card strong { color: var(--color-primary-dark); font-size: 1.65rem; }
.metric-card span { color: var(--color-text); font-weight: 700; }
.metric-card small, .helper { color: var(--color-text-muted); font-size: .78rem; }
.metric-capacity { border-color: var(--color-primary-border); }
.issue-row { display: flex; gap: 10px; flex-wrap: wrap; }
input { flex: 1; min-width: 240px; padding: 10px; border: 1px solid var(--color-border); border-radius: 8px; }
.issue-divider { text-align: center; color: var(--color-text-muted); font-size: .78rem; margin: 12px 0; text-transform: uppercase; letter-spacing: .04em; }
.field-hint { margin: 8px 0 0; font-size: .8rem; }
.row-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.ticket-row { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--color-surface-muted); }
.ticket-main { display: flex; flex-direction: column; gap: 2px; }
.ticket-main strong { font: 0.8rem monospace; overflow-wrap: anywhere; }
.ticket-main > span:not(.status-badge), .issue-card p { color: var(--color-text-muted); font-size: 0.9rem; }
.list-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.list-header h3 { margin: 0; }
.ticket-group { margin-top: 18px; }
.ticket-group h4 { margin: 0 0 8px; color: var(--color-primary-dark); }
.claimant { color: var(--color-success) !important; overflow-wrap: anywhere; }
.audit-line { color: var(--color-danger-dark) !important; overflow-wrap: anywhere; }
.audit-actor { text-decoration: underline dotted; text-underline-offset: 2px; cursor: help; }
.feedback { margin: 12px 0 0; color: var(--color-success); }
.feedback.error { color: var(--color-danger-dark); }

@media (max-width: 640px) {
  .tickets-page { padding: 14px; }
  .issue-card, .tickets-list, .compose-card { padding: 14px; }
  .issue-row { flex-direction: column; align-items: stretch; }
  .issue-row input { width: 100%; min-width: 0; }
  .issue-row :deep(.base-btn) { width: 100%; }
  .ticket-row { flex-direction: column; align-items: stretch; }
  .ticket-main { width: 100%; min-width: 0; }
  .row-actions { width: 100%; justify-content: flex-start; gap: 6px; }
  .row-actions :deep(.base-btn) { flex: 1 1 calc(50% - 6px); min-width: 0; }
  .list-header :deep(.base-btn) { width: 100%; }
}
</style>
