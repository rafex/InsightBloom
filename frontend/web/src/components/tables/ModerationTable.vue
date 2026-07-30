<template lang="pug">
.moderation-table
  table
    thead
      tr
        slot(name="headers")
    tbody
      tr(v-for="item in items" :key="item.uuid || item.messageId")
        slot(name="row" :item="item")
  .pagination(v-if="totalPages > 1")
    BaseButton(variant="secondary" size="sm" @click="$emit('page', currentPage - 1)" :disabled="currentPage <= 1") &laquo; Anterior
    span Página {{ currentPage }} / {{ totalPages }}
    BaseButton(variant="secondary" size="sm" @click="$emit('page', currentPage + 1)" :disabled="currentPage >= totalPages") Siguiente &raquo;
</template>

<script lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'

export default {
  name: 'ModerationTable',
  components: { BaseButton },
  props: {
    items: { type: Array, default: () => [] },
    currentPage: { type: Number, default: 1 },
    totalPages: { type: Number, default: 1 }
  },
  emits: ['page']
}
</script>

<style scoped>
.moderation-table { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
thead tr { background: var(--color-surface-muted); }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--color-border-subtle); }
tr:hover td { background: var(--color-surface-muted); }
.pagination {
  display: flex; gap: 12px; align-items: center; justify-content: flex-end;
  padding: 12px 0; font-size: 0.9rem;
}
</style>
