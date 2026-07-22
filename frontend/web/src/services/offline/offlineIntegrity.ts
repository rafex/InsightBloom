let wasmInstance: Promise<WebAssembly.Instance> | null = null

async function loadWasm(): Promise<WebAssembly.Instance> {
  if (!wasmInstance) {
    wasmInstance = fetch('/offline-integrity.wasm', { cache: 'no-store' })
      .then(response => {
        if (!response.ok) throw new Error('offline_wasm_unavailable')
        return response.arrayBuffer()
      })
      .then(bytes => WebAssembly.instantiate(bytes, {}))
      .then(result => result.instance)
  }
  return wasmInstance
}

export async function isValidUntil(nowSeconds: number, expiresAtSeconds: number): Promise<boolean> {
  const instance = await loadWasm()
  const check = instance.exports.is_valid_until as ((now: bigint, expiresAt: bigint) => number)
  return check(BigInt(nowSeconds), BigInt(expiresAtSeconds)) === 1
}
