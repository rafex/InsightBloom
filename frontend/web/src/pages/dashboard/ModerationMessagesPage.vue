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

  nav.sub-links
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/moderation/messages`") Moderación (mensajes)
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/moderation/words`") Moderación (palabras)
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/presentation`") Presentación
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/survey`") Encuesta

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
      option(value="DELETED") Eliminado

  .empty-state(v-if="!loading && items.length === 0")
    p No hay mensajes para moderar.

  .message-list(v-else)
    .message-row(v-for="item in items" :key="item.id || item.messageId || item.uuid")
      .msg-word(v-if="!wordCanonical")
        span.word-chip {{ item.wordText || '—' }}
      .msg-detail {{ item.detailText || item.detail || item.detailVisible || '—' }}
      .msg-meta
        span.msg-author {{ item.authorDisplayName || authorNames[item.authorUuid] || item.authorLabel || 'Anónimo' }}
        span.msg-time(v-if="item.receivedAt || item.updatedAt") · {{ formatTime(item.receivedAt || item.updatedAt) }}
      .msg-status
        span.status(:class="statusClass(item.detailStatus)") {{ statusLabel(item.detailStatus) }}
      .msg-actions
        button.btn-sm.btn-danger(
          v-if="!item.detailStatus || item.detailStatus === 'VISIBLE' || item.detailStatus === 'PENDIENTE_REVISION'"
          @click="censorDetail(item)"
          :disabled="item._loading"
        ) Censurar detalle
        button.btn-sm.btn-success(
          v-if="item.detailStatus && item.detailStatus !== 'VISIBLE' && item.detailStatus !== 'DELETED'"
          @click="restore(item)"
          :disabled="item._loading"
        ) Restaurar
        button.btn-sm.btn-warning(
          v-if="!item.detailStatus || item.detailStatus !== 'DELETED'"
          @click="deleteItem(item)"
          :disabled="item._loading"
        ) Eliminar
        button.btn-sm.btn-answer(
          v-if="!item.answerText && !item._answering"
          @click="startAnswering(item)"
        ) 💬 Responder
        button.btn-sm.btn-answer(
          v-if="item.answerText && !item._answering"
          @click="startAnswering(item)"
        ) ✏️ Editar respuesta

      .answer-block(v-if="item.answerText && !item._answering")
        strong ✓ Respondida
        p.answer-text {{ item.answerText }}

      .answer-form(v-if="item._answering")
        textarea(v-model="item._answerDraft" rows="3" placeholder="Escribe la respuesta para quien envió esta duda...")
        .answer-form-actions
          button.btn-sm.btn-primary-sm(:disabled="!item._answerDraft || item._loading" @click="submitAnswer(item)") Enviar respuesta
          button.btn-sm.btn-ghost-sm(@click="item._answering = false") Cancelar

  .pagination(v-if="!wordCanonical && totalPages > 1")
    button(@click="goToPage(page - 1)" :disabled="page <= 1") ‹
    span Página {{ page }} / {{ totalPages }}
    button(@click="goToPage(page + 1)" :disabled="page >= totalPages") ›
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getModerationMessages, censorMessage, restoreMessage, deleteMessage, answerMessage } from '@/services/api/moderationApi'
import { getWordTimeline } from '@/services/api/queryApi'
import { getConference, getUserProfile } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'ModerationMessagesPage',
  props: { conferenceId: String },
  setup(props) {
    const route = useRoute()
    const auth = useAuthStore()

    // Word filter comes from query params (set by ModerationWordsPage)
    const wordNormalized = route.query.wordNormalized || ''
    const wordCanonical = route.query.wordCanonical || ''

    const items = ref([])
    const loading = ref(false)
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const conferenceName = ref('')
    const authorNames = ref({})

    async function resolveAuthors() {
      const uuids = [...new Set(
        items.value.filter(i => !i.authorDisplayName).map(i => i.authorUuid)
      )]
        .filter(uuid => uuid && uuid !== 'anonymous' && !(uuid in authorNames.value))
      for (const uuid of uuids) {
        try {
          const profile = await getUserProfile(uuid)
          authorNames.value = { ...authorNames.value, [uuid]: profile?.displayName || profile?.email || profile?.phone || 'Anónimo' }
        } catch (e) {
          authorNames.value = { ...authorNames.value, [uuid]: 'Anónimo' }
        }
      }
    }

    // ── Word-filtered view: fetch from word timeline (query service) ──────────
    async function loadWordTimeline() {
      if (!props.conferenceId || !wordNormalized) return
      loading.value = true
      try {
        // Fetch both types and merge (a word might be doubt or topic)
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
        // Sort by receivedAt descending
        merged.sort((a, b) => new Date(b.receivedAt) - new Date(a.receivedAt))
        items.value = merged.map(m => ({ ...m, detailStatus: null, _loading: false }))
      } catch (e) { } finally { loading.value = false }
    }

    // ── Global moderation messages view ──────────────────────────────────────
    async function loadModMessages() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await getModerationMessages(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token)
        items.value = (res.data || []).map(m => ({ ...m, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
        resolveAuthors()
      } catch (e) { } finally { loading.value = false }
    }

    function goToPage(p) { page.value = p; loadModMessages() }

    async function censorDetail(item) {
      item._loading = true
      const messageId = item.messageId || item.uuid
      const detailTxt = item.detail || item.detailVisible || item.detailText || ''
      const wordTxt = item.word || item.wordText || wordCanonical || ''
      try {
        await censorMessage(
          messageId, null, 'detail',
          auth.state.token,
          props.conferenceId, wordTxt, detailTxt
        )
        item.detailStatus = 'CENSURADO_MANUAL'
        item._loading = false
      } catch (e) { item._loading = false }
    }

    async function restore(item) {
      item._loading = true
      const messageId = item.messageId || item.uuid
      try {
        await restoreMessage(messageId, auth.state.token, props.conferenceId)
        item.detailStatus = 'VISIBLE'
        item._loading = false
      } catch (e) { item._loading = false }
    }

    async function deleteItem(item) {
      item._loading = true
      const messageId = item.messageId || item.uuid
      try {
        await deleteMessage(messageId, auth.state.token, props.conferenceId)
        item.detailStatus = 'DELETED'
        item._loading = false
      } catch (e) { item._loading = false }
    }

    function startAnswering(item) {
      item._answerDraft = item.answerText || ''
      item._answering = true
    }

    async function submitAnswer(item) {
      item._loading = true
      const messageId = item.uuid || item.messageId || item.messageUuid
      try {
        await answerMessage(messageId, item._answerDraft, auth.state.userUuid, auth.state.token, props.conferenceId)
        item.answerText = item._answerDraft
        item._answering = false
      } finally {
        item._loading = false
      }
    }

    function statusClass(s) {
      if (!s) return {}
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION', 'status-deleted': s === 'DELETED' }
    }

    function statusLabel(s) {
      if (!s) return 'Visible'
      const map = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente', DELETED: 'Eliminado' }
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
      items, loading, page, totalPages, statusFilter, conferenceName, authorNames,
      wordNormalized, wordCanonical,
      conferenceId: props.conferenceId,
      loadModMessages, goToPage, censorDetail, restore, deleteItem, startAnswering, submitAnswer,
      statusClass, statusLabel, formatTime
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
.sub-links { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 20px; }
.sub-link {
  padding: 6px 14px; border: 1.5px solid #e5e7eb; border-radius: 20px; text-decoration: none;
  color: #374151; font-size: 0.82rem; font-weight: 500; transition: all 0.15s;
}
.sub-link:hover { border-color: #a5b4fc; color: #4f46e5; }
.sub-link.router-link-active { background: #4f46e5; color: #fff; border-color: #4f46e5; }

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
.msg-word { }
.word-chip {
  font-family: monospace; font-weight: 700; font-size: 0.9rem;
  background: #ede9fe; color: #4f46e5;
  padding: 2px 8px; border-radius: 6px;
}
.msg-detail { color: #1f2937; font-size: 0.95rem; line-height: 1.5; }
.msg-meta { display: flex; gap: 8px; font-size: 0.82rem; color: #6b7280; }
.msg-author { font-weight: 600; color: #374151; }
.msg-status { }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-visible { background: #dcfce7; color: #166534; }
.status-censored { background: #fee2e2; color: #991b1b; }
.status-pending { background: #fef9c3; color: #854d0e; }
.status-deleted { background: #f3f4f6; color: #6b7280; }
.msg-actions { display: flex; gap: 6px; }

.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-danger { background: #fee2e2; color: #dc2626; }
.btn-danger:hover { background: #fecaca; }
.btn-success { background: #dcfce7; color: #16a34a; }
.btn-success:hover { background: #bbf7d0; }
.btn-warning { background: #fef3c7; color: #d97706; }
.btn-warning:hover { background: #fde68a; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-answer { background: #e0e7ff; color: #4338ca; }
.btn-answer:hover { background: #c7d2fe; }
.btn-primary-sm { background: #4f46e5; color: #fff; }
.btn-ghost-sm { background: #fff; color: #6b7280; border: 1px solid #e5e7eb; }
.answer-block { background: #f0fdf4; border-radius: 8px; padding: 10px 12px; margin-top: 6px; }
.answer-block strong { color: #166534; font-size: 0.8rem; }
.answer-text { margin: 4px 0 0; color: #1f2937; font-size: 0.9rem; white-space: pre-wrap; }
.answer-form { margin-top: 6px; display: flex; flex-direction: column; gap: 6px; }
.answer-form textarea {
  width: 100%; padding: 8px 10px; border: 1.5px solid #d1d5db; border-radius: 8px;
  font-size: 0.9rem; font-family: inherit; resize: vertical;
}
.answer-form-actions { display: flex; gap: 6px; }

.pagination { display: flex; align-items: center; gap: 12px; margin-top: 20px; justify-content: center; font-size: 0.9rem; color: #374151; }
.pagination button { padding: 4px 12px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; cursor: pointer; font-size: 1rem; }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }

@media (max-width: 480px) {
  .msg-actions { flex-wrap: wrap; }
}
</style>
