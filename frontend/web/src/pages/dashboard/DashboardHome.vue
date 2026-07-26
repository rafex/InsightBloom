<template lang="pug">
.dashboard-home(v-if="isOrganizer" id="onboarding-dashboard-home")
  .dashboard-header
    h1 Dashboard

  .summary-group(v-if="!loading && conferences.length" id="onboarding-events-summary")
    h2 Eventos
    .summary-grid
      .summary-card
        span.summary-icon 🟢
        span.summary-value {{ eventStats.active }}
        span.summary-label Activos
      .summary-card
        span.summary-icon 🎤
        span.summary-value {{ eventStats.registered }}
        span.summary-label Registrados
      .summary-card
        span.summary-icon ⏱️
        span.summary-value {{ eventStats.expired }}
        span.summary-label Expirados

  .summary-group(v-if="!loading && conferences.length" id="onboarding-users-summary")
    h2 Usuarios en tus eventos
    p.summary-hint Asistentes de los eventos que organizás — no el total de cuentas de la plataforma (ver "Usuarios" en el menú).
    .summary-grid
      .summary-card
        span.summary-icon 👥
        span.summary-value {{ summaryLoading ? '…' : summary.registeredAttendees }}
        span.summary-label Registrados
      .summary-card
        span.summary-icon ✅
        span.summary-value {{ summaryLoading ? '…' : summary.activeAttendees }}
        span.summary-label Activos

  .summary-group(v-if="!loading && jaasUsage" id="onboarding-jaas-summary")
    h2 Consumo estimado de videollamadas JaaS
    .jaas-usage-card(:class="{ 'jaas-warning': jaasUsage.percentage >= 80, 'jaas-limit': jaasUsage.remaining === 0 }")
      .jaas-usage-header
        span.summary-icon 🎥
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
    .empty-state
      p Aún no tienes conferencias.
      router-link.btn-primary(to="/dashboard/conferences/new") Crear la primera

  OnboardingTour(storage-key="ib_onboarding_dashboard" :steps="organizerTourSteps")

.dashboard-home(v-else)
  .dashboard-header
    h1 Mis eventos
    router-link.btn-primary(to="/dashboard/join") + Unirse a un evento

  .section(v-if="loadingHistory")
    .loading-text Cargando historial...

  .section(v-else-if="history.length === 0")
    .empty-state
      p Aún no te has unido a ningún evento.
      router-link.btn-primary(to="/dashboard/join") Unirme a un evento

  .section(v-else)
    .conference-grid
      .conf-card(v-for="h in history" :key="h.conferenceUuid" :class="{ unavailable: !h.available }")
        .conf-card-header
          span.friendly-id {{ h.friendlyId || h.conferenceUuid }}
          span.status-badge(:class="h.available ? 'active' : 'unavailable'") {{ h.available ? 'Disponible' : 'No disponible' }}
        h3.conf-name {{ h.name || '(sin nombre)' }}
        p.joined-at Te uniste {{ formatDate(h.joinedAt) }}
        .conf-actions(v-if="h.available")
          router-link.btn-outline(:to="`/c/${h.friendlyId}/doubts`") Entrar
          router-link.btn-outline(v-if="h.seatingMode && h.seatingMode !== 'NONE'" :to="`/c/${h.friendlyId}/ticket`") 🎟️ Mi boleto
        p.unavailable-note(v-else) Este evento ya no se encuentra disponible.
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getConferences, getConferenceHistory, getUniqueRegisteredAttendeesCount, getActiveRegisteredAttendeesCount, getJaasUsage } from '@/services/api/usersApi'
import type { Conference, ConferenceHistoryEntry, JaasUsage } from '@/services/api/types'
import { isExpired } from '@/utils/dates'
import { useAuthStore } from '@/features/auth/authStore'
import OnboardingTour from '@/components/OnboardingTour.vue'

const ORGANIZER_TOUR_STEPS = [
  { selector: '#onboarding-events-summary', text: 'Aquí ves cuántos eventos tienes activos, registrados y expirados. Para crear uno nuevo o ver el listado completo, usá el menú de la izquierda.' },
  { selector: '#onboarding-users-summary', text: 'Aquí ves cuántos usuarios se registraron en tus eventos y cuántos están activos.' }
]

