<template lang="pug">
.loading-state(role="status" aria-live="polite") {{ message }}
</template>

<script lang="ts">
export default {
  name: 'LoadingState',
  props: {
    message: { type: String, required: true }
  }
}
</script>

<style scoped>
.loading-state {
  padding: var(--space-8);
  text-align: center;
  color: var(--color-text-muted);
}
</style>
