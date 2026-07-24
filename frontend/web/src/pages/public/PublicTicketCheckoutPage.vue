<template lang="pug">
.checkout-page
  AppHeader
  main.checkout-main(v-if="event")
    router-link.back(:to="`/events/${event.friendlyId}`") ← Volver al detalle
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
        section.card.coupons
          label.title Código promocional
          .coupon-form
            input.input-field(type="text" placeholder="Disponible próximamente" disabled)
            button(type="button" disabled) Aplicar
        section.card.checkout
          label.title Resumen de compra
          .details
            span Boleto de acceso
            span {{ formattedPrice }}
            span Modalidad
            span {{ event.visibility === 'HYBRID' ? 'Híbrido' : 'Público' }}
            span Descuento
            span 0.00 {{ event.ticketCurrency || 'MXN' }}
          .checkout-footer
            .total
              small Total
              strong {{ isFree ? `0.00 ${event.ticketCurrency || 'MXN'}` : formattedPrice }}
            template(v-if="!success")
              router-link.checkout-btn(v-if="!isAuthenticated" :to="{ path: '/login', query: { redirect: `/events/${event.friendlyId}/checkout` } }") Inicia sesión
              button.checkout-btn(v-else-if="isFree && event.ticketPurchaseEnabled" type="button" :disabled="submitting" @click="confirmFreeTicket")
                span(v-if="submitting") Procesando...
                span(v-else) Confirmar boleto
              span.checkout-closed(v-else) La emisión de boletos está cerrada para este evento.
              button.checkout-btn.disabled(v-else type="button" disabled) Pago próximamente
            router-link.checkout-btn.success-btn(v-else :to="`/c/${event.friendlyId}`") Entrar al evento
          p.error(v-if="actionError") {{ actionError }}
      aside.payment-panel(v-if="!isFree")
        .payment-modal
          .payment-heading
            span Pago del boleto
            small Próximamente
          .payment-options
            button(type="button" disabled) PayPal
            button(type="button" disabled) Apple Pay
            button(type="button" disabled) Google Pay
          .separator
            span
            p Métodos de pago
            span
          .payment-fields
            label Nombre completo
            input.input-field(type="text" placeholder="Se habilitará con el proveedor" disabled)
            label Tarjeta
            input.input-field(type="text" placeholder="0000 0000 0000 0000" disabled)
            .split-fields
              div
                label Vencimiento
                input.input-field(type="text" placeholder="MM/AA" disabled)
              div
                label CVV
                input.input-field(type="text" placeholder="CVV" disabled)
          p.payment-note La integración de pagos todavía no está habilitada. No se solicitarán ni almacenarán datos bancarios.
      aside.free-panel(v-else)
        .free-panel-icon 🎟️
        h2 Acceso gratuito
        p Este evento no tiene costo. Al confirmar, el boleto quedará ligado a tu cuenta y podrás entrar al evento.
        .security-note 🔒 No se solicitarán datos de pago.
  .state(v-else-if="loading") Cargando acceso al evento...
  .state.error(v-else) {{ error }}
</template>

<script lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/app/layout/AppHeader.vue'
import { getPublicConference, requestPublicTicket } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import type { PublicConference } from '@/services/api/types'

export default {
  name: 'PublicTicketCheckoutPage',
  components: { AppHeader },
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
    const isAuthenticated = computed(() => auth.isAuthenticated() && auth.state.role !== 'guest')
    const isFree = computed(() => Number(event.value?.ticketPrice || 0) === 0)
    const formattedPrice = computed(() => isFree.value
      ? 'Gratis'
      : `${event.value?.ticketPrice || '0.00'} ${event.value?.ticketCurrency || 'MXN'}`)

    onMounted(async () => {
      try {
        event.value = await getPublicConference(route.params.friendlyId as string)
      } catch {
        error.value = 'No se encontró el evento público.'
      } finally {
        loading.value = false
      }
    })

    async function confirmFreeTicket() {
      if (!event.value || !isAuthenticated.value || !auth.state.token) return
      submitting.value = true
      actionError.value = ''
      try {
        await requestPublicTicket(event.value.friendlyId, auth.state.token)
        success.value = true
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        if (code === 'capacity_exceeded') actionError.value = 'Ya no quedan lugares disponibles para este evento.'
        else if (code === 'payment_required') actionError.value = 'Este evento requiere pago y todavía no hay un proveedor habilitado.'
        else if (code === 'login_required' || code === 'token_missing') {
          await router.push({ path: '/login', query: { redirect: `/events/${event.value.friendlyId}/checkout` } })
          return
        } else actionError.value = e.response?.data?.error?.message || 'No se pudo confirmar el boleto.'
      } finally {
        submitting.value = false
      }
    }

    return { event, loading, error, actionError, submitting, success, isAuthenticated, isFree, formattedPrice, confirmFreeTicket }
  }
}
</script>

