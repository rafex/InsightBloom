import axios from 'axios'

const BASE = '/api/users/api/v1/organizer-invitations'
const ADMIN = '/api/users/api/v1/admin/organizer-invitations'
const auth = (token: string) => ({ headers: { Authorization: `Bearer ${token}` } })

export interface OrganizerInvite {
  uuid: string
  email: string
  status: 'PENDING' | 'DELIVERY_FAILED' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED' | string
  issuerUuid: string
  referralUuid?: string
  createdAt: string
  expiresAt: string
  sentAt?: string
  acceptedAt?: string
  deliveryError?: string
}

export interface OrganizerReferral {
  uuid: string
  email: string
  status: 'PENDING_REVIEW' | 'INVITATION_SENT' | 'REJECTED' | string
  inviterUuid: string
  reviewerUuid?: string
  invitationUuid?: string
  createdAt: string
  reviewedAt?: string
}

export interface OrganizerInviteSettings {
  referralsEnabled: boolean
  invitationTtlDays: number
  referralLimit: number
}

export async function listOrganizerInvites(token: string, status?: string) {
  const r = await axios.get(ADMIN, { ...auth(token), params: status ? { status } : {} })
  return r.data.data.items as OrganizerInvite[]
}
export async function createOrganizerInvite(token: string, email: string) {
  const r = await axios.post(ADMIN, { email }, auth(token)); return r.data.data as OrganizerInvite
}
export async function resendOrganizerInvite(token: string, uuid: string) {
  const r = await axios.post(`${ADMIN}/${encodeURIComponent(uuid)}/resend`, {}, auth(token)); return r.data.data as OrganizerInvite
}
export async function revokeOrganizerInvite(token: string, uuid: string) {
  return axios.post(`${ADMIN}/${encodeURIComponent(uuid)}/revoke`, {}, auth(token))
}
export async function getOrganizerInviteSettings(token: string) {
  const r = await axios.get(`${ADMIN}/settings`, auth(token)); return r.data.data as OrganizerInviteSettings
}
export async function saveOrganizerInviteSettings(token: string, settings: OrganizerInviteSettings) {
  const r = await axios.put(`${ADMIN}/settings`, settings, auth(token)); return r.data.data as OrganizerInviteSettings
}
export async function listOrganizerReferrals(token: string, status?: string) {
  const r = await axios.get(`${ADMIN}/referrals`, { ...auth(token), params: status ? { status } : {} })
  return r.data.data.items as OrganizerReferral[]
}
export async function approveOrganizerReferral(token: string, uuid: string) {
  const r = await axios.post(`${ADMIN}/referrals/${encodeURIComponent(uuid)}/approve`, {}, auth(token)); return r.data.data as OrganizerInvite
}
export async function rejectOrganizerReferral(token: string, uuid: string) {
  return axios.post(`${ADMIN}/referrals/${encodeURIComponent(uuid)}/reject`, {}, auth(token))
}
export async function listEnabledReferrers(token: string) {
  const r = await axios.get(`${ADMIN}/referrers`, auth(token)); return (r.data.data.items || []) as string[]
}
export async function listReferralEligibleOrganizers(token: string) {
  const r = await axios.get(`${ADMIN}/referrers/eligible`, auth(token)); return (r.data.data.items || []) as string[]
}
export async function setReferrerEnabled(token: string, userUuid: string, enabled: boolean) {
  return axios.put(`${ADMIN}/referrers/${encodeURIComponent(userUuid)}`, { enabled }, auth(token))
}
export async function submitOrganizerReferral(token: string, email: string) {
  const r = await axios.post(`${BASE}/referrals`, { email }, auth(token)); return r.data.data as OrganizerReferral
}
export async function getMyOrganizerReferrals(token: string) {
  const r = await axios.get(`${BASE}/referrals`, auth(token))
  return r.data.data as { items: OrganizerReferral[], remaining: number, limit: number, enabled: boolean }
}
export async function previewOrganizerInvite(token: string) {
  const r = await axios.post(`${BASE}/preview`, { token }); return r.data.data as { valid: boolean, emailHint?: string, expiresAt?: string }
}
export async function redeemOrganizerInvite(authToken: string, inviteToken: string) {
  const r = await axios.post(`${BASE}/redeem`, { token: inviteToken }, auth(authToken)); return r.data.data
}
