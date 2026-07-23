const test = require('node:test');
const assert = require('node:assert/strict');
const { presentationCookiePath, hasConferenceAccess } = require('../access');

test('uses the browser-visible proxy path for presentation cookies', () => {
  assert.equal(
    presentationCookiePath('25c3250e-e3da-4267-8972-4a045e31fb73'),
    '/api/presentations/api/v1/conferences/25c3250e-e3da-4267-8972-4a045e31fb73/presentation'
  );
});

test('allows open events and operational staff without an attendee ticket', () => {
  assert.equal(hasConferenceAccess({ data: { ticketRequired: false } }), true);
  assert.equal(hasConferenceAccess({ data: { ticketRequired: true, hasAccess: true } }), true);
  assert.equal(hasConferenceAccess({ data: { ticketRequired: true, presentationAccess: true } }), true);
  assert.equal(hasConferenceAccess({ data: { ticketRequired: true, hasAccess: false, presentationAccess: false } }), false);
});

test('rejects access when the conference is closed, including staff access', () => {
  assert.equal(hasConferenceAccess({ data: { eventActive: false, eventStatus: 'CLOSED', ticketRequired: false } }), false);
  assert.equal(hasConferenceAccess({ data: { eventActive: false, eventStatus: 'CLOSED', ticketRequired: true, presentationAccess: true } }), false);
});
