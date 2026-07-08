<template lang="pug">
.survey-manage-page
  ConferenceSubNav(:conferenceId="conferenceId")
  h2 Encuesta de la conferencia

  nav.tabs
    button.tab-btn(type="button" :class="{ active: activeTab === 'create' }" @click="activeTab = 'create'") {{ editingId ? '✏️ Editando pregunta' : '➕ Alta de preguntas' }}
    button.tab-btn(type="button" :class="{ active: activeTab === 'edit' }" @click="activeTab = 'edit'") 📋 Edición de preguntas
    button.tab-btn(type="button" :class="{ active: activeTab === 'results' }" @click="activeTab = 'results'") 📊 Resultados

  .add-card(v-show="activeTab === 'create'")
    h3 {{ editingId ? 'Editar pregunta' : 'Agregar pregunta' }}
    .ai-suggest-row(v-if="!editingId")
      button.btn-outline(type="button" :disabled="suggesting" @click="suggest") {{ suggesting ? 'Pensando...' : '✨ Sugerir preguntas con IA' }}
      span.ai-error(v-if="suggestError") {{ suggestError }}

    .suggestions(v-if="suggestions.length && !editingId")
      h4 Sugerencias (selecciona las que quieras agregar)
      .suggestion-row(v-for="(s, i) in suggestions" :key="i")
        label.suggestion-check
          input(type="checkbox" :value="i" v-model="selectedSuggestions")
        .suggestion-text
          strong {{ s.text }}
          span.suggestion-type {{ typeLabel(s.type) }}
          div(v-if="s.options && s.options.length") Opciones: {{ s.options.join(', ') }}
          div(v-if="s.referenceAnswer") Referencia: {{ s.referenceAnswer }}
      .suggestions-actions
        button.btn-sm.btn-primary-sm(type="button" :disabled="!selectedSuggestions.length || addingSuggestions" @click="addSelectedSuggestions")
          | {{ addingSuggestions ? 'Agregando...' : `Agregar seleccionadas (${selectedSuggestions.length})` }}
        button.btn-sm.btn-ghost-sm(type="button" @click="selectedSuggestions = suggestions.map((_, i) => i)") Seleccionar todas

    .text-row
      input(v-model="form.text" placeholder="¿Qué tan útil fue la charla?")
      button.btn-wand(type="button" :disabled="!form.text || improving" @click="improve" title="Mejorar con IA") {{ improving ? '✨...' : '🪄' }}
    span.ai-error(v-if="improveError") {{ improveError }}

    .suggestions.improve-suggestions(v-if="improvements.length")
      h4 Alternativas mejoradas por IA
      .suggestion-row(v-for="(s, i) in improvements" :key="i")
        .suggestion-text
          strong {{ s.text }}
          span.suggestion-type {{ typeLabel(s.type) }}
          div(v-if="s.options && s.options.length") Opciones: {{ s.options.join(', ') }}
          div(v-if="s.referenceAnswer") Referencia: {{ s.referenceAnswer }}
        button.btn-sm.btn-primary-sm(type="button" @click="applyImprovement(s)") Usar esta

    select(v-model="form.type" @change="onTypeChange")
      option(value="RATING") ★ Calificación (estrellas o emojis)
      option(value="TEXT") 📝 Texto libre
      option(value="MULTIPLE_CHOICE") ☑️ Opción múltiple
      option(value="OPEN_GRADED") 💬 Abierta (calificación IA bajo demanda)
      option(value="CODE_GRADED") 💻 Código (calificación IA bajo demanda)
      option(value="CANVAS_DRAWING") 🎨 Diagrama / dibujo
      option(value="DRAG_DROP") ⇕ Ordenar elementos (drag and drop)

    .style-row(v-if="form.type === 'RATING'")
      label.style-option(:class="{ active: form.ratingStyle === 'STARS' }")
        input(type="radio" value="STARS" v-model="form.ratingStyle")
        span ★★★★★ Estrellas
      label.style-option(:class="{ active: form.ratingStyle === 'EMOJIS' }")
        input(type="radio" value="EMOJIS" v-model="form.ratingStyle")
        span 😢😕😐🙂🤩 Emojis

    .options-editor(v-if="form.type === 'MULTIPLE_CHOICE'")
      label.options-label Opciones que verá el asistente · marca cuáles son correctas
      .option-row(v-for="(opt, idx) in form.options" :key="idx")
        label.option-correct(:title="'Marcar como correcta'")
          input(type="checkbox" v-model="form.optionsCorrect[idx]")
        input(v-model="form.options[idx]" :placeholder="'Opción ' + (idx + 1)")
        button.btn-icon(type="button" @click="removeOption(idx)" :disabled="form.options.length <= 2" title="Quitar") ✕
      button.btn-add(type="button" @click="addOption") + Agregar opción
      p.options-hint(v-if="!form.optionsCorrect.some(Boolean)") ⚠️ Marca al menos una opción correcta para poder calificar esta pregunta automáticamente.

    .options-editor(v-if="form.type === 'DRAG_DROP'")
      label.options-label Elementos en el ORDEN CORRECTO (el asistente los verá desordenados)
      .option-row(v-for="(opt, idx) in form.options" :key="idx")
        span.option-order {{ idx + 1 }}
        input(v-model="form.options[idx]" :placeholder="'Elemento ' + (idx + 1)")
        .option-arrows
          button.btn-icon(type="button" @click="moveOption(idx, -1)" :disabled="idx === 0" title="Subir") ↑
          button.btn-icon(type="button" @click="moveOption(idx, 1)" :disabled="idx === form.options.length - 1" title="Bajar") ↓
        button.btn-icon(type="button" @click="removeOption(idx)" :disabled="form.options.length <= 2" title="Quitar") ✕
      button.btn-add(type="button" @click="addOption") + Agregar elemento

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
    label.required-check
      input(type="checkbox" v-model="form.required")
      span Obligatoria (el asistente debe responderla para poder enviar la encuesta)
    .form-actions
      button.btn-primary(:disabled="!form.text || saving" @click="save") {{ saving ? 'Guardando...' : (editingId ? 'Guardar cambios' : 'Agregar') }}
      button.btn-ghost-sm(v-if="editingId" type="button" @click="cancelEdit") Cancelar

  .questions-card(v-if="questions.length" v-show="activeTab === 'edit'")
    h3 Preguntas activas
    .question-item(v-for="q in questions" :key="q.uuid" :class="{ editing: editingId === q.uuid }")
      .question-item-header
        span.q-icon {{ typeIcon(q.type) }}
        span.q-text {{ q.text }}
          span.q-required(v-if="q.required" title="Obligatoria") &nbsp;*
        span.q-type {{ typeLabel(q.type) }}
      .q-preview(v-if="q.type === 'MULTIPLE_CHOICE' && q.options && q.options.length")
        span.chip(v-for="opt in q.options" :key="opt" :class="{ 'chip-ref': parseMultiSelect(q.referenceAnswer).includes(opt) }")
          | {{ parseMultiSelect(q.referenceAnswer).includes(opt) ? '✅' : '☐' }} {{ opt }}
      .q-preview(v-else-if="q.type === 'DRAG_DROP' && q.options && q.options.length")
        span.chip.chip-ordered(v-for="(opt, idx) in q.options" :key="opt") {{ idx + 1 }}. {{ opt }}
      .q-preview(v-else-if="q.type === 'RATING'")
        span.chip {{ q.ratingStyle === 'EMOJIS' ? '😢😕😐🙂🤩' : '★★★★★' }}
      .q-preview(v-else-if="(q.type === 'OPEN_GRADED' || q.type === 'CODE_GRADED') && q.referenceAnswer")
        span.chip.chip-ref 📌 Referencia: {{ q.referenceAnswer }}
      .question-item-actions
        button.btn-sm.btn-edit(@click="startEdit(q)") Editar
        button.btn-sm.btn-delete(@click="confirmDelete(q)") 🗑 Eliminar

  .confirm-overlay(v-if="deleteTarget" @click.self="deleteTarget = null")
    .confirm-dialog
      h4 ¿Eliminar pregunta?
      p Esto quitará <strong>"{{ deleteTarget.text }}"</strong> de la encuesta de forma permanente. Las respuestas ya recibidas se conservan en los resultados.
      .confirm-actions
        button.btn-cancel(@click="deleteTarget = null") Cancelar
        button.btn-confirm(@click="doDelete") Eliminar

  .confirm-overlay(v-if="purgeTarget" @click.self="purgeTarget = null")
    .confirm-dialog
      h4 ¿Purgar respuestas?
      p Esto eliminará permanentemente las <strong>{{ purgeTarget.responseCount }} respuestas</strong> recibidas para "{{ purgeTarget.text }}". La pregunta se conserva, solo se borran las respuestas.
      .confirm-actions
        button.btn-cancel(@click="purgeTarget = null") Cancelar
        button.btn-confirm(@click="doPurge") Purgar

  .results-card(v-if="results.length" v-show="activeTab === 'results'")
    h3 Resultados

    .grading-toolbar(v-if="gradeableQuestions.length")
      button.btn-outline(type="button" @click="reviewOpen = !reviewOpen") 🤖 Revisar respuestas
      span.grading-status(v-if="gradeStatus") {{ gradeStatus }}

    .grading-panel(v-if="reviewOpen")
      label.grading-select-all
        input(type="checkbox" :checked="allGradeableSelected" @change="toggleSelectAllGradeable")
        span Seleccionar todas
      label.grading-question(v-for="q in gradeableQuestions" :key="q.questionUuid")
        input(type="checkbox" :value="q.questionUuid" v-model="selectedForGrading")
        span {{ q.text }} ({{ q.responseCount }} respuestas)
      .grading-panel-actions
        label.grading-regrade
          input(type="checkbox" v-model="regradeAll")
          span Volver a calificar las que ya tienen puntaje
        button.btn-primary-sm(type="button" :disabled="!selectedForGrading.length || grading" @click="runGrading")
          | {{ grading ? 'Calificando...' : `Revisar seleccionadas (${selectedForGrading.length})` }}

    .result-row(v-for="r in results" :key="r.questionUuid")
      p.result-question
        | {{ r.text }} ({{ r.responseCount }} respuestas)
        span.avg-grade(v-if="r.averageGradeScore != null") · Promedio IA: {{ r.averageGradeScore.toFixed(0) }}/100

      .summary(v-if="r.responseCount")
        p.summary-line(v-if="r.averageRating != null") Promedio: {{ r.averageRating.toFixed(1) }} {{ r.ratingStyle === 'EMOJIS' ? '🙂' : '★' }}
        ul.summary-counts(v-if="Object.keys(r.counts || {}).length")
          li(v-for="(count, option) in r.counts" :key="option") {{ option }}: {{ count }}
        .chart-wrap(v-if="r.averageRating != null")
          BarChart(:data="ratingChartData(r)")
        .chart-wrap(v-else-if="Object.keys(r.counts || {}).length")
          BarChart(:data="choiceChartData(r)")

      .result-actions(v-if="r.responseCount")
        button.btn-toggle(type="button" @click="toggleDetail(r.questionUuid)")
          | {{ openDetail[r.questionUuid] ? '▾ Ocultar respuestas individuales' : '▸ Ver respuestas individuales (' + r.responseCount + ')' }}
        button.btn-purge(type="button" @click="confirmPurge(r)") 🗑 Purgar respuestas

      .individual-answers(v-if="openDetail[r.questionUuid]")
        .individual-answer(v-for="(a, i) in r.individualAnswers" :key="i")
          .answer-author(v-if="a.respondentName || a.respondentUuid") 👤 {{ a.respondentName || a.respondentUuid }}
          .answer-rating(v-if="a.answerRating != null") {{ ratingDisplay(r.ratingStyle, a.answerRating) }}
          .answer-image(v-else-if="isImage(a.answerText)")
            img(:src="a.answerText")
          pre.answer-text(v-else-if="r.type === 'MULTIPLE_CHOICE' && a.answerText") {{ parseMultiSelect(a.answerText).join(', ') }}
          pre.answer-text(v-else-if="a.answerText") {{ a.answerText }}
          span.answer-empty(v-else) (sin respuesta)
          .answer-grade(v-if="a.gradeScore != null")
            strong {{ a.gradeScore.toFixed(0) }}/100
            span(v-if="a.gradeFeedback") – {{ a.gradeFeedback }}
      p.no-responses(v-if="!r.responseCount") Sin respuestas todavía
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getQuestions, createQuestion, updateQuestion, deactivateQuestion, getResults, suggestQuestions, purgeResponses, improveQuestion, gradeResponses } from '@/services/api/surveyApi'
import { useAuthStore } from '@/features/auth/authStore'
import ConferenceSubNav from './ConferenceSubNav.vue'
import BarChart from '@/components/charts/BarChart.vue'

