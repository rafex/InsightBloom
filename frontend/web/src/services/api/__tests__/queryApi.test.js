import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'
import { getDoubtCloud, getTopicCloud, getWordTimeline, streamDoubtCloud, streamTopicCloud } from '../queryApi'

vi.mock('axios')

const BASE = '/api/query/api/v1'

class FakeEventSource {
  constructor(url) {
    this.url = url
    this.listeners = {}
  }
  addEventListener(event, cb) {
    this.listeners[event] = cb
  }
  emit(event, data) {
    this.listeners[event]?.({ data: JSON.stringify(data) })
  }
  close() {}
}

describe('queryApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('EventSource', FakeEventSource)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getDoubtCloud / getTopicCloud unwrap .data.data', async () => {
    axios.get.mockResolvedValue({ data: { data: [{ word: 'kubernetes', count: 3 }] } })
    expect(await getDoubtCloud('c1')).toEqual([{ word: 'kubernetes', count: 3 }])
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/cloud/doubts`)

    axios.get.mockResolvedValue({ data: { data: [{ word: 'nats', count: 1 }] } })
    expect(await getTopicCloud('c1')).toEqual([{ word: 'nats', count: 1 }])
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/cloud/topics`)
  })

  it('getWordTimeline URL-encodes the word and forwards the type as a query param', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await getWordTimeline('c1', 'ci/cd', 'doubt')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/words/ci%2Fcd/timeline`, { params: { type: 'doubt' } })
  })

  it('streamDoubtCloud opens an EventSource on the doubts stream endpoint and dispatches snapshot/update', () => {
    const onSnapshot = vi.fn()
    const onUpdate = vi.fn()
    const es = streamDoubtCloud('c1', onSnapshot, onUpdate)
    expect(es.url).toBe(`${BASE}/conferences/c1/cloud/doubts/stream`)

    es.emit('snapshot', [{ word: 'a', count: 1 }])
    expect(onSnapshot).toHaveBeenCalledWith([{ word: 'a', count: 1 }])

    es.emit('update', { word: 'a', count: 2 })
    expect(onUpdate).toHaveBeenCalledWith({ word: 'a', count: 2 })
  })

  it('streamTopicCloud opens an EventSource on the topics stream endpoint', () => {
    const es = streamTopicCloud('c1', vi.fn(), vi.fn())
    expect(es.url).toBe(`${BASE}/conferences/c1/cloud/topics/stream`)
  })
})
