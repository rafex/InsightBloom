<template lang="pug">
nav.tools-nav(v-if="conferenceId")
  DropdownMenu(v-if="hasCapability('PRESENTATION') || hasCapability('SURVEY')" label="Contenido")
    router-link(v-if="hasCapability('PRESENTATION')" :to="`/dashboard/conferences/${conferenceId}/presentation`") Gestionar presentación
    router-link(v-if="hasCapability('PRESENTATION')" :to="`/dashboard/conferences/${conferenceId}/speaker`") Presentar en vivo
    router-link(v-if="hasCapability('SURVEY')" :to="`/dashboard/conferences/${conferenceId}/survey`") Encuesta
    BaseAnchor.menu-public-link(v-if="hasCapability('PRESENTATION') && friendlyId" variant="ghost" size="sm" :href="`/c/${friendlyId}/presentation`" @click.prevent="openPublic") Ver vista pública
  DropdownMenu(label="Moderación")
    router-link(v-if="hasCapability('WORD_CLOUD')" :to="`/dashboard/conferences/${conferenceId}/moderation/messages`") Mensajes
    router-link(v-if="hasCapability('WORD_CLOUD')" :to="`/dashboard/conferences/${conferenceId}/moderation/words`") Palabras/Nube
    router-link(v-if="hasCapability('CODE_IDE')" :to="`/dashboard/conferences/${conferenceId}/moderation/ide`") Editor Monaco
    router-link(:to="`/dashboard/conferences/${conferenceId}/moderation/tools`") Herramientas
    BaseAnchor.menu-video-link(
      v-if="hasCapability('VIDEO_CONFERENCE') && friendlyId"
      variant="ghost"
      size="sm"
      :href="`/c/${friendlyId}/video`"
      target="_blank"
      rel="opener"
      title="Abrir la videollamada en una pestaña nueva"
      aria-label="Entrar a la videollamada (abre en una pestaña nueva)"
    ) Videollamada ↗
    router-link(v-if="isOrganizer && (hasCapability('VIDEO_CONFERENCE') || hasCapability('CODE_IDE'))" :to="`/dashboard/conferences/${conferenceId}/device-blocks`") Bloqueos
  DropdownMenu(v-if="hasTicketing() || hasSeating()" label="Acceso")
    router-link(v-if="hasTicketing()" :to="`/dashboard/conferences/${conferenceId}/tickets`") Boletos
    router-link(v-if="hasSeating()" :to="`/dashboard/conferences/${conferenceId}/check-in`") Check-in
    router-link(v-if="conference?.seatingMode === 'SEATED'" :to="`/dashboard/conferences/${conferenceId}/venue-map`") Mapa de asientos
  DropdownMenu(v-if="isOrganizer" label="Configuración")
    router-link(:to="`/dashboard/conferences/${conferenceId}/edit`") Datos del evento
    router-link(:to="`/dashboard/conferences/${conferenceId}/config`") Configuración del evento
    router-link(:to="`/dashboard/conferences/${conferenceId}/${conference?.certificateEngine === 'HTML_CHROME' ? 'certificate' : 'certificate-legacy'}`") Certificado
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import BaseAnchor from '@/components/ui/BaseAnchor.vue'
import { getConference, getActiveEventTypes } from '@/services/api/usersApi'
import type { Conference, EventCapability, EventType } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { eventTypeHasCapability } from '@/features/conferences/capabilities'

/**
 * Mismo agrupamiento Presentador/Público/Moderación/Presentación que la columna "Modos" +
 * "Presentación" de ConferencesListPage.vue (ver hasCapability ahi) -- self-contained (fetch
 * propio de la conferencia y del catalogo de tipos de evento) para poder insertarse tal cual en
 * cualquier pagina de herramienta (Presentar/Encuesta/Presentación/Moderación) sin que cada una
 * duplique esta logica. Reemplaza el viejo `nav.sub-links` hardcodeado (lista plana, sin agrupar)
 * que existia hasta el rediseno de la columna Modos.
 */
export default {
  name: 'ConferenceToolsNav',
  components: { BaseAnchor, DropdownMenu },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const isOrganizer = auth.isOrganizer()
    const conference = ref<Conference | null>(null)
    const friendlyId = ref('')
    const eventTypeKey = ref<string | undefined>(undefined)
    const eventTypes = ref<EventType[]>([])

    function hasCapability(capability: EventCapability): boolean {
      return eventTypeHasCapability(eventTypes.value, eventTypeKey.value, capability)
    }

    function hasTicketing(): boolean {
      return hasCapability('TICKETING_GENERAL') || hasCapability('TICKETING_SEATED')
    }

    function hasSeating(): boolean {
      return Boolean(conference.value?.seatingMode && conference.value.seatingMode !== 'NONE')
    }

    function openPublic() {
      const url = `/c/${friendlyId.value}/presentation`
      const child = window.open(url, '_blank')
      if (!child) window.location.assign(url)
    }

    onMounted(async () => {
      if (!props.conferenceId) return
      try {
        const [conf, types] = await Promise.all([
          getConference(props.conferenceId, auth.state.token as string),
          getActiveEventTypes()
        ])
        conference.value = conf
        friendlyId.value = conf?.friendlyId || ''
        eventTypeKey.value = conf?.eventTypeKey
        eventTypes.value = types
      } catch (e: any) { /* nav degrada a "mostrar todo" si el fetch falla, ver hasCapability */ }
    })

    return { conference, friendlyId, isOrganizer, hasCapability, hasTicketing, hasSeating, openPublic }
  }
}
</script>

<style scoped>
.tools-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
</style>
