import axios from 'axios'
import { getFingerprint } from '@/services/auth/fingerprint'

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
  // La huella (ThumbmarkJS) tambien viaja en el registro para que PlatformDeviceGuard pueda
  // frenar spam de creacion de cuentas desde el mismo dispositivo -- ver checkRegistration.
  const deviceFingerprint = await getFingerprint()
  const res = await axios.post(`${BASE}/register`,
    { displayName, email, phone, password, socialLinks, deviceFingerprint })
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

/** Pide un código de login por correo. Siempre "tiene éxito" desde el punto de vista del
 *  llamador -- el backend nunca revela si el identificador existe o si la cuenta usa código
 *  (ver RequestLoginOtpUseCase). Distinto de sendOtp: ese es del flujo de verificar registro. */
export async function requestLoginOtp(identifier: string): Promise<void> {
  await axios.post(`${BASE}/login/otp/request`, { identifier })
}

export interface LoginResult {
  token: string
  userUuid: string
  role: string
  expiresAt: string
}

export async function verifyLoginOtp(identifier: string, code: string): Promise<LoginResult> {
  const deviceFingerprint = await getFingerprint()
  const res = await axios.post(`${BASE}/login/otp/verify`, { identifier, code, deviceFingerprint })
  return res.data.data
}
