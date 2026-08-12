import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import FlyerPage from '../FlyerPage.vue'

// ConferenceMap monta un mapa Leaflet real contra tiles externos -- se stubea para un test
// unitario de FlyerPage; el comportamiento propio del mapa ya tiene su propio componente.
vi.mock('@/components/map/ConferenceMap.vue', () => ({
  default: { name: 'ConferenceMap', props: ['latitude', 'longitude', 'label'], template: '<div class="conference-map-stub" />' }
}))

describe('FlyerPage', () => {
  it('renders the uploaded flyer image when present', () => {
    const wrapper = mount(FlyerPage, {
      props: { eventName: 'Mi Evento', flyerBase64: 'data:image/jpeg;base64,AAAA' }
    })
    expect(wrapper.find('.flyer-image').exists()).toBe(true)
    expect(wrapper.find('.flyer-fallback').exists()).toBe(false)
  })

  it('renders the fallback with logo, venue, date and map when there is no flyer', () => {
    const wrapper = mount(FlyerPage, {
      props: {
        eventName: 'Conferencia IA 2026',
        venue: 'Centro de Convenciones',
        latitude: 19.4326,
        longitude: -99.1332,
        eventDate: '2026-09-15',
        startTime: '09:00',
        endTime: '18:00'
      }
    })
    expect(wrapper.find('.flyer-image').exists()).toBe(false)
    expect(wrapper.find('.flyer-fallback').exists()).toBe(true)
    expect(wrapper.find('.flyer-fallback-logo').attributes('alt')).toBe('InsightBloom')
    expect(wrapper.text()).toContain('Conferencia IA 2026')
    expect(wrapper.text()).toContain('Centro de Convenciones')
    expect(wrapper.text()).toContain('09:00')
    expect(wrapper.find('.conference-map-stub').exists()).toBe(true)
  })

  it('does not render the map when coordinates are missing', () => {
    const wrapper = mount(FlyerPage, {
      props: { eventName: 'Evento sin sede', venue: 'Sede X' }
    })
    expect(wrapper.find('.conference-map-stub').exists()).toBe(false)
  })

  it('falls back to the empty-state message when there is no description either', () => {
    const wrapper = mount(FlyerPage, { props: { eventName: 'Evento pelado' } })
    expect(wrapper.text()).toContain('El organizador todavía no cargó una descripción para este evento.')
  })
})
