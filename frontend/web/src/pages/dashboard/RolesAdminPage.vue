<template lang="pug">
.roles-page
  h2 Roles

  .empty-state(v-if="!loading && roles.length === 0")
    p No hay roles creados.

  .table-scroll(v-else)
    table.roles-table
      thead
        tr
          th Nombre
          th Clave
          th Alcance
          th Permisos
          th Estado
          th Acciones
      tbody
        tr(v-for="r in roles" :key="r.uuid")
          td(data-label="Nombre")
            template(v-if="editing === r.uuid")
              input(v-model="editForm.name" placeholder="Nombre")
            template(v-else)
              strong {{ r.name }}
              .sub(v-if="r.description") {{ r.description }}
          td(data-label="Clave") {{ r.key }}
          td(data-label="Alcance")
            span.scope-badge(:class="r.scope.toLowerCase()") {{ r.scope === 'PLATFORM' ? 'Plataforma' : 'Evento' }}
          td(data-label="Permisos")
            .permissions-editor(v-if="editing === r.uuid")
              label(v-for="p in allPermissions" :key="p")
                input(type="checkbox" :value="p" v-model="editForm.permissions")
                span {{ permissionLabel(p) }}
            .permissions-list(v-else)
              span.permission-chip(v-for="p in r.permissions" :key="p") {{ permissionLabel(p) }}
          td(data-label="Estado")
            span.status-badge(:class="{ active: r.active }") {{ r.active ? 'Activo' : 'Inactivo' }}
          td.actions(data-label="Acciones")
            template(v-if="editing === r.uuid")
              textarea(v-model="editForm.description" placeholder="Descripción")
              .actions-row
                BaseButton(size="sm" :disabled="saving" @click="saveEdit(r)") Guardar
                BaseButton(variant="ghost" size="sm" @click="editing = null") Cancelar
            template(v-else)
              BaseButton(variant="secondary" size="sm" @click="startEdit(r)") Editar
              BaseButton(variant="secondary" size="sm" v-if="r.active" @click="confirmToggleActive(r, false)") Desactivar
              BaseButton(variant="secondary" size="sm" v-else @click="confirmToggleActive(r, true)") Activar

  .new-role-form
    h3 Nuevo rol
    .form-row
      input(v-model="newRole.key" placeholder="Clave única, ej. staff_coordinator")
      input(v-model="newRole.name" placeholder="Nombre visible")
    .form-row
      select(v-model="newRole.scope")
        option(value="EVENT") Alcance: Evento
        option(value="PLATFORM") Alcance: Plataforma
    textarea(v-model="newRole.description" placeholder="Descripción (opcional)")
    .permissions-editor
      label(v-for="p in allPermissions" :key="p")
        input(type="checkbox" :value="p" v-model="newRole.permissions")
        span {{ permissionLabel(p) }}
    BaseButton(type="button" :disabled="creating" @click="createNew") Crear rol
    p.error(v-if="createError") {{ createError }}

  .confirm-overlay(v-if="pendingRole" @click.self="pendingRole = null")
    .confirm-dialog
      h4 {{ pendingRoleActive ? '¿Activar rol?' : '¿Desactivar rol?' }}
      p {{ pendingRoleActive ? 'El rol volverá a estar disponible para asignarse.' : 'El rol no podrá asignarse mientras esté inactivo.' }}
      .confirm-actions
        BaseButton(variant="ghost" @click="pendingRole = null") Cancelar
        BaseButton(variant="primary" @click="runToggleActive") Confirmar
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import {
  getAllRoles, getPermissionsCatalog, createRole, updateRole, setRoleActive
} from '@/services/api/usersApi'
import type { Role, PermissionValue, RoleScopeValue } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'

const PERMISSION_LABELS: Record<string, string> = {
  MANAGE_USERS: 'Gestionar usuarios',
  MANAGE_EVENT_TYPES: 'Gestionar tipos de evento',
  HOST_EVENT: 'Crear eventos',
  MANAGE_EVENT_SETTINGS: 'Editar configuración del evento',
  ASSIGN_EVENT_ROLES: 'Asignar roles del evento',
  MODERATE_CONTENT: 'Moderar contenido',
  CHECK_IN: 'Check-in de boletos',
  MANAGE_TICKETS: 'Emitir y administrar boletos',
  MANAGE_PRESENTATION: 'Controlar presentación',
  MANAGE_SURVEY: 'Gestionar encuesta',
  MANAGE_CERTIFICATE: 'Configurar certificado',
  VIDEO_MODERATE: 'Moderar videollamada'
}

