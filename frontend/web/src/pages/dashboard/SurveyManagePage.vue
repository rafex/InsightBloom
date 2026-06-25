<template lang="pug">
.survey-manage-page
  h2 Encuesta de la conferencia

  .add-card
    h3 {{ editingId ? 'Editar pregunta' : 'Agregar pregunta' }}
    .ai-suggest-row(v-if="!editingId")
      button.btn-outline(type="button" :disabled="suggesting" @click="suggest") {{ suggesting ? 'Pensando...' : '✨ Sugerir preguntas con IA' }}
      span.ai-error(v-if="suggestError") {{ suggestError }}

    .suggestions(v-if="suggestions.length && !editingId")
      h4 Sugerencias (revisa y agrega las que quieras)
      .suggestion-row(v-for="(s, i) in suggestions" :key="i")
        .suggestion-text
          strong {{ s.text }}
          span.suggestion-type {{ typeLabel(s.type) }}
          div(v-if="s.options && s.options.length") Opciones: {{ s.options.join(', ') }}
          div(v-if="s.referenceAnswer") Referencia: {{ s.referenceAnswer }}
        button.btn-sm.btn-primary-sm(type="button" @click="addSuggestion(s)") Agregar

    input(v-model="form.text" placeholder="¿Qué tan útil fue la charla?")
    select(v-model="form.type")
      option(value="RATING") Calificación (estrellas o emojis)
      option(value="TEXT") Texto libre
      option(value="MULTIPLE_CHOICE") Opción múltiple
      option(value="OPEN_GRADED") Abierta (calificada por IA)
      option(value="CODE_GRADED") Código (calificado por IA)
      option(value="CANVAS_DRAWING") Diagrama / dibujo
      option(value="DRAG_DROP") Ordenar elementos (drag and drop)

    select(v-if="form.type === 'RATING'" v-model="form.ratingStyle")
      option(value="STARS") ★ Estrellas
      option(value="EMOJIS") 🙂 Emojis (satisfacción)

    input(
      v-if="form.type === 'MULTIPLE_CHOICE'"
      v-model="form.optionsRaw"
      placeholder="Opciones separadas por coma: Sí, No, Tal vez"
    )
    input(
      v-if="form.type === 'DRAG_DROP'"
      v-model="form.optionsRaw"
      placeholder="Elementos en el ORDEN CORRECTO, separados por coma"
    )
    textarea(
      v-if="form.type === 'OPEN_GRADED'"
      v-model="form.referenceAnswer"
      rows="2"
      placeholder="Respuesta de referencia esperada (la IA comparará contra esto)"
    )
    textarea(
      v-if="form.type === 'CODE_GRADED'"
      v-model="form.referenceAnswer"
      rows="3"
      placeholder="Criterios o solución esperada (la IA comparará el código contra esto)"
    )
    .form-actions
      button.btn-primary(:disabled="!form.text || saving" @click="save") {{ saving ? 'Guardando...' : (editingId ? 'Guardar cambios' : 'Agregar') }}
      button.btn-ghost-sm(v-if="editingId" type="button" @click="cancelEdit") Cancelar

  .questions-card(v-if="questions.length")
    h3 Preguntas activas
    .question-row(v-for="q in questions" :key="q.uuid")
      span.q-text {{ q.text }}
      span.q-type {{ typeLabel(q.type) }}
      button.btn-sm.btn-edit(@click="startEdit(q)") Editar
      button.btn-sm.btn-warning(@click="deactivate(q)") Desactivar

  .results-card(v-if="results.length")
    h3 Resultados
    .result-row(v-for="r in results" :key="r.questionUuid")
      p.result-question
        | {{ r.text }} ({{ r.responseCount }} respuestas)
        span.avg-grade(v-if="r.averageGradeScore != null") · Promedio IA: {{ r.averageGradeScore.toFixed(0) }}/100
      p(v-if="r.averageRating != null") Promedio: {{ r.averageRating.toFixed(1) }} ★
      ul(v-else-if="Object.keys(r.counts || {}).length")
        li(v-for="(count, option) in r.counts" :key="option") {{ option }}: {{ count }}
      .graded-answers(v-else-if="r.gradedAnswers && r.gradedAnswers.length")
        .graded-answer(v-for="(a, i) in r.gradedAnswers" :key="i")
          .answer-image(v-if="isImage(a.answerText)")
            img(:src="a.answerText")
          pre.answer-text(v-else) {{ a.answerText }}
          .answer-grade(v-if="a.gradeScore != null")
            strong {{ a.gradeScore.toFixed(0) }}/100
            span – {{ a.gradeFeedback }}
      ul(v-else-if="r.texts && r.texts.length")
        li(v-for="(t, i) in r.texts" :key="i")
          template(v-if="isImage(t)")
            .answer-image
              img(:src="t")
          template(v-else) "{{ t }}"
</template>

<script>
import { ref, onMounted } from 'vue'
import { getQuestions, createQuestion, updateQuestion, deactivateQuestion, getResults, suggestQuestions } from '@/services/api/surveyApi'
import { useAuthStore } from '@/features/auth/authStore'

function emptyForm() {
  return { text: '', type: 'RATING', ratingStyle: 'STARS', optionsRaw: '', referenceAnswer: '' }
}

