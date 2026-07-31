<template lang="pug">
.conference-page#main-content(role="main" tabindex="-1")
  AppHeader(v-if="!headerCollapsed")
  LoadingState(v-if="loading" message="Cargando conferencia…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="eventClosed" message="Este evento está desactivado temporalmente. El organizador puede reactivarlo desde el panel.")
  template(v-else-if="conference")
    //- Fullscreen intro map (only when conference has coordinates)
    ConferenceIntroMap(
      v-if="showIntro && privateAccess"
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
          .conf-location(v-if="privateAccess && conference.latitude != null")
            span.location-icon 📍
            span.location-coords {{ conference.latitude.toFixed(4) }}, {{ conference.longitude.toFixed(4) }}
        .conf-schedule(v-if="privateAccess && conference.eventDate")
          span.schedule-icon 🗓️
          span {{ formattedEventDate }}
          span(v-if="conference.startTime") &nbsp;· {{ conference.startTime }}{{ conference.endTime ? ` - ${conference.endTime}` : '' }}
          span(v-if="conference.venue") &nbsp;· {{ conference.venue }}
          .calendar-dropdown(v-if="isUpcoming")
            BaseButton(variant="secondary" size="sm" type="button" @click="showCalendarMenu = !showCalendarMenu") 📅 Agregar a mi calendario
            .calendar-menu(v-if="showCalendarMenu")
              BaseAnchor(variant="ghost" size="sm" :href="googleCalendarUrl" target="_blank" rel="noopener" @click="showCalendarMenu = false") Google Calendar
              BaseButton.calendar-download(variant="ghost" size="sm" type="button" @click="downloadCalendarFile(); showCalendarMenu = false") Descargar .ics (Outlook, Apple)
      .conf-toolbar-wrap(:class="{ 'fade-left': toolbarFadeLeft, 'fade-right': toolbarFadeRight }")
        nav.conf-toolbar(ref="toolbarRef" aria-label="Herramientas del evento" @scroll.passive="updateToolbarFades")
          router-link#onboarding-tab-flyer.tool-btn(:to="`/c/${friendlyId}/flyer`" active-class="active-tab" title="Flyer")
            UiIcon.tool-icon(name="flyer" size="18")
            span.tool-label Flyer
          router-link#onboarding-tab-ticket.tool-btn(v-if="hasCapability('TICKETING_GENERAL') || hasCapability('TICKETING_SEATED')" :to="`/c/${friendlyId}/ticket`" active-class="active-tab" title="Mi boleto")
            UiIcon.tool-icon(name="ticket" size="18")
            span.tool-label Mi boleto
          router-link#onboarding-tab-schedule.tool-btn(:to="`/c/${friendlyId}/schedule`" active-class="active-tab" title="Cronograma")
            UiIcon.tool-icon(name="calendar" size="18")
            span.tool-label Cronograma
          router-link#onboarding-tab-doubts.tool-btn(v-if="privateAllowed('WORD_CLOUD') && toolReleased('DOUBTS')" :to="`/c/${friendlyId}/doubts`" active-class="active-tab" title="Dudas")
            UiIcon.tool-icon(name="help" size="18")
            span.tool-label Dudas
          router-link#onboarding-tab-topics.tool-btn(v-if="privateAllowed('WORD_CLOUD') && toolReleased('TOPICS')" :to="`/c/${friendlyId}/topics`" active-class="active-tab" title="Temas")
            UiIcon.tool-icon(name="idea" size="18")
            span.tool-label Temas
          router-link#onboarding-tab-presentation.tool-btn(v-if="hasCapability('PRESENTATION') && toolReleased('PRESENTATION')" :to="`/c/${friendlyId}/presentation`" active-class="active-tab" title="Presentación")
            UiIcon.tool-icon(name="presentation" size="18")
            span.tool-label Presentación
          span#onboarding-tab-chat.tool-btn.tab-disabled(v-if="privateAllowed('CHAT_BOT') && toolReleased('CHAT') && isAnonymous" title="Regístrate y canjea tu boleto para acceder al chat")
            UiIcon.tool-icon(name="chat" size="18")
            span.tool-label Chat
          BaseAnchor#onboarding-tab-chat.tool-btn.tab-secondary(
            v-else-if="privateAllowed('CHAT_BOT') && toolReleased('CHAT')"
            variant="ghost"
            size="sm"
            :href="chatUrl"
            target="_blank"
            rel="noopener"
            title="Chat en vivo"
            @click="openChat"
          )
            UiIcon.tool-icon(name="chat" size="18")
            span.tool-label Chat
          router-link#onboarding-tab-survey.tool-btn(v-if="privateAllowed('SURVEY')" :to="`/c/${friendlyId}/survey`" active-class="active-tab" title="Encuesta")
            UiIcon.tool-icon(name="survey" size="18")
            span.tool-label Encuesta
          BaseAnchor#onboarding-tab-video.tool-btn(
            v-if="privateAllowed('VIDEO_CONFERENCE') && toolReleased('VIDEO')"
            variant="ghost"
            size="sm"
            :href="`/c/${friendlyId}/video`"
            target="_blank"
            rel="opener"
            title="Abrir videollamada en una pestaña nueva"
            aria-label="Videollamada (abre en una pestaña nueva)"
          )
            UiIcon.tool-icon(name="video" size="18")
            span.tool-label Videollamada
          router-link#onboarding-tab-diagrams.tool-btn(v-if="canvasAllowed('DRAWIO', 'DIAGRAMMING') && toolReleased('DIAGRAMS')" :to="`/c/${friendlyId}/diagrams`" active-class="active-tab" title="Diagramas")
            UiIcon.tool-icon(name="diagram" size="18")
            span.tool-label Diagramas
          router-link#onboarding-tab-whiteboard.tool-btn(v-if="canvasAllowed('EXCALIDRAW', 'WHITEBOARD') && toolReleased('WHITEBOARD')" :to="`/c/${friendlyId}/whiteboard`" active-class="active-tab" title="Pizarra")
            UiIcon.tool-icon(name="whiteboard" size="18")
            span.tool-label Pizarra
          router-link#onboarding-tab-notes.tool-btn(v-if="canvasAllowed('ETHERPAD', 'COLLAB_NOTES') && toolReleased('NOTES')" :to="`/c/${friendlyId}/notes`" active-class="active-tab" title="Notas")
            UiIcon.tool-icon(name="notes" size="18")
            span.tool-label Notas
          router-link#onboarding-tab-ide.tool-btn(v-if="privateAllowed('CODE_IDE') && toolReleased('IDE')" :to="`/c/${friendlyId}/ide`" active-class="active-tab" title="IDE de código")
            UiIcon.tool-icon(name="code" size="18")
            span.tool-label IDE
    .conf-body
      .anon-banner(v-if="isAnonymous && !$route.path.endsWith('/presentation')")
        span ⚠️ Estás en modo anónimo con opciones limitadas. #[router-link(:to="{ path: '/register', query: { redirect: $route.fullPath } }") Regístrate] o #[router-link(:to="{ path: '/login', query: { redirect: $route.fullPath } }") inicia sesión] para acceder por completo a la conferencia.
      .private-access-block(v-if="!privateAccess && !isTicketRoute && !isPublicRoute")
        h2 Registro y boleto requeridos
        p La vista pública se limita a las primeras 5 diapositivas. Regístrate y canjea tu boleto para acceder al resto del evento.
        BaseLink(:to="`/c/${friendlyId}/ticket`") Ver mi boleto / canjear
      router-view(v-else :conference-id="conference.conferenceId || conference.uuid" :presentation-source-url="conference.presentationSourceUrl" :seating-mode="conference.seatingMode" :ticketed="conference.seatingMode !== 'NONE' || hasCapability('TICKETING_GENERAL') || hasCapability('TICKETING_SEATED')" :invite-alias="friendlyId" :access-granted="routeAccess" :presentation-manager="presentationManagementAccess" :canvas-audience-mode="currentCanvasAudienceMode" :canvas-moderator="isCanvasModerator" :event-name="conference.name" :event-description="conference.description" :flyer-base64="conference.flyerBase64" :schedule-markdown="conference.scheduleMarkdown")

    OnboardingTour(storage-key="ib_onboarding_conference_v2" :steps="attendeeTourSteps")

