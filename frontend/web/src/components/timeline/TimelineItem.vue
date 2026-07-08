<template lang="pug">
.timeline-item.animate__animated.animate__fadeInUp
  .timeline-meta
    span.author {{ item.author?.displayName || 'Anónimo' }}
    span.kind.badge(:class="item.author?.kind") {{ item.author?.kind }}
    span.time {{ formattedTime }}
  .timeline-detail {{ item.detail || item.detailVisible || '—' }}
  .timeline-answer(v-if="answer")
    strong ✓ Respondida
    p.answer-text {{ answer }}
</template>

<script lang="ts">
import { getMessageAnswer } from '@/services/api/moderationApi'
import type { PropType } from 'vue'

interface TimelineMessage {
  messageId?: string
  uuid?: string
  receivedAt?: string
  author?: { displayName?: string, kind?: string }
  detail?: string
  detailVisible?: string
  [key: string]: unknown
}

export default {
  name: 'TimelineItem',
  props: {
    item: { type: Object as PropType<TimelineMessage>, required: true },
    conferenceId: { type: String, default: '' }
  },
  data(): { answer: string | null } {
    return { answer: null }
  },
  computed: {
    formattedTime() {
      const ts = this.item.receivedAt
      if (!ts) return ''
      return new Date(ts).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
    }
  },
  async mounted() {
    const messageId = this.item.messageId || this.item.uuid
    if (!messageId || !this.conferenceId) return
    try {
      const result = await getMessageAnswer(messageId, this.conferenceId) as { answer?: string } | undefined
      this.answer = result?.answer || null
    } catch (e: any) { /* sin respuesta aún */ }
  }
}
</script>

<style scoped>
.timeline-item {
  background: #fff;
  border-left: 4px solid #4f46e5;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.timeline-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 0.85rem;
  color: #6b7280;
}
.author { font-weight: 600; color: #374151; }
.badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  background: #e0e7ff;
  color: #3730a3;
}
.badge.guest { background: #fef9c3; color: #854d0e; }
.timeline-detail { color: #1f2937; font-size: 0.95rem; line-height: 1.5; }
.timeline-answer { background: #f0fdf4; border-radius: 8px; padding: 10px 12px; margin-top: 10px; }
.timeline-answer strong { color: #166534; font-size: 0.8rem; }
.answer-text { margin: 4px 0 0; color: #1f2937; font-size: 0.9rem; white-space: pre-wrap; }
</style>
