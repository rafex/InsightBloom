<template lang="pug">
section.llm-editor(:aria-labelledby="`${prefix}-title`" :aria-busy="busy")
  h3(:id="`${prefix}-title`") {{ label }}
  p(role="status" aria-live="polite") {{ loaded ? statusLabel : 'Consultando configuración…' }}
  p(v-if="state.baseUrl") {{ state.baseUrl }}
  p(v-if="state.fallbackReason") El perfil vinculado no está disponible. Se volvió a heredar del dueño; si no tiene configuración, esta herramienta queda apagada.
  FeedbackMessage(v-if="error" :message="error" tone="error")
  FeedbackMessage(v-if="saved" message="Configuración guardada." tone="success")
  BaseButton(v-if="!loaded && !busy" variant="secondary" @click="load") Volver a consultar
  template(v-if="loaded")
    .form-group(v-if="eventId")
      label(:for="`${prefix}-mode`") Fuente para este evento
      select(:id="`${prefix}-mode`" v-model="mode" :disabled="busy")
        option(value="INHERIT_OWNER") Heredar del dueño
        option(value="EVENT") Configuración propia del evento
        option(value="MEMBER_PROFILE") Vincular mi perfil personal
        option(value="DISABLED") IA apagada
      p(v-if="mode === 'MEMBER_PROFILE'") El consumo se cargará al proveedor de tu perfil. Tus cambios personales se aplicarán a este evento. Si retiras el vínculo o pierdes autorización, volverá a heredar del dueño.
      BaseButton(v-if="mode !== 'EVENT'" :disabled="busy || mode === state.mode" @click="pending = 'mode'") Guardar fuente
    form(v-if="!eventId || mode === 'EVENT'" @submit.prevent="save")
      .form-group
        label(:for="`${prefix}-provider`") Proveedor
        select(:id="`${prefix}-provider`" v-model="form.provider" :disabled="busy" @change="providerChanged")
          option(value="openai") OpenAI
          option(value="groq") Groq
          option(value="custom") URL compatible con OpenAI
      .form-group(v-if="form.provider === 'custom'")
        label(:for="`${prefix}-url`") URL base HTTPS
        input(:id="`${prefix}-url`" v-model="form.baseUrl" type="url" required maxlength="2048" placeholder="https://api.example.com/v1" :disabled="busy")
        label(:for="`${prefix}-model`") Identificador del modelo
        input(:id="`${prefix}-model`" v-model="form.model" required maxlength="128" :disabled="busy")
      .form-group(v-else)
        label(:for="`${prefix}-model`") Modelo
        select(:id="`${prefix}-model`" v-model="form.model" :disabled="busy")
          option(v-if="form.provider === 'openai'" value="gpt-4o-mini") gpt-4o-mini
          template(v-else)
            option(value="llama-3.3-70b-versatile") llama-3.3-70b-versatile
            option(value="llama-3.1-8b-instant") llama-3.1-8b-instant
            option(value="openai/gpt-oss-120b") openai/gpt-oss-120b
      .form-group
        label(:for="`${prefix}-key`") API key {{ needsKey ? '(obligatoria)' : '(opcional para conservarla)' }}
        input(:id="`${prefix}-key`" v-model="form.apiKey" type="password" autocomplete="new-password" maxlength="4096" :required="needsKey" :disabled="busy" :aria-describedby="`${prefix}-secret-help`")
        p(:id="`${prefix}-secret-help`") Se guarda cifrada y nunca vuelve a mostrarse. Dejarla vacía conserva la existente; al cambiar proveedor o URL se exige una clave nueva.
      .llm-actions
        BaseButton(type="submit" :loading="busy") Guardar configuración
        BaseButton(v-if="ownState?.configured" type="button" variant="danger" :disabled="busy" @click="pending = 'delete'") Quitar configuración
  BaseModal(v-if="pending" title="Confirmar cambio de IA" :loading="busy" :persistent="busy" @close="pending = null" @confirm="pending === 'delete' ? remove() : applyMode()")
    p(v-if="pending === 'delete'") {{ eventId ? 'Se borrará la credencial exclusiva del evento y volverá a heredar del dueño.' : 'Los eventos que dependan de esta configuración se quedarán sin ella. Los vínculos de colaboradores volverán a heredar del dueño.' }}
    template(v-else)
      p(v-if="state.eventConfiguration?.configured") Se eliminará la credencial exclusiva de este evento. Para volver a usarla tendrás que ingresarla otra vez.
      p(v-if="mode === 'MEMBER_PROFILE'") Autorizas el uso de la configuración personal de esta herramienta en este evento, con consumo a cargo de tu proveedor.
      p(v-else-if="mode === 'INHERIT_OWNER'") Este evento usará la configuración del dueño y seguirá sus cambios futuros.
      p(v-else) Se apagará la IA de esta herramienta para este evento.
</template>
<script lang="ts">
import { computed, defineComponent } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import { useLlmEditor } from '@/features/llm/configuration'
export default defineComponent({
  components: { BaseButton, BaseModal, FeedbackMessage },
  props: { eventId: { type: String, default: '' }, capability: { type: String, required: true }, label: { type: String, required: true } },
  emits: ['changed'],
  setup(props, { emit }) {
    const prefix = computed(() => `llm-${props.eventId || 'personal'}-${props.capability}`)
    return { prefix, ...useLlmEditor(props, () => emit('changed')) }
  }
})
</script>
<style scoped>
.llm-editor { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: var(--space-4); margin-block: var(--space-4); }
.llm-actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }
.form-group { display: grid; gap: var(--space-2); margin-block: var(--space-3); }
input, select { width: 100%; min-width: 0; padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); }
</style>
