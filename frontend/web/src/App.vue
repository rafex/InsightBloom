<template lang="pug">
div
  router-view
  CookieConsentBanner
  SessionExpiryModal(:show="showWarning" :seconds="secondsRemaining" @keep-connected="keepConnected")
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
    onMounted(start)
    onBeforeUnmount(stop)
    return { showWarning, secondsRemaining, keepConnected }
  }
}
</script>
