<template lang="pug">
.dashboard-home(v-if="isEventManager" id="onboarding-dashboard-home")
  .dashboard-header
    h1 Panel

  .summary-group(v-if="!loading && conferences.length" id="onboarding-events-summary")
    h2 Eventos
    .summary-grid
      .summary-card
        span.summary-icon(aria-hidden="true")
          UiIcon(name="activity")
        span.summary-value {{ eventStats.active }}
        span.summary-label Activos
      .summary-card
        span.summary-icon(aria-hidden="true")
          UiIcon(name="presentation")
        span.summary-value {{ eventStats.registered }}
        span.summary-label Registrados
      .summary-card
        span.summary-icon(aria-hidden="true")
          UiIcon(name="clock")
        span.summary-value {{ eventStats.expired }}
        span.summary-label Expirados

  .summary-group(v-if="!loading && conferences.length" id="onboarding-users-summary")
    h2 Usuarios en tus eventos
    p.summary-hint Asistentes de los eventos que administrás — no el total de cuentas de la plataforma.
    .summary-grid
      .summary-card
        span.summary-icon(aria-hidden="true")
          UiIcon(name="users")
        span.summary-value {{ summaryLoading ? '…' : summary.registeredAttendees }}
        span.summary-label Registrados
      .summary-card
        span.summary-icon(aria-hidden="true")
          UiIcon(name="check")
        span.summary-value {{ summaryLoading ? '…' : summary.activeAttendees }}
        span.summary-label Activos

  .summary-group(v-if="!loading && jaasUsage" id="onboarding-jaas-summary")
    h2 Consumo estimado de videollamadas JaaS
    .jaas-usage-card(:class="{ 'jaas-warning': jaasUsage.percentage >= 80, 'jaas-limit': jaasUsage.remaining === 0 }")
      .jaas-usage-header
        span.summary-icon(aria-hidden="true")
          UiIcon(name="video")
        .jaas-usage-title
          strong Participantes únicos del mes
          span {{ jaasUsage.month }} · límite operativo configurado
      .jaas-usage-value {{ jaasUsage.uniqueParticipants }} / {{ jaasUsage.monthlyLimit }}
      .jaas-progress(aria-hidden="true")
        span(:style="{ width: `${Math.min(100, jaasUsage.percentage)}%` }")
      p.jaas-usage-note(v-if="jaasUsage.remaining > 0") Quedan {{ jaasUsage.remaining }} participantes únicos estimados.
      p.jaas-usage-note(v-else) Se alcanzó el límite operativo configurado. Revisa el consumo en la consola de 8x8 antes de abrir más llamadas.
      p.jaas-usage-disclaimer El contador es una estimación local de autorizaciones de InsightBloom; el consumo facturable final lo determina 8x8 JaaS.
      p.jaas-bandwidth-note 📡 Límite de referencia: {{ jaasUsage.bandwidthLimitGb }} GB de ancho de banda al mes. El consumo exacto de bytes debe confirmarse en la actividad de 8x8 porque JaaS no lo expone en este endpoint.

  .section(v-if="loading")
    .loading-text Cargando conferencias...

  .section(v-else-if="conferences.length === 0")
    EmptyState(message="Aún no tienes conferencias.")
      router-link.link-btn.link-btn-primary(to="/dashboard/conferences/new") Crear la primera

  OnboardingTour(storage-key="ib_onboarding_dashboard" :steps="organizerTourSteps")

