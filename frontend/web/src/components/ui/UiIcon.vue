<template lang="pug">
svg.ui-icon(
  :width="size"
  :height="size"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="1.8"
  stroke-linecap="round"
  stroke-linejoin="round"
  :aria-hidden="label ? undefined : 'true'"
  :aria-label="label || undefined"
  role="img"
)
  path(v-for="(path, index) in iconPaths[name]" :key="index" :d="path")
</template>

<script lang="ts">
const ICON_PATHS: Record<string, string[]> = {
  activity: ['M3 12h4l2-7 4 14 2-7h6'],
  presentation: ['M4 4h16v10H4z', 'M12 14v6', 'M8 20h8'],
  clock: ['M12 7v5l3 2', 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20'],
  users: ['M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2', 'M9.5 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M22 21v-2a4 4 0 0 0-3-3.87', 'M16 3.13a4 4 0 0 1 0 7.75'],
  check: ['M5 12l4 4L19 6'],
  video: ['M15 10l4.5-3v10L15 14', 'M3 6h12v12H3z'],
  ticket: ['M3 7h18v10H3z', 'M7 7v10', 'M17 7v10']
}

export default {
  name: 'UiIcon',
  props: {
    name: { type: String, required: true },
    size: { type: [String, Number], default: 20 },
    label: { type: String, default: '' }
  },
  setup() {
    return { iconPaths: ICON_PATHS }
  }
}
</script>
