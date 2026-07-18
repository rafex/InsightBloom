export const DEFAULT_CAPACITY = 10
export const RECOMMENDED_MAX_CAPACITY = 30

export interface CapacityWarning {
  level: 'warning' | 'risk' | 'critical'
  text: string
}

/** La infraestructura tiene recursos finitos -- alerta graduada según el aforo declarado. */
export function capacityWarning(capacity: number | null | undefined): CapacityWarning | null {
  if (capacity == null) return null
  if (capacity > 100) {
    return { level: 'critical', text: 'Aforo posiblemente insostenible para la infraestructura actual.' }
  }
  if (capacity > 50) {
    return { level: 'risk', text: 'Aforo en riesgo — la infraestructura podría no soportarlo con holgura.' }
  }
  if (capacity > 40) {
    return { level: 'warning', text: `Aforo por encima de lo recomendado (${RECOMMENDED_MAX_CAPACITY}) — revisá la infraestructura antes de confirmar.` }
  }
  return null
}
