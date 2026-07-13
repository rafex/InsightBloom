<template lang="pug">
.chat-settings-page
  h2 Chat

  .loading-text(v-if="loading") Cargando...
  .settings-card(v-else)
    h3 IA del chat (Roberto)
    p.field-hint Controla si el asistente de IA responde en el chat de las conferencias. Úsalo para cortar el uso de IA rápidamente ante un intento de abuso, sin necesidad de un despliegue.
    label.toggle-row
      input(type="checkbox" v-model="chatAiEnabled" :disabled="saving")
      span {{ chatAiEnabled ? 'IA habilitada en el chat' : 'IA deshabilitada en el chat' }}

    .form-group
      label Prompt de sistema de Roberto
      p.field-hint Define la personalidad y reglas de Roberto. Déjalo vacío para usar el prompt por defecto embebido en el servicio de chat.
      textarea.prompt-input(v-model="chatSystemPrompt" rows="8" placeholder="Eres Roberto. Eres un ser humano que está participando en el chat grupal...")

    .form-group
      label Temperatura ({{ chatTemperature.toFixed(2) }})
      p.field-hint Controla qué tan creativas/aleatorias son las respuestas de Roberto. 0 = muy predecible, 2 = muy variable. Por defecto: 0.88.
      input.temperature-slider(type="range" v-model.number="chatTemperature" min="0" max="2" step="0.01")

    button.btn-primary(@click="save" :disabled="saving")
      span(v-if="saving") Guardando...
      span(v-else) Guardar cambios
    p.success(v-if="saved") Cambios guardados.
    p.error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getChatSettings, setChatSettings } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

const DEFAULT_TEMPERATURE = 0.88

export default {
  name: 'AdminChatSettingsPage',
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const chatAiEnabled = ref(true)
    const chatSystemPrompt = ref('')
    const chatTemperature = ref(DEFAULT_TEMPERATURE)
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')

    onMounted(async () => {
      try {
        const settings = await getChatSettings()
        chatAiEnabled.value = settings.chatAiEnabled
        chatSystemPrompt.value = settings.chatSystemPrompt || ''
        chatTemperature.value = settings.chatTemperature ?? DEFAULT_TEMPERATURE
      } finally {
        loading.value = false
      }
    })

    async function save() {
      saving.value = true; saved.value = false; error.value = ''
      try {
        const settings = await setChatSettings(
          chatAiEnabled.value,
          chatSystemPrompt.value.trim() || null,
          chatTemperature.value,
          auth.state.token as string
        )
        chatAiEnabled.value = settings.chatAiEnabled
        chatSystemPrompt.value = settings.chatSystemPrompt || ''
        chatTemperature.value = settings.chatTemperature ?? DEFAULT_TEMPERATURE
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar el cambio'
      } finally {
        saving.value = false
      }
    }

    return { loading, chatAiEnabled, chatSystemPrompt, chatTemperature, saving, saved, error, save }
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
.toggle-row { display: flex; align-items: center; gap: 10px; font-size: 0.95rem; cursor: pointer; margin-bottom: 20px; }
.toggle-row input { width: auto; }
.form-group { display: flex; flex-direction: column; gap: 4px; margin-bottom: 20px; }
.form-group label { font-weight: 600; font-size: 0.9rem; color: #374151; }
.prompt-input {
  padding: 10px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem;
  font-family: inherit; resize: vertical;
}
.prompt-input:focus { outline: none; border-color: #4f46e5; }
.temperature-slider { width: 100%; margin-top: 4px; }
.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.success { color: #166534; font-size: 0.85rem; margin-top: 10px; }
.error { color: #dc2626; font-size: 0.85rem; margin-top: 10px; }
</style>
