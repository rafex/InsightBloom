import { describe, it, expect } from 'vitest'
import { buildIcs, buildGoogleCalendarUrl } from '../calendarLink'

describe('calendarLink', () => {
  describe('buildIcs', () => {
    it('converts local wall-clock time to UTC using the fixed offset (GMT-6)', () => {
      const ics = buildIcs({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        utcOffsetMinutes: -360
      })
      // 09:00 local at GMT-6 (offset -360) => 15:00 UTC
      expect(ics).toContain('DTSTART:20260801T150000Z')
    })

    it('defaults the end time to start + 1h when endTime is missing', () => {
      const ics = buildIcs({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        utcOffsetMinutes: -360
      })
      expect(ics).toContain('DTEND:20260801T160000Z')
    })

    it('uses the explicit endTime when provided', () => {
      const ics = buildIcs({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        endTime: '11:30',
        utcOffsetMinutes: -360
      })
      expect(ics).toContain('DTEND:20260801T173000Z')
    })

    it('treats a missing/zero offset as UTC (no shift)', () => {
      const ics = buildIcs({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00'
      })
      expect(ics).toContain('DTSTART:20260801T090000Z')
    })

    it('escapes commas, semicolons and newlines in text fields per RFC 5545', () => {
      const ics = buildIcs({
        name: 'Charla: Node, Vue; y más\nsegunda línea',
        eventDate: '2026-08-01',
        startTime: '09:00',
        utcOffsetMinutes: -360
      })
      expect(ics).toContain('SUMMARY:Charla: Node\\, Vue\\; y más\\nsegunda línea')
    })

    it('omits LOCATION when venue is not provided', () => {
      const ics = buildIcs({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        utcOffsetMinutes: -360
      })
      expect(ics).not.toContain('LOCATION:')
    })

    it('includes LOCATION when venue is provided', () => {
      const ics = buildIcs({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        venue: 'Auditorio A',
        utcOffsetMinutes: -360
      })
      expect(ics).toContain('LOCATION:Auditorio A')
    })
  })

  describe('buildGoogleCalendarUrl', () => {
    it('builds a Google Calendar template URL with UTC dates from the local offset', () => {
      const url = buildGoogleCalendarUrl({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        endTime: '10:00',
        venue: 'Auditorio A',
        utcOffsetMinutes: -360
      })
      const parsed = new URL(url)
      expect(parsed.origin + parsed.pathname).toBe('https://calendar.google.com/calendar/render')
      expect(parsed.searchParams.get('action')).toBe('TEMPLATE')
      expect(parsed.searchParams.get('text')).toBe('Charla')
      expect(parsed.searchParams.get('dates')).toBe('20260801T150000Z/20260801T160000Z')
      expect(parsed.searchParams.get('location')).toBe('Auditorio A')
    })

    it('omits the location param when venue is not provided', () => {
      const url = buildGoogleCalendarUrl({
        name: 'Charla',
        eventDate: '2026-08-01',
        startTime: '09:00',
        utcOffsetMinutes: -360
      })
      expect(new URL(url).searchParams.has('location')).toBe(false)
    })
  })
})
