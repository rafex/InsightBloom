import { describe, it, expect, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import SurveyManagePage from '../SurveyManagePage.vue'

// Las cargas quedan pendientes a proposito para observar la pagina mientras carga.
const { pending } = vi.hoisted(() => ({ pending: () => new Promise<never>(() => {}) }))

vi.mock('@/services/api/surveyApi', async (original) => ({
  ...(await original<object>()),
  getSurveyAccessManagement: vi.fn(pending)
}))
vi.mock('@/services/api/usersApi', async (original) => ({
  ...(await original<object>()),
  getConference: vi.fn(pending)
}))

describe('SurveyManagePage', () => {
  it('shows the loading state while the survey configuration loads', () => {
    const wrapper = mount(SurveyManagePage, {
      props: { conferenceId: 'c1' },
      global: { stubs: { RouterLink: RouterLinkStub, DashboardBreadcrumb: true, ConferenceToolsNav: true } }
    })

    expect(wrapper.text()).toContain('Cargando configuración de encuesta…')
    wrapper.unmount()
  })
})
