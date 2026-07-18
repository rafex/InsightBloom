export function isExpired(iso: string | null | undefined): boolean {
  return !!iso && new Date(iso) < new Date()
}