.dashboard-home(v-else)
  .dashboard-header
    h1 Mis eventos
    router-link.link-btn.link-btn-primary(to="/dashboard/join") + Unirse a un evento

  .section(v-if="loadingHistory")
    .loading-text Cargando historial...

  .section(v-else-if="history.length === 0")
    EmptyState(message="Aún no te has unido a ningún evento.")
      router-link.link-btn.link-btn-primary(to="/dashboard/join") Unirme a un evento

  .section(v-else)
    .conference-grid
      .conf-card(v-for="h in history" :key="h.conferenceUuid" :class="{ unavailable: !h.available }")
        .conf-card-header
          span.friendly-id {{ h.friendlyId || h.conferenceUuid }}
          span.status-badge(:class="h.available ? 'active' : 'unavailable'") {{ h.available ? 'Disponible' : 'No disponible' }}
        h3.conf-name {{ h.name || '(sin nombre)' }}
        p.joined-at Te uniste {{ formatDate(h.joinedAt) }}
        .conf-actions(v-if="h.available")
          router-link.link-btn.link-btn-secondary(:to="`/c/${h.friendlyId}/doubts`") Entrar
          router-link.link-btn.link-btn-secondary(v-if="h.seatingMode && h.seatingMode !== 'NONE'" :to="`/c/${h.friendlyId}/ticket`")
            UiIcon(name="ticket" size="16" aria-hidden="true")
            | Mi boleto
        p.unavailable-note(v-else) Este evento ya no se encuentra disponible.
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getConferences, getConferenceHistory, getUniqueRegisteredAttendeesCount, getActiveRegisteredAttendeesCount, getJaasUsage } from '@/services/api/usersApi'
import type { Conference, ConferenceHistoryEntry, JaasUsage } from '@/services/api/types'
import { isExpired } from '@/utils/dates'
import { useAuthStore } from '@/features/auth/authStore'
import OnboardingTour from '@/components/OnboardingTour.vue'
import UiIcon from '@/components/ui/UiIcon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const ORGANIZER_TOUR_STEPS = [
  { selector: '#onboarding-events-summary', text: 'Aquí ves cuántos eventos tienes activos, registrados y expirados. Para crear uno nuevo o ver el listado completo, usá el menú de la izquierda.' },
  { selector: '#onboarding-users-summary', text: 'Aquí ves cuántos usuarios se registraron en tus eventos y cuántos están activos.' }
]

export default {
  name: 'DashboardHome',
  components: { OnboardingTour, UiIcon, EmptyState },
  setup() {
    const conferences = ref<Conference[]>([])
    const loading     = ref(true)
    const history = ref<ConferenceHistoryEntry[]>([])
    const loadingHistory = ref(true)
    const auth = useAuthStore()
    const isEventManager = ref(auth.isModerator())
    const summary = ref({ registeredAttendees: 0, activeAttendees: 0 })
    const summaryLoading = ref(true)
    const jaasUsage = ref<JaasUsage | null>(null)
    const organizerTourSteps = ORGANIZER_TOUR_STEPS

    const eventStats = computed(() => ({
      registered: conferences.value.length,
      active: conferences.value.filter((c) => c.status === 'ACTIVE').length,
      expired: conferences.value.filter((c) => isExpired(c.expiresAt)).length
    }))

    async function loadSummary(token: string) {
      summaryLoading.value = true
      try {
        const [registeredAttendees, activeAttendees, usage] = await Promise.all([
          getUniqueRegisteredAttendeesCount(token).catch(() => 0),
          getActiveRegisteredAttendeesCount(token).catch(() => 0),
          getJaasUsage(token).catch(() => null)
        ])
        summary.value = { registeredAttendees, activeAttendees }
        jaasUsage.value = usage
      } finally {
        summaryLoading.value = false
      }
    }

    onMounted(async () => {
      const token = auth.state.token
      if (!token) {
        loading.value = false
        loadingHistory.value = false
        return
      }
      try {
        // También resolvemos asignaciones event-scoped: un moderador operativo puede
        // conservar ATTENDEE como rol global y aun así administrar sus eventos asignados.
        conferences.value = await getConferences(token)
        if (isEventManager.value || conferences.value.length > 0) {
          isEventManager.value = true
          void loadSummary(token)
          return
        }
      } catch (e: any) {
        console.error('Error cargando conferencias', e)
      } finally {
        loading.value = false
      }
      try {
        history.value = await getConferenceHistory(token)
      } catch (e: any) {
        console.error('Error cargando historial', e)
      } finally {
        loadingHistory.value = false
      }
    })

    function formatDate(iso: string | null | undefined) {
      if (!iso) return ''
      return new Date(iso).toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' })
    }

    return {
      conferences, loading, isEventManager, history, loadingHistory,
      summary, summaryLoading, eventStats, formatDate, organizerTourSteps, jaasUsage
    }
  }
}
</script>

