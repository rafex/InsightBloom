import type { OfflinePresentationFile, OfflinePresentationManifest } from '@/services/api/presentationsApi'
import { getOfflineManifestPublicKey, getOfflinePresentationManifest, getPresentationRootUrl, primePresentationAccess } from '@/services/api/presentationsApi'

const DB_NAME = 'insightbloom-offline-presentations'
const DB_VERSION = 1
const PACKAGE_STORE = 'packages'
const CHUNK_STORE = 'chunks'
const CHUNK_SIZE = 1024 * 1024

export interface OfflinePackageRecord {
  packageId: string
  conferenceId: string
  userUuid: string
  provider: 'MARP' | 'SLIDEV'
  format: 'source' | 'fat'
  indexPath: string
  expiresAt: string
  artifactHash: string
  signature: string
  signedPayload: string
  publicKey: string
  key: CryptoKey
  manifestFiles: OfflinePresentationFile[]
  files: OfflinePresentationFile[]
  createdAt: string
}

interface OfflineChunkRecord {
  id: string
  packageId: string
  path: string
  index: number
  iv: ArrayBuffer
  ciphertext: ArrayBuffer
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onerror = () => reject(request.error || new Error('offline_db_open_failed'))
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(PACKAGE_STORE)) db.createObjectStore(PACKAGE_STORE, { keyPath: 'packageId' })
      if (!db.objectStoreNames.contains(CHUNK_STORE)) {
        const chunks = db.createObjectStore(CHUNK_STORE, { keyPath: 'id' })
        chunks.createIndex('packageId', 'packageId', { unique: false })
      }
    }
    request.onsuccess = () => resolve(request.result)
  })
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onerror = () => reject(request.error || new Error('offline_db_request_failed'))
    request.onsuccess = () => resolve(request.result)
  })
}

