<template lang="pug">
.seatmap-canvas-picker(:class="{ editing: editorMode }")
  .editor-tools(v-if="editorMode")
    .tool-tabs(role="tablist" aria-label="Modo de diseño del mapa")
      button(:class="{ active: designMode === 'template' }" @click="designMode = 'template'") Plantillas
      button(:class="{ active: designMode === 'rows' }" @click="designMode = 'rows'") Secciones y filas
      button(:class="{ active: designMode === 'free' }" @click="designMode = 'free'") Dibujo libre
    .tool-controls(v-if="designMode === 'template' || designMode === 'rows'")
      label(for="seatmap-shape") Distribución
      select#seatmap-shape(v-model="template" @change="generateTemplate")
        option(value="rectangular") Rectangular
        option(value="semicircular") Semicircular
        option(value="horseshoe") Herradura
      label(v-if="designMode === 'rows'" for="seatmap-rows") Filas
      input#seatmap-rows(v-if="designMode === 'rows'" v-model.number="rowCount" type="number" min="1" max="40" @change="generateTemplate")
      label(v-if="designMode === 'rows'" for="seatmap-columns") Asientos por fila
      input#seatmap-columns(v-if="designMode === 'rows'" v-model.number="seatCount" type="number" min="1" max="60" @change="generateTemplate")
      BaseButton(v-if="designMode === 'template'" size="sm" @click="generateTemplate") Aplicar plantilla
    .tool-controls(v-else)
      span.field-hint {{ drawingPolygon ? `Marca puntos del contorno (${draftPoints.length});` : 'Arrastra asientos para ajustar su posición.' }}
      BaseButton(v-if="!drawingPolygon" size="sm" variant="secondary" @click="startPolygon") Dibujar sección
      template(v-else)
        BaseButton(size="sm" @click="finishPolygon" :disabled="draftPoints.length < 3") Cerrar sección
        BaseButton(size="sm" variant="secondary" @click="cancelPolygon") Cancelar
      BaseButton(size="sm" variant="secondary" @click="addAisle") Añadir pasillo
      label(v-if="selectedSeatKey" for="seat-rotation") Rotar asiento
      input#seat-rotation(v-if="selectedSeatKey" type="range" min="-180" max="180" step="5" :value="seatRotations[selectedSeatKey] || 0" @input="rotateSelectedSeat")
      label(v-if="selectedSectionId" for="section-rotation") Rotar sección
      input#section-rotation(v-if="selectedSectionId" type="range" min="-180" max="180" step="5" :value="selectedSection?.rotation || 0" @input="rotateSelectedSection")
  .canvas-shell(:class="{ 'drawing-polygon': drawingPolygon, 'library-active': libraryReady && !editorMode }")
    .library-renderer(ref="libraryRoot" :aria-hidden="!libraryReady || editorMode")
    svg.seatmap-svg(ref="svgRoot" viewBox="0 0 1000 600" role="img" :aria-label="editorMode ? 'Editor del plano de asientos' : 'Mapa interactivo de asientos'" @click="onCanvasClick" @pointermove="moveSeat" @pointerup="finishSeatDrag" @pointercancel="finishSeatDrag")
      rect.canvas-bg(x="0" y="0" width="1000" height="600")
      image(v-if="imageUrl" :href="imageUrl" x="0" y="0" width="1000" height="600" preserveAspectRatio="none" opacity=".28")
      polygon(v-for="section in sections" :key="section.id" :points="polygonPoints(section.points)" :transform="sectionTransform(section)" :class="['section-shape', { aisle: section.aisle, selected: section.id === selectedSectionId }]" :aria-label="section.title" @click.stop="selectSection(section.id)")
      text.section-label(v-for="section in sections" :key="`${section.id}-label`" :x="section.x" :y="section.y" :transform="sectionTransform(section)") {{ section.title }}
      polyline(v-if="drawingPolygon && draftPoints.length" :points="polygonPoints(draftPoints)" class="draft-polygon")
      g(v-for="seat in draftSeats" :key="seat.uuid || seat.localId || seat.label" :transform="`translate(${seat.x * 1000} ${seat.y * 600}) rotate(${seatRotations[seatKey(seat)] || 0})`" :class="['seat-node', { occupied: seat.occupied, selected: seat.uuid === modelValue || seatKey(seat) === selectedSeatKey, draggable: editorMode }]" :tabindex="seat.occupied && !editorMode ? -1 : 0" role="button" :aria-label="`${seat.label}${seat.occupied ? ', ocupado' : ''}`" @click.stop="selectSeat(seat)" @keydown.enter.prevent="selectSeat(seat)" @keydown.space.prevent="selectSeat(seat)" @pointerdown.stop="startSeatDrag($event, seat)")
        circle(r="15")
        text(y="4") {{ seat.label }}
    p.visually-hidden(v-if="!editorMode" aria-live="polite") {{ liveMessage }}
  p.field-hint(v-if="!editorMode") Toca o selecciona con teclado un asiento disponible. Los asientos ocupados aparecen deshabilitados.
