<template lang="pug">
.conferences-list-page
  .list-header
    h1 Conferencias
    router-link.btn-primary(to="/dashboard/conferences/new") + Nueva conferencia

  .section(v-if="loading")
    .loading-text Cargando conferencias...

  .section(v-else-if="conferences.length === 0")
    .empty-state
      p Aún no tienes conferencias.
      router-link.btn-primary(to="/dashboard/conferences/new") Crear la primera

  .table-scroll(v-else)
    table.conferences-table
      thead
        tr
          th Nombre
          th ID amigable
          th Estado
          th Expira
          th Descargas
          th Acciones
      tbody
        tr(v-for="c in conferences" :key="c.uuid || c.conferenceId")
          td(data-label="Nombre") {{ c.name }}
          td(data-label="ID amigable")
            span.friendly-id {{ c.friendlyId }}
          td(data-label="Estado")
            span.status-badge(:class="c.status") {{ c.status }}
          td(data-label="Expira")
            span.expiry-text(v-if="c.expiresAt" :class="{ expired: isExpired(c.expiresAt) }")
              | {{ isExpired(c.expiresAt) ? 'Expiró ' : 'Expira ' }}{{ formatRelative(c.expiresAt) }}
            span(v-else) —
          td(data-label="Descargas")
            span.downloads-text(v-if="downloadCounts[c.uuid || c.conferenceId]")
              | 🎓 {{ downloadCounts[c.uuid || c.conferenceId].certificate }} · 📄 {{ downloadCounts[c.uuid || c.conferenceId].presentation }}
            span(v-else) …
          td.actions-cell(data-label="Acciones")
            .conf-actions
              a.btn-highlight(:href="`/c/${c.friendlyId}/presentation`" target="_blank") 🔴 Live
              button.btn-highlight(@click="qrTarget = c") QR
              router-link.btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/edit`") Editar
              router-link.btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/words`") Moderación
              router-link.btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/presentation`") Presentación
              router-link.btn-highlight(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/speaker`") Presentar
              router-link.btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/survey`") Encuesta
              button.btn-trash(@click="confirmDelete(c)" :disabled="c._deleting" title="Eliminar conferencia")
                svg(xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round")
                  polyline(points="3 6 5 6 21 6")
                  path(d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6")
                  path(d="M10 11v6")
                  path(d="M14 11v6")
                  path(d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2")

  .confirm-overlay(v-if="deleteTarget" @click.self="deleteTarget = null")
    .confirm-dialog
      h4 ¿Eliminar conferencia?
      p Esto borrará permanentemente <strong>{{ deleteTarget.name }}</strong> y no se puede deshacer.
      .confirm-actions
        button.btn-cancel(@click="deleteTarget = null") Cancelar
        button.btn-confirm(@click="doDelete") Eliminar

  QrCodeModal(v-if="qrTarget" :friendlyId="qrTarget.friendlyId" @close="qrTarget = null")
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getConferences, deleteConference, getDownloadCounts } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import QrCodeModal from '@/components/QrCodeModal.vue'

export default {
  name: 'ConferencesListPage',
  components: { QrCodeModal },
  setup() {
    const conferences = ref([])
    const loading = ref(true)
    const deleteTarget = ref(null)
    const qrTarget = ref(null)
    const downloadCounts = ref({})
    const auth = useAuthStore()

    onMounted(async () => {
      try {
        if (auth.state.token) {
          conferences.value = await getConferences(auth.state.token)
          loadDownloadCounts()
        }
      } catch (e: any) {
        console.error('Error cargando conferencias', e)
      } finally {
        loading.value = false
      }
    })

    async function loadDownloadCounts() {
      const token = auth.state.token
      await Promise.all(conferences.value.map(async (c) => {
        const id = c.uuid || c.conferenceId
        try {
          downloadCounts.value[id] = await getDownloadCounts(id, token)
        } catch (e: any) {
          downloadCounts.value[id] = { certificate: 0, presentation: 0 }
        }
      }))
    }

    function isExpired(iso) { return iso && new Date(iso) < new Date() }

    function formatRelative(iso) {
      const diff = new Date(iso).getTime() - new Date().getTime()
      const abs = Math.abs(diff)
      const past = diff < 0
      const mins = Math.floor(abs / 60_000)
      const hours = Math.floor(abs / 3_600_000)
      const days = Math.floor(abs / 86_400_000)
      let str
      if (mins < 60) str = `${mins}m`
      else if (hours < 24) str = `${hours}h`
      else str = `${days}d`
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
        conferences.value = conferences.value.filter((x) => (x.uuid || x.conferenceId) !== (c.uuid || c.conferenceId))
      } catch (e: any) {
        console.error('Error eliminando conferencia', e)
        c._deleting = false
      }
    }

    return {
      conferences, loading, deleteTarget, qrTarget, downloadCounts,
      isExpired, formatRelative, confirmDelete, doDelete
    }
  }
}
</script>

<style scoped>
.conferences-list-page { padding: 32px 24px; max-width: 1200px; }
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
h1 { color: #1e1b4b; margin: 0; font-size: 1.8rem; }

.section { margin-bottom: 32px; }
.loading-text { color: #6b7280; }
.empty-state { text-align: center; padding: 48px; background: #f9fafb; border-radius: 12px; }
.empty-state p { color: #6b7280; margin-bottom: 16px; }

.table-scroll { overflow-x: auto; }
.conferences-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; }
.conferences-table th {
  text-align: left; padding: 10px 14px; background: #f9fafb; color: #6b7280;
  font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
}
.conferences-table td { padding: 12px 14px; border-top: 1px solid #f3f4f6; vertical-align: top; font-size: 0.9rem; }

.friendly-id { font-size: 0.82rem; color: #6b7280; font-family: monospace; }
.status-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 99px; font-weight: 600; text-transform: uppercase; }
.status-badge.ACTIVE { background: #d1fae5; color: #065f46; }
.status-badge.CLOSED { background: #fee2e2; color: #991b1b; }

.expiry-text { color: #6b7280; font-size: 0.85rem; }
.expiry-text.expired { color: #dc2626; font-weight: 600; }
.downloads-text { color: #6b7280; font-size: 0.85rem; white-space: nowrap; }

.conf-actions { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

.btn-primary { display: inline-block; padding: 8px 18px; background: #4f46e5; color: #fff; border-radius: 8px; text-decoration: none; font-size: 0.875rem; font-weight: 500; border: none; cursor: pointer; }
.btn-primary:hover { background: #4338ca; }

.btn-outline { display: inline-block; padding: 6px 12px; border: 1px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 0.78rem; background: none; cursor: pointer; }
.btn-outline:hover { background: #eef2ff; }

.btn-ghost { display: inline-block; padding: 6px 12px; color: #6b7280; border: 1px solid #e5e7eb; border-radius: 8px; text-decoration: none; font-size: 0.78rem; }
.btn-ghost:hover { background: #f3f4f6; color: #374151; }

.btn-highlight {
  display: inline-block; padding: 6px 14px; background: #4f46e5; color: #fff;
  border: none; border-radius: 8px; text-decoration: none; font-size: 0.78rem;
  font-weight: 600; cursor: pointer;
}
.btn-highlight:hover { background: #4338ca; }

.btn-trash {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px;
  background: transparent; color: #9ca3af;
  border: 1px solid #e5e7eb; border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-trash:hover { background: #fee2e2; color: #dc2626; border-color: #fca5a5; }
.btn-trash:disabled { opacity: 0.4; cursor: not-allowed; }

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

@media (max-width: 768px) {
  .conferences-list-page { padding: 16px 14px; }
  .list-header { flex-direction: column; align-items: flex-start; }

  /* Tabla -> tarjetas apiladas en pantallas angostas */
  .conferences-table thead { display: none; }
  .conferences-table, .conferences-table tbody, .conferences-table tr, .conferences-table td {
    display: block; width: 100%;
  }
  .conferences-table tr {
    margin-bottom: 12px; border: 1px solid #e5e7eb; border-radius: 12px; padding: 8px 4px;
  }
  .conferences-table td {
    border-top: none; padding: 6px 10px;
  }
  .conferences-table td::before {
    content: attr(data-label); display: block; font-size: 0.7rem; font-weight: 600;
    text-transform: uppercase; color: #9ca3af; margin-bottom: 2px;
  }
}
</style>
