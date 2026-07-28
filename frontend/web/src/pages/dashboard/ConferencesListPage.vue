<template lang="pug">
.conferences-list-page
  DashboardBreadcrumb(:items="[{ label: 'Eventos' }]")

  .list-header
    h1 Eventos
    .header-actions
      router-link.link-btn.link-btn-secondary(v-if="isAdmin" to="/dashboard/admin/event-types") Tipos de evento
      router-link.link-btn.link-btn-primary(to="/dashboard/conferences/new") + Nuevo evento

  .section(v-if="loading")
    .loading-text Cargando eventos...

  .section(v-else-if="conferences.length === 0")
    .empty-state
      p Aún no tienes eventos.
      router-link.link-btn.link-btn-primary(to="/dashboard/conferences/new") Crear el primero

  .table-scroll(v-else)
    table.conferences-table
      thead
        tr
          th.qr-col(scope="col")
            span.sr-only QR
          th Nombre
          th Tipo
          th ID amigable
          th Estado
          th Expira
          th Descargas
          th Modos
          th Acciones
      tbody
        tr(v-for="c in conferences" :key="c.uuid || c.conferenceId")
          td.qr-col(data-label="QR")
            button.btn-icon(@click="qrTarget = c" title="Ver código QR")
              svg(xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round")
                rect(x="3" y="3" width="7" height="7")
                rect(x="14" y="3" width="7" height="7")
                rect(x="3" y="14" width="7" height="7")
                line(x1="14" y1="14" x2="14" y2="17")
                line(x1="14" y1="14" x2="17" y2="14")
                line(x1="17" y1="17" x2="21" y2="17")
                line(x1="21" y1="14" x2="21" y2="21")
                line(x1="14" y1="21" x2="17" y2="21")
          td(data-label="Nombre") {{ c.name }}
          td(data-label="Tipo")
            span.type-badge {{ eventTypeName(c.eventTypeKey) }}
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
          td(data-label="Modos")
            .conf-modes
              DropdownMenu(v-if="hasCapability(c, 'PRESENTATION') || hasCapability(c, 'SURVEY')" label="Presentador")
                router-link(v-if="hasCapability(c, 'PRESENTATION')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/speaker`") Presentar
                router-link(v-if="hasCapability(c, 'SURVEY')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/survey`") Encuesta
              a.link-btn.link-btn-ghost(v-if="hasCapability(c, 'PRESENTATION')" :href="`/c/${c.friendlyId}/presentation`" @click.prevent="openPublic(c)") 📺 Público
              DropdownMenu(v-if="hasCapability(c, 'WORD_CLOUD') || hasCapability(c, 'VIDEO_CONFERENCE') || hasCapability(c, 'CODE_IDE')" label="Moderación")
                router-link(v-if="hasCapability(c, 'WORD_CLOUD')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/messages`") Mensajes
                router-link(v-if="hasCapability(c, 'WORD_CLOUD')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/words`") Palabras/Nube
                router-link(v-if="hasCapability(c, 'CODE_IDE')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/ide`") Editor Monaco
                router-link(v-if="hasCapability(c, 'VIDEO_CONFERENCE') || hasCapability(c, 'CODE_IDE')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/device-blocks`") Bloqueos
          td.actions-cell(data-label="Acciones")
            .conf-actions
              router-link.link-btn.link-btn-ghost(v-if="hasCapability(c, 'PRESENTATION')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/presentation`") Presentación
              router-link.link-btn.link-btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/${c.certificateEngine === 'HTML_CHROME' ? 'certificate' : 'certificate-legacy'}`") 🏅 Certificado
              router-link.link-btn.link-btn-ghost(v-if="hasCapability(c, 'TICKETING_GENERAL') || hasCapability(c, 'TICKETING_SEATED')" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/tickets`") 🎟️ Boletos
              router-link.link-btn.link-btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/tools`") 🔒 Herramientas
              BaseButton(variant="ghost" v-if="!c.expiresAt" @click="toggleActive(c)" :disabled="c._togglingActive")
                | {{ c.status === 'ACTIVE' ? 'Desactivar' : 'Activar' }}
              router-link.link-btn.link-btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/edit`") Editor
              router-link.link-btn.link-btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/config`") Configuración
              template(v-if="c.seatingMode && c.seatingMode !== 'NONE'")
                router-link.link-btn.link-btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/check-in`") Check-in
                router-link.link-btn.link-btn-ghost(v-if="c.seatingMode === 'SEATED'" :to="`/dashboard/conferences/${c.uuid || c.conferenceId}/venue-map`") Mapa de asientos
              BaseButton(variant="danger" @click="confirmDelete(c)" :disabled="c._deleting" title="Eliminar conferencia")
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
         BaseButton(variant="secondary" @click="deleteTarget = null") Cancelar
         BaseButton(variant="danger" @click="doDelete") Eliminar

  QrCodeModal(v-if="qrTarget" :friendlyId="qrTarget.friendlyId" @close="qrTarget = null")
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getConferences, deleteConference, getDownloadCounts, getActiveEventTypes, setConferenceActive } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import QrCodeModal from '@/components/QrCodeModal.vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { isExpired } from '@/utils/dates'
import { eventTypeHasCapability } from '@/features/conferences/capabilities'
import type { Conference, DownloadCounts, EventType, EventCapability } from '@/services/api/types'

