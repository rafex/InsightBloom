<template lang="pug">
.admin-users-page
  DashboardBreadcrumb(:items="[{ label: 'Usuarios' }]")

  h2 Usuarios

  .filters
    label.filter-field
      span Estado
      select(v-model="statusFilter" @change="reload")
        option(value="") Todos los estados
        option(value="ACTIVE") Activos
        option(value="INACTIVE") Inactivos
        option(value="BANNED") Baneados
        option(value="DELETED") Eliminados
    label.filter-field
      span Rol
      select(v-model="roleFilter" @change="reload")
        option(value="") Todos los roles
        option(v-for="r in availableRoles" :key="r" :value="r") {{ r }}
    BaseButton(variant="secondary" size="sm" type="button" @click="toggleSort")
      | Orden alfabético {{ sort === 'username' ? '✓' : '' }}

  LoadingState(v-if="loading" message="Cargando usuarios…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="users.length === 0" message="No hay usuarios para mostrar.")

  .table-scroll(v-else)
    table.users-table
      thead
        tr
          th ID usuario
          th Usuario
          th Estado
          th Acciones
      tbody
        tr(v-for="u in users" :key="u.uuid")
          td.clickable(data-label="ID usuario" @click="goToDetail(u)")
            span.uuid-text {{ u.uuid }}
          td.clickable(data-label="Usuario" @click="goToDetail(u)")
            strong {{ u.displayName || u.username }}
            .sub {{ u.username }}
          td(data-label="Estado")
            StatusBadge(:status="u.status" :label="formatStatusLabel(u.status)")
          td.actions(data-label="Acciones")
            template(v-if="editing === u.uuid")
              input(v-model="editForm.displayName" placeholder="Nombre visible")
              input(v-model="editForm.email" placeholder="Email")
              input(v-model="editForm.phone" placeholder="Teléfono")
              .roles-editor
                label(v-for="r in availableRoles" :key="r")
                  input(type="checkbox" :value="r" v-model="editForm.roles")
                  span {{ r }}
              .actions-row
                BaseButton(size="sm" :loading="saving" @click="saveEdit(u)") Guardar
                BaseButton(variant="ghost" size="sm" @click="editing = null") Cancelar
            template(v-else)
              BaseButton(variant="secondary" size="sm" @click="startEdit(u)") Editar
              BaseButton(variant="secondary" size="sm" v-if="u.status === 'ACTIVE'" @click="confirmAction(u, 'ban')") Banear
              BaseButton(variant="secondary" size="sm" v-if="u.status === 'BANNED'" @click="confirmAction(u, 'unban')") Reactivar
              BaseButton(variant="danger" size="sm" v-if="u.status !== 'DELETED'" @click="confirmAction(u, 'delete')") Eliminar

  .pagination(v-if="totalPages > 1")
    BaseButton(variant="ghost" size="sm" type="button" :disabled="page <= 1" aria-label="Página anterior" @click="goToPage(page - 1)") ‹
    span Página {{ page }} / {{ totalPages }}
    BaseButton(variant="ghost" size="sm" type="button" :disabled="page >= totalPages" aria-label="Página siguiente" @click="goToPage(page + 1)") ›

  BaseModal(
    v-if="confirmTarget"
    :title="confirmTitle"
    :confirm-variant="confirmAction_ === 'delete' ? 'danger' : 'primary'"
    @close="confirmTarget = null"
    @confirm="runConfirmedAction"
  )
    p {{ confirmMessage }}
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listUsers, updateUser, banUser, unbanUser, deleteUserLogical } from '@/services/api/adminApi'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import { formatStatusLabel } from '@/utils/status'

type ConfirmActionType = 'ban' | 'unban' | 'delete'

interface AdminUserRow {
  uuid: string
  displayName?: string
  username?: string
  email?: string
  phone?: string
  roles?: string
  status: string
  [key: string]: unknown
}

