# InsightBloom Telegram — guía de configuración (Fase 1)

Bot de Telegram que espeja el chat web (`/dudas`, `/temas`) y reenvía notificaciones
(respuestas a dudas) a grupos de Telegram vinculados a una conferencia.

El servicio ya está desplegado en k3s (`insightbloom-telegram`, ingress en
`https://telegram-insightbloom.v1.rafex.cloud`). Lo que falta es la configuración
manual del lado de Telegram, que no se puede automatizar desde CI porque requiere
crear el bot a través de BotFather.

## 1. Crear el bot con BotFather

1. Abre una conversación con [@BotFather](https://t.me/BotFather) en Telegram.
2. Envía `/newbot` y sigue las instrucciones (nombre visible + username terminado en `bot`).
3. BotFather te entrega un **token** con el formato `123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ`. Guárdalo, es el `TELEGRAM_BOT_TOKEN`.
4. **No toques el privacy mode** (`/setprivacy`) — debe quedar en su valor por defecto, **Enabled**. Así el bot solo recibe mensajes que empiecen con `/comando`, nunca el resto de la conversación del grupo. Esto es una decisión deliberada (ver Fase 1 del plan), no un paso a configurar.

## 2. Generar el secreto del webhook

Genera un valor aleatorio que Telegram reenviará en cada request al webhook (header
`X-Telegram-Bot-Api-Secret-Token`), para que el endpoint pueda verificar que el
request viene realmente de Telegram y no de un tercero que adivinó la URL:

```bash
openssl rand -hex 24
```

Guarda ese valor como `TELEGRAM_WEBHOOK_SECRET`.

## 3. Crear los secretos en GitHub

En el repo: **Settings → Secrets and variables → Actions → New repository secret**.

| Nombre | Valor |
|---|---|
| `TELEGRAM_BOT_TOKEN` | El token de BotFather (paso 1) |
| `TELEGRAM_WEBHOOK_SECRET` | El valor generado en el paso 2 |

El siguiente deploy (`.github/workflows/deploy.yml`) los inyecta automáticamente en
el secret de k3s `insightbloom-telegram-secrets`. Si no los configuras, el deploy
sigue funcionando pero el bot no podrá llamar a la API de Telegram (`TELEGRAM_BOT_TOKEN`
vacío) y el webhook aceptará requests sin validar el secret (`TELEGRAM_WEBHOOK_SECRET`
vacío) — ambos casos quedan registrados como `::warning::` en el log del deploy, no
rompen el pipeline.

Tras configurarlos, dispara un deploy (push a `main`, o re-ejecutar el workflow
"Build and Publish Containers" manualmente) para que el secret de k3s se actualice
y el pod de `telegram` lo recoja en el siguiente reinicio.

## 4. Registrar el webhook ante Telegram

Una sola vez (no se repite en cada deploy), con el token y el secret ya guardados:

```bash
curl "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook" \
  -d "url=https://telegram-insightbloom.v1.rafex.cloud/telegram/webhook" \
  -d "secret_token=<TELEGRAM_WEBHOOK_SECRET>"
```

Respuesta esperada: `{"ok":true,"result":true,"description":"Webhook was set"}`.

Para verificar el estado del webhook en cualquier momento:

```bash
curl "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/getWebhookInfo"
```

## 5. Agregar el bot a un grupo

1. Crea o usa un grupo de Telegram existente para la conferencia.
2. Agrega el bot como miembro del grupo (buscar por su `@username`).
3. Hazlo **administrador** del grupo — lo necesita para verificar quién puede ejecutar
   `/conferencia` (vía `getChatMember`); sin ser admin, el bot puede no recibir todos
   los comandos en grupos con "Temas" (forum) activado.

## 6. Vincular el grupo a una conferencia

Dentro del grupo (solo administradores del grupo pueden hacerlo):

```
/conferencia <friendlyId|shortCode|uuid> chat
/conferencia <friendlyId|shortCode|uuid> notificaciones
```

Puedes ejecutar ambos comandos en el mismo grupo (o en distintos "Temas" del mismo
grupo, si tiene forums activado) para separar el chat en vivo de las notificaciones.

## 7. Uso

Una vez vinculado con `purpose=chat`:

```
/dudas <una_palabra> <descripción hasta 300 caracteres>
/temas <una_palabra> <descripción hasta 300 caracteres>
```

Estos comandos llegan a `insightbloom-ingest` igual que `/dudas`/`#temas` desde el
chat web. El grupo vinculado con `purpose=notificaciones` recibe un mensaje
automático cuando el organizador responde una duda de esa conferencia desde el
dashboard (además del correo, no en su lugar).

## Notas

- **`#temas` (con `#`) no funciona en Telegram** — ahí el comando es `/temas` (con `/`).
  Es así a propósito: con el privacy mode en *Enabled*, Telegram solo entrega al
  webhook mensajes que empiezan con `/`.
- La administración remota de la plataforma (moderación, gestión de usuarios) desde
  Telegram queda explícitamente fuera de esta fase — ver el plan para el motivo.
