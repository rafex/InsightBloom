<template lang="pug">
header.app-header
  a.skip-link(href="#main-content") Saltar al contenido principal
  .offline-banner(v-if="!online" role="status" aria-live="polite") 📡 Sin conexión · viendo contenido guardado
  .header-row
    .header-brand
      router-link(to="/")
        img.brand-logo(src="@/assets/logo.svg" alt="InsightBloom")
    nav.header-nav(aria-label="Acciones de sesión")
      span.version-tag(v-if="version") v{{ version }}{{ gitSha ? ' \u00b7 ' + gitSha : '' }}
      router-link(v-if="auth.state.token && auth.state.role !== 'guest'" to="/dashboard") Panel
      button.header-logout(v-if="auth.state.token" type="button" @click="logout") Salir
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
    const version = import.meta.env.VITE_APP_VERSION || 'dev'
    const gitSha = import.meta.env.VITE_GIT_SHA?.slice(0, 7) || ''
    return { auth, logout, online, version, gitSha }
  }
}
</script>

<style scoped>
.app-header {
  background: var(--color-heading); color: var(--color-text-inverse);
  position: sticky; top: 0; z-index: 100;
}
.skip-link {
  position: absolute;
  top: -100px;
  left: 12px;
  z-index: 110;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-primary-dark);
  font-weight: 700;
  text-decoration: none;
  box-shadow: var(--shadow-dropdown);
}
.skip-link:focus { top: 8px; }
.offline-banner {
  background: var(--color-warning-dark); color: var(--color-text-inverse); text-align: center;
  font-size: 0.78rem; padding: 4px 8px;
}
.header-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px;
}
.header-brand a { text-decoration: none; display: flex; align-items: center; }
.brand-logo { height: 36px; width: auto; }
.header-nav { display: flex; gap: 16px; }
.header-nav a, .header-logout {
  color: var(--color-header-link); text-decoration: none; font-size: 0.9rem;
}
.header-logout {
  border: 0; padding: 0; background: transparent; font-family: inherit; cursor: pointer;
}
.header-nav a:hover, .header-logout:hover { color: var(--color-text-inverse); }
.header-nav a:focus-visible, .header-logout:focus-visible {
  outline: 2px solid var(--color-focus); outline-offset: 4px; border-radius: 3px;
}
.version-tag {
  font-size: 0.7rem; color: var(--color-header-link-muted);
  background: rgba(255,255,255,0.08); padding: 2px 8px;
  border-radius: 9999px; margin-left: auto;
  white-space: nowrap; opacity: 0.7;
}

@media (max-width: 480px) {
  .header-row { padding: 10px 14px; }
  .header-nav { gap: 10px; }
  .header-nav a, .header-logout { font-size: 0.82rem; }
  .brand-logo { height: 28px; }
}
</style>
