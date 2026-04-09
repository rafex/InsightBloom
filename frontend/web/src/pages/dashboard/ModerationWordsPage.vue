<template lang="pug">
.mod-words-page
  nav.breadcrumbs(aria-label="breadcrumb")
    router-link(to="/dashboard") Dashboard
    span.sep /
    span.crumb-current(v-if="conferenceName") {{ conferenceName }}
    span.crumb-loading(v-else) …
    span.sep /
    span.crumb-current Moderación de palabras

  h2 Moderación de palabras

  .filters
    select(v-model="statusFilter" @change="load")
      option(value="") Todos los estados
      option(value="VISIBLE") Visible
      option(value="CENSURADO_AUTO") Censurado automático
      option(value="CENSURADO_MANUAL") Censurado manual
      option(value="PENDIENTE_REVISION") Pendiente revisión

  ModerationTable(
    :items="words"
    :currentPage="page"
    :totalPages="totalPages"
    @page="goToPage"
  )
    template(#headers)
      th Palabra
      th Normalizada
      th Estado
      th Acciones
    template(#row="{ item }")
      td
        span.word-cell {{ item.wordCanonical }}
      td {{ item.wordNormalized }}
      td
        span.status(:class="statusClass(item.status)") {{ statusLabel(item.status) }}
      td.actions
        button.btn-sm.btn-danger(
          v-if="item.status === 'VISIBLE' || item.status === 'PENDIENTE_REVISION'"
          @click="censor(item)"
          :disabled="item._loading"
        ) Censurar
        button.btn-sm.btn-success(
          v-if="item.status !== 'VISIBLE'"
          @click="restore(item)"
          :disabled="item._loading"
        ) Restaurar
        button.btn-sm.btn-secondary(@click="verMensajes(item)" title="Ver mensajes de esta palabra") Ver mensajes
</template>

<script>
import ModerationTable from '@/components/tables/ModerationTable.vue'
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getModerationWords, censorWord, restoreWord } from '@/services/api/moderationApi'
import { getConference } from '@/services/api/usersApi'
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
    const conferenceName = ref('')
    const auth = useAuthStore()
    const router = useRouter()

    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await getModerationWords(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token)
        words.value = (res.data || []).map(w => ({ ...w, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
      } catch (e) { } finally { loading.value = false }
    }

    function goToPage(p) { page.value = p; load() }

    async function censor(row) {
      row._loading = true
      try { await censorWord(row.uuid, null, auth.state.token); await load() }
      catch (e) { row._loading = false }
    }

    async function restore(row) {
      row._loading = true
      try { await restoreWord(row.uuid, auth.state.token); await load() }
      catch (e) { row._loading = false }
    }

    function statusClass(s) {
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION' }
    }

    function statusLabel(s) {
      const map = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente' }
      return map[s] || s
    }

    function verMensajes(word) {
      const params = new URLSearchParams({
        wordNormalized: word.wordNormalized,
        wordCanonical: word.wordCanonical
      })
      router.push(`/dashboard/conferences/${props.conferenceId}/moderation/messages?${params}`)
    }

    onMounted(async () => {
      load()
      if (props.conferenceId) {
        try {
          const conf = await getConference(props.conferenceId, auth.state.token)
          conferenceName.value = conf?.name || props.conferenceId
        } catch (e) { conferenceName.value = props.conferenceId }
      }
    })

    return { words, loading, page, totalPages, statusFilter, conferenceName, load, goToPage, censor, restore, statusClass, statusLabel, verMensajes }
  }
}
</script>

<style scoped>
.mod-words-page { }
.breadcrumbs {
  display: flex; align-items: center; gap: 6px;
  font-size: 0.85rem; color: #6b7280; margin-bottom: 20px; flex-wrap: wrap;
}
.breadcrumbs a { color: #4f46e5; text-decoration: none; }
.breadcrumbs a:hover { text-decoration: underline; }
.sep { color: #d1d5db; }
.crumb-current { color: #374151; font-weight: 500; }
.crumb-loading { color: #9ca3af; }
h2 { color: #1e1b4b; margin-bottom: 20px; margin-top: 0; }
.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.word-cell { font-family: monospace; font-weight: 700; font-size: 1rem; color: #1e1b4b; }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-visible { background: #dcfce7; color: #166534; }
.status-censored { background: #fee2e2; color: #991b1b; }
.status-pending { background: #fef9c3; color: #854d0e; }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-danger { background: #fee2e2; color: #dc2626; }
.btn-danger:hover { background: #fecaca; }
.btn-success { background: #dcfce7; color: #16a34a; }
.btn-success:hover { background: #bbf7d0; }
.btn-secondary { background: #ede9fe; color: #4f46e5; }
.btn-secondary:hover { background: #ddd6fe; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
