<template lang="pug">
.venue-map-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  h2 Mapa de asientos

  LoadingState(v-if="loading" message="Cargando mapa de asientos…")
  template(v-else)
    .tabs-selector
      button.tab-btn(:class="{active: activeTab === 'image'}" @click="activeTab = 'image'") 📷 Subir imagen
      button.tab-btn(:class="{active: activeTab === 'ai'}" @click="activeTab = 'ai'") ✨ Generar con IA
      button.tab-btn(:class="{active: activeTab === 'canvas'}" @click="activeTab = 'canvas'") 📐 Editor manual

    .form-group(v-show="activeTab === 'image'")
      label Imagen del recinto
      p.field-hint Sube una foto o plano del lugar; luego haz clic sobre ella para colocar cada asiento.
      input(type="file" accept="image/*" @change="onImageSelected")
      BaseButton(variant="secondary" v-if="imageBase64" type="button" :disabled="savingMap" @click="saveMap") Guardar imagen

    .form-group(v-show="activeTab === 'ai'")
      label Generar asientos con IA
      p.field-hint Describe el recinto: medidas, distancias, referencias (escenario, pasillos, entrada) y figuras geométricas (filas, semicírculo, herradura). El resultado es una propuesta que puedes editar antes de guardar.
      textarea.ai-description(v-model="aiDescription" rows="3" placeholder="Ej. Salón rectangular de 10x8 metros, 8 filas de 10 asientos con pasillo central, escenario al frente")
      BaseButton(variant="secondary" type="button" @click="generateWithAi" :disabled="generatingAi || !aiDescription.trim()")
        span(v-if="generatingAi") Generando...
        span(v-else) ✨ Generar con IA
      FeedbackMessage(v-if="aiError" :message="aiError" tone="error")

    .form-group(v-show="activeTab === 'canvas'")
      VenueMapCanvasEditor(v-if="showingCanvasEditor" @save="applyCanvasSeats" @cancel="showingCanvasEditor = false")

    template(v-if="imageBase64")
      p.field-hint Haz clic sobre el mapa para agregar un asiento. Clic en un asiento para eliminarlo.
      SeatMapPicker(:image-url="imageBase64" :seats="seats" editable @add-seat="addSeat")

    .seat-list(v-if="seats.length")
      .seat-row(v-for="seat in seats" :key="seat.uuid || seat.label")
        input.seat-label(v-model="seat.label" type="text" placeholder="Etiqueta, ej. A1")
        BaseButton(variant="danger" size="sm" type="button" @click="removeSeat(seat)") Quitar
      BaseButton(:loading="savingSeats" @click="saveSeats") Guardar asientos
    FeedbackMessage(v-if="seatsSaved" message="Asientos guardados." tone="success")
    FeedbackMessage(v-if="seatsError" :message="seatsError" tone="error")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import SeatMapPicker from '@/components/SeatMapPicker.vue'
import VenueMapCanvasEditor from '@/components/VenueMapCanvasEditor.vue'
import { getConference, setVenueMap, getConferenceSeatMap, defineVenueSeats, generateSeatLayout } from '@/services/api/usersApi'
import type { VenueSeat } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

interface EditableSeat { uuid: string | null, label: string, x: number, y: number, occupied: boolean }