interface SurveyForm {
  text: string
  type: string
  ratingStyle: string
  options: string[]
  optionsCorrect: boolean[]
  referenceAnswer: string
  required: boolean
}

interface SurveyQuestionRow {
  uuid: string
  text: string
  type: string
  active?: boolean
  options?: string[]
  referenceAnswer?: string | null
  ratingStyle?: string
  required?: boolean
}

interface IndividualAnswer {
  respondentName?: string
  respondentUuid?: string
  answerRating?: number | null
  answerText?: string
  gradeScore?: number | null
  gradeFeedback?: string
}

interface SurveyResult {
  questionUuid: string
  text: string
  type: string
  responseCount: number
  averageRating?: number | null
  averageGradeScore?: number | null
  ratingStyle?: string
  counts?: Record<string, number>
  individualAnswers?: IndividualAnswer[]
}

interface SuggestedQuestion {
  text: string
  type: string
  options?: string[]
  referenceAnswer?: string | null
}

function emptyForm(): SurveyForm {
  return { text: '', type: 'RATING', ratingStyle: 'STARS', options: [], optionsCorrect: [], referenceAnswer: '', required: true }
}

function parseMultiSelect(raw: string | null | undefined): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed
  } catch (e: any) { /* formato anterior: string plano */ }
  return [raw]
}

