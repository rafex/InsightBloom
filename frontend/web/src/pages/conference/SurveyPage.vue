<template lang="pug">
.survey-page
  template(v-if="submitted")
    .thank-you
      h2 ¡Gracias por tu retroalimentación! 🙌
      p Tu opinión ayuda a mejorar futuras charlas y talleres.

      .certificate-card
        h3 🎓 Tu certificado de asistencia
        p.cert-hint Se genera al momento, no se almacena. Puedes descargarlo cuando quieras volviendo a esta página.
        .cert-loading(v-if="certLoading") Generando certificado...
        template(v-else-if="certUrl")
          iframe.cert-preview(:src="certUrl")
          a.link-btn.link-btn-primary(:href="certUrl" :download="certFileName") Descargar certificado (PDF)
        template(v-else-if="certError")
          p.cert-error {{ certError }}
          router-link.btn-outline-link(v-if="certNeedsLogin" :to="{ path: '/login', query: { redirect: $route.fullPath } }") Iniciar sesión

      .contact-card
        h3 Sigamos en contacto
        p.contact-name {{ contact.name }}
        ul.contact-links
          li
            a(:href="`mailto:${contact.email}`") ✉️ {{ contact.email }}
          li
            a(:href="contact.website" target="_blank" rel="noopener") 🌐 {{ contact.website }}
          li
            a(:href="contact.linkedin" target="_blank" rel="noopener") 🔗 LinkedIn
          li
            a(:href="contact.linkedinNewsletter" target="_blank" rel="noopener") 📰 Newsletter en LinkedIn
          li
            a(:href="contact.github" target="_blank" rel="noopener") 🐙 GitHub
          li
            a(:href="contact.devto" target="_blank" rel="noopener") 👨‍💻 Dev.to
          li
            a(:href="contact.blog" target="_blank" rel="noopener") 📝 Blog
          li
            a(:href="telegramUrl" target="_blank" rel="noopener") 💬 Telegram {{ contact.telegram }}
          li
            a(:href="contact.telegramGroup" target="_blank" rel="noopener") 👥 Grupo de Telegram
      .download-actions
        a.link-btn.link-btn-primary(:href="pdfUrl" target="_blank" rel="noopener" v-if="pdfReady") Descargar presentación (PDF)
        BaseButton(type="button" v-if="isGroupNotes" :disabled="materialsDownloading" @click="downloadMaterials") {{ materialsDownloading ? 'Preparando materiales...' : 'Descargar materiales ZIP' }}
      p.cert-error(v-if="materialsError") {{ materialsError }}

  .login-required(v-else-if="!canParticipate")
    h2 Inicia sesión para responder la encuesta
    p Necesitas una cuenta verificada para participar.
    .login-actions
      router-link.btn-primary-link(:to="{ path: '/login', query: { redirect: $route.fullPath } }") Iniciar sesión
      router-link.btn-outline-link(:to="{ path: '/register', query: { redirect: $route.fullPath } }") Crear cuenta

  template(v-else)
    template(v-if="surveyLocked")
      .survey-locked
        h2 🔒 Encuesta bloqueada
        p El moderador todavía no ha liberado la encuesta.
        p Podrás responderla después de la charla y obtener tu certificado de asistencia.
    template(v-else)
      h2 Cuéntanos qué te pareció la charla
      .benefits-banner(v-if="!loading && (questions.length || engine === 'SURVEYJS')")
        span.benefits-icon 🎁
        span Al terminar el cuestionario obtienes: los datos de contacto del presentador, tu <strong>certificado de asistencia</strong> y la <strong>presentación en PDF</strong> para descargar.
      .survey-loading(v-if="loading") Cargando encuesta...
      .survey-empty(v-else-if="engine === 'SURVEYJS' && !surveyModel") Esta conferencia no tiene una encuesta SurveyJS publicada.
      SurveyComponent.surveyjs-form(v-else-if="engine === 'SURVEYJS' && surveyModel" :model="surveyModel")
      .survey-empty(v-else-if="!questions.length") Esta conferencia no tiene encuesta configurada.
      form.survey-form(v-else @submit.prevent="submit")
        .question(v-for="q in questions" :key="q.uuid")
          label {{ q.text }}
            span.required-mark(v-if="q.required") &nbsp;*

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
              input(type="checkbox" :value="opt" v-model="answersText[q.uuid]")
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
            BaseButton.btn-clear(variant="secondary" type="button" @click="clearCanvas(q.uuid)") Borrar dibujo

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
                  BaseButton.btn-arrow(variant="secondary" type="button" @click="moveItem(q.uuid, idx, -1)" :disabled="idx === 0") ↑
                  BaseButton.btn-arrow(variant="secondary" type="button" @click="moveItem(q.uuid, idx, 1)" :disabled="idx === dragOrder[q.uuid].length - 1") ↓

        BaseButton(type="submit" :disabled="submitting") {{ submitting ? 'Enviando...' : 'Enviar respuestas' }}
        p.survey-error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, reactive, shallowRef, computed, onMounted, type PropType } from 'vue'
