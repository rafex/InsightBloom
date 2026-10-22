<template lang="pug">
.invitation-admin-page
  DashboardBreadcrumb(:items="[{ label: 'Plataforma' }, { label: 'Invitaciones beta' }]")
  h1 Invitaciones para organizadores beta
  p.page-hint Las invitaciones habilitan la creación de eventos. Las recomendaciones quedan pendientes hasta que un administrador las apruebe.

  .tabs(role="tablist" aria-label="Administrar invitaciones beta")
    BaseButton(:variant="tab === 'invites' ? 'primary' : 'secondary'" role="tab" :aria-selected="tab === 'invites'" @click="tab = 'invites'") Invitaciones
    BaseButton(:variant="tab === 'referrals' ? 'primary' : 'secondary'" role="tab" :aria-selected="tab === 'referrals'" @click="tab = 'referrals'") Recomendaciones
    BaseButton(:variant="tab === 'settings' ? 'primary' : 'secondary'" role="tab" :aria-selected="tab === 'settings'" @click="tab = 'settings'") Reglas de la beta

  FeedbackMessage(v-if="message" :message="message" :tone="messageTone")
  LoadingState(v-if="loading" message="Cargando datos de invitaciones…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")

  section(v-else-if="tab === 'invites'" aria-labelledby="invites-title")
    .section-heading
      div
        h2#invites-title Invitaciones enviadas
        p.page-hint El enlace se liga al correo y solo puede aceptarse una vez.
      label.field
        span Estado
        select(v-model="inviteFilter" @change="loadInvites")
          option(value="") Todos
          option(value="PENDING") Pendientes
          option(value="DELIVERY_FAILED") Error de envío
          option(value="ACCEPTED") Aceptadas
          option(value="EXPIRED") Vencidas
          option(value="REVOKED") Revocadas
    form.create-form(@submit.prevent="createInvite")
      label.field(for="invite-email")
        span Correo de la persona invitada
        input#invite-email(v-model.trim="inviteEmail" type="email" required maxlength="254" autocomplete="email" placeholder="persona@ejemplo.com")
      BaseButton(type="submit" :loading="saving" :disabled="saving") Enviar invitación
    p.help-text El correo debe coincidir con el que verificará al aceptar.
    .table-scroll(v-if="invites.length")
      table.data-table
        thead
          tr
            th Correo
            th Estado
            th Vence
            th Enviada
            th Acciones
        tbody
          tr(v-for="invite in invites" :key="invite.uuid")
            td(data-label="Correo") {{ invite.email }}
            td(data-label="Estado")
              StatusBadge(:status="invite.status" :label="statusLabel(invite.status)")
              small.block(v-if="invite.deliveryError") {{ invite.deliveryError === 'email_delivery_failed' ? 'No se pudo entregar el correo.' : 'Proveedor de correo no disponible.' }}
            td(data-label="Vence") {{ dateLabel(invite.expiresAt) }}
            td(data-label="Enviada") {{ invite.sentAt ? dateLabel(invite.sentAt) : 'No confirmada' }}
            td.actions(data-label="Acciones")
              BaseButton(v-if="invite.status === 'PENDING' || invite.status === 'DELIVERY_FAILED' || invite.status === 'EXPIRED'" variant="secondary" size="sm" :disabled="saving" @click="resend(invite)") Reenviar
              BaseButton(v-if="invite.status === 'PENDING' || invite.status === 'DELIVERY_FAILED'" variant="danger" size="sm" :disabled="saving" @click="revoke(invite)") Revocar
    EmptyState(v-else message="Aún no hay invitaciones para este filtro.")

  section(v-else-if="tab === 'referrals'" aria-labelledby="referrals-title")
    h2#referrals-title Recomendaciones pendientes
    p.page-hint La aprobación envía una invitación al correo recomendado. Rechazar no notifica al emisor ni al destinatario.
    .table-scroll(v-if="referrals.length")
      table.data-table
        thead
          tr
            th Correo recomendado
            th Emisor
            th Recibida
            th Decisión
        tbody
          tr(v-for="referral in referrals" :key="referral.uuid")
            td(data-label="Correo recomendado") {{ referral.email }}
            td(data-label="Emisor") {{ referral.inviterUuid }}
            td(data-label="Recibida") {{ dateLabel(referral.createdAt) }}
            td.actions(data-label="Decisión")
              BaseButton(size="sm" :disabled="saving" @click="approve(referral)") Aprobar y enviar
              BaseButton(variant="danger" size="sm" :disabled="saving" @click="reject(referral)") Rechazar
    EmptyState(v-else message="No hay recomendaciones pendientes.")
    h2.referrers-title Organizadores habilitados para recomendar
    p.page-hint Solo se pueden habilitar cuentas de organizador directas; las personas aceptadas desde una recomendación no pueden recomendar a otras.
    .table-scroll(v-if="organizers.some(candidate => eligibleReferrers.has(candidate.uuid))")
      table.data-table
        thead
          tr
            th Organizador
            th Correo
            th Puede recomendar
        tbody
          tr(v-for="user in organizers.filter(candidate => eligibleReferrers.has(candidate.uuid))" :key="user.uuid")
            td(data-label="Organizador") {{ user.displayName || user.username }}
            td(data-label="Correo") {{ user.email || 'Sin correo' }}
            td(data-label="Puede recomendar")
              label.switch-label
                input(type="checkbox" :checked="enabledReferrers.has(user.uuid)" :disabled="saving || !eligibleReferrers.has(user.uuid) || !user.emailVerified || user.status !== 'ACTIVE'" :aria-label="`Habilitar recomendaciones para ${user.displayName || user.username}`" @change="toggleReferrer(user, $event)")
                span {{ enabledReferrers.has(user.uuid) ? 'Habilitado' : 'Deshabilitado' }}
    EmptyState(v-else message="Aún no hay organizadores directos con correo verificado que puedan habilitarse.")

  section(v-else aria-labelledby="settings-title")
    h2#settings-title Reglas de invitación
    p.page-hint Los valores tienen límites de seguridad aplicados también en el servidor.
    form.settings-form(@submit.prevent="saveSettings")
      label.switch-label
        input(v-model="settings.referralsEnabled" type="checkbox")
        span Habilitar recomendaciones de organizadores seleccionados
      .settings-grid
        label.field(for="invite-ttl")
          span Vigencia de cada invitación (días)
          input#invite-ttl(v-model.number="settings.invitationTtlDays" type="number" min="1" max="30" required)
        label.field(for="invite-quota")
          span Recomendaciones por persona cada 30 días
          input#invite-quota(v-model.number="settings.referralLimit" type="number" min="1" max="10" required)
      BaseButton(type="submit" :loading="saving" :disabled="saving") Guardar reglas
    p.help-text Valores iniciales: 7 días de vigencia y 3 recomendaciones por periodo. El gate global inicia apagado.
