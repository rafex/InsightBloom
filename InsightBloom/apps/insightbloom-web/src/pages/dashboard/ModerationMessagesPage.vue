<template lang="pug">
.mod-messages-page
  nav.breadcrumbs(aria-label="breadcrumb")
    router-link(to="/dashboard") Dashboard
    span.sep /
    span(v-if="conferenceName")
      router-link(:to="`/dashboard/conferences/${conferenceId}/moderation/words`") {{ conferenceName }}
    span.crumb-loading(v-else) …
    span.sep /
    router-link(:to="`/dashboard/conferences/${conferenceId}/moderation/words`") Moderación de palabras
    template(v-if="wordCanonical")
      span.sep /
      span.crumb-current "{{ wordCanonical }}"

  h2
    | Mensajes
    span.word-filter-badge(v-if="wordCanonical") &nbsp;de "{{ wordCanonical }}"

  .filters(v-if="!wordCanonical")
    select(v-model="statusFilter" @change="loadModMessages")
      option(value="") Todos los estados
      option(value="VISIBLE") Visible
      option(value="CENSURADO_AUTO") Censurado automático
      option(value="CENSURADO_MANUAL") Censurado manual
      option(value="PENDIENTE_REVISION") Pendiente revisión

  .empty-state(v-if="!loading && items.length === 0")
    p No hay mensajes para moderar.

  .message-list(v-if="items.length")
    .message-row(v-for="item in items" :key="item.id || item.messageId || item.uuid")
      .msg-word(v-if="!wordCanonical")
        span.word-chip {{ item.wordText || item.wordCanonical || '—' }}
      .msg-detail(v-if="!item._editing")
        | {{ item.detailVisible || item.detail || item.detailText || item.detailOriginal || '—' }}
      .msg-edit-form(v-else)
        textarea.edit-textarea(v-model="item._editedDetail" rows="3")
        .edit-actions
          button.btn-sm.btn-primary(@click="saveEdit(item)" :disabled="item._loading") Guardar
          button.btn-sm.btn-ghost(@click="cancelEdit(item)") Cancelar
      .msg-meta
        span.msg-author {{ item.author?.displayName || item.authorLabel || 'Anónimo' }}
        span.msg-time(v-if="item.receivedAt || item.updatedAt") · {{ formatTime(item.receivedAt || item.updatedAt) }}
      .msg-status
        span.status(:class="statusClass(item.detailStatus || item.contentStatus)") {{ statusLabel(item.detailStatus || item.contentStatus) }}
      .msg-actions
        button.btn-sm.btn-danger(
          v-if="canCensor(item)"
          @click="censorDetail(item)"
          :disabled="item._loading"
        ) Censurar
        button.btn-sm.btn-success(
          v-if="canRestore(item)"
          @click="restore(item)"
          :disabled="item._loading"
        ) Restaurar
        button.btn-sm.btn-secondary(
          v-if="!item._editing"
          @click="startEdit(item)"
        ) Editar

  .pagination(v-if="!wordCanonical && totalPages > 1")
    button(@click="goToPage(page - 1)" :disabled="page <= 1") ‹
    span Página {{ page }} / {{ totalPages }}
    button(@click="goToPage(page + 1)" :disabled="page >= totalPages") ›
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getModerationMessages, censorMessage, restoreMessage, editMessage } from '@/services/api/moderationApi'
import { getWordTimeline } from '@/services/api/queryApi'
import { getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'ModerationMessagesPage',
  props: { conferenceId: String },
  setup(props) {
    const route = useRoute()
    const auth = useAuthStore()

    const wordNormalized = route.query.wordNormalized || ''
    const wordCanonical = route.query.wordCanonical || ''

    const items = ref([])
    const loading = ref(false)
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const conferenceName = ref('')

    async function loadWordTimeline() {
      if (!props.conferenceId || !wordNormalized) return
      loading.value = true
      try {
        const [doubts, topics] = await Promise.allSettled([
          getWordTimeline(props.conferenceId, wordCanonical || wordNormalized, 'doubt'),
          getWordTimeline(props.conferenceId, wordCanonical || wordNormalized, 'topic')
        ])
        const seen = new Set()
        const merged = [
          ...(doubts.status === 'fulfilled' ? doubts.value || [] : []),
          ...(topics.status === 'fulfilled' ? topics.value || [] : [])
        ].filter(m => {
          const id = m.messageId || m.uuid
          if (seen.has(id)) return false
          seen.add(id)
          return true
        })
        merged.sort((a, b) => new Date(b.receivedAt) - new Date(a.receivedAt))
        items.value = merged.map(m => ({ ...m, detailStatus: null, _loading: false, _editing: false, _editedDetail: '' }))
      } catch (e) { } finally { loading.value = false }
    }

    async function loadModMessages() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await getModerationMessages(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token)
        items.value = (res.data || []).map(m => ({ ...m, _loading: false, _editing: false, _editedDetail: '' }))
        totalPages.value = res.meta?.totalPages || 1
      } catch (e) { } finally { loading.value = false }
    }

    function goToPage(p) { page.value = p; loadModMessages() }

    async function censorDetail(item) {
      item._loading = true
      const messageId = item.messageId || item.uuid
      const detailTxt = item.detailVisible || item.detail || item.detailText || item.detailOriginal || ''
      const wordTxt = item.word || item.wordText || item.wordCanonical || wordCanonical || ''
      try {
        await censorMessage(messageId, null, 'detail', auth.state.token, props.conferenceId, wordTxt, detailTxt)
        item.detailStatus = 'CENSURADO_MANUAL'
        item.contentStatus = 'CENSURADO_MANUAL'
      } catch (e) { } finally { item._loading = false }
    }

    async function restore(item) {
      item._loading = true
      const messageId = item.messageId || item.uuid
      try {
        await restoreMessage(messageId, auth.state.token)
        item.detailStatus = 'VISIBLE'
        item.contentStatus = 'VISIBLE'
      } catch (e) { } finally { item._loading = false }
    }

    function startEdit(item) {
      item._editedDetail = item.detailVisible || item.detail || item.detailText || item.detailOriginal || ''
      item._editing = true
    }

    function cancelEdit(item) {
      item._editing = false
      item._editedDetail = ''
    }

    async function saveEdit(item) {
      item._loading = true
      const messageId = item.messageId || item.uuid
      try {
        await editMessage(messageId, null, item._editedDetail, auth.state.token)
        item.detailVisible = item._editedDetail
        item.detailText = item._editedDetail
        item.detailOriginal = item._editedDetail
        item._editing = false
      } catch (e) { } finally { item._loading = false }
    }

    function canCensor(item) {
      const s = item.detailStatus || item.contentStatus
      return !s || s === 'VISIBLE' || s === 'PENDIENTE_REVISION'
    }

    function canRestore(item) {
      const s = item.detailStatus || item.contentStatus
      return s && s !== 'VISIBLE'
    }

    function statusClass(s) {
      if (!s) return {}
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION' }
    }

    function statusLabel(s) {
      if (!s) return 'Visible'
      const map = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente' }
      return map[s] || s
    }

    function formatTime(ts) {
      if (!ts) return ''
      return new Date(ts).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
    }

    onMounted(async () => {
      if (props.conferenceId) {
        try {
          const conf = await getConference(props.conferenceId, auth.state.token)
          conferenceName.value = conf?.name || props.conferenceId
        } catch (e) { conferenceName.value = props.conferenceId }
      }
      if (wordNormalized) {
        loadWordTimeline()
      } else {
        loadModMessages()
      }
    })

    return {
      items, loading, page, totalPages, statusFilter, conferenceName,
      wordNormalized, wordCanonical,
      conferenceId: props.conferenceId,
      loadModMessages, goToPage,
      censorDetail, restore, startEdit, cancelEdit, saveEdit,
      canCensor, canRestore, statusClass, statusLabel, formatTime
    }
  }
}
</script>

