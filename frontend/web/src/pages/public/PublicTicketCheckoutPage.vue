<template lang="pug">
.checkout-page
  AppHeader
  main#main-content.checkout-main(v-if="event" tabindex="-1")
    BaseLink.back(size="sm" variant="ghost" :to="`/events/${event.friendlyId}`") ← Volver al detalle
    .checkout-layout
      .master-container
        section.card.cart
          label.title Tu boleto
          .products
            .product
              .product-art
                img.event-image(v-if="event.flyerBase64" :src="event.flyerBase64" alt="")
                span(v-else aria-hidden="true") 🎟️
              .product-copy
                strong {{ event.name }}
                p(v-if="event.eventDate") {{ event.eventDate }}{{ event.venue ? ` · ${event.venue}` : '' }}
                p(v-if="event.description") {{ event.description }}
              .quantity(aria-label="Cantidad")
                span 1
              .product-price {{ formattedPrice }}
        //- Sin seccion de cupones hasta que exista la funcionalidad: un input deshabilitado
        //- "Disponible proximamente" solo genera dudas (auditoria UX 2026-07-26).
        section.card.checkout
          label.title {{ privateTickets ? "Solicitud de boleto" : "Resumen de compra" }}
          .details
            span Boleto de acceso
            span {{ formattedPrice }}
            span Modalidad
            span {{ privateTickets ? 'Por invitación' : event.visibility === 'HYBRID' ? 'Híbrido' : 'Público' }}
            span Descuento
            span 0.00 {{ event.ticketCurrency || 'MXN' }}
          .checkout-footer
            .total
              small Total
              strong {{ isFree ? `0.00 ${event.ticketCurrency || 'MXN'}` : formattedPrice }}
            .signature-pending(v-if="pendingSignature" role="status" aria-live="polite")
              strong Solicitud recibida
              p Tu lugar quedó reservado. Recibirás el boleto por correo cuando se complete la firma del organizador.
            .device-choice(v-if="!privateTickets && !success && isFree && isAuthenticated")
              label(for="ticket-device-count") Dispositivos autorizados
              select#ticket-device-count(v-model.number="maxDevices" aria-label="Cantidad de dispositivos autorizados")
                option(:value="1") 1 dispositivo (recomendado)
                option(:value="2") 2 dispositivos
                option(:value="3") 3 dispositivos
                option(:value="4") 4 dispositivos
                option(:value="5") 5 dispositivos
              p Activarás el boleto verificando el correo de tu cuenta. La clave privada queda en este navegador.
            template(v-if="!success")
              span.checkout-closed(v-if="privateTickets && !inviteToken") Abre el enlace o escanea el QR que te compartió el anfitrión.
              template(v-else-if="privateTickets")
                p Verifica el correo de tu cuenta. Si hay cupo, el sistema reservará tu boleto al solicitarlo.
                BaseLink.checkout-action(v-if="!isAuthenticated" :to="{ path: '/login', query: { redirect: route.path } }") Inicia sesión
                BaseLink.checkout-action(v-if="!isAuthenticated" :to="{ path: '/register', query: { redirect: route.path + '?solicitar=1' } }") Crear cuenta y solicitar boleto
                BaseButton.checkout-action(v-if="needsVerifiedEmail" type="button" :loading="sendingCode" @click="sendVerificationCode") {{ verificationEmail ? 'Reenviar código' : 'Enviar código de verificación' }}
                form(v-if="verificationEmail" @submit.prevent="verifyAndRequest")
                  label(for="ticket-email-code") Código enviado a {{ verificationEmail }}
                  input#ticket-email-code(v-model.trim="verificationCode" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required)
                  BaseButton(type="submit" :loading="verifying" :disabled="verificationCode.length !== 6") Verificar y solicitar boleto
                BaseButton.checkout-action(v-if="isAuthenticated && !needsVerifiedEmail" type="button" :loading="submitting" @click="confirmFreeTicket") Solicitar boleto
              span.checkout-closed(v-else-if="event.ticketSoldOut") Los boletos de este evento están agotados.
              span.checkout-closed(v-else-if="event.ticketSalesClosed") La adquisición pública fue cerrada por el organizador. Solicita tu boleto directamente al organizador.
              BaseLink.checkout-action(v-else-if="!isAuthenticated" :to="{ path: '/login', query: { redirect: `/events/${event.friendlyId}/checkout` } }") Inicia sesión
              BaseButton.checkout-action(v-else-if="isFree && event.ticketPurchaseEnabled" type="button" :loading="submitting" @click="confirmFreeTicket") Confirmar boleto
              span.checkout-closed(v-else-if="!event.ticketPurchaseEnabled") La adquisición de boletos no está disponible para este evento.
              BaseButton.checkout-action(v-else type="button" disabled) Pago próximamente
            BaseLink.checkout-action(v-else-if="pendingSignature" :to="`/events/${event.friendlyId}`") Volver al evento
            BaseLink.checkout-action(v-else variant="success" :to="`/c/${event.friendlyId}`") Entrar al evento
          FeedbackMessage.checkout-feedback(v-if="actionError" :message="actionError" tone="error")
      //- Sin formulario de tarjeta falso: campos de pago deshabilitados hacen dudar ("¿tengo
      //- que pagar? ¿esta roto?"). Hasta que exista proveedor de pagos, un aviso claro basta.
      aside.free-panel(v-if="!isFree")
        .free-panel-icon 💳
        h2 Pago en línea próximamente
        p Este evento tiene costo, pero el pago en línea todavía no está habilitado en la plataforma. Contactá a los organizadores del evento para adquirir tu boleto.
        .security-note 🔒 Nunca se te pedirán datos bancarios por esta página mientras el pago no esté habilitado.
      aside.free-panel(v-else)
        .free-panel-icon 🎟️
        h2 Acceso gratuito
        p Este evento no tiene costo. Al confirmar, el boleto quedará ligado a tu cuenta y podrás entrar al evento.
        .security-note 🔒 No se solicitarán datos de pago.
  LoadingState.state(v-else-if="loading" message="Cargando acceso al evento...")
  FeedbackMessage.state-error(v-else :message="error" tone="error")
