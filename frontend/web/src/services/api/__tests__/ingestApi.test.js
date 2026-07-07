import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { sendMessage } from '../ingestApi'

vi.mock('axios')

describe('ingestApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers().setSystemTime(new Date('2026-07-07T12:00:00Z'))
  })

  it('builds the message envelope with author/device/message/source and a receivedAt timestamp', async () => {
    axios.post.mockResolvedValue({ data: { data: { uuid: 'm1' } } })
    await sendMessage({
      conferenceId: 'c1', authorUuid: 'u1', authorKind: 'user', displayName: 'Ana',
      deviceFingerprint: 'fp1', type: 'doubt', word: 'kubernetes', detail: '¿cómo escala?', token: 'tok'
    })
    const [url, body, config] = axios.post.mock.calls[0]
    expect(url).toBe('/api/ingest/api/v1/messages')
    expect(body).toEqual({
      conferenceId: 'c1',
      author: { userId: 'u1', kind: 'user', displayName: 'Ana' },
      device: { fingerprint: 'fp1' },
      message: { type: 'doubt', word: 'kubernetes', detail: '¿cómo escala?' },
      source: { type: 'rest' },
      receivedAt: '2026-07-07T12:00:00.000Z'
    })
    expect(config.headers.Authorization).toBe('Bearer tok')
  })

  it('defaults authorKind to "user" when not provided', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })
    await sendMessage({ conferenceId: 'c1', type: 'topic', word: 'nats' })
    const [, body] = axios.post.mock.calls[0]
    expect(body.author.kind).toBe('user')
  })

  it('sends no Authorization header when token is missing', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })
    await sendMessage({ conferenceId: 'c1', type: 'topic', word: 'nats' })
    const [, , config] = axios.post.mock.calls[0]
    expect(config.headers).toEqual({})
  })
})
