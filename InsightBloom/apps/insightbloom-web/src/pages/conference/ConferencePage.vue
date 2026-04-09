<template lang="pug">
.conference-page
  AppHeader
  .conf-loading(v-if="loading") Cargando conferencia...
  .conf-error(v-else-if="error") {{ error }}
  template(v-else-if="conference")
    .conf-header
      .conf-title-row
        h1 {{ conference.name }}
        .conf-location(v-if="conference.latitude != null")
          span.location-icon 📍
          span.location-coords {{ conference.latitude.toFixed(4) }}, {{ conference.longitude.toFixed(4) }}
      .conf-tabs
        router-link(:to="`/c/${friendlyId}/doubts`" active-class="active-tab") Dudas
        router-link(:to="`/c/${friendlyId}/topics`" active-class="active-tab") Temas
    .conf-map-section(v-if="conference.latitude != null")
      ConferenceMap(
        :latitude="conference.latitude"
        :longitude="conference.longitude"
        :label="conference.name"
      )
    router-view(:conference-id="conference.conferenceId || conference.uuid")
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceMap from '@/components/map/ConferenceMap.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId } from '@/services/api/usersApi'
export default {
  name: 'ConferencePage',
  components: { AppHeader, ConferenceMap },
  setup() {
    const route = useRoute()
    const friendlyId = route.params.friendlyId
    const conference = ref(null)
    const loading = ref(true)
    const error = ref('')
    onMounted(async () => {
      try {
        conference.value = await getConferenceByFriendlyId(friendlyId)
      } catch (e) {
        error.value = 'Conferencia no encontrada. Verifica el ID.'
      } finally {
        loading.value = false
      }
    })
    return { friendlyId, conference, loading, error }
  }
}
</script>

<style scoped>
.conference-page { min-height: 100vh; background: #f5f3ff; }
.conf-header { padding: 24px; background: #fff; border-bottom: 1px solid #e5e7eb; }
.conf-title-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 12px; }
h1 { margin: 0; color: #1e1b4b; }
.conf-location { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; color: #6b7280; }
.location-coords { font-family: monospace; color: #4f46e5; }
.conf-tabs { display: flex; gap: 8px; }
.conf-tabs a { padding: 8px 20px; border-radius: 8px; text-decoration: none; color: #4f46e5; border: 1.5px solid #e0e7ff; font-weight: 500; }
.active-tab { background: #4f46e5; color: #fff; border-color: #4f46e5; }
.conf-map-section { max-height: 340px; overflow: hidden; }
.conf-loading, .conf-error { padding: 40px; text-align: center; color: #6b7280; }
</style>
