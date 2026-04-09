<template lang="pug">
.intro-overlay(:class="{ 'intro-out': leaving }")
  #intro-map(ref="mapRef")

  .intro-top
    .intro-name {{ label }}
    .intro-coords(v-if="latitude != null") {{ latitude.toFixed(4) }}, {{ longitude.toFixed(4) }}

  transition(name="hint-fade")
    .intro-hint(v-if="markerReady")
      .hint-bubble Haz clic en el marcador para entrar a la conferencia
      .hint-arrow ▼
</template>

<script>
import { ref, onMounted, onUnmounted } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// Mexico geographic center
const MX_LAT  = 23.6345
const MX_LNG  = -102.5528
const MX_ZOOM = 5

export default {
  name: 'ConferenceIntroMap',
  props: {
    latitude:  { type: Number, required: true },
    longitude: { type: Number, required: true },
    label:     { type: String, default: '' }
  },
  emits: ['enter'],
  setup(props, { emit }) {
    const mapRef    = ref(null)
    const markerReady = ref(false)
    const leaving   = ref(false)

    let map    = null
    let marker = null

    const pinIcon = L.divIcon({
      className: '',
      html: `
        <div class="intro-pin">
          <div class="intro-pin-ring ring1"></div>
          <div class="intro-pin-ring ring2"></div>
          <div class="intro-pin-core"></div>
          <div class="intro-pin-label">¡Clic para entrar!</div>
        </div>`,
      iconSize: [80, 80],
      iconAnchor: [40, 40],
      popupAnchor: [0, -44]
    })

    function enter() {
      leaving.value = true
      setTimeout(() => emit('enter'), 600)
    }

    onMounted(() => {
      // Start at Mexico overview
      map = L.map(mapRef.value, {
        zoomControl: false,
        attributionControl: true,
        dragging: false,
        scrollWheelZoom: false,
        doubleClickZoom: false,
        keyboard: false
      }).setView([MX_LAT, MX_LNG], MX_ZOOM)

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19
      }).addTo(map)

      // Phase 1: pause on Mexico overview (2s)
      // Phase 2: animate flyTo conference location (duration ~3s)
      // Phase 3: show clickable marker
      setTimeout(() => {
        map.flyTo([props.latitude, props.longitude], 13, {
          animate: true,
          duration: 3.5,
          easeLinearity: 0.25
        })

        // After flyTo completes + short pause, enable interaction and show marker
        setTimeout(() => {
          map.dragging.enable()
          map.scrollWheelZoom.enable()
          map.doubleClickZoom.enable()

          marker = L.marker([props.latitude, props.longitude], { icon: pinIcon }).addTo(map)
          marker.on('click', enter)
          markerReady.value = true
        }, 4200)
      }, 2000)
    })

    onUnmounted(() => { if (map) { map.remove(); map = null } })

    return { mapRef, markerReady, leaving }
  }
}
</script>

<style scoped>
.intro-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: #0f172a;
  transition: opacity 0.6s ease;
}
.intro-overlay.intro-out {
  opacity: 0;
  pointer-events: none;
}

#intro-map {
  width: 100%;
  height: 100%;
}

/* Top bar with conference name */
.intro-top {
  position: absolute;
  top: 0; left: 0; right: 0;
  z-index: 10000;
  background: linear-gradient(to bottom, rgba(15,23,42,0.85) 0%, transparent 100%);
  padding: 24px 32px 48px;
  pointer-events: none;
}
.intro-name {
  font-size: 1.6rem;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0,0,0,0.6);
  letter-spacing: 0.01em;
}
.intro-coords {
  margin-top: 4px;
  font-size: 0.85rem;
  font-family: monospace;
  color: #a5b4fc;
}

/* Hint text that appears after flyTo */
.intro-hint {
  position: absolute;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10000;
  text-align: center;
  pointer-events: none;
}
.hint-bubble {
  background: rgba(15, 23, 42, 0.85);
  color: #fff;
  padding: 10px 20px;
  border-radius: 24px;
  font-size: 0.95rem;
  font-weight: 500;
  border: 1px solid rgba(165, 180, 252, 0.4);
  backdrop-filter: blur(8px);
  white-space: nowrap;
}
.hint-arrow {
  margin-top: 6px;
  font-size: 1.2rem;
  color: #fbbf24;
  animation: bounce-arrow 1s ease-in-out infinite;
}

.hint-fade-enter-active, .hint-fade-leave-active { transition: opacity 0.5s; }
.hint-fade-enter-from, .hint-fade-leave-to { opacity: 0; }

@keyframes bounce-arrow {
  0%, 100% { transform: translateY(0); }
  50%       { transform: translateY(6px); }
}
</style>

<style>
/* Global — Leaflet divIcon content styles */
.intro-pin {
  position: relative;
  width: 80px;
  height: 80px;
  cursor: pointer;
}
.intro-pin-ring {
  position: absolute;
  border-radius: 50%;
  border: 2.5px solid #fbbf24;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  animation: intro-ring-pulse 2s ease-out infinite;
}
.intro-pin-ring.ring1 { width: 48px; height: 48px; animation-delay: 0s; }
.intro-pin-ring.ring2 { width: 70px; height: 70px; animation-delay: 0.7s; }
.intro-pin-core {
  position: absolute;
  width: 20px; height: 20px;
  background: #fbbf24;
  border: 3px solid #fff;
  border-radius: 50%;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 16px rgba(251,191,36,0.7);
  transition: transform 0.15s;
}
.intro-pin:hover .intro-pin-core {
  transform: translate(-50%, -50%) scale(1.3);
}
.intro-pin-label {
  position: absolute;
  bottom: -28px;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  font-size: 0.75rem;
  font-weight: 700;
  color: #fbbf24;
  background: rgba(15,23,42,0.85);
  padding: 2px 8px;
  border-radius: 8px;
  pointer-events: none;
}
@keyframes intro-ring-pulse {
  0%   { opacity: 0.8; transform: translate(-50%,-50%) scale(0.6); }
  100% { opacity: 0;   transform: translate(-50%,-50%) scale(1); }
}
</style>
