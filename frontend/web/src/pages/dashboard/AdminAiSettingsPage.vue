<template lang="pug">
.ai-settings-page
  h2 IA
  p.page-intro Cada capacidad de IA tiene su propio proveedor. Así puedes usar modelos, claves y prompts distintos para cada flujo.
  p.scope-note
    strong Alcance: Configuración global
    span Proveedores, modelos, claves y prompts base compartidos por toda la plataforma.

  LoadingState(v-if="loading" message="Cargando configuración de IA…")
  .settings-shell(v-else)
    nav.ai-tabs(role="tablist" aria-label="Configuración de IA")
      button.ai-tab(v-for="tab in tabs" :key="tab.id" :class="{ active: activeCapability === tab.id }" @click="selectCapability(tab.id)" type="button" role="tab" :aria-selected="activeCapability === tab.id" aria-controls="ai-settings-panel")
        span.tab-icon {{ tab.icon }}
        span
          strong {{ tab.label }}
          small {{ tab.summary }}

    .settings-card#ai-settings-panel(role="tabpanel")
      .capability-heading
        div
          h3 {{ activeTab.label }}
          p.field-hint {{ activeTab.description }}
        ToggleSwitch(v-model="activeProvider.enabled" :disabled="saving")
          | {{ activeProvider.enabled ? 'IA habilitada para este flujo' : 'IA deshabilitada para este flujo' }}

      .form-group
        label(for="ai-base-url") URL base compatible con OpenAI
        input#ai-base-url(v-model.trim="activeProvider.baseUrl" type="url" placeholder="https://api.openai.com/v1")
        p.field-hint Ejemplo: https://api.openai.com/v1, https://api.groq.com/openai/v1 o la URL de tu gateway compatible.

      .form-group
        label(for="ai-model") Modelo
        input#ai-model(v-model.trim="activeProvider.model" type="text" placeholder="gpt-4o-mini")

      .form-group
        label(for="ai-api-key") API key exclusiva de {{ activeTab.label }}
        input#ai-api-key(v-model="activeProvider.apiKey" type="password" autocomplete="new-password" :placeholder="activeProvider.apiKeyHint || 'Introduce una nueva clave'")
        p.field-hint(v-if="activeProvider.configured && activeProvider.apiKeyConfigured") Clave configurada: {{ activeProvider.apiKeyHint }}. Déjalo vacío para conservarla.
        p.field-hint(v-else-if="!activeProvider.configured") No se copia ni se comparte la clave del chat; puedes asignar una clave exclusiva para este flujo.
        label.clear-key(v-if="activeProvider.configured && activeProvider.apiKeyConfigured")
          input(type="checkbox" v-model="activeProvider.clearApiKey")
          span Eliminar la clave guardada

      .form-group
        label(for="ai-system-prompt") Prompt base
        textarea#ai-system-prompt.prompt-input(v-model="activeProvider.systemPrompt" rows="9" :placeholder="activeTab.promptPlaceholder")
        p.field-hint El prompt específico de la operación se añade encima de este prompt base cuando corresponde.

      .form-group
        label(for="ai-guardrails") Guardarails
        textarea#ai-guardrails.prompt-input(v-model="activeProvider.guardrails" rows="7" placeholder="Reglas de seguridad: qué nunca debe hacer, decir o revelar este flujo de IA.")
        p.field-hint Reglas de seguridad que se añaden después del prompt base y antes del prompt de la operación (que ya trae sus propias reglas fijas). Útil para restricciones específicas de tu plataforma: temas prohibidos, tono obligatorio, qué nunca confirmar o negar.

      .form-group.variables-group
        BaseButton.variables-toggle(variant="ghost" size="sm" type="button" @click="showVariables = !showVariables") {{ showVariables ? 'Ocultar' : 'Ver' }} variables de contexto disponibles
        .variables-list(v-if="showVariables")
          p.field-hint No se escriben como texto literal en el prompt: cuando dicen "ya incluida en...", ese dato ya viaja automáticamente al modelo en esa capacidad, sin que tengas que hacer nada. Sirven como referencia de qué contexto real está disponible. No se incluyen datos sensibles como el correo del asistente.
          ul
            li(v-for="v in promptVariables" :key="v.key")
              code {{ variableToken(v.key) }}
              span.var-label {{ v.label }}
              span.var-scope(v-if="v.autoIncludedIn") ya incluida en: {{ v.autoIncludedIn }}
              span.var-scope.var-scope-pending(v-else) aún no conectada a ningún prompt

      .form-group
        label(for="ai-temperature") Temperatura ({{ activeProvider.temperature.toFixed(2) }})
        input#ai-temperature.temperature-slider(type="range" v-model.number="activeProvider.temperature" min="0" max="2" step="0.01")

      BaseButton(:loading="saving" type="button" @click="save") Guardar configuración de {{ activeTab.label }}
      FeedbackMessage(v-if="saved" message="Configuración guardada." tone="success")
      FeedbackMessage(v-if="error" :message="error" tone="error")

      p.field-hint(v-if="activeCapability === 'tutor'") El objetivo pedagógico, las instrucciones y el límite de consultas de cada evento se configuran en la sección "Tutor IA" de la configuración de ese evento (Eventos → [evento] → Configuración → Tutor IA), no aquí — esta pantalla solo define el proveedor (URL, clave, prompts) que comparten todos los eventos.
