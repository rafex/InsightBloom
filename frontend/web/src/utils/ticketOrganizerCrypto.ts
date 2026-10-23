import initTicketCose, {
  encode_sign1 as encodeSign1,
  inspect_key_id as inspectKeyId,
  inspect_signature as inspectSignature,
  inspect_sign1 as inspectSign1,
  signature_input as signatureInput,
} from '../wasm/ticket-cose/ticket_cose.js'

const DB_NAME = 'insightbloom-organizer-ticket-signing'
const DB_VERSION = 1
const KEY_STORE = 'event-keys'
let initialized: Promise<unknown> | undefined

export interface OrganizerSigningKey {
  eventUuid: string
  keyId: string
  publicKeySpki: string
  privateKey: CryptoKey
  createdAt: string
}

export interface EncryptedOrganizerKeyBackup {
  version: 1
  algorithm: 'Ed25519'
  eventUuid: string
  keyId: string
  publicKeySpki: string
  kdf: 'PBKDF2-SHA-256'
  iterations: number
  salt: string
  iv: string
  ciphertext: string
}

function base64Url(bytes: ArrayBuffer | Uint8Array): string {
  const array = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  let binary = ''
  for (const byte of array) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function fromBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(normalized + '='.repeat((4 - normalized.length % 4) % 4)), c => c.charCodeAt(0))
}

function bufferSource(bytes: Uint8Array): ArrayBuffer {
  return Uint8Array.from(bytes).buffer as ArrayBuffer
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => request.result.createObjectStore(KEY_STORE, { keyPath: 'eventUuid' })
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function ensureWasm(): Promise<void> {
  initialized ??= initTicketCose()
  await initialized
}

async function saveKey(key: OrganizerSigningKey): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(KEY_STORE, 'readwrite')
    tx.objectStore(KEY_STORE).put(key)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
  db.close()
}

export async function getOrganizerSigningKey(eventUuid: string): Promise<OrganizerSigningKey | undefined> {
  const db = await openDb()
  const key = await new Promise<OrganizerSigningKey | undefined>((resolve, reject) => {
    const request = db.transaction(KEY_STORE, 'readonly').objectStore(KEY_STORE).get(eventUuid)
    request.onsuccess = () => resolve(request.result as OrganizerSigningKey | undefined)
    request.onerror = () => reject(request.error)
  })
  db.close()
  return key
}

async function deriveBackupKey(passphrase: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(passphrase), 'PBKDF2', false, ['deriveKey'])
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt: bufferSource(salt), iterations }, material,
    { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'])
}

/** Generates an event key locally. The exportable form exists only in memory long enough to encrypt the optional recovery file. */
export async function createOrganizerSigningKey(eventUuid: string, backupPassphrase: string): Promise<{
  key: OrganizerSigningKey
  backup: EncryptedOrganizerKeyBackup
}> {
  await ensureWasm()
  if (backupPassphrase.trim().length < 12) throw new Error('organizer_key_backup_passphrase_too_short')
  const keyId = crypto.randomUUID()
  const pair = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']) as CryptoKeyPair
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey('pkcs8', pair.privateKey))
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const iterations = 310_000
  const backupKey = await deriveBackupKey(backupPassphrase, salt, iterations)
  const aad = new TextEncoder().encode(`insightbloom-organizer-key-backup-v1|${eventUuid}|${keyId}`)
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: bufferSource(iv), additionalData: bufferSource(aad) }, backupKey, bufferSource(pkcs8))
  const privateKey = await crypto.subtle.importKey('pkcs8', pkcs8, { name: 'Ed25519' }, false, ['sign'])
  pkcs8.fill(0)
  const key: OrganizerSigningKey = {
    eventUuid, keyId, publicKeySpki: base64Url(await crypto.subtle.exportKey('spki', pair.publicKey)),
    privateKey, createdAt: new Date().toISOString(),
  }
  await saveKey(key)
  return {
    key,
    backup: { version: 1, algorithm: 'Ed25519', eventUuid, keyId, publicKeySpki: key.publicKeySpki,
      kdf: 'PBKDF2-SHA-256', iterations, salt: base64Url(salt), iv: base64Url(iv), ciphertext: base64Url(ciphertext) },
  }
}