<style scoped>
.checkout-page { min-height: 100vh; background: #f5f3ff; }
.checkout-main { max-width: 980px; margin: 0 auto; padding: 42px 24px 76px; }
.back { color: #4f46e5; font-weight: 700; text-decoration: none; }
.checkout-layout { display: grid; grid-template-columns: minmax(360px, 420px) minmax(300px, 1fr); gap: 28px; margin-top: 25px; align-items: start; }
.master-container { display: grid; gap: 5px; }
.card, .payment-modal, .free-panel { background: #fff; box-shadow: 0 22px 34px rgba(30, 27, 75, .09), 0 4px 12px rgba(30, 27, 75, .07); }
.card { width: 100%; }
.title { display: flex; align-items: center; min-height: 42px; padding: 0 20px; border-bottom: 1px solid #efeff3; font-size: 12px; font-weight: 800; color: #63656b; }
.cart { border-radius: 19px 19px 7px 7px; }
.products { padding: 14px; }
.product { display: grid; grid-template-columns: 60px minmax(0, 1fr) 34px auto; gap: 10px; align-items: center; }
.product-art { width: 60px; height: 60px; display: grid; place-items: center; overflow: hidden; border-radius: 9px; background: #e0e7ff; color: #4f46e5; font-size: 27px; }
.event-image { width: 100%; height: 100%; object-fit: cover; }
.product-copy { min-width: 0; }.product-copy strong { display: block; overflow: hidden; color: #47484b; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.product-copy p { overflow: hidden; margin: 4px 0 0; color: #7a7c81; font-size: 11px; font-weight: 600; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.quantity { display: grid; place-items: center; width: 32px; height: 30px; border: 1px solid #e5e5e5; border-radius: 7px; color: #47484b; font-size: 13px; font-weight: 700; }
.product-price { color: #2b2b2f; font-size: 14px; font-weight: 900; white-space: nowrap; }
.coupons { border-radius: 7px; }.coupon-form { display: grid; grid-template-columns: 1fr 90px; gap: 10px; padding: 12px; }.input-field { width: 100%; height: 38px; box-sizing: border-box; padding: 0 12px; border: 1px solid #e5e5e5; border-radius: 7px; background: #f7f7f8; color: #47484b; outline: none; }.input-field:focus { border-color: #4f46e5; box-shadow: 0 0 0 2px rgba(79, 70, 229, .16); }.coupon-form button { height: 38px; border: 0; border-radius: 7px; background: linear-gradient(180deg, #4480ff 0%, #115dfc 50%, #0550ed 100%); color: #fff; font-size: 12px; font-weight: 700; }.coupon-form button:disabled, .payment-options button:disabled { cursor: not-allowed; opacity: .55; }
.checkout { border-radius: 9px 9px 19px 19px; }.details { display: grid; grid-template-columns: 3fr 1fr; gap: 6px; padding: 14px 16px; }.details span:nth-child(odd) { color: #707175; font-size: 11px; font-weight: 700; }.details span:nth-child(even) { color: #47484b; font-size: 13px; font-weight: 700; text-align: right; white-space: nowrap; }.checkout-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 12px 12px 20px; background: #efeff3; }.total { display: flex; flex-direction: column; gap: 2px; }.total small { color: #5f5d6b; font-size: 11px; }.total strong { color: #2b2b2f; font-size: 22px; font-weight: 900; white-space: nowrap; }.checkout-btn { display: inline-flex; align-items: center; justify-content: center; min-width: 142px; height: 38px; padding: 0 15px; border: 0; border-radius: 7px; background: linear-gradient(180deg, #4480ff 0%, #115dfc 50%, #0550ed 100%); color: #fff; font-size: 13px; font-weight: 700; text-decoration: none; cursor: pointer; }.checkout-btn:disabled, .checkout-btn.disabled { cursor: not-allowed; opacity: .5; }.success-btn { background: linear-gradient(180deg, #22c55e, #15803d); }.error { margin: 0; padding: 0 16px 13px; color: #b91c1c; font-size: 12px; font-weight: 700; }
.payment-panel { min-width: 0; }.payment-modal { max-width: 450px; border-radius: 26px; padding: 20px; }.payment-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; color: #1e1b4b; font-size: 18px; font-weight: 800; }.payment-heading small { color: #9a3412; font-size: 11px; }.payment-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0; }.payment-options button { height: 50px; border: 0; border-radius: 11px; background: #f2f2f2; color: #63656b; font-size: 11px; font-weight: 700; }.separator { display: grid; grid-template-columns: 1fr auto 1fr; gap: 10px; align-items: center; margin: 10px 0 18px; color: #8b8e98; }.separator span { height: 1px; background: #e8e8e8; }.separator p { margin: 0; font-size: 11px; font-weight: 600; }.payment-fields { display: flex; flex-direction: column; gap: 6px; }.payment-fields label, .split-fields label { color: #8b8e98; font-size: 10px; font-weight: 700; }.payment-fields .input-field { height: 40px; margin-bottom: 8px; }.split-fields { display: grid; grid-template-columns: 4fr 2fr; gap: 15px; }.split-fields > div { display: flex; flex-direction: column; gap: 6px; }.payment-note, .free-panel p { color: #6b7280; font-size: 12px; line-height: 1.5; }.payment-note { margin: 8px 0 0; }
.free-panel { min-height: 220px; border-radius: 20px; padding: 30px; }.free-panel-icon { display: grid; place-items: center; width: 62px; height: 62px; margin-bottom: 16px; border-radius: 12px; background: #e0e7ff; font-size: 30px; }.free-panel h2 { margin: 0; color: #1e1b4b; }.free-panel p { margin: 10px 0 20px; }.security-note { padding: 12px; border-radius: 9px; background: #ecfdf5; color: #166534; font-size: 12px; font-weight: 700; }
.state { max-width: 980px; margin: 50px auto; padding: 30px; color: #6b7280; }.state.error { color: #b91c1c; }
@media (max-width: 760px) { .checkout-main { padding: 28px 16px 55px; }.checkout-layout { grid-template-columns: 1fr; }.payment-modal { max-width: none; }.master-container { max-width: 440px; } }
@media (max-width: 430px) { .product { grid-template-columns: 52px minmax(0, 1fr) 28px; }.product-art { width: 52px; height: 52px; }.product-price { grid-column: 2 / -1; grid-row: 2; text-align: right; }.checkout-footer { align-items: stretch; flex-direction: column; padding: 14px; }.checkout-btn { width: 100%; }.coupon-form { grid-template-columns: 1fr; } }
</style>
