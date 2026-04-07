<template lang="pug">
.dashboard-home
  .dashboard-header
    h1 Dashboard
    router-link.btn-primary(to="/dashboard/conferences/new") + Nueva conferencia

  .section(v-if="loading")
    .loading-text Cargando conferencias...

  .section(v-else-if="conferences.length === 0")
    .empty-state
      p Aún no tienes conferencias.
      router-link.btn-primary(to="/dashboard/conferences/new") Crear la primera

  .section(v-else)
    h2 Tus conferencias
    .conference-grid
      .conf-card(v-for="c in conferences" :key="c.uuid || c.conferenceId")
        .conf-card-header
          span.friendly-id {{ c.friendlyId }}
          span.status-badge(:class="c.status") {{ c.status }}
        h3.conf-name {{ c.name }}
        .conf-actions
          a.btn-outline(:href="`/c/${c.friendlyId}/doubts`" target="_blank") Ver nube
          router-link.btn-ghost(:to="`/dashboard/conferences/${c.uuid || c.conferenceId}/moderation/words`") Moderación
</template>

<script>
import { ref, onMounted } from 'vue'
import { getConferences } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'DashboardHome',
  setup() {
    const conferences = ref([])
    const loading = ref(true)
    const auth = useAuthStore()

    onMounted(async () => {
      try {
        const token = auth.state.token
        if (token) conferences.value = await getConferences(token)
      } catch (e) {
        console.error('Error cargando conferencias', e)
      } finally {
        loading.value = false
      }
    })

    return { conferences, loading }
  }
}
</script>

<style scoped>
.dashboard-home { padding: 32px 24px; max-width: 960px; }
.dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
h1 { color: #1e1b4b; margin: 0; font-size: 1.8rem; }
h2 { color: #374151; font-size: 1.1rem; font-weight: 600; margin: 0 0 16px; }

.section { margin-bottom: 32px; }
.loading-text { color: #6b7280; }
.empty-state { text-align: center; padding: 48px; background: #f9fafb; border-radius: 12px; }
.empty-state p { color: #6b7280; margin-bottom: 16px; }

.conference-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.conf-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  transition: box-shadow 0.2s;
}
.conf-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }

.conf-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.friendly-id { font-size: 0.78rem; color: #6b7280; font-family: monospace; }
.status-badge {
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 99px;
  font-weight: 600;
  text-transform: uppercase;
}
.status-badge.ACTIVE, .status-badge.active { background: #d1fae5; color: #065f46; }
.status-badge.CLOSED, .status-badge.closed { background: #fee2e2; color: #991b1b; }

.conf-name { font-size: 1rem; font-weight: 600; color: #1e1b4b; margin: 0 0 16px; }

.conf-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.btn-primary {
  display: inline-block;
  padding: 8px 18px;
  background: #4f46e5;
  color: #fff;
  border-radius: 8px;
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
}
.btn-primary:hover { background: #4338ca; }

.btn-outline {
  display: inline-block;
  padding: 6px 14px;
  border: 1px solid #4f46e5;
  color: #4f46e5;
  border-radius: 8px;
  text-decoration: none;
  font-size: 0.8rem;
}
.btn-outline:hover { background: #eef2ff; }

.btn-ghost {
  display: inline-block;
  padding: 6px 14px;
  color: #6b7280;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  text-decoration: none;
  font-size: 0.8rem;
}
.btn-ghost:hover { background: #f3f4f6; color: #374151; }
</style>
