<template lang="pug">
.whiteboard-page
  .loading-text(v-if="loading") Cargando pizarra...
  .unavailable(v-else-if="!excalidrawUrl")
    p ⚠️ La pizarra no está disponible en este momento.
    p.hint Intenta más tarde o contacta al organizador.
  iframe.excalidraw-frame(v-else :src="excalidrawUrl" title="Pizarra")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getIntegrationConfig } from '@/services/api/usersApi'

export default {
  name: 'WhiteboardPage',
  props: {
    conferenceId: { type: String, default: '' }
  },
  setup() {
    const loading = ref(true)
    const excalidrawBaseUrl = ref('')

    onMounted(async () => {
      try {
        const config = await getIntegrationConfig()
        excalidrawBaseUrl.value = config.excalidrawBaseUrl || ''
      } catch (e: any) {
        excalidrawBaseUrl.value = ''
      } finally {
        loading.value = false
      }
    })

    // La app oficial de Excalidraw no necesita ningun protocolo de embed (a diferencia de
    // drawio): guarda el dibujo en el localStorage del propio navegador y permite exportar
    // desde su UI. La colaboracion en vivo (excalidraw-room) queda deferida — la imagen
    // oficial necesita la URL del websocket "horneada" en build-time, no configurable via
    // env var en runtime (ver values.yaml, comentario sobre TASK-0031).
    const excalidrawUrl = computed(() => excalidrawBaseUrl.value || '')

    return { loading, excalidrawUrl }
  }
}
</script>

<style scoped>
.whiteboard-page { height: calc(100vh - 220px); min-height: 480px; display: flex; }
.excalidraw-frame { flex: 1; border: none; width: 100%; }
.loading-text { padding: 40px; text-align: center; color: #6b7280; }
.unavailable { margin: 40px auto; text-align: center; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 12px; padding: 24px; max-width: 420px; }
.unavailable .hint { color: #78350f; font-size: 0.85rem; margin-top: 6px; }
</style>
