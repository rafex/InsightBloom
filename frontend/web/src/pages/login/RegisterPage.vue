<template lang="pug">
.register-page
  AppHeader
  main.register-main
    .register-card
      template(v-if="step === 'form'")
        h2 Crear cuenta
        p.hint Necesitas correo o teléfono (al menos uno) para verificar tu identidad
        FormField(label="Nombre de usuario")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="form.displayName" placeholder="Tu nombre de usuario")
        FormField(label="Correo electrónico")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="form.email" type="email" placeholder="tu@correo.com")
        FormField(label="Teléfono")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="form.phone" type="tel" placeholder="+52 55 1234 5678")
        FormField(label="Contraseña")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="form.password" type="password" placeholder="••••••••")

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
        BaseButton(size="lg" @click="submitRegister" :disabled="loading" :loading="loading") Crear cuenta
        p.register-hint ¿Ya tienes cuenta? #[router-link(to="/login") Inicia sesión]

      template(v-else-if="step === 'verify'")
        h2 Verifica tu cuenta
        p.hint Enviamos un código a {{ verifyChannel === 'EMAIL' ? form.email : form.phone }}
        FormField(label="Código de verificación")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="code" placeholder="123456" maxlength="6" @keyup.enter="submitVerify")
        .error(v-if="error") {{ error }}
        BaseButton(size="lg" @click="submitVerify" :disabled="loading" :loading="loading") Verificar
        BaseButton(variant="ghost" type="button" @click="resendOtp" :disabled="loading") Reenviar código

      template(v-else-if="step === 'done'")
        h2 ¡Cuenta verificada! 🎉
        p.hint Ya puedes participar en las conferencias.
        router-link.link-btn.link-btn-primary(to="/") Ir al inicio
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import FormField from '@/components/ui/FormField.vue'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { register, sendOtp, verifyOtp, type SocialLink } from '@/services/api/authApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'RegisterPage',
  components: { AppHeader, BaseButton, FormField },
  setup() {
    const router = useRouter()
    const route = useRoute()
    const auth = useAuthStore()
    const step = ref('form')
    const loading = ref(false)
    const error = ref('')
    const code = ref('')
    const verifyChannel = ref('EMAIL')
    const verifyIdentifier = ref('')

    const form = ref<{ displayName: string, email: string, phone: string, password: string, socialLinks: SocialLink[] }>(
      { displayName: '', email: '', phone: '', password: '', socialLinks: [] }
    )

    function addLink() { form.value.socialLinks.push({ platform: 'twitter', url: '' }) }
    function removeLink(idx: number) { form.value.socialLinks.splice(idx, 1) }

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
      } catch (e: any) {
        error.value = e?.response?.status === 403
          ? 'Este dispositivo fue bloqueado por uso indebido de la plataforma. Contactá a un administrador.'
          : (e.response?.data?.error?.message || 'No se pudo crear la cuenta. Intenta de nuevo.')
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
        } catch (e: any) {
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
      } catch (e: any) {
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
        setTimeout(() => router.push(String(route.query.redirect || '/')), 1500)
      } catch (e: any) {
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
.register-page { min-height: 100vh; background: var(--color-bg); }
.register-main { display: flex; justify-content: center; padding: 60px 24px; }
.register-card { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 460px; width: 100%; }
h2 { margin: 0 0 8px; color: var(--color-heading); }
.hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 24px; }
.error { color: var(--color-danger); font-size: 0.9rem; margin-bottom: 12px; }
.register-hint { text-align: center; margin: 8px 0 0; font-size: 0.85rem; color: var(--color-text-muted); }
.register-hint a { color: var(--color-primary); font-weight: 600; text-decoration: none; }
.register-hint a:hover { text-decoration: underline; }

.social-editor { margin-bottom: 16px; }
.options-label { font-size: 0.82rem; color: var(--color-text-muted); margin-bottom: 6px; display: block; font-weight: 600; }
.social-row { display: flex; gap: 6px; margin-bottom: 6px; }
.social-row select { flex: 0 0 130px; }
.social-row input { flex: 1; }
.btn-icon {
  flex-shrink: 0; width: 38px; border: 1px solid var(--color-border-subtle); border-radius: 8px;
  background: var(--color-surface-muted); color: var(--color-text-muted); cursor: pointer;
}
.btn-add {
  padding: 6px 14px; border: 1px dashed var(--color-primary-border); border-radius: 8px; background: var(--color-bg); color: var(--color-primary-dark);
  cursor: pointer; font-size: 0.82rem; font-weight: 500;
}
.btn-add:hover { background: var(--color-primary-soft); }

@media (max-width: 480px) {
  .register-main { padding: 24px 16px; }
  .register-card { padding: 24px; }
  .social-row { flex-wrap: wrap; }
  .social-row select { flex: 1 1 100%; }
  .social-row input { flex: 1 1 100%; }
}
</style>