export default {
  name: 'AdminUsersPage',
  components: { DashboardBreadcrumb, BaseButton, BaseModal, StatusBadge, EmptyState, FeedbackMessage, LoadingState },
  setup() {
    const auth = useAuthStore()
    const router = useRouter()
    const users = ref<AdminUserRow[]>([])
    const loading = ref(true)
    const error = ref('')
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const roleFilter = ref('')
    const sort = ref<'' | 'username'>('')
    const editing = ref<string | null>(null)
    const editForm = ref<{ displayName?: string, email?: string, phone?: string, roles: string[] }>({ roles: [] })
    const saving = ref(false)
    const confirmTarget = ref<AdminUserRow | null>(null)
    const confirmAction_ = ref<ConfirmActionType | null>(null)
    const availableRoles = ['ATTENDEE', 'GUEST', 'MODERATOR', 'ORGANIZER', 'ADMIN']

    async function load() {
      loading.value = true
      error.value = ''
      try {
        const res = await listUsers(auth.state.token as string, page.value, 50, {
          status: statusFilter.value || undefined,
          role: roleFilter.value || undefined,
          sort: sort.value || undefined
        })
        users.value = res.data || []
        totalPages.value = res.meta?.totalPages || 1
      } catch (e: any) {
        users.value = []
        error.value = 'No fue posible cargar los usuarios. Inténtalo nuevamente.'
      } finally {
        loading.value = false
      }
    }

    function reload() { page.value = 1; load() }

    function toggleSort() { sort.value = sort.value === 'username' ? '' : 'username'; reload() }

    function goToPage(p: number) { page.value = p; load() }

    function goToDetail(u: AdminUserRow) { router.push(`/dashboard/admin/users/${u.uuid}`) }

    function startEdit(u: AdminUserRow) {
      editing.value = u.uuid
      editForm.value = {
        displayName: u.displayName || '',
        email: u.email || '',
        phone: u.phone || '',
        roles: (u.roles || '').split(',').map((r) => r.trim()).filter(Boolean)
      }
    }

    async function saveEdit(u: AdminUserRow) {
      saving.value = true
      error.value = ''
      try {
        const payload = { ...editForm.value, roles: editForm.value.roles.join(',') }
        const updated = await updateUser(u.uuid, payload, auth.state.token as string)
        Object.assign(u, updated)
        editing.value = null
      } catch (e: any) {
        error.value = 'No fue posible guardar los cambios del usuario. Inténtalo nuevamente.'
      } finally {
        saving.value = false
      }
    }

    const confirmTitle = computed(() => {
      const map: Record<ConfirmActionType, string> = { ban: '¿Banear usuario?', unban: '¿Reactivar usuario?', delete: '¿Eliminar usuario?' }
      return confirmAction_.value ? map[confirmAction_.value] : ''
    })

    const confirmMessage = computed(() => {
      if (!confirmTarget.value || !confirmAction_.value) return ''
      const name = confirmTarget.value.displayName || confirmTarget.value.username
      const map: Record<ConfirmActionType, string> = {
        ban: `${name} no podrá iniciar sesión y sus sesiones activas se cerrarán de inmediato.`,
        unban: `${name} podrá volver a iniciar sesión normalmente.`,
        delete: `${name} se marcará como eliminado (eliminación lógica) y no podrá iniciar sesión. Sus datos se conservan.`
      }
      return map[confirmAction_.value] || ''
    })

    function confirmAction(u: AdminUserRow, action: ConfirmActionType) {
      confirmTarget.value = u
      confirmAction_.value = action
    }

    async function runConfirmedAction() {
      const u = confirmTarget.value
      const action = confirmAction_.value
      confirmTarget.value = null
      if (!u || !action) return
      const fn = { ban: banUser, unban: unbanUser, delete: deleteUserLogical }[action]
      try {
        error.value = ''
        const updated = await fn(u.uuid, auth.state.token as string)
        Object.assign(u, updated)
      } catch (e: any) {
        error.value = 'No fue posible actualizar el estado del usuario. Inténtalo nuevamente.'
      }
    }

    onMounted(load)

    return {
      users, loading, error, page, totalPages, statusFilter, roleFilter, sort, availableRoles,
      editing, editForm, saving,
      confirmTarget, confirmTitle, confirmMessage,
      confirmAction_,
      reload, toggleSort, goToPage, goToDetail, formatStatusLabel, startEdit, saveEdit, confirmAction, runConfirmedAction
    }
  }
}
</script>

<style scoped>
.admin-users-page { padding: 24px; max-width: 1280px; }
h2 { color: var(--color-heading); margin-bottom: 20px; }
.filters { margin-bottom: 16px; display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.filter-field { display: flex; align-items: center; gap: 6px; color: var(--color-text-secondary); font-size: 0.82rem; font-weight: 600; }
select { padding: 8px 12px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 0.9rem; }
.users-table { width: 100%; border-collapse: collapse; background: var(--color-surface); border-radius: 12px; overflow: hidden; }
.users-table th { text-align: left; padding: 10px 12px; background: var(--color-surface-muted); color: var(--color-text-muted); font-size: 0.78rem; font-weight: 600; text-transform: uppercase; }
.users-table td { padding: 10px 12px; border-top: 1px solid var(--color-surface-muted); vertical-align: top; font-size: 0.88rem; }
.sub { font-size: 0.78rem; color: var(--color-text-muted); }
.uuid-text { font-family: monospace; font-size: 0.8rem; color: var(--color-text-muted); }
.clickable { cursor: pointer; }
.clickable:hover { background: var(--color-surface-muted); }

.roles-editor { display: flex; flex-direction: column; gap: 4px; margin: 4px 0; }
.roles-editor label { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; }
.roles-editor input { width: auto; margin: 0; }

.actions { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
.actions input { padding: 6px 8px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 0.82rem; }
.actions-row { display: flex; gap: 6px; }
.pagination { display: flex; align-items: center; gap: 12px; margin-top: 20px; justify-content: center; font-size: 0.9rem; color: var(--color-text-secondary); }
@media (max-width: 900px) {
  .admin-users-page { padding: 14px; }

  /* Tabla -> tarjetas apiladas: evita el scroll horizontal interno en pantallas
     angostas (el problema original: usuario/teléfono/email/acciones no cabían). */
  .users-table thead { display: none; }
  .users-table, .users-table tbody, .users-table tr, .users-table td {
    display: block; width: 100%;
  }
  .users-table tr {
    margin-bottom: 12px; border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 8px 4px;
  }
  .users-table td {
    border-top: none; padding: 8px 12px;
  }
  .users-table td::before {
    content: attr(data-label); display: block; font-size: 0.7rem; font-weight: 600;
    text-transform: uppercase; color: var(--color-text-muted); margin-bottom: 4px;
  }
  .actions { min-width: 0; }
}
</style>
