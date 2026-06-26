<template lang="pug">
.presentation-manage-page
  ConferenceSubNav(:conferenceId="conferenceId")
  h2 Presentación

  .status-card(v-if="checkedStatus")
    p(v-if="ready") ✅ Ya hay una presentación generada para esta conferencia.
    p(v-else) Aún no se ha subido una presentación.
    .preview-actions(v-if="ready")
      a.btn-secondary(:href="slidesUrl" target="_blank" rel="noopener") Ver slides
      a.btn-secondary(:href="pdfUrl" target="_blank" rel="noopener") Descargar PDF

  .upload-card
    h3 Subir presentación (.zip)
    p.hint Sube un ZIP con tu archivo Markdown de Marp, la carpeta assets/css/theme.css y assets/images. Se generará el slideshow y el PDF distribuible.
    input(type="file" accept=".zip" @change="onFileChange" ref="fileInput")
    button.btn-primary(:disabled="!file || uploading" @click="upload")
      | {{ uploading ? 'Procesando (puede tardar)...' : 'Subir y generar' }}
    p.upload-error(v-if="error") {{ error }}
    p.upload-success(v-if="success") ¡Presentación generada correctamente!
</template>

<script>
import { ref, onMounted } from 'vue'
import { uploadPresentation, getPresentationStatus, getSlidesUrl, getPdfUrl } from '@/services/api/presentationsApi'
import { useAuthStore } from '@/features/auth/authStore'
import ConferenceSubNav from './ConferenceSubNav.vue'

export default {
  name: 'PresentationManagePage',
  components: { ConferenceSubNav },
  props: { conferenceId: String },
  setup(props) {
    const auth = useAuthStore()
    const file = ref(null)
    const uploading = ref(false)
    const error = ref('')
    const success = ref(false)
    const checkedStatus = ref(false)
    const ready = ref(false)
    const slidesUrl = ref('')
    const pdfUrl = ref('')

    function onFileChange(e) {
      file.value = e.target.files[0] || null
      success.value = false
      error.value = ''
    }

    async function refreshStatus() {
      try {
        const status = await getPresentationStatus(props.conferenceId)
        ready.value = !!status.ready
        if (ready.value) {
          slidesUrl.value = getSlidesUrl(props.conferenceId)
          pdfUrl.value = getPdfUrl(props.conferenceId)
        }
      } catch (e) { ready.value = false }
      finally { checkedStatus.value = true }
    }

    async function upload() {
      if (!file.value) return
      uploading.value = true
      error.value = ''
      success.value = false
      try {
        await uploadPresentation(props.conferenceId, file.value, auth.state.token)
        success.value = true
        await refreshStatus()
      } catch (e) {
        error.value = e.response?.data?.message || 'No se pudo generar la presentación. Verifica que el ZIP tenga un archivo .md.'
      } finally {
        uploading.value = false
      }
    }

    onMounted(refreshStatus)

    return { file, uploading, error, success, checkedStatus, ready, slidesUrl, pdfUrl, onFileChange, upload }
  }
}
</script>

<style scoped>
.presentation-manage-page { padding: 24px; max-width: 600px; }
h2 { color: #1e1b4b; margin-bottom: 20px; }
.status-card, .upload-card {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
h3 { margin: 0 0 8px; color: #1e1b4b; }
.hint { color: #6b7280; font-size: 0.88rem; margin-bottom: 12px; }
input[type="file"] { display: block; margin-bottom: 12px; }
.btn-primary {
  padding: 10px 20px; border: none; border-radius: 8px; background: #4f46e5; color: #fff;
  font-weight: 600; cursor: pointer;
}
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary {
  padding: 8px 16px; border-radius: 8px; background: #eef2ff; color: #4f46e5; border: 2px solid #c7d2fe;
  text-decoration: none; font-weight: 600; font-size: 0.88rem; margin-right: 8px;
}
.preview-actions { margin-top: 8px; }
.upload-error { color: #dc2626; margin-top: 10px; }
.upload-success { color: #16a34a; margin-top: 10px; }
</style>