</template>

<script lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { SeatMapCanvas } from '@alisaitteke/seatmap-canvas'
import '@alisaitteke/seatmap-canvas/dist/seatmap.canvas.css'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { VenueSeat } from '@/services/api/types'

interface EditableSeat extends VenueSeat { localId?: string }
interface SectionShape { id: string, title: string, aisle?: boolean, points: Array<{ x: number, y: number }>, x: number, y: number, rotation: number }

export default {
  name: 'SeatMapCanvasPicker',
  components: { BaseButton },
  props: {
    seats: { type: Array as () => EditableSeat[], default: () => [] },
    layoutJson: { type: String, default: '{}' },
    imageUrl: { type: String, default: '' },
    modelValue: { type: String, default: '' },
    editorMode: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'seats-change', 'layout-change'],
  setup(props: { seats: EditableSeat[], layoutJson: string, imageUrl: string, modelValue: string, editorMode: boolean }, { emit }: { emit: (event: string, ...args: any[]) => void }) {
    const libraryRoot = ref<HTMLElement | null>(null)
    const svgRoot = ref<SVGSVGElement | null>(null)
    const designMode = ref<'template' | 'rows' | 'free'>('template')
    const template = ref<'rectangular' | 'semicircular' | 'horseshoe'>('rectangular')
    const rowCount = ref(6)
    const seatCount = ref(12)
    const draftSeats = ref<EditableSeat[]>([...props.seats])
    const sections = ref<SectionShape[]>([])
    const draftPoints = ref<Array<{x:number,y:number}>>([])
    const drawingPolygon = ref(false)
    const liveMessage = ref('')
    const libraryReady = ref(false)
    const seatRotations = ref<Record<string, number>>({})
    const selectedSeatKey = ref('')
    const selectedSectionId = ref('')
    const selectedSection = computed(() => sections.value.find(section => section.id === selectedSectionId.value) || null)
    let seatMap: SeatMapCanvas | null = null
    let dragSeatId: string | null = null
    let dragPointerId: number | null = null
    let localSequence = 0

    function alphabetLabel(index: number): string {
      let value = Math.max(1, index)
      let label = ''
      while (value > 0) {
        value -= 1
        label = String.fromCharCode(65 + value % 26) + label
        value = Math.floor(value / 26)
      }
      return label
    }

    function loadLayout(value: string) {
      try {
        const parsed = JSON.parse(value || '{}')
        sections.value = Array.isArray(parsed.sections) ? parsed.sections : []
        seatRotations.value = parsed.seatRotations && typeof parsed.seatRotations === 'object' ? parsed.seatRotations : {}
        if (Array.isArray(parsed.seats) && !props.seats.length) draftSeats.value = parsed.seats
      } catch { sections.value = [] }
    }

    function persist() {
      emit('seats-change', draftSeats.value.map(({ uuid, label, x, y }) => ({ uuid: uuid || null, label, x, y })))
      emit('layout-change', JSON.stringify({ version: 1, sections: sections.value, seatRotations: seatRotations.value }))
      renderLibrary()
    }

    function generateTemplate() {
      if (!props.editorMode) return
      const out: EditableSeat[] = []
      const rows = Math.max(1, Math.min(40, rowCount.value || 1))
      const cols = Math.max(1, Math.min(60, seatCount.value || 1))
      if (designMode.value === 'rows' || template.value === 'rectangular') {
        for (let row = 0; row < rows; row++) for (let col = 0; col < cols; col++) {
          out.push({ uuid: null as any, label: `${alphabetLabel(row + 1)}${col + 1}`, x: .18 + col * (.64 / Math.max(cols - 1, 1)), y: .24 + row * (.67 / Math.max(rows - 1, 1)), occupied: false, localId: `new-${++localSequence}` })
        }
      } else {
        const count = Math.max(12, Math.min(500, rows * cols))
        for (let index = 0; index < count; index++) {
          const ratio = index / count
          let x: number, y: number
          if (template.value === 'semicircular') {
            const angle = Math.PI + ratio * Math.PI
            x = .5 + .39 * Math.cos(angle); y = .82 + .5 * Math.sin(angle)
          } else {
            const third = Math.floor(ratio * 3), offset = (ratio * 3) % 1
            if (third === 0) { x = .18; y = .2 + .62 * offset }
            else if (third === 1) { x = .18 + .64 * offset; y = .82 }
            else { x = .82; y = .82 - .62 * offset }
          }
          out.push({ uuid: null as any, label: `${template.value === 'semicircular' ? 'S' : 'H'}${index + 1}`, x, y, occupied: false, localId: `new-${++localSequence}` })
        }
      }
      draftSeats.value = out
      sections.value = [{ id: 'section-main', title: 'Sección A', x: 500, y: 150, rotation: 0, points: [{x:120,y:120},{x:880,y:120},{x:880,y:510},{x:120,y:510}] }]
      persist()
    }

    function pointFromEvent(event: PointerEvent | MouseEvent) {
      const bounds = svgRoot.value?.getBoundingClientRect()
      if (!bounds) return { x: .5, y: .5 }
      return { x: Math.min(1, Math.max(0, (event.clientX - bounds.left) / bounds.width)), y: Math.min(1, Math.max(0, (event.clientY - bounds.top) / bounds.height)) }
    }
    function onCanvasClick(event: MouseEvent) {
      if (!props.editorMode) return
      const p = pointFromEvent(event)
      const x = Math.round(p.x * 1000), y = Math.round(p.y * 600)
      if (drawingPolygon.value) draftPoints.value.push({ x, y })
      else if (designMode.value === 'free') {
        draftSeats.value.push({ uuid: null as any, label: `A${draftSeats.value.length + 1}`, x: p.x, y: p.y, occupied: false, localId: `new-${++localSequence}` })
        persist()
      }
    }
    function startPolygon() { drawingPolygon.value = true; draftPoints.value = [] }
    function finishPolygon() {
      if (draftPoints.value.length < 3) return
      const index = sections.value.length + 1
      sections.value.push({ id: `section-${index}`, title: `Sección ${alphabetLabel(index)}`, points: [...draftPoints.value], x: draftPoints.value[0].x, y: draftPoints.value[0].y, rotation: 0 })
      cancelPolygon(); persist()
    }
    function cancelPolygon() { drawingPolygon.value = false; draftPoints.value = [] }
    function addAisle() {
      const left = 470 + sections.value.filter(section => section.aisle).length * 48
      sections.value.push({ id: `aisle-${sections.value.length + 1}`, title: 'Pasillo', aisle: true, points: [{x:left,y:100},{x:left+28,y:100},{x:left+28,y:520},{x:left,y:520}], x:left, y:100, rotation: 0 })
      persist()
    }
    function selectSeat(seat: EditableSeat) {
      if (props.editorMode) { selectedSeatKey.value = seatKey(seat); return }
      if (seat.occupied || !seat.uuid) return
      emit('update:modelValue', seat.uuid); liveMessage.value = `Asiento ${seat.label} seleccionado.`
    }
    function seatKey(seat: EditableSeat) { return seat.label }
    function selectSection(id: string) { if (props.editorMode) selectedSectionId.value = id }
    function rotateSelectedSeat(event: Event) {
      if (!selectedSeatKey.value) return
      seatRotations.value = { ...seatRotations.value, [selectedSeatKey.value]: Number((event.target as HTMLInputElement).value) }
      persist()
    }
    function rotateSelectedSection(event: Event) {
      const section = selectedSection.value
      if (!section) return
      section.rotation = Number((event.target as HTMLInputElement).value)
      persist()
    }
    function startSeatDrag(event: PointerEvent, seat: EditableSeat) {
      if (!props.editorMode) return
      dragSeatId = seat.localId || seat.uuid || seat.label; dragPointerId = event.pointerId
      ;(event.currentTarget as SVGElement).setPointerCapture?.(event.pointerId)
    }
    function moveSeat(event: PointerEvent) {
      if (!dragSeatId || dragPointerId !== event.pointerId) return
      const p = pointFromEvent(event)
      const seat = draftSeats.value.find(item => (item.localId || item.uuid || item.label) === dragSeatId)
      if (seat) { seat.x = p.x; seat.y = p.y }
    }
    function finishSeatDrag(event?: PointerEvent) {
      if (!dragSeatId) return
      if (event && dragPointerId !== event.pointerId) return
      dragSeatId = null; dragPointerId = null; persist()
    }
    function polygonPoints(points: Array<{x:number,y:number}>) { return points.map(point => `${point.x},${point.y}`).join(' ') }
    function sectionTransform(section: SectionShape) { return `rotate(${section.rotation || 0} ${section.x} ${section.y})` }
    function renderLibrary() {
      if (!libraryRoot.value) return
      libraryReady.value = false
      if (props.editorMode || draftSeats.value.length < 3) return
      try {
        libraryRoot.value.replaceChildren()
        seatMap = new SeatMapCanvas(libraryRoot.value, { resizable: true, legend: false, style: { seat: { shape: 'circle' } } } as any)
        const block = {
          id: 'insightbloom-venue', title: 'Recinto', x: 0, y: 0, width: 1000, height: 600,
          color: '#eef2ff', labels: [], seats: draftSeats.value.map(seat => ({
            id: seat.uuid || seat.localId || seat.label, x: seat.x * 1000, y: seat.y * 600,
            title: seat.label, salable: !seat.occupied, selected: seat.uuid === props.modelValue,
            color: seat.occupied ? '#64748b' : '#4f46e5', tags: [], custom_data: {}
          }))
        }
        ;(seatMap as any).data.addBulkBlock([block])
        seatMap.addEventListener('seat_click', (clicked: any) => {
          const seat = clicked?.item || clicked
          const uuid = seat?.id == null ? '' : String(seat.id)
          if (!uuid || seat?.salable === false) return
          emit('update:modelValue', uuid)
          liveMessage.value = `Asiento ${seat.title || ''} seleccionado.`
        })
        libraryReady.value = Boolean(libraryRoot.value.querySelector('svg'))
      } catch { seatMap = null; libraryReady.value = false }
    }

    loadLayout(props.layoutJson)
    watch(() => props.seats, value => { draftSeats.value = [...value]; renderLibrary() }, { deep: true })
    watch(() => props.layoutJson, loadLayout)
    watch(() => props.modelValue, renderLibrary)
    onMounted(() => { if (props.editorMode && !draftSeats.value.length) generateTemplate(); else renderLibrary() })
    onBeforeUnmount(() => { seatMap = null; libraryRoot.value?.replaceChildren() })
    return { libraryRoot, svgRoot, designMode, template, rowCount, seatCount, draftSeats, sections, draftPoints, drawingPolygon, liveMessage, libraryReady,
      seatRotations, selectedSeatKey, selectedSectionId, selectedSection,
      generateTemplate, pointFromEvent, onCanvasClick, startPolygon, finishPolygon, cancelPolygon, addAisle, selectSeat,
      startSeatDrag, moveSeat, finishSeatDrag, polygonPoints, sectionTransform, seatKey, selectSection, rotateSelectedSeat, rotateSelectedSection }
  }
}
</script>

