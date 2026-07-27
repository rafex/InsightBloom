<template lang="pug">
.form-field
  label(:for="fieldId") {{ label }}
    span.req(v-if="required" aria-hidden="true") *
  slot(:id="fieldId" :describedBy="error ? errorId : hint ? hintId : undefined")
  p.hint(v-if="hint && !error" :id="hintId") {{ hint }}
  p.field-error(v-if="error" :id="errorId" role="alert") {{ error }}
</template>

<script lang="ts">
// Wrapper de campo de formulario (auditoría UX 2026-07-26): .form-group estaba redefinido en 14
// archivos con gaps distintos y labels con 5 tipografías. El slot recibe id/describedBy para que
// el input real quede asociado al label y al mensaje de error (a11y).
let fieldSeq = 0

export default {
  name: 'FormField',
  props: {
    label: { type: String, required: true },
    hint: { type: String, default: '' },
    error: { type: String, default: '' },
    required: { type: Boolean, default: false }
  },
  setup() {
    const n = ++fieldSeq
    return {
      fieldId: `form-field-${n}`,
      hintId: `form-field-hint-${n}`,
      errorId: `form-field-error-${n}`
    }
  }
}
</script>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
}
label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.req { color: var(--color-danger); margin-left: 2px; }
.hint { margin: 0; font-size: 0.82rem; color: var(--color-text-muted); }
.field-error { margin: 0; font-size: 0.85rem; color: var(--color-danger); }
</style>