</template>

<script lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/app/layout/AppHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import { getPublicConference, getUserProfile, requestPublicTicket } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import { sendOtp, verifyOtp } from '@/services/api/authApi'
import type { PublicConference } from '@/services/api/types'

export default {
  name: 'PublicTicketCheckoutPage',
  components: { AppHeader, BaseButton, BaseLink, FeedbackMessage, LoadingState },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const auth = useAuthStore()
    const event = ref<PublicConference | null>(null)
    const loading = ref(true)
    const error = ref('')
    const actionError = ref('')
    const submitting = ref(false)
    const success = ref(false)
    const pendingSignature = ref(false)
    const maxDevices = ref(1)
    const inviteToken = ref('')
    const needsVerifiedEmail = ref(false)
    const verificationEmail = ref('')
    const verificationCode = ref('')
    const sendingCode = ref(false)
    const verifying = ref(false)
    const privateTickets = computed(() => event.value?.visibility === 'LISTED_PRIVATE_TICKETS')
    const isAuthenticated = computed(() => auth.isAuthenticated() && auth.state.role !== 'guest')
    const isFree = computed(() => Number(event.value?.ticketPrice || 0) === 0)
    const formattedPrice = computed(() => isFree.value
      ? 'Gratis'
      : `${event.value?.ticketPrice || '0.00'} ${event.value?.ticketCurrency || 'MXN'}`)

    onMounted(async () => {
      const storageKey = `ticket-request:${route.params.friendlyId}`
      const fragment = new URLSearchParams(route.hash.slice(1)).get('solicitud')
      if (fragment !== null) {
        inviteToken.value = /^[A-Za-z0-9_-]{43}$/.test(fragment) ? fragment : ''
        try {
          if (inviteToken.value) sessionStorage.setItem(storageKey, inviteToken.value)
          else sessionStorage.removeItem(storageKey)
        } catch { /* the current page can still use the link */ }
        await router.replace({ path: route.path, query: route.query })
      } else {
        try { inviteToken.value = sessionStorage.getItem(storageKey) || '' } catch { /* storage blocked */ }
      }
      try {
        event.value = await getPublicConference(route.params.friendlyId as string)
        if (route.query.solicitar === '1') {
          await router.replace({ path: route.path })
          if (privateTickets.value && inviteToken.value && isAuthenticated.value) await confirmFreeTicket()
        }
      } catch {
        error.value = 'No se encontró el evento público.'
      } finally {
        loading.value = false
      }
    })

    async function sendVerificationCode() {
      const { token, userUuid } = auth.state
      if (!token || !userUuid || sendingCode.value) return
      sendingCode.value = true
      actionError.value = ''
      try {
        const profile = await getUserProfile(userUuid, token)
        if (!profile.email) {
          actionError.value = 'Esta cuenta no tiene correo. Usa una cuenta con correo electrónico.'
          return
        }
        await sendOtp(profile.email, 'EMAIL')
        verificationEmail.value = profile.email
      } catch (error: any) {
        actionError.value = error.response?.data?.error?.message || 'No se pudo enviar el código.'
      } finally {
        sendingCode.value = false
      }
    }

    async function verifyAndRequest() {
      if (verifying.value || !verificationEmail.value) return
      verifying.value = true
      actionError.value = ''
      try {
        const session = await verifyOtp(verificationEmail.value, verificationCode.value)
        if (session.userUuid !== auth.state.userUuid) {
          actionError.value = 'Inicia sesión con la cuenta cuyo correo verificaste.'
          return
        }
        auth.setSession(session)
        needsVerifiedEmail.value = false
        verificationCode.value = ''
        await confirmFreeTicket()
      } catch (error: any) {
        actionError.value = error.response?.data?.error?.message || 'El código es inválido o venció.'
      } finally {
        verifying.value = false
      }
    }

    async function confirmFreeTicket() {
      if (!event.value || !isAuthenticated.value || !auth.state.token || (privateTickets.value && !inviteToken.value)) return
      submitting.value = true
      actionError.value = ''
      try {
        const ticket = await requestPublicTicket(event.value.friendlyId, auth.state.token, {
          maxDevices: maxDevices.value, enrollmentMethod: 'EMAIL_OTP',
          inviteToken: privateTickets.value ? inviteToken.value : undefined
        })
        if (ticket.status === 'PENDING_SIGNATURE') {
          pendingSignature.value = true
          success.value = true
        } else if (ticket.deviceMaxDevices && ticket.deviceMaxDevices > 0) {
          if (ticket.status === 'CLAIMED' || ticket.status === 'CHECKED_IN') {
            await router.push(`/c/${encodeURIComponent(event.value.friendlyId)}/ticket`)
          } else await router.push({ path: '/ticket-enroll', query: { event: ticket.conferenceUuid, ticketCode: ticket.ticketCode } })
        } else success.value = true
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        if (code === 'capacity_exceeded' || code === 'ticket_sold_out') {
          actionError.value = 'Los boletos de este evento se agotaron.'
          event.value.ticketSoldOut = true
          event.value.ticketPurchaseEnabled = false
        }
        else if (code === 'verified_email_required') {
          needsVerifiedEmail.value = true
          actionError.value = 'Verifica tu correo para continuar con la solicitud.'
        } else if (code === 'ticket_request_link_invalid') {
          actionError.value = 'El enlace ha vencido o fue revocado. Pide al anfitrión un enlace vigente.'
        } else if (code === 'ticket_request_unavailable') {
          actionError.value = 'Este evento no admite solicitudes por enlace. Contacta al anfitrión.'
        } else if (code === 'payment_required') actionError.value = 'Este evento requiere pago y todavía no hay un proveedor habilitado.'
        else if (code === 'login_required' || code === 'token_missing') {
          await router.push({ path: '/login', query: { redirect: `/events/${event.value.friendlyId}/checkout` } })
          return
        } else actionError.value = e.response?.data?.error?.message || 'No se pudo confirmar el boleto.'
      } finally {
        submitting.value = false
      }
    }

    return { event, loading, error, actionError, submitting, success, pendingSignature, maxDevices, isAuthenticated, isFree, formattedPrice, confirmFreeTicket, route, privateTickets, inviteToken, needsVerifiedEmail, verificationEmail, verificationCode, sendingCode, verifying, sendVerificationCode, verifyAndRequest }
  }
}
</script>

