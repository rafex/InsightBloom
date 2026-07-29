<template lang="pug">
.timeline-page
  nav.breadcrumbs(aria-label="breadcrumb")
    router-link(:to="`/c/${friendlyId}/${type === 'topic' ? 'topics' : 'doubts'}`")
      | ← {{ typeLabel }}s de "{{ friendlyId }}"
    span.sep /
    span.crumb-current "{{ wordDecoded }}"

  .timeline-header
    span.type-badge {{ typeLabel }}
    h2 "{{ wordDecoded }}"

  .timeline-empty(v-if="!loading && !items.length") No hay mensajes para esta palabra aún.
  .timeline-list(v-if="items.length")
    TimelineItem(v-for="item in items" :key="item.messageId || item.uuid" :item="item" :conference-id="conferenceId")
  .timeline-loading(v-if="loading") Cargando timeline...
</template>

<script lang="ts">
import TimelineItem from '@/components/timeline/TimelineItem.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getWordTimeline } from '@/services/api/queryApi'
export default {
  name: 'WordTimelinePage',
  components: { TimelineItem },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const route = useRoute()
    const friendlyId = route.params.friendlyId as string
    const word = route.params.word as string
    const wordDecoded = decodeURIComponent(word)
    const type = (route.query.type as string) || 'doubt'
    const typeLabel = type === 'doubt' ? 'Duda' : 'Tema'
    const items = ref<any[]>([])
    const loading = ref(true)
    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try { items.value = await getWordTimeline(props.conferenceId, wordDecoded, type) }
      catch (e: any) { } finally { loading.value = false }
    })
    return { friendlyId, word, wordDecoded, type, typeLabel, items, loading }
  }
}
</script>

<style scoped>
.timeline-page { padding: 24px; max-width: 720px; margin: 0 auto; }

.breadcrumbs {
  display: flex; align-items: center; gap: 6px;
  font-size: 0.85rem; color: var(--color-text-muted); margin-bottom: 20px; flex-wrap: wrap;
}
.breadcrumbs a { color: var(--color-primary); text-decoration: none; }
.breadcrumbs a:hover { text-decoration: underline; }
.sep { color: var(--color-border); }
.crumb-current { color: var(--color-text-secondary); font-weight: 600; font-family: monospace; }

.timeline-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
h2 { margin: 0; color: var(--color-heading); font-size: 1.4rem; font-family: monospace; }
.type-badge { padding: 4px 12px; background: var(--color-primary-soft); color: var(--color-primary); border-radius: 12px; font-size: 0.85rem; font-weight: 600; }
.timeline-empty { text-align: center; color: var(--color-text-muted); padding: 60px; }
.timeline-loading { text-align: center; color: var(--color-text-muted); padding: 40px; }

@media (max-width: 640px) {
  .timeline-page { padding: 14px; }
  h2 { font-size: 1.15rem; }
}
</style>
