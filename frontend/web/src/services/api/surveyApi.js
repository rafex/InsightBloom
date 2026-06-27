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

export async function createQuestion(conferenceId, { text, type, options, referenceAnswer, ratingStyle, orderIndex }, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions`,
    { text, type, options, referenceAnswer, ratingStyle, orderIndex },
    { headers: authHeader(token) })
  return res.data
}

export async function updateQuestion(conferenceId, questionId, { text, type, options, referenceAnswer, ratingStyle, orderIndex }, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/update`,
    { text, type, options, referenceAnswer, ratingStyle, orderIndex },
    { headers: authHeader(token) })
  return res.data
}

export async function suggestQuestions(conferenceId, count, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/suggest`,
    { count },
    { headers: authHeader(token) })
  return res.data
}

export async function deactivateQuestion(conferenceId, questionId, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/deactivate`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function submitResponses(conferenceId, answers, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/responses`, { answers }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function hasResponded(conferenceId, token) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/responded`, {
    headers: authHeader(token)
  })
  return res.data.data.responded
}

export async function getResults(conferenceId, token) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/survey/results`, {
    headers: authHeader(token)
  })
  return res.data
}

export async function purgeResponses(conferenceId, questionId, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/${questionId}/responses/purge`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function improveQuestion(conferenceId, { text, type, options, referenceAnswer }, token) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/questions/improve`,
    { text, type, options, referenceAnswer },
    { headers: authHeader(token) })
  return res.data
}

export async function gradeResponses(conferenceId, questionUuids, token, regrade = false) {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/survey/grade`,
    { questionUuids, regrade },
    { headers: authHeader(token) })
  return res.data
}
