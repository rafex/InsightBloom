<template lang="pug">
.survey-manage-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")
  h2 Encuesta de la conferencia

  .engine-card
    h3 Motor de encuesta
    p.engine-help El motor se elige una sola vez para este evento. SurveyJS usa la librería Form Library; la autoría se controla desde esta pantalla.
    .engine-row(v-if="!engine")
      select(v-model="selectedEngine")
        option(value="NATIVE") Motor nativo de InsightBloom
        option(value="SURVEYJS") SurveyJS Form Library
      BaseButton(variant="primary" type="button" :loading="engineSaving" :disabled="engineSaving" @click="chooseEngine") {{ engineSaving ? 'Guardando...' : 'Elegir motor' }}
    p.engine-current(v-else) Motor activo: <strong>{{ engine === 'SURVEYJS' ? 'SurveyJS Form Library' : 'Nativo de InsightBloom' }}</strong>
    p.ai-shared La sugerencia de preguntas con IA sigue disponible para ambos motores.

  nav.tabs(v-if="engine")
    button.tab-btn(type="button" :class="{ active: activeTab === 'create' }" @click="activeTab = 'create'") ➕ Crear
    button.tab-btn(type="button" :class="{ active: activeTab === 'results' }" @click="activeTab = 'results'") 📊 Resultados
    button.tab-btn(type="button" :class="{ active: activeTab === 'release' }" @click="activeTab = 'release'") 🔓 Liberar

  .surveyjs-editor(v-if="engine === 'SURVEYJS'" v-show="activeTab === 'create'")
    h3 Editor SurveyJS controlado
    p.editor-help Solo se guardan tipos compatibles con SurveyJS Form Library. No se incluye Survey Creator ni componentes comerciales.
    input(v-model="surveyJsTitle" placeholder="Título de la encuesta")
    .surveyjs-add-row
      select(v-model="surveyJsType")
        option(value="text") Texto corto
        option(value="comment") Texto largo
        option(value="radiogroup") Opción única
        option(value="checkbox") Opción múltiple
        option(value="dropdown") Lista desplegable
        option(value="rating") Calificación
        option(value="boolean") Sí / No
        option(value="ranking") Ordenar elementos
      input(v-model="surveyJsQuestion" placeholder="Pregunta")
      input(v-if="['radiogroup', 'checkbox', 'dropdown', 'ranking'].includes(surveyJsType)" v-model="surveyJsChoices" placeholder="Opciones separadas por coma")
      label.required-check
        input(type="checkbox" v-model="surveyJsRequired")
        span Obligatoria
      BaseButton(variant="primary" size="sm" type="button" @click="addSurveyElement()") Agregar
    .ai-suggest-row
      BaseButton(variant="secondary" type="button" :loading="suggesting" :disabled="suggesting" @click="suggest") {{ suggesting ? 'Pensando...' : '✨ Sugerir preguntas con IA' }}
      span.ai-error(v-if="suggestError") {{ suggestError }}
    .suggestions(v-if="suggestions.length")
      h4 Sugerencias compatibles
      .suggestion-row(v-for="(s, i) in suggestions" :key="i")
        label.suggestion-check
          input(type="checkbox" :value="i" v-model="selectedSuggestions")
        .suggestion-text
          strong {{ s.text }}
          span.suggestion-type {{ typeLabel(s.type) }}
      .suggestions-actions
        BaseButton(variant="primary" size="sm" type="button" :disabled="!selectedSuggestions.length || addingSuggestions" :loading="addingSuggestions" @click="addSelectedSuggestions") Agregar seleccionadas
    .surveyjs-elements(v-if="surveyElements.length")
      p.editor-help Reordená con las flechas y marcá cuáles son obligatorias antes de guardar o publicar.
      .surveyjs-element(v-for="(element, index) in surveyElements" :key="element.name")
        span.element-index {{ index + 1 }}
        .element-details
          strong {{ element.title }}
          span {{ element.type }}{{ element.isRequired ? ' · obligatoria' : '' }}
          small(v-if="element.choices && element.choices.length") {{ element.choices.join(', ') }}
        .element-controls
          label.required-check.element-required
            input(type="checkbox" :checked="!!element.isRequired" @change="toggleSurveyElementRequired(index)")
            span Obligatoria
          .option-arrows
            button.btn-icon(type="button" @click="moveSurveyElement(index, -1)" :disabled="index === 0" title="Subir") ↑
            button.btn-icon(type="button" @click="moveSurveyElement(index, 1)" :disabled="index === surveyElements.length - 1" title="Bajar") ↓
          button.btn-icon(type="button" @click="removeSurveyElement(index)" title="Quitar") ✕
    .surveyjs-actions
      BaseButton(variant="secondary" type="button" :disabled="surveyJsSaving || !surveyElements.length" @click="saveSurveyJs(false)") {{ surveyJsSaving ? 'Guardando...' : 'Guardar borrador' }}
      BaseButton(type="button" :disabled="surveyJsSaving || !surveyElements.length" @click="saveSurveyJs(true)") Publicar encuesta
    p.ai-error(v-if="surveyJsError") {{ surveyJsError }}
    .surveyjs-preview(v-if="surveyPreviewModel")
      h3 Vista previa
      SurveyComponent(:model="surveyPreviewModel")

  .surveyjs-results(v-if="engine === 'SURVEYJS'" v-show="activeTab === 'results'")
    h3 Respuestas recibidas
    .surveyjs-submission(v-for="submission in surveyJsSubmissions" :key="submission.uuid")
      strong {{ submission.submittedAt }}
      pre {{ JSON.stringify(submission.data, null, 2) }}
    p.no-responses(v-if="!surveyJsSubmissions.length") Sin respuestas todavía

  .access-card(v-show="activeTab === 'release'")
    h3 Liberar encuesta
    p.access-help La encuesta permanece bloqueada hasta que el moderador la libere. Puedes abrirla para todos los asistentes registrados o solo para los seleccionados.
    .access-state(:class="{ released: releasedForAll }") {{ releasedForAll ? 'Liberada para todos los asistentes, incluidos los que se registren después.' : 'Bloqueada para los asistentes.' }}
    .access-actions
      BaseButton(type="button" :disabled="releaseSaving || releasedForAll" @click="releaseAll") 🔓 Liberar para todos
      BaseButton(variant="secondary" type="button" :disabled="releaseSaving || !selectedAttendees.length" @click="releaseSelected") 🔓 Liberar seleccionados ({{ selectedAttendees.length }})
    .attendee-list(v-if="attendees.length")
      .attendee-header(aria-hidden="true")
        span
        span Asistente
        span Estado
      label.attendee-row(v-for="attendee in attendees" :key="attendee.uuid")
        input(type="checkbox" :value="attendee.uuid" v-model="selectedAttendees" :disabled="releasedForAll || attendee.responded")
        .attendee-info
          strong {{ attendee.displayName || 'Sin nombre' }}
          span {{ attendee.email || attendee.uuid }}
        span.attendee-status(:class="{ released: attendee.released, responded: attendee.responded }")
          | {{ attendee.responded ? 'Respondida' : attendee.released ? 'Liberada' : 'Bloqueada' }}
    p.access-empty(v-else) Aún no hay asistentes registrados en el evento.
    p.access-error(v-if="accessError") {{ accessError }}

  .add-card(v-if="engine === 'NATIVE'" v-show="activeTab === 'create'")
    h3 {{ editingId ? 'Editar pregunta' : 'Agregar pregunta' }}
    .ai-suggest-row(v-if="!editingId")
      BaseButton(variant="secondary" type="button" :disabled="suggesting" @click="suggest") {{ suggesting ? 'Pensando...' : '✨ Sugerir preguntas con IA' }}
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
        BaseButton(size="sm" type="button" :disabled="!selectedSuggestions.length || addingSuggestions" @click="addSelectedSuggestions") {{ addingSuggestions ? 'Agregando...' : `Agregar seleccionadas (${selectedSuggestions.length})` }}
        BaseButton(variant="ghost" size="sm" type="button" @click="selectedSuggestions = suggestions.map((_, i) => i)") Seleccionar todas

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
        BaseButton(size="sm" type="button" @click="applyImprovement(s)") Usar esta

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
      BaseButton(:disabled="!form.text || saving" @click="save") {{ saving ? 'Guardando...' : (editingId ? 'Guardar cambios' : 'Agregar') }}
      BaseButton(variant="ghost" size="sm" v-if="editingId" type="button" @click="cancelEdit") Cancelar

  .questions-card(v-if="engine === 'NATIVE' && questions.length" v-show="activeTab === 'create'")
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
        BaseButton(variant="secondary" size="sm" type="button" @click="startEdit(q)") Editar
        BaseButton(variant="danger" size="sm" type="button" @click="confirmDelete(q)") 🗑 Eliminar

  .confirm-overlay(v-if="engine === 'NATIVE' && deleteTarget" @click.self="deleteTarget = null")
    .confirm-dialog
      h4 ¿Eliminar pregunta?
      p Esto quitará <strong>"{{ deleteTarget.text }}"</strong> de la encuesta de forma permanente. Las respuestas ya recibidas se conservan en los resultados.
      .confirm-actions
        BaseButton(variant="ghost" size="sm" type="button" @click="deleteTarget = null") Cancelar
        BaseButton(variant="danger" size="sm" type="button" @click="doDelete") Eliminar

  .confirm-overlay(v-if="engine === 'NATIVE' && purgeTarget" @click.self="purgeTarget = null")
    .confirm-dialog
      h4 ¿Purgar respuestas?
      p Esto eliminará permanentemente las <strong>{{ purgeTarget.responseCount }} respuestas</strong> recibidas para "{{ purgeTarget.text }}". La pregunta se conserva, solo se borran las respuestas.
      .confirm-actions
        BaseButton(variant="ghost" size="sm" type="button" @click="purgeTarget = null") Cancelar
        BaseButton(variant="danger" size="sm" type="button" @click="doPurge") Purgar

  .results-card(v-if="engine === 'NATIVE' && results.length" v-show="activeTab === 'results'")
    h3 Resultados

    .grading-toolbar(v-if="gradeableQuestions.length")
      BaseButton(variant="secondary" size="sm" type="button" @click="reviewOpen = !reviewOpen") 🤖 Revisar respuestas
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
        BaseButton(size="sm" type="button" :disabled="!selectedForGrading.length || grading" :loading="grading" @click="runGrading")
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
        BaseButton(variant="danger" size="sm" type="button" @click="confirmPurge(r)") 🗑 Purgar respuestas

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
import { ref, computed, onMounted, shallowRef } from 'vue'
import { Model } from 'survey-core'
import 'survey-core/i18n/spanish'
import { SurveyComponent } from 'survey-vue3-ui'
import BaseButton from '@/components/ui/BaseButton.vue'
import { getQuestions, createQuestion, updateQuestion, deactivateQuestion, getResults, suggestQuestions, purgeResponses, improveQuestion, gradeResponses, getSurveyDefinition, selectSurveyEngine, saveSurveyDefinition, validateSurveyDefinition, publishSurveyDefinition, getSurveyJsSubmissions, getSurveyAccessManagement, releaseSurveyAccess, type SurveyEngine, type SurveyAttendee } from '@/services/api/surveyApi'
import { getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
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
  components: { DashboardBreadcrumb, ConferenceToolsNav, BarChart, SurveyComponent, BaseButton },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const conferenceName = ref('')
    const engine = ref<SurveyEngine | null>(null)
    const selectedEngine = ref<SurveyEngine>('NATIVE')
    const engineSaving = ref(false)
    const surveySchema = ref<Record<string, any>>(emptySurveySchema())
    const surveyPreviewModel = shallowRef<Model | null>(null)
    const surveyJsTitle = ref('Encuesta')
    const surveyJsType = ref('text')
    const surveyJsQuestion = ref('')
    const surveyJsChoices = ref('')
    const surveyJsRequired = ref(true)
    const surveyJsSaving = ref(false)
    const surveyJsError = ref('')
    const surveyJsSubmissions = ref<any[]>([])
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
    const attendees = ref<SurveyAttendee[]>([])
    const selectedAttendees = ref<string[]>([])
    const releasedForAll = ref(false)
    const releaseSaving = ref(false)
    const accessError = ref('')

    const surveyElements = computed<any[]>(() => {
      const pages = Array.isArray(surveySchema.value.pages) ? surveySchema.value.pages : []
      return pages.flatMap((page: any) => Array.isArray(page.elements) ? page.elements : [])
    })

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

    function emptySurveySchema(): Record<string, any> {
      return { title: 'Encuesta', pages: [{ name: 'pagina1', elements: [] }] }
    }

    function refreshSurveyPreview() {
      const model = new Model(JSON.parse(JSON.stringify(surveySchema.value)))
      model.locale = 'es'
      model.completeText = 'Enviar respuestas'
      surveyPreviewModel.value = model
    }

    function surveyJsTypeFor(nativeType: string): string {
      return ({
        RATING: 'rating', TEXT: 'text', OPEN_GRADED: 'comment', CODE_GRADED: 'comment',
        MULTIPLE_CHOICE: 'radiogroup', DRAG_DROP: 'ranking'
      } as Record<string, string>)[nativeType] || 'comment'
    }

    function addSurveyElement(spec?: SuggestedQuestion) {
      const type = spec ? surveyJsTypeFor(spec.type) : surveyJsType.value
      const title = spec?.text || surveyJsQuestion.value.trim()
      if (!title) return
      const choices = spec?.options?.length
        ? [...spec.options]
        : surveyJsChoices.value.split(',').map((choice) => choice.trim()).filter(Boolean)
      const element: Record<string, any> = {
        type,
        name: `pregunta_${Date.now()}_${surveyElements.value.length}`,
        title,
        isRequired: spec ? true : surveyJsRequired.value
      }
      if (['radiogroup', 'checkbox', 'dropdown', 'ranking'].includes(type)) element.choices = choices
      surveySchema.value.pages[0].elements.push(element)
      if (!spec) {
        surveyJsQuestion.value = ''
        surveyJsChoices.value = ''
      }
      refreshSurveyPreview()
    }

    function removeSurveyElement(index: number) {
      surveySchema.value.pages[0].elements.splice(index, 1)
      refreshSurveyPreview()
    }

    function moveSurveyElement(index: number, delta: number) {
      const elements = surveySchema.value.pages[0].elements
      const newIndex = index + delta
      if (newIndex < 0 || newIndex >= elements.length) return
      const [item] = elements.splice(index, 1)
      elements.splice(newIndex, 0, item)
      refreshSurveyPreview()
    }

    function toggleSurveyElementRequired(index: number) {
      const element = surveySchema.value.pages[0].elements[index]
      element.isRequired = !element.isRequired
      refreshSurveyPreview()
    }

    async function chooseEngine() {
      engineSaving.value = true
      surveyJsError.value = ''
      try {
        const response = await selectSurveyEngine(props.conferenceId as string, selectedEngine.value, auth.state.token as string)
        engine.value = response.data.engine
        if (engine.value === 'SURVEYJS') {
          surveySchema.value = emptySurveySchema()
          refreshSurveyPreview()
        } else {
          await load()
        }
      } catch (e: any) {
        surveyJsError.value = e.response?.data?.error?.message || 'No se pudo seleccionar el motor.'
      } finally {
        engineSaving.value = false
      }
    }

    async function saveSurveyJs(publish: boolean) {
      surveyJsSaving.value = true
      surveyJsError.value = ''
      surveySchema.value.title = surveyJsTitle.value || 'Encuesta'
      try {
        await validateSurveyDefinition(props.conferenceId as string, surveySchema.value, auth.state.token as string)
        if (publish) await publishSurveyDefinition(props.conferenceId as string, surveySchema.value, auth.state.token as string)
        else await saveSurveyDefinition(props.conferenceId as string, surveySchema.value, auth.state.token as string)
        await load()
      } catch (e: any) {
        surveyJsError.value = e.response?.data?.error?.message || 'No se pudo guardar la encuesta SurveyJS.'
      } finally {
        surveyJsSaving.value = false
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

    async function loadAccessManagement() {
      if (!props.conferenceId) return
      accessError.value = ''
      try {
        const response = await getSurveyAccessManagement(props.conferenceId, auth.state.token as string)
        releasedForAll.value = response.data.releasedForAll
        attendees.value = response.data.attendees || []
        selectedAttendees.value = selectedAttendees.value.filter((uuid) =>
          attendees.value.some((attendee) => attendee.uuid === uuid && !attendee.responded))
      } catch (e: any) {
        attendees.value = []
        accessError.value = 'No se pudo cargar la lista de asistentes.'
      }
    }

    async function releaseAll() {
      if (!props.conferenceId) return
      releaseSaving.value = true
      accessError.value = ''
      try {
        await releaseSurveyAccess(props.conferenceId, auth.state.token as string, [], true)
        await loadAccessManagement()
      } catch (e: any) {
        accessError.value = e.response?.data?.error?.message || 'No se pudo liberar la encuesta.'
      } finally {
        releaseSaving.value = false
      }
    }

    async function releaseSelected() {
      if (!props.conferenceId || !selectedAttendees.value.length) return
      releaseSaving.value = true
      accessError.value = ''
      try {
        await releaseSurveyAccess(props.conferenceId, auth.state.token as string, selectedAttendees.value)
        selectedAttendees.value = []
        await loadAccessManagement()
      } catch (e: any) {
        accessError.value = e.response?.data?.error?.message || 'No se pudo liberar la encuesta.'
      } finally {
        releaseSaving.value = false
      }
    }

    async function load() {
      if (!props.conferenceId) return
      await loadAccessManagement()
      try {
        const definition = (await getSurveyDefinition(props.conferenceId, auth.state.token, true)).data
        engine.value = definition.engine
        if (definition.engine === 'SURVEYJS') {
          surveySchema.value = (definition.schema as Record<string, any>) || emptySurveySchema()
          surveyJsTitle.value = String(surveySchema.value.title || 'Encuesta')
          refreshSurveyPreview()
          try {
            const submissions = await getSurveyJsSubmissions(props.conferenceId, auth.state.token as string)
            surveyJsSubmissions.value = submissions.data || []
          } catch (e: any) { surveyJsSubmissions.value = [] }
          return
        }
      } catch (e: any) {
        engine.value = null
      }
      if (engine.value !== 'NATIVE') {
        questions.value = []
        results.value = []
        return
      }
      try {
        const qRes = await getQuestions(props.conferenceId, false, auth.state.token as string)
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
        // Reportar la causa real en vez de adivinarla: el backend distingue 503 (IA no
        // configurada) de 400 (sin presentacion) -- colapsarlos en una sola adivinanza ya
        // desperdicio tiempo real de debugging (un 503 por bug de rutas se leyo como "falta
        // la presentacion").
        const status = e?.response?.status
        const code = e?.response?.data?.error?.code
        if (status === 503) {
          suggestError.value = 'El asistente de IA no está configurado en esta plataforma. Pedile al administrador que lo active en Configuración → IA.'
        } else if (code === 'presentation_not_found' || status === 400) {
          suggestError.value = 'Para sugerir preguntas hace falta una presentación subida: el asistente la usa como contexto. Subila primero en la sección Presentación.'
        } else {
          suggestError.value = `No se pudieron generar sugerencias (${e?.response?.data?.error?.message || status || 'error de conexión'}). Reintentá en unos segundos.`
        }
      } finally {
        suggesting.value = false
      }
    }

    async function addSelectedSuggestions() {
      addingSuggestions.value = true
      try {
        const toAdd = selectedSuggestions.value.map((i) => suggestions.value[i]).filter(Boolean)
        if (engine.value === 'SURVEYJS') {
          toAdd.forEach((suggestion) => addSurveyElement(suggestion))
          suggestions.value = []
          selectedSuggestions.value = []
          return
        }
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
    onMounted(async () => {
      if (!props.conferenceId) return
      try {
        const conf = await getConference(props.conferenceId, auth.state.token as string)
        conferenceName.value = conf?.name || ''
      } catch (e: any) { conferenceName.value = '' }
    })

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Encuesta' }
    ])

    return {
      activeTab, questions, results, saving, suggesting, suggestError, suggestions, form, editingId,
      engine, selectedEngine, engineSaving, chooseEngine, surveyJsTitle, surveyJsType, surveyJsQuestion,
      surveyJsChoices, surveyJsRequired, surveyJsSaving, surveyJsError, surveyJsSubmissions,
      surveyElements, surveyPreviewModel, addSurveyElement, removeSurveyElement, moveSurveyElement, toggleSurveyElementRequired, saveSurveyJs,
      selectedSuggestions, addingSuggestions,
      deleteTarget, purgeTarget, openDetail, improving, improveError, improvements,
      reviewOpen, selectedForGrading, regradeAll, grading, gradeStatus, gradeableQuestions, allGradeableSelected,
      toggleSelectAllGradeable, runGrading,
      typeLabel, typeIcon, isImage, parseMultiSelect, ratingDisplay, ratingChartData, choiceChartData, toggleDetail, save, confirmDelete, doDelete,
      confirmPurge, doPurge, suggest, addSelectedSuggestions, startEdit, cancelEdit, onTypeChange, addOption,
      removeOption, moveOption, improve, applyImprovement, breadcrumbItems,
      attendees, selectedAttendees, releasedForAll, releaseSaving, accessError, releaseAll, releaseSelected
    }
  }
}
</script>

<style scoped>
.survey-manage-page { padding: 24px; max-width: 720px; }
h2 { color: var(--color-heading); margin-bottom: 20px; }
.engine-card, .surveyjs-editor {
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
.access-card {
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
.access-help, .access-empty { color: var(--color-text-muted); font-size: 0.85rem; line-height: 1.45; }
.access-state { display: inline-block; padding: 6px 10px; border-radius: 999px; background: var(--color-warning-soft); color: var(--color-warning); font-size: 0.82rem; margin: 4px 0 14px; }
.access-state.released { background: var(--color-success-soft); color: var(--color-success); }
.access-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.attendee-list { border-top: 1px solid var(--color-surface-muted); }
.attendee-header, .attendee-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) max-content;
  align-items: center;
  gap: 10px;
}
.attendee-header {
  padding: 8px 0;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.attendee-header span:last-child { text-align: right; }
.attendee-row { padding: 10px 0; border-bottom: 1px solid var(--color-surface-muted); cursor: pointer; }
.attendee-row input { width: auto; margin: 0; justify-self: center; }
.attendee-info { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.attendee-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attendee-info span { color: var(--color-text-muted); font-size: 0.78rem; overflow-wrap: anywhere; word-break: break-word; line-height: 1.3; }
.attendee-status { justify-self: end; color: var(--color-warning); font-size: 0.78rem; white-space: nowrap; text-align: right; }
.attendee-status.released { color: var(--color-success); }
.attendee-status.responded { color: var(--color-info); }
.access-error { color: var(--color-danger); font-size: 0.85rem; margin-top: 12px; }
.engine-help, .editor-help, .ai-shared { color: var(--color-text-muted); font-size: 0.85rem; line-height: 1.45; }
.engine-row, .surveyjs-add-row, .surveyjs-actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.engine-row select { flex: 1; min-width: 220px; margin-bottom: 0; }
.engine-row button { flex-shrink: 0; }
.engine-current { color: var(--color-text-secondary); }
.surveyjs-add-row { align-items: flex-start; margin: 14px 0; }
.surveyjs-add-row select, .surveyjs-add-row input { flex: 1; min-width: 140px; margin-bottom: 0; }
.surveyjs-add-row .required-check { flex-shrink: 0; margin: 8px 0 0; }
.surveyjs-elements { margin: 16px 0; border-top: 1px solid var(--color-surface-muted); }
.surveyjs-element { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--color-surface-muted); }
.element-controls { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.element-required { margin: 0; white-space: nowrap; }
.element-index { width: 24px; height: 24px; border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary-dark); display: inline-flex; align-items: center; justify-content: center; font-size: 0.75rem; }
.element-details { flex: 1; display: flex; flex-direction: column; gap: 2px; color: var(--color-text-secondary); }
.element-details span, .element-details small { color: var(--color-text-muted); font-size: 0.78rem; }
.surveyjs-actions { justify-content: flex-end; }
.surveyjs-preview { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--color-border-subtle); }
.surveyjs-results { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--color-border-subtle); }
.surveyjs-submission { background: var(--color-surface-muted); border-radius: 8px; padding: 10px; margin-bottom: 8px; }
.surveyjs-submission pre { white-space: pre-wrap; font-size: 0.78rem; margin: 8px 0 0; }
.tabs { display: flex; gap: 6px; margin-bottom: 16px; border-bottom: 1px solid var(--color-border-subtle); flex-wrap: wrap; }
.tab-btn {
  padding: 10px 16px; border: none; background: none; color: var(--color-text-muted); cursor: pointer;
  font-size: 0.9rem; font-weight: 600; border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.tab-btn:hover { color: var(--color-primary); }
.tab-btn.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.add-card, .questions-card, .results-card {
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
h3 { margin: 0 0 12px; color: var(--color-heading); }
h4 { margin: 0 0 8px; color: var(--color-text-secondary); font-size: 0.9rem; }
input, select, textarea {
  display: block; width: 100%; padding: 8px 12px; border: 1.5px solid var(--color-border); border-radius: 8px;
  margin-bottom: 10px; font-size: 0.9rem; font-family: inherit;
}
.required-check { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: var(--color-text-secondary); margin-bottom: 12px; }
.required-check input { width: auto; margin: 0; }
.q-required { color: var(--color-danger); font-weight: 700; }
.form-actions { display: flex; gap: 10px; align-items: center; }
.ai-suggest-row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.ai-error { color: var(--color-danger); font-size: 0.82rem; }
.text-row { display: flex; gap: 8px; align-items: flex-start; }
.text-row input { flex: 1; }
.btn-wand {
  flex-shrink: 0; width: 38px; height: 38px; border: 1.5px solid var(--color-primary-border); border-radius: 8px;
  background: var(--color-primary-soft); cursor: pointer; font-size: 1rem;
}
.btn-wand:hover:not(:disabled) { background: var(--color-primary-soft); }
.btn-wand:disabled { opacity: 0.4; cursor: not-allowed; }
.suggestions { background: var(--color-primary-soft); border-radius: 10px; padding: 12px; margin-bottom: 16px; }
.suggestion-row {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 8px 0; border-bottom: 1px solid var(--color-primary-border);
}
.suggestion-row:last-child { border-bottom: none; }
.suggestion-check { display: flex; align-items: flex-start; flex-shrink: 0; padding-top: 2px; }
.suggestion-check input { flex-shrink: 0; width: 16px; height: 16px; margin: 0; }
.suggestions-actions { display: flex; gap: 10px; align-items: center; padding-top: 10px; }
.suggestion-text { flex: 1; min-width: 0; font-size: 0.85rem; color: var(--color-text-secondary); }
.suggestion-type { margin-left: 8px; color: var(--color-text-muted); font-size: 0.75rem; }
.style-row { display: flex; gap: 10px; margin-bottom: 10px; }
.style-option {
  flex: 1; display: flex; align-items: center; gap: 6px; padding: 8px 12px;
  border: 1.5px solid var(--color-border); border-radius: 8px; cursor: pointer; font-size: 0.85rem; font-weight: 500;
}
.style-option input { width: auto; margin: 0; }
.style-option.active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-dark); }

