<template lang="pug">
.conference-page
  AppHeader(v-if="!headerCollapsed")
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

    .conf-header(:class="{ collapsed: headerCollapsed }")
      button.btn-collapse-toggle(type="button" @click="headerCollapsed = !headerCollapsed" :title="headerCollapsed ? 'Mostrar cabecera' : 'Ocultar cabecera y dar más espacio'")
        span(v-if="headerCollapsed") {{ conference.name }} ▾
        span(v-else) ▴ Compactar
      template(v-if="!headerCollapsed")
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
      nav.conf-toolbar
        router-link#onboarding-tab-doubts.tool-btn(v-if="hasCapability('WORD_CLOUD')" :to="`/c/${friendlyId}/doubts`" active-class="active-tab" title="Dudas")
          span.tool-icon ❓
          span.tool-label Dudas
        router-link#onboarding-tab-topics.tool-btn(v-if="hasCapability('WORD_CLOUD')" :to="`/c/${friendlyId}/topics`" active-class="active-tab" title="Temas")
          span.tool-icon 💡
          span.tool-label Temas
        router-link#onboarding-tab-presentation.tool-btn(v-if="hasCapability('PRESENTATION')" :to="`/c/${friendlyId}/presentation`" active-class="active-tab" title="Presentación")
          span.tool-icon 📽️
          span.tool-label Presentación
        a.tool-btn.tab-disabled(v-if="hasCapability('CHAT_BOT') && isAnonymous" title="Inicia sesión para acceder al chat")
          span.tool-icon 💬
          span.tool-label Chat
        a.tool-btn.tab-secondary(v-else-if="hasCapability('CHAT_BOT')" :href="chatUrl" target="_blank" rel="noopener" title="Chat en vivo (opcional)")
          span.tool-icon 💬
          span.tool-label Chat
        router-link#onboarding-tab-survey.tool-btn(v-if="hasCapability('SURVEY')" :to="`/c/${friendlyId}/survey`" active-class="active-tab" title="Encuesta")
          span.tool-icon 📝
          span.tool-label Encuesta
        router-link.tool-btn(v-if="hasCapability('VIDEO_CONFERENCE')" :to="`/c/${friendlyId}/video`" active-class="active-tab" title="Videollamada")
          span.tool-icon 🎥
          span.tool-label Videollamada
        router-link.tool-btn(v-if="hasCapability('DIAGRAMMING')" :to="`/c/${friendlyId}/diagrams`" active-class="active-tab" title="Diagramas")
          span.tool-icon 🧩
          span.tool-label Diagramas
        router-link.tool-btn(v-if="hasCapability('WHITEBOARD')" :to="`/c/${friendlyId}/whiteboard`" active-class="active-tab" title="Pizarra")
          span.tool-icon 🖍️
          span.tool-label Pizarra
        router-link.tool-btn(v-if="hasCapability('COLLAB_NOTES')" :to="`/c/${friendlyId}/notes`" active-class="active-tab" title="Notas")
          span.tool-icon 🗒️
          span.tool-label Notas
        router-link.tool-btn(v-if="conference.seatingMode && conference.seatingMode !== 'NONE'" :to="`/c/${friendlyId}/ticket`" active-class="active-tab" title="Mi boleto")
          span.tool-icon 🎟️
          span.tool-label Mi boleto
        router-link.tool-btn(v-if="hasCapability('CODE_IDE')" :to="`/c/${friendlyId}/ide`" active-class="active-tab" title="IDE de código")
          span.tool-icon 💻
          span.tool-label IDE
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId, getTimezones, getActiveEventTypes } from '@/services/api/usersApi'
import type { Conference, Timezone, EventCapability } from '@/services/api/types'
import { downloadIcs, buildGoogleCalendarUrl } from '@/utils/calendarLink'
import { useAuthStore } from '@/features/auth/authStore'

// Rutas de herramientas de "lienzo" (presentación, diagramas, notas, video) donde el espacio
// vertical importa mas que el titulo/horario del evento — se colapsa la cabecera por defecto,
// el usuario puede reabrirla con el boton toggle.
const TOOL_ROUTE_SUFFIXES = ['/presentation', '/diagrams', '/notes', '/video', '/whiteboard']

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
    const capabilities = ref<Set<string>>(new Set())
    const headerCollapsed = ref(TOOL_ROUTE_SUFFIXES.some((s) => route.path.endsWith(s)))

    watch(() => route.path, (newPath) => {
      headerCollapsed.value = TOOL_ROUTE_SUFFIXES.some((s) => newPath.endsWith(s))
    })

    function hasCapability(capability: EventCapability): boolean {
      // Sin catálogo cargado (aún cargando o falló), no se ocultan pestañas — evita parpadeo
      // y deja que el backend siga siendo la fuente de verdad autoritativa (409 si no aplica).
      return capabilities.value.size === 0 || capabilities.value.has(capability)
    }

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
        const [conf, tzList, eventTypes] = await Promise.all([
          getConferenceByFriendlyId(friendlyId),
          getTimezones().catch(() => []),
          getActiveEventTypes().catch(() => [])
        ])
        conference.value = conf
        timezones.value = tzList
        const eventType = eventTypes.find((t) => t.key === conf.eventTypeKey)
        if (eventType) capabilities.value = new Set(eventType.capabilities)
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
      googleCalendarUrl, downloadCalendarFile, hasCapability, headerCollapsed
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

.btn-collapse-toggle {
  border: none; background: none; cursor: pointer; color: #6b7280; font-size: 0.78rem;
  font-weight: 600; padding: 2px 4px; margin-bottom: 6px;
}
.btn-collapse-toggle:hover { color: #4f46e5; }
.conf-header.collapsed { padding: 8px 24px; }
.conf-header.collapsed .btn-collapse-toggle { margin-bottom: 4px; font-size: 0.9rem; color: #1e1b4b; }

.conf-toolbar { display: flex; gap: 4px; overflow-x: auto; padding-bottom: 2px; }
.tool-btn {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px; min-width: 56px; padding: 6px 8px;
  border-radius: 8px; text-decoration: none;
  border: 1.5px solid transparent;
  color: #4f46e5;
  transition: all 0.15s ease;
  flex-shrink: 0;
}
.tool-icon { font-size: 1.1rem; line-height: 1; }
.tool-label { font-size: 0.65rem; font-weight: 600; white-space: nowrap; }
.tool-btn:hover:not(.active-tab) {
  background: #eef2ff;
  border-color: #a5b4fc;
}
.tool-btn.active-tab {
  background: #4f46e5;
  color: #ffffff !important;
  border-color: #4f46e5;
}
.tool-btn.tab-disabled {
  cursor: not-allowed;
  color: #9ca3af;
}
.tool-btn.tab-secondary {
  color: #9ca3af;
  font-weight: 500;
}
.tool-btn.tab-secondary:hover {
  color: #6b7280;
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
  .tool-btn { min-width: 48px; padding: 5px 6px; }
  .tool-label { font-size: 0.6rem; }
  .btn-qr { margin-left: 0; }
  .anon-banner { margin: 12px 16px 0; }
}
</style>