import { useRoute } from 'vue-router'
import { Model } from 'survey-core'
import 'survey-core/i18n/spanish'
import { SurveyComponent } from 'survey-vue3-ui'
import BaseButton from '@/components/ui/BaseButton.vue'
import { getQuestions, submitResponses, getSurveyAccess, getSurveyDefinition, submitSurveyJs } from '@/services/api/surveyApi'
import { getPresentationStatus, getPdfUrl, primePresentationAccess } from '@/services/api/presentationsApi'
import { getCertificateBlobUrl, downloadEventMaterials } from '@/services/api/usersApi'
import { organizerContact, telegramContactUrl } from '@/config/contact'
import { useAuthStore } from '@/features/auth/authStore'

const ORDER_SEP = ';;'
const EMOJI_SCALE = ['😢', '😕', '😐', '🙂', '🤩']

interface SurveyQuestion {
  uuid: string
  text: string
  type: string
  options?: string[]
  required?: boolean
  ratingStyle?: string
}

type PointerLikeEvent = MouseEvent | TouchEvent

export default {
  name: 'SurveyPage',
  components: { SurveyComponent, BaseButton },
  props: {
    conferenceId: { type: String as PropType<string | undefined>, default: undefined },
    canvasAudienceMode: { type: String, default: '' }
  },
  setup(props: { conferenceId?: string, canvasAudienceMode?: string }) {
    const route = useRoute()
    const auth = useAuthStore()
    const canParticipate = auth.isAuthenticated() && auth.state.role !== 'guest'
    const friendlyId = route.params.friendlyId as string
    const questions = ref<SurveyQuestion[]>([])
    const engine = ref<'NATIVE' | 'SURVEYJS' | null>(null)
    const surveyModel = shallowRef<Model | null>(null)
    const loading = ref(true)
    const submitting = ref(false)
    const submitted = ref(false)
    const surveyLocked = ref(false)
    const error = ref('')
    const answers = reactive<Record<string, { rating: number }>>({})
    const answersText = reactive<Record<string, string | string[]>>({})
    const dragOrder = reactive<Record<string, string[]>>({})
    const pdfReady = ref(false)
    const pdfUrl = ref('')
    const contact = organizerContact
    const telegramUrl = telegramContactUrl(friendlyId || props.conferenceId || '')
    const emojiScale = EMOJI_SCALE
    const certLoading = ref(false)
    const certUrl = ref('')
    const certError = ref('')
    const certNeedsLogin = ref(false)
    const certFileName = `certificado-${friendlyId || props.conferenceId}.pdf`
    const materialsDownloading = ref(false)
    const materialsError = ref('')
    const isGroupNotes = computed(() => props.canvasAudienceMode !== 'INDEPENDENT')

    async function loadCertificate() {
      certLoading.value = true
      certError.value = ''
      certNeedsLogin.value = false
      try {
        certUrl.value = await getCertificateBlobUrl(props.conferenceId as string, auth.state.token as string)
      } catch (e: any) {
        const status = e?.response?.status
        let payload: any = e?.response?.data
        // Axios exposes error responses as Blob when the successful response is a PDF.
        if (payload instanceof Blob) {
          try { payload = JSON.parse(await payload.text()) } catch { payload = null }
        }
        const code = payload?.error?.code
        if (status === 401 || code === 'token_invalid' || code === 'certificate_user_required') {
          certNeedsLogin.value = true
          certError.value = 'Tu sesión no es válida para descargar el certificado. Inicia sesión nuevamente.'
        } else if (code === 'survey_not_completed') {
          certError.value = 'Completa la encuesta para generar tu certificado.'
        } else {
          certError.value = 'No se pudo generar tu certificado todavía. Intenta nuevamente en unos minutos.'
        }
      } finally {
        certLoading.value = false
      }
    }

    async function loadPresentationStatus() {
      try {
        const status = await getPresentationStatus(props.conferenceId as string)
        pdfReady.value = !!status.ready
        if (pdfReady.value) {
          await primePresentationAccess(props.conferenceId as string, auth.state.token as string)
          pdfUrl.value = getPdfUrl(props.conferenceId as string)
        }
      } catch (e: any) {
        pdfReady.value = false
        pdfUrl.value = ''
      }
    }

    async function downloadMaterials() {
      materialsDownloading.value = true
      materialsError.value = ''
      try {
        const blob = await downloadEventMaterials(props.conferenceId as string, auth.state.token as string)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = 'event-materials.zip'
        link.click()
        URL.revokeObjectURL(url)
      } catch (e: any) {
        materialsError.value = 'No se pudieron preparar los materiales del evento.'
      } finally {
        materialsDownloading.value = false
      }
    }

    const canvasRefs: Record<string, HTMLCanvasElement> = {}
    const drawState: Record<string, boolean> = {}

    function setRating(questionUuid: string, n: number) {
      answers[questionUuid] = { rating: n }
    }

    function setCanvasRef(questionUuid: string, el: Element | null) {
      if (el) canvasRefs[questionUuid] = el as HTMLCanvasElement
    }

    function canvasPos(canvas: HTMLCanvasElement, evt: PointerLikeEvent) {
      const rect = canvas.getBoundingClientRect()
      const point = 'touches' in evt ? evt.touches[0] : evt
      return { x: point.clientX - rect.left, y: point.clientY - rect.top }
    }

    function startDraw(questionUuid: string, evt: PointerLikeEvent) {
      const canvas = canvasRefs[questionUuid]
      if (!canvas) return
      const ctx = canvas.getContext('2d')!
      const { x, y } = canvasPos(canvas, evt)
      drawState[questionUuid] = true
      ctx.beginPath()
      ctx.moveTo(x, y)
    }

    function moveDraw(questionUuid: string, evt: PointerLikeEvent) {
      if (!drawState[questionUuid]) return
      const canvas = canvasRefs[questionUuid]
      const ctx = canvas.getContext('2d')!
      const { x, y } = canvasPos(canvas, evt)
      ctx.lineWidth = 2
      ctx.lineCap = 'round'
      ctx.strokeStyle = '#1e1b4b'
      ctx.lineTo(x, y)
      ctx.stroke()
    }

    function endDraw(questionUuid: string) {
      if (!drawState[questionUuid]) return
      drawState[questionUuid] = false
      const canvas = canvasRefs[questionUuid]
      answersText[questionUuid] = canvas.toDataURL('image/png')
    }

    function clearCanvas(questionUuid: string) {
      const canvas = canvasRefs[questionUuid]
      if (!canvas) return
      canvas.getContext('2d')!.clearRect(0, 0, canvas.width, canvas.height)
      answersText[questionUuid] = ''
    }

    let dragFromIndex: number | null = null
    function dragStart(questionUuid: string, idx: number) { dragFromIndex = idx }
    function dragDrop(questionUuid: string, toIdx: number) {
      if (dragFromIndex === null) return
      const list = dragOrder[questionUuid]
      const [moved] = list.splice(dragFromIndex, 1)
      list.splice(toIdx, 0, moved)
      dragFromIndex = null
    }
    function moveItem(questionUuid: string, idx: number, delta: number) {
      const list = dragOrder[questionUuid]
      const newIdx = idx + delta
      if (newIdx < 0 || newIdx >= list.length) return
      const [item] = list.splice(idx, 1)
      list.splice(newIdx, 0, item)
    }

    async function load() {
      if (!props.conferenceId) { loading.value = false; return }

      if (canParticipate) {
        try {
          const access = (await getSurveyAccess(props.conferenceId, auth.state.token as string)).data
          if (access.responded) {
            submitted.value = true
            loading.value = false
            await loadCertificate()
            await loadPresentationStatus()
            return
          }
          if (!access.released) {
            surveyLocked.value = true
            loading.value = false
            await loadPresentationStatus()
            return
          }
        } catch (e: any) {
          // El candado debe fallar cerrado: si no podemos consultar el permiso,
          // no mostramos un formulario que luego no podrá enviarse.
          surveyLocked.value = true
          loading.value = false
          await loadPresentationStatus()
          return
        }
      }

      try {
        const definition = (await getSurveyDefinition(props.conferenceId)).data
        engine.value = definition.engine
        if (definition.engine === 'SURVEYJS' && definition.schema) {
          const model = new Model(definition.schema)
          // SurveyJS usa el idioma del modelo para sus textos internos. El
          // contenido de las preguntas ya viene del moderador y se conserva.
          model.locale = 'es'
          model.completeText = 'Enviar respuestas'
          model.onComplete.add(async (sender) => { await submitSurveyJsForm(sender.data) })
          surveyModel.value = model
        } else if (definition.engine !== 'SURVEYJS') {
          const res = await getQuestions(props.conferenceId)
          questions.value = res.data || []
          for (const q of questions.value) {
            if (q.type === 'DRAG_DROP') dragOrder[q.uuid] = [...(q.options || [])]
            else if (q.type === 'MULTIPLE_CHOICE') answersText[q.uuid] = []
          }
        }
      } catch (e: any) { questions.value = []; surveyModel.value = null }
      finally { loading.value = false }

      await loadPresentationStatus()
    }

    function isAnswered(q: SurveyQuestion): boolean {
      if (q.type === 'DRAG_DROP') return true
      if (q.type === 'MULTIPLE_CHOICE') return ((answersText[q.uuid] as string[]) || []).length > 0
      if (q.type === 'RATING') return !!answers[q.uuid]?.rating
      const text = answersText[q.uuid] as string
      return !!(text && text.trim())
    }

    async function submit() {
      error.value = ''
      const missing = questions.value.filter((q) => q.required && !isAnswered(q))
      if (missing.length) {
        error.value = `Falta responder: ${missing.map((q) => q.text).join(', ')}`
        return
      }
      submitting.value = true
      try {
        const payload = questions.value.map((q) => {
          if (q.type === 'DRAG_DROP') {
            return { questionUuid: q.uuid, text: (dragOrder[q.uuid] || []).join(ORDER_SEP), rating: null }
          }
          if (q.type === 'MULTIPLE_CHOICE') {
            const selected = (answersText[q.uuid] as string[]) || []
            return { questionUuid: q.uuid, text: selected.length ? JSON.stringify(selected) : null, rating: null }
          }
          return {
            questionUuid: q.uuid,
            text: (answersText[q.uuid] as string) || null,
            rating: answers[q.uuid]?.rating || null
          }
        })
        await submitResponses(props.conferenceId as string, payload, auth.state.token as string)
        submitted.value = true
        await loadCertificate()
        await loadPresentationStatus()
      } catch (e: any) {
        if (e.response?.status === 423 || e.response?.data?.error?.code === 'survey_locked') {
          surveyLocked.value = true
        } else if (e.response?.status === 409) {
          error.value = 'Ya habías respondido esta encuesta.'
        } else if (e.response?.data?.error?.code === 'required_question_missing') {
          error.value = 'Falta responder alguna pregunta obligatoria.'
        } else {
          error.value = 'No se pudo enviar tu encuesta. Intenta de nuevo.'
        }
      } finally {
        submitting.value = false
      }
    }

    async function submitSurveyJsForm(data: Record<string, unknown>) {
      if (submitting.value) return
      error.value = ''
      submitting.value = true
      try {
        await submitSurveyJs(props.conferenceId as string, data, auth.state.token as string)
        submitted.value = true
        await loadCertificate()
        await loadPresentationStatus()
      } catch (e: any) {
        if (e.response?.status === 423 || e.response?.data?.error?.code === 'survey_locked') {
          surveyLocked.value = true
          return
        }
        error.value = e.response?.status === 409
          ? 'Ya habías respondido esta encuesta.'
          : 'No se pudo enviar tu encuesta. Intenta de nuevo.'
      } finally {
        submitting.value = false
      }
    }

    onMounted(load)

    return {
      friendlyId, questions, loading, submitting, submitted, surveyLocked, error, canParticipate, engine, surveyModel,
      answers, answersText, dragOrder, pdfReady, pdfUrl, contact, telegramUrl, emojiScale, setRating, submit,
      setCanvasRef, startDraw, moveDraw, endDraw, clearCanvas,
      dragStart, dragDrop, moveItem,
      certLoading, certUrl, certError, certNeedsLogin, certFileName, isGroupNotes,
      materialsDownloading, materialsError, downloadMaterials
    }
  }
}
</script>

