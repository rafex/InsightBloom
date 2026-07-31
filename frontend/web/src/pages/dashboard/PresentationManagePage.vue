<template lang="pug">
.presentation-manage-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")
  h2 Presentación

  LoadingState(v-if="!checkedStatus" message="Consultando el estado de la presentación…")
  .presentation-content(v-else)
    .status-card
      StatusBadge.presentation-status(
        :status="ready ? 'ACTIVE' : 'INACTIVE'"
        :label="ready ? 'Presentación disponible' : 'Sin presentación'"
      )
      p.status-help(v-if="ready") Ya hay una presentación {{ provider === 'SLIDEV' ? 'Slidev' : 'Marp' }}{{ presentationFormat === 'fat' ? ' FAT precompilada' : '' }} generada para esta conferencia.
      p.status-help(v-else) Aún no se ha subido una presentación.
      .preview-actions(v-if="ready")
        BaseAnchor(variant="secondary" :href="publicSlidesUrl || slidesUrl" target="_blank" rel="noopener") Ver slides
        BaseAnchor(variant="secondary" :href="pdfUrl" target="_blank" rel="noopener") Descargar PDF

    .upload-card
      h3 Subir presentación (.zip)
      .form-group
        label Engine de la presentación
        select.provider-select(v-model="provider")
          option(value="MARP") Marp
          option(value="SLIDEV") Slidev
        p.field-hint(v-if="provider === 'MARP'") ZIP con el Markdown de Marp y sus assets locales (CSS, imágenes y fuentes).
        p.field-hint(v-else) ZIP fuente con slides.md y assets locales, o ZIP FAT con slidev-artifact.json y dist/ precompilado. El FAT se detecta automáticamente y sólo se acepta si la auditoría está habilitada.
      p.hint El engine seleccionado se usará para generar la vista pública, el modo Presentar y las exportaciones.
      input(type="file" accept=".zip" @change="onFileChange" ref="fileInput")
      .form-group
        label URL del repositorio o descarga (opcional)
        input.source-input(v-model="sourceUrl" type="url" placeholder="https://github.com/usuario/repo")
        p.field-hint Si la agregas, la audiencia verá un botón para ir al sitio de origen de la presentación.
      BaseButton(:disabled="!file || uploading" :loading="uploading" @click="upload") Subir y generar
      FeedbackMessage(v-if="error" :message="error" tone="error")
      FeedbackMessage(v-if="success" message="¡Presentación generada correctamente!" tone="success")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { uploadPresentation, getPresentationStatus, getSlidesUrl, getPresentationRootUrl, getPdfUrl, primePresentationAccess } from '@/services/api/presentationsApi'
import type { PresentationFormat, PresentationProvider } from '@/services/api/presentationsApi'
import { getConference, updateConference } from '@/services/api/usersApi'
import type { Conference } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseAnchor from '@/components/ui/BaseAnchor.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

export default {
  name: 'PresentationManagePage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, BaseAnchor, BaseButton, FeedbackMessage, LoadingState, StatusBadge },
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
    const publicSlidesUrl = ref('')
    const conferenceFriendlyId = ref('')
    const pdfUrl = ref('')
    const provider = ref<PresentationProvider>('MARP')
    const presentationFormat = ref<PresentationFormat>('source')
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
        provider.value = status.provider === 'SLIDEV' ? 'SLIDEV' : 'MARP'
        presentationFormat.value = status.presentationFormat === 'fat' ? 'fat' : 'source'
        if (ready.value) {
          await primePresentationAccess(props.conferenceId as string, auth.state.token as string, true)
          slidesUrl.value = provider.value === 'SLIDEV'
            ? getPresentationRootUrl(props.conferenceId as string)
            : getSlidesUrl(props.conferenceId as string)
          pdfUrl.value = getPdfUrl(props.conferenceId as string)
        }
      } catch (e: any) { ready.value = false }
      finally { checkedStatus.value = true }
    }

    async function loadConference() {
      try {
        conference = await getConference(props.conferenceId as string, auth.state.token as string)
        sourceUrl.value = (conference?.presentationSourceUrl as string) || ''
        conferenceName.value = conference?.name || ''
        conferenceFriendlyId.value = conference?.friendlyId || ''
        publicSlidesUrl.value = conferenceFriendlyId.value
          ? `/c/${conferenceFriendlyId.value}/presentation`
          : ''
      } catch (e: any) { /* el campo simplemente queda vacío */ }
    }

    async function upload() {
      if (!file.value) return
      uploading.value = true
      error.value = ''
      success.value = false
      try {
        await uploadPresentation(props.conferenceId as string, file.value, auth.state.token as string, provider.value)
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
        const status = e.response?.status
        const backendMessage = e.response?.data?.message || e.response?.data?.error
        error.value = status === 504
          ? 'La generación tardó demasiado. El servidor sigue protegido contra cargas concurrentes; inténtalo nuevamente.'
          : backendMessage || (status === 400
            ? 'El ZIP no cumple el formato de la presentación seleccionada.'
            : 'No se pudo generar la presentación. Revisa los logs del servicio.')
      } finally {
        uploading.value = false
      }
    }

    onMounted(() => {
      refreshStatus()
      loadConference()
    })

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: !conferenceName.value },
      { label: 'Presentación' }
    ])

    return {
      file, uploading, error, success, checkedStatus, ready, slidesUrl, publicSlidesUrl, pdfUrl,
      provider, presentationFormat, sourceUrl, onFileChange, upload, breadcrumbItems
    }
  }
}
</script>

<style scoped>
.presentation-manage-page { padding: 24px; max-width: 600px; }
h2 { color: var(--color-heading); margin-bottom: 20px; }
.status-card, .upload-card {
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 20px; margin-bottom: 20px;
}
.presentation-status { margin-bottom: 6px; }
.status-help { color: var(--color-text-muted); margin: 0 0 8px; }
h3 { margin: 0 0 8px; color: var(--color-heading); }
.hint { color: var(--color-text-muted); font-size: 0.88rem; margin-bottom: 12px; }
input[type="file"] { display: block; margin-bottom: 12px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-weight: 600; font-size: 0.88rem; color: var(--color-text-secondary); margin-bottom: 6px; }
.source-input {
  width: 100%; padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px;
  font-size: 0.95rem; box-sizing: border-box;
}
.provider-select {
  width: 100%; padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px;
  font-size: 0.95rem; box-sizing: border-box; background: var(--color-surface);
}
.source-input:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; border-color: var(--color-primary); }
.field-hint { margin: 6px 0 0; font-size: 0.8rem; }
.preview-actions { margin-top: 8px; }

@media (max-width: 480px) {
  .presentation-manage-page { padding: 14px; }
  .preview-actions { display: flex; flex-direction: column; gap: 8px; }
  .preview-actions > :where(.base-anchor) { text-align: center; }
}
</style>
