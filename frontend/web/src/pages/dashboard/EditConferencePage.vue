<template lang="pug">
.edit-conf-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  h2 Editor del evento

  ConferenceToolsNav(:conferenceId="conferenceId")

  LoadingState(v-if="loading" message="Cargando conferencia...")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  .form(v-else)
    SaveState(:state="saveState")
    .form-group.readonly-group
      label UUID
      .readonly-value {{ conference.uuid }}

    .form-group.readonly-group
      label Nombre para compartir la conferencia (ID amigable)
      .readonly-value {{ conference.friendlyId }}
      p.field-hint Este valor no se puede editar porque ya se usó para generar el enlace público.

    FormField(label="Nombre a mostrar en el certificado")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model="displayName" type="text" placeholder="Conferencia de Inteligencia Artificial 2026")

    FormField(label="Detalle del evento" hint="Se muestra en la cartelera y la ficha pública cuando el evento es público o híbrido.")
      template(#default="{ id, describedBy }")
        textarea(:id="id" :aria-describedby="describedBy" v-model="description" rows="4" maxlength="4000" placeholder="Describe el objetivo, público y contenido del evento...")

    FormField(label="Visibilidad y boletos públicos")
      template(#default="{ id, describedBy }")
        select(:id="id" :aria-describedby="describedBy" v-model="visibility")
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
      textarea.schedule-textarea(v-model="scheduleMarkdown" rows="8" maxlength="12000" placeholder="## 09:00 — Registro\n\nBienvenida y apertura")
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

    .form-group.event-theme-group
      label Diseño de la cartelera pública
      p.field-hint Elige una presentación base. Solo cambia la apariencia; el contenido siempre usa los datos de este evento.
      .event-theme-options
        label.event-theme-option(v-for="theme in publicThemeOptions" :key="theme.value" :class="{ selected: publicTheme === theme.value }")
          input(type="radio" name="publicTheme" :value="theme.value" v-model="publicTheme")
          span.event-theme-preview(:class="`event-theme-preview-${theme.value.toLowerCase()}`")
          span.event-theme-copy
            strong {{ theme.label }}
            small {{ theme.description }}

    FormField(label="Fecha del evento (opcional)")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model="eventDate" type="date")

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

    FormField(label="Sede (opcional)")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model="venue" type="text" placeholder="Auditorio, ciudad...")

    .form-group
      label Ubicación (opcional)
      .map-url-row
        input.map-url-input(v-model.trim="mapUrl" type="url" placeholder="Pega una URL de Google Maps u OpenStreetMap")
        BaseButton(variant="secondary" type="button" @click="extractMapCoordinates") Extraer coordenadas
      p.field-hint Ejemplos: Google Maps con /@latitud,longitud o OpenStreetMap con #map=nivel/latitud/longitud.
      FeedbackMessage(v-if="locationError" :message="locationError" tone="error")
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
        BaseButton(variant="danger" type="button" @click="removeFlyer") Quitar flyer

    FeedbackMessage(v-if="saveError" :message="saveError" tone="error")
    .actions
      BaseButton(:loading="saving" :disabled="saving || saveState === 'clean' || saveState === 'saved'" @click="save") Guardar cambios
      BaseLink(variant="secondary" to="/dashboard") Volver al panel
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import FormField from '@/components/ui/FormField.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import SaveState from '@/components/ui/SaveState.vue'
import { getConference, updateConference, uploadConferenceFlyer, getTimezones } from '@/services/api/usersApi'
import type { Conference, Timezone } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { parseMapCoordinates } from '@/utils/mapCoordinates'

const MAX_FLYER_BYTES = 8 * 1024 * 1024

export default {
  name: 'EditConferencePage',
  components: { ConferenceMap, DashboardBreadcrumb, ConferenceToolsNav, BaseButton, BaseLink, FormField, FeedbackMessage, LoadingState, SaveState },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth        = useAuthStore()
    const conference   = ref<Conference | null>(null)
    const loading      = ref(true)
    const error        = ref('')
    const saving       = ref(false)
    const saveError    = ref('')
    const saved        = ref(false)
    const initialFormSnapshot = ref('')

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

    function formSnapshot() {
      return JSON.stringify({
        displayName: displayName.value,
        description: description.value,
        visibility: visibility.value,
        ticketPrice: ticketPrice.value,
        ticketCurrency: ticketCurrency.value,
        scheduleMarkdown: scheduleMarkdown.value,
        scheduleLayout: scheduleLayout.value,
        publicTheme: publicTheme.value,
        eventDate: eventDate.value,
        venue: venue.value,
        startTime: startTime.value,
        endTime: endTime.value,
        latitude: latitude.value,
        longitude: longitude.value,
        flyerBase64: flyerBase64.value,
        flyerRemoved: flyerRemoved.value,
        timezoneId: timezoneId.value
      })
    }

    const saveState = computed(() => {
      if (saving.value) return 'saving'
      if (!initialFormSnapshot.value || formSnapshot() !== initialFormSnapshot.value) return 'dirty'
      return saved.value ? 'saved' : 'clean'
    })

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
        initialFormSnapshot.value = formSnapshot()
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
        initialFormSnapshot.value = formSnapshot()
        saved.value = true
      } catch (e: any) {
        saveError.value = e.response?.data?.error?.message || 'Error al guardar los cambios'
      } finally {
        saving.value = false
      }
    }

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/events' },
      { label: conference.value?.name || props.conferenceId || '', loading: loading.value && !conference.value },
      { label: 'Editor' }
    ])

    return { conference, loading, error, saving, saveError, saved, saveState, displayName, description, visibility,
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
.edit-conf-page { max-width: 680px; }
h2 { color: var(--color-heading); margin-bottom: 8px; margin-top: 0; }
.readonly-group .readonly-value {
  padding: 10px 14px; border: 1.5px solid var(--color-border-subtle); border-radius: 8px;
  background: var(--color-surface-muted); color: var(--color-text-muted); font-family: monospace; font-size: 0.9rem;
}

/* Único textarea de esta página que es hijo directo de un .form-group plano (sin flex) en vez
   de ir dentro de FormField (que estira sus hijos vía flex-direction:column) -- sin este ancho
   explícito, cae al tamaño nativo por defecto del navegador (basado en el atributo cols, ~20
   caracteres) en vez de ocupar el ancho del formulario como los demás campos. */
.schedule-textarea { width: 100%; }
.schedule-help { margin-top: 4px; border: 1px solid var(--color-border-subtle); border-radius: 8px; padding: 8px 12px; background: var(--color-surface-muted); }
.schedule-help summary { cursor: pointer; color: var(--color-primary); font-size: 0.85rem; font-weight: 600; }
.schedule-example { overflow-x: auto; margin: 8px 0 0; padding: 12px; border-radius: 8px; background: var(--color-heading); color: var(--color-primary-soft); font: 0.78rem/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }
.map-url-row { display: flex; gap: 8px; align-items: center; }
.map-url-input { flex: 1; min-width: 0; }

.coords-row { display: flex; gap: 12px; }
.price-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: var(--color-text-muted); font-weight: 500; }
.map-preview { margin-bottom: 20px; border-radius: 12px; overflow: hidden; }

.flyer-preview { margin-top: 10px; display: flex; align-items: center; gap: 12px; }
.flyer-preview img { max-width: 160px; max-height: 160px; border-radius: 8px; border: 1px solid var(--color-border-subtle); object-fit: cover; }

.actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 8px; }

@media (max-width: 480px) {
  .coords-row { flex-direction: column; gap: 14px; }
}
</style>
