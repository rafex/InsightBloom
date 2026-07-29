<template lang="pug">
.mod-messages-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

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
        BaseButton(size="sm" variant="danger"
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

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getModerationMessages, censorMessage, restoreMessage, deleteMessage, answerMessage } from '@/services/api/moderationApi'
import { getWordTimeline } from '@/services/api/queryApi'
import { getConference, getUserProfile } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb, { type BreadcrumbItem } from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

interface ModMessageItem {
  id?: string
  messageId?: string
  messageUuid?: string
  uuid?: string
  wordText?: string
  word?: string
  detailText?: string
  detail?: string
  detailVisible?: string
  authorDisplayName?: string
  authorUuid?: string
  authorLabel?: string
  receivedAt?: string
  updatedAt?: string
  detailStatus: string | null
  answerText?: string
  _loading: boolean
  _answering?: boolean
  _answerDraft?: string
  [key: string]: unknown
}

export default {
  name: 'ModerationMessagesPage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, BaseButton },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const route = useRoute()
    const auth = useAuthStore()

    // Word filter comes from query params (set by ModerationWordsPage)
    const wordNormalized = (route.query.wordNormalized as string) || ''
    const wordCanonical = (route.query.wordCanonical as string) || ''

    const items = ref<ModMessageItem[]>([])
    const loading = ref(false)
    const page = ref(1)
    const totalPages = ref(1)
    const statusFilter = ref('')
    const conferenceName = ref('')
    const authorNames = ref<Record<string, string>>({})

    async function resolveAuthors() {
      const uuids = [...new Set(
        items.value.filter(i => !i.authorDisplayName).map(i => i.authorUuid)
      )]
        .filter((uuid): uuid is string => !!uuid && uuid !== 'anonymous' && !(uuid in authorNames.value))
      for (const uuid of uuids) {
        try {
          const profile = await getUserProfile(uuid, auth.state.token as string)
          authorNames.value = { ...authorNames.value, [uuid]: profile?.displayName || profile?.email || profile?.phone || 'Anónimo' }
        } catch (e: any) {
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
        ].filter((m: any) => {
          const id = m.messageId || m.uuid
          if (seen.has(id)) return false
          seen.add(id)
          return true
        })
        // Sort by receivedAt descending
        merged.sort((a: any, b: any) => new Date(b.receivedAt).getTime() - new Date(a.receivedAt).getTime())
        items.value = merged.map((m: any) => ({ ...m, detailStatus: null, _loading: false }))
      } catch (e: any) { } finally { loading.value = false }
    }

    // ── Global moderation messages view ──────────────────────────────────────
    async function loadModMessages() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await getModerationMessages(props.conferenceId, page.value, 20, statusFilter.value, auth.state.token as string)
        items.value = (res.data || []).map((m: any) => ({ ...m, _loading: false }))
        totalPages.value = res.meta?.totalPages || 1
        resolveAuthors()
      } catch (e: any) { } finally { loading.value = false }
    }

    function goToPage(p: number) { page.value = p; loadModMessages() }

    async function censorDetail(item: ModMessageItem) {
      item._loading = true
      const messageId = (item.messageId || item.uuid) as string
      const detailTxt = item.detail || item.detailVisible || item.detailText || ''
      const wordTxt = item.word || item.wordText || wordCanonical || ''
      try {
        await censorMessage(
          messageId, null as any, 'detail',
          auth.state.token as string,
          props.conferenceId as string, wordTxt, detailTxt
        )
        item.detailStatus = 'CENSURADO_MANUAL'
        item._loading = false
      } catch (e: any) { item._loading = false }
    }

    async function restore(item: ModMessageItem) {
      item._loading = true
      const messageId = (item.messageId || item.uuid) as string
      try {
        await restoreMessage(messageId, auth.state.token as string, props.conferenceId as string)
        item.detailStatus = 'VISIBLE'
        item._loading = false
      } catch (e: any) { item._loading = false }
    }

    async function deleteItem(item: ModMessageItem) {
      item._loading = true
      const messageId = (item.messageId || item.uuid) as string
      try {
        await deleteMessage(messageId, auth.state.token as string, props.conferenceId as string)
        item.detailStatus = 'DELETED'
        item._loading = false
      } catch (e: any) { item._loading = false }
    }

    function startAnswering(item: ModMessageItem) {
      item._answerDraft = item.answerText || ''
      item._answering = true
    }

    async function submitAnswer(item: ModMessageItem) {
      item._loading = true
      const messageId = (item.uuid || item.messageId || item.messageUuid) as string
      try {
        await answerMessage(messageId, item._answerDraft as string, auth.state.userUuid as string, auth.state.token as string, props.conferenceId as string)
        item.answerText = item._answerDraft
        item._answering = false
      } finally {
        item._loading = false
      }
    }

    function statusClass(s: string | null | undefined) {
      if (!s) return {}
      return { 'status-visible': s === 'VISIBLE', 'status-censored': s?.startsWith('CENSURADO'), 'status-pending': s === 'PENDIENTE_REVISION', 'status-deleted': s === 'DELETED' }
    }

    function statusLabel(s: string | null | undefined): string {
      if (!s) return 'Visible'
      const map: Record<string, string> = { VISIBLE: 'Visible', CENSURADO_AUTO: 'Auto', CENSURADO_MANUAL: 'Manual', PENDIENTE_REVISION: 'Pendiente', DELETED: 'Eliminado' }
      return map[s] || s
    }

    function formatTime(ts: string | undefined): string {
      if (!ts) return ''
      return new Date(ts).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
    }

    onMounted(async () => {
      if (props.conferenceId) {
        try {
          const conf = await getConference(props.conferenceId, auth.state.token as string)
          conferenceName.value = conf?.name || props.conferenceId
        } catch (e: any) { conferenceName.value = props.conferenceId as string }
      }
      if (wordNormalized) {
        loadWordTimeline()
      } else {
        loadModMessages()
      }
    })

    const breadcrumbItems = computed(() => {
      const wordsPath = `/dashboard/conferences/${props.conferenceId}/moderation/words`
      const items: BreadcrumbItem[] = [
        { label: conferenceName.value || props.conferenceId || '', to: wordsPath, loading: !conferenceName.value }
      ]
      if (wordCanonical) {
        items.push({ label: 'Moderación de palabras', to: wordsPath })
        items.push({ label: `"${wordCanonical}"` })
      } else {
        items.push({ label: 'Moderación (mensajes)' })
      }
      return items
    })

    return {
      items, loading, page, totalPages, statusFilter, conferenceName, authorNames,
      wordNormalized, wordCanonical, breadcrumbItems,
      loadModMessages, goToPage, censorDetail, restore, deleteItem, startAnswering, submitAnswer,
      statusClass, statusLabel, formatTime
    }
  }
}
</script>

<style scoped>
.mod-messages-page { }
h2 { color: #1e1b4b; margin-bottom: 20px; margin-top: 0; }
.word-filter-badge { font-family: monospace; color: #4f46e5; }

.filters { margin-bottom: 16px; }
select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }

.empty-state { text-align: center; color: var(--color-text-muted); padding: 60px; }

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