interface ConferenceRow extends Conference {
  conferenceId?: string
  _deleting?: boolean
  _togglingActive?: boolean
}

export default {
  name: 'ConferencesListPage',
  components: { QrCodeModal, DropdownMenu, DashboardBreadcrumb, BaseButton },
  setup() {
    const conferences = ref<ConferenceRow[]>([])
    const loading = ref(true)
    const deleteTarget = ref<ConferenceRow | null>(null)
    const qrTarget = ref<ConferenceRow | null>(null)
    const downloadCounts = ref<Record<string, DownloadCounts>>({})
    const eventTypes = ref<EventType[]>([])
    const auth = useAuthStore()
    const isAdmin = auth.isAdmin()

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
      try {
        eventTypes.value = await getActiveEventTypes()
      } catch (e: any) { /* la columna Tipo cae a la clave cruda si el catálogo no carga */ }
    })

    function eventTypeName(key?: string): string {
      return eventTypes.value.find((t) => t.key === key)?.name || key || '—'
    }

    function hasCapability(c: ConferenceRow, capability: EventCapability): boolean {
      return eventTypeHasCapability(eventTypes.value, c.eventTypeKey, capability)
    }

    async function loadDownloadCounts() {
      const token = auth.state.token as string
      await Promise.all(conferences.value.map(async (c) => {
        const id = (c.uuid || c.conferenceId) as string
        try {
          downloadCounts.value[id] = await getDownloadCounts(id, token)
        } catch (e: any) {
          downloadCounts.value[id] = { certificate: 0, presentation: 0 }
        }
      }))
    }

    function formatRelative(iso: string): string {
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

    async function toggleActive(c: ConferenceRow) {
      c._togglingActive = true
      try {
        const nextActive = c.status !== 'ACTIVE'
        const updated = await setConferenceActive((c.uuid || c.conferenceId) as string, nextActive, auth.state.token as string)
        c.status = updated.status
      } catch (e: any) {
        console.error('Error activando/desactivando conferencia', e)
      } finally {
        c._togglingActive = false
      }
    }

    function confirmDelete(c: ConferenceRow) { deleteTarget.value = c }

    function openPublic(conference: ConferenceRow) {
      const url = `/c/${conference.friendlyId}/presentation`
      const child = window.open(url, '_blank')
      if (!child) window.location.assign(url)
    }

    async function doDelete() {
      const c = deleteTarget.value
      if (!c) return
      c._deleting = true
      deleteTarget.value = null
      try {
        await deleteConference((c.uuid || c.conferenceId) as string, auth.state.token as string)
        conferences.value = conferences.value.filter((x) => (x.uuid || x.conferenceId) !== (c.uuid || c.conferenceId))
      } catch (e: any) {
        console.error('Error eliminando conferencia', e)
        c._deleting = false
      }
    }

    return {
      conferences, loading, deleteTarget, qrTarget, downloadCounts, isAdmin,
      isExpired, formatRelative, confirmDelete, doDelete, eventTypeName, hasCapability, toggleActive, openPublic
    }
  }
}
</script>