<style scoped>
.survey-page { padding: 24px; max-width: 640px; margin: 0 auto; }
h2 { color: #1e1b4b; margin-bottom: 16px; }
.survey-loading, .survey-empty { text-align: center; color: #6b7280; padding: 60px; }
.survey-locked {
  text-align: center; background: #fff; border: 1px solid #fde68a; border-radius: 12px;
  color: #92400e; padding: 48px 24px; margin: 24px 0;
}
.survey-locked h2 { color: #92400e; }
.survey-locked p { margin: 8px 0; }
.benefits-banner {
  display: flex; align-items: center; gap: 10px; background: #fef3c7; color: #92400e;
  border-radius: 10px; padding: 12px 16px; margin-bottom: 20px; font-size: 0.88rem; line-height: 1.4;
}
.benefits-icon { font-size: 1.3rem; flex-shrink: 0; }
.login-required { text-align: center; padding: 60px 24px; }
.login-required p { color: #6b7280; margin-bottom: 24px; }
.login-actions { display: flex; gap: 10px; justify-content: center; }
.btn-primary-link, .btn-outline-link {
  padding: 10px 22px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 0.95rem;
}
.btn-primary-link { background: #4f46e5; color: #fff; }
.btn-outline-link { border: 1.5px solid #4f46e5; color: #4f46e5; }
.question { margin-bottom: 24px; }
.question label { display: block; font-weight: 600; color: #1e1b4b; margin-bottom: 8px; }
.required-mark { color: #dc2626; }
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
.download-actions { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }

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
.drag-handle { color: var(--color-text-muted); }
.drag-arrows { margin-left: auto; display: flex; gap: 4px; }
.btn-arrow {
  width: 26px; height: 26px; border: 1px solid #e5e7eb; border-radius: 6px; background: #f9fafb;
  cursor: pointer; font-size: 0.8rem;
}
.btn-arrow:disabled { opacity: 0.3; cursor: not-allowed; }

.thank-you { text-align: center; padding: 24px 0; }
.certificate-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 24px; margin: 24px 0;
}
.certificate-card h3 { margin: 0 0 8px; color: #1e1b4b; }
.cert-hint { color: #6b7280; font-size: 0.85rem; margin: 0 0 16px; }
.cert-loading { color: #6b7280; padding: 20px; }
.cert-error { color: #dc2626; font-size: 0.9rem; }
.cert-preview { width: 100%; height: 360px; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 16px; }
.contact-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 24px; margin: 24px 0;
}
.contact-card h3 { margin: 0 0 8px; color: #1e1b4b; }
.contact-name { font-weight: 600; color: #4f46e5; margin-bottom: 12px; }
.contact-links { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.contact-links a { color: #4f46e5; text-decoration: none; font-weight: 500; }
.contact-links a:hover { text-decoration: underline; }

@media (max-width: 640px) {
  .survey-page { padding: 14px; }
  .star { font-size: 1.6rem; }
  .emoji-rating .emoji { font-size: 1.5rem; }
}
</style>
