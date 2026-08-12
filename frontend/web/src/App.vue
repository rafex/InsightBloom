<template lang="pug">
div
  router-view
  CookieConsentBanner
  SessionExpiryModal(:show="showWarning" :seconds="secondsRemaining" @keep-connected="keepConnected")
  AppToast
  .version-tag v{{ ver }}
</template>

<script lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import CookieConsentBanner from '@/components/CookieConsentBanner.vue'
import SessionExpiryModal from '@/components/SessionExpiryModal.vue'
import AppToast from '@/components/ui/AppToast.vue'
import { useSessionManager } from '@/features/auth/useSessionManager'

export default {
  name: 'App',
  components: { CookieConsentBanner, SessionExpiryModal, AppToast },
  setup() {
    const { showWarning, secondsRemaining, keepConnected, start, stop } = useSessionManager()
    const ver = import.meta.env.VITE_APP_VERSION || 'dev'
    onMounted(start)
    onBeforeUnmount(stop)
    return { showWarning, secondsRemaining, keepConnected, ver }
  }
}
</script>

<style scoped>
/* Sin el sha del commit (2026-08): quedaba redundante para el usuario final -- la versión sola
   ya identifica el release. Tono más marcado que antes (0.45 -> 0.65 de opacidad) para que se
   lea mejor sobre fondos claros y oscuros. */
.version-tag {
  position: fixed; bottom: 8px; right: 12px;
  font-size: 0.7rem; color: rgba(107, 114, 128, 0.65);
  z-index: 1000; pointer-events: none; user-select: none;
}
</style>
