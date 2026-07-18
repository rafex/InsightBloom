import axios from 'axios'

const BASE = '/api/users/api/v1/admin/users'

function authHeader(token?: string | null) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export interface AdminUserUpdate {
  displayName?: string | null
  email?: string | null
  phone?: string | null
  roles?: string
  firstName?: string | null
  lastName?: string | null
}

export interface ListUsersFilters {
  status?: string
  role?: string
  sort?: string
}

export async function listUsers(
  token: string, page = 1, pageSize = 50, filters: ListUsersFilters = {}
): Promise<{ data: any[], meta?: { totalPages?: number, [key: string]: unknown } }> {
  const res = await axios.get(BASE, { params: { page, pageSize, ...filters }, headers: authHeader(token) })
  return res.data
}

export async function getUser(uuid: string, token: string): Promise<any> {
  const res = await axios.get(`${BASE}/${uuid}`, { headers: authHeader(token) })
  return res.data.data
}

export interface UserReservationEntry {
  conferenceUuid: string
  conferenceName?: string | null
  friendlyId?: string | null
  status: string
  createdAt: string
  certificateDownloaded: boolean
}

export async function getUserReservations(uuid: string, token: string): Promise<UserReservationEntry[]> {
  const res = await axios.get(`${BASE}/${uuid}/reservations`, { headers: authHeader(token) })
  return res.data.data
}

export async function updateUser(uuid: string, { displayName, email, phone, roles, firstName, lastName }: AdminUserUpdate, token: string): Promise<unknown> {
  const res = await axios.put(`${BASE}/${uuid}`, { displayName, email, phone, roles, firstName, lastName }, {
    headers: authHeader(token)
  })
  return res.data.data
}

export async function banUser(uuid: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/${uuid}/ban`, {}, { headers: authHeader(token) })
  return res.data.data
}

export async function unbanUser(uuid: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/${uuid}/unban`, {}, { headers: authHeader(token) })
  return res.data.data
}

export async function deleteUserLogical(uuid: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/${uuid}/delete`, {}, { headers: authHeader(token) })
  return res.data.data
}
