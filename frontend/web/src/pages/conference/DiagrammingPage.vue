<template lang="pug">
.diagramming-page
  .loading-text(v-if="loading") Cargando pizarra de diagramas...
  .unavailable(v-else-if="!drawioUrl")
    p ⚠️ La pizarra de diagramas no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  iframe.drawio-frame(v-else :src="drawioUrl" title="Diagramas" allow="clipboard-write")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getIntegrationConfig } from '@/services/api/usersApi'

export default {
  name: 'DiagrammingPage',
  props: {
    conferenceId: { type: String, default: '' }
  },
  setup() {
    const route = useRoute()
    const friendlyId = route.params.friendlyId as string
    const loading = ref(true)
    const drawioBaseUrl = ref('')

    onMounted(async () => {
      try {
        const config = await getIntegrationConfig()
        drawioBaseUrl.value = config.drawioBaseUrl || ''
      } catch (e: any) {
        drawioBaseUrl.value = ''
      } finally {
        loading.value = false
      }
    })

    // Una sala por evento (derivada del friendlyId, no del uuid interno) para que el
    // diagrama sea consistente entre todos los asistentes de la misma conferencia.
    // drawio no persiste el diagrama del lado del servidor (ver DEC-0017/NFR): el
    // parámetro solo evita que el editor abra en blanco cada vez, sin implicar guardado.
    const drawioUrl = computed(() => {
      if (!drawioBaseUrl.value) return ''
      return `${drawioBaseUrl.value}/?embed=1&noExitBtn=1&proto=json#R`
    })

    return { loading, drawioUrl, friendlyId }
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
