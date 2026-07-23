<template lang="pug">
nav.tools-nav(v-if="conferenceId")
  DropdownMenu(v-if="hasCapability('PRESENTATION') || hasCapability('SURVEY')" label="Presentador")
    router-link(v-if="hasCapability('PRESENTATION')" :to="`/dashboard/conferences/${conferenceId}/speaker`") Presentar
    router-link(v-if="hasCapability('SURVEY')" :to="`/dashboard/conferences/${conferenceId}/survey`") Encuesta
  a.btn-ghost(v-if="hasCapability('PRESENTATION') && friendlyId" :href="`/c/${friendlyId}/presentation`" @click.prevent="openPublic") 📺 Público
  DropdownMenu(v-if="hasCapability('WORD_CLOUD')" label="Moderación")
    router-link(:to="`/dashboard/conferences/${conferenceId}/moderation/messages`") Mensajes
    router-link(:to="`/dashboard/conferences/${conferenceId}/moderation/words`") Palabras/Nube
    router-link(v-if="hasCapability('CODE_IDE')" :to="`/dashboard/conferences/${conferenceId}/moderation/ide`") Editor Monaco
  router-link.btn-ghost(v-if="hasCapability('PRESENTATION')" :to="`/dashboard/conferences/${conferenceId}/presentation`") Presentación
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import DropdownMenu from '@/components/DropdownMenu.vue'
import { getConference, getActiveEventTypes } from '@/services/api/usersApi'
import type { EventCapability, EventType } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'

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
  components: { DropdownMenu },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const friendlyId = ref('')
    const eventTypeKey = ref<string | undefined>(undefined)
    const eventTypes = ref<EventType[]>([])

    function hasCapability(capability: EventCapability): boolean {
      if (eventTypes.value.length === 0) return true
      const type = eventTypes.value.find((t) => t.key === eventTypeKey.value)
      return type ? type.capabilities.includes(capability) : true
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
        friendlyId.value = conf?.friendlyId || ''
        eventTypeKey.value = conf?.eventTypeKey
        eventTypes.value = types
      } catch (e: any) { /* nav degrada a "mostrar todo" si el fetch falla, ver hasCapability */ }
    })

    return { friendlyId, hasCapability, openPublic }
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
.btn-ghost {
  display: inline-block;
  padding: 6px 14px;
  border: 1.5px solid #e5e7eb;
  border-radius: 20px;
  text-decoration: none;
  color: #374151;
  font-size: 0.82rem;
  font-weight: 500;
  transition: all 0.15s;
  background: none;
}
.btn-ghost:hover { border-color: #a5b4fc; color: #4f46e5; }
.btn-ghost.router-link-active { background: #4f46e5; color: #fff; border-color: #4f46e5; }
</style>
