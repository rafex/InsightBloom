const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrador',
  ATTENDEE: 'Asistente',
  GUEST: 'Invitado',
  MODERATOR: 'Moderador',
  ORGANIZER: 'Organizador',
  STAFF: 'Personal de apoyo'
}

export function formatRoleLabel(role: string | null | undefined): string {
  const normalized = role?.trim().toUpperCase() || ''
  if (!normalized) return 'Sin rol'
  if (ROLE_LABELS[normalized]) return ROLE_LABELS[normalized]

  return normalized
    .replace(/_/g, ' ')
    .toLocaleLowerCase('es-MX')
    .replace(/^./u, (character) => character.toLocaleUpperCase('es-MX'))
}

export function formatRoleList(roles: string | null | undefined): string {
  return (roles || '')
    .split(',')
    .map((role) => formatRoleLabel(role))
    .filter((role) => role !== 'Sin rol')
    .join(', ')
}
