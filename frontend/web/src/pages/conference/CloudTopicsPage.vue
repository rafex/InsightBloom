<template lang="pug">
.cloud-page
  .cloud-header
    h2 Temas
    span.count(v-if="words.length") {{ words.length }} palabras

  .submit-box(v-if="canSubmit")
    input.submit-input(v-model="word" type="text" placeholder="Escribe el tema en una palabra o frase corta" maxlength="80" @keyup.enter="submit")
    input.submit-input(v-model="detail" type="text" placeholder="Detalle (opcional)" maxlength="240" @keyup.enter="submit")
    button.btn-submit(type="button" :disabled="!word.trim() || sending" @click="submit")
      span(v-if="sending") Enviando...
      span(v-else) Enviar
  .submit-anon(v-else)
    span ⚠️ #[router-link(:to="{ path: '/login', query: { redirect: $route.fullPath } }") Inicia sesión] para enviar tu tema directamente aquí.
  p.submit-feedback(v-if="feedback" :class="feedbackClass") {{ feedback }}

  .cloud-empty(v-if="!loading && !words.length") No hay temas aún. Sé el primero en enviar uno.
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

<script lang="ts">
import WordCloud from '@/components/cloud/WordCloud.vue'
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { streamTopicCloud, type CloudWord } from '@/services/api/queryApi'
import { sendMessage } from '@/services/api/ingestApi'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'CloudTopicsPage',
  components: { WordCloud },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const words = ref<CloudWord[]>([])
    const loading = ref(true)
    const cloudWidth = ref(800)
    const route = useRoute()
    const router = useRouter()
    const friendlyId = route.params.friendlyId as string
    const auth = useAuthStore()
    const canSubmit = auth.isAuthenticated() && auth.state.role !== 'guest'
    const word = ref('')
    const detail = ref('')
    const sending = ref(false)
    const feedback = ref('')
    const feedbackClass = ref('')
    let eventSource: EventSource | null = null
    function upsertWord(updated: CloudWord) {
      const i = words.value.findIndex((w) => w.wordNormalized === updated.wordNormalized)
      if (updated.visible === false) {
        if (i !== -1) words.value.splice(i, 1)
        return
      }
      if (i === -1) words.value.push(updated)
      else words.value[i] = { ...words.value[i], ...updated }
    }
    function connectStream() {
      if (!props.conferenceId) return
      eventSource = streamTopicCloud(
        props.conferenceId,
        (snapshot) => { words.value = snapshot; loading.value = false },
        (update) => upsertWord(update)
      )
    }
    function onWordClick(word: CloudWord) {
      router.push(`/c/${friendlyId}/words/${encodeURIComponent(word.wordNormalized as string)}?type=topic`)
    }
    async function submit() {
      if (!word.value.trim() || sending.value) return
      sending.value = true; feedback.value = ''
      try {
        await sendMessage({
          conferenceId: props.conferenceId as string,
          authorUuid: auth.state.userUuid as string,
          authorKind: auth.state.role === 'guest' ? 'guest' : 'user',
          type: 'topic',
          word: word.value.trim(),
          detail: detail.value.trim(),
          token: auth.state.token
        })
        word.value = ''; detail.value = ''
        feedback.value = 'Tema enviado.'
        feedbackClass.value = 'ok'
      } catch (e: any) {
        feedback.value = 'No se pudo enviar. Intenta de nuevo.'
        feedbackClass.value = 'error'
      } finally {
        sending.value = false
      }
    }
    function resize() { cloudWidth.value = Math.min(window.innerWidth - 32, 1000) }
    onMounted(() => {
      resize(); window.addEventListener('resize', resize)
      connectStream()
    })
    onUnmounted(() => { if (eventSource) eventSource.close(); window.removeEventListener('resize', resize) })
    return {
      words, loading, cloudWidth, onWordClick, canSubmit, word, detail, sending,
      feedback, feedbackClass, submit
    }
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

.submit-box { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.submit-input {
  flex: 1; min-width: 180px; padding: 10px 14px; border: 1.5px solid #d1d5db;
  border-radius: 8px; font-size: 0.95rem;
}
.submit-input:focus { outline: none; border-color: #0891b2; }
.btn-submit {
  padding: 10px 22px; background: #0891b2; color: #fff; border: none; border-radius: 8px;
  font-weight: 600; cursor: pointer; font-size: 0.95rem;
}
.btn-submit:disabled { opacity: 0.5; cursor: not-allowed; }
.submit-anon {
  margin-bottom: 12px; padding: 10px 16px; background: #fef3c7; color: #92400e;
  border: 1px solid #fde68a; border-radius: 8px; font-size: 0.85rem;
}
.submit-anon :deep(a) { color: #0891b2; font-weight: 600; text-decoration: none; }
.submit-feedback { margin: 0 0 12px; font-size: 0.85rem; }
.submit-feedback.ok { color: #166534; }
.submit-feedback.error { color: #dc2626; }

@media (max-width: 640px) {
  .cloud-page { padding: 14px; }
  .submit-box { flex-direction: column; }
}
</style>
