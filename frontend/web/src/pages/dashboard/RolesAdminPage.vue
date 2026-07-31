<template lang="pug">
.roles-page
  h2 Roles

  LoadingState(v-if="loading" message="Cargando roles…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="roles.length === 0" message="No hay roles creados.")

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
            StatusBadge(:status="r.active ? 'ACTIVE' : 'INACTIVE'" :label="r.active ? 'Activo' : 'Inactivo'")
          td.actions(data-label="Acciones")
            template(v-if="editing === r.uuid")
              SaveState(:state="editSaveState")
              textarea(v-model="editForm.description" placeholder="Descripción")
              .actions-row
                BaseButton(size="sm" :loading="saving" :disabled="saving || editSaveState === 'clean'" @click="saveEdit(r)") Guardar
                BaseButton(variant="ghost" size="sm" @click="editing = null") Cancelar
            template(v-else)
              BaseButton(variant="secondary" size="sm" @click="startEdit(r)") Editar
              BaseButton(variant="secondary" size="sm" v-if="r.active" @click="confirmToggleActive(r, false)") Desactivar
              BaseButton(variant="secondary" size="sm" v-else @click="confirmToggleActive(r, true)") Activar

  .new-role-form
    h3 Nuevo rol
    SaveState(:state="newRoleSaveState")
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
    BaseButton(type="button" :loading="creating" :disabled="creating || newRoleSaveState === 'clean'" @click="createNew") Crear rol
    FeedbackMessage(v-if="createError" :message="createError" tone="error")

  BaseModal(
    v-if="pendingRole"
    :title="pendingRoleActive ? '¿Activar rol?' : '¿Desactivar rol?'"
    :loading="actionLoading"
    :persistent="actionLoading"
    @close="pendingRole = null"
    @confirm="runToggleActive"
  )
    p {{ pendingRoleActive ? 'El rol volverá a estar disponible para asignarse.' : 'El rol no podrá asignarse mientras esté inactivo.' }}
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getAllRoles, getPermissionsCatalog, createRole, updateRole, setRoleActive
} from '@/services/api/usersApi'
import type { Role, PermissionValue, RoleScopeValue } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import SaveState from '@/components/ui/SaveState.vue'

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
  components: { BaseButton, BaseModal, StatusBadge, EmptyState, FeedbackMessage, LoadingState, SaveState },
  setup() {
    const auth = useAuthStore()
    const roles = ref<Role[]>([])
    const allPermissions = ref<PermissionValue[]>([])
    const loading = ref(true)
    const error = ref('')
    const editing = ref<string | null>(null)
    const editForm = ref<{ name: string, description: string, permissions: PermissionValue[] }>(
      { name: '', description: '', permissions: [] })
    const initialEditSnapshot = ref('')
    const saving = ref(false)
    const newRole = ref<{ key: string, name: string, description: string, scope: RoleScopeValue, permissions: PermissionValue[] }>(
      { key: '', name: '', description: '', scope: 'EVENT', permissions: [] })
    const creating = ref(false)
    const createError = ref('')
    const initialNewRoleSnapshot = ref('')
    const pendingRole = ref<Role | null>(null)
    const pendingRoleActive = ref(false)
    const actionLoading = ref(false)

    function editSnapshot(): string {
      return JSON.stringify({
        name: editForm.value.name,
        description: editForm.value.description,
        permissions: editForm.value.permissions
      })
    }

    function newRoleSnapshot(): string {
      return JSON.stringify({
        key: newRole.value.key,
        name: newRole.value.name,
        description: newRole.value.description,
        scope: newRole.value.scope,
        permissions: newRole.value.permissions
      })
    }

    const editSaveState = computed(() => {
      if (saving.value) return 'saving'
      return editSnapshot() === initialEditSnapshot.value ? 'clean' : 'dirty'
    })
    const newRoleSaveState = computed(() => {
      if (creating.value) return 'saving'
      return newRoleSnapshot() === initialNewRoleSnapshot.value ? 'clean' : 'dirty'
    })

    initialNewRoleSnapshot.value = newRoleSnapshot()

    function permissionLabel(p: string): string {
      return PERMISSION_LABELS[p] || p
    }

    async function load() {
      loading.value = true
      error.value = ''
      try {
        const [roleList, permissions] = await Promise.all([
          getAllRoles(auth.state.token as string),
          getPermissionsCatalog()
        ])
        roles.value = roleList
        allPermissions.value = permissions
      } catch (e: any) {
        error.value = 'No fue posible cargar los roles. Inténtalo nuevamente.'
      } finally {
        loading.value = false
      }
    }

    function startEdit(r: Role) {
      editing.value = r.uuid
      editForm.value = { name: r.name, description: r.description || '', permissions: [...r.permissions] }
      initialEditSnapshot.value = editSnapshot()
    }

    async function saveEdit(r: Role) {
      saving.value = true
      error.value = ''
      try {
        const updated = await updateRole(
          r.uuid, editForm.value.name, editForm.value.description || null,
          editForm.value.permissions, auth.state.token as string)
        Object.assign(r, updated)
        initialEditSnapshot.value = editSnapshot()
        editing.value = null
      } catch (e: any) {
        error.value = 'No fue posible guardar el rol. Inténtalo nuevamente.'
      } finally {
        saving.value = false
      }
    }

    async function toggleActive(r: Role, active: boolean) {
      try {
        error.value = ''
        const updated = await setRoleActive(r.uuid, active, auth.state.token as string)
        Object.assign(r, updated)
      } catch (e: any) {
        error.value = 'No fue posible actualizar el estado del rol. Inténtalo nuevamente.'
      }
    }

    function confirmToggleActive(r: Role, active: boolean) {
      pendingRole.value = r
      pendingRoleActive.value = active
    }

    async function runToggleActive() {
      if (actionLoading.value) return
      const role = pendingRole.value
      const active = pendingRoleActive.value
      if (!role) return
      actionLoading.value = true
      try { await toggleActive(role, active) }
      finally {
        actionLoading.value = false
        pendingRole.value = null
      }
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
        initialNewRoleSnapshot.value = newRoleSnapshot()
      } catch (e: any) {
        createError.value = e.response?.data?.error?.message || 'No se pudo crear el rol'
      } finally {
        creating.value = false
      }
    }

    onMounted(load)

    return {
      roles, allPermissions, loading, error, editing, editForm, saving, editSaveState, newRole, creating, createError, newRoleSaveState,
      pendingRole, pendingRoleActive, actionLoading,
      permissionLabel, startEdit, saveEdit, toggleActive, confirmToggleActive, runToggleActive, createNew
    }
  }
}
</script>

