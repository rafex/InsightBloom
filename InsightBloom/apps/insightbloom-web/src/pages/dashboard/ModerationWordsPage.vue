<template lang="pug">
.mod-words-page
  h2 Moderación de palabras
  .filters
    select(v-model="statusFilter" @change="load")
      option(value="") Todos los estados
      option(value="VISIBLE") Visible
      option(value="CENSURADO_AUTO") Censurado automático
      option(value="CENSURADO_MANUAL") Censurado manual
      option(value="PENDIENTE_REVISION") Pendiente revisión
  ModerationTable(
    :rows="words"
    :loading="loading"
    :page="page"
    :totalPages="totalPages"
    @prev="prevPage"
    @next="nextPage"
  )
    template(#header)
      th Palabra
      th Tipo
      th Relevancia
      th Estado
      th Acciones
    template(#row="{ row }")
      td
        span.word-cell {{ row.wordCanonical }}
      td
        span.badge(:class="row.messageType === 'DOUBT' ? 'badge-doubt' : 'badge-topic'") {{ row.messageType === 'DOUBT' ? 'Duda' : 'Tema' }}
      td {{ row.relevanceScore?.toFixed(2) || '—' }}
      td
        span.status(:class="statusClass(row.contentStatus)") {{ statusLabel(row.contentStatus) }}
      td.actions
        button.btn-sm.btn-danger(
          v-if="row.contentStatus === 'VISIBLE' || row.contentStatus === 'PENDIENTE_REVISION'"
          @click="censor(row)"
          :disabled="row._loading"
        ) Censurar
        button.btn-sm.btn-success(
          v-if="row.contentStatus !== 'VISIBLE'"
          @click="restore(row)"
          :disabled="row._loading"
        ) Restaurar
</template>

<script>
import ModerationTable from '@/components/tables/ModerationTable.vue'
import { ref, onMounted } from 'vue'
import { getModerationWords, censorWord, restoreWord } from '@/services/api/moderationApi'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'ModerationWordsPage',
  components: { ModerationTable },
  props: { conferenceId: String },
  setup(props) {
    const words = ref([])
    const loading = ref(false)
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const auth = useAuthStore()
    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await getModerationWords(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token)
        words.value = (res.data || []).map(w => ({ ...w, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
      } catch (e) { } finally { loading.value = false }
    }
    function prevPage() { if (page.value > 1) { page.value--; load() } }
    function nextPage() { if (page.value < totalPages.value) { page.value++; load() } }
    async function censor(row) {
      row._loading = true
      try { await censorWord(props.conferenceId, row.wordId, auth.state.token); await load() }
      catch (e) { row._loading = false }
    }
    async function restore(row) {
      row._loading = true
      try { await restoreWord(props.conferenceId, row.wordId, auth.state.token); await load() }
      catch (e) { row._loading = false }
    }
    function statusClass(s) {
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION' }
    }
    function statusLabel(s) {
      const map = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente' }
      return map[s] || s
    }
    onMounted(load)
    return { words, loading, page, totalPages, statusFilter, load, prevPage, nextPage, censor, restore, statusClass, statusLabel }
  }
}
</script>

<style scoped>
.mod-words-page { }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.word-cell { font-family: monospace; font-weight: 700; font-size: 1rem; color: #1e1b4b; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 0.8rem; font-weight: 600; }
.badge-doubt { background: #ede9fe; color: #4f46e5; }
.badge-topic { background: #cffafe; color: #0891b2; }
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
