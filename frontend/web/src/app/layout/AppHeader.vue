<template lang="pug">
header.app-header
  .header-brand
    router-link(to="/")
      img.brand-logo(src="@/assets/logo.svg" alt="InsightBloom")
  nav.header-nav
    router-link(v-if="auth.state.token" to="/dashboard") Dashboard
    a(v-if="auth.state.token" href="#" @click.prevent="logout") Salir
    router-link(v-else to="/login") Entrar
</template>

<script>
import { useAuthStore } from '@/features/auth/authStore'
import { useRouter } from 'vue-router'
export default {
  name: 'AppHeader',
  setup() {
    const auth = useAuthStore()
    const router = useRouter()
    function logout() { auth.logout(); router.push('/') }
    return { auth, logout }
  }
}
</script>

<style scoped>
.app-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px; background: #1e1b4b; color: #fff;
  position: sticky; top: 0; z-index: 100;
}
.header-brand a { text-decoration: none; display: flex; align-items: center; }
.brand-logo { height: 36px; width: auto; }
.header-nav { display: flex; gap: 16px; }
.header-nav a { color: #c7d2fe; text-decoration: none; font-size: 0.9rem; }
.header-nav a:hover { color: #fff; }
</style>