</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import ConferenceIntroMap from '@/components/map/ConferenceIntroMap.vue'
import OnboardingTour from '@/components/OnboardingTour.vue'
import BaseAnchor from '@/components/ui/BaseAnchor.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import UiIcon from '@/components/ui/UiIcon.vue'
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getConferenceByFriendlyId, getTimezones, getActiveEventTypes, joinConference, getConferenceAccess, getPresentationManagementAccess, createChatSsoExchange, getToolAccess } from '@/services/api/usersApi'
import type { ToolKeyName } from '@/services/api/usersApi'
import type { Conference, Timezone, EventCapability } from '@/services/api/types'
import { downloadIcs, buildGoogleCalendarUrl } from '@/utils/calendarLink'
import { useAuthStore } from '@/features/auth/authStore'

const ATTENDEE_TOUR_STEPS = [
  { selector: '#onboarding-tab-doubts', text: 'Aquí envías tus dudas sobre la charla — todos las ven en una nube de palabras en vivo.' },
  { selector: '#onboarding-tab-topics', text: 'Aquí propones temas de interés para futuras conferencias.' },
  { selector: '#onboarding-tab-presentation', text: 'Sigue la presentación en vivo, sincronizada con el presentador.' },
  { selector: '#onboarding-tab-chat', text: 'Aquí puedes conversar en vivo con el grupo y el moderador.' },
  { selector: '#onboarding-tab-survey', text: 'Al terminar, responde la encuesta para obtener tu certificado.' },
  { selector: '#onboarding-tab-video', text: 'Únete a la videollamada del evento. El acceso requiere sesión y boleto cuando el evento está protegido.' },
  { selector: '#onboarding-tab-diagrams', text: 'Consulta y trabaja con los diagramas del evento según el modo configurado.' },
  { selector: '#onboarding-tab-whiteboard', text: 'Usa la pizarra para colaborar o consultar la publicación del moderador.' },
  { selector: '#onboarding-tab-notes', text: 'Toma notas del evento. Según la configuración, pueden ser grupales o individuales.' },
  { selector: '#onboarding-tab-ticket', text: 'Aquí consultas y gestionas tu boleto de acceso al evento.' },
  { selector: '#onboarding-tab-ide', text: 'Accede al IDE de código del evento cuando esté habilitado.' }
]

