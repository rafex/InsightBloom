<template lang="pug">
.admin-users-page
  h2 Usuarios

  .filters
    select(v-model="statusFilter")
      option(value="") Todos los estados
      option(value="ACTIVE") Activos
      option(value="BANNED") Baneados
      option(value="DELETED") Eliminados

  .empty-state(v-if="!loading && filteredUsers.length === 0")
    p No hay usuarios para mostrar.

  table.users-table(v-else)
    thead
      tr
        th Usuario
        th Email / Teléfono
        th Rol
        th Estado
        th Acciones
    tbody
      tr(v-for="u in filteredUsers" :key="u.uuid")
        td
          strong {{ u.displayName || u.username }}
          .sub {{ u.username }}
        td
          div {{ u.email || '—' }}
          .sub(v-if="u.phone") {{ u.phone }}
        td
          select(v-if="editing === u.uuid" v-model="editForm.role")
            option(value="ATTENDEE") Attendee
            option(value="MODERATOR") Moderator
            option(value="ORGANIZER") Organizer
            option(value="ADMIN") Admin
          span(v-else) {{ u.role }}
        td
          span.status-badge(:class="u.status") {{ statusLabel(u.status) }}
        td.actions
          template(v-if="editing === u.uuid")
            input(v-model="editForm.displayName" placeholder="Nombre visible")
            input(v-model="editForm.email" placeholder="Email")
            input(v-model="editForm.phone" placeholder="Teléfono")
            .actions-row
              button.btn-sm.btn-primary-sm(:disabled="saving" @click="saveEdit(u)") Guardar
              button.btn-sm.btn-ghost-sm(@click="editing = null") Cancelar
          template(v-else)
            button.btn-sm.btn-edit(@click="startEdit(u)") Editar
            button.btn-sm.btn-warning(v-if="u.status === 'ACTIVE'" @click="confirmAction(u, 'ban')") Banear
            button.btn-sm.btn-success(v-if="u.status === 'BANNED'" @click="confirmAction(u, 'unban')") Reactivar
            button.btn-sm.btn-danger(v-if="u.status !== 'DELETED'" @click="confirmAction(u, 'delete')") Eliminar

  .pagination(v-if="totalPages > 1")
    button(@click="goToPage(page - 1)" :disabled="page <= 1") ‹
    span Página {{ page }} / {{ totalPages }}
    button(@click="goToPage(page + 1)" :disabled="page >= totalPages") ›

  .confirm-overlay(v-if="confirmTarget" @click.self="confirmTarget = null")
    .confirm-dialog
      h4 {{ confirmTitle }}
      p {{ confirmMessage }}
      .confirm-actions
        button.btn-cancel(@click="confirmTarget = null") Cancelar
        button.btn-confirm(@click="runConfirmedAction") Confirmar
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { listUsers, updateUser, banUser, unbanUser, deleteUserLogical } from '@/services/api/adminApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'AdminUsersPage',
  setup() {
    const auth = useAuthStore()
    const users = ref([])
    const loading = ref(true)
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const editing = ref(null)
    const editForm = ref({})
    const saving = ref(false)
    const confirmTarget = ref(null)
    const confirmAction_ = ref(null)

    const filteredUsers = computed(() =>
      statusFilter.value ? users.value.filter((u) => u.status === statusFilter.value) : users.value)

    async function load() {
      loading.value = true
      try {
        const res = await listUsers(auth.state.token, page.value, 50)
        users.value = res.data || []
        totalPages.value = res.meta?.totalPages || 1
      } catch (e) {
        users.value = []
      } finally {
        loading.value = false
      }
    }

    function goToPage(p) { page.value = p; load() }

    function statusLabel(s) {
      return { ACTIVE: 'Activo', BANNED: 'Baneado', DELETED: 'Eliminado', INACTIVE: 'Inactivo' }[s] || s
    }

    function startEdit(u) {
      editing.value = u.uuid
      editForm.value = {
        displayName: u.displayName || '',
        email: u.email || '',
        phone: u.phone || '',
        role: u.role
      }
    }

    async function saveEdit(u) {
      saving.value = true
      try {
        const updated = await updateUser(u.uuid, editForm.value, auth.state.token)
        Object.assign(u, updated)
        editing.value = null
      } finally {
        saving.value = false
      }
    }

    const confirmTitle = computed(() => {
      const map = { ban: '¿Banear usuario?', unban: '¿Reactivar usuario?', delete: '¿Eliminar usuario?' }
      return confirmAction_.value ? map[confirmAction_.value] : ''
    })

    const confirmMessage = computed(() => {
      if (!confirmTarget.value) return ''
      const name = confirmTarget.value.displayName || confirmTarget.value.username
      const map = {
        ban: `${name} no podrá iniciar sesión y sus sesiones activas se cerrarán de inmediato.`,
        unban: `${name} podrá volver a iniciar sesión normalmente.`,
        delete: `${name} se marcará como eliminado (eliminación lógica) y no podrá iniciar sesión. Sus datos se conservan.`
      }
      return map[confirmAction_.value] || ''
    })

    function confirmAction(u, action) {
      confirmTarget.value = u
      confirmAction_.value = action
    }

    async function runConfirmedAction() {
      const u = confirmTarget.value
      const action = confirmAction_.value
      confirmTarget.value = null
      if (!u || !action) return
      const fn = { ban: banUser, unban: unbanUser, delete: deleteUserLogical }[action]
      const updated = await fn(u.uuid, auth.state.token)
      Object.assign(u, updated)
    }

    onMounted(load)

    return {
      users, loading, page, totalPages, statusFilter, filteredUsers, editing, editForm, saving,
      confirmTarget, confirmTitle, confirmMessage,
      goToPage, statusLabel, startEdit, saveEdit, confirmAction, runConfirmedAction
    }
  }
}
</script>

