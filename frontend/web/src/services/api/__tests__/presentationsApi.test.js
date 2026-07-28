import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'
import {
  uploadPresentation, getPresentationStatus, getSlidesUrl, getPresentationRootUrl, getPresenterSlidesUrl, getSlidesPreviewUrl, getPdfUrl,
  getAudienceWsUrl, getPresenterWsUrl, getRemoteWsUrl, createRemoteLinkToken
} from '../presentationsApi'

vi.mock('axios')

const BASE = '/api/presentations/api/v1'

describe('presentationsApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('uploadPresentation sends the file as multipart/form-data with auth header', async () => {
    axios.post.mockResolvedValue({ data: { ok: true } })
    const file = new Blob(['fake'], { type: 'application/zip' })
    await uploadPresentation('c1', file, 'tok', 'SLIDEV')
    const [url, form, config] = axios.post.mock.calls[0]
    expect(url).toBe(`${BASE}/conferences/c1/presentation`)
    expect(form).toBeInstanceOf(FormData)
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
    expect(config.headers.Authorization).toBe('Bearer tok')
    expect(form.get('presentationProvider')).toBe('SLIDEV')
  })

  it('getPresentationStatus is a public GET returning the raw body', async () => {
    axios.get.mockResolvedValue({ data: { ready: true } })
    const result = await getPresentationStatus('c1')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/presentation/status`)
    expect(result).toEqual({ ready: true })
  })

  it('getSlidesUrl / getSlidesPreviewUrl / getPdfUrl build direct asset URLs', () => {
    expect(getSlidesUrl('c1')).toBe(`${BASE}/conferences/c1/presentation/slides`)
    expect(getSlidesUrl('c1', 'a b+c')).toBe(`${BASE}/conferences/c1/presentation/slides`)
    expect(getPresentationRootUrl('c1', 'a b+c')).toBe(`${BASE}/conferences/c1/presentation/`)
    expect(getPresenterSlidesUrl('c1', 'a b+c')).toBe(`${BASE}/conferences/c1/presentation/moderator/presenter`)
    expect(getSlidesPreviewUrl('c1')).toBe(`${BASE}/conferences/c1/presentation/slides/preview`)
    expect(getPdfUrl('c1')).toBe(`${BASE}/conferences/c1/presentation/pdf`)
    expect(getPdfUrl('c1', 'a b+c')).toBe(`${BASE}/conferences/c1/presentation/pdf`)
  })

  describe('websocket URL builders', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('uses wss:// when the page is served over https', () => {
      vi.stubGlobal('window', { location: { protocol: 'https:', host: 'insightbloom.example.com' } })
      expect(getAudienceWsUrl('c1')).toBe(`wss://insightbloom.example.com${BASE}/conferences/c1/presentation/ws/audience`)
    })

    it('uses ws:// when the page is served over plain http (local dev)', () => {
      vi.stubGlobal('window', { location: { protocol: 'http:', host: 'localhost:5173' } })
      expect(getAudienceWsUrl('c1')).toBe(`ws://localhost:5173${BASE}/conferences/c1/presentation/ws/audience`)
    })

    it('presenter URLs do not carry the session token; remote control keeps its short-lived token', () => {
      vi.stubGlobal('window', { location: { protocol: 'https:', host: 'insightbloom.example.com' } })
      expect(getPresenterWsUrl('c1', 'a b+c')).toBe(
        `wss://insightbloom.example.com${BASE}/conferences/c1/presentation/ws/presenter`
      )
      expect(getRemoteWsUrl('c1', 'a b+c')).toBe(
        `wss://insightbloom.example.com${BASE}/conferences/c1/presentation/ws/remote?token=a%20b%2Bc`
      )
    })
  })

  it('createRemoteLinkToken posts with the organizer token and returns the raw .token field', async () => {
    axios.post.mockResolvedValue({ data: { token: 'remote-tok' } })
    const result = await createRemoteLinkToken('c1', 'organizer-tok')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/presentation/remote-token`, {},
      { headers: { Authorization: 'Bearer organizer-tok' } }
    )
    expect(result).toBe('remote-tok')
  })
})
