<template lang="pug">
.conf-map-wrap
  #conf-leaflet-map(ref="mapRef")
</template>

<script>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

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
      html: `<div class="lf-pin"><div class="lf-pin-pulse"></div><div class="lf-pin-dot"></div></div>`,
      iconSize: [36, 36],
      iconAnchor: [18, 18],
      popupAnchor: [0, -20]
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
#conf-leaflet-map { width: 100%; height: 100%; }
</style>

<style>
/* Global styles for the divIcon — must be unscoped */
.lf-pin {
  position: relative;
  width: 36px;
  height: 36px;
}
.lf-pin-pulse {
  position: absolute;
  inset: 0;
  border: 2.5px solid #4f46e5;
  border-radius: 50%;
  animation: lf-pulse 1.8s ease-out infinite;
}
.lf-pin-dot {
  position: absolute;
  width: 14px;
  height: 14px;
  background: #4f46e5;
  border: 2px solid #fff;
  border-radius: 50%;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 2px 6px rgba(79,70,229,0.5);
}
@keyframes lf-pulse {
  0%   { transform: scale(0.5); opacity: 0.8; }
  100% { transform: scale(1.6); opacity: 0; }
}
</style>