<style scoped>
.checkout-page { min-height: 100vh; background: var(--color-primary-soft); }
.checkout-main { max-width: 980px; margin: 0 auto; padding: 42px 24px 76px; }
.back { margin-left: -12px; }
.checkout-layout { display: grid; grid-template-columns: minmax(360px, 420px) minmax(300px, 1fr); gap: 28px; margin-top: 25px; align-items: start; }
.master-container { display: grid; gap: 5px; }
.card, .payment-modal, .free-panel { background: var(--color-surface); box-shadow: 0 22px 34px rgba(30, 27, 75, .09), 0 4px 12px rgba(30, 27, 75, .07); }
.card { width: 100%; }
.title { display: flex; align-items: center; min-height: 42px; padding: 0 20px; border-bottom: 1px solid var(--color-border-subtle); font-size: 12px; font-weight: 800; color: var(--color-text-muted); }
.cart { border-radius: 19px 19px 7px 7px; }
.products { padding: 14px; }
.product { display: grid; grid-template-columns: 60px minmax(0, 1fr) 34px auto; gap: 10px; align-items: center; }
.product-art { width: 60px; height: 60px; display: grid; place-items: center; overflow: hidden; border-radius: 9px; background: var(--color-primary-soft); color: var(--color-primary); font-size: 27px; }
.event-image { width: 100%; height: 100%; object-fit: cover; }
.product-copy { min-width: 0; }.product-copy strong { display: block; overflow: hidden; color: var(--color-text-secondary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.product-copy p { overflow: hidden; margin: 4px 0 0; color: var(--color-text-muted); font-size: 11px; font-weight: 600; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.quantity { display: grid; place-items: center; width: 32px; height: 30px; border: 1px solid var(--color-border-subtle); border-radius: 7px; color: var(--color-text-secondary); font-size: 13px; font-weight: 700; }
.product-price { color: var(--color-text); font-size: 14px; font-weight: 900; white-space: nowrap; }
.coupons { border-radius: 7px; }.coupon-form { display: grid; grid-template-columns: 1fr 90px; gap: 10px; padding: 12px; }.input-field { width: 100%; height: 38px; box-sizing: border-box; padding: 0 12px; border: 1px solid var(--color-border-subtle); border-radius: 7px; background: var(--color-surface-muted); color: var(--color-text-secondary); }.input-field:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(79, 70, 229, .16); }.input-field:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }.coupon-form button { height: 38px; border: 0; border-radius: 7px; background: var(--color-primary); color: var(--color-on-primary); font-size: 12px; font-weight: 700; }.coupon-form button:disabled, .payment-options button:disabled { cursor: not-allowed; opacity: .55; }
.checkout { border-radius: 9px 9px 19px 19px; }.details { display: grid; grid-template-columns: 3fr 1fr; gap: 6px; padding: 14px 16px; }.details span:nth-child(odd) { color: var(--color-text-muted); font-size: 11px; font-weight: 700; }.details span:nth-child(even) { color: var(--color-text-secondary); font-size: 13px; font-weight: 700; text-align: right; white-space: nowrap; }.checkout-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 12px 12px 20px; background: var(--color-surface-muted); }.total { display: flex; flex-direction: column; gap: 2px; }.total small { color: var(--color-text-muted); font-size: 11px; }.total strong { color: var(--color-text); font-size: 22px; font-weight: 900; white-space: nowrap; }.checkout-action { min-width: 142px; min-height: 38px; padding: 0 15px; border-radius: 7px; font-size: 13px; }.error { margin: 0; padding: 0 16px 13px; color: var(--color-danger-dark); font-size: 12px; font-weight: 700; }
.payment-panel { min-width: 0; }.payment-modal { max-width: 450px; border-radius: 26px; padding: 20px; }.payment-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; color: var(--color-heading); font-size: 18px; font-weight: 800; }.payment-heading small { color: var(--color-warning); font-size: 11px; }.payment-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0; }.payment-options button { height: 50px; border: 0; border-radius: 11px; background: var(--color-surface-muted); color: var(--color-text-muted); font-size: 11px; font-weight: 700; }.separator { display: grid; grid-template-columns: 1fr auto 1fr; gap: 10px; align-items: center; margin: 10px 0 18px; color: var(--color-text-muted); }.separator span { height: 1px; background: var(--color-border-subtle); }.separator p { margin: 0; font-size: 11px; font-weight: 600; }.payment-fields { display: flex; flex-direction: column; gap: 6px; }.payment-fields label, .split-fields label { color: var(--color-text-muted); font-size: 10px; font-weight: 700; }.payment-fields .input-field { height: 40px; margin-bottom: 8px; }.split-fields { display: grid; grid-template-columns: 4fr 2fr; gap: 15px; }.split-fields > div { display: flex; flex-direction: column; gap: 6px; }.payment-note, .free-panel p { color: var(--color-text-muted); font-size: 12px; line-height: 1.5; }.payment-note { margin: 8px 0 0; }
.free-panel { min-height: 220px; border-radius: 20px; padding: 30px; }.free-panel-icon { display: grid; place-items: center; width: 62px; height: 62px; margin-bottom: 16px; border-radius: 12px; background: var(--color-primary-soft); font-size: 30px; }.free-panel h2 { margin: 0; color: var(--color-heading); }.free-panel p { margin: 10px 0 20px; }.security-note { padding: 12px; border-radius: 9px; background: var(--color-success-soft); color: var(--color-success); font-size: 12px; font-weight: 700; }
.state { max-width: 980px; margin: 50px auto; padding: 30px; color: var(--color-text-muted); }.state-error { max-width: 980px; margin: 50px auto; padding: 30px; color: var(--color-danger-dark); }.checkout-feedback { margin: 0; padding: 0 16px 13px; color: var(--color-danger-dark); font-size: 12px; font-weight: 700; }
.signature-pending { margin: 12px 0; padding: 14px; border: 1px solid var(--color-warning, #d99a00); border-radius: 10px; background: var(--color-warning-soft, #fff8e6); }.signature-pending p { margin: 6px 0 0; }
@media (max-width: 760px) { .checkout-main { padding: 28px 16px 55px; }.checkout-layout { grid-template-columns: 1fr; }.payment-modal { max-width: none; }.master-container { max-width: 440px; } }
@media (max-width: 430px) { .product { grid-template-columns: 52px minmax(0, 1fr) 28px; }.product-art { width: 52px; height: 52px; }.product-price { grid-column: 2 / -1; grid-row: 2; text-align: right; }.checkout-footer { align-items: stretch; flex-direction: column; padding: 14px; }.checkout-action { width: 100%; }.coupon-form { grid-template-columns: 1fr; } }
</style>
