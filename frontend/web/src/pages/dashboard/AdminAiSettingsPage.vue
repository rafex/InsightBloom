<template lang="pug">
.ai-settings-page
  h2 IA
  p.page-intro Configura el proveedor compatible con OpenAI que usarán el chat, el tutor y las herramientas de IA de la plataforma.

  .loading-text(v-if="loading") Cargando...
  .settings-card(v-else)
    h3 Proveedor de IA
    p.field-hint La clave se cifra antes de guardarse y nunca se vuelve a mostrar completa en el navegador. El servicio la entrega únicamente a los procesos internos que necesitan llamar al proveedor.
    label.toggle-row
      input(type="checkbox" v-model="chatAiEnabled" :disabled="saving")
      span {{ chatAiEnabled ? 'IA habilitada' : 'IA deshabilitada' }}

    .form-group
      label(for="ai-base-url") URL base compatible con OpenAI
      input#ai-base-url(v-model.trim="aiBaseUrl" type="url" placeholder="https://api.openai.com/v1")
      p.field-hint Ejemplo: https://api.openai.com/v1, https://api.groq.com/openai/v1 o la URL de tu gateway compatible.

    .form-group
      label(for="ai-model") Modelo
      input#ai-model(v-model.trim="aiModel" type="text" placeholder="gpt-4o-mini")

    .form-group
      label(for="ai-api-key") API key
      input#ai-api-key(v-model="aiApiKey" type="password" autocomplete="new-password" :placeholder="apiKeyHint || 'Introduce una nueva clave'")
      p.field-hint(v-if="apiKeyConfigured") Clave configurada: {{ apiKeyHint }}. Déjalo vacío para conservarla.
      label.clear-key(v-if="apiKeyConfigured")
        input(type="checkbox" v-model="clearApiKey")
        span Eliminar la clave guardada

    .chat-section
      h3 Chat y tutor
      p.field-hint El prompt se aplica al asistente del chat. La configuración pedagógica de cada evento puede añadir su objetivo y el contexto de la presentación.
      .form-group
        label Prompt de sistema
        textarea.prompt-input(v-model="chatSystemPrompt" rows="9" placeholder="Eres Roberto...")
      .form-group
        label Temperatura ({{ chatTemperature.toFixed(2) }})
        input.temperature-slider(type="range" v-model.number="chatTemperature" min="0" max="2" step="0.01")

    button.btn-primary(@click="save" :disabled="saving")
      span(v-if="saving") Guardando...
      span(v-else) Guardar configuración de IA
    p.success(v-if="saved") Configuración guardada.
    p.error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import { getAiSettings, setAiSettings } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

const DEFAULT_TEMPERATURE = 0.88

export default {
  name: 'AdminAiSettingsPage',
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const chatAiEnabled = ref(false)
    const aiBaseUrl = ref('')
    const aiModel = ref('')
    const aiApiKey = ref('')
    const apiKeyConfigured = ref(false)
    const apiKeyHint = ref<string | null>(null)
    const clearApiKey = ref(false)
    const chatSystemPrompt = ref('')
    const chatTemperature = ref(DEFAULT_TEMPERATURE)
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')

    function applySettings(settings: any) {
      chatAiEnabled.value = settings.chatAiEnabled
      aiBaseUrl.value = settings.aiBaseUrl || ''
      aiModel.value = settings.aiModel || ''
      apiKeyConfigured.value = Boolean(settings.aiApiKeyConfigured)
      apiKeyHint.value = settings.aiApiKeyHint || null
      chatSystemPrompt.value = settings.chatSystemPrompt || ''
      chatTemperature.value = settings.chatTemperature ?? DEFAULT_TEMPERATURE
      aiApiKey.value = ''
      clearApiKey.value = false
    }

    onMounted(async () => {
      try {
        applySettings(await getAiSettings(auth.state.token as string))
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo cargar la configuración de IA'
      } finally {
        loading.value = false
      }
    })

    async function save() {
      saving.value = true; saved.value = false; error.value = ''
      try {
        const settings = await setAiSettings(
          chatAiEnabled.value, aiBaseUrl.value, aiModel.value,
          aiApiKey.value.trim() || null, clearApiKey.value,
          chatSystemPrompt.value.trim() || null, chatTemperature.value,
          auth.state.token as string
        )
        applySettings(settings)
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar la configuración de IA'
      } finally {
        saving.value = false
      }
    }

    return {
      loading, chatAiEnabled, aiBaseUrl, aiModel, aiApiKey, apiKeyConfigured, apiKeyHint,
      clearApiKey, chatSystemPrompt, chatTemperature, saving, saved, error, save
    }
  }
}
</script>

<style scoped>
.ai-settings-page { padding: 24px; max-width: 760px; margin: 0 auto; }
h2 { color: #1e1b4b; margin-bottom: 6px; }
.page-intro { color: #6b7280; margin: 0 0 18px; }
.loading-text { color: #6b7280; }
.settings-card { background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #e5e7eb; }
.settings-card h3, .chat-section h3 { margin: 0 0 8px; color: #1e1b4b; font-size: 1rem; }
.field-hint { margin: 0 0 16px; font-size: 0.85rem; color: #6b7280; }
.toggle-row { display: flex; align-items: center; gap: 10px; font-size: 0.95rem; cursor: pointer; margin-bottom: 20px; }
.toggle-row input, .clear-key input { width: auto; }
.form-group { display: flex; flex-direction: column; gap: 4px; margin-bottom: 20px; }
.form-group label { font-weight: 600; font-size: 0.9rem; color: #374151; }
.form-group input[type="url"], .form-group input[type="text"], .form-group input[type="password"] {
  padding: 10px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem;
}
.prompt-input { padding: 10px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; font-family: inherit; resize: vertical; }
.prompt-input:focus, .form-group input:focus { outline: none; border-color: #4f46e5; }
.clear-key { display: flex; gap: 8px; align-items: center; font-size: 0.85rem; color: #991b1b; font-weight: 400 !important; margin-top: 4px; }
.chat-section { border-top: 1px solid #e5e7eb; padding-top: 20px; margin-top: 8px; }
.temperature-slider { width: 100%; margin-top: 4px; }
.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.success { color: #166534; font-size: 0.85rem; margin-top: 10px; }
.error { color: #dc2626; font-size: 0.85rem; margin-top: 10px; }
</style>
