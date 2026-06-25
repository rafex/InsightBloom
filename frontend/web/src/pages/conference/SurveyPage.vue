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
        .rating(v-if="q.type === 'RATING'")
          button.star(
            type="button"
            v-for="n in 5" :key="n"
            :class="{ active: (answers[q.uuid]?.rating || 0) >= n }"
            @click="setRating(q.uuid, n)"
          ) ★
        textarea(v-else-if="q.type === 'TEXT'" v-model="answersText[q.uuid]" rows="3" placeholder="Escribe tu comentario...")
        .choices(v-else-if="q.type === 'MULTIPLE_CHOICE'")
          label.choice(v-for="opt in q.options" :key="opt")
            input(type="radio" :name="q.uuid" :value="opt" v-model="answersText[q.uuid]")
            span {{ opt }}
      button.btn-primary(type="submit" :disabled="submitting") {{ submitting ? 'Enviando...' : 'Enviar respuestas' }}
      p.survey-error(v-if="error") {{ error }}
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getQuestions, submitResponses } from '@/services/api/surveyApi'
import { getPresentationStatus, getPdfUrl } from '@/services/api/presentationsApi'
import { organizerContact } from '@/config/contact'

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
    const pdfReady = ref(false)
    const pdfUrl = ref('')
    const contact = organizerContact

    function setRating(questionUuid, n) {
      answers[questionUuid] = { rating: n }
    }

    async function load() {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const res = await getQuestions(props.conferenceId)
        questions.value = res.data || []
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
        const payload = questions.value.map((q) => ({
          questionUuid: q.uuid,
          text: answersText[q.uuid] || null,
          rating: answers[q.uuid]?.rating || null
        }))
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
      answers, answersText, pdfReady, pdfUrl, contact, setRating, submit
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
.rating { display: flex; gap: 4px; }
.star {
  background: none; border: none; font-size: 2rem; color: #d1d5db; cursor: pointer; line-height: 1;
}
.star.active { color: #f59e0b; }
.choices { display: flex; flex-direction: column; gap: 8px; }
.choice { display: flex; align-items: center; gap: 8px; font-weight: 400; cursor: pointer; }
.survey-error { color: #dc2626; margin-top: 12px; }
.btn-primary {
  padding: 12px 24px; border: none; border-radius: 8px; background: #4f46e5; color: #fff;
  font-weight: 600; font-size: 0.95rem; cursor: pointer; text-decoration: none; display: inline-block;
}
.btn-primary:hover { background: #4338ca; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

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
