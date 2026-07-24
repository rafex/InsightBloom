<template lang="pug">
.edit-conf-page
  DashboardBreadcrumb(:items="breadcrumbItems")

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
      label Detalle del evento
      textarea(v-model="description" rows="4" maxlength="4000" placeholder="Describe el objetivo, público y contenido del evento...")
      p.field-hint Se muestra en la cartelera y la ficha pública cuando el evento es público o híbrido.

    .form-group
      label Visibilidad y boletos públicos
      select(v-model="visibility")
        option(value="PRIVATE") Privado: el organizador distribuye los boletos
        option(value="PUBLIC") Público: aparece en la cartelera y permite solicitar boleto
        option(value="HYBRID") Híbrido: cartelera pública y boletos privados adicionales

    .form-group
      label Precio y moneda del boleto
      .price-row
        input(v-model="ticketPrice" type="number" min="0" step="0.01" placeholder="0.00")
        select(v-model="ticketCurrency")
          option(value="MXN") MXN — Peso mexicano
          option(value="USD") USD — Dólar estadounidense
          option(value="EUR") EUR — Euro
      p.field-hint(v-if="Number(ticketPrice) > 0") Evento de pago. La integración del proveedor de pagos se habilitará después; por ahora no se emitirá ningún boleto.
      p.field-hint(v-else) Gratis — se podrá solicitar el boleto sin cobro.

    .form-group
      label Cronograma en Markdown (opcional)
      textarea(v-model="scheduleMarkdown" rows="8" maxlength="12000" placeholder="## 09:00 — Registro\n\nBienvenida y apertura")
      details.schedule-help
        summary Ver ejemplo detallado de cronograma
        p.field-hint Usa títulos Markdown para cada bloque y listas para describir actividades. No incluyas HTML, scripts ni enlaces sensibles.
        pre.schedule-example {{ scheduleExample }}
      .coords-row
        .coord-field
          span.coord-label Ubicación del cronograma
          select(v-model="scheduleLayout")
            option(value="RIGHT") A la derecha del flyer
            option(value="LEFT") A la izquierda del flyer

    .form-group.public-theme-group
      label Diseño de la cartelera pública
      p.field-hint Elige una presentación base. Solo cambia la apariencia; el contenido siempre usa los datos de este evento.
      .theme-options
        label.theme-option(v-for="theme in publicThemeOptions" :key="theme.value" :class="{ selected: publicTheme === theme.value }")
          input(type="radio" name="publicTheme" :value="theme.value" v-model="publicTheme")
          span.theme-preview(:class="`theme-preview-${theme.value.toLowerCase()}`")
          span.theme-copy
            strong {{ theme.label }}
            small {{ theme.description }}

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
      .map-url-row
        input.map-url-input(v-model.trim="mapUrl" type="url" placeholder="Pega una URL de Google Maps u OpenStreetMap")
        button.btn-outline(type="button" @click="extractMapCoordinates") Extraer coordenadas
      p.field-hint Ejemplos: Google Maps con /@latitud,longitud o OpenStreetMap con #map=nivel/latitud/longitud.
      p.error(v-if="locationError") {{ locationError }}
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
      input(type="file" accept="image/png,image/jpeg" @change="onFlyerSelected")
      .flyer-preview(v-if="flyerBase64")
        img(:src="flyerBase64" alt="Flyer del evento")
        button.btn-remove-flyer(type="button" @click="removeFlyer") Quitar flyer

    .error(v-if="saveError") {{ saveError }}
    .success(v-if="saved") Cambios guardados correctamente.
    .actions
      button.btn-primary(@click="save" :disabled="saving")
        span(v-if="saving") Guardando...
        span(v-else) Guardar cambios
      router-link.btn-outline(:to="`/dashboard`") Volver al dashboard
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import { getConference, updateConference, uploadConferenceFlyer, getTimezones } from '@/services/api/usersApi'
import type { Conference, Timezone } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { parseMapCoordinates } from '@/utils/mapCoordinates'