</template>

<script lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAiSettings, setAiProviderSettings, getAiPromptCatalog } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import type { AiProviderSettings, AiSettings, AiPromptVariable } from '@/services/api/types'
import ToggleSwitch from '@/components/ui/ToggleSwitch.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

type Capability = 'chat' | 'tutor' | 'survey' | 'seat-layout' | 'email'

interface ProviderForm extends Omit<AiProviderSettings, 'systemPrompt' | 'guardrails'> {
  systemPrompt: string
  guardrails: string
  apiKey: string
  clearApiKey: boolean
}

const DEFAULT_BASE_URL = 'https://api.groq.com/openai/v1'
const DEFAULT_MODEL = 'openai/gpt-oss-120b'
const DEFAULT_TEMPERATURE = 0.7

const tabs: Array<{ id: Capability, icon: string, label: string, summary: string, description: string, promptPlaceholder: string }> = [
  { id: 'chat', icon: '💬', label: 'Chat / Roberto', summary: 'Chat grupal', description: 'Proveedor para Roberto, el asistente conversacional del chat del evento.', promptPlaceholder: 'Eres Roberto, un participante humano...' },
  { id: 'tutor', icon: '🧭', label: 'Tutor IA', summary: 'Ayuda pedagógica', description: 'Tutor que orienta a los asistentes usando el objetivo y la presentación del evento, sin resolverles directamente el ejercicio.', promptPlaceholder: 'Ayuda al asistente a razonar y descubrir la respuesta...' },
  { id: 'survey', icon: '📝', label: 'Encuestas', summary: 'Sugerir y calificar', description: 'Sugerencias, mejora de preguntas y calificación de respuestas abiertas.', promptPlaceholder: 'Genera contenido de encuesta claro, evaluable y relacionado con el evento...' },
  { id: 'seat-layout', icon: '💺', label: 'Mapas de asientos', summary: 'Generación de layouts', description: 'Genera la distribución inicial de asientos cuando el organizador describe el recinto.', promptPlaceholder: 'Devuelve únicamente un layout de asientos válido según la descripción...' },
  { id: 'email', icon: '✉️', label: 'Correos a inscritos', summary: 'Redacción de emails', description: 'Ayuda al organizador a redactar correos profesionales para los inscritos de cada evento.', promptPlaceholder: 'Sos un asistente de redacción profesional para comunicaciones de eventos...' }
]

function emptyProvider(): ProviderForm {
  return {
    configured: false, enabled: false, baseUrl: DEFAULT_BASE_URL, model: DEFAULT_MODEL,
    systemPrompt: '', guardrails: '', temperature: DEFAULT_TEMPERATURE, apiKeyConfigured: false,
    apiKeyHint: null, apiKey: '', clearApiKey: false
  }
}

export default {
  name: 'AdminAiSettingsPage',
  components: { ToggleSwitch, BaseButton, FeedbackMessage, LoadingState },
  setup() {
    const auth = useAuthStore()
    const route = useRoute()
    const router = useRouter()
    const loading = ref(true)
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')
    const activeCapability = ref<Capability>('chat')
    const providers = reactive<Record<Capability, ProviderForm>>({
      chat: emptyProvider(), tutor: emptyProvider(), survey: emptyProvider(), 'seat-layout': emptyProvider(), email: emptyProvider()
    })
    const showVariables = ref(false)
    const promptVariables = ref<AiPromptVariable[]>([])
    function variableToken(key: string): string {
      return '{{' + key + '}}'
    }
    const activeTab = computed(() => tabs.find(tab => tab.id === activeCapability.value) || tabs[0])
    const activeProvider = computed(() => providers[activeCapability.value])

    function capabilityFromRoute(value: unknown): Capability {
      const candidate = Array.isArray(value) ? value[0] : value
      return tabs.some(tab => tab.id === candidate) ? candidate as Capability : 'chat'
    }

    function selectCapability(capability: Capability) {
      activeCapability.value = capability
      router.push({ path: `/dashboard/admin/ai/${capability}`, query: route.query })
    }

    watch(() => route.params.capability, (value) => {
      const capability = capabilityFromRoute(value)
      activeCapability.value = capability
      if (value !== capability) {
        router.replace({ path: `/dashboard/admin/ai/${capability}`, query: route.query })
      }
    }, { immediate: true })

    function mapProvider(value: AiProviderSettings | undefined): ProviderForm {
      const source = value || emptyProvider()
      return {
        ...source, systemPrompt: source.systemPrompt || '', guardrails: source.guardrails || '',
        apiKey: '', clearApiKey: false, temperature: source.temperature ?? DEFAULT_TEMPERATURE
      }
    }

    function applySettings(settings: AiSettings) {
      const incoming = settings.providers || {} as AiSettings['providers']
      Object.assign(providers.chat, mapProvider(incoming.chat || {
        configured: true, enabled: settings.chatAiEnabled, baseUrl: settings.aiBaseUrl,
        model: settings.aiModel, systemPrompt: settings.chatSystemPrompt, guardrails: settings.chatGuardrails,
        temperature: settings.chatTemperature, apiKeyConfigured: settings.aiApiKeyConfigured,
        apiKeyHint: settings.aiApiKeyHint
      }))
      Object.assign(providers.tutor, mapProvider(incoming.tutor))
      Object.assign(providers.survey, mapProvider(incoming.survey))
      Object.assign(providers['seat-layout'], mapProvider(incoming.seatLayout))
      Object.assign(providers.email, mapProvider(incoming.email))
    }

    onMounted(async () => {
      try {
        applySettings(await getAiSettings(auth.state.token as string))
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo cargar la configuración de IA'
      } finally {
        loading.value = false
      }
      try {
        promptVariables.value = await getAiPromptCatalog(auth.state.token as string)
      } catch { /* referencia opcional; no bloquea la pantalla si falla */ }
    })

    async function save() {
      saving.value = true; saved.value = false; error.value = ''
      const provider = activeProvider.value
      try {
        applySettings(await setAiProviderSettings(
          activeCapability.value, provider.enabled, provider.baseUrl, provider.model,
          provider.apiKey.trim() || null, provider.clearApiKey,
          provider.systemPrompt.trim() || null, provider.guardrails.trim() || null, provider.temperature,
          auth.state.token as string
        ))
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || `No se pudo guardar la configuración de ${activeTab.value.label}`
      } finally {
        saving.value = false
      }
    }

    return { tabs, activeCapability, activeTab, activeProvider, loading, saving, saved, error, save, selectCapability,
      showVariables, promptVariables, variableToken }
  }
}
</script>

