const DB_NAME = 'insightbloom-ticket-devices'
const STORE = 'credentials'

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = () => request.result.createObjectStore(STORE)
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

export interface TicketDeviceKeyPair {
  privateKey: CryptoKey
  publicKeyJwk: JsonWebKey
}

export async function createTicketDeviceKeyPair(): Promise<TicketDeviceKeyPair> {
  if (!window.isSecureContext || !crypto.subtle) throw new Error('secure_context_required')
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign', 'verify']) as CryptoKeyPair
  return { privateKey: pair.privateKey, publicKeyJwk: await crypto.subtle.exportKey('jwk', pair.publicKey) }
}

export async function storeTicketDeviceKey(ticketUuid: string, credentialUuid: string, privateKey: CryptoKey): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.objectStore(STORE).put(privateKey, `${ticketUuid}:${credentialUuid}`)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
  db.close()
}

export async function loadTicketDeviceKey(ticketUuid: string, credentialUuid: string): Promise<CryptoKey | null> {
  const db = await openDb()
  const key = await new Promise<CryptoKey | null>((resolve, reject) => {
    const request = db.transaction(STORE, 'readonly').objectStore(STORE).get(`${ticketUuid}:${credentialUuid}`)
    request.onsuccess = () => resolve(request.result || null)
    request.onerror = () => reject(request.error)
  })
  db.close()
  return key
}

function base64Url(bytes: ArrayBuffer): string {
  const array = new Uint8Array(bytes)
  let binary = ''
  for (const byte of array) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export async function createTicketPresentation(ticketUuid: string, credentialUuid: string, privateKey: CryptoKey) {
  const expiresAt = Math.floor(Date.now() / 1000) + 45
  const payload = `insightbloom-ticket-v1|${ticketUuid}|${credentialUuid}|${expiresAt}`
  const signature = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, privateKey, new TextEncoder().encode(payload))
  return JSON.stringify({ v: 1, ticketUuid, credentialUuid, expiresAt, signature: base64Url(signature) })
}

export interface OfflineTicketBundle { payload: string, signature: string, publicKey: string, expiresAt: string }
export interface OfflineTicketScan { scanUuid: string, conferenceUuid?: string, ticketUuid: string, credentialUuid: string, expiresAt: number, signature: string, scannedAt: number }

function decodeBase64Url(value: string): ArrayBuffer {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(normalized + '='.repeat((4 - normalized.length % 4) % 4))
  return Uint8Array.from(binary, ch => ch.charCodeAt(0)).buffer as ArrayBuffer
}

export async function verifyOfflineTicketBundle(bundle: OfflineTicketBundle, conferenceUuid: string, requireFresh = true): Promise<any> {
  const serverKey = await crypto.subtle.importKey('spki', decodeBase64Url(bundle.publicKey.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')),
    { name: 'Ed25519' }, false, ['verify'])
  const bundleVerified = await crypto.subtle.verify({ name: 'Ed25519' }, serverKey, decodeBase64Url(bundle.signature), new TextEncoder().encode(bundle.payload))
  if (!bundleVerified) throw new Error('offline_bundle_signature_invalid')
  const data = JSON.parse(bundle.payload)
  const now = Math.floor(Date.now() / 1000)
  if (data.version !== 1 || data.conferenceUuid !== conferenceUuid || (requireFresh && data.expiresAt <= now)) throw new Error('offline_bundle_expired')
  return data
}

export async function verifyOfflineTicketScan(bundle: OfflineTicketBundle, raw: string, conferenceUuid: string): Promise<Omit<OfflineTicketScan, 'scanUuid' | 'scannedAt'>> {
  const data = await verifyOfflineTicketBundle(bundle, conferenceUuid)
  const now = Math.floor(Date.now() / 1000)
  const presentation = JSON.parse(raw)
  if (presentation.v !== 1 || presentation.expiresAt < now - 10 || presentation.expiresAt > now + 90) throw new Error('ticket_presentation_expired')
  const device = (data.devices as Array<{ ticketUuid: string, credentialUuid: string, publicKeyJwk: JsonWebKey | string }>)
    .find(item => item.ticketUuid === presentation.ticketUuid && item.credentialUuid === presentation.credentialUuid)
  if (!device) throw new Error('ticket_device_not_in_bundle')
  const publicJwk = typeof device.publicKeyJwk === 'string' ? JSON.parse(device.publicKeyJwk) as JsonWebKey : device.publicKeyJwk
  const deviceKey = await crypto.subtle.importKey('jwk', publicJwk, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
  const signed = `insightbloom-ticket-v1|${presentation.ticketUuid}|${presentation.credentialUuid}|${presentation.expiresAt}`
  const valid = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, deviceKey, decodeBase64Url(presentation.signature), new TextEncoder().encode(signed))
  if (!valid) throw new Error('ticket_presentation_invalid')
  return { ticketUuid: presentation.ticketUuid, credentialUuid: presentation.credentialUuid, expiresAt: presentation.expiresAt, signature: presentation.signature }
}

const OFFLINE_QUEUE_KEY = 'insightbloom-ticket-offline-queue-v1'
export function readOfflineTicketQueue(): OfflineTicketScan[] {
  try { return JSON.parse(localStorage.getItem(OFFLINE_QUEUE_KEY) || '[]') as OfflineTicketScan[] } catch { return [] }
}
export function queueOfflineTicketScan(scan: OfflineTicketScan): void {
  const queue = readOfflineTicketQueue()
  if (!queue.some(item => item.scanUuid === scan.scanUuid)) {
    queue.push(scan)
    localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(queue))
  }
}
export function removeSyncedOfflineTicketScans(scanUuids: string[]): void {
  const synced = new Set(scanUuids)
  localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(readOfflineTicketQueue().filter(item => !synced.has(item.scanUuid))))
}
