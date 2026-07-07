import axios from 'axios'

const BASE = '/api/moderation/api/v1'

function authHeader(token?: string | null) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function getModerationWords(conferenceId: string, page = 1, pageSize = 50, status = '', token?: string): Promise<unknown> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/moderation/words`, {
    params: { page, pageSize, ...(status ? { status } : {}) },
    headers: authHeader(token)
  })
  return res.data
}

export async function getModerationMessages(conferenceId: string, page = 1, pageSize = 50, status = '', token?: string): Promise<unknown> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/moderation/messages`, {
    params: { page, pageSize, ...(status ? { status } : {}) },
    headers: authHeader(token)
  })
  return res.data
}

export async function censorWord(wordId: string, reason: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/words/${wordId}/censor`, { reason }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function restoreWord(wordId: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/words/${wordId}/restore`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function censorMessage(
  messageId: string, reason: string, target: string | undefined, token: string | undefined,
  conferenceId: string, wordText?: string, detailText?: string
): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/messages/${messageId}/censor`,
    { reason, target: target || 'detail', conferenceUuid: conferenceId, wordText, detailText },
    { headers: authHeader(token) })
  return res.data
}

export async function restoreMessage(messageId: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/messages/${messageId}/restore`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function editWord(wordId: string, value: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.patch(`${BASE}/conferences/${conferenceId}/moderation/words/${wordId}`, { value }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function editMessage(messageId: string, editedWord: string, editedDetail: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.patch(`${BASE}/conferences/${conferenceId}/moderation/messages/${messageId}`, { editedWord, editedDetail }, {
    headers: authHeader(token)
  })
  return res.data
}

export async function deleteWord(wordId: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/words/${wordId}/delete`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function deleteMessage(messageId: string, token: string | undefined, conferenceId: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/messages/${messageId}/delete`, {}, {
    headers: authHeader(token)
  })
  return res.data
}

export async function answerMessage(
  messageId: string, answerText: string, answeredByUserUuid: string, token: string | undefined, conferenceId: string
): Promise<unknown> {
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/moderation/messages/${messageId}/answer`,
    { answerText, answeredByUserUuid },
    { headers: authHeader(token) })
  return res.data
}

export async function getMessageAnswer(messageId: string, conferenceId: string): Promise<unknown> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/moderation/messages/${messageId}/answer`)
  return res.data.data
}
