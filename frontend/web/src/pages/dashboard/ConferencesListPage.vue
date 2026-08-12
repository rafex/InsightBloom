<template lang="pug">
.conferences-list-page
  DashboardBreadcrumb(:items="[{ label: 'Eventos' }]")

  .list-header
    h1 Eventos
    .header-actions
      BaseLink(v-if="isAdmin" variant="secondary" to="/dashboard/admin/event-types") Tipos de evento
      BaseLink(v-if="isOrganizer" to="/dashboard/events/new") + Nuevo evento

  .section(v-if="loading")
    LoadingState(message="Cargando eventos…")

  FeedbackMessage(v-else-if="error" :message="error" tone="error")

  template(v-else)
    h2.section-title Organizo
    .section(v-if="conferences.length === 0")
      EmptyState(:message="isOrganizer ? 'Aún no tienes eventos.' : 'No tienes eventos asignados.'")
        BaseLink(v-if="isOrganizer" to="/dashboard/events/new") Crear el primero

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
              BaseButton.qr-button(variant="ghost" size="sm" @click="qrTarget = c" title="Ver código QR" aria-label="Ver código QR")
                svg(xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true")
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
              StatusBadge(:status="c.status" :tone="c.status === 'ACTIVE' ? 'success' : c.status === 'CLOSED' ? 'danger' : 'neutral'")
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
                  router-link(v-if="hasCapability(c, 'PRESENTATION')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/speaker`") Presentar
                  router-link(v-if="hasCapability(c, 'SURVEY')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/survey`") Encuesta
                BaseAnchor(v-if="hasCapability(c, 'PRESENTATION')" variant="ghost" :href="`/c/${c.friendlyId}/presentation`" @click.prevent="openPublic(c)") 📺 Público
                DropdownMenu(v-if="hasCapability(c, 'WORD_CLOUD') || hasCapability(c, 'VIDEO_CONFERENCE') || hasCapability(c, 'CODE_IDE')" label="Moderación")
                  router-link(v-if="hasCapability(c, 'WORD_CLOUD')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/moderation/messages`") Mensajes
                  router-link(v-if="hasCapability(c, 'WORD_CLOUD')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/moderation/words`") Palabras/Nube
                  router-link(v-if="hasCapability(c, 'CODE_IDE')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/moderation/ide`") Editor Monaco
                  BaseAnchor.menu-video-link(
                    v-if="hasCapability(c, 'VIDEO_CONFERENCE')"
                    variant="ghost"
                    size="sm"
                    :href="`/c/${c.friendlyId}/video`"
                    target="_blank"
                    rel="opener"
                    title="Abrir la videollamada en una pestaña nueva"
                    aria-label="Entrar a la videollamada (abre en una pestaña nueva)"
                  ) Videollamada ↗
                  router-link(v-if="isOrganizer && (hasCapability(c, 'VIDEO_CONFERENCE') || hasCapability(c, 'CODE_IDE'))" :to="`/dashboard/events/${c.uuid || c.conferenceId}/device-blocks`") Bloqueos
            td.actions-cell(data-label="Acciones")
              .conf-actions
                BaseLink(v-if="isOrganizer" size="sm" :to="`/dashboard/events/${c.uuid || c.conferenceId}/config`") Abrir evento
                BaseLink(v-else size="sm" :to="`/dashboard/events/${c.uuid || c.conferenceId}/moderation/tools`") Abrir moderación
                DropdownMenu(v-if="isOrganizer" label="Gestionar")
                  router-link(:to="`/dashboard/events/${c.uuid || c.conferenceId}/edit`") Editar información
                  router-link(:to="`/dashboard/events/${c.uuid || c.conferenceId}/config`") Configuración
                  router-link(v-if="hasCapability(c, 'PRESENTATION')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/presentation`") Gestionar presentación
                  router-link(v-if="hasCapability(c, 'ON_DEMAND_VIDEO')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/on-demand`") Video on-demand
                  router-link(:to="`/dashboard/events/${c.uuid || c.conferenceId}/${c.certificateEngine === 'HTML_CHROME' ? 'certificate' : 'certificate-legacy'}`") Certificado
                  router-link(v-if="hasCapability(c, 'TICKETING_GENERAL') || hasCapability(c, 'TICKETING_SEATED')" :to="`/dashboard/events/${c.uuid || c.conferenceId}/tickets`") Boletos
                  router-link(:to="`/dashboard/events/${c.uuid || c.conferenceId}/moderation/tools`") Herramientas
                  router-link(v-if="c.seatingMode && c.seatingMode !== 'NONE'" :to="`/dashboard/events/${c.uuid || c.conferenceId}/check-in`") Check-in
                  router-link(v-if="c.seatingMode === 'SEATED'" :to="`/dashboard/events/${c.uuid || c.conferenceId}/venue-map`") Mapa de asientos
                DropdownMenu(label="Más")
                  button.menu-item(v-if="!c.expiresAt" type="button" @click="toggleActive(c)" :disabled="c._togglingActive")
                    | {{ c.status === 'ACTIVE' ? 'Desactivar evento' : 'Activar evento' }}
                  button.menu-item.menu-item-danger(type="button" @click="confirmDelete(c)" :disabled="c._deleting") Eliminar evento

    h2.section-title.section-title-inscrito Inscrito
    .section(v-if="loadingHistory")
      LoadingState(message="Cargando inscripciones…")
    .section(v-else-if="attendeeHistory.length === 0")
      EmptyState(message="No estás inscrito a otros eventos.")
    .table-scroll(v-else)
      table.conferences-table.conferences-table-history
        thead
          tr
            th Nombre
            th ID amigable
            th Estado
            th Inscrito
            th Acciones
        tbody
          tr(v-for="h in attendeeHistory" :key="h.conferenceUuid")
            td(data-label="Nombre") {{ h.name || '(sin nombre)' }}
            td(data-label="ID amigable")
              span.friendly-id {{ h.friendlyId || h.conferenceUuid }}
            td(data-label="Estado")
              StatusBadge(:status="h.available ? 'ACTIVE' : 'UNAVAILABLE'" :label="h.available ? 'Disponible' : 'No disponible'" :tone="h.available ? 'success' : 'neutral'")
            td(data-label="Inscrito") {{ formatDate(h.joinedAt) }}
            td.actions-cell(data-label="Acciones")
              .conf-actions(v-if="h.available")
                BaseLink(size="sm" :to="`/c/${h.friendlyId}/doubts`") Entrar
                BaseLink(size="sm" variant="secondary" v-if="h.seatingMode && h.seatingMode !== 'NONE'" :to="`/c/${h.friendlyId}/ticket`") Mi boleto
              span.unavailable-note(v-else) No disponible

  BaseModal(
    v-if="deleteTarget"
    title="¿Eliminar conferencia?"
    confirm-label="Eliminar"
    confirm-variant="danger"
    @close="deleteTarget = null"
    @confirm="doDelete"
  )
    p Esto borrará permanentemente <strong>{{ deleteTarget.name }}</strong> y no se puede deshacer.

  QrCodeModal(v-if="qrTarget" :friendlyId="qrTarget.friendlyId" :url="ticketUrl(qrTarget.friendlyId)" @close="qrTarget = null")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getConferences, getConferenceHistory, deleteConference, getDownloadCounts, getActiveEventTypes, setConferenceActive } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import QrCodeModal from '@/components/QrCodeModal.vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseAnchor from '@/components/ui/BaseAnchor.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { isExpired } from '@/utils/dates'
