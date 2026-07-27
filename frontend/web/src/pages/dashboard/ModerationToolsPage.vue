<template lang="pug">
.mod-tools-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Candado por herramienta
  p.page-intro Dudas, Temas, Presentación, Chat, Videollamada, Diagramas, Pizarra, Notas e IDE arrancan #[strong bloqueados] para los asistentes hasta que las liberás acá. "Mi boleto" y "Flyer" siempre están visibles. Encuesta tiene su propio candado en la sección Encuesta.

  .release-all-card
    div
      strong ¿Todo bloqueado por error?
      p Libera las 9 herramientas para todos los asistentes de una sola vez.
    BaseButton(variant="primary" :loading="releasingAll" @click="releaseAllTools") Liberar todo el evento

  p.loading-text(v-if="loading") Cargando...
  p.error(v-if="error") {{ error }}

  .tool-card(v-for="tool in tools" :key="tool.key" v-else)
    .tool-card-header
      div
        strong {{ tool.icon }} {{ tool.label }}
        span.tool-status(:class="{ on: matrix[tool.key]?.releasedForAll }")
          | {{ matrix[tool.key]?.releasedForAll ? 'Liberado para todos' : 'Bloqueado' }}
      ToggleSwitch(
        :model-value="!!matrix[tool.key]?.releasedForAll"
        :disabled="busyTool === tool.key"
        @update:modelValue="(v: boolean) => toggleAll(tool.key, v)"
      )
    button.btn-link(type="button" @click="expanded[tool.key] = !expanded[tool.key]")
      | {{ expanded[tool.key] ? 'Ocultar' : 'Ver' }} asistentes individuales ({{ matrix[tool.key]?.attendees.length || 0 }})
    .attendee-list(v-if="expanded[tool.key]")
      p.empty-text(v-if="!matrix[tool.key]?.attendees.length") Todavía no hay asistentes registrados en este evento.
      .attendee-row(v-for="a in matrix[tool.key]?.attendees" :key="a.uuid")
        div
          strong {{ a.displayName || 'Sin nombre' }}
          span {{ a.email }}
        ToggleSwitch(
          :model-value="a.released"
          :disabled="matrix[tool.key]?.releasedForAll || busyTool === tool.key"
          @update:modelValue="(v: boolean) => toggleUser(tool.key, a.uuid, v)"
        )
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import ToggleSwitch from '@/components/ui/ToggleSwitch.vue'
import { getToolAccessManagement, releaseTool, lockTool, releaseAllTools as releaseAllToolsApi } from '@/services/api/usersApi'
import type { ToolKeyName, ToolManagementEntry } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

const TOOLS: { key: ToolKeyName, label: string, icon: string }[] = [
  { key: 'DOUBTS', label: 'Dudas', icon: '❓' },
  { key: 'TOPICS', label: 'Temas', icon: '💡' },
  { key: 'PRESENTATION', label: 'Presentación', icon: '📽️' },
  { key: 'CHAT', label: 'Chat', icon: '💬' },
  { key: 'VIDEO', label: 'Videollamada', icon: '🎥' },
  { key: 'DIAGRAMS', label: 'Diagramas', icon: '🧩' },
  { key: 'WHITEBOARD', label: 'Pizarra', icon: '🖍️' },
  { key: 'NOTES', label: 'Notas', icon: '🗒️' },
  { key: 'IDE', label: 'IDE de código', icon: '💻' }
]

export default {
  name: 'ModerationToolsPage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, BaseButton, ToggleSwitch },
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
.page-intro { color: var(--color-text-secondary, #374151); margin-bottom: 20px; }

.release-all-card {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 16px 20px; border-radius: var(--radius-lg, 12px);
  background: var(--color-warning-soft, #fef3c7); border: 1px solid #fde68a;
  margin-bottom: 24px;
}
.release-all-card p { margin: 2px 0 0; color: var(--color-text-secondary, #374151); font-size: 0.88rem; }

.loading-text { color: var(--color-text-muted, #6b7280); }
.error { color: var(--color-danger, #dc2626); }

.tool-card {
  border: 1px solid #e5e7eb; border-radius: var(--radius-lg, 12px);
  padding: 16px 20px; margin-bottom: 12px; background: #fff;
}
.tool-card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.tool-status {
  display: block; font-size: 0.8rem; margin-top: 2px; color: var(--color-text-muted, #6b7280);
}
.tool-status.on { color: var(--color-success, #166534); font-weight: 600; }

.btn-link {
  background: none; border: none; color: var(--color-primary, #4f46e5);
  font-size: 0.85rem; cursor: pointer; padding: 8px 0 0; text-decoration: underline;
}

.attendee-list { margin-top: 10px; border-top: 1px solid #f3f4f6; padding-top: 10px; }
.attendee-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 8px 0;
}
.attendee-row div { display: flex; flex-direction: column; }
.attendee-row span { font-size: 0.8rem; color: var(--color-text-muted, #6b7280); }
.empty-text { color: var(--color-text-muted, #6b7280); font-size: 0.88rem; }
</style>
