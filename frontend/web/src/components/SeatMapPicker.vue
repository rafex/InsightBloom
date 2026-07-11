<template lang="pug">
.seat-map-picker
  .map-wrap(ref="mapWrap" @click="onMapClick")
    img(v-if="imageUrl" :src="imageUrl" alt="Mapa del recinto")
    .seat-marker(
      v-for="seat in seats"
      :key="seat.uuid"
      :class="{ occupied: seat.occupied, selected: seat.uuid === modelValue }"
      :style="{ left: `${seat.x * 100}%`, top: `${seat.y * 100}%` }"
      :title="seat.label"
      @click.stop="selectSeat(seat)"
    ) {{ seat.label }}
</template>

<script lang="ts">
import { ref } from 'vue'
import type { VenueSeat } from '@/services/api/types'

export default {
  name: 'SeatMapPicker',
  props: {
    imageUrl: { type: String, default: '' },
    seats: { type: Array as () => VenueSeat[], default: () => [] },
    modelValue: { type: String, default: null },
    editable: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'add-seat'],
  setup(props: { editable?: boolean }, { emit }: { emit: (event: string, ...args: any[]) => void }) {
    const mapWrap = ref<HTMLElement | null>(null)

    function selectSeat(seat: VenueSeat) {
      if (props.editable || seat.occupied) return
      emit('update:modelValue', seat.uuid)
    }

    function onMapClick(e: MouseEvent) {
      if (!props.editable || !mapWrap.value) return
      const rect = mapWrap.value.getBoundingClientRect()
      const x = (e.clientX - rect.left) / rect.width
      const y = (e.clientY - rect.top) / rect.height
      emit('add-seat', { x, y })
    }

    return { mapWrap, selectSeat, onMapClick }
  }
}
</script>

<style scoped>
.seat-map-picker { width: 100%; }
.map-wrap { position: relative; width: 100%; border-radius: 12px; overflow: hidden; background: #f3f4f6; }
.map-wrap img { width: 100%; display: block; }
.seat-marker {
  position: absolute; transform: translate(-50%, -50%);
  width: 28px; height: 28px; border-radius: 50%;
  background: #4f46e5; color: #fff; font-size: 0.65rem; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; border: 2px solid #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.25);
}
.seat-marker.occupied { background: #9ca3af; cursor: not-allowed; }
.seat-marker.selected { background: #16a34a; }
</style>
