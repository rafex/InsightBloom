<template lang="pug">
.cookie-banner(v-if="visible")
  p.
    Usamos cookies estándar para mantener tu sesión y mejorar tu experiencia en InsightBloom.
    Al continuar navegando aceptas su uso.
  button.btn-accept(type="button" @click="accept") Entendido
</template>

<script lang="ts">
import { ref } from 'vue'

const STORAGE_KEY = 'ib_cookie_consent'

export default {
  name: 'CookieConsentBanner',
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
  background: #1e1b4b;
  color: #e0e7ff;
  font-size: 0.85rem;
}
.cookie-banner p { margin: 0; max-width: 640px; }
.btn-accept {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: #4f46e5;
  color: #fff;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
  flex-shrink: 0;
}
.btn-accept:hover { background: #4338ca; }

@media (max-width: 480px) {
  .cookie-banner { flex-direction: column; text-align: center; padding: 12px 16px; }
}
</style>
