<template lang="pug">
.survey-page
  template(v-if="submitted")
    .thank-you
      h2 ¡Gracias por tu retroalimentación! 🙌
      p Tu opinión ayuda a mejorar futuras charlas y talleres.
      .contact-card
        h3 Sigamos en contacto
        p.contact-name {{ contact.name }}
        ul.contact-links
          li
            a(:href="`mailto:${contact.email}`") ✉️ {{ contact.email }}
          li
            a(:href="contact.linkedin" target="_blank" rel="noopener") 🔗 LinkedIn
          li
            a(:href="contact.github" target="_blank" rel="noopener") 🐙 GitHub
          li
            a(:href="contact.blog" target="_blank" rel="noopener") 📝 Blog
      a.btn-primary(:href="pdfUrl" target="_blank" rel="noopener" v-if="pdfReady") Descargar presentación (PDF)

  template(v-else)
    h2 Cuéntanos qué te pareció la charla
    .survey-loading(v-if="loading") Cargando encuesta...
    .survey-empty(v-else-if="!questions.length") Esta conferencia no tiene encuesta configurada.
    form.survey-form(v-else @submit.prevent="submit")
      .question(v-for="q in questions" :key="q.uuid")
        label {{ q.text }}

        .rating(v-if="q.type === 'RATING' && q.ratingStyle !== 'EMOJIS'")
          button.star(
            type="button"
            v-for="n in 5" :key="n"
            :class="{ active: (answers[q.uuid]?.rating || 0) >= n }"
            @click="setRating(q.uuid, n)"
          ) ★

        .rating.emoji-rating(v-else-if="q.type === 'RATING' && q.ratingStyle === 'EMOJIS'")
          button.emoji(
            type="button"
            v-for="(e, idx) in emojiScale" :key="idx"
            :class="{ active: answers[q.uuid]?.rating === idx + 1 }"
            @click="setRating(q.uuid, idx + 1)"
          ) {{ e }}

        textarea(v-else-if="q.type === 'TEXT' || q.type === 'OPEN_GRADED'" v-model="answersText[q.uuid]" rows="3" placeholder="Escribe tu respuesta...")

        textarea.code-input(v-else-if="q.type === 'CODE_GRADED'" v-model="answersText[q.uuid]" rows="8" placeholder="Escribe tu código aquí...")

        .choices(v-else-if="q.type === 'MULTIPLE_CHOICE'")
          label.choice(v-for="opt in q.options" :key="opt")
            input(type="radio" :name="q.uuid" :value="opt" v-model="answersText[q.uuid]")
            span {{ opt }}

        .canvas-wrap(v-else-if="q.type === 'CANVAS_DRAWING'")
          canvas.draw-canvas(
            :ref="el => setCanvasRef(q.uuid, el)"
            width="500" height="300"
            @mousedown="startDraw(q.uuid, $event)"
            @mousemove="moveDraw(q.uuid, $event)"
            @mouseup="endDraw(q.uuid)"
            @mouseleave="endDraw(q.uuid)"
            @touchstart.prevent="startDraw(q.uuid, $event)"
            @touchmove.prevent="moveDraw(q.uuid, $event)"
            @touchend.prevent="endDraw(q.uuid)"
          )
          button.btn-clear(type="button" @click="clearCanvas(q.uuid)") Borrar dibujo

        .drag-drop(v-else-if="q.type === 'DRAG_DROP'")
          ul.drag-list
            li.drag-item(
              v-for="(item, idx) in dragOrder[q.uuid]" :key="item"
              draggable="true"
              @dragstart="dragStart(q.uuid, idx)"
              @dragover.prevent
              @drop="dragDrop(q.uuid, idx)"
            )
              span.drag-handle ⠿
              span {{ item }}
              .drag-arrows
                button.btn-arrow(type="button" @click="moveItem(q.uuid, idx, -1)" :disabled="idx === 0") ↑
                button.btn-arrow(type="button" @click="moveItem(q.uuid, idx, 1)" :disabled="idx === dragOrder[q.uuid].length - 1") ↓

      button.btn-primary(type="submit" :disabled="submitting") {{ submitting ? 'Enviando...' : 'Enviar respuestas' }}
      p.survey-error(v-if="error") {{ error }}
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getQuestions, submitResponses } from '@/services/api/surveyApi'
import { getPresentationStatus, getPdfUrl } from '@/services/api/presentationsApi'
import { organizerContact } from '@/config/contact'

