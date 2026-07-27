<template lang="pug">
.user-detail-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  .loading-text(v-if="loading") Cargando usuario...

  template(v-else-if="user")
    .header
      h2 {{ user.displayName || user.username }}
      span.status-badge(:class="user.status") {{ statusLabel(user.status) }}

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
        .loading-text(v-if="loadingReservations") Cargando...
        p.empty-text(v-else-if="reservations.length === 0") No está inscrito en ningún evento.
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
                td {{ r.status }}
                td
                  span(v-if="surveyStatus[r.conferenceUuid] === undefined") …
                  span(v-else) {{ surveyStatus[r.conferenceUuid] ? 'Respondida ✓' : 'Sin responder' }}
                td {{ r.certificateDownloaded ? 'Descargado ✓' : 'No descargado' }}

  .empty-state(v-else)
    p Usuario no encontrado.
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getUser, getUserReservations, type UserReservationEntry } from '@/services/api/adminApi'
import { hasResponded } from '@/services/api/surveyApi'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'

export default {
  name: 'UserDetailPage',
  components: { DashboardBreadcrumb },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const user = ref<Record<string, any> | null>(null)
    const loading = ref(true)
    const reservations = ref<UserReservationEntry[]>([])
    const loadingReservations = ref(true)
    const surveyStatus = ref<Record<string, boolean>>({})

    function statusLabel(s: string): string {
      return ({ ACTIVE: 'Activo', BANNED: 'Baneado', DELETED: 'Eliminado', INACTIVE: 'Inactivo' } as Record<string, string>)[s] || s
    }

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    }

    async function loadReservations(uuid: string, token: string) {
      loadingReservations.value = true
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
      } finally {
        loading.value = false
      }
    })

    const breadcrumbItems = computed(() => [
      { label: 'Usuarios', to: '/dashboard/admin/users' },
      { label: user.value?.displayName || user.value?.username || '', loading: loading.value }
    ])

    return { user, loading, reservations, loadingReservations, surveyStatus, statusLabel, formatDate, breadcrumbItems }
  }
}
</script>

<style scoped>
.user-detail-page { padding: 24px; max-width: 900px; }
.loading-text { color: #6b7280; }
.empty-state { text-align: center; color: var(--color-text-muted); padding: 60px; }
.empty-text { color: var(--color-text-muted); font-size: 0.88rem; }

.header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.header h2 { color: #1e1b4b; margin: 0; }

.status-badge { font-size: 0.78rem; font-weight: 600; padding: 2px 10px; border-radius: 10px; }
.status-badge.ACTIVE { background: #dcfce7; color: #166534; }
.status-badge.BANNED { background: #fee2e2; color: #991b1b; }
.status-badge.DELETED { background: #f3f4f6; color: #6b7280; }
.status-badge.INACTIVE { background: #fef9c3; color: #854d0e; }

.detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
.detail-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; }
.detail-card h3 { margin: 0 0 14px; color: #374151; font-size: 1rem; }

dl { display: grid; grid-template-columns: auto 1fr; gap: 6px 12px; margin: 0; }
dt { color: var(--color-text-muted); font-size: 0.78rem; text-transform: uppercase; font-weight: 600; }
dd { margin: 0; font-size: 0.9rem; color: #1e1b4b; }
.uuid-text { font-family: monospace; font-size: 0.8rem; }

.table-scroll { overflow-x: auto; }
.reservations-table { width: 100%; border-collapse: collapse; }
.reservations-table th { text-align: left; padding: 6px 8px; color: #6b7280; font-size: 0.72rem; text-transform: uppercase; font-weight: 600; }
.reservations-table td { padding: 8px; border-top: 1px solid #f3f4f6; font-size: 0.85rem; }
</style>
