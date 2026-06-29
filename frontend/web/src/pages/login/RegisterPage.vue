<template lang="pug">
.register-page
  AppHeader
  main.register-main
    .register-card
      template(v-if="step === 'form'")
        h2 Crear cuenta
        p.hint Necesitas correo o teléfono (al menos uno) para verificar tu identidad
        .form-group
          label Nombre de usuario
          input(v-model="form.displayName" placeholder="Tu nombre de usuario")
        .form-group
          label Correo electrónico
          input(v-model="form.email" type="email" placeholder="tu@correo.com")
        .form-group
          label Teléfono
          input(v-model="form.phone" type="tel" placeholder="+52 55 1234 5678")
        .form-group
          label Contraseña
          input(v-model="form.password" type="password" placeholder="••••••••")

        .social-editor
          label.options-label Redes sociales (opcional)
          .social-row(v-for="(link, idx) in form.socialLinks" :key="idx")
            select(v-model="link.platform")
              option(value="twitter") Twitter/X
              option(value="instagram") Instagram
              option(value="linkedin") LinkedIn
              option(value="github") GitHub
              option(value="facebook") Facebook
              option(value="tiktok") TikTok
              option(value="web") Sitio web
            input(v-model="link.url" placeholder="https://...")
            button.btn-icon(type="button" @click="removeLink(idx)" title="Quitar") ✕
          button.btn-add(type="button" @click="addLink") + Agregar red social

        .error(v-if="error") {{ error }}
        button.btn-primary(@click="submitRegister" :disabled="loading")
          span(v-if="loading") Creando...
          span(v-else) Crear cuenta
        p.register-hint ¿Ya tienes cuenta? #[router-link(to="/login") Inicia sesión]

      template(v-else-if="step === 'verify'")
        h2 Verifica tu cuenta
        p.hint Enviamos un código a {{ verifyChannel === 'EMAIL' ? form.email : form.phone }}
        .form-group
          label Código de verificación
          input(v-model="code" placeholder="123456" maxlength="6" @keyup.enter="submitVerify")
        .error(v-if="error") {{ error }}
        button.btn-primary(@click="submitVerify" :disabled="loading")
          span(v-if="loading") Verificando...
          span(v-else) Verificar
        button.btn-ghost(type="button" @click="resendOtp" :disabled="loading") Reenviar código

      template(v-else-if="step === 'done'")
        h2 ¡Cuenta verificada! 🎉
        p.hint Ya puedes participar en las conferencias.
        router-link.btn-primary-link(to="/") Ir al inicio
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, sendOtp, verifyOtp } from '@/services/api/authApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'RegisterPage',
  components: { AppHeader },
  setup() {
    const router = useRouter()
    const auth = useAuthStore()
    const step = ref('form')
    const loading = ref(false)
    const error = ref('')
    const code = ref('')
    const verifyChannel = ref('EMAIL')
    const verifyIdentifier = ref('')

    const form = ref({ displayName: '', email: '', phone: '', password: '', socialLinks: [] })

    function addLink() { form.value.socialLinks.push({ platform: 'twitter', url: '' }) }
    function removeLink(idx) { form.value.socialLinks.splice(idx, 1) }

    async function submitRegister() {
      error.value = ''
      if (!form.value.email && !form.value.phone) {
        error.value = 'Ingresa al menos un correo o teléfono'
        return
      }
      if (!form.value.password) {
        error.value = 'La contraseña es obligatoria'
        return
      }
      loading.value = true
      try {
        const socialLinks = form.value.socialLinks.filter(l => l.url && l.url.trim())
        await register({ ...form.value, socialLinks })
        await sendOtpWithFallback()
        step.value = 'verify'
      } catch (e) {
        error.value = e.response?.data?.error?.message || 'No se pudo crear la cuenta. Intenta de nuevo.'
      } finally {
        loading.value = false
      }
    }

    // Correo es el canal primario; si falla (ej. proveedor no configurado o
    // error de envío) y hay teléfono, se reintenta automáticamente por SMS.
    async function sendOtpWithFallback() {
      if (form.value.email) {
        try {
          verifyChannel.value = 'EMAIL'
          verifyIdentifier.value = form.value.email
          await sendOtp(verifyIdentifier.value, 'EMAIL')
          return
        } catch (e) {
          if (!form.value.phone) throw e
        }
      }
      verifyChannel.value = 'SMS'
      verifyIdentifier.value = form.value.phone
      await sendOtp(verifyIdentifier.value, 'SMS')
    }

    async function resendOtp() {
      error.value = ''
      loading.value = true
      try {
        await sendOtp(verifyIdentifier.value, verifyChannel.value)
      } catch (e) {
        error.value = 'No se pudo reenviar el código.'
      } finally {
        loading.value = false
      }
    }

    async function submitVerify() {
      error.value = ''
      if (!code.value.trim()) { error.value = 'Ingresa el código recibido'; return }
      loading.value = true
      try {
        const result = await verifyOtp(verifyIdentifier.value, code.value.trim())
        auth.setSession(result)
        step.value = 'done'
        setTimeout(() => router.push('/'), 1500)
      } catch (e) {
        error.value = e.response?.data?.error?.message || 'Código inválido o expirado.'
      } finally {
        loading.value = false
      }
    }

    return {
      step, form, code, verifyChannel, loading, error,
      addLink, removeLink, submitRegister, submitVerify, resendOtp
    }
  }
}
</script>

