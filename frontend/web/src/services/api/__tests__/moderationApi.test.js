import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  getModerationWords, getModerationMessages, censorWord, restoreWord,
  censorMessage, restoreMessage, editWord, editMessage, deleteWord, deleteMessage,
  answerMessage, getMessageAnswer
} from '../moderationApi'

vi.mock('axios')

const BASE = '/api/moderation/api/v1'

describe('moderationApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('getModerationWords sends pagination + optional status filter', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await getModerationWords('c1', 2, 25, 'censored', 'tok')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/words`, {
      params: { page: 2, pageSize: 25, status: 'censored' },
      headers: { Authorization: 'Bearer tok' }
    })
  })

  it('getModerationWords omits the status param when not provided', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await getModerationWords('c1', 1, 50, '', 'tok')
    const [, config] = axios.get.mock.calls[0]
    expect(config.params).toEqual({ page: 1, pageSize: 50 })
  })

  it('getModerationMessages sends pagination + optional status filter', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await getModerationMessages('c1', 1, 50, 'pending', 'tok')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/messages`, {
      params: { page: 1, pageSize: 50, status: 'pending' },
      headers: { Authorization: 'Bearer tok' }
    })
  })

  it('censorWord / restoreWord hit the per-conference word action endpoints', async () => {
    axios.post.mockResolvedValue({ data: {} })
    await censorWord('w1', 'spam', 'tok', 'c1')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/words/w1/censor`, { reason: 'spam' }, { headers: { Authorization: 'Bearer tok' } })

    await restoreWord('w1', 'tok', 'c1')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/words/w1/restore`, {}, { headers: { Authorization: 'Bearer tok' } })
  })

  it('censorMessage defaults target to "detail" and includes conferenceUuid/wordText/detailText', async () => {
    axios.post.mockResolvedValue({ data: {} })
    await censorMessage('m1', 'spam', undefined, 'tok', 'c1', 'kubernetes', '¿cómo escala?')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/moderation/messages/m1/censor`,
      { reason: 'spam', target: 'detail', conferenceUuid: 'c1', wordText: 'kubernetes', detailText: '¿cómo escala?' },
      { headers: { Authorization: 'Bearer tok' } }
    )
  })

  it('restoreMessage / editWord / editMessage / deleteWord / deleteMessage hit expected endpoints', async () => {
    axios.post.mockResolvedValue({ data: {} })
    axios.patch.mockResolvedValue({ data: {} })

    await restoreMessage('m1', 'tok', 'c1')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/messages/m1/restore`, {}, { headers: { Authorization: 'Bearer tok' } })

    await editWord('w1', 'nuevo valor', 'tok', 'c1')
    expect(axios.patch).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/words/w1`, { value: 'nuevo valor' }, { headers: { Authorization: 'Bearer tok' } })

    await editMessage('m1', 'palabra', 'detalle', 'tok', 'c1')
    expect(axios.patch).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/messages/m1`, { editedWord: 'palabra', editedDetail: 'detalle' }, { headers: { Authorization: 'Bearer tok' } })

    await deleteWord('w1', 'tok', 'c1')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/words/w1/delete`, {}, { headers: { Authorization: 'Bearer tok' } })

    await deleteMessage('m1', 'tok', 'c1')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/messages/m1/delete`, {}, { headers: { Authorization: 'Bearer tok' } })
  })

  it('answerMessage posts answerText + answeredByUserUuid', async () => {
    axios.post.mockResolvedValue({ data: {} })
    await answerMessage('m1', 'Aquí la respuesta', 'u1', 'tok', 'c1')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/moderation/messages/m1/answer`,
      { answerText: 'Aquí la respuesta', answeredByUserUuid: 'u1' },
      { headers: { Authorization: 'Bearer tok' } }
    )
  })

  it('getMessageAnswer is a public GET (no auth header) that unwraps .data.data', async () => {
    axios.get.mockResolvedValue({ data: { data: { answerText: 'hola' } } })
    const result = await getMessageAnswer('m1', 'c1')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/moderation/messages/m1/answer`)
    expect(result).toEqual({ answerText: 'hola' })
  })
})