const TYPE_ICONS: Record<string, string> = {
  RATING: '★', TEXT: '📝', MULTIPLE_CHOICE: '☑️', OPEN_GRADED: '💬',
  CODE_GRADED: '💻', CANVAS_DRAWING: '🎨', DRAG_DROP: '⇕'
}

export default {
  name: 'SurveyManagePage',
  components: { ConferenceSubNav, BarChart },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const activeTab = ref('create')
    const questions = ref<SurveyQuestionRow[]>([])
    const results = ref<SurveyResult[]>([])
    const saving = ref(false)
    const suggesting = ref(false)
    const suggestError = ref('')
    const suggestions = ref<SuggestedQuestion[]>([])
    const selectedSuggestions = ref<number[]>([])
    const addingSuggestions = ref(false)
    const improving = ref(false)
    const improveError = ref('')
    const improvements = ref<SuggestedQuestion[]>([])
    const editingId = ref<string | null>(null)
    const form = ref<SurveyForm>(emptyForm())
    const deleteTarget = ref<SurveyQuestionRow | null>(null)
    const purgeTarget = ref<SurveyResult | null>(null)
    const openDetail = ref<Record<string, boolean>>({})
    const reviewOpen = ref(false)
    const selectedForGrading = ref<string[]>([])
    const regradeAll = ref(false)
    const grading = ref(false)
    const gradeStatus = ref('')

    const gradeableQuestions = computed(() =>
      results.value.filter((r) => r.type === 'OPEN_GRADED' || r.type === 'CODE_GRADED' || r.type === 'MULTIPLE_CHOICE'))

    const allGradeableSelected = computed(() =>
      gradeableQuestions.value.length > 0
      && gradeableQuestions.value.every((q) => selectedForGrading.value.includes(q.questionUuid)))

    function toggleSelectAllGradeable() {
      selectedForGrading.value = allGradeableSelected.value
        ? []
        : gradeableQuestions.value.map((q) => q.questionUuid)
    }

    async function runGrading() {
      grading.value = true
      gradeStatus.value = ''
      try {
        const res = await gradeResponses(props.conferenceId as string, selectedForGrading.value, auth.state.token as string, regradeAll.value)
        const { graded, skipped } = res.data || {}
        gradeStatus.value = `Calificadas ${graded ?? 0}, omitidas ${skipped ?? 0}`
        reviewOpen.value = false
        selectedForGrading.value = []
        await load()
      } catch (e: any) {
        gradeStatus.value = 'No se pudo calificar (¿está configurado el proveedor de IA?)'
      } finally {
        grading.value = false
      }
    }

    const EMOJI_SCALE = ['😢', '😕', '😐', '🙂', '🤩']
    function ratingDisplay(ratingStyle: string | undefined, value: number): string {
      if (ratingStyle === 'EMOJIS') return EMOJI_SCALE[value - 1] || String(value)
      return '★'.repeat(value) + '☆'.repeat(5 - value)
    }

    function ratingChartData(r: SurveyResult) {
      const labels = r.ratingStyle === 'EMOJIS' ? EMOJI_SCALE : ['1★', '2★', '3★', '4★', '5★']
      const counts = [0, 0, 0, 0, 0]
      for (const a of r.individualAnswers || []) {
        if (a.answerRating && a.answerRating >= 1 && a.answerRating <= 5) counts[a.answerRating - 1]++
      }
      return labels.map((label, i) => ({ label, value: counts[i] }))
    }

    function choiceChartData(r: SurveyResult) {
      return Object.entries(r.counts || {}).map(([label, value]) => ({ label, value }))
    }

    function toggleDetail(questionUuid: string) {
      openDetail.value = { ...openDetail.value, [questionUuid]: !openDetail.value[questionUuid] }
    }

    function typeLabel(t: string): string {
      return ({
        RATING: 'Calificación', TEXT: 'Texto libre', MULTIPLE_CHOICE: 'Opción múltiple',
        OPEN_GRADED: 'Abierta (IA)', CODE_GRADED: 'Código (IA)',
        CANVAS_DRAWING: 'Diagrama/dibujo', DRAG_DROP: 'Ordenar (drag and drop)'
      } as Record<string, string>)[t] || t
    }

    function typeIcon(t: string): string { return TYPE_ICONS[t] || '•' }

    function isImage(text: string | undefined): boolean {
      return typeof text === 'string' && text.startsWith('data:image')
    }

    function onTypeChange() {
      const needsOptions = form.value.type === 'MULTIPLE_CHOICE' || form.value.type === 'DRAG_DROP'
      if (needsOptions && form.value.options.length < 2) {
        form.value.options = ['', '']
      }
      if (form.value.optionsCorrect.length !== form.value.options.length) {
        form.value.optionsCorrect = form.value.options.map((_, i) => !!form.value.optionsCorrect[i])
      }
    }

    function addOption() {
      form.value.options.push('')
      form.value.optionsCorrect.push(false)
    }

    function removeOption(idx: number) {
      form.value.options.splice(idx, 1)
      form.value.optionsCorrect.splice(idx, 1)
    }

    function moveOption(idx: number, delta: number) {
      const newIdx = idx + delta
      if (newIdx < 0 || newIdx >= form.value.options.length) return
      const [item] = form.value.options.splice(idx, 1)
      form.value.options.splice(newIdx, 0, item)
      const [correct] = form.value.optionsCorrect.splice(idx, 1)
      form.value.optionsCorrect.splice(newIdx, 0, correct)
    }

    async function load() {
      if (!props.conferenceId) return
      try {
        const qRes = await getQuestions(props.conferenceId, false)
        questions.value = (qRes.data || []).filter((q: SurveyQuestionRow) => q.active)
      } catch (e: any) { questions.value = [] }
      try {
        const rRes = await getResults(props.conferenceId, auth.state.token as string)
        results.value = rRes.data || []
      } catch (e: any) { results.value = [] }
    }

    async function suggest() {
      suggestError.value = ''
      suggesting.value = true
      try {
        const res = await suggestQuestions(props.conferenceId as string, 5, auth.state.token as string)
        suggestions.value = res.data || []
        selectedSuggestions.value = []
      } catch (e: any) {
        suggestError.value = 'No se pudieron generar sugerencias (¿hay una presentación subida?)'
      } finally {
        suggesting.value = false
      }
    }

    async function addSelectedSuggestions() {
      addingSuggestions.value = true
      try {
        const toAdd = selectedSuggestions.value.map((i) => suggestions.value[i]).filter(Boolean)
        for (const s of toAdd) {
          const isOptionsType = s.type === 'MULTIPLE_CHOICE' || s.type === 'DRAG_DROP'
          const payload = {
            text: s.text,
            type: s.type,
            options: isOptionsType && s.options && s.options.length ? [...s.options] : null,
            referenceAnswer: s.referenceAnswer || null,
            ratingStyle: s.type === 'RATING' ? 'STARS' : null,
            orderIndex: questions.value.length
          }
          await createQuestion(props.conferenceId as string, payload, auth.state.token as string)
        }
        suggestions.value = []
        selectedSuggestions.value = []
        await load()
      } finally {
        addingSuggestions.value = false
      }
    }

    async function improve() {
      improveError.value = ''
      improving.value = true
      try {
        const isOptionsType = form.value.type === 'MULTIPLE_CHOICE' || form.value.type === 'DRAG_DROP'
        const options = isOptionsType ? form.value.options.map((o) => o.trim()).filter(Boolean) : null
        const res = await improveQuestion(props.conferenceId as string, {
          text: form.value.text,
          type: form.value.type,
          options,
          referenceAnswer: form.value.referenceAnswer || null
        }, auth.state.token as string)
        improvements.value = res.data || []
      } catch (e: any) {
        improveError.value = 'No se pudo mejorar la pregunta con IA. Intenta de nuevo.'
      } finally {
        improving.value = false
      }
    }

    function applyImprovement(s: SuggestedQuestion) {
      const options = s.options && s.options.length ? [...s.options] : []
      form.value = {
        text: s.text,
        type: s.type,
        ratingStyle: form.value.ratingStyle || 'STARS',
        options,
        optionsCorrect: options.map(() => false),
        referenceAnswer: s.type === 'MULTIPLE_CHOICE' ? '' : (s.referenceAnswer || ''),
        required: form.value.required
      }
      onTypeChange()
      improvements.value = []
    }

    function startEdit(q: SurveyQuestionRow) {
      activeTab.value = 'create'
      editingId.value = q.uuid
      const options = q.options && q.options.length ? [...q.options] : []
      const correctSet = q.type === 'MULTIPLE_CHOICE' ? parseMultiSelect(q.referenceAnswer) : []
      form.value = {
        text: q.text,
        type: q.type,
        ratingStyle: q.ratingStyle || 'STARS',
        options,
        optionsCorrect: options.map((o) => correctSet.includes(o)),
        referenceAnswer: q.type === 'MULTIPLE_CHOICE' ? '' : (q.referenceAnswer || ''),
        required: q.required !== false
      }
      onTypeChange()
      suggestions.value = []
      improvements.value = []
    }

    function cancelEdit() {
      editingId.value = null
      form.value = emptyForm()
      improvements.value = []
    }

    async function save() {
      saving.value = true
      try {
        const isOptionsType = form.value.type === 'MULTIPLE_CHOICE' || form.value.type === 'DRAG_DROP'
        const options = isOptionsType
          ? form.value.options.map((o) => o.trim()).filter(Boolean)
          : null
        const referenceAnswer = form.value.type === 'MULTIPLE_CHOICE'
          ? (() => {
              const correct = form.value.options
                .map((o, i) => (form.value.optionsCorrect[i] ? o.trim() : null))
                .filter(Boolean)
              return correct.length ? JSON.stringify(correct) : null
            })()
          : (form.value.referenceAnswer || null)
        const payload = {
          text: form.value.text,
          type: form.value.type,
          options,
          referenceAnswer,
          ratingStyle: form.value.type === 'RATING' ? form.value.ratingStyle : null,
          orderIndex: questions.value.length,
          required: form.value.required
        }
        if (editingId.value) {
          await updateQuestion(props.conferenceId as string, editingId.value, payload, auth.state.token as string)
        } else {
          await createQuestion(props.conferenceId as string, payload, auth.state.token as string)
        }
        editingId.value = null
        form.value = emptyForm()
        suggestions.value = []
        improvements.value = []
        await load()
      } finally {
        saving.value = false
      }
    }

    function confirmDelete(q: SurveyQuestionRow) { deleteTarget.value = q }

    async function doDelete() {
      const q = deleteTarget.value
      if (!q) return
      deleteTarget.value = null
      await deactivateQuestion(props.conferenceId as string, q.uuid, auth.state.token as string)
      await load()
    }

    function confirmPurge(r: SurveyResult) { purgeTarget.value = r }

    async function doPurge() {
      const r = purgeTarget.value
      if (!r) return
      purgeTarget.value = null
      await purgeResponses(props.conferenceId as string, r.questionUuid, auth.state.token as string)
      await load()
    }

    onMounted(load)

    return {
      activeTab, questions, results, saving, suggesting, suggestError, suggestions, form, editingId,
      selectedSuggestions, addingSuggestions,
      deleteTarget, purgeTarget, openDetail, improving, improveError, improvements,
      reviewOpen, selectedForGrading, regradeAll, grading, gradeStatus, gradeableQuestions, allGradeableSelected,
      toggleSelectAllGradeable, runGrading,
      typeLabel, typeIcon, isImage, parseMultiSelect, ratingDisplay, ratingChartData, choiceChartData, toggleDetail, save, confirmDelete, doDelete,
      confirmPurge, doPurge, suggest, addSelectedSuggestions, startEdit, cancelEdit, onTypeChange, addOption,
      removeOption, moveOption, improve, applyImprovement
    }
  }
}
</script>

