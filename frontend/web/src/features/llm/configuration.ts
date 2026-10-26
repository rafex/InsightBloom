import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/features/auth/authStore'
export const llmTools = [
  { id: 'chat', label: 'Chat en vivo' }, { id: 'tutor', label: 'Tutor IA' },
  { id: 'survey', label: 'Encuestas' }, { id: 'seatLayout', label: 'Distribución de asientos' },
  { id: 'email', label: 'Borradores de correo' }
]
export type LlmMode = 'EVENT' | 'INHERIT_OWNER' | 'MEMBER_PROFILE' | 'DISABLED'
export interface LlmState { configured: boolean; provider?: string; model?: string; baseUrl?: string; mode?: LlmMode; effectiveSource?: string; fallbackReason?: string; linkedProfileName?: string; eventConfiguration?: LlmState }
export interface LlmDependency { eventId: string; name: string; capability: string; kind: string }
export const llmAuth = () => ({ headers: { Authorization: `Bearer ${useAuthStore().state.token}` } })
export const personalLlmUrl = '/api/users/api/v1/me/llm'
export function useLlmEditor(props: { eventId: string; capability: string }, changed: () => void) {
  const state = ref<LlmState>({ configured: false }), mode = ref<LlmMode>('INHERIT_OWNER')
  const busy = ref(false), loaded = ref(false), error = ref(''), saved = ref(false)
  const form = reactive({ provider: 'openai', model: 'gpt-4o-mini', baseUrl: '', apiKey: '' })
  const pending = ref<'mode' | 'delete' | null>(null)
  const endpoint = computed(() => props.eventId ? `/api/users/api/v1/conferences/${encodeURIComponent(props.eventId)}/llm/${props.capability}` : `${personalLlmUrl}/${props.capability}`)
  const ownState = computed(() => props.eventId ? state.value.eventConfiguration : state.value)
  const needsKey = computed(() => !ownState.value?.configured
    || form.provider !== ownState.value.provider
    || (form.provider === 'custom' && form.baseUrl.replace(/\/+$/, '') !== (ownState.value.baseUrl || '').replace(/\/+$/, '')))
  const statusLabel = computed(() => {
    const source = state.value.effectiveSource
    const label = source === 'OWNER' ? 'Heredada del dueño' : source === 'MEMBER_PROFILE' ? `Perfil de ${state.value.linkedProfileName || 'colaborador'}` : source === 'EVENT' ? 'Propia del evento' : props.eventId ? 'IA apagada' : state.value.configured ? 'Configuración personal' : 'Sin configurar'
    return label + (state.value.configured ? ` · ${state.value.provider} · ${state.value.model}` : '')
  })
  function accept(data: LlmState) {
    state.value = data; mode.value = data.mode || 'INHERIT_OWNER'
    const own = props.eventId ? data.eventConfiguration : data
    form.provider = own?.provider || 'openai'; form.model = own?.model || 'gpt-4o-mini'; form.baseUrl = own?.baseUrl || ''; form.apiKey = ''
    loaded.value = true
  }
  async function load() {
    busy.value = true; error.value = ''
    try { accept((await axios.get(endpoint.value, llmAuth())).data.data) }
    catch { error.value = 'No se pudo consultar la configuración. Revisa tu acceso y vuelve a intentar.' }
    finally { busy.value = false }
  }
  async function save() {
    if (!loaded.value || busy.value) return
    busy.value = true; error.value = ''; saved.value = false
    try {
      accept((await axios.put(endpoint.value, { ...form, baseUrl: form.provider === 'custom' ? form.baseUrl : '' }, llmAuth())).data.data)
      saved.value = true; changed()
    } catch { error.value = 'No se pudo guardar. Revisa URL, modelo y clave; cambiar proveedor o URL requiere una clave nueva.' }
    finally { busy.value = false }
  }
  async function applyMode() {
    if (!loaded.value || busy.value) return
    busy.value = true; error.value = ''; saved.value = false
    try {
      accept((await axios.patch(endpoint.value, { mode: mode.value, confirmCredentialRemoval: true }, llmAuth())).data.data)
      saved.value = true; changed(); pending.value = null
    } catch { error.value = 'No se pudo cambiar la fuente. Para vincular tu perfil primero configura esta herramienta en tu cuenta.'; pending.value = null }
    finally { busy.value = false }
  }
  async function remove() {
    busy.value = true; error.value = ''
    try { await axios.delete(endpoint.value, llmAuth()); await load(); changed() }
    catch { error.value = 'No se pudo quitar la configuración.' }
    finally { busy.value = false; pending.value = null }
  }
  function providerChanged() { form.model = form.provider === 'openai' ? 'gpt-4o-mini' : form.provider === 'groq' ? 'llama-3.3-70b-versatile' : ''; form.baseUrl = '' }
  onMounted(load); onBeforeUnmount(() => { form.apiKey = '' })
  return { state, mode, busy, loaded, error, saved, form, pending, ownState, needsKey, statusLabel, load, save, applyMode, remove, providerChanged }
}
