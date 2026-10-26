<template lang="pug">
section
  h1 Mi configuración de IA
  p Tus eventos heredan estos ajustes cuando no tienen configuración propia. Al cambiarlos, sus próximos usos se actualizarán. Cada herramienta tiene sus propias credenciales.
  LlmConfigurationEditor(v-for="tool in llmTools" :key="tool.id" :capability="tool.id" :label="tool.label" @changed="loadDependencies")
  h2 Eventos que usan tu perfil
  FeedbackMessage(v-if="error" :message="error" tone="error")
  p(v-if="!dependencies.length") No hay eventos que hereden o tengan vinculado tu perfil.
  ul
    li(v-for="item in dependencies" :key="`${item.eventId}-${item.capability}-${item.kind}`")
      span {{ item.name }} · {{ toolLabel(item.capability) }} · {{ item.kind === 'OWNER' ? 'Herencia del dueño' : 'Vínculo personal' }}
      BaseButton(v-if="item.kind === 'MEMBER_PROFILE'" variant="secondary" size="sm" @click="withdrawing = item") Retirar mi vínculo
  BaseModal(v-if="withdrawing" title="Retirar vínculo personal" :loading="busy" :persistent="busy" @close="withdrawing = null" @confirm="withdraw")
    p El evento volverá a heredar la configuración de su dueño para esta herramienta. Si no tiene configuración, quedará sin IA.
</template>
<script lang="ts">
import { defineComponent, onMounted, ref } from 'vue'
import axios from 'axios'
import LlmConfigurationEditor from '@/components/LlmConfigurationEditor.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import { llmTools, personalLlmUrl, llmAuth, type LlmDependency } from '@/features/llm/configuration'
export default defineComponent({
components: { LlmConfigurationEditor, BaseButton, BaseModal, FeedbackMessage },
setup() {
const dependencies = ref<LlmDependency[]>([]), error = ref(''), busy = ref(false), withdrawing = ref<LlmDependency | null>(null)
const toolLabel = (id: string) => llmTools.find(t => t.id === id)?.label || id
async function loadDependencies() {
  try { dependencies.value = (await axios.get(`${personalLlmUrl}/events`, llmAuth())).data.data.events; error.value = '' }
  catch { error.value = 'No se pudieron consultar los eventos vinculados.' }
}
async function withdraw() {
  const item = withdrawing.value; if (!item) return
  busy.value = true
  try { await axios.delete(`${personalLlmUrl}/events/${item.eventId}/${item.capability}`, llmAuth()); withdrawing.value = null; await loadDependencies() }
  catch { error.value = 'No se pudo retirar el vínculo.' }
  finally { busy.value = false }
}
onMounted(loadDependencies)
return { llmTools, dependencies, error, busy, withdrawing, toolLabel, loadDependencies, withdraw }
}
})
</script>
