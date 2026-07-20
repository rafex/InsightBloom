// Huella real de dispositivo via ThumbmarkJS (canvas, WebGL, audio, fuentes, hardware, etc.),
// con fallback a un UUID estable en localStorage si la libreria falla, tarda mas del timeout, o
// el navegador bloquea alguna de las APIs que usa (ej. modo incognito extremo, extensiones de
// privacidad). El fallback existia antes de integrar ThumbmarkJS -- se mantiene para que un
// dispositivo nunca se quede sin poder loguearse solo porque el calculo de la huella real fallo.
import { Thumbmark } from '@thumbmarkjs/thumbmarkjs'

let _fingerprint: string | null = null

export async function getFingerprint(): Promise<string> {
  if (_fingerprint) return _fingerprint

  try {
    const tm = new Thumbmark({ timeout: 3000 })
    const result = await tm.get()
    if (result?.thumbmark) {
      _fingerprint = result.thumbmark
      return _fingerprint
    }
  } catch (e) { /* cae al fallback de abajo */ }

  // Fallback: stable UUID stored in localStorage
  let fp = localStorage.getItem('ib_fingerprint')
  if (!fp) {
    fp = 'fp-' + Math.random().toString(36).substring(2) + Date.now().toString(36)
    localStorage.setItem('ib_fingerprint', fp)
  }
  _fingerprint = fp
  return _fingerprint
}
