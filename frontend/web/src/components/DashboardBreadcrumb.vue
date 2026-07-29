<template lang="pug">
nav.breadcrumbs(aria-label="Ruta de navegación")
  template(v-for="(item, i) in allItems" :key="i")
    router-link(v-if="item.to" :to="item.to") {{ item.label }}
    span.crumb-loading(v-else-if="item.loading") …
    span.crumb-current(v-else aria-current="page") {{ item.label }}
    span.sep(v-if="i < allItems.length - 1" aria-hidden="true") /
</template>

<script lang="ts">
// Las páginas declaran únicamente la ruta contextual que necesitan. El dashboard raíz no se
// repite aquí porque ya está disponible como "Inicio" en la navegación global.
import { computed } from 'vue'

export interface BreadcrumbItem {
  label: string
  to?: string
  loading?: boolean
}

export default {
  name: 'DashboardBreadcrumb',
  props: {
    items: { type: Array as () => BreadcrumbItem[], required: true }
  },
  setup(props: { items: BreadcrumbItem[] }) {
    const allItems = computed<BreadcrumbItem[]>(() => props.items)
    return { allItems }
  }
}
</script>

<style scoped>
.breadcrumbs {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: #6b7280;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.breadcrumbs a { color: #4f46e5; text-decoration: none; }
.breadcrumbs a:hover { text-decoration: underline; }
.breadcrumbs a:focus-visible,
.crumb-current:focus-visible { outline: 2px solid #4f46e5; outline-offset: 3px; border-radius: 3px; }
.crumb-current { color: #374151; font-weight: 500; }
.crumb-loading { color: var(--color-text-muted); }
</style>