<style scoped>
.register-page { min-height: 100vh; background: #f5f3ff; }
.register-main { display: flex; justify-content: center; padding: 60px 24px; }
.register-card { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 460px; width: 100%; }
h2 { margin: 0 0 8px; color: #1e1b4b; }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 24px; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input, select { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; }
input:focus, select:focus { outline: none; border-color: #4f46e5; }
.btn-primary {
  width: 100%; padding: 12px; background: #4f46e5; color: #fff; border: none; border-radius: 8px;
  cursor: pointer; font-size: 1rem; margin-bottom: 10px;
}
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary-link {
  display: block; text-align: center; width: 100%; padding: 12px; background: #4f46e5; color: #fff;
  border-radius: 8px; text-decoration: none; font-size: 1rem; box-sizing: border-box;
}
.btn-ghost {
  width: 100%; padding: 10px; background: #fff; color: #6b7280; border: 1px solid #e5e7eb;
  border-radius: 8px; cursor: pointer; font-size: 0.9rem;
}
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.register-hint { text-align: center; margin: 8px 0 0; font-size: 0.85rem; color: #6b7280; }
.register-hint a { color: #4f46e5; font-weight: 600; text-decoration: none; }
.register-hint a:hover { text-decoration: underline; }

.social-editor { margin-bottom: 16px; }
.options-label { font-size: 0.82rem; color: #6b7280; margin-bottom: 6px; display: block; font-weight: 600; }
.social-row { display: flex; gap: 6px; margin-bottom: 6px; }
.social-row select { flex: 0 0 130px; }
.social-row input { flex: 1; }
.btn-icon {
  flex-shrink: 0; width: 38px; border: 1px solid #e5e7eb; border-radius: 8px;
  background: #f9fafb; color: #6b7280; cursor: pointer;
}
.btn-add {
  padding: 6px 14px; border: 1px dashed #a5b4fc; border-radius: 8px; background: #f5f3ff; color: #4338ca;
  cursor: pointer; font-size: 0.82rem; font-weight: 500;
}
.btn-add:hover { background: #ede9fe; }

@media (max-width: 480px) {
  .register-main { padding: 24px 16px; }
  .register-card { padding: 24px; }
  .social-row { flex-wrap: wrap; }
  .social-row select { flex: 1 1 100%; }
  .social-row input { flex: 1 1 100%; }
}
</style>
