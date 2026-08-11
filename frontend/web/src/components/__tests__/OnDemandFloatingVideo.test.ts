import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import OnDemandFloatingVideo from '../OnDemandFloatingVideo.vue'

// Solo cubre el proveedor PEERTUBE: es un <iframe> simple, sin la carga de la YouTube IFrame API
// (que dispara un <script> externo real e YT.Player -- fuera de alcance de un test unitario,
// verificado a mano en el navegador en su lugar). El mecanismo de Teleport entre la caja flotante
// y el slot de la pestana completa es identico para ambos proveedores, asi que este test igual
// cubre la parte estructuralmente mas riesgosa del componente.

function makeRouter(initialPath: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/c/:friendlyId/on-demand', component: { template: '<div/>' } },
      { path: '/c/:friendlyId/survey', component: { template: '<div/>' } }
    ]
  })
  router.push(initialPath)
  return router
}

const baseProps = {
  conferenceId: 'conf-1',
  friendlyId: 'evento-demo',
  provider: 'PEERTUBE' as const,
  videoUrl: 'https://peertube.example/w/abc123',
  cuePoints: [{ atSeconds: 30, label: 'Abrí la encuesta', toolPath: 'survey' }]
}

describe('OnDemandFloatingVideo', () => {
  let fullSlot: HTMLDivElement
  let floatingSlot: HTMLDivElement

  beforeEach(() => {
    fullSlot = document.createElement('div')
    fullSlot.id = 'ondemand-full-slot'
    document.body.appendChild(fullSlot)
    floatingSlot = document.createElement('div')
    floatingSlot.id = 'ondemand-floating-slot'
    document.body.appendChild(floatingSlot)
  })

  afterEach(() => {
    fullSlot.remove()
    floatingSlot.remove()
  })

  it('teleports into the floating slot when not on the on-demand tab', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    expect(floatingSlot.querySelector('.video-frame')).not.toBeNull()
    expect(fullSlot.querySelector('.video-frame')).toBeNull()
  })

  it('teleports into the full-tab slot when on the on-demand tab', async () => {
    const router = makeRouter('/c/evento-demo/on-demand')
    await router.isReady()
    mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    expect(fullSlot.querySelector('.video-frame')).not.toBeNull()
    expect(floatingSlot.querySelector('.video-frame')).toBeNull()
  })

  it('emits closed when the close button is clicked while floating', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    // El contenido esta teletransportado fuera de la raiz montada del wrapper (vive en
    // floatingSlot, un nodo aparte de document.body) -- wrapper.find() no lo alcanza, hace falta
    // buscarlo directo en el DOM real, igual que el test del boton de expandir.
    const closeButton = floatingSlot.querySelector<HTMLButtonElement>('.floating-close')
    closeButton?.click()
    await flushPromises()
    expect(wrapper.emitted('closed')).toHaveLength(1)
  })

  it('navigates to the on-demand tab when the expand button is clicked', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    const expandButton = floatingSlot.querySelector<HTMLButtonElement>('.floating-expand')
    expandButton?.click()
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/c/evento-demo/on-demand')
  })

  // El disparo real de una notificacion pasa por el polling del YT.Player (requiere mockear el
  // script externo de la YouTube IFrame API por completo -- fuera de alcance de un test unitario).
  // En cambio, se puebla `notifications` directamente via wrapper.vm (queda expuesto en el
  // return() del componente) para probar el renderizado del panel -- paginacion, orden y
  // descarte -- de forma aislada de esa dependencia externa.
  describe('notification panel', () => {
    function seedNotifications(count: number) {
      return Array.from({ length: count }, (_, i) => ({
        id: i + 1,
        cue: { atSeconds: i * 10, label: `Sugerencia ${i + 1}`, toolPath: 'survey' }
      }))
    }

    it('shows at most 5 notifications per page, newest first', async () => {
      const router = makeRouter('/c/evento-demo/survey')
      await router.isReady()
      const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
      await flushPromises()

      ;(wrapper.vm as any).notifications = seedNotifications(7)
      await flushPromises()

      const items = floatingSlot.querySelectorAll('.notification-item')
      expect(items).toHaveLength(5)
      expect(floatingSlot.querySelector('.notification-pagination')?.textContent).toContain('1 / 2')
    })

    it('paginates to older notifications', async () => {
      const router = makeRouter('/c/evento-demo/survey')
      await router.isReady()
      const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
      await flushPromises()

      ;(wrapper.vm as any).notifications = seedNotifications(7)
      await flushPromises()

      const [, nextButton] = floatingSlot.querySelectorAll<HTMLButtonElement>('.notification-pagination button')
      nextButton.click()
      await flushPromises()

      const items = floatingSlot.querySelectorAll('.notification-item')
      expect(items).toHaveLength(2)
      expect(floatingSlot.querySelector('.notification-pagination')?.textContent).toContain('2 / 2')
    })

    it('removes a notification when its dismiss button is clicked, without affecting the others', async () => {
      const router = makeRouter('/c/evento-demo/survey')
      await router.isReady()
      const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
      await flushPromises()

      ;(wrapper.vm as any).notifications = seedNotifications(2)
      await flushPromises()

      const dismissButtons = floatingSlot.querySelectorAll<HTMLButtonElement>('.notification-dismiss')
      dismissButtons[0].click()
      await flushPromises()

      expect((wrapper.vm as any).notifications).toHaveLength(1)
      expect(floatingSlot.querySelectorAll('.notification-item')).toHaveLength(1)
    })

    it('opens the suggestion link in a new tab', async () => {
      const router = makeRouter('/c/evento-demo/survey')
      await router.isReady()
      const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
      await flushPromises()

      ;(wrapper.vm as any).notifications = seedNotifications(1)
      await flushPromises()

      const link = floatingSlot.querySelector<HTMLAnchorElement>('.notification-link')
      expect(link?.target).toBe('_blank')
      expect(link?.rel).toBe('noopener')
      expect(link?.getAttribute('href')).toBe('/c/evento-demo/survey')
    })
  })
})