export default {
  name: 'ConferencePage',
  components: { AppHeader, BaseAnchor, BaseButton, BaseLink, ConferenceIntroMap, EmptyState, FeedbackMessage, LoadingState, OnboardingTour, UiIcon },
  setup() {
    const route      = useRoute()
    const friendlyId = route.params.friendlyId as string
    const conference = ref<Conference | null>(null)
    const loading    = ref(true)
    const error      = ref('')
    const showIntro  = ref(false)
    const timezones  = ref<Timezone[]>([])
    const showCalendarMenu = ref(false)
    const capabilities = ref<Set<string>>(new Set())
    const privateAccess = ref(false)
    const presentationAccess = ref(false)
    const eventClosed = ref(false)
    const presentationManagementAccess = ref(false)
    const headerCollapsed = ref(true)
    const auth = useAuthStore()
    let accessWatchTimer: number | null = null

    // Candado por herramienta (2026-07-27): a diferencia de hasCapability/privateAllowed (fijos
    // por el TIPO de evento), esto lo prende/apaga el moderador en vivo por evento -- arranca
    // todo bloqueado (mapa vacio = { } = false para toda clave) hasta que se resuelva el fetch.
    const toolAccess = ref<Partial<Record<ToolKeyName, boolean>>>({})
    function toolReleased(key: ToolKeyName): boolean {
      return toolAccess.value[key] === true
    }
    async function loadToolAccess() {
      if (!conference.value) return
      try {
        toolAccess.value = await getToolAccess(conference.value.uuid, auth.state.token)
      } catch { /* best-effort: si falla, todo queda bloqueado (fail-closed) */ }
    }

    async function refreshEventAccess() {
      if (!conference.value || eventClosed.value) return false
      try {
        const access = await getConferenceAccess(conference.value.uuid, auth.state.token)
        const active = conference.value.status !== 'CLOSED' && access.eventActive !== false && access.eventStatus !== 'CLOSED'
        if (!active) {
          eventClosed.value = true
          privateAccess.value = false
          presentationAccess.value = false
          return false
        }
        privateAccess.value = !access.ticketRequired || access.hasAccess
        presentationAccess.value = !access.ticketRequired || access.hasAccess || access.presentationAccess === true
        return true
      } catch {
        return true
      }
    }

    // Affordance de scroll del tab bar (auditoría UX): con 11 tabs y overflow-x, en móvil los
    // últimos quedaban invisibles sin ninguna señal. Los gradientes laterales indican "hay más",
    // y al cambiar de ruta el tab activo se trae a la vista.
    const toolbarRef = ref<HTMLElement | null>(null)
    const toolbarFadeLeft = ref(false)
    const toolbarFadeRight = ref(false)

    function updateToolbarFades() {
      const el = toolbarRef.value
      if (!el) return
      toolbarFadeLeft.value = el.scrollLeft > 4
      toolbarFadeRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 4
    }

    function scrollActiveTabIntoView() {
      const el = toolbarRef.value
      const active = el?.querySelector<HTMLElement>('.active-tab')
      active?.scrollIntoView({ inline: 'nearest', block: 'nearest', behavior: 'smooth' })
      updateToolbarFades()
    }

    onMounted(() => {
      void nextTick(scrollActiveTabIntoView)
      window.addEventListener('resize', updateToolbarFades, { passive: true })
    })
    onBeforeUnmount(() => window.removeEventListener('resize', updateToolbarFades))

    watch(() => route.path, () => {
      headerCollapsed.value = true
      void refreshEventAccess()
      void nextTick(scrollActiveTabIntoView)
    })

    function hasCapability(capability: EventCapability): boolean {
      // Sin catálogo cargado (aún cargando o falló), no se ocultan pestañas — evita parpadeo
      // y deja que el backend siga siendo la fuente de verdad autoritativa (409 si no aplica).
      return capabilities.value.size === 0 || capabilities.value.has(capability)
    }

    function privateAllowed(capability: EventCapability): boolean {
      return privateAccess.value && hasCapability(capability)
    }

    function canvasAllowed(tool: 'DRAWIO' | 'EXCALIDRAW' | 'ETHERPAD', capability: EventCapability): boolean {
      const selected = conference.value?.canvasTool
      const configs = conference.value?.canvasConfigs || []
      const enabledByMultipleConfig = configs.length > 0 && configs.some(config => config.tool === tool)
      return privateAllowed(capability) && (enabledByMultipleConfig || (configs.length === 0 && (!selected || selected === tool)))
    }

    const currentCanvasAudienceMode = computed(() => {
      const path = route.path
      const tool = path.endsWith('/diagrams') ? 'DRAWIO'
        : path.endsWith('/whiteboard') ? 'EXCALIDRAW'
          : path.endsWith('/notes') ? 'ETHERPAD' : null
      const configs = conference.value?.canvasConfigs || []
      if (configs.length > 0) {
        const selectedTool = tool || 'ETHERPAD'
        return configs.find(config => config.tool === selectedTool)?.audienceMode || ''
      }
      return conference.value?.canvasAudienceMode || ''
    })

    const isCanvasModerator = computed(() => {
      const userUuid = auth.state.userUuid
      return !!userUuid && (userUuid === conference.value?.createdByUserUuid || auth.isAdmin())
    })

    const isTicketRoute = computed(() => route.path.endsWith('/ticket'))
    const isPublicRoute = computed(() => route.path.endsWith('/presentation'))
    // The presentation route has a narrower authorization contract than the
    // rest of the private event tools.
    const routeAccess = computed(() => isPublicRoute.value ? presentationAccess.value : privateAccess.value)

    const isAnonymous = !auth.isAuthenticated() || auth.state.role === 'guest'
    const attendeeTourSteps = ATTENDEE_TOUR_STEPS
    const chatHost = location.hostname.startsWith('chat-') ? location.hostname : `chat-${location.hostname}`
    // Sin token en el link: el JWT de sesión nunca debe viajar en la URL (queda en historial del
    // navegador y en logs de acceso de cualquier proxy delante del subdominio chat-*). El click
    // handler de abajo pide un código de intercambio de un solo uso (TTL 60s) justo antes de
    // navegar; este href "pelado" solo sirve de fallback para clic-derecho/abrir-en-pestaña-nueva.
    const chatUrl = `${location.protocol}//${chatHost}/?${new URLSearchParams({ conference: friendlyId }).toString()}`

    async function openChat(event: MouseEvent) {
      if (!auth.isAuthenticated() || auth.state.role === 'guest') return
      // Deja pasar clic central/ctrl/cmd para que el navegador maneje "abrir en pestaña nueva" con
      // el fallback sin sesión (mejor eso que bloquear el gesto nativo del usuario).
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return
      event.preventDefault()
      try {
        const { code } = await createChatSsoExchange(auth.state.token as string)
        const params = new URLSearchParams({ conference: friendlyId, sso_code: code })
        window.open(`${location.protocol}//${chatHost}/?${params.toString()}`, '_blank', 'noopener')
      } catch {
        window.open(chatUrl, '_blank', 'noopener')
      }
    }

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
        // sessionStorage is intentionally tab-scoped. If this page was opened
        // from the dashboard, wait for the same-origin opener to hand off the
        // existing session before evaluating ticket/presentation access.
        await auth.waitForSessionBridge()
        const [conf, tzList, eventTypes] = await Promise.all([
          getConferenceByFriendlyId(friendlyId),
          getTimezones().catch(() => []),
          getActiveEventTypes().catch(() => [])
        ])
        conference.value = conf
        const access = await getConferenceAccess(conf.uuid, auth.state.token)
        const active = conf.status !== 'CLOSED' && access.eventActive !== false && access.eventStatus !== 'CLOSED'
        if (!active) {
          eventClosed.value = true
          return
        }
        privateAccess.value = !access.ticketRequired || access.hasAccess
        presentationAccess.value = !access.ticketRequired || access.hasAccess || access.presentationAccess === true
        if (auth.state.token && auth.state.role !== 'guest') {
          presentationManagementAccess.value = await getPresentationManagementAccess(conf.uuid, auth.state.token)
        }
        timezones.value = tzList
        const eventType = eventTypes.find((t) => t.key === conf.eventTypeKey)
        if (eventType) capabilities.value = new Set(eventType.capabilities)
        // Show intro only when conference has a location
        showIntro.value = conference.value?.latitude != null
        void loadToolAccess()
      } catch (e: any) {
        error.value = 'Conferencia no encontrada. Verifica el ID.'
      } finally {
        loading.value = false
      }

      // Inscripción implícita: quien llega por un link/QR compartido (el camino normal) nunca
      // pasaba por /dashboard/join, así que nunca quedaba ConferenceMembership ni se emitía el
      // boleto automático (GENERAL/NONE, ver JoinConferenceUseCase) — "Mi boleto" quedaba
      // efectivamente inalcanzable para ese flujo. Best-effort: nunca bloquea el render ni
      // muestra error si falla (ej. conferencia ya vencida).
      if (conference.value && !eventClosed.value && auth.isAuthenticated() && auth.state.role !== 'guest') {
        joinConference(friendlyId, auth.state.token as string).catch(() => {})
      }
      if (conference.value && !eventClosed.value) {
        // This is an event-lifecycle watchdog, not presentation synchronization. It removes
        // an already-open public event page shortly after the organizer deactivates the event;
        // each backend tool endpoint remains authoritative and rejects direct requests too.
        accessWatchTimer = window.setInterval(() => { void refreshEventAccess() }, 10000)
      }
    })

    onBeforeUnmount(() => {
      if (accessWatchTimer != null) window.clearInterval(accessWatchTimer)
    })

    return {
      friendlyId, conference, loading, error, showIntro, dismissIntro, chatUrl, openChat,
      isAnonymous, attendeeTourSteps, formattedEventDate, isUpcoming, showCalendarMenu,
      googleCalendarUrl, downloadCalendarFile, hasCapability, privateAllowed, canvasAllowed, isCanvasModerator, currentCanvasAudienceMode,
      privateAccess, presentationAccess, presentationManagementAccess, routeAccess, isTicketRoute, isPublicRoute, headerCollapsed, eventClosed,
      toolbarRef, toolbarFadeLeft, toolbarFadeRight, updateToolbarFades, toolReleased
    }
  }
}
</script>

