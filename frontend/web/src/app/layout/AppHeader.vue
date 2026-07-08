<template lang="pug">
header.app-header
  .offline-banner(v-if="!online") 📡 Sin conexión · viendo contenido guardado
  .header-row
    .header-brand
      router-link(to="/")
        img.brand-logo(src="@/assets/logo.svg" alt="InsightBloom")
    nav.header-nav
      router-link(v-if="auth.state.token" to="/dashboard") Dashboard
      a(v-if="auth.state.token" href="#" @click.prevent="logout") Salir
      router-link(v-else to="/login") Entrar
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from '@/features/auth/authStore'
import { useRouter } from 'vue-router'
export default {
  name: 'AppHeader',
  setup() {
    const auth = useAuthStore()
    const router = useRouter()
    const online = ref(navigator.onLine)
    function setOnline() { online.value = true }
    function setOffline() { online.value = false }
    onMounted(() => {
      window.addEventListener('online', setOnline)
      window.addEventListener('offline', setOffline)
    })
    onBeforeUnmount(() => {
      window.removeEventListener('online', setOnline)
      window.removeEventListener('offline', setOffline)
    })
    function logout() { auth.logout(); router.push('/') }
    return { auth, logout, online }
  }
}
</script>

<style scoped>
.app-header {
  background: #1e1b4b; color: #fff;
  position: sticky; top: 0; z-index: 100;
}
.offline-banner {
  background: #92400e; color: #fff; text-align: center;
  font-size: 0.78rem; padding: 4px 8px;
}
.header-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px;
}
.header-brand a { text-decoration: none; display: flex; align-items: center; }
.brand-logo { height: 36px; width: auto; }
.header-nav { display: flex; gap: 16px; }
.header-nav a { color: #c7d2fe; text-decoration: none; font-size: 0.9rem; }
.header-nav a:hover { color: #fff; }

@media (max-width: 480px) {
  .header-row { padding: 10px 14px; }
  .header-nav { gap: 10px; }
  .header-nav a { font-size: 0.82rem; }
  .brand-logo { height: 28px; }
}
</style>
