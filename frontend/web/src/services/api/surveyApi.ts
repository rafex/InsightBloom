import axios from 'axios'

const BASE = '/api/survey/api/v1'

function authHeader(token?: string | null) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export interface SurveyQuestionInput {
  text: string
  type: string
  options?: string[] | null
  referenceAnswer?: string | null
  ratingStyle?: string | null
  orderIndex?: number
  required?: boolean
}

export async function getQuestions(conferenceId: string, onlyActive = true): Promise<{ data: any[] }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/questions`, {
    params: { onlyActive }
  })
  return res.data
}

export async function createQuestion(conferenceId: string, question: SurveyQuestionInput, token: string): Promise<unknown> {
  const { text, type, options, referenceAnswer, ratingStyle, orderIndex, required } = question
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions`,
    { text, type, options, referenceAnswer, ratingStyle, orderIndex, required },
    { headers: authHeader(token) })
  return res.data
}

export async function updateQuestion(conferenceId: string, questionId: string, question: SurveyQuestionInput, token: string): Promise<unknown> {
  const { text, type, options, referenceAnswer, ratingStyle, orderIndex, required } = question
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/update`,
    { text, type, options, referenceAnswer, ratingStyle, orderIndex, required },
    { headers: authHeader(token) })
  return res.data
}

export async function suggestQuestions(conferenceId: string, count: number, token: string): Promise<{ data: any[] }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/suggest`,
    { count },
    { headers: authHeader(token) })
  return res.data
}

export async function deactivateQuestion(conferenceId: string, questionId: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/deactivate`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function submitResponses(conferenceId: string, answers: unknown[], token?: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/responses`, { answers }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function hasResponded(conferenceId: string, token: string, userUuid?: string): Promise<boolean> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/responded`, {
    params: userUuid ? { userUuid } : undefined,
    headers: authHeader(token)
  })
  return res.data.data.responded
}

export async function getResults(conferenceId: string, token: string): Promise<{ data: any[] }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/results`, {
    headers: authHeader(token)
  })
  return res.data
}

export async function purgeResponses(conferenceId: string, questionId: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/responses/purge`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function improveQuestion(
  conferenceId: string,
  { text, type, options, referenceAnswer }: Pick<SurveyQuestionInput, 'text' | 'type' | 'options' | 'referenceAnswer'>,
  token: string
): Promise<{ data: any }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/improve`,
    { text, type, options, referenceAnswer },
    { headers: authHeader(token) })
  return res.data
}

export async function gradeResponses(conferenceId: string, questionUuids: string[], token: string, regrade = false): Promise<{ data: { graded?: number, skipped?: number } }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/grade`,
    { questionUuids, regrade },
    { headers: authHeader(token) })
  return res.data
}