</template>

<script lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/features/auth/authStore'
import { listUsers } from '@/services/api/adminApi'
import {
  approveOrganizerReferral, createOrganizerInvite, getOrganizerInviteSettings,
  listEnabledReferrers, listOrganizerInvites, listOrganizerReferrals,
  rejectOrganizerReferral, resendOrganizerInvite, revokeOrganizerInvite,
  saveOrganizerInviteSettings, setReferrerEnabled, listReferralEligibleOrganizers,
  type OrganizerInvite, type OrganizerInviteSettings, type OrganizerReferral
} from '@/services/api/organizerInvitationsApi'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

interface AdminOrganizer { uuid: string, username?: string, displayName?: string, email?: string, emailVerified?: boolean, status: string, roles?: string }

export default {
  name: 'AdminOrganizerInvitationsPage',
  components: { DashboardBreadcrumb, BaseButton, StatusBadge, EmptyState, FeedbackMessage, LoadingState },
  setup() {
    const auth = useAuthStore()
    const tab = ref<'invites' | 'referrals' | 'settings'>('invites')
    const loading = ref(true); const saving = ref(false); const error = ref(''); const message = ref(''); const messageTone = ref<'success' | 'error'>('success')
    const invites = ref<OrganizerInvite[]>([]); const referrals = ref<OrganizerReferral[]>([]); const organizers = ref<AdminOrganizer[]>([])
    const enabledReferrers = ref(new Set<string>())
    const eligibleReferrers = ref(new Set<string>())
    const inviteEmail = ref(''); const inviteFilter = ref('')
    const settings = reactive<OrganizerInviteSettings>({ referralsEnabled: false, invitationTtlDays: 7, referralLimit: 3 })
    const token = () => auth.state.token as string

    async function loadInvites() { invites.value = await listOrganizerInvites(token(), inviteFilter.value || undefined) }
    async function load() {
      loading.value = true; error.value = ''
      try {
        await Promise.all([loadInvites(),
          listOrganizerReferrals(token(), 'PENDING_REVIEW').then(v => referrals.value = v),
          getOrganizerInviteSettings(token()).then(v => Object.assign(settings, v)),
          listEnabledReferrers(token()).then(v => enabledReferrers.value = new Set(v)),
          listReferralEligibleOrganizers(token()).then(v => eligibleReferrers.value = new Set(v)),
          listUsers(token(), 1, 100, { role: 'ORGANIZER', status: 'ACTIVE' }).then(v => organizers.value = v.data || [])])
      } catch (e: any) { error.value = 'No fue posible cargar la configuración de beta. Inténtalo de nuevo.' }
      finally { loading.value = false }
    }
    function notify(text: string, tone: 'success' | 'error' = 'success') { message.value = text; messageTone.value = tone }
    async function createInvite() {
      saving.value = true; message.value = ''
      try { await createOrganizerInvite(token(), inviteEmail.value); inviteEmail.value = ''; notify('Invitación procesada. Revisa su estado de entrega en la lista.'); await loadInvites() }
      catch (e: any) { notify('No se pudo emitir la invitación. Verifica el correo y vuelve a intentarlo.', 'error') }
      finally { saving.value = false }
    }
    async function resend(invite: OrganizerInvite) {
      if (!window.confirm(`Se enviará un enlace nuevo a ${invite.email}; el anterior dejará de funcionar. ¿Continuar?`)) return
      saving.value = true
      try { await resendOrganizerInvite(token(), invite.uuid); notify('Se generó un enlace nuevo y se intentó enviar.'); await loadInvites() }
      catch (e: any) { notify('No se pudo reenviar la invitación.', 'error') }
      finally { saving.value = false }
    }
    async function revoke(invite: OrganizerInvite) {
      if (!window.confirm(`¿Revocar la invitación pendiente para ${invite.email}?`)) return
      saving.value = true
      try { await revokeOrganizerInvite(token(), invite.uuid); notify('Invitación revocada.'); await loadInvites() }
      catch (e: any) { notify('No se pudo revocar la invitación.', 'error') }
      finally { saving.value = false }
    }
    async function approve(row: OrganizerReferral) {
      if (!window.confirm(`Se enviará una invitación de organizador a ${row.email}. ¿Aprobar?`)) return
      saving.value = true
      try { await approveOrganizerReferral(token(), row.uuid); notify('Recomendación aprobada; revisa el estado de entrega en Invitaciones.'); await load() }
      catch (e: any) { notify('No se pudo aprobar la recomendación.', 'error') }
      finally { saving.value = false }
    }
    async function reject(row: OrganizerReferral) {
      if (!window.confirm(`¿Rechazar la recomendación para ${row.email}?`)) return
      saving.value = true
      try { await rejectOrganizerReferral(token(), row.uuid); notify('Recomendación rechazada.'); await load() }
      catch (e: any) { notify('No se pudo rechazar la recomendación.', 'error') }
      finally { saving.value = false }
    }
    async function toggleReferrer(user: AdminOrganizer, event: Event) {
      const enabled = (event.target as HTMLInputElement).checked; saving.value = true
      try { await setReferrerEnabled(token(), user.uuid, enabled); const next = new Set(enabledReferrers.value); if (enabled) next.add(user.uuid); else next.delete(user.uuid); enabledReferrers.value = next; notify(enabled ? 'Organizador habilitado para recomendar.' : 'Permiso de recomendación retirado.') }
      catch (e: any) { (event.target as HTMLInputElement).checked = !enabled; notify('No se pudo actualizar el permiso.', 'error') }
      finally { saving.value = false }
    }
    async function saveSettings() {
      saving.value = true
      try { Object.assign(settings, await saveOrganizerInviteSettings(token(), { ...settings })); notify('Reglas de beta guardadas.') }
      catch (e: any) { notify('No se guardaron las reglas. Usa vigencia entre 1 y 30 días y cuota entre 1 y 10.', 'error') }
      finally { saving.value = false }
    }
    function statusLabel(status: string) { return ({ PENDING: 'Pendiente', DELIVERY_FAILED: 'Error de envío', ACCEPTED: 'Aceptada', EXPIRED: 'Vencida', REVOKED: 'Revocada' } as Record<string,string>)[status] || status }
    function dateLabel(value: string) { return new Intl.DateTimeFormat('es-MX',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value)) }
    onMounted(load)
    return { tab, loading, saving, error, message, messageTone, invites, referrals, organizers, enabledReferrers, eligibleReferrers, inviteEmail, inviteFilter, settings,
      loadInvites, createInvite, resend, revoke, approve, reject, toggleReferrer, saveSettings, statusLabel, dateLabel }
  }
}
</script>

