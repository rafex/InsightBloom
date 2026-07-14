<template lang="pug">
div
  router-view
  CookieConsentBanner
  SessionExpiryModal(:show="showWarning" :seconds="secondsRemaining" @keep-connected="keepConnected")
  .version-tag {{ ver }} ({{ sha.slice(0, 7) }})
</template>

<script lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import CookieConsentBanner from '@/components/CookieConsentBanner.vue'
import SessionExpiryModal from '@/components/SessionExpiryModal.vue'
import { useSessionManager } from '@/features/auth/useSessionManager'

export default {
  name: 'App',
  components: { CookieConsentBanner, SessionExpiryModal },
  setup() {
    const { showWarning, secondsRemaining, keepConnected, start, stop } = useSessionManager()
    const ver = import.meta.env.VITE_APP_VERSION || 'dev'
    const sha = import.meta.env.VITE_GIT_SHA || 'unknown'
    onMounted(start)
    onBeforeUnmount(stop)
    return { showWarning, secondsRemaining, keepConnected, ver, sha }
  }
}
</script>

<style scoped>
.version-tag {
  position: fixed; bottom: 8px; right: 12px;
  font-size: 0.7rem; color: rgba(107, 114, 128, 0.45);
  z-index: 1000; pointer-events: none; user-select: none;
}
</style>
