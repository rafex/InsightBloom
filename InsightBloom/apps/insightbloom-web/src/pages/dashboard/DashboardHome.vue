<template lang="pug">
.dashboard-home
  .dashboard-header
    h1 Dashboard
    router-link.btn-primary(to="/dashboard/conferences/new") + Nueva conferencia

  .section(v-if="loading")
    .loading-text Cargando conferencias...

  .section(v-else-if="conferences.length === 0")
    .empty-state
      p Aún no tienes conferencias.
      router-link.btn-primary(to="/dashboard/conferences/new") Crear la primera

  .section(v-else)
    h2 Tus conferencias
    .conference-grid
      .conf-card(v-for="c in conferences" :key="c.uuid || c.conferenceId")
        .conf-card-header
          span.friendly-id {{ c.friendlyId }}
          span.status-badge(:class="c.status") {{ c.status }}
        h3.conf-name {{ c.name }}
        .expiry-row(v-if="c.expiresAt")
          span.expiry-icon ⏱
          span.expiry-text(:class="{ expired: isExpired(c.expiresAt) }")
            | {{ isExpired(c.expiresAt) ? 'Expiró ' : 'Expira ' }}{{ formatRelative(c.expiresAt) }}
        .conf-actions
          a.btn-outline(:href="`/c/${c.friendlyId}/doubts`" target="_blank") Ver nube
          router-link.btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/words`") Moderación
          button.btn-danger(@click="confirmDelete(c)" :disabled="c._deleting") Eliminar

  .confirm-overlay(v-if="deleteTarget" @click.self="deleteTarget = null")
    .confirm-dialog
      h4 ¿Eliminar conferencia?
      p Esto borrará permanentemente <strong>{{ deleteTarget.name }}</strong> y no se puede deshacer.
      .confirm-actions
        button.btn-cancel(@click="deleteTarget = null") Cancelar
        button.btn-confirm(@click="doDelete") Eliminar
</template>

<script>
import { ref, onMounted } from 'vue'
import { getConferences, deleteConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'DashboardHome',
  setup() {
    const conferences = ref([])
    const loading     = ref(true)
    const deleteTarget = ref(null)
    const auth = useAuthStore()

    onMounted(async () => {
      try {
        const token = auth.state.token
        if (token) conferences.value = await getConferences(token)
      } catch (e) {
        console.error('Error cargando conferencias', e)
      } finally {
        loading.value = false
      }
    })

    function isExpired(iso) { return iso && new Date(iso) < new Date() }

    function formatRelative(iso) {
      const diff = new Date(iso) - new Date()
      const abs  = Math.abs(diff)
      const past = diff < 0
      const mins  = Math.floor(abs / 60_000)
      const hours = Math.floor(abs / 3_600_000)
      const days  = Math.floor(abs / 86_400_000)
      let str
      if (mins < 60)       str = `${mins}m`
      else if (hours < 24) str = `${hours}h`
      else                 str = `${days}d`
      return past ? `hace ${str}` : `en ${str}`
    }

    function confirmDelete(c) { deleteTarget.value = c }

    async function doDelete() {
      const c = deleteTarget.value
      if (!c) return
      c._deleting = true
      deleteTarget.value = null
      try {
        await deleteConference(c.uuid || c.conferenceId, auth.state.token)
        conferences.value = conferences.value.filter(x => (x.uuid || x.conferenceId) !== (c.uuid || c.conferenceId))
      } catch (e) {
        console.error('Error eliminando conferencia', e)
        c._deleting = false
      }
    }

    return { conferences, loading, deleteTarget, isExpired, formatRelative, confirmDelete, doDelete }
  }
}
</script>

<style scoped>
.dashboard-home { padding: 32px 24px; max-width: 960px; }
.dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
h1 { color: #1e1b4b; margin: 0; font-size: 1.8rem; }
h2 { color: #374151; font-size: 1.1rem; font-weight: 600; margin: 0 0 16px; }

.section { margin-bottom: 32px; }
.loading-text { color: #6b7280; }
.empty-state { text-align: center; padding: 48px; background: #f9fafb; border-radius: 12px; }
.empty-state p { color: #6b7280; margin-bottom: 16px; }

.conference-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }

.conf-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; transition: box-shadow 0.2s; }
.conf-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }

.conf-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.friendly-id { font-size: 0.78rem; color: #6b7280; font-family: monospace; }
.status-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 99px; font-weight: 600; text-transform: uppercase; }
.status-badge.ACTIVE, .status-badge.active { background: #d1fae5; color: #065f46; }
.status-badge.CLOSED, .status-badge.closed { background: #fee2e2; color: #991b1b; }

.conf-name { font-size: 1rem; font-weight: 600; color: #1e1b4b; margin: 0 0 8px; }

.expiry-row { display: flex; align-items: center; gap: 4px; margin-bottom: 12px; font-size: 0.8rem; }
.expiry-icon { font-size: 0.85rem; }
.expiry-text { color: #6b7280; }
.expiry-text.expired { color: #dc2626; font-weight: 600; }

.conf-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }

.btn-primary { display: inline-block; padding: 8px 18px; background: #4f46e5; color: #fff; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 500; border: none; cursor: pointer; }
.btn-primary:hover { background: #4338ca; }

.btn-outline { display: inline-block; padding: 6px 14px; border: 1px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 0.8rem; }
.btn-outline:hover { background: #eef2ff; }

.btn-ghost { display: inline-block; padding: 6px 14px; color: #6b7280; border: 1px solid #e5e7eb; border-radius: 8px; text-decoration: none; font-size: 0.8rem; }
.btn-ghost:hover { background: #f3f4f6; color: #374151; }

.btn-danger { padding: 6px 14px; background: transparent; color: #dc2626; border: 1px solid #fca5a5; border-radius: 8px; cursor: pointer; font-size: 0.8rem; margin-left: auto; }
.btn-danger:hover { background: #fee2e2; }
.btn-danger:disabled { opacity: 0.4; cursor: not-allowed; }

/* Modal de confirmación */
.confirm-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 100;
}
.confirm-dialog {
  background: #fff; border-radius: 16px; padding: 28px 32px;
  max-width: 400px; width: 90%; box-shadow: 0 8px 40px rgba(0,0,0,0.2);
}
.confirm-dialog h4 { margin: 0 0 12px; color: #1e1b4b; font-size: 1.1rem; }
.confirm-dialog p { color: #6b7280; font-size: 0.95rem; margin: 0 0 24px; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; }
.btn-cancel { padding: 8px 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #374151; cursor: pointer; }
.btn-cancel:hover { background: #f3f4f6; }
.btn-confirm { padding: 8px 18px; background: #dc2626; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; }
.btn-confirm:hover { background: #b91c1c; }
</style>
