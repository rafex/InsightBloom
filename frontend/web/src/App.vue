<template lang="pug">
div
  router-view
  CookieConsentBanner
  SessionExpiryModal(:show="showWarning" :seconds="secondsRemaining" @keep-connected="keepConnected")
  AppToast
  .version-tag v{{ ver }} ({{ sha.slice(0, 7) }})
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
    const sha = import.meta.env.VITE_GIT_SHA || 'unknown'
    onMounted(start)
    onBeforeUnmount(stop)
    return { showWarning, secondsRemaining, keepConnected, ver, sha }
  }
}
</script>

<style scoped>
/* El sha del commit (junto a la versión) es el único identificador visual confiable de qué
   build está realmente desplegado -- sin él no se puede distinguir a simple vista un deploy
   nuevo de uno cacheado (bug reportado 2026-08-12, restaurado tras haberse quitado por error
   en #68 creyéndolo redundante). Tono marcado (0.65 de opacidad) para que se lea bien sobre
   fondos claros y oscuros. */
.version-tag {
  position: fixed; bottom: 8px; right: 12px;
  font-size: 0.7rem; color: rgba(107, 114, 128, 0.65);
  z-index: 1000; pointer-events: none; user-select: none;
}
</style>
