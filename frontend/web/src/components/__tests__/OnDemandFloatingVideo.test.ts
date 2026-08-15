import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
vi.mock('../OnDemandVideoPlayer.vue', () => ({
  default: { name: 'OnDemandVideoPlayer', template: '<iframe class="video-frame" />' }
}))

import OnDemandFloatingVideo from '../OnDemandFloatingVideo.vue'
import {
  floatingVideoHeightForWidth,
  sanitizeFloatingVideoGeometry
} from '@/features/conferences/floatingVideoGeometry'

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
      { path: '/c/:friendlyId/survey', component: { template: '<div/>' } },
      { path: '/on-demand-session/:friendlyId', component: { template: '<div/>' } }
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
    localStorage.removeItem('ondemand-floating-conf-1')
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

  it('persists a dragged position and resized dimensions', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    const toolbar = floatingSlot.querySelector<HTMLElement>('.floating-toolbar')
    toolbar!.setPointerCapture = vi.fn()
    toolbar?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 100, clientY: 100, pointerId: 1 }))
    toolbar?.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: 130, clientY: 80, pointerId: 1 }))
    toolbar?.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, clientX: 130, clientY: 80, pointerId: 1 }))

    const resize = floatingSlot.querySelector<HTMLElement>('.resize-handle')
    resize!.setPointerCapture = vi.fn()
    resize?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 100, clientY: 100, pointerId: 2 }))
    resize?.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: 160, clientY: 120, pointerId: 2 }))
    resize?.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, clientX: 160, clientY: 120, pointerId: 2 }))

    const saved = JSON.parse(localStorage.getItem('ondemand-floating-conf-1') || '{}')
    expect(saved.right).toBeLessThan(20)
    expect(saved.bottom).toBeGreaterThan(20)
    expect(saved.width).toBeGreaterThan(320)
    expect(saved.height).toBe(floatingVideoHeightForWidth(saved.width))
  })

  it('terminates an interaction on pointercancel and keeps the latest geometry', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    const toolbar = floatingSlot.querySelector<HTMLElement>('.floating-toolbar')!
    toolbar.setPointerCapture = vi.fn()
    toolbar.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 100, clientY: 100, pointerId: 4 }))
    toolbar.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: 180, clientY: 150, pointerId: 4 }))
    toolbar.dispatchEvent(new PointerEvent('pointercancel', { bubbles: true, clientX: 180, clientY: 150, pointerId: 4 }))

    const saved = JSON.parse(localStorage.getItem('ondemand-floating-conf-1') || '{}')
    expect(saved.right).toBeLessThan(20)
    expect(saved.bottom).toBeLessThan(20)
    toolbar.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: 260, clientY: 200, pointerId: 4 }))
    expect(JSON.parse(localStorage.getItem('ondemand-floating-conf-1') || '{}')).toEqual(saved)
  })

  it('sanitizes corrupted geometry and keeps the video inside the viewport', () => {
    const geometry = sanitizeFloatingVideoGeometry({
      right: Number.NaN,
      bottom: -100,
      width: 99999,
      height: 1
    }, { width: 800, height: 600 })

    expect(geometry.right).toBeGreaterThanOrEqual(8)
    expect(geometry.bottom).toBeGreaterThanOrEqual(8)
    expect(geometry.width).toBeLessThanOrEqual(800 - 16)
    expect(geometry.height).toBe(floatingVideoHeightForWidth(geometry.width))
  })

  it('opens the standalone popup from the widget', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)

    floatingSlot.querySelector<HTMLButtonElement>('.floating-popup')?.click()

    expect(open).toHaveBeenCalledWith(
      '/on-demand-session/evento-demo',
      'insightbloom-on-demand',
      expect.stringContaining('popup=yes')
    )
    open.mockRestore()
  })

  it('hides the widget while the popup is open and restores it when the popup closes', async () => {
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    const popup = { closed: false } as unknown as Window
    const open = vi.spyOn(window, 'open').mockImplementation(() => popup)
    const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    floatingSlot.querySelector<HTMLButtonElement>('.floating-popup')?.click()
    await flushPromises()
    expect(floatingSlot.querySelector('.ondemand-floating-video')).toBeNull()

    window.dispatchEvent(new MessageEvent('message', {
      origin: window.location.origin,
      source: popup,
      data: {
        type: 'insightbloom-on-demand-popup-closed',
        conferenceId: 'conf-1'
      }
    }))
    await flushPromises()

    expect(floatingSlot.querySelector('.ondemand-floating-video')).not.toBeNull()
    expect(floatingSlot.querySelector('.video-frame')).not.toBeNull()
    wrapper.unmount()
    open.mockRestore()
  })

  it('restores the widget if the popup is closed by the browser', async () => {
    vi.useFakeTimers()
    const router = makeRouter('/c/evento-demo/survey')
    await router.isReady()
    const popup = { closed: false } as unknown as Window
    const open = vi.spyOn(window, 'open').mockImplementation(() => popup)
    const wrapper = mount(OnDemandFloatingVideo, { props: baseProps, global: { plugins: [router] } })
    await flushPromises()

    floatingSlot.querySelector<HTMLButtonElement>('.floating-popup')?.click()
    await flushPromises()
    expect(floatingSlot.querySelector('.ondemand-floating-video')).toBeNull()
    ;(popup as unknown as { closed: boolean }).closed = true
    vi.advanceTimersByTime(400)
    await flushPromises()

    expect(floatingSlot.querySelector('.ondemand-floating-video')).not.toBeNull()
    wrapper.unmount()
    open.mockRestore()
    vi.useRealTimers()
  })

})