<style scoped>
/* flex column de altura completa: las paginas "de lienzo" (diagramas/pizarra/notas/video) usan
   flex:1 en su propia raiz para llenar exactamente el alto restante bajo el header colapsado --
   antes reservaban un calc(100vh - 220px) fijo que asumia el header SIN colapsar (~220px), pero
   en estas rutas headerCollapsed=true oculta AppHeader y encoge .conf-header, dejando ~150px+ de
   espacio en blanco sin usar debajo del iframe (reportado 2026-07-19). */
.conference-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--color-bg); }
.conf-body { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.conf-header { padding: 24px; background: var(--color-surface); border-bottom: 1px solid var(--color-border-subtle); }
.conf-title-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 12px; }
h1 { margin: 0; color: var(--color-heading); }
.conf-location { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; color: var(--color-text-muted); }
.location-coords { font-family: monospace; color: var(--color-primary); }
.conf-schedule {
  display: flex; align-items: center; flex-wrap: wrap; gap: 4px;
  font-size: 0.85rem; color: var(--color-text-secondary); margin-bottom: 12px;
}
.schedule-icon { margin-right: 2px; }
.calendar-dropdown { position: relative; margin-left: 12px; }
.calendar-menu {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 20;
  background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12); overflow: hidden; min-width: 200px;
  display: flex; flex-direction: column;
}
.calendar-download { width: 100%; justify-content: flex-start; }
.calendar-menu a, .calendar-menu button {
  padding: 10px 16px; text-align: left; font-size: 0.85rem; color: var(--color-text-secondary);
  text-decoration: none; background: none; border: none; cursor: pointer; font-family: inherit;
}
.calendar-menu a:hover, .calendar-menu button:hover { background: var(--color-surface-muted); }

