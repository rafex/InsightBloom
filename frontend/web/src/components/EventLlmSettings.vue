<template lang="pug">
section
  h2 IA del evento
  p Cada herramienta puede heredar del dueño, usar una configuración propia, vincular tu perfil personal o quedar apagada. Las claves no se muestran.
  LlmConfigurationEditor(v-for="tool in llmTools" :key="`${eventId}-${tool.id}`" :event-id="eventId" :capability="tool.id" :label="tool.label")
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import LlmConfigurationEditor from '@/components/LlmConfigurationEditor.vue'
import { llmTools } from '@/features/llm/configuration'
export default defineComponent({
  components: { LlmConfigurationEditor }, props: { eventId: { type: String, required: true } },
  setup() { return { llmTools } }
})
</script>
