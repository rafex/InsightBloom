// Datos de contacto del organizador/presentador, mostrados al final del
// flujo de encuesta (pantalla de agradecimiento). Edita estos valores con
// tu información real.
export const organizerContact = {
  name: 'Raúl González (rafex)',
  email: 'rafex@rafex.dev',
  website: 'https://rafex.dev',
  linkedin: 'https://linkedin.com/in/soft-architect-raul-gonzalez',
  linkedinNewsletter: 'https://www.linkedin.com/newsletters/explorador-t%C3%A9cnico-7325876304472330240/',
  github: 'https://github.com/rafex',
  devto: 'https://dev.to/rafex',
  blog: 'https://theworldofrafex.blog/',
  telegram: '@rafex0'
}

// Construye el deep link de Telegram con un mensaje precargado mencionando
// la conferencia (friendlyId o uuid) que el asistente acaba de calificar.
export function telegramContactUrl(conferenceLabel) {
  const username = organizerContact.telegram.replace('@', '')
  const message = `¡Hola me gustó tu conferencia ${conferenceLabel}!`
  return `https://t.me/${username}?text=${encodeURIComponent(message)}`
}
