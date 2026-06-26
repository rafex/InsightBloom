<template lang="pug">
.dashboard-layout
  AppHeader
  .dashboard-body
    aside.sidebar
      nav
        router-link(to="/dashboard") Inicio
        router-link(v-if="isOrganizer" to="/dashboard/conferences/new") Nueva conferencia
        router-link(v-else to="/dashboard/join") Unirse a una conferencia
        router-link(v-if="isOrganizer" to="/dashboard/certificate-settings") Diseño de certificado
        router-link(to="/profile") Mi perfil
    main.dashboard-main
      router-view
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'DashboardLayout',
  components: { AppHeader },
  setup() {
    const auth = useAuthStore()
    return { isOrganizer: auth.isOrganizer() }
  }
}
</script>

<style scoped>
.dashboard-layout { min-height: 100vh; background: #f5f3ff; }
.dashboard-body { display: flex; }
.sidebar { width: 220px; background: #fff; border-right: 1px solid #e5e7eb; min-height: calc(100vh - 56px); padding: 24px 16px; }
.sidebar nav { display: flex; flex-direction: column; gap: 8px; }
.sidebar nav a { padding: 8px 12px; border-radius: 6px; text-decoration: none; color: #374151; font-size: 0.95rem; }
.sidebar nav a:hover, .sidebar nav a.router-link-active { background: #ede9fe; color: #4f46e5; }
.dashboard-main { flex: 1; padding: 32px; }
</style>