const MAX_FLYER_BYTES = 8 * 1024 * 1024

export default {
  name: 'EditConferencePage',
  components: { ConferenceMap, DashboardBreadcrumb },
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
    const description = ref('')
    const visibility = ref<'PRIVATE' | 'PUBLIC' | 'HYBRID'>('PRIVATE')
    const ticketPrice = ref('0.00')
    const ticketCurrency = ref('MXN')
    const scheduleMarkdown = ref('')
    const scheduleLayout = ref<'LEFT' | 'RIGHT'>('RIGHT')
    const publicTheme = ref<'CLASSIC' | 'EDITORIAL' | 'MINIMAL'>('CLASSIC')
    const scheduleExample = `## 09:00 — Registro y bienvenida

Registro de asistentes y entrega de materiales.

## 09:30 — Apertura

- Presentación del evento
- Objetivos y dinámica de trabajo

## 10:00 — Charla principal

**Ponente:** Nombre de la persona
**Tema:** Introducción práctica

## 11:15 — Pausa

> Regresamos a las 11:30.

## 11:30 — Taller práctico

1. Preparar el entorno
2. Resolver el ejercicio
3. Compartir preguntas

## 13:00 — Cierre

Conclusiones, encuesta y entrega de certificados.`
    const eventDate    = ref('')
    const venue        = ref('')
    const startTime    = ref('')
    const endTime      = ref('')
    const latitude     = ref<number | null>(null)
    const longitude    = ref<number | null>(null)
    const mapUrl       = ref('')
    const locationError = ref('')
    const flyerBase64  = ref('')
    const flyerFile    = ref<File | null>(null)
    const flyerRemoved = ref(false)
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
        description.value = conference.value.description || ''
        visibility.value = conference.value.visibility || 'PRIVATE'
        ticketPrice.value = conference.value.ticketPrice || '0.00'
        ticketCurrency.value = conference.value.ticketCurrency || 'MXN'
        scheduleMarkdown.value = conference.value.scheduleMarkdown || ''
        scheduleLayout.value = conference.value.scheduleLayout || 'RIGHT'
        publicTheme.value = conference.value.publicTheme || 'CLASSIC'
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
      if (!['image/png', 'image/jpeg'].includes(file.type)) {
        saveError.value = 'El flyer debe estar en formato PNG o JPEG.'
        return
      }
      if (file.size > MAX_FLYER_BYTES) {
        saveError.value = 'El flyer no puede superar los 8 MiB.'
        return
      }
      flyerFile.value = file
      flyerRemoved.value = false
      const reader = new FileReader()
      reader.onload = () => { flyerBase64.value = reader.result as string }
      reader.readAsDataURL(file)
    }

    function extractMapCoordinates() {
      const coordinates = parseMapCoordinates(mapUrl.value)
      if (!coordinates) {
        locationError.value = 'Pega una URL de Google Maps u OpenStreetMap que incluya las coordenadas.'
        return
      }
      latitude.value = coordinates.latitude
      longitude.value = coordinates.longitude
      locationError.value = ''
    }

    function removeFlyer() {
      flyerBase64.value = ''
      flyerFile.value = null
      flyerRemoved.value = true
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
          flyerBase64: flyerRemoved.value ? '' : undefined,
          timezoneId: timezoneId.value,
          description: description.value.trim() || null,
          visibility: visibility.value,
          scheduleMarkdown: scheduleMarkdown.value.trim() || null,
          scheduleLayout: scheduleLayout.value,
          publicTheme: publicTheme.value,
          ticketPrice: ticketPrice.value || '0.00',
          ticketCurrency: ticketCurrency.value
        }, auth.state.token as string)
        if (flyerFile.value) {
          conference.value = await uploadConferenceFlyer(
            props.conferenceId as string,
            flyerFile.value,
            auth.state.token as string
          )
          flyerFile.value = null
        }
        saved.value = true
      } catch (e: any) {
        saveError.value = e.response?.data?.error?.message || 'Error al guardar los cambios'
      } finally {
        saving.value = false
      }
    }

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conference.value?.name || props.conferenceId || '', loading: loading.value && !conference.value },
      { label: 'Editor' }
    ])

    return { conference, loading, error, saving, saveError, saved, displayName, description, visibility,
             ticketPrice, ticketCurrency,
             eventDate, venue, startTime, endTime, latitude, longitude, flyerBase64,
             mapUrl, locationError, extractMapCoordinates, scheduleMarkdown, scheduleLayout, scheduleExample, publicTheme, timezones, timezoneId, breadcrumbItems, onFlyerSelected, removeFlyer, save,
             publicThemeOptions: [
               { value: 'CLASSIC', label: 'Clásico', description: 'Información clara y equilibrada' },
               { value: 'EDITORIAL', label: 'Editorial', description: 'Flyer protagonista y estilo de revista' },
               { value: 'MINIMAL', label: 'Minimalista', description: 'Diseño ligero y directo' }
             ] }
  }
}
</script>

