<template lang="pug">
.timeline-page
  .timeline-header
    button.back-btn(@click="$router.back()") &larr; Volver
    h2 "{{ wordDecoded }}"
    span.type-badge {{ typeLabel }}
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
    return { word, wordDecoded, typeLabel, items, loading }
  }
}
</script>

<style scoped>
.timeline-page { padding: 24px; max-width: 720px; margin: 0 auto; }
.timeline-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
.back-btn { background: none; border: 1.5px solid #d1d5db; border-radius: 6px; padding: 6px 14px; cursor: pointer; color: #374151; }
h2 { margin: 0; color: #1e1b4b; font-size: 1.4rem; }
.type-badge { padding: 4px 12px; background: #ede9fe; color: #4f46e5; border-radius: 12px; font-size: 0.85rem; font-weight: 600; }
.timeline-empty { text-align: center; color: #9ca3af; padding: 60px; }
.timeline-loading { text-align: center; color: #6b7280; padding: 40px; }
</style>
