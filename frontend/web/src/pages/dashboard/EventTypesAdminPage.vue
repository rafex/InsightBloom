<template lang="pug">
.event-types-page
  h2 Tipos de evento

  .empty-state(v-if="!loading && eventTypes.length === 0")
    p No hay tipos de evento creados.

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
            span.status-badge(:class="{ active: t.active }") {{ t.active ? 'Activo' : 'Inactivo' }}
          td.actions(data-label="Acciones")
            template(v-if="editing === t.uuid")
              textarea(v-model="editForm.description" placeholder="Descripción")
              .actions-row
                button.btn-sm.btn-primary-sm(:disabled="saving" @click="saveEdit(t)") Guardar
                button.btn-sm.btn-ghost-sm(@click="editing = null") Cancelar
            template(v-else)
              button.btn-sm.btn-edit(@click="startEdit(t)") Editar
              button.btn-sm.btn-warning(v-if="t.active" @click="toggleActive(t, false)") Desactivar
              button.btn-sm.btn-success(v-else @click="toggleActive(t, true)") Activar

  .new-type-form
    h3 Nuevo tipo de evento
    .form-row
      input(v-model="newType.key" placeholder="Clave única, ej. standup")
      input(v-model="newType.name" placeholder="Nombre visible")
    textarea(v-model="newType.description" placeholder="Descripción (opcional)")
    .capabilities-editor
      label(v-for="c in allCapabilities" :key="c")
        input(type="checkbox" :value="c" v-model="newType.capabilities")
        span {{ capabilityLabel(c) }}
    button.btn-primary(type="button" :disabled="creating" @click="createNew") Crear tipo de evento
    p.error(v-if="createError") {{ createError }}
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import {
  getAllEventTypes, getEventCapabilities, createEventType, updateEventType, setEventTypeActive
} from '@/services/api/usersApi'
import type { EventType, EventCapability } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

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
  setup() {
    const auth = useAuthStore()
    const eventTypes = ref<EventType[]>([])
    const allCapabilities = ref<EventCapability[]>([])
    const loading = ref(true)
    const editing = ref<string | null>(null)
    const editForm = ref<{ name: string, description: string, capabilities: EventCapability[] }>(
      { name: '', description: '', capabilities: [] })
    const saving = ref(false)
    const newType = ref<{ key: string, name: string, description: string, capabilities: EventCapability[] }>(
      { key: '', name: '', description: '', capabilities: [] })
    const creating = ref(false)
    const createError = ref('')

    function capabilityLabel(c: string): string {
      return CAPABILITY_LABELS[c] || c
    }

    async function load() {
      loading.value = true
      try {
        const [types, capabilities] = await Promise.all([
          getAllEventTypes(auth.state.token as string),
          getEventCapabilities()
        ])
        eventTypes.value = types
        allCapabilities.value = capabilities
      } finally {
        loading.value = false
      }
    }

    function startEdit(t: EventType) {
      editing.value = t.uuid
      editForm.value = { name: t.name, description: t.description || '', capabilities: [...t.capabilities] }
    }

    async function saveEdit(t: EventType) {
      saving.value = true
      try {
        const updated = await updateEventType(
          t.uuid, editForm.value.name, editForm.value.description || null,
          editForm.value.capabilities, auth.state.token as string)
        Object.assign(t, updated)
        editing.value = null
      } finally {
        saving.value = false
      }
    }

    async function toggleActive(t: EventType, active: boolean) {
      const updated = await setEventTypeActive(t.uuid, active, auth.state.token as string)
      Object.assign(t, updated)
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
      } catch (e: any) {
        createError.value = e.response?.data?.error?.message || 'No se pudo crear el tipo de evento'
      } finally {
        creating.value = false
      }
    }

    onMounted(load)

    return {
      eventTypes, allCapabilities, loading, editing, editForm, saving, newType, creating, createError,
      capabilityLabel, startEdit, saveEdit, toggleActive, createNew
    }
  }
}
</script>

<style scoped>
.event-types-page { padding: 24px; max-width: 1100px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.empty-state { text-align: center; color: var(--color-text-muted); padding: 60px; }

.table-scroll { overflow-x: auto; margin-bottom: 32px; }
.types-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; }
.types-table th { text-align: left; padding: 10px 12px; background: #f9fafb; color: #6b7280; font-size: 0.78rem; font-weight: 600; text-transform: uppercase; }
.types-table td { padding: 10px 12px; border-top: 1px solid #f3f4f6; vertical-align: top; font-size: 0.88rem; }
.sub { font-size: 0.78rem; color: var(--color-text-muted); }

.capabilities-list { display: flex; flex-wrap: wrap; gap: 4px; }
.capability-chip { font-size: 0.72rem; background: #e0e7ff; color: #4338ca; padding: 2px 8px; border-radius: 10px; }

.capabilities-editor { display: flex; flex-direction: column; gap: 4px; }
.capabilities-editor label { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; }
.capabilities-editor input { width: auto; margin: 0; }

.status-badge { font-size: 0.78rem; font-weight: 600; padding: 2px 10px; border-radius: 10px; background: #f3f4f6; color: #6b7280; }
.status-badge.active { background: #dcfce7; color: #166534; }

.actions { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
.actions textarea { padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; min-height: 50px; }
.actions-row { display: flex; gap: 6px; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.8rem; }
.btn-edit { background: #e0e7ff; color: #4338ca; }
.btn-warning { background: #fef3c7; color: #92400e; }
.btn-success { background: #dcfce7; color: #166534; }
.btn-primary-sm { background: #4f46e5; color: #fff; }
.btn-ghost-sm { background: #fff; color: #6b7280; border: 1px solid #e5e7eb; }

.new-type-form { background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #e5e7eb; }
.new-type-form h3 { margin: 0 0 14px; color: #1e1b4b; font-size: 1rem; }
.form-row { display: flex; gap: 10px; margin-bottom: 10px; }
.form-row input { flex: 1; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.new-type-form textarea { width: 100%; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; margin-bottom: 10px; min-height: 60px; box-sizing: border-box; }
.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 0.95rem; margin-top: 10px; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: #dc2626; font-size: 0.85rem; margin-top: 8px; }

@media (max-width: 900px) {
  .event-types-page { padding: 14px; }
  .types-table thead { display: none; }
  .types-table, .types-table tbody, .types-table tr, .types-table td { display: block; width: 100%; }
  .types-table tr { margin-bottom: 12px; border: 1px solid #e5e7eb; border-radius: 12px; padding: 8px 4px; }
  .types-table td { border-top: none; padding: 8px 12px; }
  .types-table td::before {
    content: attr(data-label); display: block; font-size: 0.7rem; font-weight: 600;
    text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 4px;
  }
  .actions { min-width: 0; }
  .form-row { flex-direction: column; }
}
</style>
