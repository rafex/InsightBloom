<template lang="pug">
.empty-state(role="status" aria-live="polite")
  p {{ message }}
  slot
</template>

<script lang="ts">
export default {
  name: 'EmptyState',
  props: {
    message: { type: String, required: true }
  }
}
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  min-height: 120px;
  padding: var(--space-6);
  color: var(--color-text-muted);
  text-align: center;
}
.empty-state p { margin: 0; }
</style>
