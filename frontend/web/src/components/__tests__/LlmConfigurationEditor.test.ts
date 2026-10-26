import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import LlmConfigurationEditor from '../LlmConfigurationEditor.vue'
import BaseModal from '../ui/BaseModal.vue'
vi.mock('axios', () => ({ default: { get: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
vi.mock('@/features/auth/authStore', () => ({ useAuthStore: () => ({ state: { token: 'session-token' } }) }))
const inherited = { configured: true, mode: 'INHERIT_OWNER', effectiveSource: 'OWNER', provider: 'groq', model: 'llama-3.3-70b-versatile', eventConfiguration: { configured: false } }
const event = { configured: true, mode: 'EVENT', effectiveSource: 'EVENT', provider: 'openai', model: 'gpt-4o-mini', eventConfiguration: { configured: true, provider: 'openai', model: 'gpt-4o-mini' } }
const mounted: ReturnType<typeof mount>[] = []
function editor(eventId = 'event') { const w = mount(LlmConfigurationEditor, { props: { eventId, capability: 'chat', label: 'Chat' }, attachTo: document.body }); mounted.push(w); return w }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(axios.get).mockResolvedValue({ data: { data: inherited } }) })
afterEach(() => { mounted.forEach(w => w.unmount()); mounted.length = 0 })
describe('LLM configuration UX', () => {
  it('shows inherited source and clears a new key after saving an event override', async () => {
    const w = editor(); await flushPromises(); expect(w.text()).toContain('Heredada del dueño')
    await w.get('select[id$="-mode"]').setValue('EVENT')
    await w.get('input[type="password"]').setValue('private-api-key')
    vi.mocked(axios.put).mockResolvedValue({ data: { data: event } })
    await w.get('form').trigger('submit'); await flushPromises()
    expect(axios.put).toHaveBeenCalledWith(expect.stringContaining('/conferences/event/llm/chat'), expect.objectContaining({ apiKey: 'private-api-key' }), expect.anything())
    expect((w.get('input[type="password"]').element as HTMLInputElement).value).toBe('')
    expect(w.text()).not.toContain('private-api-key'); expect(w.text()).toContain('Propia del evento')
  })
  it('confirms destructive source changes and only sends a mode, never another user identity', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { data: event } })
    const w = editor(); await flushPromises(); await w.get('select[id$="-mode"]').setValue('MEMBER_PROFILE')
    const button = w.findAll('button').find(b => b.text() === 'Guardar fuente')!; await button.trigger('click')
    expect(axios.patch).not.toHaveBeenCalled(); expect(document.body.textContent).toContain('eliminará la credencial')
    vi.mocked(axios.patch).mockResolvedValue({ data: { data: { ...inherited, mode: 'MEMBER_PROFILE', effectiveSource: 'MEMBER_PROFILE', linkedProfileName: 'Mi perfil' } } })
    w.findComponent(BaseModal).vm.$emit('confirm'); await flushPromises()
    expect(axios.patch).toHaveBeenCalledWith(expect.any(String), { mode: 'MEMBER_PROFILE', confirmCredentialRemoval: true }, expect.anything())
  })
  it('allows a metadata edit with an empty key to preserve the personal secret', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { data: { configured: true, provider: 'openai', model: 'gpt-4o-mini' } } })
    vi.mocked(axios.put).mockResolvedValue({ data: { data: { configured: true, provider: 'openai', model: 'gpt-4o-mini' } } })
    const w = editor(''); await flushPromises(); await w.get('form').trigger('submit'); await flushPromises()
    expect(axios.put).toHaveBeenCalledWith(expect.stringContaining('/me/llm/chat'), expect.objectContaining({ apiKey: '' }), expect.anything())
  })
  it('requires a fresh key when a configured custom endpoint changes', async () => {
    const custom = { configured: true, provider: 'custom', model: 'model-v1', baseUrl: 'https://api.example.test/v1' }
    vi.mocked(axios.get).mockResolvedValue({ data: { data: { ...custom, mode: 'EVENT', effectiveSource: 'EVENT', eventConfiguration: custom } } })
    const w = editor(); await flushPromises()
    const url = w.get('input[type="url"]')
    expect((w.get('input[type="password"]').element as HTMLInputElement).required).toBe(false)
    await url.setValue('https://api.example.test/v2')
    expect((w.get('input[type="password"]').element as HTMLInputElement).required).toBe(true)
  })
  it('does not expose editing when access is denied', async () => {
    vi.mocked(axios.get).mockRejectedValue(new Error('forbidden'))
    const w = editor(); await flushPromises(); expect(w.find('input[type="password"]').exists()).toBe(false)
    expect(w.text()).toContain('Revisa tu acceso')
  })
})
