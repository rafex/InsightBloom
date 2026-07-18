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
    h2 Usuarios
    .summary-grid
      .summary-card
        span.summary-icon 👥
        span.summary-value {{ summaryLoading ? '…' : summary.registeredAttendees }}
        span.summary-label Registrados
      .summary-card
        span.summary-icon ✅
        span.summary-value {{ summaryLoading ? '…' : summary.activeAttendees }}
        span.summary-label Activos

  .section(v-if="loading")
    .loading-text Cargando conferencias...

  .section(v-else-if="conferences.length === 0")
    .empty-state
      p Aún no tienes conferencias.
      router-link.btn-primary(to="/dashboard/conferences/new") Crear la primera

  OnboardingTour(storage-key="ib_onboarding_dashboard" :steps="organizerTourSteps")

.dashboard-home(v-else)
  .dashboard-header
    h1 Mis conferencias
    router-link.btn-primary(to="/dashboard/join") + Unirse a una conferencia

  .section(v-if="loadingHistory")
    .loading-text Cargando historial...

  .section(v-else-if="history.length === 0")
    .empty-state
      p Aún no te has unido a ninguna conferencia.
      router-link.btn-primary(to="/dashboard/join") Unirme a una conferencia

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
        p.unavailable-note(v-else) Esta conferencia ya no se encuentra disponible.
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getConferences, getConferenceHistory, getUniqueRegisteredAttendeesCount, getActiveRegisteredAttendeesCount } from '@/services/api/usersApi'
import type { Conference, ConferenceHistoryEntry } from '@/services/api/types'
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
    const organizerTourSteps = ORGANIZER_TOUR_STEPS

    const eventStats = computed(() => ({
      registered: conferences.value.length,
      active: conferences.value.filter((c) => c.status === 'ACTIVE').length,
      expired: conferences.value.filter((c) => isExpired(c.expiresAt)).length
    }))

    async function loadSummary(token: string) {
      summaryLoading.value = true
      try {
        const [registeredAttendees, activeAttendees] = await Promise.all([
          getUniqueRegisteredAttendeesCount(token).catch(() => 0),
          getActiveRegisteredAttendeesCount(token).catch(() => 0)
        ])
        summary.value = { registeredAttendees, activeAttendees }
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
            if (conferences.value.length) loadSummary(token)
            else summaryLoading.value = false
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
      summary, summaryLoading, eventStats, formatDate, organizerTourSteps
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
