<template lang="pug">
.presentation-page
  .presentation-header
    h2 Presentación
  .presentation-loading(v-if="loading") Verificando presentación...
  .presentation-empty(v-else-if="!ready")
    p El organizador aún no ha subido la presentación de esta conferencia.
  template(v-else)
    iframe.slides-frame(:src="slidesUrl" title="Slides")
    .presentation-actions
      a.btn-primary(:href="pdfUrl" target="_blank" rel="noopener") Descargar PDF
      router-link.btn-secondary(:to="`/c/${friendlyId}/survey`") Dar mi opinión sobre la charla →
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPresentationStatus, getSlidesUrl, getPdfUrl } from '@/services/api/presentationsApi'

export default {
  name: 'PresentationPage',
  props: { conferenceId: String },
  setup(props) {
    const route = useRoute()
    const friendlyId = route.params.friendlyId
    const loading = ref(true)
    const ready = ref(false)
    const slidesUrl = ref('')
    const pdfUrl = ref('')

    onMounted(async () => {
      if (!props.conferenceId) { loading.value = false; return }
      try {
        const status = await getPresentationStatus(props.conferenceId)
        ready.value = !!status.ready
        if (ready.value) {
          slidesUrl.value = getSlidesUrl(props.conferenceId)
          pdfUrl.value = getPdfUrl(props.conferenceId)
        }
      } catch (e) { ready.value = false }
      finally { loading.value = false }
    })

    return { friendlyId, loading, ready, slidesUrl, pdfUrl }
  }
}
</script>

<style scoped>
.presentation-page { padding: 24px; }
.presentation-header { margin-bottom: 16px; }
h2 { margin: 0; color: #1e1b4b; }
.presentation-loading, .presentation-empty { text-align: center; color: #6b7280; padding: 60px; }
.slides-frame {
  width: 100%;
  height: 70vh;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}
.presentation-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.btn-primary, .btn-secondary {
  padding: 10px 20px;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
}
.btn-primary { background: #4f46e5; color: #fff; }
.btn-primary:hover { background: #4338ca; }
.btn-secondary { background: #eef2ff; color: #4f46e5; border: 2px solid #c7d2fe; }
.btn-secondary:hover { background: #e0e7ff; }
</style>
