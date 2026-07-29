<template lang="pug">
.conference-schedule-page
  .schedule-card
    h2 Cronograma
    .markdown-body(v-if="renderedSchedule" v-html="renderedSchedule")
    p.empty-state(v-else) El organizador todavía no publicó un cronograma para este evento.
</template>

<script lang="ts">
import { computed } from 'vue'
import { marked, Renderer } from 'marked'

export default {
  name: 'ConferenceSchedulePage',
  props: {
    scheduleMarkdown: { type: String, default: '' }
  },
  setup(props: { scheduleMarkdown?: string }) {
    const renderer = new Renderer()
    renderer.html = () => ''
    const renderedSchedule = computed(() => {
      if (!props.scheduleMarkdown?.trim()) return ''
      const html = marked.parse(props.scheduleMarkdown, { async: false, renderer }) as string
      return html.replace(/href\s*=\s*["'](?!https?:\/\/|mailto:)[^"']*["']/gi, 'href="#"')
    })

    return { renderedSchedule }
  }
}
</script>

<style scoped>
.conference-schedule-page { max-width: 900px; margin: 0 auto; padding: 24px; }
.schedule-card { padding: 28px; border: 1px solid var(--color-border-subtle); border-radius: 16px; background: var(--color-surface); box-shadow: var(--shadow-card, 0 1px 3px rgba(17,24,39,.12)); }
h2 { margin: 0 0 20px; color: var(--color-heading); }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { color: var(--color-heading); margin-top: 20px; }
.markdown-body :deep(h1:first-child), .markdown-body :deep(h2:first-child), .markdown-body :deep(h3:first-child) { margin-top: 0; }
.markdown-body :deep(p), .markdown-body :deep(li) { color: var(--color-text-secondary); line-height: 1.6; }
.markdown-body :deep(a) { color: var(--color-primary); }
.empty-state { margin: 0; color: var(--color-text-muted); }
@media (max-width: 640px) { .conference-schedule-page { padding: 14px; }.schedule-card { padding: 20px 16px; } }
</style>
