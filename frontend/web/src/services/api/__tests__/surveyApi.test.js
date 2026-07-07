import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  getQuestions, createQuestion, updateQuestion, suggestQuestions, deactivateQuestion,
  submitResponses, hasResponded, getResults, purgeResponses, improveQuestion, gradeResponses
} from '../surveyApi'

vi.mock('axios')

const BASE = '/api/survey/api/v1'

describe('surveyApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('getQuestions defaults onlyActive to true and is unauthenticated', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await getQuestions('c1')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/survey/questions`, { params: { onlyActive: true } })
  })

  it('getQuestions can request inactive questions too', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await getQuestions('c1', false)
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/conferences/c1/survey/questions`, { params: { onlyActive: false } })
  })

  it('createQuestion forwards the "required" flag (regression risk: default must survive round-trip)', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })
    await createQuestion('c1', {
      text: '¿Qué opinas?', type: 'TEXT', options: null, referenceAnswer: null,
      ratingStyle: null, orderIndex: 1, required: false
    }, 'tok')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/survey/questions`,
      { text: '¿Qué opinas?', type: 'TEXT', options: null, referenceAnswer: null, ratingStyle: null, orderIndex: 1, required: false },
      { headers: { Authorization: 'Bearer tok' } }
    )
  })

  it('updateQuestion posts to the /update sub-route with the full editable field set', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })
    await updateQuestion('c1', 'q1', {
      text: 'Nuevo texto', type: 'MULTIPLE_CHOICE', options: ['a', 'b'],
      referenceAnswer: 'a', ratingStyle: null, orderIndex: 2, required: true
    }, 'tok')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/survey/questions/q1/update`,
      { text: 'Nuevo texto', type: 'MULTIPLE_CHOICE', options: ['a', 'b'], referenceAnswer: 'a', ratingStyle: null, orderIndex: 2, required: true },
      { headers: { Authorization: 'Bearer tok' } }
    )
  })

  it('suggestQuestions / deactivateQuestion / purgeResponses hit their expected endpoints', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })

    await suggestQuestions('c1', 5, 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/survey/questions/suggest`, { count: 5 }, { headers: { Authorization: 'Bearer tok' } })

    await deactivateQuestion('c1', 'q1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/survey/questions/q1/deactivate`, {}, { headers: { Authorization: 'Bearer tok' } })

    await purgeResponses('c1', 'q1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/conferences/c1/survey/questions/q1/responses/purge`, {}, { headers: { Authorization: 'Bearer tok' } })
  })

  it('submitResponses wraps the answers array in a body object', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })
    await submitResponses('c1', [{ questionId: 'q1', value: 'x' }], 'tok')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/survey/responses`,
      { answers: [{ questionId: 'q1', value: 'x' }] },
      { headers: { Authorization: 'Bearer tok' } }
    )
  })

  it('hasResponded unwraps .data.data.responded', async () => {
    axios.get.mockResolvedValue({ data: { data: { responded: true } } })
    expect(await hasResponded('c1', 'tok')).toBe(true)
  })

  it('getResults returns the raw response body (not unwrapped) since callers read .data', async () => {
    axios.get.mockResolvedValue({ data: { data: [{ responseCount: 3 }] } })
    const result = await getResults('c1', 'tok')
    expect(result).toEqual({ data: [{ responseCount: 3 }] })
  })

  it('improveQuestion and gradeResponses forward their specific payload shapes', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })

    await improveQuestion('c1', { text: 't', type: 'TEXT', options: null, referenceAnswer: null }, 'tok')
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/survey/questions/improve`,
      { text: 't', type: 'TEXT', options: null, referenceAnswer: null },
      { headers: { Authorization: 'Bearer tok' } }
    )

    await gradeResponses('c1', ['q1', 'q2'], 'tok', true)
    expect(axios.post).toHaveBeenCalledWith(
      `${BASE}/conferences/c1/survey/grade`,
      { questionUuids: ['q1', 'q2'], regrade: true },
      { headers: { Authorization: 'Bearer tok' } }
    )
  })

  it('gradeResponses defaults regrade to false', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })
    await gradeResponses('c1', ['q1'], 'tok')
    const [, body] = axios.post.mock.calls[0]
    expect(body.regrade).toBe(false)
  })
})