export async function restoreOrganizerSigningKey(backup: EncryptedOrganizerKeyBackup, passphrase: string): Promise<OrganizerSigningKey> {
  await ensureWasm()
  if (backup.version !== 1 || backup.algorithm !== 'Ed25519' || backup.iterations < 200_000 || backup.iterations > 1_000_000)
    throw new Error('organizer_key_backup_invalid')
  const salt = fromBase64Url(backup.salt), iv = fromBase64Url(backup.iv)
  const key = await deriveBackupKey(passphrase, salt, backup.iterations)
  const aad = new TextEncoder().encode(`insightbloom-organizer-key-backup-v1|${backup.eventUuid}|${backup.keyId}`)
  let pkcs8: ArrayBuffer
  try { pkcs8 = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: bufferSource(iv), additionalData: bufferSource(aad) }, key, bufferSource(fromBase64Url(backup.ciphertext))) }
  catch { throw new Error('organizer_key_backup_passphrase_invalid') }
  const privateKey = await crypto.subtle.importKey('pkcs8', pkcs8, { name: 'Ed25519' }, false, ['sign'])
  new Uint8Array(pkcs8).fill(0)
  const publicKey = await crypto.subtle.importKey('spki', bufferSource(fromBase64Url(backup.publicKeySpki)), { name: 'Ed25519' }, false, ['verify'])
  const probe = new TextEncoder().encode(`insightbloom-event-key-proof-v1|${backup.eventUuid}|${backup.keyId}`)
  const signature = await crypto.subtle.sign('Ed25519', privateKey, probe)
  if (!await crypto.subtle.verify('Ed25519', publicKey, signature, probe)) throw new Error('organizer_key_backup_mismatch')
  const restored: OrganizerSigningKey = { eventUuid: backup.eventUuid, keyId: backup.keyId,
    publicKeySpki: backup.publicKeySpki, privateKey, createdAt: new Date().toISOString() }
  await saveKey(restored)
  return restored
}

export async function createOrganizerKeyProof(key: OrganizerSigningKey, challenge: string): Promise<string> {
  return base64Url(await crypto.subtle.sign('Ed25519', key.privateKey, new TextEncoder().encode(challenge)))
}

export async function signCosePayload(key: OrganizerSigningKey, payload: Uint8Array): Promise<string> {
  await ensureWasm()
  const input = signatureInput(payload)
  const signature = new Uint8Array(await crypto.subtle.sign('Ed25519', key.privateKey, bufferSource(input)))
  return base64Url(encodeSign1(payload, signature, new TextEncoder().encode(key.keyId)))
}

export async function verifyCosePayload(coseBase64Url: string, publicKeySpki: string, expectedPayload?: Uint8Array): Promise<{
  keyId: string
  payload: Uint8Array
}> {
  await ensureWasm()
  const cose = fromBase64Url(coseBase64Url)
  const payload = inspectSign1(cose)
  if (expectedPayload && (payload.length !== expectedPayload.length || payload.some((byte, index) => byte !== expectedPayload[index])))
    throw new Error('cose_payload_mismatch')
  const signature = inspectSignature(cose)
  const keyId = new TextDecoder().decode(inspectKeyId(cose))
  const publicKey = await crypto.subtle.importKey('spki', bufferSource(fromBase64Url(publicKeySpki)), { name: 'Ed25519' }, false, ['verify'])
  if (!await crypto.subtle.verify('Ed25519', publicKey, bufferSource(signature), bufferSource(signatureInput(payload)))) throw new Error('cose_signature_invalid')
  return { keyId, payload }
}

export async function getCoseKeyId(coseBase64Url: string): Promise<string> {
  await ensureWasm()
  return new TextDecoder().decode(inspectKeyId(fromBase64Url(coseBase64Url)))
}
