<template lang="pug">
.event-types-page
  h2 Tipos de evento

  LoadingState(v-if="loading" message="Cargando tipos de evento…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="eventTypes.length === 0" message="No hay tipos de evento creados.")

  .table-scroll(v-else)
    table.types-table
      thead
        tr
          th Nombre
          th Clave
          th Capacidades
          th Estado
          th Acciones
      tbody
        tr(v-for="t in eventTypes" :key="t.uuid")
          td(data-label="Nombre")
            template(v-if="editing === t.uuid")
              input(v-model="editForm.name" placeholder="Nombre")
            template(v-else)
              strong {{ t.name }}
              .sub(v-if="t.description") {{ t.description }}
          td(data-label="Clave") {{ t.key }}
          td(data-label="Capacidades")
            .capabilities-editor(v-if="editing === t.uuid")
              label(v-for="c in allCapabilities" :key="c")
                input(type="checkbox" :value="c" v-model="editForm.capabilities")
                span {{ capabilityLabel(c) }}
            .capabilities-list(v-else)
              span.capability-chip(v-for="c in t.capabilities" :key="c") {{ capabilityLabel(c) }}
          td(data-label="Estado")
            StatusBadge(:status="t.active ? 'ACTIVE' : 'INACTIVE'" :label="t.active ? 'Activo' : 'Inactivo'")
          td.admin-actions(data-label="Acciones")
            template(v-if="editing === t.uuid")
              SaveState(:state="editSaveState")
              textarea(v-model="editForm.description" placeholder="Descripción")
              .admin-actions-row
                BaseButton(size="sm" :loading="saving" :disabled="saving || editSaveState === 'clean'" @click="saveEdit(t)") Guardar
                BaseButton(variant="ghost" size="sm" @click="editing = null") Cancelar
            template(v-else)
              BaseButton(variant="secondary" size="sm" @click="startEdit(t)") Editar
              BaseButton(variant="secondary" size="sm" v-if="t.active" :loading="actionLoadingUuid === t.uuid" :disabled="actionLoadingUuid === t.uuid" @click="toggleActive(t, false)") Desactivar
              BaseButton(variant="success" size="sm" v-else :loading="actionLoadingUuid === t.uuid" :disabled="actionLoadingUuid === t.uuid" @click="toggleActive(t, true)") Activar

  .new-type-form
    h3 Nuevo tipo de evento
    SaveState(:state="newTypeSaveState")
    .form-row
      input(v-model="newType.key" placeholder="Clave única, ej. standup")
      input(v-model="newType.name" placeholder="Nombre visible")
    textarea(v-model="newType.description" placeholder="Descripción (opcional)")
    .capabilities-editor
      label(v-for="c in allCapabilities" :key="c")
        input(type="checkbox" :value="c" v-model="newType.capabilities")
        span {{ capabilityLabel(c) }}
    BaseButton(type="button" :loading="creating" :disabled="creating || newTypeSaveState === 'clean'" @click="createNew") Crear tipo de evento
    FeedbackMessage(v-if="createError" :message="createError" tone="error")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getAllEventTypes, getEventCapabilities, createEventType, updateEventType, setEventTypeActive
} from '@/services/api/usersApi'
import type { EventType, EventCapability } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import SaveState from '@/components/ui/SaveState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

const CAPABILITY_LABELS: Record<string, string> = {
  TICKETING_GENERAL: 'Boletos (aforo)',
  TICKETING_SEATED: 'Boletos (asientos)',
  SURVEY: 'Encuestas',
  PRESENTATION: 'Presentación',
  WORD_CLOUD: 'Nube de palabras',
  CHAT_BOT: 'Chat / Bot',
  VIDEO_CONFERENCE: 'Videollamada',
  WHITEBOARD: 'Pizarra',
  DIAGRAMMING: 'Diagramas',
  COLLAB_NOTES: 'Notas colaborativas',
  CODE_IDE: 'IDE'
}

