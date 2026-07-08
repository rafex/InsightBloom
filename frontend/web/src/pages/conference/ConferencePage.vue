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
      :flyer-url="conference.flyerBase64"
      @enter="dismissIntro"
    )

    .conf-header
      .conf-title-row
        h1 {{ conference.name }}
        .conf-location(v-if="conference.latitude != null")
          span.location-icon 📍
          span.location-coords {{ conference.latitude.toFixed(4) }}, {{ conference.longitude.toFixed(4) }}
        button.btn-qr(type="button" @click="showQr = true") 📱 Mostrar QR
      .conf-schedule(v-if="conference.eventDate")
        span.schedule-icon 🗓️
        span {{ formattedEventDate }}
        span(v-if="conference.startTime") &nbsp;· {{ conference.startTime }}{{ conference.endTime ? ` - ${conference.endTime}` : '' }}
        span(v-if="conference.venue") &nbsp;· {{ conference.venue }}
        .calendar-dropdown(v-if="isUpcoming")
          button.btn-calendar(type="button" @click="showCalendarMenu = !showCalendarMenu") 📅 Agregar a mi calendario
          .calendar-menu(v-if="showCalendarMenu")
            a(:href="googleCalendarUrl" target="_blank" rel="noopener" @click="showCalendarMenu = false") Google Calendar
            button(type="button" @click="downloadCalendarFile(); showCalendarMenu = false") Descargar .ics (Outlook, Apple)
      .conf-tabs
        router-link#onboarding-tab-doubts(:to="`/c/${friendlyId}/doubts`" active-class="active-tab") Dudas
        router-link#onboarding-tab-topics(:to="`/c/${friendlyId}/topics`" active-class="active-tab") Temas
        router-link#onboarding-tab-presentation(:to="`/c/${friendlyId}/presentation`" active-class="active-tab") Presentación
        a.tab-disabled(v-if="isAnonymous" title="Inicia sesión para acceder al chat") Chat
        a.tab-secondary(v-else :href="chatUrl" target="_blank" rel="noopener" title="Chat en vivo (opcional)") Chat
        router-link#onboarding-tab-survey(:to="`/c/${friendlyId}/survey`" active-class="active-tab") Encuesta
        router-link(v-if="conference.seatingMode && conference.seatingMode !== 'NONE'" :to="`/c/${friendlyId}/ticket`" active-class="active-tab") 🎟️ Mi boleto
    .anon-banner(v-if="isAnonymous && !$route.path.endsWith('/presentation')")
      span ⚠️ Estás en modo anónimo con opciones limitadas. #[router-link(:to="{ path: '/register', query: { redirect: $route.fullPath } }") Regístrate] o #[router-link(:to="{ path: '/login', query: { redirect: $route.fullPath } }") inicia sesión] para acceder por completo a la conferencia.
    router-view(:conference-id="conference.conferenceId || conference.uuid" :presentation-source-url="conference.presentationSourceUrl" :seating-mode="conference.seatingMode")

    OnboardingTour(storage-key="ib_onboarding_conference" :steps="attendeeTourSteps")

  QrCodeModal(v-if="showQr" :friendlyId="friendlyId" @close="showQr = false")
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceIntroMap from '@/components/map/ConferenceIntroMap.vue'
import QrCodeModal from '@/components/QrCodeModal.vue'
import OnboardingTour from '@/components/OnboardingTour.vue'
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId, getTimezones } from '@/services/api/usersApi'
import type { Conference, Timezone } from '@/services/api/types'
import { downloadIcs, buildGoogleCalendarUrl } from '@/utils/calendarLink'
import { useAuthStore } from '@/features/auth/authStore'

const ATTENDEE_TOUR_STEPS = [
  { selector: '#onboarding-tab-doubts', text: 'Aquí envías tus dudas sobre la charla — todos las ven en una nube de palabras en vivo.' },
  { selector: '#onboarding-tab-topics', text: 'Aquí propones temas de interés para futuras conferencias.' },
  { selector: '#onboarding-tab-presentation', text: 'Sigue la presentación en vivo, sincronizada con el presentador.' },
  { selector: '#onboarding-tab-survey', text: 'Al terminar, responde la encuesta para obtener tu certificado.' }
]

