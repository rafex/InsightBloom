<template lang="pug">
.intro-overlay(:class="{ 'intro-out': leaving }")
  div#intro-map(ref="mapRef")

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

const MX_LAT  = 23.6345
const MX_LNG  = -102.5528
const MX_ZOOM = 5

const BLOOM_SVG = `<svg viewBox='0 0 72 72' xmlns='http://www.w3.org/2000/svg' fill='none'>
  <g transform='translate(8,8)'>
    <circle cx='24' cy='6'  r='6'   fill='#c7d2fe' opacity='0.9'/>
    <circle cx='42' cy='6'  r='5'   fill='#a5b4fc' opacity='0.9'/>
    <circle cx='6'  cy='20' r='5'   fill='#a5b4fc' opacity='0.9'/>
    <circle cx='54' cy='18' r='7'   fill='#818cf8' opacity='0.9'/>
    <circle cx='6'  cy='36' r='7'   fill='#818cf8' opacity='0.9'/>
    <circle cx='54' cy='36' r='5'   fill='#a5b4fc' opacity='0.9'/>
    <circle cx='14' cy='48' r='6'   fill='#a5b4fc' opacity='0.9'/>
    <circle cx='46' cy='48' r='6'   fill='#c7d2fe' opacity='0.9'/>
    <circle cx='30' cy='54' r='5'   fill='#c7d2fe' opacity='0.9'/>
    <circle cx='18' cy='14' r='8'   fill='#818cf8' opacity='0.95'/>
    <circle cx='42' cy='16' r='9'   fill='#6366f1' opacity='0.95'/>
    <circle cx='12' cy='36' r='9'   fill='#6366f1' opacity='0.95'/>
    <circle cx='48' cy='38' r='8'   fill='#818cf8' opacity='0.95'/>
    <circle cx='30' cy='46' r='9'   fill='#6366f1' opacity='0.95'/>
    <circle cx='30' cy='28' r='16'  fill='#4f46e5'/>
    <circle cx='25' cy='23' r='5'   fill='#818cf8' opacity='0.35'/>
    <circle cx='35' cy='22' r='2.5' fill='white'   opacity='0.7'/>
  </g>
</svg>`

export default {
  name: 'ConferenceIntroMap',
  props: {
    latitude:  { type: Number, required: true },
    longitude: { type: Number, required: true },
    label:     { type: String, default: '' }
  },
  emits: ['enter'],
  setup(props, { emit }) {
    const mapRef      = ref(null)
    const markerReady = ref(false)
    const leaving     = ref(false)

    let map    = null
    let marker = null

    const pinIcon = L.divIcon({
      className: '',
      html: `<div class="intro-logo-pin">
               <div class="ilp-ring ilp-ring1"></div>
               <div class="ilp-ring ilp-ring2"></div>
               <div class="ilp-logo">${BLOOM_SVG}</div>
               <div class="ilp-needle"></div>
               <div class="ilp-label">¡Clic para entrar!</div>
             </div>`,
      iconSize:    [72, 102],
      iconAnchor:  [36, 96],
      popupAnchor: [0, -100]
    })

    function enter() {
      leaving.value = true
      setTimeout(() => emit('enter'), 600)
    }

    onMounted(() => {
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

      // 2 s pause on Mexico → 3.5 s flyTo → show marker
      setTimeout(() => {
        map.flyTo([props.latitude, props.longitude], 13, {
          animate: true,
          duration: 3.5,
          easeLinearity: 0.25
        })

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
.intro-overlay.intro-out { opacity: 0; pointer-events: none; }

#intro-map { width: 100%; height: 100%; }

.intro-top {
  position: absolute;
  top: 0; left: 0; right: 0;
  z-index: 10000;
  background: linear-gradient(to bottom, rgba(15,23,42,0.85) 0%, transparent 100%);
  padding: 24px 32px 56px;
  pointer-events: none;
}
.intro-name {
  font-size: 1.7rem;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0,0,0,0.6);
}
.intro-coords {
  margin-top: 4px;
  font-size: 0.85rem;
  font-family: monospace;
  color: #a5b4fc;
}

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
  background: rgba(15,23,42,0.85);
  color: #fff;
  padding: 10px 22px;
  border-radius: 24px;
  font-size: 0.95rem;
  font-weight: 500;
  border: 1px solid rgba(165,180,252,0.4);
  backdrop-filter: blur(8px);
  white-space: nowrap;
}
.hint-arrow {
  margin-top: 8px;
  font-size: 1.3rem;
  color: #4f46e5;
  animation: bounce-arr 1s ease-in-out infinite;
}

.hint-fade-enter-active, .hint-fade-leave-active { transition: opacity 0.5s; }
.hint-fade-enter-from, .hint-fade-leave-to { opacity: 0; }

@keyframes bounce-arr {
  0%, 100% { transform: translateY(0); }
  50%       { transform: translateY(7px); }
}
</style>

<style>
/* ── Intro map pin (global — inside Leaflet divIcon) ── */
.intro-logo-pin {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 72px;
  cursor: pointer;
}

.ilp-ring {
  position: absolute;
  border-radius: 50%;
  border: 2.5px solid #4f46e5;
  top: 0; left: 0;
  width: 72px; height: 72px;
  animation: ilp-pulse 2.2s ease-out infinite;
}
.ilp-ring2 { animation-delay: 0.9s; }

.ilp-logo {
  width: 72px; height: 72px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 4px 20px rgba(79,70,229,0.55), 0 0 0 3px rgba(79,70,229,0.2);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
}
.intro-logo-pin:hover .ilp-logo {
  transform: scale(1.08);
  box-shadow: 0 6px 28px rgba(79,70,229,0.7), 0 0 0 4px rgba(79,70,229,0.3);
}
.ilp-logo svg { width: 62px; height: 62px; }

.ilp-needle {
  width: 0; height: 0;
  border-left: 11px solid transparent;
  border-right: 11px solid transparent;
  border-top: 22px solid #4f46e5;
  margin-top: -2px;
  filter: drop-shadow(0 3px 4px rgba(0,0,0,0.3));
}

.ilp-label {
  margin-top: 6px;
  white-space: nowrap;
  font-size: 0.72rem;
  font-weight: 700;
  color: #fff;
  background: rgba(79,70,229,0.9);
  padding: 3px 10px;
  border-radius: 10px;
  letter-spacing: 0.01em;
  pointer-events: none;
}

@keyframes ilp-pulse {
  0%   { transform: scale(0.6); opacity: 0.7; }
  100% { transform: scale(1.55); opacity: 0; }
}
</style>