<style scoped>
.roles-page { padding: 24px; max-width: 1200px; }
h2 { color: var(--color-heading); margin-bottom: 20px; }
.table-scroll { margin-bottom: 32px; }
.roles-table { width: 100%; border-collapse: collapse; background: var(--color-surface); border-radius: 12px; overflow: hidden; }
.roles-table th { text-align: left; padding: 10px 12px; background: var(--color-surface-muted); color: var(--color-text-muted); font-size: 0.78rem; font-weight: 600; text-transform: uppercase; }
.roles-table td { padding: 10px 12px; border-top: 1px solid var(--color-surface-muted); vertical-align: top; font-size: 0.88rem; }
.sub { font-size: 0.78rem; color: var(--color-text-muted); }

.scope-badge { font-size: 0.72rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; background: var(--color-surface-muted); color: var(--color-text-muted); }
.scope-badge.platform { background: var(--color-warning-soft); color: var(--color-warning); }
.scope-badge.event { background: var(--color-primary-soft); color: var(--color-primary-dark); }

.permissions-list { display: flex; flex-wrap: wrap; gap: 4px; max-width: 260px; }
.permission-chip { font-size: 0.7rem; background: var(--color-success-soft); color: var(--color-success); padding: 2px 8px; border-radius: 10px; }

.permissions-editor { display: flex; flex-direction: column; gap: 4px; max-width: 320px; }
.permissions-editor label { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; }
.permissions-editor input { width: auto; margin: 0; }

.actions { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
.actions textarea { font-size: 0.82rem; min-height: 50px; }
.actions-row { display: flex; gap: 6px; }
.new-role-form { background: var(--color-surface); border-radius: 12px; padding: 20px; border: 1px solid var(--color-border-subtle); }
.new-role-form h3 { margin: 0 0 14px; color: var(--color-heading); font-size: 1rem; }
.form-row { display: flex; gap: 10px; margin-bottom: 10px; }
.form-row input, .form-row select { flex: 1; font-size: 0.9rem; }
.new-role-form textarea { width: 100%; font-size: 0.9rem; margin-bottom: 10px; min-height: 60px; box-sizing: border-box; }
@media (max-width: 900px) {
  .roles-page { padding: 14px; }
  .roles-table thead { display: none; }
  .roles-table, .roles-table tbody, .roles-table tr, .roles-table td { display: block; width: 100%; }
  .roles-table tr { margin-bottom: 12px; border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 8px 4px; }
  .roles-table td { border-top: none; padding: 8px 12px; }
  .roles-table td::before {
    content: attr(data-label); display: block; font-size: 0.7rem; font-weight: 600;
    text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 4px;
  }
  .actions { min-width: 0; }
  .form-row { flex-direction: column; }
}
</style>
