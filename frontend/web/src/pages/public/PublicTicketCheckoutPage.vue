<template lang="pug">
.checkout-page
  AppHeader
  main.checkout-main(v-if="event")
    router-link.back(:to="`/events/${event.friendlyId}`") ← Volver al detalle
    .checkout-layout
      section.checkout-card
        .eyebrow InsightBloom · acceso al evento
        h1 {{ Number(event.ticketPrice || 0) > 0 ? 'Comprar boleto' : 'Solicitar boleto' }}
        .event-summary
          img.event-image(v-if="event.flyerBase64" :src="event.flyerBase64" alt="")
          .event-summary-copy
            h2 {{ event.name }}
            p(v-if="event.eventDate") {{ event.eventDate }}{{ event.venue ? ` · ${event.venue}` : '' }}
            p(v-if="event.description") {{ event.description }}
        .order-line
          span Boleto de acceso
          strong {{ Number(event.ticketPrice || 0) > 0 ? `${event.ticketPrice} ${event.ticketCurrency || 'MXN'}` : 'Gratis' }}
        .free-note(v-if="isFree")
          strong Sin cobro
          span Este evento es gratuito. Confirmaremos tu solicitud y ligaremos el boleto a tu cuenta.
        .payment-slot(v-else)
          strong Pago pendiente de integración
          p La pantalla ya separa el pedido del pago. Cuando habilitemos el proveedor, aquí aparecerán sus métodos y confirmación; por ahora no se realizará ningún cargo ni se emitirá el boleto.
        .checkout-actions(v-if="!success")
          router-link.btn-outline(v-if="!isAuthenticated" :to="{ path: '/login', query: { redirect: `/events/${event.friendlyId}/checkout` } }") Inicia sesión para continuar
          button.btn-primary(v-else-if="isFree" type="button" :disabled="submitting" @click="confirmFreeTicket")
            span(v-if="submitting") Procesando...
            span(v-else) 🎟️ Confirmar solicitud gratuita
          button.btn-primary.disabled(v-else type="button" disabled) 💳 Pago próximamente
        .success-panel(v-else)
          strong ✅ Boleto confirmado
          p Tu boleto gratuito ya está ligado a tu cuenta.
          router-link.btn-primary(:to="`/c/${event.friendlyId}`") Entrar al evento
        p.error(v-if="actionError") {{ actionError }}
      aside.order-summary
        h2 Resumen
        .summary-row
          span Evento
          strong {{ event.name }}
        .summary-row
          span Modalidad
          strong {{ event.visibility === 'HYBRID' ? 'Híbrido' : 'Público' }}
        .summary-row.total
          span Total
          strong {{ Number(event.ticketPrice || 0) > 0 ? `${event.ticketPrice} ${event.ticketCurrency || 'MXN'}` : '0.00' }}
        p.summary-note(v-if="isFree") No se solicitarán datos de pago.
        p.summary-note(v-else) El pago estará disponible cuando se conecte un proveedor autorizado.
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

    return { event, loading, error, actionError, submitting, success, isAuthenticated, isFree, confirmFreeTicket }
  }
}
</script>

<style scoped>
.checkout-page { min-height: 100vh; background: #f5f3ff; }
.checkout-main { max-width: 1060px; margin: 0 auto; padding: 42px 24px 76px; }
.back { color: #4f46e5; font-weight: 700; text-decoration: none; }
.checkout-layout { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(260px, .65fr); gap: 22px; margin-top: 25px; align-items: start; }
.checkout-card, .order-summary { background: #fff; border-radius: 18px; box-shadow: 0 8px 28px rgba(30,27,75,.1); padding: 30px; }
.eyebrow { color: #4f46e5; font-size: .78rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
h1 { color: #1e1b4b; font-size: clamp(2rem, 5vw, 3.2rem); margin: 8px 0 25px; }
h2 { color: #1e1b4b; margin: 0; }
.event-summary { display: flex; gap: 16px; align-items: center; padding: 16px; border: 1px solid #e5e7eb; border-radius: 14px; }
.event-image { width: 92px; height: 76px; object-fit: cover; border-radius: 10px; background: #e0e7ff; }
.event-summary-copy { min-width: 0; }.event-summary-copy h2 { font-size: 1.2rem; }.event-summary-copy p { color: #6b7280; line-height: 1.45; margin: 5px 0 0; }
.order-line, .summary-row { display: flex; justify-content: space-between; gap: 14px; align-items: center; border-bottom: 1px solid #eef0f5; padding: 17px 0; color: #6b7280; }.order-line strong, .summary-row strong { color: #1e1b4b; }.summary-row.total { border-bottom: 0; font-size: 1.2rem; }.summary-row.total strong { color: #4f46e5; }
.free-note, .payment-slot { margin: 20px 0; padding: 16px; border-radius: 12px; background: #ecfdf5; color: #166534; display: flex; flex-direction: column; gap: 5px; }.payment-slot { background: #fff7ed; color: #9a3412; }.payment-slot p { margin: 0; line-height: 1.5; font-size: .9rem; }
.checkout-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 22px; }.btn-primary, .btn-outline { display: inline-flex; justify-content: center; align-items: center; border-radius: 9px; padding: 12px 16px; font-weight: 700; text-decoration: none; cursor: pointer; }.btn-primary { border: 0; background: #4f46e5; color: #fff; }.btn-outline { border: 1px solid #4f46e5; color: #4f46e5; background: #fff; }.btn-primary:disabled, .btn-primary.disabled { opacity: .55; cursor: not-allowed; }
.success-panel { display: flex; flex-direction: column; gap: 10px; color: #166534; margin-top: 22px; }.success-panel p { margin: 0; }.success-panel .btn-primary { align-self: flex-start; }.summary-note { color: #6b7280; line-height: 1.45; font-size: .9rem; }.error { color: #b91c1c; margin-bottom: 0; }.state { max-width: 1060px; margin: 50px auto; padding: 30px; color: #6b7280; }.state.error { color: #b91c1c; }
@media (max-width: 720px) { .checkout-main { padding: 28px 16px 55px; }.checkout-layout { grid-template-columns: 1fr; }.checkout-card, .order-summary { padding: 22px; } }
</style>