<style scoped>
.survey-manage-page { padding: 24px; max-width: 720px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.tabs { display: flex; gap: 6px; margin-bottom: 16px; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap; }
.tab-btn {
  padding: 10px 16px; border: none; background: none; color: #6b7280; cursor: pointer;
  font-size: 0.9rem; font-weight: 600; border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.tab-btn:hover { color: #4f46e5; }
.tab-btn.active { color: #4f46e5; border-bottom-color: #4f46e5; }
.add-card, .questions-card, .results-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
h3 { margin: 0 0 12px; color: #1e1b4b; }
h4 { margin: 0 0 8px; color: #374151; font-size: 0.9rem; }
input, select, textarea {
  display: block; width: 100%; padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px;
  margin-bottom: 10px; font-size: 0.9rem; font-family: inherit;
}
.required-check { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: #374151; margin-bottom: 12px; }
.required-check input { width: auto; margin: 0; }
.q-required { color: #dc2626; font-weight: 700; }
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
.text-row { display: flex; gap: 8px; align-items: flex-start; }
.text-row input { flex: 1; }
.btn-wand {
  flex-shrink: 0; width: 38px; height: 38px; border: 1.5px solid #a5b4fc; border-radius: 8px;
  background: #f5f3ff; cursor: pointer; font-size: 1rem;
}
.btn-wand:hover:not(:disabled) { background: #ede9fe; }
.btn-wand:disabled { opacity: 0.4; cursor: not-allowed; }
.suggestions { background: #f5f3ff; border-radius: 10px; padding: 12px; margin-bottom: 16px; }
.suggestion-row {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 8px 0; border-bottom: 1px solid #e9e5ff;
}
.suggestion-row:last-child { border-bottom: none; }
.suggestion-check { display: flex; align-items: flex-start; flex-shrink: 0; padding-top: 2px; }
.suggestion-check input { flex-shrink: 0; width: 16px; height: 16px; margin: 0; }
.suggestions-actions { display: flex; gap: 10px; align-items: center; padding-top: 10px; }
.suggestion-text { flex: 1; min-width: 0; font-size: 0.85rem; color: #374151; }
.suggestion-type { margin-left: 8px; color: #6b7280; font-size: 0.75rem; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-primary-sm { background: #4f46e5; color: #fff; flex-shrink: 0; }

.style-row { display: flex; gap: 10px; margin-bottom: 10px; }
.style-option {
  flex: 1; display: flex; align-items: center; gap: 6px; padding: 8px 12px;
  border: 1.5px solid #d1d5db; border-radius: 8px; cursor: pointer; font-size: 0.85rem; font-weight: 500;
}
.style-option input { width: auto; margin: 0; }
.style-option.active { border-color: #4f46e5; background: #f5f3ff; color: #4338ca; }

.options-editor { margin-bottom: 10px; }
.options-label { font-size: 0.82rem; color: #6b7280; margin-bottom: 6px; display: block; }
.option-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.option-row input { margin-bottom: 0; flex: 1; }
.option-bullet { color: #9ca3af; flex-shrink: 0; }
.option-correct { flex-shrink: 0; display: flex; align-items: center; }
.option-correct input { width: auto; margin: 0; }
.options-hint { color: #92400e; font-size: 0.78rem; margin: 4px 0 0; }
.option-order {
  flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%; background: #4f46e5; color: #fff;
  font-size: 0.7rem; font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.option-arrows { display: flex; gap: 2px; flex-shrink: 0; }
.btn-icon {
  flex-shrink: 0; width: 28px; height: 28px; border: 1px solid #e5e7eb; border-radius: 6px;
  background: #f9fafb; color: #6b7280; cursor: pointer; font-size: 0.8rem;
}
.btn-icon:hover:not(:disabled) { background: #f3f4f6; color: #374151; }
.btn-icon:disabled { opacity: 0.3; cursor: not-allowed; }
.btn-add {
  padding: 6px 14px; border: 1px dashed #a5b4fc; border-radius: 8px; background: #f5f3ff; color: #4338ca;
  cursor: pointer; font-size: 0.82rem; font-weight: 500;
}
.btn-add:hover { background: #ede9fe; }

.question-item {
  padding: 12px 0; border-bottom: 1px solid #f3f4f6;
}
.question-item.editing { background: #f5f3ff; border-radius: 8px; padding: 12px; margin: -2px 0; }
.question-item-header { display: flex; align-items: center; gap: 8px; }
.q-icon { flex-shrink: 0; }
.q-text { flex: 1; font-weight: 500; color: #1e1b4b; }
.q-type { color: #6b7280; font-size: 0.78rem; }
.q-preview { margin: 6px 0 0 24px; display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  background: #f3f4f6; color: #374151; border-radius: 6px; padding: 3px 9px; font-size: 0.78rem;
}
.chip-ordered { background: #e0e7ff; color: #4338ca; }
.chip-ref { background: #fef3c7; color: #92400e; max-width: 100%; }
.question-item-actions { margin: 8px 0 0 24px; display: flex; gap: 8px; }
.btn-edit { background: #e0e7ff; color: #4338ca; }
.btn-edit:hover { background: #c7d2fe; }
.btn-delete { background: #fee2e2; color: #dc2626; }
.btn-delete:hover { background: #fecaca; }

.confirm-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 100;
}
.confirm-dialog {
  background: #fff; border-radius: 16px; padding: 28px 32px;
  max-width: 420px; width: 90%; box-shadow: 0 8px 40px rgba(0,0,0,0.2);
}
.confirm-dialog h4 { margin: 0 0 12px; color: #1e1b4b; font-size: 1.1rem; }
.confirm-dialog p { color: #6b7280; font-size: 0.92rem; margin: 0 0 24px; line-height: 1.5; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; }
.btn-cancel { padding: 8px 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #374151; cursor: pointer; }
.btn-cancel:hover { background: #f3f4f6; }
.btn-confirm { padding: 8px 18px; background: #dc2626; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; }
.btn-confirm:hover { background: #b91c1c; }

.grading-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.grading-status { font-size: 0.82rem; color: #059669; }
.grading-panel {
  background: #f5f3ff; border-radius: 10px; padding: 14px; margin-bottom: 16px;
  display: flex; flex-direction: column; gap: 8px;
}
.grading-select-all { font-weight: 600; color: #4338ca; font-size: 0.85rem; }
.grading-select-all, .grading-question, .grading-regrade {
  display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: #374151;
}
.grading-question input, .grading-select-all input, .grading-regrade input { width: auto; margin: 0; }
.grading-panel-actions { display: flex; align-items: center; gap: 14px; margin-top: 6px; flex-wrap: wrap; }
.result-row { margin-bottom: 16px; padding-bottom: 16px; border-bottom: 1px solid #f3f4f6; }
.result-row:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
.result-question { font-weight: 600; color: #1e1b4b; margin-bottom: 4px; }
.avg-grade { color: #059669; font-weight: 600; }
.summary-line { color: #374151; margin: 4px 0; }
.summary-counts { margin: 4px 0 0 16px; padding: 0; color: #374151; }
.chart-wrap { margin-top: 10px; max-width: 360px; }
.no-responses { color: #9ca3af; font-size: 0.85rem; font-style: italic; margin: 4px 0 0; }
.result-actions { display: flex; align-items: center; gap: 14px; margin-top: 8px; }
.btn-toggle {
  padding: 4px 0; border: none; background: none; color: #4f46e5;
  cursor: pointer; font-size: 0.82rem; font-weight: 500;
}
.btn-toggle:hover { text-decoration: underline; }
.btn-purge {
  padding: 4px 10px; border: 1px solid #fecaca; border-radius: 6px; background: #fff5f5; color: #dc2626;
  cursor: pointer; font-size: 0.78rem; font-weight: 500;
}
.btn-purge:hover { background: #fee2e2; }
.individual-answers { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.individual-answer { background: #f9fafb; border-radius: 8px; padding: 10px 12px; }
.answer-author { font-size: 0.78rem; color: #4f46e5; font-weight: 600; margin-bottom: 4px; }
.answer-rating { font-size: 1.1rem; }
.answer-text { white-space: pre-wrap; font-family: 'SF Mono', Consolas, monospace; font-size: 0.82rem; margin: 0 0 6px; color: #374151; }
.answer-empty { color: #9ca3af; font-size: 0.82rem; font-style: italic; }
.answer-grade { font-size: 0.85rem; color: #059669; margin-top: 4px; }
.answer-image img { max-width: 100%; border-radius: 6px; border: 1px solid #e5e7eb; }

@media (max-width: 640px) {
  .survey-manage-page { padding: 14px; }
  .tabs { gap: 4px; }
  .tab-btn { padding: 8px 10px; font-size: 0.82rem; }
}
</style>
