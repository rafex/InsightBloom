<template lang="pug">
nav.breadcrumbs(aria-label="breadcrumb")
  template(v-for="(item, i) in allItems" :key="i")
    router-link(v-if="item.to" :to="item.to") {{ item.label }}
    span.crumb-loading(v-else-if="item.loading") …
    span.crumb-current(v-else) {{ item.label }}
    span.sep(v-if="i < allItems.length - 1") /
</template>

<script lang="ts">
// El crumb raíz ("Panel" → /dashboard) lo agrega este componente — estaba duplicado literal
// como { label: 'Dashboard' } en 14 páginas (auditoría UX 2026-07-26; de paso se traduce el
// anglicismo). Las páginas solo declaran sus crumbs propios.
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
    const allItems = computed<BreadcrumbItem[]>(() => [
      { label: 'Panel', to: '/dashboard' },
      ...props.items
    ])
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
.crumb-current { color: #374151; font-weight: 500; }
.crumb-loading { color: var(--color-text-muted); }
</style>