function base64ToBytes(value: string): ArrayBuffer {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

function encodePath(value: string): string {
  return value.split('/').map(segment => encodeURIComponent(segment)).join('/')
}

async function sha256Hex(data: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}

let publicKeyPromise: Promise<string> | undefined

function getPublicKey(): Promise<string> {
  if (!publicKeyPromise) publicKeyPromise = getOfflineManifestPublicKey()
  return publicKeyPromise
}

async function verifyManifest(manifest: OfflinePresentationManifest, publicKeyBase64?: string): Promise<void> {
  const publicKey = publicKeyBase64 || await getPublicKey()
  const key = await crypto.subtle.importKey(
    'spki',
    base64ToBytes(publicKey),
    { name: 'Ed25519' } as AlgorithmIdentifier,
    false,
    ['verify']
  )
  const valid = await crypto.subtle.verify(
    { name: 'Ed25519' } as AlgorithmIdentifier,
    key,
    base64ToBytes(manifest.signature),
    base64ToBytes(manifest.signedPayload)
  )
  if (!valid) throw new Error('offline_manifest_signature_invalid')
  const canonical = JSON.stringify({
    conferenceId: manifest.conferenceId,
    provider: manifest.provider,
    format: manifest.format,
    indexPath: manifest.indexPath,
    artifactHash: manifest.artifactHash,
    expiresAt: manifest.expiresAt,
    files: manifest.files,
  })
  if (new TextDecoder().decode(base64ToBytes(manifest.signedPayload)) !== canonical) {
    throw new Error('offline_manifest_payload_invalid')
  }
}

async function validExpiry(expiresAt: string): Promise<void> {
  const expiresAtSeconds = Math.floor(Date.parse(expiresAt) / 1000)
  if (!Number.isFinite(expiresAtSeconds)) throw new Error('offline_expiry_invalid')
  const module = await import('./offlineIntegrity')
  if (!(await module.isValidUntil(Math.floor(Date.now() / 1000), expiresAtSeconds))) {
    throw new Error('offline_presentation_expired')
  }
}

async function verifyStoredPackage(record: OfflinePackageRecord): Promise<void> {
  await verifyManifest({
    conferenceId: record.conferenceId,
    provider: record.provider,
    format: record.format,
    indexPath: record.indexPath,
    artifactHash: record.artifactHash,
    expiresAt: record.expiresAt,
    files: record.manifestFiles,
    signedPayload: record.signedPayload,
    signature: record.signature,
  }, record.publicKey)
  await validExpiry(record.expiresAt)
}

async function putPackage(record: OfflinePackageRecord): Promise<void> {
  const db = await openDb()
  try {
    await requestResult(db.transaction(PACKAGE_STORE, 'readwrite').objectStore(PACKAGE_STORE).put(record))
  } finally { db.close() }
}

async function putChunk(record: OfflineChunkRecord): Promise<void> {
  const db = await openDb()
  try {
    await requestResult(db.transaction(CHUNK_STORE, 'readwrite').objectStore(CHUNK_STORE).put(record))
  } finally { db.close() }
}

async function getPackages(): Promise<OfflinePackageRecord[]> {
  const db = await openDb()
  try { return await requestResult(db.transaction(PACKAGE_STORE, 'readonly').objectStore(PACKAGE_STORE).getAll()) }
  finally { db.close() }
}

async function registerAndUnlock(record: OfflinePackageRecord): Promise<void> {
  if (!('serviceWorker' in navigator)) throw new Error('offline_service_worker_unsupported')
  const scope = `/offline-presentations/${record.packageId}/`
  const registration = await navigator.serviceWorker.register('/offline-presentations/offline-sw.js', { scope })
  let worker = registration.active
  if (!worker) {
    const pending = registration.waiting || registration.installing
    if (pending) {
      worker = await new Promise<ServiceWorker>((resolve, reject) => {
        const onStateChange = () => {
          if (registration.active) {
            pending.removeEventListener('statechange', onStateChange)
            resolve(registration.active)
          } else if (pending.state === 'redundant') {
            pending.removeEventListener('statechange', onStateChange)
            reject(new Error('offline_service_worker_redundant'))
          }
        }
        pending.addEventListener('statechange', onStateChange)
        onStateChange()
      })
    }
  }
  if (!worker) throw new Error('offline_service_worker_unavailable')
  worker.postMessage({ type: 'UNLOCK', packageId: record.packageId, key: record.key })
}

export async function getOfflinePackage(conferenceId: string, userUuid: string): Promise<OfflinePackageRecord | undefined> {
  const packages = await getPackages()
  const matches = packages
    .filter(item => item.conferenceId === conferenceId && item.userUuid === userUuid)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const latest = matches[0]
  if (!latest) return undefined
  try { await verifyStoredPackage(latest); return latest }
  catch { await deleteOfflinePackage(latest.packageId); return undefined }
}

export async function prepareOfflinePresentation(conferenceId: string, token: string, userUuid: string): Promise<OfflinePackageRecord> {
  const manifest = await getOfflinePresentationManifest(conferenceId, token)
  const publicKey = await getPublicKey()
  await verifyManifest(manifest, publicKey)
  await validExpiry(manifest.expiresAt)
  await primePresentationAccess(conferenceId, token, true)

  const packageId = crypto.randomUUID()
  // The AES key is non-extractable. IndexedDB can persist the CryptoKey so the
  // moderator can reopen the package offline, while copying the database does
  // not provide a raw key that can be immediately reused elsewhere.
  const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'])
  const sourceBase = new URL(getPresentationRootUrl(conferenceId), window.location.origin).pathname
  const offlineBase = `/offline-presentations/${packageId}/presentation/`
  const storedFiles: OfflinePresentationFile[] = []

  try {
    for (const file of manifest.files) {
      const response = await fetch(`${getPresentationRootUrl(conferenceId)}${encodePath(file.path)}`, { credentials: 'include' })
      if (!response.ok) throw new Error(`offline_file_download_failed:${file.path}`)
      let data = await response.arrayBuffer()
      const sourceHash = await sha256Hex(data)
      if (sourceHash !== file.sha256) throw new Error(`offline_file_integrity_failed:${file.path}`)

      if (file.contentType.startsWith('text/') || file.contentType.includes('javascript') || file.contentType.includes('json')) {
        const text = new TextDecoder().decode(data).replaceAll(sourceBase, offlineBase)
        data = new TextEncoder().encode(text).buffer
      }
      const storedFile = { ...file, size: data.byteLength, sha256: await sha256Hex(data) }
      storedFiles.push(storedFile)
      for (let offset = 0, index = 0; offset < data.byteLength; offset += CHUNK_SIZE, index += 1) {
        const chunk = data.slice(offset, Math.min(offset + CHUNK_SIZE, data.byteLength))
        const iv = crypto.getRandomValues(new Uint8Array(12))
        const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, chunk)
        await putChunk({
          id: `${packageId}:${file.path}:${index}`,
          packageId,
          path: file.path,
          index,
          iv: iv.buffer,
          ciphertext,
        })
      }
    }

    const record: OfflinePackageRecord = {
      packageId,
      conferenceId,
      userUuid,
      provider: manifest.provider,
      format: manifest.format,
      indexPath: manifest.indexPath,
      expiresAt: manifest.expiresAt,
      artifactHash: manifest.artifactHash,
      signature: manifest.signature,
      signedPayload: manifest.signedPayload,
      publicKey,
      key,
      manifestFiles: manifest.files,
      files: storedFiles,
      createdAt: new Date().toISOString(),
    }
    await putPackage(record)
    await registerAndUnlock(record)
    return record
  } catch (error) {
    await deleteOfflinePackage(packageId)
    throw error
  }
}

export async function openOfflinePresentation(record: OfflinePackageRecord): Promise<string> {
  await verifyStoredPackage(record)
  await registerAndUnlock(record)
  return `/offline-presentations/${record.packageId}/presentation/${encodePath(record.indexPath)}`
}

export async function deleteOfflinePackage(packageId: string): Promise<void> {
  const db = await openDb()
  try {
    const readTransaction = db.transaction(CHUNK_STORE, 'readonly')
    const chunks = await requestResult(readTransaction.objectStore(CHUNK_STORE).index('packageId').getAll(packageId)) as OfflineChunkRecord[]
    const transaction = db.transaction([PACKAGE_STORE, CHUNK_STORE], 'readwrite')
    for (const chunk of chunks) transaction.objectStore(CHUNK_STORE).delete(chunk.id)
    transaction.objectStore(PACKAGE_STORE).delete(packageId)
    await new Promise<void>((resolve, reject) => {
      transaction.onerror = () => reject(transaction.error || new Error('offline_delete_failed'))
      transaction.oncomplete = () => resolve()
    })
  } finally { db.close() }
}
