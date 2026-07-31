<template lang="pug">
.venue-canvas-editor
  .editor-header
    h3 📐 Diseñador de Recinto Manual
    p Define la geometría de tu recinto sin subir imagen

  .layout-selector
    .selector-group
      label Forma del recinto:
      select.shape-select(v-model="selectedShape" @change="updatePreview")
        option(value="rectangular") Rectangular (filas × columnas)
        option(value="semicircular") Semicircular (anfiteatro)
        option(value="horseshoe") Herradura
        option(value="custom") Personalizado (manual)

  .config-section(v-if="selectedShape !== 'custom'")
    .config-row
      .input-group
        label Largo (metros):
        input.input-field(v-model.number="layout.width" type="number" min="5" max="100" @input="updatePreview")
      .input-group
        label Ancho (metros):
        input.input-field(v-model.number="layout.height" type="number" min="5" max="100" @input="updatePreview")

    .config-row(v-if="selectedShape === 'rectangular'")
      .input-group
        label Asientos por fila:
        input.input-field(v-model.number="layout.seatsPerRow" type="number" min="1" max="50" @input="updatePreview")
      .input-group
        label Número de filas:
        input.input-field(v-model.number="layout.rows" type="number" min="1" max="50" @input="updatePreview")

    .config-row(v-if="selectedShape === 'semicircular'")
      .input-group
        label Asientos en semicírculo:
        input.input-field(v-model.number="layout.totalSeats" type="number" min="10" max="500" @input="updatePreview")
      .info-text ℹ️ Se distribuirán uniformemente en un arco semicircular

    .config-row(v-if="selectedShape === 'horseshoe'")
      .input-group
        label Asientos en herradura:
        input.input-field(v-model.number="layout.totalSeats" type="number" min="20" max="500" @input="updatePreview")
      .info-text ℹ️ Crea un patrón tipo herradura (útil para conferencias interactivas)

  .preview-section
    h4 Vista Previa ({{ totalSeats }} asientos)
    .canvas-container
      svg.preview-canvas(viewBox="0 0 400 300" ref="canvasRef")
        rect.background(x="0" y="0" width="400" height="300")
        g.stage
          rect.stage-rect(x="50" y="20" width="300" height="50" fill="#8b5cf6")
          text.stage-text(x="200" y="50" text-anchor="middle" fill="white" font-size="12" font-weight="bold") ESCENARIO
        g.seats(v-for="seat in previewSeats" :key="seat.id")
          circle.seat-circle(:cx="seat.x" :cy="seat.y" r="4" :data-seat="seat.id")

  .action-buttons
    BaseButton(@click="saveLayout" :disabled="!validLayout")
      span ✓ Guardar Geometría
    BaseButton(variant="secondary" @click="$emit('cancel')")
      span ✗ Cancelar
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'

type ShapeType = 'rectangular' | 'semicircular' | 'horseshoe' | 'custom'

interface Seat {
  id: string
  x: number
  y: number
  label: string
}

