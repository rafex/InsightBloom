<template lang="pug">
.dropdown-menu(ref="rootEl")
  button.dropdown-trigger(type="button" @click="open = !open")
    | {{ label }}
    span.dropdown-caret ▾
  .dropdown-panel(v-if="open" @click="open = false")
    slot
</template>

<script lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

export default {
  name: 'DropdownMenu',
  props: {
    label: { type: String, required: true }
  },
  setup() {
    const open = ref(false)
    const rootEl = ref<HTMLElement | null>(null)

    function onDocumentClick(e: MouseEvent) {
      if (rootEl.value && !rootEl.value.contains(e.target as Node)) {
        open.value = false
      }
    }

    onMounted(() => document.addEventListener('click', onDocumentClick))
    onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))

    return { open, rootEl }
  }
}
</script>

<style scoped>
.dropdown-menu { position: relative; display: inline-block; }

.dropdown-trigger {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; color: #6b7280; border: 1px solid #e5e7eb; border-radius: 8px;
  font-size: 0.78rem; background: #fff; cursor: pointer;
}
.dropdown-trigger:hover { background: #f3f4f6; color: #374151; }
.dropdown-caret { font-size: 0.7rem; }

.dropdown-panel {
  position: absolute; top: calc(100% + 4px); left: 0; z-index: 50;
  min-width: 160px; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1); padding: 4px; display: flex; flex-direction: column;
}

.dropdown-panel :deep(a), .dropdown-panel :deep(button) {
  display: block; width: 100%; text-align: left; padding: 8px 10px; border-radius: 6px;
  color: #374151; text-decoration: none; font-size: 0.85rem; border: none; background: none; cursor: pointer;
}
.dropdown-panel :deep(a:hover), .dropdown-panel :deep(button:hover) { background: #f3f4f6; }
</style>
