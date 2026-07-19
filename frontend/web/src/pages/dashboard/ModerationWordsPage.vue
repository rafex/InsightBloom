<template lang="pug">
.mod-words-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  nav.sub-links
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/moderation/messages`") Moderación (mensajes)
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/moderation/words`") Moderación (palabras)
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/presentation`") Presentación
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/survey`") Encuesta

  h2 Moderación de palabras

  .filters
    select(v-model="statusFilter" @change="load")
      option(value="") Todos los estados
      option(value="VISIBLE") Visible
      option(value="CENSURADO_AUTO") Censurado automático
      option(value="CENSURADO_MANUAL") Censurado manual
      option(value="PENDIENTE_REVISION") Pendiente revisión
      option(value="DELETED") Eliminado

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
          v-if="item.contentStatus !== 'VISIBLE' && item.contentStatus !== 'DELETED'"
          @click="restore(item)"
          :disabled="item._loading"
        ) Restaurar
        button.btn-sm.btn-warning(
          v-if="item.contentStatus !== 'DELETED'"
          @click="deleteItem(item)"
          :disabled="item._loading"
        ) Eliminar
        button.btn-sm.btn-secondary(@click="verMensajes(item)") Ver mensajes
</template>

<script lang="ts">
import ModerationTable from '@/components/tables/ModerationTable.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getModerationWords, censorWord, restoreWord, deleteWord } from '@/services/api/moderationApi'
import { getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

interface ModWordItem {
  uuid: string
  wordCanonical: string
  wordNormalized: string
  contentStatus: string
  _loading: boolean
  [key: string]: unknown
}

export default {
  name: 'ModerationWordsPage',
  components: { ModerationTable, DashboardBreadcrumb },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const words = ref<ModWordItem[]>([])
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
        const res = await getModerationWords(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token as string)
        words.value = (res.data || []).map((w: any) => ({ ...w, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
      } catch (e: any) { } finally { loading.value = false }
    }

    function goToPage(p: number) { page.value = p; load() }

    async function censor(item: ModWordItem) {
      item._loading = true
      try { await censorWord(item.uuid, null as any, auth.state.token as string, props.conferenceId as string); await load() }
      catch (e: any) { item._loading = false }
    }

    async function restore(item: ModWordItem) {
      item._loading = true
      try { await restoreWord(item.uuid, auth.state.token as string, props.conferenceId as string); await load() }
      catch (e: any) { item._loading = false }
    }

    async function deleteItem(item: ModWordItem) {
      item._loading = true
      try { await deleteWord(item.uuid, auth.state.token as string, props.conferenceId as string); await load() }
      catch (e: any) { item._loading = false }
    }

    function verMensajes(item: ModWordItem) {
      const params = new URLSearchParams({
        wordNormalized: item.wordNormalized || item.wordCanonical,
        wordCanonical: item.wordCanonical
      })
      router.push(`/dashboard/conferences/${props.conferenceId}/moderation/messages?${params}`)
    }

    function statusClass(s: string | undefined) {
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION', 'status-deleted': s === 'DELETED' }
    }

    function statusLabel(s: string): string {
      const map: Record<string, string> = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente', DELETED: 'Eliminado' }
      return map[s] || s
    }

    onMounted(async () => {
      load()
      if (props.conferenceId) {
        try {
          const conf = await getConference(props.conferenceId, auth.state.token as string)
          conferenceName.value = conf?.name || props.conferenceId
        } catch (e: any) { conferenceName.value = props.conferenceId as string }
      }
    })

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: conferenceName.value || props.conferenceId || '', to: `/dashboard/conferences/${props.conferenceId}/moderation/words`, loading: !conferenceName.value },
      { label: 'Moderación (palabras)' }
    ])

    return { words, loading, page, totalPages, statusFilter, conferenceName, breadcrumbItems, load, goToPage, censor, restore, deleteItem, verMensajes, statusClass, statusLabel }
  }
}
</script>

<style scoped>
.mod-words-page { }
.sub-links { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 20px; }
.sub-link {
  padding: 6px 14px; border: 1.5px solid #e5e7eb; border-radius: 20px; text-decoration: none;
  color: #374151; font-size: 0.82rem; font-weight: 500; transition: all 0.15s;
}
.sub-link:hover { border-color: #a5b4fc; color: #4f46e5; }
.sub-link.router-link-active { background: #4f46e5; color: #fff; border-color: #4f46e5; }
h2 { color: #1e1b4b; margin-bottom: 20px; margin-top: 0; }
.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.word-cell { font-family: monospace; font-weight: 700; font-size: 1rem; color: #1e1b4b; }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-visible { background: #dcfce7; color: #166534; }
.status-censored { background: #fee2e2; color: #991b1b; }
.status-pending { background: #fef9c3; color: #854d0e; }
.status-deleted { background: #f3f4f6; color: #6b7280; }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-danger { background: #fee2e2; color: #dc2626; }
.btn-danger:hover { background: #fecaca; }
.btn-success { background: #dcfce7; color: #16a34a; }
.btn-success:hover { background: #bbf7d0; }
.btn-warning { background: #fef3c7; color: #d97706; }
.btn-warning:hover { background: #fde68a; }
.btn-secondary { background: #ede9fe; color: #4f46e5; }
.btn-secondary:hover { background: #ddd6fe; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