export default {
  name: 'EventTypesAdminPage',
  components: { BaseButton, EmptyState, FeedbackMessage, LoadingState, SaveState, StatusBadge },
  setup() {
    const auth = useAuthStore()
    const eventTypes = ref<EventType[]>([])
    const allCapabilities = ref<EventCapability[]>([])
    const loading = ref(true)
    const error = ref('')
    const editing = ref<string | null>(null)
    const editForm = ref<{ name: string, description: string, capabilities: EventCapability[] }>(
      { name: '', description: '', capabilities: [] })
    const initialEditSnapshot = ref('')
    const saving = ref(false)
    const newType = ref<{ key: string, name: string, description: string, capabilities: EventCapability[] }>(
      { key: '', name: '', description: '', capabilities: [] })
    const creating = ref(false)
    const createError = ref('')
    const initialNewTypeSnapshot = ref('')
    const actionLoadingUuid = ref<string | null>(null)

    function editSnapshot(): string {
      return JSON.stringify({
        name: editForm.value.name,
        description: editForm.value.description,
        capabilities: editForm.value.capabilities
      })
    }

    function newTypeSnapshot(): string {
      return JSON.stringify({
        key: newType.value.key,
        name: newType.value.name,
        description: newType.value.description,
        capabilities: newType.value.capabilities
      })
    }

    const editSaveState = computed(() => {
      if (saving.value) return 'saving'
      return editSnapshot() === initialEditSnapshot.value ? 'clean' : 'dirty'
    })
    const newTypeSaveState = computed(() => {
      if (creating.value) return 'saving'
      return newTypeSnapshot() === initialNewTypeSnapshot.value ? 'clean' : 'dirty'
    })

    initialNewTypeSnapshot.value = newTypeSnapshot()

    function capabilityLabel(c: string): string {
      return CAPABILITY_LABELS[c] || c
    }

    async function load() {
      loading.value = true
      error.value = ''
      try {
        const [types, capabilities] = await Promise.all([
          getAllEventTypes(auth.state.token as string),
          getEventCapabilities()
        ])
        eventTypes.value = types
        allCapabilities.value = capabilities
      } catch (e: any) {
        error.value = 'No fue posible cargar los tipos de evento. Inténtalo nuevamente.'
      } finally {
        loading.value = false
      }
    }

    function startEdit(t: EventType) {
      editing.value = t.uuid
      editForm.value = { name: t.name, description: t.description || '', capabilities: [...t.capabilities] }
      initialEditSnapshot.value = editSnapshot()
    }

    async function saveEdit(t: EventType) {
      saving.value = true
      error.value = ''
      try {
        const updated = await updateEventType(
          t.uuid, editForm.value.name, editForm.value.description || null,
          editForm.value.capabilities, auth.state.token as string)
        Object.assign(t, updated)
        initialEditSnapshot.value = editSnapshot()
        editing.value = null
      } catch (e: any) {
        error.value = 'No fue posible guardar el tipo de evento. Inténtalo nuevamente.'
      } finally {
        saving.value = false
      }
    }

    async function toggleActive(t: EventType, active: boolean) {
      if (actionLoadingUuid.value) return
      actionLoadingUuid.value = t.uuid
      try {
        error.value = ''
        const updated = await setEventTypeActive(t.uuid, active, auth.state.token as string)
        Object.assign(t, updated)
      } catch (e: any) {
        error.value = 'No fue posible actualizar el estado del tipo de evento. Inténtalo nuevamente.'
      } finally {
        actionLoadingUuid.value = null
      }
    }

    async function createNew() {
      createError.value = ''
      creating.value = true
      try {
        const created = await createEventType(
          newType.value.key, newType.value.name, newType.value.description || null,
          newType.value.capabilities, auth.state.token as string)
        eventTypes.value.push(created)
        newType.value = { key: '', name: '', description: '', capabilities: [] }
        initialNewTypeSnapshot.value = newTypeSnapshot()
      } catch (e: any) {
        createError.value = e.response?.data?.error?.message || 'No se pudo crear el tipo de evento'
      } finally {
        creating.value = false
      }
    }

    onMounted(load)

    return {
      eventTypes, allCapabilities, loading, error, editing, editForm, saving, editSaveState, newType, creating, createError, newTypeSaveState, actionLoadingUuid,
      capabilityLabel, startEdit, saveEdit, toggleActive, createNew
    }
  }
}
</script>

<style scoped>
.event-types-page { padding: 24px; max-width: 1100px; }
h2 { color: var(--color-heading); margin-bottom: 20px; }
.table-scroll { margin-bottom: 32px; }
.types-table { width: 100%; border-collapse: collapse; background: var(--color-surface); border-radius: 12px; overflow: hidden; }
.types-table th { text-align: left; padding: 10px 12px; background: var(--color-surface-muted); color: var(--color-text-muted); font-size: 0.78rem; font-weight: 600; text-transform: uppercase; }
.types-table td { padding: 10px 12px; border-top: 1px solid var(--color-surface-muted); vertical-align: top; font-size: 0.88rem; }
.sub { font-size: 0.78rem; color: var(--color-text-muted); }

.capabilities-list { display: flex; flex-wrap: wrap; gap: 4px; }
.capability-chip { font-size: 0.72rem; background: var(--color-primary-soft); color: var(--color-primary-dark); padding: 2px 8px; border-radius: 10px; }

.capabilities-editor { display: flex; flex-direction: column; gap: 4px; }
.capabilities-editor label { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; }
.capabilities-editor input { width: auto; margin: 0; }

.admin-actions textarea { font-size: 0.82rem; min-height: 50px; }

.new-type-form { background: var(--color-surface); border-radius: 12px; padding: 20px; border: 1px solid var(--color-border-subtle); }
.new-type-form h3 { margin: 0 0 14px; color: var(--color-heading); font-size: 1rem; }
.form-row { display: flex; gap: 10px; margin-bottom: 10px; }
.form-row input { flex: 1; font-size: 0.9rem; }
.new-type-form textarea { width: 100%; font-size: 0.9rem; margin-bottom: 10px; min-height: 60px; box-sizing: border-box; }
@media (max-width: 900px) {
  .event-types-page { padding: 14px; }
  .types-table thead { display: none; }
  .types-table, .types-table tbody, .types-table tr, .types-table td { display: block; width: 100%; }
  .types-table tr { margin-bottom: 12px; border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 8px 4px; }
  .types-table td { border-top: none; padding: 8px 12px; }
  .types-table td::before {
    content: attr(data-label); display: block; font-size: 0.7rem; font-weight: 600;
    text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 4px;
  }
  .admin-actions { min-width: 0; }
  .form-row { flex-direction: column; }
}
</style>
