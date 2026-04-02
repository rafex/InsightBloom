<template lang="pug">
.dashboard-home
  h1 Dashboard
  p.subtitle Bienvenido al panel de InsightBloom
  .cards
    .card
      h3 Nueva conferencia
      p Crea una conferencia y obtén su ID amigable para compartir.
      router-link.btn-primary(to="/dashboard/conferences/new") Crear conferencia
</template>

<script>
export default { name: 'DashboardHome' }
</script>

<style scoped>
h1 { color: #1e1b4b; margin: 0 0 8px; }
.subtitle { color: #6b7280; margin-bottom: 32px; }
.cards { display: flex; gap: 20px; flex-wrap: wrap; }
.card { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); min-width: 240px; }
h3 { margin: 0 0 8px; color: #1e1b4b; }
p { color: #6b7280; font-size: 0.9rem; margin-bottom: 16px; }
.btn-primary { display: inline-block; padding: 8px 18px; background: #4f46e5; color: #fff; border-radius: 8px; text-decoration: none; font-size: 0.9rem; }
</style>
