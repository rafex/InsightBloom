<template lang="pug">
.landing-page
  AppHeader
  main.landing-main
    .landing-hero
      h1.animate__animated.animate__fadeInDown InsightBloom
      p.subtitle Convierte el chat de tu conferencia en señales accionables
    .landing-form.animate__animated.animate__fadeInUp
      h2 Entrar a una conferencia
      .form-group
        input(
          v-model="friendlyId"
          type="text"
          placeholder="ID de la conferencia (ej: ia-2026)"
          @keyup.enter="enter"
        )
        button.btn-primary(@click="enter" :disabled="!friendlyId.trim()") Entrar
      p.hint ¿Organizas la conferencia?
        router-link(to="/login")  Inicia sesión aquí
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
export default {
  name: 'LandingPage',
  components: { AppHeader },
  setup() {
    const friendlyId = ref('')
    const router = useRouter()
    function enter() {
      if (friendlyId.value.trim()) router.push(`/c/${friendlyId.value.trim()}`)
    }
    return { friendlyId, enter }
  }
}
</script>

<style scoped>
.landing-page { min-height: 100vh; background: #f5f3ff; }
.landing-main { display: flex; flex-direction: column; align-items: center; padding: 80px 24px; }
.landing-hero { text-align: center; margin-bottom: 48px; }
h1 { font-size: 3rem; font-weight: 800; color: #1e1b4b; margin: 0 0 12px; }
.subtitle { color: #6b7280; font-size: 1.2rem; }
.landing-form { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 440px; width: 100%; }
h2 { margin: 0 0 24px; color: #1e1b4b; }
.form-group { display: flex; gap: 10px; }
input { flex: 1; padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; outline: none; }
input:focus { border-color: #4f46e5; }
.btn-primary { padding: 10px 20px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.hint { color: #6b7280; font-size: 0.9rem; margin-top: 16px; }
.hint a { color: #4f46e5; }
</style>
