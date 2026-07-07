// Ayudantes puros para "Agregar a mi calendario". Sin librería de zonas horarias: la
// conferencia ya trae un offset UTC fijo (sin DST) desde el catálogo de zonas horarias.

export interface CalendarEventInput {
  name: string
  eventDate: string
  startTime: string
  endTime?: string | null
  venue?: string | null
  utcOffsetMinutes?: number | null
}

function toUtcDate(eventDate: string, time: string | null | undefined, utcOffsetMinutes: number | null | undefined): Date {
  const [y, m, d] = eventDate.split('-').map(Number)
  const [h, mi] = (time || '00:00').split(':').map(Number)
  const fakeUtcMillis = Date.UTC(y, m - 1, d, h, mi)
  const offset = utcOffsetMinutes || 0
  return new Date(fakeUtcMillis - offset * 60_000)
}

function formatUtc(date: Date): string {
  return date.toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z'
}

function escapeIcsText(text: string | null | undefined): string {
  return String(text || '').replace(/[\\;,]/g, (c) => '\\' + c).replace(/\n/g, '\\n')
}

/** Contenido de un archivo .ics para el evento dado. */
export function buildIcs({ name, eventDate, startTime, endTime, venue, utcOffsetMinutes }: CalendarEventInput): string {
  const start = toUtcDate(eventDate, startTime, utcOffsetMinutes)
  const end = endTime
    ? toUtcDate(eventDate, endTime, utcOffsetMinutes)
    : new Date(start.getTime() + 60 * 60_000) // 1h por defecto si no hay hora de fin

  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//InsightBloom//Conferencia//ES',
    'BEGIN:VEVENT',
    `UID:${Date.now()}@insightbloom`,
    `DTSTAMP:${formatUtc(new Date())}`,
    `DTSTART:${formatUtc(start)}`,
    `DTEND:${formatUtc(end)}`,
    `SUMMARY:${escapeIcsText(name)}`,
    venue ? `LOCATION:${escapeIcsText(venue)}` : null,
    'END:VEVENT',
    'END:VCALENDAR'
  ].filter(Boolean).join('\r\n')
}

/** Dispara la descarga del .ics generado por {@link buildIcs}. */
export function downloadIcs(opts: CalendarEventInput): void {
  const ics = buildIcs(opts)
  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${(opts.name || 'conferencia').replace(/[^\w\-]+/g, '_')}.ics`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/** URL para "Añadir en Google Calendar" con los campos precargados. */
export function buildGoogleCalendarUrl({ name, eventDate, startTime, endTime, venue, utcOffsetMinutes }: CalendarEventInput): string {
  const start = toUtcDate(eventDate, startTime, utcOffsetMinutes)
  const end = endTime
    ? toUtcDate(eventDate, endTime, utcOffsetMinutes)
    : new Date(start.getTime() + 60 * 60_000)
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: name || '',
    dates: `${formatUtc(start)}/${formatUtc(end)}`
  })
  if (venue) params.set('location', venue)
  return `https://calendar.google.com/calendar/render?${params.toString()}`
}
