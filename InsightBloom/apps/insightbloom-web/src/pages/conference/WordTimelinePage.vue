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
    TimelineItem(v-for="item in items" :key="item.messageId || item.uuid" :item="item")
  .timeline-loading(v-if="loading") Cargando timeline...
</template>

<script>
import TimelineItem from '@/components/timeline/TimelineItem.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getWordTimeline } from '@/services/api/queryApi'
export default {
  name: 'WordTimelinePage',
  components: { TimelineItem },
  props: { conferenceId: String },
  setup(props) {
    const route = useRoute()
    const friendlyId = route.params.friendlyId
    const word = route.params.word
    const wordDecoded = decodeURIComponent(word)
    const type = route.query.type || 'doubt'
    const typeLabel = type === 'doubt' ? 'Duda' : 'Tema'
    const items = ref([])
    const loading = ref(true)
    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try { items.value = await getWordTimeline(props.conferenceId, wordDecoded, type) }
      catch (e) { } finally { loading.value = false }
    })
    return { friendlyId, word, wordDecoded, type, typeLabel, items, loading }
  }
}
</script>

<style scoped>
.timeline-page { padding: 24px; max-width: 720px; margin: 0 auto; }

.breadcrumbs {
  display: flex; align-items: center; gap: 6px;
  font-size: 0.85rem; color: #6b7280; margin-bottom: 20px; flex-wrap: wrap;
}
.breadcrumbs a { color: #4f46e5; text-decoration: none; }
.breadcrumbs a:hover { text-decoration: underline; }
.sep { color: #d1d5db; }
.crumb-current { color: #374151; font-weight: 600; font-family: monospace; }

.timeline-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
h2 { margin: 0; color: #1e1b4b; font-size: 1.4rem; font-family: monospace; }
.type-badge { padding: 4px 12px; background: #ede9fe; color: #4f46e5; border-radius: 12px; font-size: 0.85rem; font-weight: 600; }
.timeline-empty { text-align: center; color: #9ca3af; padding: 60px; }
.timeline-loading { text-align: center; color: #6b7280; padding: 40px; }
</style>
