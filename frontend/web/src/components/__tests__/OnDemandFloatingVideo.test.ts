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
})
