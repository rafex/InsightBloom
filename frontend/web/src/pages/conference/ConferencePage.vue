<template lang="pug">
.conference-page
  AppHeader
  .conf-loading(v-if="loading") Cargando conferencia...
  .conf-error(v-else-if="error") {{ error }}
  template(v-else-if="conference")
    //- Fullscreen intro map (only when conference has coordinates)
    ConferenceIntroMap(
      v-if="showIntro"
      :latitude="conference.latitude"
      :longitude="conference.longitude"
      :label="conference.name"
      @enter="dismissIntro"
    )

    .conf-header
      .conf-title-row
        h1 {{ conference.name }}
        .conf-location(v-if="conference.latitude != null")
          span.location-icon 📍
          span.location-coords {{ conference.latitude.toFixed(4) }}, {{ conference.longitude.toFixed(4) }}
      .conf-tabs
        router-link(:to="`/c/${friendlyId}/doubts`" active-class="active-tab") Dudas
        router-link(:to="`/c/${friendlyId}/topics`" active-class="active-tab") Temas
    router-view(:conference-id="conference.conferenceId || conference.uuid")
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceIntroMap from '@/components/map/ConferenceIntroMap.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId } from '@/services/api/usersApi'

export default {
  name: 'ConferencePage',
  components: { AppHeader, ConferenceIntroMap },
  setup() {
    const route      = useRoute()
    const friendlyId = route.params.friendlyId
    const conference = ref(null)
    const loading    = ref(true)
    const error      = ref('')
    const showIntro  = ref(false)

    function dismissIntro() {
      showIntro.value = false
    }

    onMounted(async () => {
      try {
        conference.value = await getConferenceByFriendlyId(friendlyId)
        // Show intro only when conference has a location
        showIntro.value = conference.value?.latitude != null
      } catch (e) {
        error.value = 'Conferencia no encontrada. Verifica el ID.'
      } finally {
        loading.value = false
      }
    })

    return { friendlyId, conference, loading, error, showIntro, dismissIntro }
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
.conf-tabs a {
  padding: 8px 20px;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.95rem;
  border: 2px solid #c7d2fe;
  background: #fff;
  color: #4f46e5;
  transition: all 0.15s ease;
}
.conf-tabs a:hover:not(.active-tab) {
  background: #eef2ff;
  border-color: #a5b4fc;
}
.conf-tabs a.active-tab {
  background: #4f46e5;
  color: #ffffff !important;
  border-color: #4f46e5;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.35);
}
.conf-loading, .conf-error { padding: 40px; text-align: center; color: #6b7280; }
</style>
