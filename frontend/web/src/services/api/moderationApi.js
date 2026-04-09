import axios from 'axios'

const BASE = '/api/moderation/api/v1'

function authHeader(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function getModerationWords(conferenceId, page = 1, pageSize = 50, status = '', token) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/moderation/words`, {
    params: { page, pageSize, ...(status ? { status } : {}) },
    headers: authHeader(token)
  })
  return res.data
}

export async function getModerationMessages(conferenceId, page = 1, pageSize = 50, status = '', token) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/moderation/messages`, {
    params: { page, pageSize, ...(status ? { status } : {}) },
    headers: authHeader(token)
  })
  return res.data
}

export async function censorWord(wordId, reason, token) {
  const res = await axios.post(`${BASE}/moderation/words/${wordId}/censor`, { reason }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function restoreWord(wordId, token) {
  const res = await axios.post(`${BASE}/moderation/words/${wordId}/restore`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function censorMessage(messageId, reason, target, token, conferenceId, wordText, detailText) {
  const res = await axios.post(`${BASE}/moderation/messages/${messageId}/censor`,
    { reason, target: target || 'detail', conferenceUuid: conferenceId, wordText, detailText },
    { headers: authHeader(token) })
  return res.data
}

export async function restoreMessage(messageId, token) {
  const res = await axios.post(`${BASE}/moderation/messages/${messageId}/restore`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function editWord(wordId, value, token) {
  const res = await axios.patch(`${BASE}/moderation/words/${wordId}`, { value }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function editMessage(messageId, editedWord, editedDetail, token) {
  const res = await axios.patch(`${BASE}/moderation/messages/${messageId}`, { editedWord, editedDetail }, {
    headers: authHeader(token)
  })
  return res.data
}