export default {
  name: 'ConferencePage',
  components: { AppHeader, ConferenceIntroMap, QrCodeModal, OnboardingTour },
  setup() {
    const route      = useRoute()
    const friendlyId = route.params.friendlyId as string
    const conference = ref<Conference | null>(null)
    const loading    = ref(true)
    const error      = ref('')
    const showIntro  = ref(false)
    const showQr     = ref(false)
    const timezones  = ref<Timezone[]>([])
    const showCalendarMenu = ref(false)

    const auth = useAuthStore()
    const isAnonymous = !auth.isAuthenticated() || auth.state.role === 'guest'
    const attendeeTourSteps = ATTENDEE_TOUR_STEPS
    const chatHost = location.hostname.startsWith('chat-') ? location.hostname : `chat-${location.hostname}`
    const chatParams = new URLSearchParams({ conference: friendlyId })
    if (auth.isAuthenticated() && auth.state.role !== 'guest') {
      chatParams.set('ib_token', auth.state.token as string)
    }
    const chatUrl = `${location.protocol}//${chatHost}/?${chatParams.toString()}`

    function dismissIntro() {
      showIntro.value = false
    }

    const utcOffsetMinutes = computed(() => {
      const tz = timezones.value.find((t) => t.id === conference.value?.timezoneId)
      return tz ? tz.utcOffsetMinutes : -360 // GMT-6 por defecto
    })

    const formattedEventDate = computed(() => {
      if (!conference.value?.eventDate) return ''
      const [y, m, d] = conference.value.eventDate.split('-').map(Number)
      return new Date(y, m - 1, d).toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' })
    })

    const isUpcoming = computed(() => {
      if (!conference.value?.eventDate) return false
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const [y, m, d] = conference.value.eventDate.split('-').map(Number)
      return new Date(y, m - 1, d) >= today
    })

    function calendarOpts() {
      return {
        name: conference.value!.name,
        eventDate: conference.value!.eventDate as string,
        startTime: conference.value!.startTime as string,
        endTime: conference.value!.endTime as string | undefined,
        venue: conference.value!.venue as string | undefined,
        utcOffsetMinutes: utcOffsetMinutes.value
      }
    }

    const googleCalendarUrl = computed(() => conference.value ? buildGoogleCalendarUrl(calendarOpts()) : '#')

    function downloadCalendarFile() {
      downloadIcs(calendarOpts())
    }

    onMounted(async () => {
      try {
        const [conf, tzList] = await Promise.all([
          getConferenceByFriendlyId(friendlyId),
          getTimezones().catch(() => [])
        ])
        conference.value = conf
        timezones.value = tzList
        // Show intro only when conference has a location
        showIntro.value = conference.value?.latitude != null
      } catch (e: any) {
        error.value = 'Conferencia no encontrada. Verifica el ID.'
      } finally {
        loading.value = false
      }
    })

    return {
      friendlyId, conference, loading, error, showIntro, dismissIntro, chatUrl, showQr,
      isAnonymous, attendeeTourSteps, formattedEventDate, isUpcoming, showCalendarMenu,
      googleCalendarUrl, downloadCalendarFile
    }
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
.conf-schedule {
  display: flex; align-items: center; flex-wrap: wrap; gap: 4px;
  font-size: 0.85rem; color: #4b5563; margin-bottom: 12px;
}
.schedule-icon { margin-right: 2px; }
.calendar-dropdown { position: relative; margin-left: 12px; }
.btn-calendar {
  padding: 6px 14px; border-radius: 8px; border: 1.5px solid #4f46e5;
  background: #fff; color: #4f46e5; font-weight: 600; font-size: 0.8rem; cursor: pointer;
}
.btn-calendar:hover { background: #eef2ff; }
.calendar-menu {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 20;
  background: #fff; border: 1px solid #e5e7eb; border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12); overflow: hidden; min-width: 200px;
  display: flex; flex-direction: column;
}
.calendar-menu a, .calendar-menu button {
  padding: 10px 16px; text-align: left; font-size: 0.85rem; color: #374151;
  text-decoration: none; background: none; border: none; cursor: pointer; font-family: inherit;
}
.calendar-menu a:hover, .calendar-menu button:hover { background: #f3f4f6; }

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
.conf-tabs a.tab-secondary {
  color: #9ca3af;
  border-color: #e5e7eb;
  background: #fff;
  font-weight: 500;
  font-size: 0.85rem;
}
.conf-tabs a.tab-secondary:hover {
  color: #6b7280;
  border-color: #d1d5db;
  background: #f9fafb;
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