.options-editor { margin-bottom: 10px; }
.options-label { font-size: 0.82rem; color: var(--color-text-muted); margin-bottom: 6px; display: block; }
.option-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.option-row input { margin-bottom: 0; flex: 1; }
.option-bullet { color: var(--color-text-muted); flex-shrink: 0; }
.option-correct { flex-shrink: 0; display: flex; align-items: center; }
.option-correct input { width: auto; margin: 0; }
.options-hint { color: var(--color-warning); font-size: 0.78rem; margin: 4px 0 0; }
.option-order {
  flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%; background: var(--color-primary); color: var(--color-on-primary);
  font-size: 0.7rem; font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.option-arrows { display: flex; gap: 2px; flex-shrink: 0; }
.btn-icon {
  flex-shrink: 0; width: 28px; height: 28px; border: 1px solid var(--color-border-subtle); border-radius: 6px;
  background: var(--color-surface-muted); color: var(--color-text-muted); cursor: pointer; font-size: 0.8rem;
}
.btn-icon:hover:not(:disabled) { background: var(--color-surface-muted); color: var(--color-text-secondary); }
.btn-icon:disabled { opacity: 0.3; cursor: not-allowed; }
.btn-add {
  padding: 6px 14px; border: 1px dashed var(--color-primary-border); border-radius: 8px; background: var(--color-primary-soft); color: var(--color-primary-dark);
  cursor: pointer; font-size: 0.82rem; font-weight: 500;
}
.btn-add:hover { background: var(--color-primary-soft); }

.question-item {
  padding: 12px 0; border-bottom: 1px solid var(--color-surface-muted);
}
.question-item.editing { background: var(--color-primary-soft); border-radius: 8px; padding: 12px; margin: -2px 0; }
.question-item-header { display: flex; align-items: center; gap: 8px; }
.q-icon { flex-shrink: 0; }
.q-text { flex: 1; font-weight: 500; color: var(--color-heading); }
.q-type { color: var(--color-text-muted); font-size: 0.78rem; }
.q-preview { margin: 6px 0 0 24px; display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  background: var(--color-surface-muted); color: var(--color-text-secondary); border-radius: 6px; padding: 3px 9px; font-size: 0.78rem;
}
.chip-ordered { background: var(--color-primary-soft); color: var(--color-primary-dark); }
.chip-ref { background: var(--color-warning-soft); color: var(--color-warning); max-width: 100%; }
.question-item-actions { margin: 8px 0 0 24px; display: flex; gap: 8px; }
.confirm-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 100;
}
.confirm-dialog {
  background: var(--color-surface); border-radius: 16px; padding: 28px 32px;
  max-width: 420px; width: 90%; box-shadow: 0 8px 40px rgba(0,0,0,0.2);
}
.confirm-dialog h4 { margin: 0 0 12px; color: var(--color-heading); font-size: 1.1rem; }
.confirm-dialog p { color: var(--color-text-muted); font-size: 0.92rem; margin: 0 0 24px; line-height: 1.5; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; }

