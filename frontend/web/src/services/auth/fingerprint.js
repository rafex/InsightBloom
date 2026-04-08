// ThumbmarkJS fingerprint - loads from CDN or fallback UUID
let _fingerprint = null

export async function getFingerprint() {
  if (_fingerprint) return _fingerprint

  try {
    // Try ThumbmarkJS if loaded globally
    if (typeof Thumbmark !== 'undefined') {
      _fingerprint = await Thumbmark.getFingerprint()
      return _fingerprint
    }
  } catch (e) { /* fallback */ }

  // Fallback: stable UUID stored in localStorage
  let fp = localStorage.getItem('ib_fingerprint')
  if (!fp) {
    fp = 'fp-' + Math.random().toString(36).substring(2) + Date.now().toString(36)
    localStorage.setItem('ib_fingerprint', fp)
  }
  _fingerprint = fp
  return _fingerprint
}
