<template lang="pug">
div
  router-view
  CookieConsentBanner
</template>

<script>
import CookieConsentBanner from '@/components/CookieConsentBanner.vue'
export default { name: 'App', components: { CookieConsentBanner } }
</script>
