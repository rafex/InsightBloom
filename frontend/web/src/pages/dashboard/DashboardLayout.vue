<template lang="pug">
.dashboard-layout
  AppHeader
  .dashboard-body
    button.sidebar-toggle(type="button" @click="sidebarOpen = !sidebarOpen" :aria-expanded="sidebarOpen") ☰
    .sidebar-backdrop(v-if="sidebarOpen" @click="sidebarOpen = false")
    aside.sidebar(:class="{ open: sidebarOpen }")
      nav(aria-label="Navegación principal")
        router-link(to="/dashboard" active-class="" exact-active-class="router-link-active" @click="sidebarOpen = false") Inicio
        .nav-section
          h2.nav-section-title Eventos
          router-link(v-if="isOrganizer" to="/dashboard/conferences" @click="sidebarOpen = false") Mis eventos
          router-link(to="/events" @click="sidebarOpen = false") Cartelera pública
          router-link(v-if="!isOrganizer" to="/dashboard/join" @click="sidebarOpen = false") Unirse a un evento
        .nav-section(v-if="isOrganizer || isAdmin")
          h2.nav-section-title Plataforma
          router-link(v-if="isAdmin" to="/dashboard/admin/users" @click="sidebarOpen = false") Usuarios
          router-link(v-if="isAdmin" to="/dashboard/admin/roles" @click="sidebarOpen = false") Roles
          router-link(v-if="isAdmin" to="/dashboard/admin/event-types" @click="sidebarOpen = false") Tipos de evento
          router-link(v-if="isAdmin" to="/dashboard/admin/ai" @click="sidebarOpen = false") IA
          router-link(v-if="isAdmin" to="/dashboard/admin/device-access" @click="sidebarOpen = false") Acceso por dispositivo
          router-link(v-if="isAdmin" to="/dashboard/admin/egress-policy" @click="sidebarOpen = false") Control de red
          router-link(v-if="isOrganizer" to="/dashboard/certificate-settings" @click="sidebarOpen = false") Plantilla global
        .nav-section
          h2.nav-section-title Cuenta
          router-link(to="/profile" @click="sidebarOpen = false") Mi perfil
    main.dashboard-main
      router-view
</template>

<script lang="ts">
import { ref } from 'vue'
import AppHeader from '@/app/layout/AppHeader.vue'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'DashboardLayout',
  components: { AppHeader },
  setup() {
    const auth = useAuthStore()
    const sidebarOpen = ref(false)
    return { isOrganizer: auth.isOrganizer(), isAdmin: auth.isAdmin(), sidebarOpen }
  }
}
</script>

<style scoped>
.dashboard-layout { min-height: 100vh; background: #f5f3ff; }
.dashboard-body { display: flex; position: relative; }
.sidebar-toggle { display: none; }
.sidebar-backdrop { display: none; }
.sidebar { width: 220px; background: #fff; border-right: 1px solid #e5e7eb; min-height: calc(100vh - 56px); padding: 24px 16px; }
.sidebar nav { display: flex; flex-direction: column; gap: 8px; }
.nav-section { display: flex; flex-direction: column; gap: 4px; margin-top: 12px; }
.nav-section-title {
  margin: 0 12px 2px;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.sidebar nav a { padding: 8px 12px; border-radius: 6px; text-decoration: none; color: #374151; font-size: 0.95rem; }
.sidebar nav a:hover, .sidebar nav a.router-link-active { background: #ede9fe; color: #4f46e5; }
.dashboard-main { flex: 1; padding: 32px; min-width: 0; }

@media (max-width: 768px) {
  .sidebar-toggle {
    display: block; position: fixed; top: 64px; left: 12px; z-index: 60;
    width: 40px; height: 40px; border-radius: 8px; border: 1px solid #e5e7eb;
    background: #fff; color: #4f46e5; font-size: 1.1rem; cursor: pointer;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  }
  .sidebar-backdrop {
    display: block; position: fixed; inset: 0; top: 56px; background: rgba(0,0,0,0.4); z-index: 40;
  }
  .sidebar {
    position: fixed; top: 56px; bottom: 0; left: 0; z-index: 50;
    min-height: auto; padding-top: 64px; transform: translateX(-100%); transition: transform 0.2s ease;
    box-shadow: 2px 0 12px rgba(0,0,0,0.15);
  }
  .sidebar.open { transform: translateX(0); }
  .dashboard-main { padding: 64px 16px 24px; }
}
</style>
