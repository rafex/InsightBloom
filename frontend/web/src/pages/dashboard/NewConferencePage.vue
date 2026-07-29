<template lang="pug">
.new-conf-page
  h2 Nuevo evento
  .form(v-if="!created")
    FormField(label="Nombre para compartir el evento" hint="Se usa para generar el enlace público (ID amigable). No se podrá cambiar después.")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model="name" type="text" placeholder="Evento IA 2026" @keyup.enter="create")

    FormField(label="Nombre a mostrar en el certificado (opcional)" hint="Si lo dejas vacío, se usará el nombre para compartir. Se puede editar después.")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model="displayName" type="text" placeholder="Evento de Inteligencia Artificial 2026")

    FormField(label="Detalle del evento (opcional)" hint="Se mostrará en la cartelera y en la ficha pública del evento.")
      template(#default="{ id, describedBy }")
        textarea(:id="id" :aria-describedby="describedBy" v-model="description" rows="4" maxlength="4000" placeholder="Describe el objetivo, público y contenido del evento...")

    FormField(label="Visibilidad y boletos públicos" hint="Los eventos privados nunca aparecen en la cartelera pública.")
      template(#default="{ id, describedBy }")
        select(:id="id" :aria-describedby="describedBy" v-model="visibility")
          option(value="PRIVATE") Privado: el organizador distribuye los boletos
          option(value="PUBLIC") Público: aparece en la cartelera y permite solicitar boleto
          option(value="HYBRID") Híbrido: cartelera pública y boletos adicionales distribuidos por el organizador

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
      textarea(v-model="scheduleMarkdown" rows="7" maxlength="12000" placeholder="## 09:00 — Registro\n\nBienvenida y apertura\n\n## 10:00 — Charla principal")
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
      label Tiempo de vida
      .expiry-options
        button.expiry-btn(
          v-for="opt in expiryOptions" :key="opt.value"
          :class="{ active: expiryMode === opt.value }"
          type="button"
          @click="setExpiryMode(opt.value)"
        ) {{ opt.label }}
      .custom-date(v-if="expiryMode === 'custom'")
        input(v-model="customDate" type="datetime-local" :min="minDate")

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

    FormField(label="Tipo de evento" hint="Determina qué herramientas están disponibles (boletos, encuestas, videollamada...). Se puede cambiar después." v-if="eventTypes.length")
      template(#default="{ id, describedBy }")
        select(:id="id" :aria-describedby="describedBy" v-model="eventTypeKey")
          option(v-for="t in eventTypes" :key="t.key" :value="t.key") {{ t.name }}

    FormField(label="Motor de certificado" hint="Elige cómo se generarán los certificados de este evento. Puedes cambiarlo después en Configuración; el diseño global queda como respaldo del motor Inhouse.")
      template(#default="{ id, describedBy }")
        select(:id="id" :aria-describedby="describedBy" v-model="certificateEngine")
          option(value="INHOUSE") Inhouse (PDFBox)
          option(value="HTML_CHROME") HTML + Playwright/Chromium

    .form-group
      label Lienzo del evento (opcional)
      p.field-hint Selecciona una o varias herramientas. Si no eliges ninguna, se usarán las herramientas del tipo de evento.
      .canvas-tools
        label.canvas-tool-option(v-for="tool in canvasToolOptions" :key="tool.value")
          input(type="checkbox" :value="tool.value" v-model="canvasTools")
          span {{ tool.label }}
      .canvas-mode-row(v-for="tool in canvasTools" :key="tool")
        span.canvas-mode-label {{ canvasToolLabel(tool) }}
        select(v-model="canvasModes[tool]")
          option(v-for="option in canvasModeOptions(tool)" :key="option.value" :value="option.value") {{ option.label }}
      p.field-hint(v-if="canvasTools.includes('ETHERPAD')") Etherpad sólo admite notas grupales (todos colaboran) o notas individuales (un pad privado por asistente); no tiene modo de publicación exclusiva del moderador. Las notas individuales se borran al vencer el evento y se pueden exportar.
      p.field-hint Cada herramienta puede tener una modalidad distinta.

    FormField(label="Aforo máximo" :hint="`Cuántas personas van a tener acceso al evento y sus herramientas (IDE, encuestas...), incluso si es virtual — la infraestructura tiene recursos limitados. El mínimo es 2 porque el creador ocupa un boleto operativo contado. Cada moderador adicional ocupa otra plaza. Recomendado hasta ${recommendedMaxCapacity}. Se puede cambiar después.`")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model.number="capacity" type="number" min="2" placeholder="10")
    p.capacity-alert(v-if="capacityAlert" :class="capacityAlert.level") {{ capacityAlert.text }}

    FormField(label="Sede (opcional)")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model="venue" type="text" placeholder="Auditorio, ciudad...")

    .form-group
      label Ubicación (opcional)
      .map-url-row
        input.map-url-input(v-model.trim="mapUrl" type="url" placeholder="Pega una URL de Google Maps u OpenStreetMap")
        BaseButton(variant="secondary" type="button" @click="extractMapCoordinates") Extraer coordenadas
      p.field-hint Ejemplos: Google Maps con /@latitud,longitud o OpenStreetMap con #map=nivel/latitud/longitud.
      p.error(v-if="locationError") {{ locationError }}
      .coords-row
        .coord-field
          span.coord-label Latitud
          input(v-model.number="latitude" type="number" step="0.000001" min="-90" max="90" placeholder="19.4326")
        .coord-field
          span.coord-label Longitud
          input(v-model.number="longitude" type="number" step="0.000001" min="-180" max="180" placeholder="-99.1332")
      p.coord-hint Coordenadas del lugar donde se realiza el evento

    .map-preview(v-if="latitude != null && longitude != null && !isNaN(latitude) && !isNaN(longitude)")
      ConferenceMap(:latitude="latitude" :longitude="longitude" :label="name || 'Evento'")

    .error(v-if="error") {{ error }}
    BaseButton(:loading="loading" :disabled="loading || !name.trim()" @click="create") Crear evento

  .created-info.animate__animated.animate__fadeIn(v-else)
    h3 ¡Evento creado!
    .info-row
      span Nombre:
      strong {{ created.name }}
    .info-row
      span ID amigable:
      .friendly-id {{ created.friendlyId }}
    .info-row(v-if="created.expiresAt")
      span Expira:
      strong {{ formatDate(created.expiresAt) }}
    .info-row(v-if="created.eventDate")
      span Fecha del evento:
      strong {{ created.eventDate }}
    .info-row(v-if="created.startTime || created.endTime")
      span Horario:
      strong {{ created.startTime || '?' }} - {{ created.endTime || '?' }}
    .info-row(v-if="created.venue")
      span Sede:
      strong {{ created.venue }}
    .info-row(v-if="created.latitude != null")
      span Ubicación:
      span.coords-display {{ created.latitude.toFixed(4) }}, {{ created.longitude.toFixed(4) }}
    .info-row
      span Link público:
      a(:href="`/c/${created.friendlyId}/doubts`" target="_blank") /c/{{ created.friendlyId }}
    .map-created(v-if="created.latitude != null")
      ConferenceMap(:latitude="created.latitude" :longitude="created.longitude" :label="created.name")
    .actions
      router-link.link-btn.link-btn-secondary(:to="`/dashboard/conferences/${created.conferenceId}/moderation/messages`") Ver moderación mensajes
      router-link.link-btn.link-btn-secondary(:to="`/dashboard/conferences/${created.conferenceId}/moderation/words`") Ver moderación palabras
      BaseButton(@click="reset") Crear otra
</template>

<script lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import FormField from '@/components/ui/FormField.vue'
import { createConference, getTimezones, getActiveEventTypes } from '@/services/api/usersApi'
import type { Conference, Timezone, EventType, CanvasTool, CanvasAudienceMode, CanvasToolConfig, CertificateEngine } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { capacityWarning, DEFAULT_CAPACITY, RECOMMENDED_MAX_CAPACITY } from '@/utils/capacityWarning'
import { parseMapCoordinates } from '@/utils/mapCoordinates'

type ExpiryMode = 'none' | '1h' | '2h' | '4h' | '1d' | 'custom'

const EXPIRY_OPTIONS = [
  { label: 'Sin límite', value: 'none' },
  { label: '1 hora',    value: '1h'   },
  { label: '2 horas',   value: '2h'   },
  { label: '4 horas',   value: '4h'   },
  { label: '1 día',     value: '1d'   },
  { label: 'Fecha…',    value: 'custom' },
]

export default {
  name: 'NewConferencePage',
  components: { ConferenceMap, BaseButton, FormField },
  setup() {
    const name       = ref('')
    const displayName = ref('')
    const description = ref('')
    const visibility = ref<'PRIVATE' | 'PUBLIC' | 'HYBRID'>('PRIVATE')
    const ticketPrice = ref('0.00')
    const ticketCurrency = ref('MXN')
    const scheduleMarkdown = ref('')
    const scheduleLayout = ref<'LEFT' | 'RIGHT'>('RIGHT')
    const publicTheme = ref<'CLASSIC' | 'EDITORIAL' | 'MINIMAL'>('CLASSIC')
    const error      = ref('')
    const loading    = ref(false)
    const created    = ref<Conference | null>(null)
    const expiryMode = ref<ExpiryMode>('none')
    const customDate = ref('')
    const latitude   = ref<number | null>(null)
    const longitude  = ref<number | null>(null)
    const mapUrl     = ref('')
    const locationError = ref('')
    const eventDate  = ref('')
    const venue      = ref('')
    const startTime  = ref('')
    const endTime    = ref('')
    const timezones  = ref<Timezone[]>([])
    const timezoneId = ref<number | null>(null)
    const eventTypes = ref<EventType[]>([])
    const eventTypeKey = ref('conference')
    const certificateEngine = ref<CertificateEngine>('INHOUSE')
    const canvasTools = ref<CanvasTool[]>([])
    const canvasModes = reactive<Record<CanvasTool, CanvasAudienceMode>>({
      DRAWIO: 'INDEPENDENT', EXCALIDRAW: 'INDEPENDENT', ETHERPAD: 'COLLABORATIVE'
    })
    const capacity = ref<number | null>(DEFAULT_CAPACITY)
    const recommendedMaxCapacity = RECOMMENDED_MAX_CAPACITY
    const capacityAlert = computed(() => capacityWarning(capacity.value))
    const auth       = useAuthStore()

    onMounted(async () => {
      try {
        timezones.value = await getTimezones()
        const def = timezones.value.find((t) => t.isDefault)
        if (def) timezoneId.value = def.id
      } catch (e: any) { /* selector queda vacío, el backend igual usa su propio default */ }
      try {
        eventTypes.value = await getActiveEventTypes()
      } catch (e: any) { /* selector queda vacío, el backend igual usa su propio default "conference" */ }
    })

    const minDate = computed(() => new Date().toISOString().slice(0, 16))

    function setExpiryMode(val: ExpiryMode) { expiryMode.value = val }

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

    function computeExpiresAt(): string | null {
      const now = Date.now()
      const map: Record<string, number> = { '1h': 3600_000, '2h': 7200_000, '4h': 14400_000, '1d': 86400_000 }
      if (expiryMode.value === 'none') return null
      if (expiryMode.value === 'custom') {
        if (!customDate.value) return null
        return new Date(customDate.value).toISOString()
      }
      return new Date(now + map[expiryMode.value]).toISOString()
    }

    async function create() {
      if (!name.value.trim()) return
      loading.value = true; error.value = ''
      try {
        const expiresAt = computeExpiresAt()
        const lat = (latitude.value != null && !isNaN(latitude.value)) ? latitude.value : null
        const lng = (longitude.value != null && !isNaN(longitude.value)) ? longitude.value : null
        created.value = await createConference(name.value.trim(), expiresAt, auth.state.token as string, lat, lng,
          eventDate.value || null, venue.value.trim() || null, startTime.value || null, endTime.value || null,
          displayName.value.trim() || null, timezoneId.value, eventTypeKey.value, capacity.value,
          null, null, canvasTools.value.map((tool): CanvasToolConfig => ({
            tool, audienceMode: canvasModes[tool]
          })), certificateEngine.value, description.value.trim() || null, visibility.value,
          scheduleMarkdown.value.trim() || null, scheduleLayout.value, publicTheme.value,
          ticketPrice.value || '0.00', ticketCurrency.value)
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'Error al crear el evento'
      } finally { loading.value = false }
    }

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
    }

    function reset() {
      name.value = ''; displayName.value = ''; description.value = ''; visibility.value = 'PRIVATE'
      scheduleMarkdown.value = ''; scheduleLayout.value = 'RIGHT'; publicTheme.value = 'CLASSIC'; created.value = null; expiryMode.value = 'none';
      customDate.value = ''; latitude.value = null; longitude.value = null; mapUrl.value = ''; locationError.value = ''
      eventDate.value = ''; venue.value = ''; startTime.value = ''; endTime.value = ''
      canvasTools.value = []
      canvasModes.DRAWIO = 'INDEPENDENT'; canvasModes.EXCALIDRAW = 'INDEPENDENT'; canvasModes.ETHERPAD = 'COLLABORATIVE'
      capacity.value = DEFAULT_CAPACITY; certificateEngine.value = 'INHOUSE'
      ticketPrice.value = '0.00'; ticketCurrency.value = 'MXN'
    }

    function canvasToolLabel(tool: CanvasTool): string {
      return { DRAWIO: 'Drawio', EXCALIDRAW: 'Excalidraw', ETHERPAD: 'Etherpad' }[tool]
    }

    function canvasModeOptions(tool: CanvasTool): Array<{ value: CanvasAudienceMode; label: string }> {
      if (tool === 'ETHERPAD') {
        return [
          { value: 'COLLABORATIVE', label: 'Notas grupales (todos colaboran)' },
          { value: 'INDEPENDENT', label: 'Notas individuales (se borran al vencer el evento)' }
        ]
      }
      return [
        { value: 'INDEPENDENT', label: 'Trabajo independiente (solo persiste el moderador)' },
        { value: 'MODERATOR_ONLY', label: 'Solo el moderador edita; asistentes ven la publicación' }
      ]
    }

    return { name, displayName, description, visibility, ticketPrice, ticketCurrency, scheduleMarkdown, scheduleLayout, publicTheme, error, loading, created, expiryMode, customDate, minDate, latitude, longitude, mapUrl, locationError, extractMapCoordinates,
             eventDate, venue, startTime, endTime, timezones, timezoneId, eventTypes, eventTypeKey, certificateEngine,
             canvasTools, canvasModes, canvasToolLabel, canvasModeOptions,
             canvasToolOptions: [
               { value: 'DRAWIO', label: 'Drawio (diagramas)' },
               { value: 'EXCALIDRAW', label: 'Excalidraw (pizarra)' },
               { value: 'ETHERPAD', label: 'Etherpad (notas)' }
             ] as Array<{ value: CanvasTool; label: string }>,
             capacity, recommendedMaxCapacity, capacityAlert,
             publicThemeOptions: [
               { value: 'CLASSIC', label: 'Clásico', description: 'Información clara y equilibrada' },
               { value: 'EDITORIAL', label: 'Editorial', description: 'Flyer protagonista y estilo de revista' },
               { value: 'MINIMAL', label: 'Minimalista', description: 'Diseño ligero y directo' }
             ],
             expiryOptions: EXPIRY_OPTIONS, setExpiryMode, create, formatDate, reset }
  }
}
</script>

<style scoped>
.public-theme-group { border-top: 1px solid var(--color-border-subtle); padding-top: 18px; }
.theme-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.theme-option { display: flex; flex-direction: column; gap: 8px; padding: 10px; border: 1.5px solid var(--color-border-subtle); border-radius: 12px; background: var(--color-surface); cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.theme-option.selected { border-color: var(--color-primary); box-shadow: 0 0 0 2px var(--color-primary-soft); }
.theme-option input { position: absolute; opacity: 0; pointer-events: none; }
.theme-preview { height: 48px; border-radius: 8px; display: block; background: var(--color-primary-soft); position: relative; overflow: hidden; }
.theme-preview::after { content: ''; position: absolute; left: 15%; right: 15%; top: 12px; height: 5px; border-radius: 4px; background: currentColor; box-shadow: 0 12px 0 currentColor, 0 24px 0 currentColor; opacity: .65; }
.theme-preview-classic { color: var(--color-primary); background: linear-gradient(135deg, var(--color-primary-soft), var(--color-surface)); border: 1px solid var(--color-primary-border); }
.theme-preview-editorial { color: var(--color-text-inverse); background: linear-gradient(135deg, var(--color-primary-dark) 0 42%, var(--color-warning) 42%); }
.theme-preview-minimal { color: var(--color-text-secondary); background: var(--color-surface-muted); border: 1px solid var(--color-border); }
.theme-copy { display: flex; flex-direction: column; gap: 2px; color: var(--color-heading); }.theme-copy small { color: var(--color-text-muted); line-height: 1.3; }
@media (max-width: 580px) { .theme-options { grid-template-columns: 1fr; } .theme-option { display: grid; grid-template-columns: 72px 1fr; align-items: center; }.theme-preview { grid-row: span 2; } }
.new-conf-page { max-width: 680px; }
h2 { color: var(--color-heading); margin-bottom: 24px; margin-top: 0; }
.expiry-options { display: flex; gap: 6px; flex-wrap: wrap; }
.expiry-btn {
  padding: 6px 14px; border: 1.5px solid var(--color-border); border-radius: 20px;
  background: var(--color-surface); color: var(--color-text-secondary); cursor: pointer; font-size: 0.85rem; font-weight: 500;
  transition: all 0.15s;
}
.expiry-btn:hover { border-color: var(--color-primary-border); color: var(--color-primary); }
.expiry-btn.active { background: var(--color-primary); color: var(--color-text-inverse); border-color: var(--color-primary); }
.custom-date { margin-top: 8px; }

.coords-row { display: flex; gap: 12px; }
.price-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: var(--color-text-muted); font-weight: 500; }
.coord-hint { margin: 6px 0 0; font-size: 0.8rem; color: var(--color-text-muted); }
.field-hint { margin: 4px 0 0; font-size: 0.8rem; color: var(--color-text-muted); }
.map-url-row { display: flex; gap: 8px; align-items: center; }
.map-url-input { flex: 1; min-width: 0; }
.canvas-tools { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border: 1.5px solid var(--color-border); border-radius: 8px; background: var(--color-surface); }
.canvas-tool-option { display: flex; align-items: center; gap: 8px; font-weight: 500; cursor: pointer; }
.canvas-tool-option input { width: auto; }
.canvas-mode-row { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.canvas-mode-label { font-size: 0.8rem; color: var(--color-text-muted); font-weight: 600; }
.map-preview { margin-bottom: 20px; border-radius: 12px; overflow: hidden; }

.capacity-alert { margin: 6px 0 0; font-size: 0.82rem; font-weight: 600; padding: 6px 10px; border-radius: 6px; }
.capacity-alert.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.capacity-alert.risk { background: var(--color-warning-soft); color: var(--color-warning); }
.capacity-alert.critical { background: var(--color-danger-soft); color: var(--color-danger-dark); }

.error { color: var(--color-danger); font-size: 0.9rem; margin-bottom: 12px; }
.created-info { background: var(--color-success-soft); border: 1.5px solid var(--color-success); border-radius: 12px; padding: 24px; }
h3 { color: var(--color-success); margin: 0 0 16px; }
.info-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; font-size: 0.95rem; }
.info-row span { color: var(--color-text-muted); min-width: 100px; }
.friendly-id { font-family: monospace; background: var(--color-success-soft); padding: 4px 10px; border-radius: 6px; font-size: 1.1rem; font-weight: 700; color: var(--color-success); letter-spacing: 1px; }
.coords-display { font-family: monospace; font-size: 0.9rem; color: var(--color-text-secondary); }
.map-created { margin: 16px 0; border-radius: 10px; overflow: hidden; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 20px; }

@media (max-width: 480px) {
  .coords-row { flex-direction: column; gap: 14px; }
  .price-row { grid-template-columns: 1fr; }
}
</style>
