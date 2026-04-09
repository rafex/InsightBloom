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
        span.status(:class="statusClass(item.contentStatus)") {{ statusLabel(item.contentStatus) }}
      td.actions
        button.btn-sm.btn-danger(
          v-if="item.contentStatus === 'VISIBLE' || item.contentStatus === 'PENDIENTE_REVISION'"
          @click="censor(item)"
          :disabled="item._loading"
        ) Censurar
        button.btn-sm.btn-success(
          v-if="item.contentStatus !== 'VISIBLE'"
          @click="restore(item)"
          :disabled="item._loading"
        ) Restaurar
        button.btn-sm.btn-secondary(@click="verMensajes(item)") Ver mensajes
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

    async function censor(item) {
      item._loading = true
      try { await censorWord(item.uuid, null, auth.state.token); await load() }
      catch (e) { item._loading = false }
    }

    async function restore(item) {
      item._loading = true
      try { await restoreWord(item.uuid, auth.state.token); await load() }
      catch (e) { item._loading = false }
    }

    function verMensajes(item) {
      const params = new URLSearchParams({
        wordNormalized: item.wordNormalized || item.wordCanonical,
        wordCanonical: item.wordCanonical
      })
      router.push(`/dashboard/conferences/${props.conferenceId}/moderation/messages?${params}`)
    }

    function statusClass(s) {
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION' }
    }

    function statusLabel(s) {
      const map = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente' }
      return map[s] || s
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

    return { words, loading, page, totalPages, statusFilter, conferenceName, load, goToPage, censor, restore, verMensajes, statusClass, statusLabel }
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
