// Ayudantes puros para "Agregar a mi calendario". Sin librería de zonas horarias: la
// conferencia ya trae un offset UTC fijo (sin DST) desde el catálogo de zonas horarias.

function toUtcDate(eventDate, time, utcOffsetMinutes) {
  const [y, m, d] = eventDate.split('-').map(Number)
  const [h, mi] = (time || '00:00').split(':').map(Number)
  const fakeUtcMillis = Date.UTC(y, m - 1, d, h, mi)
  const offset = utcOffsetMinutes || 0
  return new Date(fakeUtcMillis - offset * 60_000)
}

function formatUtc(date) {
  return date.toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z'
}

function escapeIcsText(text) {
  return String(text || '').replace(/[\\;,]/g, (c) => '\\' + c).replace(/\n/g, '\\n')
}

/**
 * @param {{name:string, eventDate:string, startTime:string, endTime?:string, venue?:string, utcOffsetMinutes?:number}} opts
 * @returns {string} contenido de un archivo .ics
 */
export function buildIcs({ name, eventDate, startTime, endTime, venue, utcOffsetMinutes }) {
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
export function downloadIcs(opts) {
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

/** @returns {string} URL para "Añadir en Google Calendar" con los campos precargados. */
export function buildGoogleCalendarUrl({ name, eventDate, startTime, endTime, venue, utcOffsetMinutes }) {
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
