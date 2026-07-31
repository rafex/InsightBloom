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

const TICKET_STATUS_LABELS: Record<string, string> = {
  ISSUED: 'Emitido · sin reclamar',
  CLAIMED: 'Reclamado',
  CHECKED_IN: 'Registrado en check-in',
  REVOKED: 'Revocado',
  EXPIRED: 'Expirado'
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

/** Etiquetas de boleto: conserva el detalle que necesita el moderador sin exponer el enum. */
export function formatTicketStatusLabel(status: string | null | undefined): string {
  const normalized = status?.trim().toUpperCase() || ''
  return TICKET_STATUS_LABELS[normalized] || formatStatusLabel(normalized)
}
