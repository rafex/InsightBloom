<template lang="pug">
.presentation-manage-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")
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
    .form-group
      label URL del repositorio o descarga (opcional)
      input.source-input(v-model="sourceUrl" type="url" placeholder="https://github.com/usuario/repo")
      p.field-hint Si la agregas, la audiencia verá un botón para ir al sitio de origen de la presentación.
    button.btn-primary(:disabled="!file || uploading" @click="upload")
      | {{ uploading ? 'Procesando (puede tardar)...' : 'Subir y generar' }}
    p.upload-error(v-if="error") {{ error }}
    p.upload-success(v-if="success") ¡Presentación generada correctamente!
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { uploadPresentation, getPresentationStatus, getSlidesUrl, getPdfUrl } from '@/services/api/presentationsApi'
import { getConference, updateConference } from '@/services/api/usersApi'
import type { Conference } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'

export default {
  name: 'PresentationManagePage',
  components: { DashboardBreadcrumb, ConferenceToolsNav },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const file = ref<File | null>(null)
    const uploading = ref(false)
    const error = ref('')
    const success = ref(false)
    const checkedStatus = ref(false)
    const ready = ref(false)
    const slidesUrl = ref('')
    const pdfUrl = ref('')
    const sourceUrl = ref('')
    const conferenceName = ref('')
    let conference: Conference | null = null

    function onFileChange(e: Event) {
      file.value = (e.target as HTMLInputElement).files?.[0] || null
      success.value = false
      error.value = ''
    }

    async function refreshStatus() {
      try {
        const status = await getPresentationStatus(props.conferenceId as string)
        ready.value = !!status.ready
        if (ready.value) {
          slidesUrl.value = getSlidesUrl(props.conferenceId as string, auth.state.token)
          pdfUrl.value = getPdfUrl(props.conferenceId as string, auth.state.token)
        }
      } catch (e: any) { ready.value = false }
      finally { checkedStatus.value = true }
    }

    async function loadConference() {
      try {
        conference = await getConference(props.conferenceId as string, auth.state.token as string)
        sourceUrl.value = (conference?.presentationSourceUrl as string) || ''
        conferenceName.value = conference?.name || ''
      } catch (e: any) { /* el campo simplemente queda vacío */ }
    }

    async function upload() {
      if (!file.value) return
      uploading.value = true
      error.value = ''
      success.value = false
      try {
        await uploadPresentation(props.conferenceId as string, file.value, auth.state.token as string)
        success.value = true
        await refreshStatus()
        // Actualiza la URL de origen junto con el resto de campos ya existentes de la
        // conferencia (la API sobreescribe todo el conjunto, no solo el campo nuevo).
        if (conference) {
          await updateConference(props.conferenceId as string, {
            venue: conference.venue,
            eventDate: conference.eventDate,
            startTime: conference.startTime,
            endTime: conference.endTime,
            latitude: conference.latitude,
            longitude: conference.longitude,
            presentationSourceUrl: sourceUrl.value.trim() || null
          }, auth.state.token as string)
        }
      } catch (e: any) {
        error.value = e.response?.data?.message || 'No se pudo generar la presentación. Verifica que el ZIP tenga un archivo .md.'
      } finally {
        uploading.value = false
      }
    }

    onMounted(() => {
      refreshStatus()
      loadConference()
    })

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Presentación' }
    ])

    return {
      file, uploading, error, success, checkedStatus, ready, slidesUrl, pdfUrl,
      sourceUrl, onFileChange, upload, breadcrumbItems
    }
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
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-weight: 600; font-size: 0.88rem; color: #374151; margin-bottom: 6px; }
.source-input {
  width: 100%; padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px;
  font-size: 0.95rem; box-sizing: border-box;
}
.source-input:focus { outline: none; border-color: #4f46e5; }
.field-hint { margin: 6px 0 0; font-size: 0.8rem; color: #9ca3af; }
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

@media (max-width: 480px) {
  .presentation-manage-page { padding: 14px; }
  .preview-actions { display: flex; flex-direction: column; gap: 8px; }
  .btn-secondary { margin-right: 0; text-align: center; }
}
</style>