<style scoped>
.seatmap-canvas-picker { width: 100%; min-width: 0; }
.editor-tools { margin-bottom: 12px; }
.tool-tabs,.tool-controls { display:flex; flex-wrap:wrap; align-items:center; gap:8px; margin-bottom:10px; }
.tool-tabs button { padding:8px 12px; border:1px solid var(--color-border); border-radius:8px; background:var(--color-surface); color:var(--color-text); cursor:pointer; }
.tool-tabs button.active { border-color:var(--color-primary); color:var(--color-primary); font-weight:700; }
.tool-controls input,.tool-controls select { min-width:80px; padding:7px; border:1px solid var(--color-border); border-radius:6px; background:var(--color-surface); color:var(--color-text); }
.canvas-shell { position:relative; width:100%; aspect-ratio:5/3; overflow:hidden; border:1px solid var(--color-border-subtle); border-radius:12px; background:var(--color-surface); }
.library-renderer { position:absolute; inset:0; width:100%; height:100%; pointer-events:none; }
.library-renderer :deep(svg) { width:100%; height:100%; }
.library-active .library-renderer { pointer-events:auto; }
.library-active .seatmap-svg { pointer-events:none; }
.library-active .seat-node { opacity:0; }
.library-active .canvas-bg { fill:transparent; }
.seatmap-svg { position:absolute; inset:0; width:100%; height:100%; touch-action:none; }
.canvas-bg { fill:var(--color-surface-muted); }
.section-shape { fill:color-mix(in srgb,var(--color-primary) 12%,transparent); stroke:var(--color-primary); stroke-width:3; }
.section-shape.aisle { fill:color-mix(in srgb,var(--color-text-muted) 18%,transparent); stroke:var(--color-text-muted); stroke-dasharray:8 5; }
.section-shape.selected { stroke-width:7; }
.section-label { fill:var(--color-heading); font-size:20px; font-weight:700; text-anchor:middle; }
.draft-polygon { fill:color-mix(in srgb,var(--color-primary) 18%,transparent); stroke:var(--color-primary); stroke-width:4; stroke-dasharray:10 6; }
.seat-node { cursor:pointer; outline:none; }
.seat-node circle { fill:var(--color-primary); stroke:white; stroke-width:3; }
.seat-node text { fill:var(--color-text-inverse); font-size:11px; font-weight:700; text-anchor:middle; pointer-events:none; }
.seat-node.occupied circle { fill:var(--color-text-muted); }
.seat-node.selected circle { fill:var(--color-success); }
.seat-node:focus-visible circle { stroke:var(--color-focus); stroke-width:6; }
.seat-node.draggable { cursor:move; }
.drawing-polygon { cursor:crosshair; }
.visually-hidden { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
</style>