<style scoped>
.ai-settings-page { padding: 24px; max-width: 980px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 6px; }
.page-intro { color: var(--color-text-muted); margin: 0 0 18px; }
.scope-note { display: flex; flex-wrap: wrap; gap: 6px; align-items: baseline; margin: 0 0 18px; padding: 10px 12px; border: 1px solid var(--color-primary-border); border-radius: 8px; background: var(--color-primary-soft); color: var(--color-primary-dark); font-size: 0.86rem; }
.scope-note strong { font-weight: 700; }
.settings-shell { display: grid; grid-template-columns: 235px minmax(0, 1fr); gap: 18px; align-items: start; }
.ai-tabs { display: flex; flex-direction: column; gap: 8px; position: sticky; top: 18px; }
.ai-tab { display: flex; align-items: flex-start; gap: 10px; text-align: left; padding: 12px; border: 1px solid var(--color-border-subtle); border-radius: 10px; background: var(--color-surface); color: var(--color-text-secondary); cursor: pointer; }
.ai-tab.active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-dark); box-shadow: 0 0 0 1px var(--color-primary); }
.tab-icon { font-size: 1.2rem; }
.ai-tab strong, .ai-tab small { display: block; }
.ai-tab small { margin-top: 3px; color: var(--color-text-muted); font-size: .75rem; }
.settings-card { background: var(--color-surface); border-radius: 12px; padding: 22px; border: 1px solid var(--color-border-subtle); }
.capability-heading { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.settings-card h3 { margin: 0 0 8px; color: var(--color-heading); font-size: 1.05rem; }
.field-hint { margin: 0 0 16px; font-size: .85rem; }
.clear-key input { width: auto; }
.form-group { display: flex; flex-direction: column; gap: 4px; margin-bottom: 20px; }
.form-group label { font-weight: 600; font-size: .9rem; color: var(--color-text-secondary); }
.form-group input[type="url"], .form-group input[type="text"], .form-group input[type="password"] { font-size: .9rem; }
.prompt-input { font-size: .9rem; font-family: inherit; resize: vertical; }
.clear-key { display: flex; gap: 8px; align-items: center; font-size: .85rem; color: var(--color-danger-dark); font-weight: 400 !important; margin-top: 4px; }
.temperature-slider { width: 100%; margin-top: 4px; }
.variables-toggle { align-self: flex-start; }
.variables-list { margin-top: 10px; background: var(--color-surface-muted); border: 1px solid var(--color-border-subtle); border-radius: 8px; padding: 10px 14px; }
.variables-list ul { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 6px; }
.variables-list li { display: flex; align-items: center; gap: 8px; font-size: .82rem; }
.variables-list code { background: var(--color-primary-soft); color: var(--color-primary-dark); padding: 2px 6px; border-radius: 4px; font-size: .78rem; }
.variables-list .var-label { color: var(--color-text-muted); }
.variables-list li { flex-wrap: wrap; }
.variables-list .var-scope { font-size: .72rem; color: var(--color-success); background: var(--color-success-soft); padding: 1px 8px; border-radius: 8px; }
.variables-list .var-scope-pending { color: var(--color-warning); background: var(--color-warning-soft); }
@media (max-width: 760px) { .settings-shell { grid-template-columns: 1fr; } .ai-tabs { position: static; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .capability-heading { flex-direction: column; } }
</style>