const ORDER_SEP = ';;'
const EMOJI_SCALE = ['😢', '😕', '😐', '🙂', '🤩']

export default {
  name: 'SurveyPage',
  props: { conferenceId: String },
  setup(props) {
    const route = useRoute()
    const friendlyId = route.params.friendlyId
    const questions = ref([])
    const loading = ref(true)
    const submitting = ref(false)
    const submitted = ref(false)
    const error = ref('')
    const answers = reactive({})
    const answersText = reactive({})
    const dragOrder = reactive({})
    const pdfReady = ref(false)
    const pdfUrl = ref('')
    const contact = organizerContact
    const emojiScale = EMOJI_SCALE

    const canvasRefs = {}
    const drawState = {}

    function setRating(questionUuid, n) {
      answers[questionUuid] = { rating: n }
    }

    function setCanvasRef(questionUuid, el) {
      if (el) canvasRefs[questionUuid] = el
    }

    function canvasPos(canvas, evt) {
      const rect = canvas.getBoundingClientRect()
      const point = evt.touches ? evt.touches[0] : evt
      return { x: point.clientX - rect.left, y: point.clientY - rect.top }
    }

    function startDraw(questionUuid, evt) {
      const canvas = canvasRefs[questionUuid]
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      const { x, y } = canvasPos(canvas, evt)
      drawState[questionUuid] = true
      ctx.beginPath()
      ctx.moveTo(x, y)
    }

    function moveDraw(questionUuid, evt) {
      if (!drawState[questionUuid]) return
      const canvas = canvasRefs[questionUuid]
      const ctx = canvas.getContext('2d')
      const { x, y } = canvasPos(canvas, evt)
      ctx.lineWidth = 2
      ctx.lineCap = 'round'
      ctx.strokeStyle = '#1e1b4b'
      ctx.lineTo(x, y)
      ctx.stroke()
    }

    function endDraw(questionUuid) {
      if (!drawState[questionUuid]) return
      drawState[questionUuid] = false
      const canvas = canvasRefs[questionUuid]
      answersText[questionUuid] = canvas.toDataURL('image/png')
    }

    function clearCanvas(questionUuid) {
      const canvas = canvasRefs[questionUuid]
      if (!canvas) return
      canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height)
      answersText[questionUuid] = ''
    }

    let dragFromIndex = null
    function dragStart(questionUuid, idx) { dragFromIndex = idx }
    function dragDrop(questionUuid, toIdx) {
      if (dragFromIndex === null) return
      const list = dragOrder[questionUuid]
      const [moved] = list.splice(dragFromIndex, 1)
      list.splice(toIdx, 0, moved)
      dragFromIndex = null
    }
    function moveItem(questionUuid, idx, delta) {
      const list = dragOrder[questionUuid]
      const newIdx = idx + delta
      if (newIdx < 0 || newIdx >= list.length) return
      const [item] = list.splice(idx, 1)
      list.splice(newIdx, 0, item)
    }

    async function load() {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const res = await getQuestions(props.conferenceId)
        questions.value = res.data || []
        for (const q of questions.value) {
          if (q.type === 'DRAG_DROP') dragOrder[q.uuid] = [...(q.options || [])]
        }
      } catch (e) { questions.value = [] }
      finally { loading.value = false }

      try {
        const status = await getPresentationStatus(props.conferenceId)
        pdfReady.value = !!status.ready
        if (pdfReady.value) pdfUrl.value = getPdfUrl(props.conferenceId)
      } catch (e) { pdfReady.value = false }
    }

    async function submit() {
      error.value = ''
      submitting.value = true
      try {
        const payload = questions.value.map((q) => {
          if (q.type === 'DRAG_DROP') {
            return { questionUuid: q.uuid, text: (dragOrder[q.uuid] || []).join(ORDER_SEP), rating: null }
          }
          return {
            questionUuid: q.uuid,
            text: answersText[q.uuid] || null,
            rating: answers[q.uuid]?.rating || null
          }
        })
        await submitResponses(props.conferenceId, payload)
        submitted.value = true
      } catch (e) {
        error.value = 'No se pudo enviar tu encuesta. Intenta de nuevo.'
      } finally {
        submitting.value = false
      }
    }

    onMounted(load)

    return {
      friendlyId, questions, loading, submitting, submitted, error,
      answers, answersText, dragOrder, pdfReady, pdfUrl, contact, emojiScale, setRating, submit,
      setCanvasRef, startDraw, moveDraw, endDraw, clearCanvas,
      dragStart, dragDrop, moveItem
    }
  }
}
</script>

