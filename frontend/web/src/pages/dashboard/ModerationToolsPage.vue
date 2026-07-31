<template lang="pug">
.mod-tools-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Candado por herramienta
  p.page-intro Las herramientas del evento y las acciones de entrega del IDE arrancan #[strong bloqueadas] para los asistentes hasta que las liberás acá. "Mi boleto" y "Flyer" siempre están visibles. Encuesta tiene su propio candado en la sección Encuesta.

  .release-all-card
    div
      strong ¿Todo bloqueado por error?
      p Libera todas las herramientas y acciones para todos los asistentes de una sola vez.
    BaseButton(variant="primary" :loading="releasingAll" @click="releaseAllTools") Liberar todo el evento

  LoadingState(v-if="loading" message="Cargando acceso de herramientas...")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")

  template(v-else)
    .tool-card(v-for="tool in tools" :key="tool.key")
      .tool-card-header
        div
          strong
            UiIcon(:name="tool.icon" size="18" aria-hidden="true")
            | {{ tool.label }}
          StatusBadge(
            :status="matrix[tool.key]?.releasedForAll ? 'ACTIVE' : 'INACTIVE'"
            :label="matrix[tool.key]?.releasedForAll ? 'Liberado para todos' : 'Bloqueado'"
          )
        ToggleSwitch(
          :model-value="!!matrix[tool.key]?.releasedForAll"
          :disabled="busyTool === tool.key"
          :loading="busyTool === tool.key"
          @update:modelValue="(v: boolean) => toggleAll(tool.key, v)"
        )
      BaseButton.toggle-attendees(variant="ghost" size="sm" type="button" @click="expanded[tool.key] = !expanded[tool.key]")
        | {{ expanded[tool.key] ? 'Ocultar' : 'Ver' }} asistentes individuales ({{ matrix[tool.key]?.attendees.length || 0 }})
      .attendee-list(v-if="expanded[tool.key]")
        EmptyState(v-if="!matrix[tool.key]?.attendees.length" message="Todavía no hay asistentes registrados en este evento.")
        .attendee-row(v-for="a in matrix[tool.key]?.attendees" :key="a.uuid")
          div
            strong {{ a.displayName || 'Sin nombre' }}
            span {{ a.email }}
          ToggleSwitch(
            :model-value="a.released"
            :disabled="matrix[tool.key]?.releasedForAll || busyTool === tool.key"
            :loading="busyTool === tool.key"
            @update:modelValue="(v: boolean) => toggleUser(tool.key, a.uuid, v)"
          )
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import ToggleSwitch from '@/components/ui/ToggleSwitch.vue'
import UiIcon from '@/components/ui/UiIcon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { getToolAccessManagement, releaseTool, lockTool, releaseAllTools as releaseAllToolsApi } from '@/services/api/usersApi'
import type { ToolKeyName, ToolManagementEntry } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

const TOOLS: { key: ToolKeyName, label: string, icon: string }[] = [
  { key: 'DOUBTS', label: 'Dudas', icon: 'help' },
  { key: 'TOPICS', label: 'Temas', icon: 'idea' },
  { key: 'PRESENTATION', label: 'Presentación', icon: 'presentation' },
  { key: 'CHAT', label: 'Chat', icon: 'chat' },
  { key: 'VIDEO', label: 'Videollamada', icon: 'video' },
  { key: 'DIAGRAMS', label: 'Diagramas', icon: 'diagram' },
  { key: 'WHITEBOARD', label: 'Pizarra', icon: 'whiteboard' },
  { key: 'NOTES', label: 'Notas', icon: 'notes' },
  { key: 'IDE', label: 'IDE de código', icon: 'code' },
  { key: 'IDE_DOWNLOAD', label: 'Descargar workspace', icon: 'download' },
  { key: 'IDE_PUBLISH_PAGE', label: 'Publicar página temporal', icon: 'globe' },
  { key: 'IDE_PUBLISH_API', label: 'Publicar backend/API', icon: 'api' }
]

export default {
  name: 'ModerationToolsPage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, BaseButton, ToggleSwitch, UiIcon, EmptyState, FeedbackMessage, LoadingState, StatusBadge },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const error = ref('')
    const releasingAll = ref(false)
    const busyTool = ref<ToolKeyName | ''>('')
    const matrix = ref<Partial<Record<ToolKeyName, ToolManagementEntry>>>({})
    const expanded = ref<Partial<Record<ToolKeyName, boolean>>>({})

    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      error.value = ''
      try {
        matrix.value = await getToolAccessManagement(props.conferenceId, auth.state.token as string)
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo cargar el estado de las herramientas'
      } finally {
        loading.value = false
      }
    }

    async function toggleAll(key: ToolKeyName, value: boolean) {
      if (!props.conferenceId) return
      busyTool.value = key
      try {
        if (value) await releaseTool(props.conferenceId, key, auth.state.token as string, true)
        else await lockTool(props.conferenceId, key, auth.state.token as string, true)
        await load()
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo actualizar la herramienta'
      } finally {
        busyTool.value = ''
      }
    }

    async function toggleUser(key: ToolKeyName, userUuid: string, value: boolean) {
      if (!props.conferenceId) return
      busyTool.value = key
      try {
        if (value) await releaseTool(props.conferenceId, key, auth.state.token as string, false, [userUuid])
        else await lockTool(props.conferenceId, key, auth.state.token as string, false, [userUuid])
        await load()
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo actualizar el acceso del asistente'
      } finally {
        busyTool.value = ''
      }
    }

    async function releaseAllTools() {
      if (!props.conferenceId) return
      releasingAll.value = true
      try {
        await releaseAllToolsApi(props.conferenceId, auth.state.token as string)
        await load()
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo liberar el evento'
      } finally {
        releasingAll.value = false
      }
    }

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: 'Candado por herramienta' }
    ])

    onMounted(load)

    return {
      tools: TOOLS, loading, error, releasingAll, busyTool, matrix, expanded,
      toggleAll, toggleUser, releaseAllTools, breadcrumbItems
    }
  }
}
</script>

<style scoped>
.mod-tools-page { padding: 24px; max-width: 800px; }
.page-intro { color: var(--color-text-secondary); margin-bottom: 20px; }

.release-all-card {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 16px 20px; border-radius: var(--radius-lg);
  background: var(--color-warning-soft); border: 1px solid var(--color-warning);
  margin-bottom: 24px;
}
.release-all-card p { margin: 2px 0 0; color: var(--color-text-secondary); font-size: 0.88rem; }

.tool-card {
  border: 1px solid var(--color-border-subtle); border-radius: var(--radius-lg);
  padding: 16px 20px; margin-bottom: 12px; background: var(--color-surface);
}
.tool-card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.tool-card-header strong { display: inline-flex; align-items: center; gap: 8px; }
.toggle-attendees { margin-top: var(--space-2); padding-left: 0; text-decoration: underline; }

.attendee-list { margin-top: 10px; border-top: 1px solid var(--color-surface-muted); padding-top: 10px; }
.attendee-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 8px 0;
}
.attendee-row div { display: flex; flex-direction: column; }
.attendee-row span { font-size: 0.8rem; color: var(--color-text-muted); }
</style>
