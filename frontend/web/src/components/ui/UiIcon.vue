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
  camera: ['M4 7h3l2-2h6l2 2h3v12H4z', 'M12 16a3 3 0 1 0 0-6 3 3 0 0 0 0 6'],
  image: ['M4 5h16v14H4z', 'M7 15l3-3 2 2 2-2 3 3', 'M8 9h.01'],
  ticket: ['M3 7h18v10H3z', 'M7 7v10', 'M17 7v10'],
  flyer: ['M4 4h16v16H4z', 'M8 8h8', 'M8 12h8', 'M8 16h5'],
  calendar: ['M4 5h16v15H4z', 'M8 3v4', 'M16 3v4', 'M4 10h16'],
  pin: ['M12 21s7-7.58 7-12a7 7 0 1 0-14 0c0 4.42 7 12 7 12z', 'M12 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4'],
  bell: ['M6 8a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6', 'M10 21a2 2 0 0 0 4 0'],
  help: ['M9.1 9a3 3 0 1 1 5.8 1c0 2-3 2-3 4', 'M12 17h.01', 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20'],
  idea: ['M9 18h6', 'M10 22h4', 'M8.5 14.5a6 6 0 1 1 7 0c-.9.7-1.5 1.6-1.5 2.5h-5c0-.9-.6-1.8-1.5-2.5z'],
  chat: ['M4 5h16v11H8l-4 4z', 'M8 9h8', 'M8 12h5'],
  survey: ['M6 3h12v18H6z', 'M9 7h6', 'M9 11h6', 'M9 15h4'],
  diagram: ['M6 4h4v4H6z', 'M14 16h4v4h-4z', 'M14 4h4v4h-4z', 'M6 16h4v4H6z', 'M10 6h4', 'M8 8v8', 'M10 18h4'],
  whiteboard: ['M4 4h16v13H4z', 'M8 21l3-4', 'M13 21l-3-4', 'M8 9h8', 'M8 12h5'],
  notes: ['M5 3h14v18H5z', 'M8 7h8', 'M8 11h8', 'M8 15h5'],
  code: ['M9 6L3 12l6 6', 'M15 6l6 6-6 6', 'M13 4l-2 16'],
  download: ['M12 3v11', 'M7 10l5 5 5-5', 'M4 19h16'],
  globe: ['M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20', 'M2 12h20', 'M12 2c3 3 3 17 0 20', 'M12 2c-3 3-3 17 0 20'],
  api: ['M4 4h16v16H4z', 'M8 9h8', 'M8 13h5', 'M8 17h8'],
  trash: ['M4 7h16', 'M10 11v6', 'M14 11v6', 'M6 7l1 14h10l1-14', 'M9 7V4h6v3'],
  refresh: ['M20 11a8 8 0 0 0-14.7-4L3 10', 'M3 5v5h5', 'M4 13a8 8 0 0 0 14.7 4L21 14', 'M21 19v-5h-5'],
  qr: ['M3 3h7v7h-7z', 'M14 3h7v7h-7z', 'M3 14h7v7h-7z', 'M14 14L14 17', 'M14 14L17 14', 'M17 17L21 17', 'M21 14L21 21', 'M14 21L17 21'],
  copy: ['M8 8h12v12H8z', 'M4 4h12v12h-12z'],
  send: ['M22 2L11 13', 'M22 2L15 22L11 13L2 9Z'],
  edit: ['M12 20h9', 'M16.5 3.5a2.121 2.121 0 1 1 3 3L7 19l-4 1 1-4L16.5 3.5z'],
  link: ['M8 12a4 4 0 0 1 4-4h3a4 4 0 0 1 0 8h-1', 'M16 12a4 4 0 0 1-4 4H9a4 4 0 0 1 0-8h1']
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
