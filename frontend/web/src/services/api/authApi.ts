import axios from 'axios'

const BASE = '/api/users/api/v1/auth'

export interface SocialLink {
  platform: string
  url: string
}

export interface RegisterRequest {
  displayName: string
  email?: string | null
  phone?: string | null
  password: string
  socialLinks?: SocialLink[]
}

export async function register({ displayName, email, phone, password, socialLinks }: RegisterRequest): Promise<unknown> {
  const res = await axios.post(`${BASE}/register`, { displayName, email, phone, password, socialLinks })
  return res.data.data
}

export async function sendOtp(identifier: string, channel: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/otp/send`, { identifier, channel })
  return res.data.data
}

export async function verifyOtp(identifier: string, code: string): Promise<{ token: string, role: string, userUuid: string }> {
  const res = await axios.post(`${BASE}/otp/verify`, { identifier, code })
  return res.data.data
}
