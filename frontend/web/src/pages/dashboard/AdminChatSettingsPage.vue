<template lang="pug">
.chat-settings-page
  h2 Chat

  .loading-text(v-if="loading") Cargando...
  .settings-card(v-else)
    h3 IA del chat (Roberto)
    p.field-hint Controla si el asistente de IA responde en el chat de las conferencias. Úsalo para cortar el uso de IA rápidamente ante un intento de abuso, sin necesidad de un despliegue.
    label.toggle-row
      input(type="checkbox" :checked="chatAiEnabled" @change="onToggle" :disabled="saving")
      span {{ chatAiEnabled ? 'IA habilitada en el chat' : 'IA deshabilitada en el chat' }}
    p.success(v-if="saved") Cambios guardados.
    p.error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getChatAiSetting, setChatAiSetting } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'AdminChatSettingsPage',
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const chatAiEnabled = ref(true)
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')

    onMounted(async () => {
      try {
        chatAiEnabled.value = await getChatAiSetting()
      } finally {
        loading.value = false
      }
    })

    async function onToggle(e: Event) {
      const next = (e.target as HTMLInputElement).checked
      saving.value = true; saved.value = false; error.value = ''
      try {
        chatAiEnabled.value = await setChatAiSetting(next, auth.state.token as string)
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar el cambio'
      } finally {
        saving.value = false
      }
    }

    return { loading, chatAiEnabled, saving, saved, error, onToggle }
  }
}
</script>

<style scoped>
.chat-settings-page { padding: 24px; max-width: 640px; margin: 0 auto; }
h2 { color: #1e1b4b; margin-bottom: 16px; }
.loading-text { color: #6b7280; }
.settings-card { background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #e5e7eb; }
.settings-card h3 { margin: 0 0 8px; color: #1e1b4b; font-size: 1rem; }
.field-hint { margin: 0 0 16px; font-size: 0.85rem; color: #6b7280; }
.toggle-row { display: flex; align-items: center; gap: 10px; font-size: 0.95rem; cursor: pointer; }
.toggle-row input { width: auto; }
.success { color: #166534; font-size: 0.85rem; margin-top: 10px; }
.error { color: #dc2626; font-size: 0.85rem; margin-top: 10px; }
</style>
