<template lang="pug">
.dashboard-layout
  AppHeader
  .dashboard-body
    BaseButton.sidebar-toggle(variant="secondary" size="sm" type="button" @click="sidebarOpen = !sidebarOpen" :aria-expanded="sidebarOpen" :aria-label="sidebarOpen ? 'Cerrar navegación' : 'Abrir navegación'") ☰
    .sidebar-backdrop(v-if="sidebarOpen" aria-hidden="true" @click="sidebarOpen = false")
    aside.sidebar(:class="{ open: sidebarOpen }")
      nav(aria-label="Navegación principal")
        router-link(to="/dashboard" active-class="" exact-active-class="router-link-active" @click="sidebarOpen = false") Panel
        .nav-section
          h2.nav-section-title Eventos
          router-link(v-if="isModerator" to="/dashboard/conferences" @click="sidebarOpen = false") Mis eventos
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
import { ref, onMounted } from 'vue'
import AppHeader from '@/app/layout/AppHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { getConferences } from '@/services/api/usersApi'
export default {
  name: 'DashboardLayout',
  components: { AppHeader, BaseButton },
  setup() {
    const auth = useAuthStore()
    const sidebarOpen = ref(false)
    const isModerator = ref(auth.isModerator())
    onMounted(async () => {
      // Los moderadores asignados por evento pueden tener solo el rol global ATTENDEE.
      // La lista de conferencias ya está limitada por el backend a eventos propios/asignados.
      if (isModerator.value || !auth.state.token) return
      try {
        isModerator.value = (await getConferences(auth.state.token)).length > 0
      } catch (e: any) {
        // La navegación base sigue disponible aunque falle la detección opcional.
      }
    })
    return {
      isOrganizer: auth.isOrganizer(),
      isModerator,
      isAdmin: auth.isAdmin(),
      sidebarOpen
    }
  }
}
</script>

<style scoped>
.dashboard-layout { min-height: 100vh; background: var(--color-bg); }
.dashboard-body { display: flex; position: relative; }
.sidebar-toggle { display: none; }
.sidebar-backdrop { display: none; }
.sidebar { width: 220px; background: var(--color-surface); border-right: 1px solid var(--color-border-subtle); min-height: calc(100vh - 56px); padding: 24px 16px; }
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
.sidebar nav a { padding: 8px 12px; border-radius: 6px; text-decoration: none; color: var(--color-text-secondary); font-size: 0.95rem; }
.sidebar nav a:hover, .sidebar nav a.router-link-active { background: var(--color-primary-soft); color: var(--color-primary); }
.dashboard-main { flex: 1; padding: 32px; min-width: 0; }

@media (max-width: 768px) {
  .sidebar-toggle {
    display: inline-flex; position: fixed; top: 64px; left: 12px; z-index: 60;
    width: 40px; height: 40px; padding: 0; border-radius: var(--radius-md);
    font-size: 1.1rem;
    box-shadow: var(--shadow-dropdown);
  }
  .sidebar-backdrop {
    display: block; position: fixed; inset: 0; top: 56px; background: var(--color-overlay); z-index: 40;
  }
  .sidebar {
    position: fixed; top: 56px; bottom: 0; left: 0; z-index: 50;
    min-height: auto; padding-top: 64px; transform: translateX(-100%); transition: transform 0.2s ease;
    box-shadow: var(--shadow-dropdown);
  }
  .sidebar.open { transform: translateX(0); }
  .dashboard-main { padding: 64px 16px 24px; }
}
</style>
