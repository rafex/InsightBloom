<template lang="pug">
.cookie-banner(v-if="visible")
  p.
    Usamos cookies estándar para mantener tu sesión y mejorar tu experiencia en InsightBloom.
    Al continuar navegando aceptas su uso.
  BaseButton.cookie-accept(size="sm" type="button" @click="accept") Entendido
</template>

<script lang="ts">
import { ref } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const STORAGE_KEY = 'ib_cookie_consent'

export default {
  name: 'CookieConsentBanner',
  components: { BaseButton },
  setup() {
    const visible = ref(!localStorage.getItem(STORAGE_KEY))

    function accept() {
      localStorage.setItem(STORAGE_KEY, '1')
      visible.value = false
    }

    return { visible, accept }
  }
}
</script>

<style scoped>
.cookie-banner {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 20px;
  background: var(--color-heading);
  color: var(--color-primary-soft);
  font-size: 0.85rem;
}
.cookie-banner p { margin: 0; max-width: 640px; }
.cookie-accept { flex-shrink: 0; }

@media (max-width: 480px) {
  .cookie-banner { flex-direction: column; text-align: center; padding: 12px 16px; }
}
</style>
