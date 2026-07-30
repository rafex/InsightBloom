<template lang="pug">
.cloud-page
  .cloud-header
    h2 Temas
    span.count(v-if="words.length") {{ words.length }} palabras

  .submit-box(v-if="canSubmit")
    input.submit-input(v-model="word" type="text" aria-label="Tema" placeholder="Escribe el tema en una palabra o frase corta" maxlength="80" @keyup.enter="submit")
    input.submit-input(v-model="detail" type="text" aria-label="Detalle del tema" placeholder="Detalle (opcional)" maxlength="240" @keyup.enter="submit")
    BaseButton(type="button" :disabled="!word.trim()" :loading="sending" @click="submit") Enviar
  .submit-anon(v-else)
    span ⚠️ #[router-link(:to="{ path: '/login', query: { redirect: $route.fullPath } }") Inicia sesión] para enviar tu tema directamente aquí.
  FeedbackMessage(v-if="feedback" :message="feedback" :tone="feedbackTone")

  LoadingState(v-if="loading" message="Cargando temas…")
  EmptyState(v-else-if="!words.length" message="No hay temas aún. Sé el primero en enviar uno.")
  WordCloud(
    v-else
    :words="words"
    :width="cloudWidth"
    :height="500"
    color="var(--color-info)"
    @word-click="onWordClick"
  )
</template>

<script lang="ts">
import WordCloud from '@/components/cloud/WordCloud.vue'
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { streamTopicCloud, type CloudWord } from '@/services/api/queryApi'
import { sendMessage } from '@/services/api/ingestApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
export default {
  name: 'CloudTopicsPage',
  components: { BaseButton, EmptyState, FeedbackMessage, LoadingState, WordCloud },
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
    const feedbackTone = ref<'success' | 'error'>('success')
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
        feedbackTone.value = 'success'
      } catch (e: any) {
        feedback.value = 'No se pudo enviar. Intenta de nuevo.'
        feedbackTone.value = 'error'
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
      feedback, feedbackTone, submit
    }
  }
}
</script>

<style scoped>
.cloud-page { padding: 24px; }
.cloud-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
h2 { margin: 0; color: var(--color-heading); }
.count { color: var(--color-text-muted); font-size: 0.9rem; }

.submit-box { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.submit-input {
  flex: 1; min-width: 180px; padding: 10px 14px; border: 1.5px solid var(--color-border);
  border-radius: 8px; font-size: 0.95rem;
}
.submit-input:focus { outline: none; border-color: var(--color-info); }
.submit-anon {
  margin-bottom: 12px; padding: 10px 16px; background: var(--color-warning-soft); color: var(--color-warning);
  border: 1px solid var(--color-warning); border-radius: 8px; font-size: 0.85rem;
}
.submit-anon :deep(a) { color: var(--color-info); font-weight: 600; text-decoration: none; }
@media (max-width: 640px) {
  .cloud-page { padding: 14px; }
  .submit-box { flex-direction: column; }
}
</style>
