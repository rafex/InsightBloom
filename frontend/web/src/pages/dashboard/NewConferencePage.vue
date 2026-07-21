<template lang="pug">
.new-conf-page
  h2 Nuevo evento
  .form(v-if="!created")
    .form-group
      label Nombre para compartir el evento
      input(v-model="name" type="text" placeholder="Evento IA 2026" @keyup.enter="create")
      p.field-hint Se usa para generar el enlace público (ID amigable). No se podrá cambiar después.

    .form-group
      label Nombre a mostrar en el certificado (opcional)
      input(v-model="displayName" type="text" placeholder="Evento de Inteligencia Artificial 2026")
      p.field-hint Si lo dejas vacío, se usará el nombre para compartir. Se puede editar después.

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

    .form-group(v-if="eventTypes.length")
      label Tipo de evento
      select(v-model="eventTypeKey")
        option(v-for="t in eventTypes" :key="t.key" :value="t.key") {{ t.name }}
      p.field-hint Determina qué herramientas están disponibles (boletos, encuestas, videollamada...). Se puede cambiar después.

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

    .form-group
      label Aforo máximo
      input(v-model.number="capacity" type="number" min="1" placeholder="10")
      p.field-hint Cuántas personas van a tener acceso al evento y sus herramientas (IDE, encuestas...), incluso si es virtual — la infraestructura tiene recursos limitados. Recomendado hasta {{ recommendedMaxCapacity }}. Se puede cambiar después.
      p.capacity-alert(v-if="capacityAlert" :class="capacityAlert.level") {{ capacityAlert.text }}

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
      p.coord-hint Coordenadas del lugar donde se realiza el evento

    .map-preview(v-if="latitude != null && longitude != null && !isNaN(latitude) && !isNaN(longitude)")
      ConferenceMap(:latitude="latitude" :longitude="longitude" :label="name || 'Evento'")

    .error(v-if="error") {{ error }}
    button.btn-primary(@click="create" :disabled="loading || !name.trim()")
      span(v-if="loading") Creando...
      span(v-else) Crear evento

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
      router-link.btn-outline(:to="`/dashboard/conferences/${created.conferenceId}/moderation/messages`") Ver moderación mensajes
      router-link.btn-outline(:to="`/dashboard/conferences/${created.conferenceId}/moderation/words`") Ver moderación palabras
      button.btn-primary(@click="reset") Crear otra
</template>

<script lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import { createConference, getTimezones, getActiveEventTypes } from '@/services/api/usersApi'
import type { Conference, Timezone, EventType, CanvasTool, CanvasAudienceMode, CanvasToolConfig } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { capacityWarning, DEFAULT_CAPACITY, RECOMMENDED_MAX_CAPACITY } from '@/utils/capacityWarning'

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
  components: { ConferenceMap },
  setup() {
    const name       = ref('')
    const displayName = ref('')
    const error      = ref('')
    const loading    = ref(false)
    const created    = ref<Conference | null>(null)
    const expiryMode = ref<ExpiryMode>('none')
    const customDate = ref('')
    const latitude   = ref<number | null>(null)
    const longitude  = ref<number | null>(null)
    const eventDate  = ref('')
    const venue      = ref('')
    const startTime  = ref('')
    const endTime    = ref('')
    const timezones  = ref<Timezone[]>([])
    const timezoneId = ref<number | null>(null)
    const eventTypes = ref<EventType[]>([])
    const eventTypeKey = ref('conference')
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
          })))
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'Error al crear el evento'
      } finally { loading.value = false }
    }

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
    }

    function reset() {
      name.value = ''; displayName.value = ''; created.value = null; expiryMode.value = 'none';
      customDate.value = ''; latitude.value = null; longitude.value = null
      eventDate.value = ''; venue.value = ''; startTime.value = ''; endTime.value = ''
      canvasTools.value = []
      canvasModes.DRAWIO = 'INDEPENDENT'; canvasModes.EXCALIDRAW = 'INDEPENDENT'; canvasModes.ETHERPAD = 'COLLABORATIVE'
      capacity.value = DEFAULT_CAPACITY
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

    return { name, displayName, error, loading, created, expiryMode, customDate, minDate, latitude, longitude,
             eventDate, venue, startTime, endTime, timezones, timezoneId, eventTypes, eventTypeKey,
             canvasTools, canvasModes, canvasToolLabel, canvasModeOptions,
             canvasToolOptions: [
               { value: 'DRAWIO', label: 'Drawio (diagramas)' },
               { value: 'EXCALIDRAW', label: 'Excalidraw (pizarra)' },
               { value: 'ETHERPAD', label: 'Etherpad (notas)' }
             ] as Array<{ value: CanvasTool; label: string }>,
             capacity, recommendedMaxCapacity, capacityAlert,
             expiryOptions: EXPIRY_OPTIONS, setExpiryMode, create, formatDate, reset }
  }
}
</script>