export default {
  name: 'VenueMapEditorPage',
  components: { DashboardBreadcrumb, SeatMapPicker, VenueMapCanvasEditor, BaseButton, FeedbackMessage, LoadingState },
  props: { conferenceId: { type: String, default: '' } },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const loading = ref(true)
    const imageBase64 = ref('')
    const seats = ref<EditableSeat[]>([])
    const savingMap = ref(false)
    const savingSeats = ref(false)
    const seatsSaved = ref(false)
    const seatsError = ref('')
    const aiDescription = ref('')
    const generatingAi = ref(false)
    const aiError = ref('')
    const activeTab = ref('image')
    const showingCanvasEditor = ref(true)
    const conferenceName = ref('')
    let seatCounter = 0

    onMounted(async () => {
      try {
        const [conf, seatMap] = await Promise.all([
          getConference(props.conferenceId as string, auth.state.token as string),
          getConferenceSeatMap(props.conferenceId as string, auth.state.token as string)
        ])
        imageBase64.value = conf.venueMapBase64 || ''
        seats.value = seatMap.map((s: VenueSeat) => ({ uuid: s.uuid, label: s.label, x: s.x, y: s.y, occupied: s.occupied }))
        conferenceName.value = conf.name || ''
      } finally {
        loading.value = false
      }
    })

    function onImageSelected(e: Event) {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = () => { imageBase64.value = reader.result as string }
      reader.readAsDataURL(file)
    }

    async function saveMap() {
      savingMap.value = true
      try {
        await setVenueMap(props.conferenceId as string, imageBase64.value, auth.state.token as string)
      } finally {
        savingMap.value = false
      }
    }

    function addSeat({ x, y }: { x: number, y: number }) {
      seatCounter += 1
      seats.value.push({ uuid: null, label: `Asiento ${seatCounter}`, x, y, occupied: false })
    }

    function removeSeat(target: EditableSeat) {
      seats.value = seats.value.filter((s) => s !== target)
    }

    async function generateWithAi() {
      generatingAi.value = true; aiError.value = ''
      try {
        const generated = await generateSeatLayout(props.conferenceId as string, aiDescription.value, auth.state.token as string)
        seats.value = generated.map((s) => ({ uuid: s.uuid, label: s.label, x: s.x, y: s.y, occupied: false }))
        seatsSaved.value = false
      } catch (e: any) {
        aiError.value = e.response?.status === 503
          ? 'La generación por IA no está configurada en este despliegue.'
          : (e.response?.data?.error?.message || 'No se pudo generar el layout con IA')
      } finally {
        generatingAi.value = false
      }
    }

    async function saveSeats() {
      savingSeats.value = true; seatsError.value = ''; seatsSaved.value = false
      try {
        const payload = seats.value.map((s) => ({ uuid: s.uuid, label: s.label, x: s.x, y: s.y }))
        const saved = await defineVenueSeats(props.conferenceId as string, payload, auth.state.token as string)
        seats.value = saved.map((s) => ({ uuid: s.uuid, label: s.label, x: s.x, y: s.y, occupied: false }))
        seatsSaved.value = true
      } catch (e: any) {
        seatsError.value = e.response?.data?.error?.message || 'No se pudieron guardar los asientos'
      } finally {
        savingSeats.value = false
      }
    }

    function applyCanvasSeats(canvasSeats: any[]) {
      seats.value = canvasSeats.map((s, idx) => ({
        uuid: null,
        label: s.label || `Asiento ${idx + 1}`,
        x: s.x,
        y: s.y,
        occupied: false
      }))
      showingCanvasEditor.value = true
    }

    const breadcrumbItems = computed(() => [
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conferenceName.value || props.conferenceId || '', loading: loading.value && !conferenceName.value },
      { label: 'Mapa de asientos' }
    ])

    return {
      loading, imageBase64, seats, savingMap, savingSeats, seatsSaved, seatsError,
      aiDescription, generatingAi, aiError, activeTab, showingCanvasEditor,
      onImageSelected, saveMap, addSeat, removeSeat, saveSeats, generateWithAi, applyCanvasSeats, breadcrumbItems
    }
  }
}
</script>

<style scoped>
.venue-map-page { padding: 24px; max-width: 900px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 16px; }
.tabs-selector { display: flex; gap: 8px; margin-bottom: 24px; border-bottom: 1px solid var(--color-border-subtle); }
.tab-btn { padding: 12px 16px; background: none; border: none; border-bottom: 3px solid transparent; color: var(--color-text-muted); font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: all 0.2s ease; }
.tab-btn:hover { color: var(--color-primary); }
.tab-btn.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.form-group { display: flex; flex-direction: column; gap: 8px; margin-bottom: 20px; }
.field-hint { margin: 0; font-size: 0.8rem; }
.ai-description { padding: 8px 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 0.85rem; font-family: inherit; resize: vertical; }
.seat-list { margin: 16px 0; display: flex; flex-direction: column; gap: 6px; }
.seat-row { display: flex; gap: 8px; align-items: center; }
.seat-label { flex: 1; padding: 6px 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 0.85rem; }
</style>
