<template lang="pug">
.cloud-page
  .cloud-header
    h2 Temas
    span.count(v-if="words.length") {{ words.length }} palabras
  .cloud-empty(v-if="!loading && !words.length") No hay temas aún. Los participantes pueden enviar mensajes con /tema
  WordCloud(
    v-if="words.length"
    :words="words"
    :width="cloudWidth"
    :height="500"
    color="#0891b2"
    @word-click="onWordClick"
  )
  .cloud-loading(v-if="loading") Cargando temas...
</template>

<script>
import WordCloud from '@/components/cloud/WordCloud.vue'
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTopicCloud } from '@/services/api/queryApi'
export default {
  name: 'CloudTopicsPage',
  components: { WordCloud },
  props: { conferenceId: String },
  setup(props) {
    const words = ref([])
    const loading = ref(true)
    const cloudWidth = ref(800)
    const route = useRoute()
    const router = useRouter()
    const friendlyId = route.params.friendlyId
    let interval = null
    async function load() {
      if (!props.conferenceId) return
      try { words.value = await getTopicCloud(props.conferenceId) }
      catch (e) { } finally { loading.value = false }
    }
    function onWordClick(word) {
      router.push(`/c/${friendlyId}/words/${encodeURIComponent(word.wordNormalized)}?type=topic`)
    }
    function resize() { cloudWidth.value = Math.min(window.innerWidth - 48, 1000) }
    onMounted(() => {
      resize(); window.addEventListener('resize', resize)
      load(); interval = setInterval(load, 5000)
    })
    onUnmounted(() => { clearInterval(interval); window.removeEventListener('resize', resize) })
    return { words, loading, cloudWidth, onWordClick }
  }
}
</script>

<style scoped>
.cloud-page { padding: 24px; }
.cloud-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
h2 { margin: 0; color: #164e63; }
.count { color: #6b7280; font-size: 0.9rem; }
.cloud-empty { text-align: center; color: #9ca3af; padding: 60px; font-size: 1.1rem; }
.cloud-loading { text-align: center; color: #6b7280; padding: 40px; }
</style>
