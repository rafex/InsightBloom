<template lang="pug">
.login-page
  AppHeader
  main.login-main
    .login-card.animate__animated.animate__fadeIn
      h2 Iniciar sesión
      nav.login-mode-tabs
        button.mode-tab(type="button" :class="{ active: mode === 'password' }" @click="switchMode('password')") Contraseña
        button.mode-tab(type="button" :class="{ active: mode === 'otp' }" @click="switchMode('otp')") Código por correo

      template(v-if="mode === 'password'")
        p.hint Inicia sesión con tu correo electrónico verificado
        FormField(label="Correo electrónico")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="username" type="email" autocomplete="username" placeholder="tu@correo.com" @keyup.enter="doLogin")
        FormField(label="Contraseña")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="password" type="password" autocomplete="current-password" placeholder="••••••••" @keyup.enter="doLogin")
        .error(v-if="error") {{ error }}
        .login-actions
          BaseButton(size="lg" @click="doLogin" :disabled="loading" :loading="loading") Iniciar sesión

      template(v-else)
        template(v-if="otpStep === 'request'")
          p.hint Te mandamos un código de 6 dígitos a tu correo registrado. Válido solo para cuentas que activaron este método en su perfil.
          FormField(label="Correo electrónico o usuario")
            template(#default="{ id, describedBy }")
              input(:id="id" :aria-describedby="describedBy" v-model="otpIdentifier" type="text" autocomplete="username" placeholder="tu@correo.com" @keyup.enter="doRequestOtp")
          .error(v-if="error") {{ error }}
          .login-actions
            BaseButton(size="lg" @click="doRequestOtp" :disabled="loading" :loading="loading") Enviar código
        template(v-else)
          p.hint Ingresá el código de 6 dígitos que te llegó a tu correo. Vence en 10 minutos.
          FormField(label="Código")
            template(#default="{ id, describedBy }")
              input(:id="id" :aria-describedby="describedBy" v-model="otpCode" type="text" inputmode="numeric" maxlength="6" placeholder="123456" @keyup.enter="doVerifyOtp")
          .error(v-if="error") {{ error }}
          .login-actions
            BaseButton(size="lg" @click="doVerifyOtp" :disabled="loading" :loading="loading") Verificar e iniciar sesión
          button.btn-resend(type="button" @click="doRequestOtp" :disabled="loading") Reenviar código

      p.register-hint ¿No tienes cuenta? #[router-link(to="/register") Regístrate]
      //- Entrada como invitado visible solo cuando se llegó desde un evento (auditoría UX: el
      //- modo anónimo existía pero solo se descubría por accidente dentro de la página del
      //- boleto — y es justo la opción de menor fricción para quien escaneó un QR).
      .guest-block(v-if="guestTarget")
        .divider o
        BaseButton(variant="secondary" type="button" @click="continueAsGuest") Continuar como invitado
        p.guest-hint Entrás al evento sin cuenta: podés ver la presentación y participar de forma limitada, pero sin certificado ni historial.
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
import { requestLoginOtp, verifyLoginOtp } from '@/services/api/authApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import FormField from '@/components/ui/FormField.vue'
export default {
  name: 'LoginPage',
  components: { AppHeader, BaseButton, FormField },
  setup() {
    const username = ref('')
    const password = ref('')
    const error = ref('')
    const loading = ref(false)
    const router = useRouter()
    const route = useRoute()
    const auth = useAuthStore()

    // Dos maneras de entrar: contraseña (default) o código por correo (opt-in desde el
    // perfil, ver ProfilePage.vue). No se detecta automáticamente cuál usa la cuenta --
    // eso filtraría si un identificador existe o no (ver spec de login OTP, sección 5.3).
    const mode = ref<'password' | 'otp'>('password')
    const otpStep = ref<'request' | 'verify'>('request')
    const otpIdentifier = ref('')
    const otpCode = ref('')

    function switchMode(next: 'password' | 'otp') {
      mode.value = next
      otpStep.value = 'request'
      otpCode.value = ''
      error.value = ''
    }

    async function doRequestOtp() {
      if (!otpIdentifier.value.trim()) {
        error.value = 'Ingresá tu correo o usuario'
        return
      }
      loading.value = true; error.value = ''
      try {
        await requestLoginOtp(otpIdentifier.value.trim())
        otpStep.value = 'verify'
      } catch (e: any) {
        error.value = 'No se pudo enviar el código. Reintentá en unos segundos.'
      } finally {
        loading.value = false
      }
    }

    async function doVerifyOtp() {
      if (!otpCode.value.trim()) {
        error.value = 'Ingresá el código que recibiste'
        return
      }
      loading.value = true; error.value = ''
      try {
        const session = await verifyLoginOtp(otpIdentifier.value.trim(), otpCode.value.trim())
        auth.setSession(session)
        router.push(String(route.query.redirect || '/dashboard'))
      } catch (e: any) {
        error.value = 'Código incorrecto o vencido. Pedí uno nuevo.'
      } finally {
        loading.value = false
      }
    }

    // Solo se ofrece "invitado" si venimos redirigidos desde la página de un evento (/c/...):
    // sin contexto de evento no hay a dónde entrar como invitado.
    const redirectPath = String(route.query.redirect || '')
    const guestTarget = redirectPath.startsWith('/c/') ? redirectPath : ''
    function continueAsGuest() { router.push(guestTarget) }
    async function doLogin() {
      if (!username.value.trim() || !password.value.trim()) {
        error.value = 'Correo y contraseña son obligatorios'
        return
      }
      loading.value = true; error.value = ''
      try {
        await auth.login(username.value.trim(), password.value)
        router.push(String(route.query.redirect || '/dashboard'))
      } catch (e: any) {
        // Distinguir "te equivocaste" de "no hay conexion": la accion correctiva es opuesta
        // (corregir credenciales vs reintentar) y el mensaje mezclado no dejaba saber cual.
        const status = e?.response?.status
        const code = e?.response?.data?.error?.code
        if (code === 'otp_login_required') {
          error.value = 'Esta cuenta usa código de acceso, no contraseña. Cambiá a la pestaña "Código por correo".'
        } else if (status === 403) {
          error.value = 'Este dispositivo fue bloqueado por uso indebido de la plataforma. Contactá a un administrador.'
        } else if (status === 401 || status === 400) {
          error.value = 'Correo o contraseña incorrectos.'
        } else if (!e?.response) {
          error.value = 'No se pudo conectar con el servidor. Verificá tu conexión y reintentá.'
        } else {
          error.value = 'El servidor respondió con un error inesperado. Reintentá en unos segundos.'
        }
      } finally {
        loading.value = false
      }
    }
    return {
      username, password, error, loading, doLogin, guestTarget, continueAsGuest,
      mode, otpStep, otpIdentifier, otpCode, switchMode, doRequestOtp, doVerifyOtp
    }
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; background: var(--color-bg); }
.login-main { display: flex; justify-content: center; padding: 80px 24px; }
.login-card { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 400px; width: 100%; }
h2 { margin: 0 0 8px; color: var(--color-heading); }
.login-mode-tabs { display: flex; gap: 6px; margin-bottom: 20px; border-bottom: 1px solid var(--color-border-subtle); }
.mode-tab {
  flex: 1; padding: 8px 10px; border: none; background: none; color: var(--color-text-muted); cursor: pointer;
  font-size: 0.85rem; font-weight: 600; border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.mode-tab:hover { color: var(--color-primary); }
.mode-tab.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.btn-resend {
  display: block; margin: 10px auto 0; padding: 6px 10px; border: none; background: none;
  color: var(--color-primary); font-size: 0.82rem; font-weight: 600; cursor: pointer;
}
.btn-resend:hover { text-decoration: underline; }
.btn-resend:disabled { opacity: 0.5; cursor: not-allowed; }
.hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 24px; }
.login-actions { display: flex; justify-content: center; }
.guest-block { margin-top: 14px; text-align: center; }
.divider { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 10px; }
.guest-hint { margin: 8px 0 0; font-size: 0.8rem; color: var(--color-text-muted); }
.error { color: var(--color-danger); font-size: 0.9rem; margin-bottom: 12px; }
.register-hint { text-align: center; margin: 16px 0 0; font-size: 0.85rem; color: var(--color-text-muted); }
.register-hint a { color: var(--color-primary); font-weight: 600; text-decoration: none; }
.register-hint a:hover { text-decoration: underline; }

@media (max-width: 480px) {
  .login-main { padding: 32px 16px; }
  .login-card { padding: 24px; }
}
</style>