.btn-collapse-toggle {
  border: none; background: none; cursor: pointer; color: var(--color-text-muted); font-size: 0.78rem;
  font-weight: 600; padding: 2px 4px; margin-bottom: 6px;
}
.btn-collapse-toggle:hover { color: var(--color-primary); }
.conf-header.collapsed { padding: 8px 24px; }
.conf-header.collapsed .btn-collapse-toggle { margin-bottom: 4px; font-size: 0.9rem; color: var(--color-heading); }

.conf-toolbar-wrap { position: relative; }
.conf-toolbar { display: flex; gap: 4px; overflow-x: auto; padding-bottom: 2px; }
/* Gradientes laterales: señal de "hay más tabs" cuando el scroll no está en el borde. */
.conf-toolbar-wrap::before,
.conf-toolbar-wrap::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 2px;
  width: 28px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s;
  z-index: 1;
}
.conf-toolbar-wrap::before { left: 0; background: linear-gradient(to right, var(--color-surface), transparent); }
.conf-toolbar-wrap::after { right: 0; background: linear-gradient(to left, var(--color-surface), transparent); }
.conf-toolbar-wrap.fade-left::before { opacity: 1; }
.conf-toolbar-wrap.fade-right::after { opacity: 1; }
.tool-btn {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px; min-width: 56px; padding: 6px 8px;
  border-radius: 8px; text-decoration: none;
  border: 1.5px solid transparent;
  color: var(--color-primary);
  transition: all 0.15s ease;
  flex-shrink: 0;
}
.tool-icon { font-size: 1.1rem; line-height: 1; }
.tool-label { font-size: 0.65rem; font-weight: 600; white-space: nowrap; }
.tool-btn:hover:not(.active-tab) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-border);
}
.tool-btn.active-tab {
  background: var(--color-primary);
  color: var(--color-text-inverse) !important;
  border-color: var(--color-primary);
}
.tool-btn.tab-disabled {
  cursor: not-allowed;
  color: var(--color-text-muted);
}
.tool-btn.tab-secondary {
  color: var(--color-text-muted);
  font-weight: 500;
}
.tool-btn.tab-secondary:hover {
  color: var(--color-text-muted);
  background: var(--color-surface-muted);
  border-color: var(--color-border-subtle);
}

.anon-banner {
  margin: 16px 24px 0;
  padding: 10px 16px;
  background: var(--color-warning-soft);
  color: var(--color-warning);
  border: 1px solid var(--color-warning);
  border-radius: 8px;
  font-size: 0.85rem;
}
.anon-banner :deep(a) { color: var(--color-primary); font-weight: 600; text-decoration: none; }
.anon-banner :deep(a):hover { text-decoration: underline; }

@media (max-width: 640px) {
  .conf-header { padding: 16px; }
  h1 { font-size: 1.4rem; }
  .tool-btn { min-width: 48px; padding: 5px 6px; }
  .tool-label { font-size: 0.6rem; }
  .anon-banner { margin: 12px 16px 0; }
}
</style>
