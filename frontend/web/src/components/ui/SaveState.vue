<template lang="pug">
.save-state(:class="`state-${resolvedState}`" role="status" aria-live="polite")
  span.save-state-dot(aria-hidden="true")
  span {{ labels[resolvedState] }}
</template>

<script lang="ts">
import { computed } from 'vue'

type SaveStateValue = 'clean' | 'dirty' | 'saving' | 'saved'

const LABELS: Record<SaveStateValue, string> = {
  clean: 'Sin cambios',
  dirty: 'Cambios pendientes',
  saving: 'Guardando',
  saved: 'Guardado'
}

export default {
  name: 'SaveState',
  props: {
    state: { type: String, default: 'clean' }
  },
  setup(props: { state: SaveStateValue }) {
    const resolvedState = computed<SaveStateValue>(() =>
      (props.state in LABELS ? props.state : 'clean') as SaveStateValue)
    return { labels: LABELS, resolvedState }
  }
}
</script>

<style scoped>
.save-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 14px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
}
.save-state-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.state-clean { background: var(--color-surface-muted); color: var(--color-text-muted); }
.state-dirty { background: var(--color-warning-soft); color: var(--color-warning); }
.state-saving { background: var(--color-info-soft); color: var(--color-info); }
.state-saved { background: var(--color-success-soft); color: var(--color-success); }
</style>
