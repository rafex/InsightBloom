/*
 * Dedicated service worker for moderator-only offline presentations.
 *
 * This worker deliberately has no global fetch handler. It only serves files
 * below the random package scope after the moderator unlocks that package in
 * this browser session. The package contents are encrypted in IndexedDB.
 */
const DB_NAME = 'insightbloom-offline-presentations'
const DB_VERSION = 1
const PACKAGE_STORE = 'packages'
const CHUNK_STORE = 'chunks'

const unlockedKeys = new Map()

function openDb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onerror = () => reject(request.error || new Error('offline_db_open_failed'))
    request.onsuccess = () => resolve(request.result)
  })
}

function requestResult(request) {
  return new Promise((resolve, reject) => {
    request.onerror = () => reject(request.error || new Error('offline_db_request_failed'))
    request.onsuccess = () => resolve(request.result)
  })
}

async function getPackage(packageId) {
  const db = await openDb()
  try {
    return await requestResult(db.transaction(PACKAGE_STORE, 'readonly').objectStore(PACKAGE_STORE).get(packageId))
  } finally {
    db.close()
  }
}

async function getChunks(packageId) {
  const db = await openDb()
  try {
    const chunks = await requestResult(
      db.transaction(CHUNK_STORE, 'readonly').objectStore(CHUNK_STORE).index('packageId').getAll(packageId)
    )
    return chunks.sort((a, b) => a.path.localeCompare(b.path) || a.index - b.index)
  } finally {
    db.close()
  }
}

function contentType(file) {
  return file?.contentType || 'application/octet-stream'
}

function parseRequest(pathname) {
  const match = pathname.match(/^\/offline-presentations\/([^/]+)\/presentation\/(.+)$/)
  if (!match || match[2].split('/').some(segment => segment === '..')) return null
  return { packageId: match[1], path: decodeURIComponent(match[2]) }
}

async function decryptPackageFile(packageRecord, packageId, path) {
  const key = unlockedKeys.get(packageId)
  if (!key) return new Response('offline package locked', { status: 401 })

  const file = packageRecord.files.find(item => item.path === path)
  if (!file) return new Response('offline file not found', { status: 404 })

  const chunks = (await getChunks(packageId)).filter(item => item.path === path)
  const parts = []
  let total = 0
  for (const chunk of chunks) {
    const plaintext = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: new Uint8Array(chunk.iv) },
      key,
      chunk.ciphertext
    )
    parts.push(new Uint8Array(plaintext))
    total += plaintext.byteLength
  }

  const data = new Uint8Array(total)
  let offset = 0
  for (const part of parts) {
    data.set(part, offset)
    offset += part.byteLength
  }
  const digest = await crypto.subtle.digest('SHA-256', data)
  const actualHash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
  if (actualHash !== file.sha256) return new Response('offline integrity check failed', { status: 410 })

  return new Response(data, {
    status: 200,
    headers: {
      'Content-Type': contentType(file),
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
    },
  })
}

self.addEventListener('install', event => event.waitUntil(self.skipWaiting()))
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))

self.addEventListener('message', event => {
  if (event.data?.type !== 'UNLOCK' || !event.data.packageId || !event.data.key) return
  unlockedKeys.set(event.data.packageId, event.data.key)
})

self.addEventListener('fetch', event => {
  const request = parseRequest(new URL(event.request.url).pathname)
  if (!request) return
  event.respondWith((async () => {
    try {
      const packageRecord = await getPackage(request.packageId)
      if (!packageRecord || Date.parse(packageRecord.expiresAt) <= Date.now()) {
        return new Response('offline package expired', { status: 410 })
      }
      return await decryptPackageFile(packageRecord, request.packageId, request.path)
    } catch (error) {
      return new Response('offline package unavailable', { status: 503 })
    }
  })())
})
