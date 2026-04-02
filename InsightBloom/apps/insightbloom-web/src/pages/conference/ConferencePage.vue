<template lang="pug">
.conference-page
  AppHeader
  .conf-header(v-if="conference")
    h1 {{ conference.name }}
    .conf-tabs
      router-link(:to="`/c/${friendlyId}/doubts`" active-class="active-tab") Dudas
      router-link(:to="`/c/${friendlyId}/topics`" active-class="active-tab") Temas
  .conf-loading(v-else-if="loading") Cargando conferencia...
  .conf-error(v-else-if="error") {{ error }}
  router-view(v-if="conference" :conference-id="conference.conferenceId || conference.uuid")
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId } from '@/services/api/usersApi'
export default {
  name: 'ConferencePage',
  components: { AppHeader },
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
h1 { margin: 0 0 12px; color: #1e1b4b; }
.conf-tabs { display: flex; gap: 8px; }
.conf-tabs a { padding: 8px 20px; border-radius: 8px; text-decoration: none; color: #4f46e5; border: 1.5px solid #e0e7ff; font-weight: 500; }
.active-tab { background: #4f46e5; color: #fff; border-color: #4f46e5; }
.conf-loading, .conf-error { padding: 40px; text-align: center; color: #6b7280; }
</style>