import { eventTypeHasCapability } from '@/features/conferences/capabilities'
import type { Conference, ConferenceHistoryEntry, DownloadCounts, EventType, EventCapability } from '@/services/api/types'

interface ConferenceRow extends Conference {
  conferenceId?: string
  _deleting?: boolean
  _togglingActive?: boolean
}

export default {
  name: 'ConferencesListPage',
  components: { QrCodeModal, DropdownMenu, DashboardBreadcrumb, BaseAnchor, BaseButton, BaseLink, BaseModal, EmptyState, FeedbackMessage, LoadingState, StatusBadge },
  setup() {
    const conferences = ref<ConferenceRow[]>([])
    const loading = ref(true)
    const error = ref('')
    const deleteTarget = ref<ConferenceRow | null>(null)
    const qrTarget = ref<ConferenceRow | null>(null)
    const downloadCounts = ref<Record<string, DownloadCounts>>({})
    const eventTypes = ref<EventType[]>([])
    const auth = useAuthStore()
    const isAdmin = auth.isAdmin()
    const isOrganizer = auth.isOrganizer()

    // "Inscrito": eventos donde el usuario se registró/inscribió como asistente, aparte de los
    // que organiza/modera arriba. Mismo endpoint self-service y mismo criterio de deduplicación
    // (excluir lo que ya aparece en "Organizo") que ya usa DashboardHome.vue.
    const history = ref<ConferenceHistoryEntry[]>([])
    const loadingHistory = ref(true)
    const attendeeHistory = computed(() => {
      const managedConferenceUuids = new Set(conferences.value.map((conference) => conference.uuid))
      return history.value.filter((entry) => !managedConferenceUuids.has(entry.conferenceUuid))
    })

    onMounted(async () => {
      try {
        if (auth.state.token) {
          conferences.value = await getConferences(auth.state.token)
          loadDownloadCounts()
        }
      } catch (e: any) {
        error.value = 'No fue posible cargar los eventos. Inténtalo nuevamente.'
      } finally {
        loading.value = false
      }
      try {
        eventTypes.value = await getActiveEventTypes()
      } catch (e: any) { /* la columna Tipo cae a la clave cruda si el catálogo no carga */ }
      try {
        if (auth.state.token) history.value = await getConferenceHistory(auth.state.token)
      } catch (e: any) { /* la seccion "Inscrito" simplemente queda vacia si falla */ } finally {
        loadingHistory.value = false
      }
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

    function formatDate(iso: string | null | undefined): string {
      if (!iso) return ''
      return new Date(iso).toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' })
    }

    async function toggleActive(c: ConferenceRow) {
      c._togglingActive = true
      try {
        const nextActive = c.status !== 'ACTIVE'
        const updated = await setConferenceActive((c.uuid || c.conferenceId) as string, nextActive, auth.state.token as string)
        c.status = updated.status
      } catch (e: any) {
        error.value = 'No fue posible actualizar el estado del evento. Inténtalo nuevamente.'
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

    function ticketUrl(friendlyId: string): string {
      return `${window.location.origin}/c/${friendlyId}/ticket`
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
        c._deleting = false
        error.value = 'No fue posible eliminar el evento. Inténtalo nuevamente.'
      }
    }

    return {
      conferences, loading, error, deleteTarget, qrTarget, downloadCounts, isAdmin, isOrganizer,
      history, loadingHistory, attendeeHistory, formatDate,
      isExpired, formatRelative, confirmDelete, doDelete, eventTypeName, hasCapability, toggleActive, openPublic, ticketUrl
    }
  }
}
</script>

<style scoped>
.conferences-list-page { padding: 32px 24px; max-width: 1200px; }
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
h1 { color: var(--color-heading); margin: 0; font-size: 1.8rem; }
.header-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.type-badge { font-size: 0.75rem; background: var(--color-primary-soft); color: var(--color-primary-dark); padding: 3px 10px; border-radius: 10px; font-weight: 600; }

.section-title { color: var(--color-text-secondary); font-size: 1.1rem; font-weight: 600; margin: 0 0 16px; }
.section-title-inscrito { margin-top: 36px; }
.unavailable-note { font-size: 0.82rem; color: var(--color-text-muted); font-style: italic; }

.section { margin-bottom: 32px; }
.conferences-table { width: 100%; border-collapse: collapse; background: var(--color-surface); border-radius: 12px; overflow: hidden; }
.conferences-table th {
  text-align: left; padding: 10px 14px; background: var(--color-surface-muted); color: var(--color-text-muted);
  font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
}
.conferences-table td { padding: 12px 14px; border-top: 1px solid var(--color-surface-muted); vertical-align: top; font-size: 0.9rem; }

.qr-col { width: 40px; text-align: center; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.qr-button { width: 30px; height: 30px; padding: 4px; border: 1px solid var(--color-border-subtle); color: var(--color-primary); }
.friendly-id { font-size: 0.82rem; color: var(--color-text-muted); font-family: monospace; }
.expiry-text { color: var(--color-text-muted); font-size: 0.85rem; }
.expiry-text.expired { color: var(--color-danger); font-weight: 600; }
.downloads-text { color: var(--color-text-muted); font-size: 0.85rem; white-space: nowrap; }

.conf-actions { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.conf-modes { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }

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
    position: relative; margin: 0; border: 1px solid var(--color-border-subtle); border-radius: 14px;
    padding: 12px 14px; background: var(--color-surface); box-shadow: 0 2px 8px rgba(30, 27, 75, 0.06);
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
    display: block; padding: 2px 42px 12px 0; border-bottom: 1px solid var(--color-surface-muted);
    font-size: 1rem; font-weight: 600; color: var(--color-heading); overflow-wrap: anywhere;
  }
  .conferences-table td[data-label="Nombre"]::before { margin-bottom: 3px; }
  .conferences-table td[data-label="ID amigable"] .friendly-id { overflow-wrap: anywhere; }
  .conferences-table td[data-label="Descargas"] .downloads-text { white-space: normal; }
  .conferences-table td.actions-cell {
    display: block; margin-top: 8px; padding: 12px 0 0; border-top: 1px solid var(--color-border-subtle);
  }
  .conferences-table td.actions-cell::before { margin-bottom: 8px; }
  .conf-actions, .conf-modes { gap: 7px; }
  .conf-actions .base-anchor, .conf-actions :deep(.dropdown-trigger),
  .conf-modes :deep(.dropdown-trigger) {
    min-height: 36px; padding: 7px 10px; font-size: 0.76rem;
  }
  .type-badge, .status-badge { justify-self: start; }

  @media (max-width: 380px) {
    .conferences-list-page { padding-left: 10px; padding-right: 10px; }
    .header-actions { flex-direction: column; }
    .header-actions > * { flex-basis: auto; width: 100%; }
    .conferences-table td { grid-template-columns: 78px minmax(0, 1fr); gap: 8px; }
    .conf-actions .base-anchor, .conf-actions :deep(.dropdown-trigger),
    .conf-modes :deep(.dropdown-trigger) { padding-left: 8px; padding-right: 8px; }
  }
}
</style>