export default {
  name: 'RolesAdminPage',
  components: { BaseButton },
  setup() {
    const auth = useAuthStore()
    const roles = ref<Role[]>([])
    const allPermissions = ref<PermissionValue[]>([])
    const loading = ref(true)
    const editing = ref<string | null>(null)
    const editForm = ref<{ name: string, description: string, permissions: PermissionValue[] }>(
      { name: '', description: '', permissions: [] })
    const saving = ref(false)
    const newRole = ref<{ key: string, name: string, description: string, scope: RoleScopeValue, permissions: PermissionValue[] }>(
      { key: '', name: '', description: '', scope: 'EVENT', permissions: [] })
    const creating = ref(false)
    const createError = ref('')
    const pendingRole = ref<Role | null>(null)
    const pendingRoleActive = ref(false)

    function permissionLabel(p: string): string {
      return PERMISSION_LABELS[p] || p
    }

    async function load() {
      loading.value = true
      try {
        const [roleList, permissions] = await Promise.all([
          getAllRoles(auth.state.token as string),
          getPermissionsCatalog()
        ])
        roles.value = roleList
        allPermissions.value = permissions
      } finally {
        loading.value = false
      }
    }

    function startEdit(r: Role) {
      editing.value = r.uuid
      editForm.value = { name: r.name, description: r.description || '', permissions: [...r.permissions] }
    }

    async function saveEdit(r: Role) {
      saving.value = true
      try {
        const updated = await updateRole(
          r.uuid, editForm.value.name, editForm.value.description || null,
          editForm.value.permissions, auth.state.token as string)
        Object.assign(r, updated)
        editing.value = null
      } finally {
        saving.value = false
      }
    }

    async function toggleActive(r: Role, active: boolean) {
      const updated = await setRoleActive(r.uuid, active, auth.state.token as string)
      Object.assign(r, updated)
    }

    function confirmToggleActive(r: Role, active: boolean) {
      pendingRole.value = r
      pendingRoleActive.value = active
    }

    async function runToggleActive() {
      const role = pendingRole.value
      const active = pendingRoleActive.value
      pendingRole.value = null
      if (role) await toggleActive(role, active)
    }

    async function createNew() {
      createError.value = ''
      creating.value = true
      try {
        const created = await createRole(
          newRole.value.key, newRole.value.name, newRole.value.description || null,
          newRole.value.scope, newRole.value.permissions, auth.state.token as string)
        roles.value.push(created)
        newRole.value = { key: '', name: '', description: '', scope: 'EVENT', permissions: [] }
      } catch (e: any) {
        createError.value = e.response?.data?.error?.message || 'No se pudo crear el rol'
      } finally {
        creating.value = false
      }
    }

    onMounted(load)

    return {
      roles, allPermissions, loading, editing, editForm, saving, newRole, creating, createError,
      pendingRole, pendingRoleActive,
      permissionLabel, startEdit, saveEdit, toggleActive, confirmToggleActive, runToggleActive, createNew
    }
  }
}
</script>

<style scoped>
.roles-page { padding: 24px; max-width: 1200px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.empty-state { text-align: center; color: var(--color-text-muted); padding: 60px; }

.table-scroll { overflow-x: auto; margin-bottom: 32px; }
.roles-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; }
.roles-table th { text-align: left; padding: 10px 12px; background: #f9fafb; color: #6b7280; font-size: 0.78rem; font-weight: 600; text-transform: uppercase; }
.roles-table td { padding: 10px 12px; border-top: 1px solid #f3f4f6; vertical-align: top; font-size: 0.88rem; }
.sub { font-size: 0.78rem; color: var(--color-text-muted); }

.scope-badge { font-size: 0.72rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; background: #f3f4f6; color: #6b7280; }
.scope-badge.platform { background: #fef3c7; color: #92400e; }
.scope-badge.event { background: #e0e7ff; color: #4338ca; }

.permissions-list { display: flex; flex-wrap: wrap; gap: 4px; max-width: 260px; }
.permission-chip { font-size: 0.7rem; background: #ecfdf5; color: #065f46; padding: 2px 8px; border-radius: 10px; }

.permissions-editor { display: flex; flex-direction: column; gap: 4px; max-width: 320px; }
.permissions-editor label { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; }
.permissions-editor input { width: auto; margin: 0; }

.status-badge { font-size: 0.78rem; font-weight: 600; padding: 2px 10px; border-radius: 10px; background: #f3f4f6; color: #6b7280; }
.status-badge.active { background: #dcfce7; color: #166534; }

.actions { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
.actions textarea { padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; min-height: 50px; }
.actions-row { display: flex; gap: 6px; }
.new-role-form { background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #e5e7eb; }
.new-role-form h3 { margin: 0 0 14px; color: #1e1b4b; font-size: 1rem; }
.form-row { display: flex; gap: 10px; margin-bottom: 10px; }
.form-row input, .form-row select { flex: 1; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.new-role-form textarea { width: 100%; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; margin-bottom: 10px; min-height: 60px; box-sizing: border-box; }
.error { color: #dc2626; font-size: 0.85rem; margin-top: 8px; }
.confirm-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.confirm-dialog { background: #fff; border-radius: 16px; padding: 28px 32px; max-width: 420px; width: 90%; box-shadow: 0 8px 40px rgba(0,0,0,0.2); }
.confirm-dialog h4 { margin: 0 0 12px; color: #1e1b4b; font-size: 1.1rem; }
.confirm-dialog p { color: #6b7280; font-size: 0.92rem; margin: 0 0 24px; line-height: 1.5; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; }

@media (max-width: 900px) {
  .roles-page { padding: 14px; }
  .roles-table thead { display: none; }
  .roles-table, .roles-table tbody, .roles-table tr, .roles-table td { display: block; width: 100%; }
  .roles-table tr { margin-bottom: 12px; border: 1px solid #e5e7eb; border-radius: 12px; padding: 8px 4px; }
  .roles-table td { border-top: none; padding: 8px 12px; }
  .roles-table td::before {
    content: attr(data-label); display: block; font-size: 0.7rem; font-weight: 600;
    text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 4px;
  }
  .actions { min-width: 0; }
  .form-row { flex-direction: column; }
}
</style>
