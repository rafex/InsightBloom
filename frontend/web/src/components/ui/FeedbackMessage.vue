<template lang="pug">
p.feedback-message(:class="`tone-${tone}`" :role="tone === 'error' ? 'alert' : 'status'" aria-live="polite")
  | {{ message }}
</template>

<script lang="ts">
export default {
  name: 'FeedbackMessage',
  props: {
    message: { type: String, required: true },
    tone: { type: String, default: 'error' }
  }
}
</script>

<style scoped>
.feedback-message {
  margin: 0 0 12px;
  font-size: 0.9rem;
}
.tone-error { color: var(--color-danger); }
.tone-success { color: var(--color-success); }
.tone-info { color: var(--color-info); }
</style>
