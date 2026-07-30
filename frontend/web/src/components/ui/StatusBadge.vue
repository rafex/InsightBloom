<template lang="pug">
span.status-badge(:class="`tone-${resolvedTone}`") {{ resolvedLabel }}
</template>

<script lang="ts">
import { computed } from 'vue'
import { formatStatusLabel } from '@/utils/status'

type StatusTone = 'success' | 'warning' | 'danger' | 'neutral' | 'info'

const STATUS_TONES: Record<string, StatusTone> = {
  ACTIVE: 'success',
  VISIBLE: 'success',
  BANNED: 'danger',
  DELETED: 'neutral',
  INACTIVE: 'warning',
  PENDING: 'warning',
  PENDIENTE_REVISION: 'warning',
  CENSURADO_AUTO: 'danger',
  CENSURADO_MANUAL: 'danger'
}

export default {
  name: 'StatusBadge',
  props: {
    status: { type: String, required: true },
    label: { type: String, default: '' },
    tone: { type: String, default: '' }
  },
  setup(props: { status: string, label: string, tone: string }) {
    const resolvedLabel = computed(() => props.label || formatStatusLabel(props.status))
    const resolvedTone = computed<StatusTone>(() => {
      if (props.tone && props.tone in { success: true, warning: true, danger: true, neutral: true, info: true }) {
        return props.tone as StatusTone
      }
      return STATUS_TONES[props.status] || 'neutral'
    })
    return { resolvedLabel, resolvedTone }
  }
}
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  font-size: 0.78rem;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
}
.tone-success { background: var(--color-success-soft); color: var(--color-success); }
.tone-warning { background: var(--color-warning-soft); color: var(--color-warning); }
.tone-danger { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.tone-info { background: var(--color-info-soft); color: var(--color-info); }
.tone-neutral { background: var(--color-surface-muted); color: var(--color-text-muted); }
</style>