export default {
  name: 'VenueMapCanvasEditor',
  components: { BaseButton },
  emits: ['save', 'cancel'],
  setup(_, { emit }) {
    const selectedShape = ref<ShapeType>('rectangular')
    const canvasRef = ref<SVGSVGElement | null>(null)

    const layout = ref({
      width: 20,
      height: 15,
      seatsPerRow: 10,
      rows: 5,
      totalSeats: 100
    })

    const previewSeats = ref<Seat[]>([])

    const totalSeats = computed(() => {
      if (selectedShape.value === 'rectangular') {
        return layout.value.seatsPerRow * layout.value.rows
      }
      return layout.value.totalSeats
    })

    const validLayout = computed(() => {
      if (selectedShape.value === 'rectangular') {
        return layout.value.seatsPerRow > 0 && layout.value.rows > 0
      }
      return layout.value.totalSeats > 0
    })

    function generateRectangularSeats(): Seat[] {
      const seats: Seat[] = []
      const seatsPerRow = Math.min(layout.value.seatsPerRow, 25)
      const rows = Math.min(layout.value.rows, 30)
      const startX = 50
      const startY = 100
      const spacingX = 300 / seatsPerRow
      const spacingY = 150 / rows

      let seatNum = 1
      for (let row = 0; row < rows; row++) {
        for (let col = 0; col < seatsPerRow; col++) {
          seats.push({
            id: `seat-${seatNum}`,
            x: startX + col * spacingX + spacingX / 2,
            y: startY + row * spacingY + spacingY / 2,
            label: `${String.fromCharCode(65 + row)}${col + 1}`
          })
          seatNum++
        }
      }
      return seats
    }

    function generateSemicircularSeats(): Seat[] {
      const seats: Seat[] = []
      const count = Math.min(layout.value.totalSeats, 500)
      const centerX = 200
      const centerY = 220
      const radius = 120
      const startAngle = Math.PI
      const endAngle = 2 * Math.PI

      for (let i = 0; i < count; i++) {
        const angle = startAngle + (endAngle - startAngle) * (i / count)
        const x = centerX + radius * Math.cos(angle)
        const y = centerY + radius * Math.sin(angle)
        seats.push({
          id: `seat-${i + 1}`,
          x,
          y,
          label: `S${i + 1}`
        })
      }
      return seats
    }

    function generateHorseshoeSeats(): Seat[] {
      const seats: Seat[] = []
      const count = Math.min(layout.value.totalSeats, 500)
      const centerX = 200
      const centerY = 200
      const radiusOuter = 130

      const arcsPerSide = Math.floor(count / 3)
      let seatNum = 1

      // Arco izquierdo (270° a 90°)
      for (let i = 0; i < arcsPerSide; i++) {
        const angle = -Math.PI / 2 + (Math.PI * (i / (arcsPerSide - 1 || 1)))
        const x = centerX + radiusOuter * Math.cos(angle)
        const y = centerY + radiusOuter * Math.sin(angle)
        seats.push({
          id: `seat-${seatNum}`,
          x,
          y,
          label: `L${i + 1}`
        })
        seatNum++
      }

      // Arco derecho (90° a 270°)
      for (let i = 0; i < arcsPerSide; i++) {
        const angle = Math.PI / 2 + (Math.PI * (i / (arcsPerSide - 1 || 1)))
        const x = centerX + radiusOuter * Math.cos(angle)
        const y = centerY + radiusOuter * Math.sin(angle)
        seats.push({
          id: `seat-${seatNum}`,
          x,
          y,
          label: `R${i + 1}`
        })
        seatNum++
      }

      // Fila inferior (conecta los arcos)
      const remaining = count - seatNum + 1
      for (let i = 0; i < remaining; i++) {
        const xRatio = i / (remaining - 1 || 1)
        const x = 80 + (240 * xRatio)
        const y = 250
        seats.push({
          id: `seat-${seatNum}`,
          x,
          y,
          label: `B${i + 1}`
        })
        seatNum++
      }

      return seats.slice(0, count)
    }

    function updatePreview() {
      if (selectedShape.value === 'rectangular') {
        previewSeats.value = generateRectangularSeats()
      } else if (selectedShape.value === 'semicircular') {
        previewSeats.value = generateSemicircularSeats()
      } else if (selectedShape.value === 'horseshoe') {
        previewSeats.value = generateHorseshoeSeats()
      }
    }

    function saveLayout() {
      const seats = previewSeats.value.map(s => ({
        label: s.label,
        x: (s.x - 50) / 300,
        y: (s.y - 20) / 280
      }))
      emit('save', seats)
    }

    onMounted(() => {
      updatePreview()
    })

    return {
      selectedShape,
      layout,
      canvasRef,
      previewSeats,
      totalSeats,
      validLayout,
      updatePreview,
      saveLayout
    }
  }
}
</script>

<style scoped>
.venue-canvas-editor {
  padding: 24px;
  background: var(--color-surface-muted);
  border-radius: 12px;
  max-width: 700px;
  margin: 0 auto;
}

.editor-header {
  margin-bottom: 24px;
  text-align: center;
}

.editor-header h3 {
  margin: 0 0 8px 0;
  color: var(--color-heading);
  font-size: 1.3rem;
}

.editor-header p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.9rem;
}

.layout-selector {
  margin-bottom: 20px;
}

.selector-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selector-group label {
  font-weight: 600;
  color: var(--color-text-secondary);
  min-width: 120px;
}

.shape-select {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 0.95rem;
  background: var(--color-surface);
  cursor: pointer;
}

.config-section {
  background: var(--color-surface);
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 20px;
  border: 1px solid var(--color-border-subtle);
}

.config-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.config-row:last-child {
  margin-bottom: 0;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.input-group label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.input-field {
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 0.9rem;
}

.input-field:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
  border-color: var(--color-primary);
}

.info-text {
  padding: 8px;
  background: var(--color-info-soft);
  color: var(--color-info);
  font-size: 0.85rem;
  border-radius: 4px;
  font-style: italic;
}

.preview-section {
  margin-bottom: 20px;
}

.preview-section h4 {
  margin: 0 0 12px 0;
  color: var(--color-heading);
}

.canvas-container {
  background: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 4 / 3;
}

.preview-canvas {
  width: 100%;
  height: 100%;
}

.background {
  fill: var(--color-surface-muted);
}

.stage-rect {
  stroke: var(--color-primary);
  stroke-width: 2;
}

.stage-text {
  font-family: Arial, sans-serif;
}

.seat-circle {
  fill: var(--color-primary);
  stroke: #312e81;
  stroke-width: 0.5;
}

.seat-circle:hover {
  fill: #6366f1;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