<style scoped>
.conferences-list-page { padding: 32px 24px; max-width: 1200px; }
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
h1 { color: #1e1b4b; margin: 0; font-size: 1.8rem; }
.header-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.type-badge { font-size: 0.75rem; background: #e0e7ff; color: #4338ca; padding: 3px 10px; border-radius: 10px; font-weight: 600; }

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

.qr-col { width: 40px; text-align: center; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.btn-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px;
  background: transparent; color: #4f46e5;
  border: 1px solid #e5e7eb; border-radius: 8px;
  cursor: pointer; transition: all 0.15s;
}
.btn-icon:hover { background: #eef2ff; border-color: #c7d2fe; }
.friendly-id { font-size: 0.82rem; color: #6b7280; font-family: monospace; }
.status-badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 99px; font-weight: 600; text-transform: uppercase; }
.status-badge.ACTIVE { background: #d1fae5; color: #065f46; }
.status-badge.CLOSED { background: #fee2e2; color: #991b1b; }

.expiry-text { color: #6b7280; font-size: 0.85rem; }
.expiry-text.expired { color: #dc2626; font-weight: 600; }
.downloads-text { color: #6b7280; font-size: 0.85rem; white-space: nowrap; }

.conf-actions { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.conf-modes { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

.btn-trash {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px;
  background: transparent; color: var(--color-text-muted);
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

@media (max-width: 768px) {
  .conferences-list-page { padding: 16px 14px; }
  .list-header { flex-direction: column; align-items: flex-start; }
  .header-actions { width: 100%; }
  .header-actions > * { flex: 1 1 0; text-align: center; min-width: 0; }

  /* Tabla -> tarjetas apiladas en pantallas angostas */
  .table-scroll { overflow: visible; }
  .conferences-table thead { display: none; }
  .conferences-table, .conferences-table tbody, .conferences-table tr, .conferences-table td {
    display: block; width: 100%;
  }
  .conferences-table tbody { display: grid; gap: 14px; }
  .conferences-table tr {
    position: relative; margin: 0; border: 1px solid #e5e7eb; border-radius: 14px;
    padding: 12px 14px; background: #fff; box-shadow: 0 2px 8px rgba(30, 27, 75, 0.06);
  }
  .conferences-table td {
    border-top: none; padding: 7px 0; min-width: 0;
    display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 10px; align-items: center;
  }
  .conferences-table td::before {
    content: attr(data-label); display: block; font-size: 0.68rem; font-weight: 700;
    letter-spacing: 0.03em; text-transform: uppercase; color: var(--color-text-muted);
  }
  .conferences-table td.qr-col {
    position: absolute; top: 12px; right: 14px; width: auto; padding: 0; display: block;
  }
  .conferences-table td.qr-col::before { display: none; }
  .conferences-table td[data-label="Nombre"] {
    display: block; padding: 2px 42px 12px 0; border-bottom: 1px solid #f3f4f6;
    font-size: 1rem; font-weight: 600; color: #1e1b4b; overflow-wrap: anywhere;
  }
  .conferences-table td[data-label="Nombre"]::before { margin-bottom: 3px; }
  .conferences-table td[data-label="ID amigable"] .friendly-id { overflow-wrap: anywhere; }
  .conferences-table td[data-label="Descargas"] .downloads-text { white-space: normal; }
  .conferences-table td.actions-cell {
    display: block; margin-top: 8px; padding: 12px 0 0; border-top: 1px solid #e5e7eb;
  }
  .conferences-table td.actions-cell::before { margin-bottom: 8px; }
  .conf-actions, .conf-modes { gap: 7px; }
  .conf-actions .btn-ghost, .conf-actions .btn-trash,
  .conf-modes .btn-ghost, .conf-modes :deep(.dropdown-trigger) {
    min-height: 36px; padding: 7px 10px; font-size: 0.76rem;
  }
  .conf-actions .btn-trash { width: 36px; }
  .type-badge, .status-badge { justify-self: start; }

  @media (max-width: 380px) {
    .conferences-list-page { padding-left: 10px; padding-right: 10px; }
    .header-actions { flex-direction: column; }
    .header-actions > * { flex-basis: auto; width: 100%; }
    .conferences-table td { grid-template-columns: 78px minmax(0, 1fr); gap: 8px; }
    .conf-actions .btn-ghost, .conf-modes .btn-ghost,
    .conf-modes :deep(.dropdown-trigger) { padding-left: 8px; padding-right: 8px; }
  }
}
</style>
