<template lang="pug">
RouterLink.base-link(:class="[`v-${variant}`, `s-${size}`]" :to="to")
  slot
</template>

<script lang="ts">
import { RouterLink } from 'vue-router'

// Acción de navegación canónica. Evita que cada pantalla redefina los enlaces que
// visualmente son botones y conserva la semántica/enrutamiento de RouterLink.
export default {
  name: 'BaseLink',
  components: { RouterLink },
  props: {
    to: { type: [String, Object], required: true },
    variant: { type: String, default: 'primary' }, // primary | secondary | success | ghost
    size: { type: String, default: 'md' } // sm | md | lg
  }
}
</script>

<style scoped>
.base-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-weight: 600;
  text-decoration: none;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.base-link:focus-visible { outline: 3px solid var(--color-focus); outline-offset: 2px; }

.s-sm { padding: 6px 12px; font-size: 0.85rem; }
.s-md { padding: 10px 18px; font-size: 0.95rem; }
.s-lg { padding: 12px 24px; font-size: 1rem; }

.v-primary { background: var(--color-primary); color: var(--color-on-primary); }
.v-primary:hover { background: var(--color-primary-dark); }
.v-secondary { background: var(--color-surface); color: var(--color-primary); border-color: var(--color-primary-border); }
.v-secondary:hover { background: var(--color-primary-soft); }
.v-success { background: var(--color-success); color: var(--color-on-primary); }
.v-success:hover { filter: brightness(0.96); }
.v-ghost { background: transparent; color: var(--color-text-secondary); }
.v-ghost:hover { background: var(--color-primary-soft); color: var(--color-primary); }
</style>
