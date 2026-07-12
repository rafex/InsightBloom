<template lang="pug">
.diagramming-page
  .loading-text(v-if="loading") Cargando pizarra de diagramas...
  .unavailable(v-else-if="!drawioUrl")
    p ⚠️ La pizarra de diagramas no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  iframe.drawio-frame(v-else ref="frameRef" :src="drawioUrl" title="Diagramas" allow="clipboard-write")
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getIntegrationConfig, getEventDiagram, saveEventDiagram } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'DiagrammingPage',
  props: {
    conferenceId: { type: String, default: '' }
  },
  setup(props: { conferenceId?: string }) {
    const route = useRoute()
    const auth = useAuthStore()
    const friendlyId = route.params.friendlyId as string
    const loading = ref(true)
    const drawioBaseUrl = ref('')
    const frameRef = ref<HTMLIFrameElement | null>(null)
    let savedXml = ''

    onMounted(async () => {
      try {
        const [config, diagram] = await Promise.all([
          getIntegrationConfig(),
          props.conferenceId ? getEventDiagram(props.conferenceId, auth.state.token as string) : Promise.resolve({ xml: '' })
        ])
        drawioBaseUrl.value = config.drawioBaseUrl || ''
        savedXml = diagram.xml || ''
      } catch (e: any) {
        drawioBaseUrl.value = ''
      } finally {
        loading.value = false
      }
    })

    // Modo embed=1 de drawio: la pagina que lo embebe (esta) debe implementar el protocolo
    // postMessage (handshake init/load/save) para orquestar el diagrama — de lo contrario el
    // canvas se queda esperando indefinidamente (ver bug previo, ya corregido). Guardamos el XML
    // en el backend (columna efimera, TTL igual que notas/DEC-0020) para poder descargarlo y
    // distribuirlo despues, sin depender de que drawio persista nada del lado del servidor.
    // drawio ya no tiene ingress publico propio: esta detras de insightbloom-tools-gateway,
    // que exige sesion de InsightBloom antes de reenviar el request al pod real (evita que
    // pegar la URL directo en el navegador de acceso sin login). ib_token en la query arranca
    // esa sesion en el primer request; el gateway responde con una cookie para el resto.
    const drawioUrl = computed(() => {
      if (!drawioBaseUrl.value) return ''
      const token = auth.state.token ? `&ib_token=${encodeURIComponent(auth.state.token)}` : ''
      return `${drawioBaseUrl.value}/?embed=1&proto=json&spin=1&noSaveBtn=0&saveAndExit=0${token}`
    })

    function postToFrame(message: Record<string, unknown>) {
      frameRef.value?.contentWindow?.postMessage(JSON.stringify(message), '*')
    }

    async function persist(xml: string) {
      if (!props.conferenceId || xml === savedXml) return
      savedXml = xml
      try {
        await saveEventDiagram(props.conferenceId, xml, auth.state.token as string)
      } catch (e: any) {
        // best-effort: un fallo de red no debe interrumpir la edicion del diagrama
      }
    }

    function onMessage(evt: MessageEvent) {
      if (!frameRef.value || evt.source !== frameRef.value.contentWindow) return
      let msg: any
      try {
        msg = typeof evt.data === 'string' ? JSON.parse(evt.data) : evt.data
      } catch (e) {
        return
      }
      if (!msg || !msg.event) return

      if (msg.event === 'init') {
        postToFrame({ action: 'load', xml: savedXml, autosave: 1 })
      } else if (msg.event === 'autosave' || msg.event === 'save') {
        if (typeof msg.xml === 'string') persist(msg.xml)
      } else if (msg.event === 'export') {
        if (typeof msg.data === 'string') persist(msg.data)
      }
    }

    onMounted(() => window.addEventListener('message', onMessage))
    onBeforeUnmount(() => window.removeEventListener('message', onMessage))

    return { loading, drawioUrl, friendlyId, frameRef }
  }
}
</script>

<style scoped>
.diagramming-page { height: calc(100vh - 220px); min-height: 480px; display: flex; }
.drawio-frame { flex: 1; border: none; width: 100%; }
.loading-text { padding: 40px; text-align: center; color: #6b7280; }
.unavailable { margin: 40px auto; text-align: center; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: #78350f; font-size: 0.85rem; margin-top: 6px; }
</style>
