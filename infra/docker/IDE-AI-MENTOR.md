# Tutor IA de programación

Si el facilitador lo habilitó para este evento, puedes pedir pistas al tutor sin salir del IDE:

```bash
insightbloom mentor
```

También puedes hacer una pregunta única y adjuntar el archivo que estás revisando:

```bash
insightbloom mentor "¿Qué debería comprobar primero?" --file src/app.js
```

La sesión se obtiene automáticamente dentro del sandbox. Si no hay una sesión guardada,
`insightbloom login` solicita usuario y contraseña sin mostrarlos en pantalla. Solo el token
se guarda fuera del workspace, en `~/.config/insightbloom/session.json`; la contraseña nunca se
almacena. Si el token caduca, el comando solicita iniciar sesión de nuevo y reintenta una vez.
También puedes usar `--token-prompt` para introducir un token puntual sin guardarlo.

El tutor recibe el objetivo del evento y, si el facilitador lo permite, el contexto de la
presentación. Está diseñado para enseñar: hace preguntas y da pistas progresivas, no entrega
la solución completa del ejercicio. No pegues contraseñas, tokens, claves SSH ni archivos `.env`.
