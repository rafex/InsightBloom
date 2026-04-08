# PRODUCT.md

Fuente de verdad del producto.

## Problema

Durante una conferencia o charla, las dudas y temas del publico llegan de
forma dispersa y sin una visualizacion agregada que permita detectar rapido
los conceptos que mas preocupan o interesan.

El proyecto busca convertir mensajes enviados por chat mediante comandos en
una nube de palabras interactiva. Cada palabra representa una duda o tema
recurrente y, al seleccionarla, debe mostrar el detalle de los mensajes en
un formato tipo timeline social para profundizar en el contexto. La
plataforma separa visualmente dudas y temas, y añade una capa de moderacion
operable en vivo durante la conferencia. El acceso al evento parte desde un
portal donde el organizador crea una conferencia con nombre, UUID interno y
un identificador amigable para compartirla rapidamente.

## Usuarios

- Ponente o conferencista:
  necesita identificar en tiempo real que conceptos concentran mas dudas o
  interes del publico sin leer el chat completo.
- Organizador autenticado:
  necesita crear una conferencia, obtener un identificador amigable para
  compartirla y habilitar la sesion activa.
- Audiencia participante:
  necesita una forma simple y guiada de enviar dudas o temas usando comandos
  cortos como `/duda` y `/tema`, tanto desde integraciones webhook como
  desde clientes que consuman la API REST.
- Equipo organizador o moderador:
  necesita una vista entendible de la conversacion para apoyar la moderacion
  y priorizar preguntas;
  tambien necesita intervenir manualmente cuando un termino o detalle deba
  ocultarse aunque no haya sido filtrado automaticamente.

## Objetivos

- Objetivo principal:
  visualizar en una nube de palabras las dudas y temas capturados desde el
  chat durante una conferencia para que el ponente entienda rapido que esta
  pensando y preguntando la audiencia.
- Objetivos secundarios:
  permitir explorar el detalle de cada palabra y ordenar primero los mensajes
  que usaron exactamente esa palabra y despues los relacionados;
  reducir ruido en la visualizacion mediante censura de palabras no deseadas
  y una medicion inicial de intencion;
  ofrecer un dashboard para revisar y censurar manualmente casos que la
  barrera automatica no detecte, incluyendo dobles sentidos.
- Metricas de exito:
  los participantes pueden enviar mensajes validos usando comandos sin
  capacitacion adicional;
  el conferencista puede detectar rapidamente los topicos dominantes;
  al seleccionar una palabra se muestran sus detalles sin ambiguedad;
  la nube excluye terminos censurados o irrelevantes definidos para el PoC;
  el dashboard permite censura manual rapida durante la conferencia;
  dudas y temas pueden consultarse por separado.

## No objetivos

- No se busca construir una plataforma completa de chat general.
- No se busca soporte multiroom, autenticacion avanzada o administracion de
  eventos en esta primera etapa.
- No se busca aun analisis semantico avanzado, clustering por IA o
  moderacion automatica avanzada.

## Valor diferencial

La solución combina una entrada extremadamente simple basada en comandos con
una visualización en tiempo real pensada para escenarios de conferencia.
No solo muestra frecuencia de palabras: también conserva el contexto humano
de cada intervención para que la nube sea explorable y útil en vivo.

InsightBloom existe para convertir el ruido de un chat en señales accionables
durante una conferencia. En lugar de obligar al ponente a leer cientos de
mensajes, sintetiza el interés colectivo en nubes separadas de dudas y temas,
permite entrar al detalle cronológico de cada palabra y da al moderador un
dashboard para corregir en tiempo real lo que la barrera automática no puede
entender por sí sola.