<style scoped>
.survey-page { padding: 24px; max-width: 640px; margin: 0 auto; }
h2 { color: #1e1b4b; margin-bottom: 16px; }
.survey-loading, .survey-empty { text-align: center; color: #6b7280; padding: 60px; }
.question { margin-bottom: 24px; }
.question label { display: block; font-weight: 600; color: #1e1b4b; margin-bottom: 8px; }
textarea {
  width: 100%; padding: 10px 12px; border: 1.5px solid #d1d5db; border-radius: 8px;
  font-size: 0.95rem; font-family: inherit; resize: vertical;
}
.code-input { font-family: 'SF Mono', Consolas, monospace; font-size: 0.85rem; background: #1e1b4b; color: #e0e7ff; }
.rating { display: flex; gap: 4px; }
.star {
  background: none; border: none; font-size: 2rem; color: #d1d5db; cursor: pointer; line-height: 1;
}
.star.active { color: #f59e0b; }
.emoji-rating .emoji {
  background: none; border: none; font-size: 1.8rem; cursor: pointer; line-height: 1; opacity: 0.4;
  filter: grayscale(60%); transition: all 0.15s;
}
.emoji-rating .emoji.active { opacity: 1; filter: none; transform: scale(1.2); }
.choices { display: flex; flex-direction: column; gap: 8px; }
.choice { display: flex; align-items: center; gap: 8px; font-weight: 400; cursor: pointer; }
.survey-error { color: #dc2626; margin-top: 12px; }
.btn-primary {
  padding: 12px 24px; border: none; border-radius: 8px; background: #4f46e5; color: #fff;
  font-weight: 600; font-size: 0.95rem; cursor: pointer; text-decoration: none; display: inline-block;
}
.btn-primary:hover { background: #4338ca; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

.canvas-wrap { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.draw-canvas { border: 1.5px solid #d1d5db; border-radius: 8px; background: #fff; touch-action: none; max-width: 100%; }
.btn-clear {
  padding: 6px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #6b7280;
  cursor: pointer; font-size: 0.82rem;
}

.drag-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
.drag-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px; background: #fff;
  border: 1.5px solid #e5e7eb; border-radius: 8px; cursor: grab; font-weight: 500;
}
.drag-handle { color: #9ca3af; }
.drag-arrows { margin-left: auto; display: flex; gap: 4px; }
.btn-arrow {
  width: 26px; height: 26px; border: 1px solid #e5e7eb; border-radius: 6px; background: #f9fafb;
  cursor: pointer; font-size: 0.8rem;
}
.btn-arrow:disabled { opacity: 0.3; cursor: not-allowed; }

.thank-you { text-align: center; padding: 24px 0; }
.contact-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 24px; margin: 24px 0;
}
.contact-card h3 { margin: 0 0 8px; color: #1e1b4b; }
.contact-name { font-weight: 600; color: #4f46e5; margin-bottom: 12px; }
.contact-links { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.contact-links a { color: #4f46e5; text-decoration: none; font-weight: 500; }
.contact-links a:hover { text-decoration: underline; }
</style>
