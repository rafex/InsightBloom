<template lang="pug">
.new-conf-page
  h2 Nueva conferencia
  .form(v-if="!created")
    .form-group
      label Nombre de la conferencia
      input(v-model="name" type="text" placeholder="Conferencia IA 2026" @keyup.enter="create")

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
      label Ubicación (opcional)
      .coords-row
        .coord-field
          span.coord-label Latitud
          input(v-model.number="latitude" type="number" step="0.000001" min="-90" max="90" placeholder="19.4326")
        .coord-field
          span.coord-label Longitud
          input(v-model.number="longitude" type="number" step="0.000001" min="-180" max="180" placeholder="-99.1332")
      p.coord-hint Coordenadas del lugar donde se realiza la conferencia

    .map-preview(v-if="latitude != null && longitude != null && !isNaN(latitude) && !isNaN(longitude)")
      ConferenceMap(:latitude="latitude" :longitude="longitude" :label="name || 'Conferencia'")

    .error(v-if="error") {{ error }}
    button.btn-primary(@click="create" :disabled="loading || !name.trim()")
      span(v-if="loading") Creando...
      span(v-else) Crear conferencia

  .created-info.animate__animated.animate__fadeIn(v-else)
    h3 ¡Conferencia creada!
    .info-row
      span Nombre:
      strong {{ created.name }}
    .info-row
      span ID amigable:
      .friendly-id {{ created.friendlyId }}
    .info-row(v-if="created.expiresAt")
      span Expira:
      strong {{ formatDate(created.expiresAt) }}
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

<script>
import { ref, computed } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import { createConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

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
    const error      = ref('')
    const loading    = ref(false)
    const created    = ref(null)
    const expiryMode = ref('none')
    const customDate = ref('')
    const latitude   = ref(null)
    const longitude  = ref(null)
    const auth       = useAuthStore()

    const minDate = computed(() => new Date().toISOString().slice(0, 16))

    function setExpiryMode(val) { expiryMode.value = val }

    function computeExpiresAt() {
      const now = Date.now()
      const map = { '1h': 3600_000, '2h': 7200_000, '4h': 14400_000, '1d': 86400_000 }
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
        created.value = await createConference(name.value.trim(), expiresAt, auth.state.token, lat, lng)
      } catch (e) {
        error.value = e.response?.data?.error?.message || 'Error al crear la conferencia'
      } finally { loading.value = false }
    }

    function formatDate(iso) {
      return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
    }

    function reset() {
      name.value = ''; created.value = null; expiryMode.value = 'none';
      customDate.value = ''; latitude.value = null; longitude.value = null
    }

    return { name, error, loading, created, expiryMode, customDate, minDate, latitude, longitude,
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
.map-preview { margin-bottom: 20px; border-radius: 12px; overflow: hidden; }

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
</style>
