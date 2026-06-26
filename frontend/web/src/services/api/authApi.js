import axios from 'axios'

const BASE = '/api/users/api/v1/auth'

export async function register({ displayName, email, phone, password, socialLinks }) {
  const res = await axios.post(`${BASE}/register`, { displayName, email, phone, password, socialLinks })
  return res.data.data
}

export async function sendOtp(identifier, channel) {
  const res = await axios.post(`${BASE}/otp/send`, { identifier, channel })
  return res.data.data
}

export async function verifyOtp(identifier, code) {
  const res = await axios.post(`${BASE}/otp/verify`, { identifier, code })
  return res.data.data
}
