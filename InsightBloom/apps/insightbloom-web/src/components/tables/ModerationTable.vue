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
    button(@click="$emit('page', currentPage - 1)" :disabled="currentPage <= 1") &laquo; Anterior
    span Página {{ currentPage }} / {{ totalPages }}
    button(@click="$emit('page', currentPage + 1)" :disabled="currentPage >= totalPages") Siguiente &raquo;
</template>

<script>
export default {
  name: 'ModerationTable',
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
thead tr { background: #f3f4f6; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #e5e7eb; }
tr:hover td { background: #f9fafb; }
.pagination {
  display: flex; gap: 12px; align-items: center; justify-content: flex-end;
  padding: 12px 0; font-size: 0.9rem;
}
button {
  padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 6px;
  background: #fff; cursor: pointer;
}
button:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
