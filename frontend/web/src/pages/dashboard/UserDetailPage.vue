<template lang="pug">
.user-detail-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  LoadingState(v-if="loading" message="Cargando usuario…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")

  template(v-else-if="user")
    .header
      h2 {{ user.displayName || user.username }}
      StatusBadge(:status="user.status" :label="formatStatusLabel(user.status)")

    .detail-grid
      .detail-card
        h3 Datos
        dl
          dt ID usuario
          dd.uuid-text {{ user.uuid }}
          dt Usuario
          dd {{ user.username }}
          dt Nombre
          dd {{ [user.firstName, user.lastName].filter(Boolean).join(' ') || '—' }}
          dt Email
          dd {{ user.email || '—' }}
          dt Teléfono
          dd {{ user.phone || '—' }}
          dt Rol
          dd {{ user.roles || '—' }}
          dt Última conexión
          dd {{ user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Nunca' }}

      .detail-card
        h3 Eventos en los que está inscrito
        LoadingState(v-if="loadingReservations" message="Cargando inscripciones…")
        FeedbackMessage(v-else-if="reservationsError" :message="reservationsError" tone="error")
        EmptyState(v-else-if="reservations.length === 0" message="No está inscrito en ningún evento.")
        .table-scroll(v-else)
          table.reservations-table
            thead
              tr
                th Evento
                th Estado
                th Encuesta
                th Certificado
            tbody
              tr(v-for="r in reservations" :key="r.conferenceUuid")
                td {{ r.conferenceName || r.friendlyId || r.conferenceUuid }}
                td {{ formatStatusLabel(r.status) }}
                td
                  span(v-if="surveyStatus[r.conferenceUuid] === undefined") …
                  span(v-else) {{ surveyStatus[r.conferenceUuid] ? 'Respondida ✓' : 'Sin responder' }}
                td {{ r.certificateDownloaded ? 'Descargado ✓' : 'No descargado' }}

  EmptyState(v-else message="Usuario no encontrado.")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getUser, getUserReservations, type UserReservationEntry } from '@/services/api/adminApi'
import { hasResponded } from '@/services/api/surveyApi'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import { formatStatusLabel } from '@/utils/status'

export default {
  name: 'UserDetailPage',
  components: { DashboardBreadcrumb, StatusBadge, EmptyState, FeedbackMessage, LoadingState },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const user = ref<Record<string, any> | null>(null)
    const loading = ref(true)
    const error = ref('')
    const reservations = ref<UserReservationEntry[]>([])
    const loadingReservations = ref(true)
    const reservationsError = ref('')
    const surveyStatus = ref<Record<string, boolean>>({})

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    }

    async function loadReservations(uuid: string, token: string) {
      loadingReservations.value = true
      reservationsError.value = ''
      try {
        reservations.value = await getUserReservations(uuid, token)
        await Promise.all(reservations.value.map(async (r) => {
          try {
            surveyStatus.value[r.conferenceUuid] = await hasResponded(r.conferenceUuid, token, uuid)
          } catch (e: any) {
            surveyStatus.value[r.conferenceUuid] = false
          }
        }))
      } catch (e: any) {
        reservations.value = []
        reservationsError.value = 'No fue posible cargar las inscripciones de este usuario.'
      } finally {
        loadingReservations.value = false
      }
    }

    onMounted(async () => {
      const uuid = route.params.uuid as string
      const token = auth.state.token as string
      try {
        user.value = await getUser(uuid, token)
        loadReservations(uuid, token)
      } catch (e: any) {
        user.value = null
        error.value = 'No fue posible cargar la información del usuario.'
      } finally {
        loading.value = false
      }
    })

    const breadcrumbItems = computed(() => [
      { label: 'Usuarios', to: '/dashboard/admin/users' },
      { label: user.value?.displayName || user.value?.username || '', loading: loading.value }
    ])

    return { user, loading, error, reservations, loadingReservations, reservationsError, surveyStatus, formatDate, formatStatusLabel, breadcrumbItems }
  }
}
</script>

<style scoped>
.user-detail-page { padding: 24px; max-width: 900px; }

.header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.header h2 { color: var(--color-heading); margin: 0; }

.detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
.detail-card { background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; }
.detail-card h3 { margin: 0 0 14px; color: var(--color-text-secondary); font-size: 1rem; }

dl { display: grid; grid-template-columns: auto 1fr; gap: 6px 12px; margin: 0; }
dt { color: var(--color-text-muted); font-size: 0.78rem; text-transform: uppercase; font-weight: 600; }
dd { margin: 0; font-size: 0.9rem; color: var(--color-heading); }
.uuid-text { font-family: monospace; font-size: 0.8rem; }

.table-scroll { overflow-x: auto; }
.reservations-table { width: 100%; border-collapse: collapse; }
.reservations-table th { text-align: left; padding: 6px 8px; color: var(--color-text-muted); font-size: 0.72rem; text-transform: uppercase; font-weight: 600; }
.reservations-table td { padding: 8px; border-top: 1px solid var(--color-surface-muted); font-size: 0.85rem; }
</style>
