<template lang="pug">
.mod-words-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Moderación de palabras

  .filters
    FormField(label="Estado de moderación")
      template(#default="{ id, describedBy }")
        select(:id="id" :aria-describedby="describedBy" v-model="statusFilter" @change="applyFilter")
          option(value="") Todos los estados
          option(value="VISIBLE") Visible
          option(value="CENSURADO_AUTO") Censurado automático
          option(value="CENSURADO_MANUAL") Censurado manual
          option(value="PENDIENTE_REVISION") Pendiente revisión
          option(value="DELETED") Eliminado

  LoadingState(v-if="loading" message="Cargando palabras moderadas…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="words.length === 0" message="No hay palabras que coincidan con este filtro.")
  ModerationTable(v-else
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
        StatusBadge(:status="item.contentStatus")
      td.actions
        BaseButton(size="sm" variant="danger"
          v-if="item.contentStatus === 'VISIBLE' || item.contentStatus === 'PENDIENTE_REVISION'"
          @click="censor(item)"
          :disabled="item._loading"
          :loading="item._loading"
        ) Censurar
        BaseButton(
          variant="success"
          size="sm"
          v-if="item.contentStatus !== 'VISIBLE' && item.contentStatus !== 'DELETED'"
          @click="restore(item)"
          :disabled="item._loading"
          :loading="item._loading"
        ) Restaurar
        BaseButton(
          variant="danger"
          size="sm"
          v-if="item.contentStatus !== 'DELETED'"
          @click="deleteItem(item)"
          :disabled="item._loading"
          :loading="item._loading"
        ) Eliminar
        BaseButton(size="sm" variant="secondary" @click="verMensajes(item)") Ver mensajes
</template>

<script lang="ts">
import ModerationTable from '@/components/tables/ModerationTable.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import FormField from '@/components/ui/FormField.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
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
  components: { ModerationTable, DashboardBreadcrumb, ConferenceToolsNav, BaseButton, EmptyState, FeedbackMessage, FormField, LoadingState, StatusBadge },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const words = ref<ModWordItem[]>([])
    const loading = ref(false)
    const error = ref('')
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const conferenceName = ref('')
    const auth = useAuthStore()
    const router = useRouter()

    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      error.value = ''
      try {
        const res = await getModerationWords(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token as string)
        words.value = (res.data || []).map((w: any) => ({ ...w, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
      } catch (e: any) {
        error.value = 'No fue posible cargar las palabras moderadas. Inténtalo nuevamente.'
      } finally { loading.value = false }
    }

    function goToPage(p: number) { page.value = p; load() }
    function applyFilter() { page.value = 1; load() }

    async function censor(item: ModWordItem) {
      item._loading = true
      try { await censorWord(item.uuid, null as any, auth.state.token as string, props.conferenceId as string); await load() }
      catch (e: any) {
        item._loading = false
        error.value = 'No fue posible censurar la palabra. Inténtalo nuevamente.'
      }
    }

    async function restore(item: ModWordItem) {
      item._loading = true
      try { await restoreWord(item.uuid, auth.state.token as string, props.conferenceId as string); await load() }
      catch (e: any) {
        item._loading = false
        error.value = 'No fue posible restaurar la palabra. Inténtalo nuevamente.'
      }
    }

    async function deleteItem(item: ModWordItem) {
      item._loading = true
      try { await deleteWord(item.uuid, auth.state.token as string, props.conferenceId as string); await load() }
      catch (e: any) {
        item._loading = false
        error.value = 'No fue posible eliminar la palabra. Inténtalo nuevamente.'
      }
    }

    function verMensajes(item: ModWordItem) {
      const params = new URLSearchParams({
        wordNormalized: item.wordNormalized || item.wordCanonical,
        wordCanonical: item.wordCanonical
      })
      router.push(`/dashboard/conferences/${props.conferenceId}/moderation/messages?${params}`)
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
      { label: conferenceName.value || props.conferenceId || '', to: `/dashboard/conferences/${props.conferenceId}/moderation/words`, loading: !conferenceName.value },
      { label: 'Moderación (palabras)' }
    ])

    return { words, loading, error, page, totalPages, statusFilter, conferenceName, breadcrumbItems, load, applyFilter, goToPage, censor, restore, deleteItem, verMensajes }
  }
}
</script>

<style scoped>
.mod-words-page { }
h2 { color: var(--color-heading); margin-bottom: 20px; margin-top: 0; }
.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 0.9rem; }
.word-cell { font-family: monospace; font-weight: 700; font-size: 1rem; color: var(--color-heading); }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