<style scoped>
.dashboard-home { padding: 32px 24px; max-width: 960px; }
.dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
h1 { color: var(--color-heading); margin: 0; font-size: 1.8rem; }
h2 { color: var(--color-text-secondary); font-size: 1.1rem; font-weight: 600; margin: 0 0 16px; }

.summary-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px; margin-bottom: 32px;
}
.summary-card {
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 18px;
  display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center;
}
.summary-icon { font-size: 1.4rem; }
.summary-value { font-size: 1.6rem; font-weight: 700; color: var(--color-heading); }
.summary-label { font-size: 0.78rem; color: var(--color-text-muted); }
.summary-hint { margin: -6px 0 10px; font-size: 0.8rem; color: var(--color-text-muted); }

.jaas-usage-card { background: var(--color-surface); border: 1px solid var(--color-primary-border); border-radius: 12px; padding: 18px 20px; margin-bottom: 32px; }
.jaas-usage-card.jaas-warning { border-color: var(--color-warning); background: var(--color-warning-soft); }
.jaas-usage-card.jaas-limit { border-color: var(--color-danger); background: var(--color-danger-soft); }
.jaas-usage-header { display: flex; align-items: center; gap: 10px; }
.jaas-usage-title { display: flex; flex-direction: column; gap: 2px; color: var(--color-heading); }
.jaas-usage-title span { color: var(--color-text-muted); font-size: .78rem; }
.jaas-usage-value { color: var(--color-heading); font-size: 1.6rem; font-weight: 700; margin: 14px 0 8px; }
.jaas-progress { height: 10px; border-radius: 99px; background: var(--color-border-subtle); overflow: hidden; }
.jaas-progress span { display: block; height: 100%; border-radius: inherit; background: var(--color-primary); transition: width .25s ease; }
.jaas-warning .jaas-progress span { background: var(--color-warning); }
.jaas-limit .jaas-progress span { background: var(--color-danger); }
.jaas-usage-note { margin: 10px 0 0; color: var(--color-text-secondary); font-size: .85rem; }
.jaas-usage-disclaimer { margin: 8px 0 0; color: var(--color-text-muted); font-size: .72rem; }
.jaas-bandwidth-note { margin: 8px 0 0; color: var(--color-text-secondary); font-size: .78rem; }

.section { margin-bottom: 32px; }
.conference-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }

.conf-card { background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; transition: box-shadow 0.2s; }
.conf-card:hover { box-shadow: var(--shadow-dropdown); }
.conf-card.unavailable { opacity: 0.6; }

.conf-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.friendly-id { font-size: 0.78rem; color: var(--color-text-muted); font-family: monospace; }
.status-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 99px; font-weight: 600; text-transform: uppercase; }
.status-badge.active { background: var(--color-success-soft); color: var(--color-success); }
.status-badge.unavailable { background: var(--color-surface-muted); color: var(--color-text-muted); }

.conf-name { font-size: 1rem; font-weight: 600; color: var(--color-heading); margin: 0 0 8px; }
.joined-at { font-size: 0.8rem; color: var(--color-text-muted); margin: 0 0 12px; }
.unavailable-note { font-size: 0.82rem; color: var(--color-text-muted); font-style: italic; margin: 0; }

.conf-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }

@media (max-width: 640px) {
  .dashboard-home { padding: 16px 14px; }
  .dashboard-header { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>