export default {
  name: 'DashboardHome',
  components: { OnboardingTour },
  setup() {
    const conferences = ref<Conference[]>([])
    const loading     = ref(true)
    const history = ref<ConferenceHistoryEntry[]>([])
    const loadingHistory = ref(true)
    const auth = useAuthStore()
    const isOrganizer = auth.isOrganizer()
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
      if (isOrganizer) {
        try {
          if (token) {
            conferences.value = await getConferences(token)
            void loadSummary(token)
          }
        } catch (e: any) {
          console.error('Error cargando conferencias', e)
        } finally {
          loading.value = false
        }
      } else {
        try {
          if (token) history.value = await getConferenceHistory(token)
        } catch (e: any) {
          console.error('Error cargando historial', e)
        } finally {
          loadingHistory.value = false
        }
      }
    })

    function formatDate(iso: string | null | undefined) {
      if (!iso) return ''
      return new Date(iso).toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' })
    }

    return {
      conferences, loading, isOrganizer, history, loadingHistory,
      summary, summaryLoading, eventStats, formatDate, organizerTourSteps, jaasUsage
    }
  }
}
</script>

<style scoped>
.dashboard-home { padding: 32px 24px; max-width: 960px; }
.dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
h1 { color: #1e1b4b; margin: 0; font-size: 1.8rem; }
h2 { color: #374151; font-size: 1.1rem; font-weight: 600; margin: 0 0 16px; }

.summary-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px; margin-bottom: 32px;
}
.summary-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 18px;
  display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center;
}
.summary-icon { font-size: 1.4rem; }
.summary-value { font-size: 1.6rem; font-weight: 700; color: #1e1b4b; }
.summary-label { font-size: 0.78rem; color: #6b7280; }
.summary-hint { margin: -6px 0 10px; font-size: 0.8rem; color: #9ca3af; }

.jaas-usage-card { background: #fff; border: 1px solid #c7d2fe; border-radius: 12px; padding: 18px 20px; margin-bottom: 32px; }
.jaas-usage-card.jaas-warning { border-color: #fbbf24; background: #fffbeb; }
.jaas-usage-card.jaas-limit { border-color: #fca5a5; background: #fef2f2; }
.jaas-usage-header { display: flex; align-items: center; gap: 10px; }
.jaas-usage-title { display: flex; flex-direction: column; gap: 2px; color: #1e1b4b; }
.jaas-usage-title span { color: #6b7280; font-size: .78rem; }
.jaas-usage-value { color: #1e1b4b; font-size: 1.6rem; font-weight: 700; margin: 14px 0 8px; }
.jaas-progress { height: 10px; border-radius: 99px; background: #e5e7eb; overflow: hidden; }
.jaas-progress span { display: block; height: 100%; border-radius: inherit; background: #4f46e5; transition: width .25s ease; }
.jaas-warning .jaas-progress span { background: #d97706; }
.jaas-limit .jaas-progress span { background: #dc2626; }
.jaas-usage-note { margin: 10px 0 0; color: #374151; font-size: .85rem; }
.jaas-usage-disclaimer { margin: 8px 0 0; color: #6b7280; font-size: .72rem; }
.jaas-bandwidth-note { margin: 8px 0 0; color: #4b5563; font-size: .78rem; }

.section { margin-bottom: 32px; }
.loading-text { color: #6b7280; }
.empty-state { text-align: center; padding: 48px; background: #f9fafb; border-radius: 12px; }
.empty-state p { color: #6b7280; margin-bottom: 16px; }

.conference-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }

.conf-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; transition: box-shadow 0.2s; }
.conf-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.conf-card.unavailable { opacity: 0.6; }

.conf-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.friendly-id { font-size: 0.78rem; color: #6b7280; font-family: monospace; }
.status-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 99px; font-weight: 600; text-transform: uppercase; }
.status-badge.active { background: #d1fae5; color: #065f46; }
.status-badge.unavailable { background: #f3f4f6; color: #6b7280; }

.conf-name { font-size: 1rem; font-weight: 600; color: #1e1b4b; margin: 0 0 8px; }
.joined-at { font-size: 0.8rem; color: #6b7280; margin: 0 0 12px; }
.unavailable-note { font-size: 0.82rem; color: #9ca3af; font-style: italic; margin: 0; }

.conf-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }

.btn-primary { display: inline-block; padding: 8px 18px; background: #4f46e5; color: #fff; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 500; border: none; cursor: pointer; }
.btn-primary:hover { background: #4338ca; }

.btn-secondary { display: inline-block; padding: 8px 18px; background: #eef2ff; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 600; border: 2px solid #c7d2fe; }
.btn-secondary:hover { background: #e0e7ff; }

.btn-outline { display: inline-block; padding: 6px 14px; border: 1px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 0.8rem; }
.btn-outline:hover { background: #eef2ff; }

@media (max-width: 640px) {
  .dashboard-home { padding: 16px 14px; }
  .dashboard-header { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>
