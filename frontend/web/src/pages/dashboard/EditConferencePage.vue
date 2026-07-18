<template lang="pug">
.edit-conf-page
  h2 Editor del evento

  nav.sub-links(v-if="conferenceId")
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/edit`") Editor
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/config`") Configuración

  .loading-text(v-if="loading") Cargando conferencia...
  .error(v-else-if="error") {{ error }}
  .form(v-else)
    .form-group.readonly-group
      label UUID
      .readonly-value {{ conference.uuid }}

    .form-group.readonly-group
      label Nombre para compartir la conferencia (ID amigable)
      .readonly-value {{ conference.friendlyId }}
      p.field-hint Este valor no se puede editar porque ya se usó para generar el enlace público.

    .form-group
      label Nombre a mostrar en el certificado
      input(v-model="displayName" type="text" placeholder="Conferencia de Inteligencia Artificial 2026")

    .form-group
      label Fecha del evento (opcional)
      input(v-model="eventDate" type="date")

    .form-group
      label Horario (opcional)
      .coords-row
        .coord-field
          span.coord-label Hora de inicio
          input(v-model="startTime" type="time")
        .coord-field
          span.coord-label Hora de fin
          input(v-model="endTime" type="time")
      .coord-field(v-if="timezones.length")
        span.coord-label Zona horaria
        select(v-model.number="timezoneId")
          option(v-for="tz in timezones" :key="tz.id" :value="tz.id") {{ tz.label }}

    .form-group
      label Sede (opcional)
      input(v-model="venue" type="text" placeholder="Auditorio, ciudad...")

    .form-group
      label Ubicación (opcional)
      .coords-row
        .coord-field
          span.coord-label Latitud
          input(v-model.number="latitude" type="number" step="0.000001" min="-90" max="90" placeholder="19.4326")
        .coord-field
          span.coord-label Longitud
          input(v-model.number="longitude" type="number" step="0.000001" min="-180" max="180" placeholder="-99.1332")

    .map-preview(v-if="latitude != null && longitude != null && !isNaN(latitude) && !isNaN(longitude)")
      ConferenceMap(:latitude="latitude" :longitude="longitude" :label="displayName || 'Conferencia'")

    .form-group
      label Flyer del evento (opcional)
      p.field-hint Se muestra en la animación de mapa al entrar a la conferencia. No siempre se cuenta con uno.
      input(type="file" accept="image/*" @change="onFlyerSelected")
      .flyer-preview(v-if="flyerBase64")
        img(:src="flyerBase64" alt="Flyer del evento")
        button.btn-remove-flyer(type="button" @click="flyerBase64 = ''") Quitar flyer

    .error(v-if="saveError") {{ saveError }}
    .success(v-if="saved") Cambios guardados correctamente.
    .actions
      button.btn-primary(@click="save" :disabled="saving")
        span(v-if="saving") Guardando...
        span(v-else) Guardar cambios
      router-link.btn-outline(:to="`/dashboard`") Volver al dashboard
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import { getConference, updateConference, getTimezones } from '@/services/api/usersApi'
import type { Conference, Timezone } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'EditConferencePage',
  components: { ConferenceMap },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth        = useAuthStore()
    const conference   = ref<Conference | null>(null)
    const loading      = ref(true)
    const error        = ref('')
    const saving       = ref(false)
    const saveError    = ref('')
    const saved        = ref(false)

    const displayName = ref('')
    const eventDate    = ref('')
    const venue        = ref('')
    const startTime    = ref('')
    const endTime      = ref('')
    const latitude     = ref<number | null>(null)
    const longitude    = ref<number | null>(null)
    const flyerBase64  = ref('')
    const timezones    = ref<Timezone[]>([])
    const timezoneId   = ref<number | null>(null)

    onMounted(async () => {
      try {
        const [conf, tzList] = await Promise.all([
          getConference(props.conferenceId as string, auth.state.token as string),
          getTimezones()
        ])
        conference.value = conf
        timezones.value = tzList
        displayName.value = conference.value.name || ''
        eventDate.value = (conference.value.eventDate as string) || ''
        venue.value = (conference.value.venue as string) || ''
        startTime.value = (conference.value.startTime as string) || ''
        endTime.value = (conference.value.endTime as string) || ''
        latitude.value = (conference.value.latitude as number) ?? null
        longitude.value = (conference.value.longitude as number) ?? null
        flyerBase64.value = conference.value.flyerBase64 || ''
        timezoneId.value = conference.value.timezoneId ?? tzList.find((t) => t.isDefault)?.id ?? null
      } catch (e: any) {
        error.value = 'No se pudo cargar la conferencia.'
      } finally {
        loading.value = false
      }
    })

    function onFlyerSelected(e: Event) {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = () => { flyerBase64.value = reader.result as string }
      reader.readAsDataURL(file)
    }

    async function save() {
      saving.value = true; saveError.value = ''; saved.value = false
      try {
        const lat = (latitude.value != null && !isNaN(latitude.value)) ? latitude.value : null
        const lng = (longitude.value != null && !isNaN(longitude.value)) ? longitude.value : null
        conference.value = await updateConference(props.conferenceId as string, {
          displayName: displayName.value.trim() || null,
          venue: venue.value.trim() || null,
          eventDate: eventDate.value || null,
          startTime: startTime.value || null,
          endTime: endTime.value || null,
          latitude: lat,
          longitude: lng,
          flyerBase64: flyerBase64.value,
          timezoneId: timezoneId.value
        }, auth.state.token as string)
        saved.value = true
      } catch (e: any) {
        saveError.value = e.response?.data?.error?.message || 'Error al guardar los cambios'
      } finally {
        saving.value = false
      }
    }

    return { conference, loading, error, saving, saveError, saved, displayName,
             eventDate, venue, startTime, endTime, latitude, longitude, flyerBase64,
             timezones, timezoneId, onFlyerSelected, save }
  }
}
</script>

