<template lang="pug">
.mod-messages-page
  h2 Moderación de mensajes
  .filters
    select(v-model="statusFilter" @change="load")
      option(value="") Todos los estados
      option(value="VISIBLE") Visible
      option(value="CENSURADO_AUTO") Censurado automático
      option(value="CENSURADO_MANUAL") Censurado manual
      option(value="PENDIENTE_REVISION") Pendiente revisión
  ModerationTable(
    :items="messages"
    :currentPage="page"
    :totalPages="totalPages"
    @page="goToPage"
  )
    template(#headers)
      th Palabra
      th Detalle
      th Estado detalle
      th Acciones
    template(#row="{ item }")
      td
        span.word-cell {{ item.wordText || item.messageUuid?.slice(0,8) }}
      td.detail-cell {{ truncate(item.detailText, 80) }}
      td
        span.status(:class="statusClass(item.detailStatus)") {{ statusLabel(item.detailStatus) }}
      td.actions
        button.btn-sm.btn-danger(
          v-if="item.detailStatus === 'VISIBLE' || item.detailStatus === 'PENDIENTE_REVISION'"
          @click="censorDetail(item)"
          :disabled="item._loading"
        ) Censurar detalle
        button.btn-sm.btn-success(
          v-if="item.detailStatus !== 'VISIBLE'"
          @click="restore(item)"
          :disabled="item._loading"
        ) Restaurar
</template>

<script>
import ModerationTable from '@/components/tables/ModerationTable.vue'
import { ref, onMounted } from 'vue'
import { getModerationMessages, censorMessage, restoreMessage } from '@/services/api/moderationApi'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'ModerationMessagesPage',
  components: { ModerationTable },
  props: { conferenceId: String },
  setup(props) {
    const messages = ref([])
    const loading = ref(false)
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const auth = useAuthStore()
    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await getModerationMessages(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token)
        messages.value = (res.data || []).map(m => ({ ...m, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
      } catch (e) { } finally { loading.value = false }
    }
    function goToPage(p) { page.value = p; load() }
    async function censorDetail(item) {
      item._loading = true
      try { await censorMessage(item.uuid, null, 'detail', auth.state.token); await load() }
      catch (e) { item._loading = false }
    }
    async function restore(item) {
      item._loading = true
      try { await restoreMessage(item.uuid, auth.state.token); await load() }
      catch (e) { item._loading = false }
    }
    function statusClass(s) {
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION' }
    }
    function statusLabel(s) {
      const map = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente' }
      return map[s] || s
    }
    function truncate(text, n) { return text && text.length > n ? text.slice(0, n) + '…' : (text || '') }
    onMounted(load)
    return { messages, loading, page, totalPages, statusFilter, load, goToPage, censorDetail, restore, statusClass, statusLabel, truncate }
  }
}
</script>

<style scoped>
.mod-messages-page { }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.word-cell { font-family: monospace; font-weight: 700; font-size: 1rem; color: #1e1b4b; }
.detail-cell { max-width: 300px; font-size: 0.88rem; color: #374151; }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-visible { background: #dcfce7; color: #166534; }
.status-censored { background: #fee2e2; color: #991b1b; }
.status-pending { background: #fef9c3; color: #854d0e; }
.actions { display: flex; gap: 6px; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-danger { background: #fee2e2; color: #dc2626; }
.btn-danger:hover { background: #fecaca; }
.btn-success { background: #dcfce7; color: #16a34a; }
.btn-success:hover { background: #bbf7d0; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
