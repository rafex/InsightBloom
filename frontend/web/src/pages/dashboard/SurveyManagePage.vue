<template lang="pug">
.survey-manage-page
  h2 Encuesta de la conferencia

  .add-card
    h3 Agregar pregunta
    input(v-model="newQuestion.text" placeholder="¿Qué tan útil fue la charla?")
    select(v-model="newQuestion.type")
      option(value="RATING") Calificación (1-5 estrellas)
      option(value="TEXT") Texto libre
      option(value="MULTIPLE_CHOICE") Opción múltiple
    input(
      v-if="newQuestion.type === 'MULTIPLE_CHOICE'"
      v-model="newQuestion.optionsRaw"
      placeholder="Opciones separadas por coma: Sí, No, Tal vez"
    )
    button.btn-primary(:disabled="!newQuestion.text || creating" @click="addQuestion") Agregar

  .questions-card(v-if="questions.length")
    h3 Preguntas activas
    .question-row(v-for="q in questions" :key="q.uuid")
      span.q-text {{ q.text }}
      span.q-type {{ typeLabel(q.type) }}
      button.btn-sm.btn-warning(@click="deactivate(q)") Desactivar

  .results-card(v-if="results.length")
    h3 Resultados
    .result-row(v-for="r in results" :key="r.questionUuid")
      p.result-question {{ r.text }} ({{ r.responseCount }} respuestas)
      p(v-if="r.averageRating != null") Promedio: {{ r.averageRating.toFixed(1) }} ★
      ul(v-else-if="Object.keys(r.counts || {}).length")
        li(v-for="(count, option) in r.counts" :key="option") {{ option }}: {{ count }}
      ul(v-else-if="r.texts && r.texts.length")
        li(v-for="(t, i) in r.texts" :key="i") "{{ t }}"
</template>

<script>
import { ref, onMounted } from 'vue'
import { getQuestions, createQuestion, deactivateQuestion, getResults } from '@/services/api/surveyApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'SurveyManagePage',
  props: { conferenceId: String },
  setup(props) {
    const auth = useAuthStore()
    const questions = ref([])
    const results = ref([])
    const creating = ref(false)
    const newQuestion = ref({ text: '', type: 'RATING', optionsRaw: '' })

    function typeLabel(t) {
      return { RATING: 'Calificación', TEXT: 'Texto libre', MULTIPLE_CHOICE: 'Opción múltiple' }[t] || t
    }

    async function load() {
      if (!props.conferenceId) return
      try {
        const qRes = await getQuestions(props.conferenceId, false)
        questions.value = (qRes.data || []).filter((q) => q.active)
      } catch (e) { questions.value = [] }
      try {
        const rRes = await getResults(props.conferenceId, auth.state.token)
        results.value = rRes.data || []
      } catch (e) { results.value = [] }
    }

    async function addQuestion() {
      creating.value = true
      try {
        const options = newQuestion.value.type === 'MULTIPLE_CHOICE'
          ? newQuestion.value.optionsRaw.split(',').map((s) => s.trim()).filter(Boolean)
          : null
        await createQuestion(props.conferenceId, {
          text: newQuestion.value.text,
          type: newQuestion.value.type,
          options,
          orderIndex: questions.value.length
        }, auth.state.token)
        newQuestion.value = { text: '', type: 'RATING', optionsRaw: '' }
        await load()
      } finally {
        creating.value = false
      }
    }

    async function deactivate(q) {
      await deactivateQuestion(props.conferenceId, q.uuid, auth.state.token)
      await load()
    }

    onMounted(load)

    return { questions, results, creating, newQuestion, typeLabel, addQuestion, deactivate }
  }
}
</script>

<style scoped>
.survey-manage-page { padding: 24px; max-width: 640px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.add-card, .questions-card, .results-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
h3 { margin: 0 0 12px; color: #1e1b4b; }
input, select {
  display: block; width: 100%; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px;
  margin-bottom: 10px; font-size: 0.9rem;
}
.btn-primary {
  padding: 8px 18px; border: none; border-radius: 8px; background: #4f46e5; color: #fff;
  font-weight: 600; cursor: pointer;
}
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.question-row {
  display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f3f4f6;
}
.q-text { flex: 1; }
.q-type { color: #6b7280; font-size: 0.82rem; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-warning { background: #fef3c7; color: #d97706; }
.btn-warning:hover { background: #fde68a; }
.result-row { margin-bottom: 16px; }
.result-question { font-weight: 600; color: #1e1b4b; margin-bottom: 4px; }
ul { margin: 4px 0 0 16px; padding: 0; color: #374151; }
</style>
