<template lang="pug">
.new-conf-page
  h2 Nueva conferencia
  .form(v-if="!created")
    .form-group
      label Nombre de la conferencia
      input(v-model="name" type="text" placeholder="Conferencia IA 2026" @keyup.enter="create")

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
import { ref } from 'vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import { createConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'NewConferencePage',
  components: { ConferenceMap },
  setup() {
    const name = ref('')
    const latitude = ref(null)
    const longitude = ref(null)
    const error = ref('')
    const loading = ref(false)
    const created = ref(null)
    const auth = useAuthStore()

    async function create() {
      if (!name.value.trim()) return
      loading.value = true; error.value = ''
      try {
        const lat = (latitude.value != null && !isNaN(latitude.value)) ? latitude.value : null
        const lng = (longitude.value != null && !isNaN(longitude.value)) ? longitude.value : null
        created.value = await createConference(name.value.trim(), auth.state.token, lat, lng)
      } catch (e) {
        error.value = e.response?.data?.error?.message || 'Error al crear la conferencia'
      } finally { loading.value = false }
    }

    function reset() { name.value = ''; latitude.value = null; longitude.value = null; created.value = null }

    return { name, latitude, longitude, error, loading, created, create, reset }
  }
}
</script>

<style scoped>
.new-conf-page { max-width: 680px; }
h2 { color: #1e1b4b; margin-bottom: 24px; margin-top: 0; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input[type="text"], input[type="number"] {
  padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem;
}
input:focus { outline: none; border-color: #4f46e5; }
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
