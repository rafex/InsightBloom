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

export type SurveyEngine = 'NATIVE' | 'SURVEYJS'

export interface SurveyDefinition {
  configured: boolean
  engine: SurveyEngine | null
  uuid?: string
  schema?: Record<string, unknown> | null
  schemaVersion?: number
  status?: string
  updatedAt?: string
  publishedAt?: string | null
}

export interface SurveyAccessStatus {
  released: boolean
  releasedForAll: boolean
  responded: boolean
  published: boolean
}

export interface SurveyAttendee {
  uuid: string
  displayName: string
  email: string
  joinedAt: string
  released: boolean
  responded: boolean
}

export interface SurveyAccessManagement {
  releasedForAll: boolean
  attendees: SurveyAttendee[]
}

export interface AiMentorConfig {
  conferenceUuid?: string
  enabled: boolean
  objective: string
  prompt: string
  includePresentation: boolean
  maxRequestsPerMinute: number
  updatedAt?: string
}

export interface AiMentorChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export async function getQuestions(conferenceId: string, onlyActive = true, token?: string | null): Promise<{ data: any[] }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/questions`, {
    params: { onlyActive },
    headers: authHeader(token)
  })
  return res.data
}

export async function getAiMentorConfig(conferenceId: string, token: string): Promise<{ data: AiMentorConfig }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/mentor/config`, {
    headers: authHeader(token)
  })
  return res.data
}

export async function setAiMentorConfig(
  conferenceId: string,
  config: Omit<AiMentorConfig, 'conferenceUuid' | 'updatedAt'>,
  token: string
): Promise<{ data: AiMentorConfig }> {
  const res = await axios.put(`${BASE}/conferences/${conferenceId}/mentor/config`, config, {
    headers: authHeader(token)
  })
  return res.data
}

export async function chatAiMentor(
  conferenceId: string,
  input: { message: string, fileName?: string, codeContext?: string, history?: AiMentorChatMessage[] },
  token: string
): Promise<{ data: { reply: string } }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/mentor/chat`, input, {
    headers: authHeader(token)
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

export async function getSurveyAccess(conferenceId: string, token: string): Promise<{ data: SurveyAccessStatus }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/access`, {
    headers: authHeader(token)
  })
  return res.data
}

export async function getSurveyAccessManagement(conferenceId: string, token: string): Promise<{ data: SurveyAccessManagement }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/access-management`, {
    headers: authHeader(token)
  })
  return res.data
}

export async function releaseSurveyAccess(
  conferenceId: string,
  token: string,
  userUuids: string[] = [],
  all = false
): Promise<{ data: { releasedForAll: boolean, releasedCount: number } }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/access/release`, {
    all,
    userUuids
  }, { headers: authHeader(token) })
  return res.data
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

export async function getSurveyDefinition(conferenceId: string, token?: string | null, draft = false): Promise<{ data: SurveyDefinition }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/definition`, {
    params: draft ? { draft: true } : undefined,
    headers: authHeader(token)
  })
  return res.data
}

export async function selectSurveyEngine(conferenceId: string, engine: SurveyEngine, token: string): Promise<{ data: SurveyDefinition }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/definition/engine`, { engine }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function saveSurveyDefinition(conferenceId: string, schema: Record<string, unknown>, token: string): Promise<{ data: SurveyDefinition }> {
  const res = await axios.put(`${BASE}/conferences/${conferenceId}/survey/definition`, { schema }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function validateSurveyDefinition(conferenceId: string, schema: Record<string, unknown>, token: string): Promise<{ data: { valid: boolean } }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/definition/validate`, { schema }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function publishSurveyDefinition(conferenceId: string, schema: Record<string, unknown>, token: string): Promise<{ data: SurveyDefinition }> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/definition/publish`, { schema }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function submitSurveyJs(conferenceId: string, data: Record<string, unknown>, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/submissions`, { data }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function getSurveyJsSubmissions(conferenceId: string, token: string): Promise<{ data: any[] }> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/submissions`, {
    headers: authHeader(token)
  })
  return res.data
}