.grading-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.grading-status { font-size: 0.82rem; color: var(--color-success); }
.grading-panel {
  background: var(--color-primary-soft); border-radius: 10px; padding: 14px; margin-bottom: 16px;
  display: flex; flex-direction: column; gap: 8px;
}
.grading-select-all { font-weight: 600; color: var(--color-primary-dark); font-size: 0.85rem; }
.grading-select-all, .grading-question, .grading-regrade {
  display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: var(--color-text-secondary);
}
.grading-question input, .grading-select-all input, .grading-regrade input { width: auto; margin: 0; }
.grading-panel-actions { display: flex; align-items: center; gap: 14px; margin-top: 6px; flex-wrap: wrap; }
.result-row { margin-bottom: 16px; padding-bottom: 16px; border-bottom: 1px solid var(--color-surface-muted); }
.result-row:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
.result-question { font-weight: 600; color: var(--color-heading); margin-bottom: 4px; }
.avg-grade { color: var(--color-success); font-weight: 600; }
.summary-line { color: var(--color-text-secondary); margin: 4px 0; }
.summary-counts { margin: 4px 0 0 16px; padding: 0; color: var(--color-text-secondary); }
.chart-wrap { margin-top: 10px; max-width: 360px; }
.no-responses { color: var(--color-text-muted); font-size: 0.85rem; font-style: italic; margin: 4px 0 0; }
.result-actions { display: flex; align-items: center; gap: 14px; margin-top: 8px; }
.btn-toggle {
  padding: 4px 0; border: none; background: none; color: var(--color-primary);
  cursor: pointer; font-size: 0.82rem; font-weight: 500;
}
.btn-toggle:hover { text-decoration: underline; }
.individual-answers { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.individual-answer { background: var(--color-surface-muted); border-radius: 8px; padding: 10px 12px; }
.answer-author { font-size: 0.78rem; color: var(--color-primary); font-weight: 600; margin-bottom: 4px; }
.answer-rating { font-size: 1.1rem; }
.answer-text { white-space: pre-wrap; font-family: var(--font-family-mono); font-size: 0.82rem; margin: 0 0 6px; color: var(--color-text-secondary); }
.answer-empty { color: var(--color-text-muted); font-size: 0.82rem; font-style: italic; }
.answer-grade { font-size: 0.85rem; color: var(--color-success); margin-top: 4px; }
.answer-image img { max-width: 100%; border-radius: 6px; border: 1px solid var(--color-border-subtle); }

@media (max-width: 640px) {
  .survey-manage-page { padding: 14px; }
  .tabs { gap: 4px; }
  .tab-btn { padding: 8px 10px; font-size: 0.82rem; }
  .attendee-header { display: none; }
  .attendee-row { grid-template-columns: 24px minmax(0, 1fr) max-content; gap: 8px; padding: 11px 0; }
}
</style>