<style scoped>
.mod-messages-page { }

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
.word-filter-badge { font-family: monospace; color: #4f46e5; }

.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }

.empty-state { text-align: center; color: #9ca3af; padding: 60px; }

.message-list { display: flex; flex-direction: column; gap: 12px; }

.message-row {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left: 4px solid #4f46e5;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex; flex-direction: column; gap: 6px;
}
.word-chip {
  font-family: monospace; font-weight: 700; font-size: 0.9rem;
  background: #ede9fe; color: #4f46e5;
  padding: 2px 8px; border-radius: 6px;
}
.msg-detail { color: #1f2937; font-size: 0.95rem; line-height: 1.5; }
.msg-edit-form { display: flex; flex-direction: column; gap: 6px; }
.edit-textarea {
  width: 100%; padding: 8px; border: 1.5px solid #a5b4fc; border-radius: 6px;
  font-size: 0.9rem; font-family: inherit; resize: vertical;
}
.edit-actions { display: flex; gap: 6px; }
.msg-meta { display: flex; gap: 8px; font-size: 0.82rem; color: #6b7280; }
.msg-author { font-weight: 600; color: #374151; }
.msg-status { }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-visible { background: #dcfce7; color: #166534; }
.status-censored { background: #fee2e2; color: #991b1b; }
.status-pending { background: #fef9c3; color: #854d0e; }
.msg-actions { display: flex; gap: 6px; flex-wrap: wrap; }

.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-danger { background: #fee2e2; color: #dc2626; }
.btn-danger:hover { background: #fecaca; }
.btn-success { background: #dcfce7; color: #16a34a; }
.btn-success:hover { background: #bbf7d0; }
.btn-secondary { background: #ede9fe; color: #4f46e5; }
.btn-secondary:hover { background: #ddd6fe; }
.btn-primary { background: #4f46e5; color: #fff; }
.btn-primary:hover { background: #4338ca; }
.btn-ghost { background: #f3f4f6; color: #374151; }
.btn-ghost:hover { background: #e5e7eb; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }

.pagination { display: flex; align-items: center; gap: 12px; margin-top: 20px; justify-content: center; font-size: 0.9rem; color: #374151; }
.pagination button { padding: 4px 12px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; cursor: pointer; font-size: 1rem; }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