<style scoped>
.admin-users-page { padding: 24px; max-width: 960px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.empty-state { text-align: center; color: #9ca3af; padding: 60px; }

.users-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; }
.users-table th { text-align: left; padding: 10px 12px; background: #f9fafb; color: #6b7280; font-size: 0.78rem; font-weight: 600; text-transform: uppercase; }
.users-table td { padding: 10px 12px; border-top: 1px solid #f3f4f6; vertical-align: top; font-size: 0.88rem; }
.sub { font-size: 0.78rem; color: #9ca3af; }

.status-badge { font-size: 0.78rem; font-weight: 600; padding: 2px 10px; border-radius: 10px; }
.status-badge.ACTIVE { background: #dcfce7; color: #166534; }
.status-badge.BANNED { background: #fee2e2; color: #991b1b; }
.status-badge.DELETED { background: #f3f4f6; color: #6b7280; }
.status-badge.INACTIVE { background: #fef9c3; color: #854d0e; }

.actions { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
.actions input { padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.82rem; }
.actions-row { display: flex; gap: 6px; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.8rem; }
.btn-edit { background: #e0e7ff; color: #4338ca; }
.btn-warning { background: #fef3c7; color: #92400e; }
.btn-success { background: #dcfce7; color: #166534; }
.btn-danger { background: #fee2e2; color: #991b1b; }
.btn-primary-sm { background: #4f46e5; color: #fff; }
.btn-ghost-sm { background: #fff; color: #6b7280; border: 1px solid #e5e7eb; }

.pagination { display: flex; align-items: center; gap: 12px; margin-top: 20px; justify-content: center; font-size: 0.9rem; color: #374151; }
.pagination button { padding: 4px 12px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; cursor: pointer; }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }

.confirm-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.confirm-dialog { background: #fff; border-radius: 16px; padding: 28px 32px; max-width: 420px; width: 90%; box-shadow: 0 8px 40px rgba(0,0,0,0.2); }
.confirm-dialog h4 { margin: 0 0 12px; color: #1e1b4b; font-size: 1.1rem; }
.confirm-dialog p { color: #6b7280; font-size: 0.92rem; margin: 0 0 24px; line-height: 1.5; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; }
.btn-cancel { padding: 8px 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #374151; cursor: pointer; }
.btn-confirm { padding: 8px 18px; background: #dc2626; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; }
</style>