<style scoped>
.invitation-admin-page { max-width: 1200px; margin: 0 auto; }
h1 { margin: 0 0 8px; color: var(--color-heading); }
h2 { color: var(--color-heading); margin: 24px 0 12px; }
.tabs { display: flex; flex-wrap: wrap; gap: 8px; border-bottom: 1px solid var(--color-border-subtle); padding: 16px 0; margin-bottom: 18px; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.section-heading h2 { margin-top: 8px; }
.create-form,.settings-form { display: flex; align-items: end; flex-wrap: wrap; gap: 12px; background: var(--color-surface); border: 1px solid var(--color-border-subtle); padding: 16px; border-radius: 12px; margin: 16px 0 8px; }
.field { display: flex; flex-direction: column; gap: 6px; min-width: min(100%,280px); color: var(--color-text-secondary); font-size: .86rem; font-weight: 600; }
.field input,.field select { min-height: 42px; }
.help-text { color: var(--color-text-muted); font-size: .82rem; margin: 8px 0 18px; }
.data-table { width: 100%; border-collapse: collapse; background: var(--color-surface); }
.data-table th,.data-table td { padding: 12px; border-bottom: 1px solid var(--color-border-subtle); text-align: left; vertical-align: top; }
.data-table th { color: var(--color-text-muted); font-size: .74rem; text-transform: uppercase; }
.actions { display: flex; flex-wrap: wrap; gap: 6px; }
.block { display:block; margin-top:6px; color:var(--color-danger); }
.switch-label { display:flex; align-items:center; gap:10px; cursor:pointer; min-height:42px; }
.settings-form { display:block; }
.settings-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:14px; margin:16px 0; }
.referrers-title { margin-top:32px; }
@media(max-width:760px) {
  .data-table thead { display:none; }
  .data-table,.data-table tbody,.data-table tr,.data-table td { display:block; width:100%; }
  .data-table tr { border:1px solid var(--color-border-subtle); border-radius:12px; margin:0 0 12px; padding:6px; }
  .data-table td { border:0; padding:8px; }
  .data-table td::before { content:attr(data-label); display:block; color:var(--color-text-muted); font-size:.68rem; font-weight:700; text-transform:uppercase; margin-bottom:4px; }
  .actions { flex-direction:column; align-items:stretch; }
}
</style>
