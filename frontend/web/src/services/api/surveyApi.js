import axios from 'axios'

const BASE = '/api/survey/api/v1'

function authHeader(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function getQuestions(conferenceId, onlyActive = true) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/questions`, {
    params: { onlyActive }
  })
  return res.data
}

export async function createQuestion(conferenceId, { text, type, options, orderIndex }, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions`,
    { text, type, options, orderIndex },
    { headers: authHeader(token) })
  return res.data
}

export async function deactivateQuestion(conferenceId, questionId, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/deactivate`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function submitResponses(conferenceId, answers) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/responses`, { answers })
  return res.data
}

export async function getResults(conferenceId, token) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/results`, {
    headers: authHeader(token)
  })
  return res.data
}