<style scoped>
.public-theme-group { border-top: 1px solid #e5e7eb; padding-top: 18px; }
.theme-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.theme-option { display: flex; flex-direction: column; gap: 8px; padding: 10px; border: 1.5px solid #e5e7eb; border-radius: 12px; background: #fff; cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.theme-option.selected { border-color: #4f46e5; box-shadow: 0 0 0 2px #e0e7ff; }
.theme-option input { position: absolute; opacity: 0; pointer-events: none; }
.theme-preview { height: 48px; border-radius: 8px; display: block; background: #e0e7ff; position: relative; overflow: hidden; }
.theme-preview::after { content: ''; position: absolute; left: 15%; right: 15%; top: 12px; height: 5px; border-radius: 4px; background: currentColor; box-shadow: 0 12px 0 currentColor, 0 24px 0 currentColor; opacity: .65; }
.theme-preview-classic { color: #4f46e5; background: linear-gradient(135deg, #eef2ff, #fff); border: 1px solid #a5b4fc; }
.theme-preview-editorial { color: #fff; background: linear-gradient(135deg, #312e81 0 42%, #f59e0b 42%); }
.theme-preview-minimal { color: #374151; background: #f9fafb; border: 1px solid #d1d5db; }
.theme-copy { display: flex; flex-direction: column; gap: 2px; color: #1e1b4b; }.theme-copy small { color: #6b7280; line-height: 1.3; }
@media (max-width: 580px) { .theme-options { grid-template-columns: 1fr; } .theme-option { display: grid; grid-template-columns: 72px 1fr; align-items: center; }.theme-preview { grid-row: span 2; } }
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
textarea, select { width: 100%; box-sizing: border-box; padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font: inherit; background: #fff; }
input:focus { outline: none; border-color: #4f46e5; }

.readonly-group .readonly-value {
  padding: 10px 14px; border: 1.5px solid #e5e7eb; border-radius: 8px;
  background: #f9fafb; color: #6b7280; font-family: monospace; font-size: 0.9rem;
}
.field-hint { margin: 4px 0 0; font-size: 0.8rem; color: #9ca3af; }

.schedule-help { margin-top: 4px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 12px; background: #fafafa; }
.schedule-help summary { cursor: pointer; color: #4f46e5; font-size: 0.85rem; font-weight: 600; }
.schedule-example { overflow-x: auto; margin: 8px 0 0; padding: 12px; border-radius: 8px; background: #1e1b4b; color: #eef2ff; font: 0.78rem/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }
.map-url-row { display: flex; gap: 8px; align-items: center; }
.map-url-input { flex: 1; min-width: 0; }

.coords-row { display: flex; gap: 12px; }
.price-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
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
