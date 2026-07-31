<template lang="pug">
label.toggle-switch(:class="{ disabled, loading }" :aria-busy="loading || undefined")
  input(
    type="checkbox"
    role="switch"
    :checked="modelValue"
    :disabled="disabled || loading"
    :aria-checked="modelValue"
    :aria-label="ariaLabel || undefined"
    @change="onChange"
  )
  span.track
    span.thumb
  span.toggle-label(v-if="$slots.default")
    slot
  span.toggle-loading(v-if="loading" aria-hidden="true")
</template>

<script lang="ts">
// Interruptor visual (auditoría UX 2026-07-27): un <input type="checkbox"> plano no comunica
// "esto activa/desactiva un flujo" -- un caso real: la IA de Encuestas tenía la API key cargada
// pero el checkbox "habilitada" seguía destildado porque no se percibía como un control a
// accionar. El thumb se desliza y cambia de color (gris -> primario) para que el estado sea
// obvio de un vistazo, además del texto que ya acompaña a cada uso.
export default {
  name: 'ToggleSwitch',
  props: {
    modelValue: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    ariaLabel: { type: String, default: '' }
  },
  emits: ['update:modelValue'],
  methods: {
    onChange(e: Event) {
      this.$emit('update:modelValue', (e.target as HTMLInputElement).checked)
    }
  }
}
</script>

<style scoped>
.toggle-switch {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  cursor: pointer;
  user-select: none;
}
.toggle-switch.disabled { cursor: not-allowed; opacity: 0.6; }
.toggle-switch.loading { cursor: wait; }

.toggle-switch input {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
}

.track {
  --track-w: 44px;
  --track-h: 24px;
  display: inline-flex;
  align-items: center;
  width: var(--track-w);
  height: var(--track-h);
  border-radius: var(--track-h);
  background: var(--color-border);
  padding: 2px;
  box-sizing: border-box;
  transition: background 0.2s ease;
  flex-shrink: 0;
}
.thumb {
  width: calc(var(--track-h) - 4px);
  height: calc(var(--track-h) - 4px);
  border-radius: 50%;
  background: var(--color-surface);
  box-shadow: var(--shadow-control);
  transition: transform 0.2s ease;
}

.toggle-switch input:checked + .track {
  background: var(--color-primary);
}
.toggle-switch input:checked + .track .thumb {
  transform: translateX(calc(var(--track-w) - var(--track-h)));
}

.toggle-switch input:focus-visible + .track {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.toggle-label { font-size: 0.9rem; font-weight: 500; color: var(--color-text); }
.toggle-loading {
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-primary-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: toggle-spin 0.7s linear infinite;
}
@keyframes toggle-spin { to { transform: rotate(360deg); } }
</style>
