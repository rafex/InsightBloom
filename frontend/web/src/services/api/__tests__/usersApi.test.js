import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'
import {
  createConference,
  updateConference,
  getUniqueRegisteredAttendeesCount,
  getRegisteredAttendeesCount,
  getTimezones,
  setCanvasConfig,
  setCanvasConfigs,
  downloadEventMaterials,
  getEventDiagram,
  saveEventDiagram,
  streamEventDiagram
} from '../usersApi'

vi.mock('axios')

class FakeEventSource {
  constructor(url) { this.url = url }
  close() {}
}

describe('usersApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('EventSource', FakeEventSource)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('createConference', () => {
    it('only sends optional fields when they are actually provided', async () => {
      axios.post.mockResolvedValue({ data: { data: { uuid: 'c1' } } })
      await createConference('Mi charla', null, 'tok', null, null, null, null, null, null, null, null)
      const [, body] = axios.post.mock.calls[0]
      expect(body).toEqual({ name: 'Mi charla' })
    })

    it('forwards timezoneId when provided (regression: was silently dropped before)', async () => {
      axios.post.mockResolvedValue({ data: { data: { uuid: 'c1' } } })
      await createConference('Mi charla', null, 'tok', 19.4, -99.1, '2026-08-01', 'Auditorio', '09:00', '10:00', 'Display', 3)
      const [, body] = axios.post.mock.calls[0]
      expect(body).toMatchObject({
        name: 'Mi charla',
        latitude: 19.4,
        longitude: -99.1,
        eventDate: '2026-08-01',
        venue: 'Auditorio',
        startTime: '09:00',
        endTime: '10:00',
        displayName: 'Display',
        timezoneId: 3
      })
    })
  })

  describe('updateConference', () => {
    it('forwards flyerBase64 and timezoneId in the PUT body (regression: flyerBase64 used to be silently dropped)', async () => {
      axios.put.mockResolvedValue({ data: { data: { uuid: 'c1' } } })
      await updateConference('c1', {
        displayName: 'Display',
        venue: 'Auditorio',
        eventDate: '2026-08-01',
        startTime: '09:00',
        endTime: '10:00',
        latitude: 19.4,
        longitude: -99.1,
        presentationSourceUrl: 'https://example.com',
        flyerBase64: 'data:image/png;base64,AAAA',
        timezoneId: 3
      }, 'tok')

      const [url, body, config] = axios.put.mock.calls[0]
      expect(url).toBe('/api/users/api/v1/conferences/c1')
      expect(body.flyerBase64).toBe('data:image/png;base64,AAAA')
      expect(body.timezoneId).toBe(3)
      expect(config.headers.Authorization).toBe('Bearer tok')
    })
  })

  describe('getUniqueRegisteredAttendeesCount', () => {
    it('hits the deduplicated summary endpoint, not the per-conference count', async () => {
      axios.get.mockResolvedValue({ data: { data: { uniqueRegisteredAttendees: 42 } } })
      const count = await getUniqueRegisteredAttendeesCount('tok')
      expect(axios.get).toHaveBeenCalledWith(
        '/api/users/api/v1/conferences/attendees/registered-summary',
        { headers: { Authorization: 'Bearer tok' } }
      )
      expect(count).toBe(42)
    })
  })

  describe('getRegisteredAttendeesCount', () => {
    it('reads the per-conference registered field, not the raw attendee count', async () => {
      axios.get.mockResolvedValue({ data: { data: { count: 10, registered: 4 } } })
      const registered = await getRegisteredAttendeesCount('conf-1', 'tok')
      expect(registered).toBe(4)
    })
  })

  describe('getTimezones', () => {
    it('calls the public catalog endpoint without an Authorization header', async () => {
      axios.get.mockResolvedValue({ data: { data: [{ id: 1, isDefault: true }] } })
      const list = await getTimezones()
      expect(axios.get).toHaveBeenCalledWith('/api/users/api/v1/timezones')
      expect(list).toEqual([{ id: 1, isDefault: true }])
    })
  })

  describe('setCanvasConfig', () => {
    it('sends the selected tool and audience mode to the event configuration endpoint', async () => {
      axios.put.mockResolvedValue({ data: { data: { uuid: 'c1', canvasTool: 'DRAWIO' } } })
      await setCanvasConfig('c1', 'DRAWIO', 'MODERATOR_ONLY', 'tok')

      expect(axios.put).toHaveBeenCalledWith(
        '/api/users/api/v1/conferences/c1/canvas-config',
        { canvasTool: 'DRAWIO', canvasAudienceMode: 'MODERATOR_ONLY' },
        { headers: { Authorization: 'Bearer tok' } }
      )
    })

    it('sends one audience mode per selected tool', async () => {
      axios.put.mockResolvedValue({ data: { data: { uuid: 'c1' } } })
      const canvasConfigs = [
        { tool: 'DRAWIO', audienceMode: 'MODERATOR_ONLY' },
        { tool: 'EXCALIDRAW', audienceMode: 'MODERATOR_ONLY' },
        { tool: 'ETHERPAD', audienceMode: 'INDEPENDENT' }
      ]
      await setCanvasConfigs('c1', canvasConfigs, 'tok')

      expect(axios.put).toHaveBeenCalledWith(
        '/api/users/api/v1/conferences/c1/canvas-config',
        { canvasConfigs },
        { headers: { Authorization: 'Bearer tok' } }
      )
    })
  })

  describe('moderator diagram publication', () => {
    it('reads and sends the published SVG together with the native XML', async () => {
      axios.get.mockResolvedValue({ data: { data: {
        xml: '<mxGraphModel/>', publishedSvg: 'data:image/svg+xml;base64,AAA', version: 4
      } } })
      axios.put.mockResolvedValue({ data: { data: { saved: true } } })

      const diagram = await getEventDiagram('c1', 'tok')
      await saveEventDiagram('c1', diagram.xml, 'tok', diagram.publishedSvg)

      expect(diagram.version).toBe(4)
      expect(axios.put).toHaveBeenCalledWith(
        '/api/users/api/v1/conferences/c1/diagram',
        { xml: '<mxGraphModel/>', publishedSvg: 'data:image/svg+xml;base64,AAA' },
        { headers: { Authorization: 'Bearer tok' } }
      )
    })

    it('opens the authenticated SSE stream without putting the token in the URL', () => {
      const stream = streamEventDiagram('c 1', 'token/1')
      expect(stream.url).toBe('/api/users/api/v1/conferences/c 1/diagram/stream')
    })
  })

  describe('event materials', () => {
    it('downloads the ZIP through the authenticated users endpoint', async () => {
      const blob = new Blob(['zip'])
      axios.get.mockResolvedValue({ data: blob })

      const result = await downloadEventMaterials('c1', 'tok')

      expect(result).toBe(blob)
      expect(axios.get).toHaveBeenCalledWith(
        '/api/users/api/v1/conferences/c1/materials.zip',
        { headers: { Authorization: 'Bearer tok' }, responseType: 'blob' }
      )
    })
  })
})