<style scoped>
.edit-conf-page { max-width: 680px; }
h2 { color: #1e1b4b; margin-bottom: 8px; margin-top: 0; }
.sub-links { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 24px; }
.sub-link {
  padding: 6px 14px; border: 1.5px solid #e5e7eb; border-radius: 20px; text-decoration: none;
  color: #374151; font-size: 0.82rem; font-weight: 500; transition: all 0.15s;
}
.sub-link:hover { border-color: #a5b4fc; color: #4f46e5; }
.sub-link.router-link-active { background: #4f46e5; color: #fff; border-color: #4f46e5; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input[type="text"], input[type="date"], input[type="time"], input[type="number"] {
  padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem;
}
input:focus { outline: none; border-color: #4f46e5; }

.readonly-group .readonly-value {
  padding: 10px 14px; border: 1.5px solid #e5e7eb; border-radius: 8px;
  background: #f9fafb; color: #6b7280; font-family: monospace; font-size: 0.9rem;
}
.field-hint { margin: 4px 0 0; font-size: 0.8rem; color: #9ca3af; }

.coords-row { display: flex; gap: 12px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: #6b7280; font-weight: 500; }
.map-preview { margin-bottom: 20px; border-radius: 12px; overflow: hidden; }

.flyer-preview { margin-top: 10px; display: flex; align-items: center; gap: 12px; }
.flyer-preview img { max-width: 160px; max-height: 160px; border-radius: 8px; border: 1px solid #e5e7eb; object-fit: cover; }
.btn-remove-flyer { padding: 6px 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #dc2626; cursor: pointer; font-size: 0.85rem; }
.btn-remove-flyer:hover { background: #fee2e2; }

.actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 8px; }
.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-outline { padding: 10px 22px; border: 1.5px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 1rem; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.success { color: #166534; font-size: 0.9rem; margin-bottom: 12px; }
.loading-text { color: #6b7280; }

@media (max-width: 480px) {
  .coords-row { flex-direction: column; gap: 14px; }
}
</style>
