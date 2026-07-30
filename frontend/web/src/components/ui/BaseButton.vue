<template lang="pug">
button.base-btn(
  :class="[`v-${variant}`, `s-${size}`, { 'is-loading': loading }]"
  :type="type"
  :disabled="disabled || loading"
  :aria-busy="loading || undefined"
)
  span.spinner(v-if="loading" aria-hidden="true")
  slot
</template>

<script lang="ts">
// Botón canónico del sistema de diseño (auditoría UX 2026-07-26): btn-primary estaba
// redefinido en ~20 páginas con paddings/disabled divergentes. Las páginas migran a este
// componente gradualmente; las clases locales viejas conviven mientras tanto.
export default {
  name: 'BaseButton',
  props: {
    variant: { type: String, default: 'primary' }, // primary | secondary | success | danger | ghost
    size: { type: String, default: 'md' },         // sm | md | lg
    type: { type: String, default: 'button' },
    disabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false }
  }
}
</script>

<style scoped>
.base-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.base-btn:disabled { cursor: not-allowed; opacity: 0.55; }
.base-btn:focus-visible { outline: 3px solid var(--color-focus); outline-offset: 2px; }

.s-sm { padding: 6px 12px; font-size: 0.85rem; }
.s-md { padding: 10px 18px; font-size: 0.95rem; }
.s-lg { padding: 12px 24px; font-size: 1rem; }

.v-primary { background: var(--color-primary); color: var(--color-on-primary); }
.v-primary:hover:not(:disabled) { background: var(--color-primary-dark); }

.v-secondary {
  background: var(--color-surface);
  color: var(--color-primary);
  border-color: var(--color-primary-border);
}
.v-secondary:hover:not(:disabled) { background: var(--color-primary-soft); }

.v-success { background: var(--color-success-soft); color: var(--color-success); }
.v-success:hover:not(:disabled) { filter: brightness(0.96); }

.v-danger { background: var(--color-danger); color: var(--color-on-danger); }
.v-danger:hover:not(:disabled) { background: var(--color-danger-dark); }

.v-ghost { background: transparent; color: var(--color-text-secondary); }
.v-ghost:hover:not(:disabled) { background: var(--color-primary-soft); color: var(--color-primary); }

.spinner {
  width: 1em;
  height: 1em;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: base-btn-spin 0.7s linear infinite;
}
@keyframes base-btn-spin { to { transform: rotate(360deg); } }
</style>
