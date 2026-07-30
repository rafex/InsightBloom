const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
  BANNED: 'Baneado',
  CLAIMED: 'Reclamado',
  CHECKED_IN: 'Registrado en check-in',
  CENSURADO_AUTO: 'Censurado automáticamente',
  CENSURADO_MANUAL: 'Censurado manualmente',
  CLOSED: 'Cerrado',
  DELETED: 'Eliminado',
  DRAFT: 'Borrador',
  EXPIRED: 'Expirado',
  INACTIVE: 'Inactivo',
  ISSUED: 'Emitido',
  PENDING: 'Pendiente',
  PENDIENTE_REVISION: 'Pendiente de revisión',
  RESERVED: 'Reservado',
  REVOKED: 'Revocado',
  UNAVAILABLE: 'No disponible',
  VISIBLE: 'Visible'
}

export function formatStatusLabel(status: string | null | undefined): string {
  const normalized = status?.trim().toUpperCase() || ''
  if (!normalized) return 'Sin estado'
  if (STATUS_LABELS[normalized]) return STATUS_LABELS[normalized]

  return normalized
    .replace(/_/g, ' ')
    .toLocaleLowerCase('es-MX')
    .replace(/^./u, (character) => character.toLocaleUpperCase('es-MX'))
}
