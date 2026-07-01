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
        button.btn-qr(type="button" @click="showQr = true") 📱 Mostrar QR
      .conf-tabs
        router-link(:to="`/c/${friendlyId}/doubts`" active-class="active-tab") Dudas
        router-link(:to="`/c/${friendlyId}/topics`" active-class="active-tab") Temas
        router-link(:to="`/c/${friendlyId}/presentation`" active-class="active-tab") Presentación
        a.tab-disabled(v-if="isAnonymous" title="Inicia sesión para acceder al chat") Chat
        a(v-else :href="chatUrl" target="_blank" rel="noopener") Chat
        router-link(:to="`/c/${friendlyId}/survey`" active-class="active-tab") Encuesta
    .anon-banner(v-if="isAnonymous")
      span ⚠️ Estás en modo anónimo con opciones limitadas. #[router-link(:to="{ path: '/register', query: { redirect: $route.fullPath } }") Regístrate] o #[router-link(:to="{ path: '/login', query: { redirect: $route.fullPath } }") inicia sesión] para acceder por completo a la conferencia.
    router-view(:conference-id="conference.conferenceId || conference.uuid")

  QrCodeModal(v-if="showQr" :friendlyId="friendlyId" @close="showQr = false")
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceIntroMap from '@/components/map/ConferenceIntroMap.vue'
import QrCodeModal from '@/components/QrCodeModal.vue'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'ConferencePage',
  components: { AppHeader, ConferenceIntroMap, QrCodeModal },
  setup() {
    const route      = useRoute()
    const friendlyId = route.params.friendlyId
    const conference = ref(null)
    const loading    = ref(true)
    const error      = ref('')
    const showIntro  = ref(false)
    const showQr     = ref(false)

    const auth = useAuthStore()
    const isAnonymous = !auth.isAuthenticated() || auth.state.role === 'guest'
    const chatHost = location.hostname.startsWith('chat-') ? location.hostname : `chat-${location.hostname}`
    const chatParams = new URLSearchParams({ conference: friendlyId })
    if (auth.isAuthenticated() && auth.state.role !== 'guest') {
      chatParams.set('ib_token', auth.state.token)
    }
    const chatUrl = `${location.protocol}//${chatHost}/?${chatParams.toString()}`

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

    return { friendlyId, conference, loading, error, showIntro, dismissIntro, chatUrl, showQr, isAnonymous }
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
.btn-qr {
  margin-left: auto; padding: 8px 16px; border-radius: 8px; border: 2px solid #c7d2fe;
  background: #eef2ff; color: #4f46e5; font-weight: 600; font-size: 0.85rem; cursor: pointer;
}
.btn-qr:hover { background: #e0e7ff; }
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
.conf-tabs a.tab-disabled {
  cursor: not-allowed;
  color: #9ca3af;
  border-color: #e5e7eb;
  background: #f9fafb;
}
.conf-tabs a.tab-disabled:hover {
  background: #f9fafb;
  border-color: #e5e7eb;
}
.conf-loading, .conf-error { padding: 40px; text-align: center; color: #6b7280; }

.anon-banner {
  margin: 16px 24px 0;
  padding: 10px 16px;
  background: #fef3c7;
  color: #92400e;
  border: 1px solid #fde68a;
  border-radius: 8px;
  font-size: 0.85rem;
}
.anon-banner :deep(a) { color: #4f46e5; font-weight: 600; text-decoration: none; }
.anon-banner :deep(a):hover { text-decoration: underline; }

@media (max-width: 640px) {
  .conf-header { padding: 16px; }
  h1 { font-size: 1.4rem; }
  .conf-tabs { flex-wrap: wrap; gap: 6px; }
  .conf-tabs a { padding: 7px 14px; font-size: 0.85rem; }
  .btn-qr { margin-left: 0; }
  .anon-banner { margin: 12px 16px 0; }
}
</style>
