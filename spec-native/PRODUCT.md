# PRODUCT.md

Fuente de verdad del producto. Actualizado a fase producto (post-PoC).

## Problema

Durante una conferencia o charla, las dudas y temas del publico llegan de
forma dispersa y sin una visualizacion agregada que permita detectar rapido
los conceptos que mas preocupan o interesan.

InsightBloom convierte mensajes enviados por chat mediante comandos en
nubes de palabras interactivas separadas por dudas y temas. Cada palabra
representa un concepto recurrente y, al seleccionarla, muestra el detalle
de los mensajes en formato timeline cronologico. La plataforma incluye
moderacion en vivo, encuestas con certificados, presentaciones de slides,
y un bot de chat con IA para acompañar la conferencia. El organizador crea
una conferencia con nombre, UUID, identificador amigable, ubicacion
geografica y tiempo de expiracion.

## Usuarios

- Ponente o conferencista:
  necesita identificar en tiempo real que conceptos concentran mas dudas o
  interes del publico sin leer el chat completo.
- Organizador autenticado (rol ORGANIZER):
  necesita crear conferencias, obtener un identificador amigable, gestionar
  encuestas, subir presentaciones y moderar contenido.
- Administrador (rol ADMIN):
  necesita gestionar usuarios del sistema, configurar certificados y
  supervisar multiples conferencias.
- Moderador (rol MODERATOR):
  necesita revisar y censurar contenido manualmente durante la conferencia
  sin poder crear conferencias ni gestionar usuarios.
- Audiencia participante (rol GUEST):
  necesita una forma simple de enviar dudas o temas usando comandos como
  `/duda` y `/tema`, responder encuestas, y ver presentaciones. Accede via
  identificador amigable compartido por el organizador.

## Objetivos

- Objetivo principal:
  visualizar en nubes de palabras las dudas y temas capturados desde el
  chat durante una conferencia para que el ponente entienda rapido que esta
  pensando y preguntando la audiencia.
- Objetivos secundarios:
  permitir explorar el detalle de cada palabra con timeline cronologico;
  reducir ruido mediante censura de palabras no deseadas (automatica y manual);
  ofrecer dashboard de moderacion operable en vivo durante la conferencia;
  permitir al organizador crear encuestas y emitir certificados de participacion;
  soportar presentaciones de slides (Marp Markdown → HTML) integradas;
  ofrecer un bot de chat con IA (Roberto) para acompañar la conferencia;
  administrar usuarios y roles desde el sistema;
  verificar identidad de usuarios via OTP (SMS Twilio / email Zoho).
- Metricas de exito:
  los participantes pueden enviar mensajes validos usando comandos sin
  capacitacion adicional;
  el conferencista detecta rapidamente los topicos dominantes;
  al seleccionar una palabra se muestran sus detalles sin ambiguedad;
  la nube excluye terminos censurados;
  el dashboard permite censura manual rapida durante la conferencia;
  dudas y temas pueden consultarse por separado;
  encuestas se crean, responden y generan certificados sin friccion;
  slides Marp se renderizan correctamente en el visor de la conferencia.

## No objetivos

- No se busca construir una plataforma completa de chat general (el chat
  existe como soporte a la conferencia, no como producto independiente).
- No se busca soporte multiroom o multi-conferencia simultanea desde una
  misma interfaz de organizador.
- No se busca aun analisis semantico avanzado, clustering por IA o
  moderacion automatica avanzada.
- No se busca gestion de pagos, ticketing ni registro masivo de asistentes.

## Capacidades actuales (fase producto)

- **Nubes de palabras**: dudas y temas separadas, D3.js interactivo
- **Timeline**: detalle cronologico por palabra con censura respetada
- **Moderacion**: dashboard con censura/restauracion/edicion manual y
  barrera automatica de terminos bloqueados
- **Conferencias**: creacion con nombre, UUID, friendlyId, ubicacion
  (mapa Leaflet), expiracion
- **Autenticacion**: login con password + JWT, guests via fingerprint
  (ThumbmarkJS), OTP opcional via Twilio SMS / Zoho email
- **Roles**: ADMIN, ORGANIZER, MODERATOR, GUEST con matriz de permisos
- **Encuestas**: creacion, respuesta, resultados, certificados de
  finalizacion
- **Presentaciones**: upload de archivos Marp Markdown, conversion a
  slides HTML, visualizacion en la conferencia
- **Chat en tiempo real**: WebSocket + bot IA Roberto (DeepSeek/LLM)
- **Perfil de usuario**: edicion de datos personales
- **Admin de usuarios**: gestion de usuarios por rol ADMIN
- **Certificados**: emision y configuracion por conferencia
- **CLI administrativo**: `insightbloom-cli` para crear/actualizar
  usuarios sin exponer endpoints administrativos

## Valor diferencial

InsightBloom combina una entrada simple basada en comandos con
visualizacion en tiempo real y herramientas complementarias para el
organizador (encuestas, presentaciones, certificados). No solo muestra
frecuencia de palabras: conserva el contexto humano de cada intervencion
para que la nube sea explorable y util en vivo. El bot de chat con IA
(Roberto) acompana la experiencia. La plataforma esta diseñada para
operar en K3s con Helm y desplegarse con un solo comando.
