<template lang="pug">
.ai-email-assistant(v-if="visible")
  .ai-panel
    .ai-header
      span ✨ Asistente IA de correos
      button.ai-close(type="button" @click="$emit('close')") ✕
    p.ai-hint Describí qué querés comunicar a los inscritos. El asistente te generará un borrador.
    textarea(v-model="prompt" rows="3" placeholder="Ej: Recordar que el evento cambió a la sala B y que traigan laptop...")
    .ai-actions
      BaseButton(variant="primary" type="button" :loading="generating" :disabled="!prompt.trim()" @click="generate") {{ generating ? 'Generando...' : 'Generar borrador' }}
    FeedbackMessage(v-if="feedback" :message="feedback" :tone="feedbackTone")
    .ai-result(v-if="draft")
      .result-header Borrador generado:
      .result-body(v-html="renderedDraft")
      BaseButton(variant="secondary" type="button" @click="useDraft") Usar este borrador
</template>

<script lang="ts">
import { ref, computed } from 'vue'
import { Marked } from 'marked'
import { generateEmailDraft } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'

const previewMarked = new Marked()

export default {
  name: 'AiEmailAssistant',
  components: { BaseButton, FeedbackMessage },
  props: {
    conferenceId: { type: String, required: true },
    visible: { type: Boolean, default: false }
  },
  emits: ['close', 'useDraft'],
  setup(props: { conferenceId: string, visible: boolean }, context: any) {
    const auth = useAuthStore()
    const prompt = ref('')
    const generating = ref(false)
    const draft = ref('')
    const feedback = ref('')
    const feedbackTone = ref<'success' | 'error'>('error')

    const renderedDraft = computed(() => {
      if (!draft.value) return ''
      return previewMarked.parse(draft.value, { async: false }) as string
    })

    async function generate() {
      if (!prompt.value.trim()) return
      generating.value = true
      feedback.value = ''
      draft.value = ''
      try {
        const result = await generateEmailDraft(props.conferenceId, prompt.value.trim(), auth.state.token as string)
        draft.value = result.draft
        feedback.value = 'Borrador generado.'
        feedbackTone.value = 'success'
      } catch (e: any) {
        const code = e.response?.data?.error?.code || e.response?.data?.error?.message || ''
        feedbackTone.value = 'error'
        if (code === 'email_ai_not_configured') {
          feedback.value = 'El asistente IA de correos no está configurado. Pedile al administrador que lo active en Dashboard → IA → Correos a inscritos.'
        } else {
          feedback.value = e.response?.data?.error?.detail || 'No se pudo generar el borrador. Reintentá.'
        }
      } finally {
        generating.value = false
      }
    }

    function useDraft() {
      if (draft.value) {
        context.emit('useDraft', draft.value)
        draft.value = ''
        prompt.value = ''
        feedback.value = ''
      }
    }

    return { prompt, generating, draft, feedback, feedbackTone, renderedDraft, generate, useDraft }
  }
}
</script>

<style scoped>
.ai-email-assistant {
  margin-bottom: 12px;
}
.ai-panel {
  border: 1px solid var(--color-primary-border); border-radius: 10px;
  background: var(--color-primary-soft); padding: 14px;
}
.ai-header {
  display: flex; justify-content: space-between; align-items: center;
  font-weight: 700; color: var(--color-primary-dark); margin-bottom: 8px;
}
.ai-close {
  background: none; border: none; cursor: pointer; font-size: 1rem;
  color: var(--color-text-muted);
}
.ai-hint {
  font-size: .82rem; color: var(--color-text-muted); margin: 0 0 10px;
}
.ai-panel textarea {
  width: 100%; box-sizing: border-box; padding: 8px; border: 1px solid var(--color-border);
  border-radius: 7px; font: inherit; resize: vertical;
}
.ai-actions {
  margin-top: 10px; display: flex; gap: 10px; align-items: center;
}
.ai-result {
  margin-top: 12px; border-top: 1px solid var(--color-border); padding-top: 10px;
}
.result-header {
  font-size: .78rem; font-weight: 600; color: var(--color-text-muted);
  margin-bottom: 6px;
}
.result-body {
  background: #fff; border-radius: 8px; padding: 12px; font-size: .88rem;
  line-height: 1.6; color: #1f2937; max-height: 160px; overflow-y: auto;
}
.result-body :deep(h3) { margin: 0 0 4px; font-size: 1rem; }
.result-body :deep(p) { margin: 0 0 6px; }
.result-body :deep(ul), .result-body :deep(ol) { margin: 0 0 6px; padding-left: 18px; }
.result-body :deep(strong) { font-weight: 700; }
.result-body :deep(a) { color: var(--color-primary); }
</style>
