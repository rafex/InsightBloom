<template lang="pug">
.conf-map-wrap
  div(ref="mapRef" style="width:100%;height:100%")
</template>

<script>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

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
  name: 'ConferenceMap',
  props: {
    latitude:  { type: Number, required: true },
    longitude: { type: Number, required: true },
    label:     { type: String, default: '' }
  },
  setup(props) {
    const mapRef = ref(null)
    let map    = null
    let marker = null

    const pinIcon = L.divIcon({
      className: '',
      html: `<div class="cm-pin">
               <div class="cm-pin-pulse"></div>
               <div class="cm-pin-logo">${BLOOM_SVG}</div>
               <div class="cm-pin-needle"></div>
             </div>`,
      iconSize:   [44, 58],
      iconAnchor: [22, 58],
      popupAnchor: [0, -60]
    })

    onMounted(() => {
      map = L.map(mapRef.value, { zoomControl: true, scrollWheelZoom: false })
        .setView([props.latitude, props.longitude], 12)

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19
      }).addTo(map)

      marker = L.marker([props.latitude, props.longitude], { icon: pinIcon }).addTo(map)
      if (props.label) marker.bindPopup(`<b>${props.label}</b>`).openPopup()
    })

    watch(() => [props.latitude, props.longitude], ([lat, lng]) => {
      if (!map || lat == null || lng == null) return
      map.flyTo([lat, lng], 12, { duration: 1 })
      marker?.setLatLng([lat, lng])
    })

    onUnmounted(() => { if (map) { map.remove(); map = null } })

    return { mapRef }
  }
}
</script>

<style scoped>
.conf-map-wrap {
  width: 100%;
  height: 320px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.12);
}
</style>

<style>
/* ── ConferenceMap pin ─────────────────────────────── */
.cm-pin {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 44px;
}
.cm-pin-pulse {
  position: absolute;
  top: 0; left: 0;
  width: 44px; height: 44px;
  border: 2px solid #4f46e5;
  border-radius: 50%;
  animation: cm-pulse 2s ease-out infinite;
}
.cm-pin-logo {
  width: 44px; height: 44px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 10px rgba(79,70,229,0.45);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cm-pin-logo svg { width: 38px; height: 38px; }
.cm-pin-needle {
  width: 0; height: 0;
  border-left: 7px solid transparent;
  border-right: 7px solid transparent;
  border-top: 14px solid #4f46e5;
  margin-top: -1px;
  filter: drop-shadow(0 2px 2px rgba(0,0,0,0.25));
}
@keyframes cm-pulse {
  0%   { transform: scale(0.7); opacity: 0.7; }
  100% { transform: scale(1.5); opacity: 0; }
}
</style>