<style scoped>
.new-conf-page { max-width: 680px; }
h2 { color: #1e1b4b; margin-bottom: 24px; margin-top: 0; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input[type="text"], input[type="datetime-local"], input[type="number"] {
  padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem;
}
select { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; background: #fff; }
input:focus { outline: none; border-color: #4f46e5; }

.expiry-options { display: flex; gap: 6px; flex-wrap: wrap; }
.expiry-btn {
  padding: 6px 14px; border: 1.5px solid #d1d5db; border-radius: 20px;
  background: #fff; color: #374151; cursor: pointer; font-size: 0.85rem; font-weight: 500;
  transition: all 0.15s;
}
.expiry-btn:hover { border-color: #a5b4fc; color: #4f46e5; }
.expiry-btn.active { background: #4f46e5; color: #fff; border-color: #4f46e5; }
.custom-date { margin-top: 8px; }

.coords-row { display: flex; gap: 12px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: #6b7280; font-weight: 500; }
.coord-hint { margin: 6px 0 0; font-size: 0.8rem; color: #9ca3af; }
.field-hint { margin: 4px 0 0; font-size: 0.8rem; color: #9ca3af; }
.canvas-tools { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; background: #fff; }
.canvas-tool-option { display: flex; align-items: center; gap: 8px; font-weight: 500; cursor: pointer; }
.canvas-tool-option input { width: auto; }
.canvas-mode-row { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.canvas-mode-label { font-size: 0.8rem; color: #6b7280; font-weight: 600; }
.map-preview { margin-bottom: 20px; border-radius: 12px; overflow: hidden; }

.capacity-alert { margin: 6px 0 0; font-size: 0.82rem; font-weight: 600; padding: 6px 10px; border-radius: 6px; }
.capacity-alert.warning { background: #fef3c7; color: #92400e; }
.capacity-alert.risk { background: #ffedd5; color: #9a3412; }
.capacity-alert.critical { background: #fee2e2; color: #991b1b; }

.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.created-info { background: #f0fdf4; border: 1.5px solid #86efac; border-radius: 12px; padding: 24px; }
h3 { color: #166534; margin: 0 0 16px; }
.info-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; font-size: 0.95rem; }
.info-row span { color: #6b7280; min-width: 100px; }
.friendly-id { font-family: monospace; background: #dcfce7; padding: 4px 10px; border-radius: 6px; font-size: 1.1rem; font-weight: 700; color: #14532d; letter-spacing: 1px; }
.coords-display { font-family: monospace; font-size: 0.9rem; color: #374151; }
.map-created { margin: 16px 0; border-radius: 10px; overflow: hidden; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 20px; }
.btn-outline { padding: 8px 16px; border: 1.5px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 0.9rem; }

@media (max-width: 480px) {
  .coords-row { flex-direction: column; gap: 14px; }
}
</style>
