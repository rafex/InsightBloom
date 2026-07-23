const DEFAULT_PUBLIC_PREFIX = '/api/presentations';

/**
 * The presentation service is exposed through the frontend proxy. The browser
 * therefore sees /api/presentations/api/v1/... even though Express receives
 * the rewritten /api/v1/... path. Cookies must use the browser-visible path.
 */
function presentationCookiePath(conferenceId) {
  const prefix = String(process.env.PRESENTATIONS_PUBLIC_PREFIX || DEFAULT_PUBLIC_PREFIX)
    .replace(/\/+$/, '');
  return `${prefix}/api/v1/conferences/${conferenceId}/presentation`;
}

function hasConferenceAccess(body) {
  const data = body?.data || body || {};
  if (data.eventActive === false || data.eventStatus === 'CLOSED') return false;
  return data.ticketRequired !== true
    || data.hasAccess === true
    || data.presentationAccess === true;
}

module.exports = { presentationCookiePath, hasConferenceAccess };