export default {
  name: 'SurveyManagePage',
  props: { conferenceId: String },
  setup(props) {
    const auth = useAuthStore()
    const questions = ref([])
    const results = ref([])
    const saving = ref(false)
    const suggesting = ref(false)
    const suggestError = ref('')
    const suggestions = ref([])
    const editingId = ref(null)
    const form = ref(emptyForm())

    function typeLabel(t) {
      return {
        RATING: 'Calificación', TEXT: 'Texto libre', MULTIPLE_CHOICE: 'Opción múltiple',
        OPEN_GRADED: 'Abierta (IA)', CODE_GRADED: 'Código (IA)',
        CANVAS_DRAWING: 'Diagrama/dibujo', DRAG_DROP: 'Ordenar (drag and drop)'
      }[t] || t
    }

    function isImage(text) {
      return typeof text === 'string' && text.startsWith('data:image')
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

    async function suggest() {
      suggestError.value = ''
      suggesting.value = true
      try {
        const res = await suggestQuestions(props.conferenceId, 5, auth.state.token)
        suggestions.value = res.data || []
      } catch (e) {
        suggestError.value = 'No se pudieron generar sugerencias (¿hay una presentación subida?)'
      } finally {
        suggesting.value = false
      }
    }

    function addSuggestion(s) {
      form.value = {
        text: s.text,
        type: s.type,
        ratingStyle: 'STARS',
        optionsRaw: (s.options || []).join(', '),
        referenceAnswer: s.referenceAnswer || ''
      }
    }

    function startEdit(q) {
      editingId.value = q.uuid
      form.value = {
        text: q.text,
        type: q.type,
        ratingStyle: q.ratingStyle || 'STARS',
        optionsRaw: (q.options || []).join(', '),
        referenceAnswer: q.referenceAnswer || ''
      }
      suggestions.value = []
    }

    function cancelEdit() {
      editingId.value = null
      form.value = emptyForm()
    }

    async function save() {
      saving.value = true
      try {
        const isOptionsType = form.value.type === 'MULTIPLE_CHOICE' || form.value.type === 'DRAG_DROP'
        const options = isOptionsType
          ? form.value.optionsRaw.split(',').map((s) => s.trim()).filter(Boolean)
          : null
        const payload = {
          text: form.value.text,
          type: form.value.type,
          options,
          referenceAnswer: form.value.referenceAnswer || null,
          ratingStyle: form.value.type === 'RATING' ? form.value.ratingStyle : null,
          orderIndex: questions.value.length
        }
        if (editingId.value) {
          await updateQuestion(props.conferenceId, editingId.value, payload, auth.state.token)
        } else {
          await createQuestion(props.conferenceId, payload, auth.state.token)
        }
        editingId.value = null
        form.value = emptyForm()
        suggestions.value = []
        await load()
      } finally {
        saving.value = false
      }
    }

    async function deactivate(q) {
      await deactivateQuestion(props.conferenceId, q.uuid, auth.state.token)
      await load()
    }

    onMounted(load)

    return {
      questions, results, saving, suggesting, suggestError, suggestions, form, editingId,
      typeLabel, isImage, save, deactivate, suggest, addSuggestion, startEdit, cancelEdit
    }
  }
}
</script>

<style scoped>
.survey-manage-page { padding: 24px; max-width: 720px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.add-card, .questions-card, .results-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
h3 { margin: 0 0 12px; color: #1e1b4b; }
h4 { margin: 0 0 8px; color: #374151; font-size: 0.9rem; }
input, select, textarea {
  display: block; width: 100%; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px;
  margin-bottom: 10px; font-size: 0.9rem; font-family: inherit;
}
.form-actions { display: flex; gap: 10px; align-items: center; }
.btn-primary {
  padding: 8px 18px; border: none; border-radius: 8px; background: #4f46e5; color: #fff;
  font-weight: 600; cursor: pointer;
}
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost-sm {
  padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #6b7280;
  cursor: pointer; font-size: 0.85rem;
}
.btn-outline {
  padding: 8px 16px; border: 1px solid #4f46e5; border-radius: 8px; background: #fff; color: #4f46e5;
  font-weight: 600; cursor: pointer; font-size: 0.85rem;
}
.btn-outline:disabled { opacity: 0.6; cursor: not-allowed; }
.ai-suggest-row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.ai-error { color: #dc2626; font-size: 0.82rem; }
.suggestions { background: #f5f3ff; border-radius: 10px; padding: 12px; margin-bottom: 16px; }
.suggestion-row {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 10px;
  padding: 8px 0; border-bottom: 1px solid #e9e5ff;
}
.suggestion-row:last-child { border-bottom: none; }
.suggestion-text { font-size: 0.85rem; color: #374151; }
.suggestion-type { margin-left: 8px; color: #6b7280; font-size: 0.75rem; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-primary-sm { background: #4f46e5; color: #fff; flex-shrink: 0; }
.question-row {
  display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f3f4f6;
}
.q-text { flex: 1; }
.q-type { color: #6b7280; font-size: 0.82rem; }
.btn-edit { background: #e0e7ff; color: #4338ca; }
.btn-edit:hover { background: #c7d2fe; }
.btn-warning { background: #fef3c7; color: #d97706; }
.btn-warning:hover { background: #fde68a; }
.result-row { margin-bottom: 16px; }
.result-question { font-weight: 600; color: #1e1b4b; margin-bottom: 4px; }
.avg-grade { color: #059669; font-weight: 600; }
ul { margin: 4px 0 0 16px; padding: 0; color: #374151; }
.graded-answers { display: flex; flex-direction: column; gap: 10px; }
.graded-answer { background: #f9fafb; border-radius: 8px; padding: 10px 12px; }
.answer-text { white-space: pre-wrap; font-family: 'SF Mono', Consolas, monospace; font-size: 0.82rem; margin: 0 0 6px; }
.answer-grade { font-size: 0.85rem; color: #059669; }
.answer-image img { max-width: 100%; border-radius: 6px; border: 1px solid #e5e7eb; }
</style>
