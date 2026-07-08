<template lang="pug">
.cert-settings-page
  h2 Diseño del certificado
  p.hint Personaliza el certificado de asistencia que reciben los participantes al terminar la encuesta. Los cambios aplican a todas las conferencias.

  .loading(v-if="loading") Cargando...
  template(v-else)
    .layout
      .form-col
        .form-group
          label Logotipo (opcional)
          input(type="file" accept="image/png,image/jpeg" @change="onLogoChange")
          button.btn-ghost-sm(v-if="form.logoBase64" type="button" @click="form.logoBase64 = ''") Quitar logotipo

        .form-group
          label Tipo de letra
          select(v-model="form.fontFamily")
            option(value="HELVETICA") Helvetica (sans-serif)
            option(value="TIMES_ROMAN") Times Roman (serif)
            option(value="COURIER") Courier (monoespaciada)

        .form-group
          label Tamaño del título: {{ form.titleFontSize }}pt
          input(type="range" min="14" max="48" v-model.number="form.titleFontSize")

        .form-group
          label Tamaño del texto: {{ form.bodyFontSize }}pt
          input(type="range" min="8" max="24" v-model.number="form.bodyFontSize")

        .form-group
          label Color principal
          input(type="color" v-model="form.primaryColorHex")

        .form-group
          label.options-label Datos a mostrar
          label.checkbox-row
            input(type="checkbox" v-model="form.showVenue")
            span Sede de la conferencia
          label.checkbox-row
            input(type="checkbox" v-model="form.showSchedule")
            span Fecha y horario del evento
          label.checkbox-row
            input(type="checkbox" v-model="form.showIssuedDate")
            span Fecha de emisión del certificado

        .error(v-if="error") {{ error }}
        .success(v-if="success") ¡Configuración guardada!
        button.btn-primary(@click="save" :disabled="saving")
          span(v-if="saving") Guardando...
          span(v-else) Guardar configuración

      .preview-col
        h3 Vista previa
        .preview-card(:style="{ borderColor: form.primaryColorHex }")
          img.preview-logo(v-if="form.logoBase64" :src="form.logoBase64")
          h4(:style="{ color: form.primaryColorHex, fontSize: form.titleFontSize + 'px', fontFamily: previewFontFamily }") Certificado de Asistencia
          p(:style="{ fontSize: form.bodyFontSize + 'px', fontFamily: previewFontFamily }") Se otorga el presente certificado a
          h5(:style="{ color: form.primaryColorHex, fontSize: (form.titleFontSize * 0.78) + 'px', fontFamily: previewFontFamily }") Nombre del Asistente
          p(:style="{ fontSize: form.bodyFontSize + 'px', fontFamily: previewFontFamily }") por su asistencia a la conferencia
          h5(:style="{ color: form.primaryColorHex, fontSize: (form.titleFontSize * 0.68) + 'px', fontFamily: previewFontFamily }") Nombre de la Conferencia
          p.preview-meta(v-if="form.showSchedule" :style="{ fontFamily: previewFontFamily }") Fecha del evento: 2026-06-26 (09:00 - 13:00)
          p.preview-meta(v-if="form.showVenue" :style="{ fontFamily: previewFontFamily }") Sede: Auditorio Principal
          p.preview-meta(v-if="form.showIssuedDate" :style="{ fontFamily: previewFontFamily }") Fecha de emisión: 26/06/2026
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getCertificateSettings, saveCertificateSettings } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

const FONT_MAP = { HELVETICA: 'Helvetica, Arial, sans-serif', TIMES_ROMAN: '"Times New Roman", serif', COURIER: '"Courier New", monospace' }

export default {
  name: 'CertificateSettingsPage',
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const saving = ref(false)
    const error = ref('')
    const success = ref(false)
    const form = ref({
      logoBase64: '', fontFamily: 'HELVETICA', titleFontSize: 28, bodyFontSize: 14,
      primaryColorHex: '#1e1b4b', showVenue: true, showSchedule: true, showIssuedDate: true
    })

    const previewFontFamily = computed(() => FONT_MAP[form.value.fontFamily] || FONT_MAP.HELVETICA)

    onMounted(async () => {
      try {
        const settings = await getCertificateSettings()
        form.value = { ...form.value, ...settings, logoBase64: settings.logoBase64 || '' }
      } catch (e: any) {
        error.value = 'No se pudo cargar la configuración.'
      } finally {
        loading.value = false
      }
    })

    function onLogoChange(evt) {
      const file = evt.target.files?.[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = () => { form.value.logoBase64 = reader.result as string }
      reader.readAsDataURL(file)
    }

    async function save() {
      error.value = ''
      success.value = false
      saving.value = true
      try {
        await saveCertificateSettings(form.value, auth.state.token)
        success.value = true
      } catch (e: any) {
        error.value = 'No se pudo guardar la configuración.'
      } finally {
        saving.value = false
      }
    }

    return { loading, saving, error, success, form, previewFontFamily, onLogoChange, save }
  }
}
</script>

<style scoped>
.cert-settings-page { padding: 24px; max-width: 980px; }
h2 { color: #1e1b4b; margin-bottom: 8px; }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 24px; max-width: 640px; }
.loading { color: #6b7280; }
.layout { display: flex; gap: 32px; flex-wrap: wrap; align-items: flex-start; }
.form-col { flex: 1; min-width: 300px; max-width: 420px; }
.preview-col { flex: 1; min-width: 320px; }
.preview-col h3 { color: #1e1b4b; margin: 0 0 12px; font-size: 1rem; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 18px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input[type="file"], select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
input[type="range"] { width: 100%; }
input[type="color"] { width: 60px; height: 36px; border: 1.5px solid #d1d5db; border-radius: 8px; padding: 2px; }
.options-label { margin-bottom: 2px; }
.checkbox-row { display: flex; align-items: center; gap: 8px; font-weight: 400; font-size: 0.9rem; color: #374151; cursor: pointer; }
.checkbox-row input { width: auto; }
.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 0.95rem; font-weight: 600; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost-sm { padding: 6px 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #6b7280; cursor: pointer; font-size: 0.8rem; align-self: flex-start; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.success { color: #059669; font-size: 0.9rem; margin-bottom: 12px; }

.preview-card {
  background: #fdfdfd; border: 3px solid #1e1b4b; border-radius: 12px; padding: 32px 24px;
  text-align: center; aspect-ratio: 11 / 8.5; display: flex; flex-direction: column; justify-content: center; gap: 8px;
}
.preview-logo { max-height: 50px; margin: 0 auto 8px; }
.preview-card h4, .preview-card h5, .preview-card p { margin: 0; }
.preview-card p { color: #374151; }
.preview-meta { color: #6b7280 !important; font-size: 0.8em !important; }

@media (max-width: 640px) {
  .cert-settings-page { padding: 14px; }
  .layout { gap: 20px; }
}
</style>
