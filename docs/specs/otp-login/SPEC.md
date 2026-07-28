# SPEC.md — OTP Login

> Dirigido a quien (persona o agente de IA) implemente esta spec. Antes de escribir
> código, leer completa la sección 1 — hay infraestructura de OTP ya construida en el
> repo que hay que **reutilizar**, no reinventar.

## 0. Cómo trabajar esta spec: worktree y rama

Igual criterio que en `frontend/web/docs/DESIGN_SYSTEM_MIGRATION.md` sección 0 (mismo
repo, mismo motivo: cambio multi-área que no debe trabajarse en el checkout principal).

```bash
git fetch origin
git worktree add ../InsightBloom-otp-login -b feature/otp-login origin/main
```

- Rama: `feature/otp-login`, desde `origin/main` actualizado.
- Worktree: `../InsightBloom-otp-login` (hermano del checkout principal, nunca anidado).
- Backend: `cd ../InsightBloom-otp-login/backend/services/insightbloom-users && mvn -q -o compile`
  para verificar que el módulo compila antes de empezar a tocar código.
- Frontend: `cd ../InsightBloom-otp-login/frontend/web && npm install`.
- Push de la rama + PR contra `main` al terminar — no push directo a `main` (superficie
  de cambio: modelo de usuario, login, endpoints de auth — amerita revisión antes de
  integrar, a diferencia del flujo directo usado en trabajo rutinario de este repo).
- Si esta spec y la migración del design system (`DESIGN_SYSTEM_MIGRATION.md`) se
  trabajan en paralelo, son dos worktrees y dos ramas totalmente independientes — no
  comparten archivos (una toca `insightbloom-users` + páginas de login/perfil, la otra
  toca páginas de dashboard/formularios en general), así que no hay riesgo de conflicto
  entre ambas.
- Al mergear: `git worktree remove ../InsightBloom-otp-login` y borrar la rama.

## 1. Estado actual — leer antes de diseñar nada

**Ya existe infraestructura de OTP funcionando en el repo**, aunque hoy solo se usa
para verificar el correo/teléfono al registrarse, no para iniciar sesión. No hace falta
construir el envío de códigos, el modelo de datos del código, ni el email transaccional
desde cero — hay que **extender** esto para el caso de uso de login:

- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/model/OtpChannel.java`
  — enum `EMAIL`, `SMS`.
- `.../domain/model/OtpCode.java` — código de 6 dígitos, `expiresAt`, `consumed`,
  `isValid(candidateCode)`.
- `.../domain/ports/OtpCodeRepository.java` — `save`, `findLatestActive(identifier)`,
  `markConsumed(uuid)`. Implementación SQLite en
  `.../adapters/outbound/sqlite/SqliteOtpCodeRepository.java`.
- `.../application/usecases/SendOtpUseCase.java` — genera el código de 6 dígitos,
  lo guarda con `expiresAt = now + 10 min`, y lo manda por `EmailPort` (texto plano
  hoy, ver tarea 6) o `SmsPort` según el canal.
- `.../application/usecases/VerifyOtpUseCase.java` — valida el código, lo marca
  consumido, marca `emailVerified`/`phoneVerified` en el `User`, y — dato importante —
  **ya emite un token de sesión completo** (`Token` vía `TokenService.issueUserToken`)
  igual que `LoginUseCase`. Es decir: el mecanismo para "verificar un código y devolver
  una sesión válida" ya está resuelto, solo hay que decidir cuándo se expone como
  login en sí (ver sección 3).
- Endpoints ya registrados en `AuthHandler.java`: `POST /auth/otp/send` y
  `POST /auth/otp/verify` (líneas ~69-70 y ~193-ss/278-ss).
- Frontend: `frontend/web/src/services/api/authApi.ts` ya tiene `sendOtp(identifier,
  channel)` y `verifyOtp(identifier, code)`. **Hoy solo se usan desde
  `frontend/web/src/pages/login/RegisterPage.vue`**, para el paso de verificación
  al registrarse — `LoginPage.vue` no los usa en absoluto.

**Lo que falta** (y es el objeto real de esta spec) no es "OTP" en general — es: (a)
una preferencia de método de acceso por usuario, configurable desde el perfil, (b) que
`LoginPage.vue` respete esa preferencia y ofrezca el flujo OTP como alternativa al
password, y (c) separar el flujo de "OTP para verificar registro" del flujo de "OTP
para iniciar sesión" para no acoplar ni debilitar ninguno de los dos por accidente
(ver sección 3.3).

## 2. Modelo de datos

### 2.1 `User` — nueva preferencia de método de acceso

Agregar a `domain/model/User.java` un campo:

```java
private AuthMethod authMethod = AuthMethod.PASSWORD; // default explícito
```

Nuevo enum `domain/model/AuthMethod.java`:

```java
public enum AuthMethod { PASSWORD, OTP_EMAIL }
```

(Se llama `AuthMethod` y no reutiliza `OtpChannel` porque conceptualmente son cosas
distintas: `OtpChannel` es "por dónde se manda un código puntual", `AuthMethod` es "cuál
es el mecanismo de acceso permanente de la cuenta". `OTP_EMAIL` dejar el nombre así —no
solo `OTP`— para que agregar `OTP_SMS` en el futuro sea un enum value nuevo, no una
migración de significado del existente.)

### 2.2 Columna en SQLite

Seguir el patrón ya usado en `adapters/outbound/sqlite/DatabaseManager.java` (bloque de
migraciones con `ALTER TABLE ... ADD COLUMN`, envuelto en `try/catch` para tolerar que
ya exista si el método corre más de una vez — ver el `ALTER TABLE users ADD COLUMN
password_hash TEXT` ya presente ahí mismo como ejemplo exacto a copiar):

```java
try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN auth_method TEXT NOT NULL DEFAULT 'PASSWORD'"); }
catch (final SQLException ignored) { /* columna ya existe */ }
```

El `DEFAULT 'PASSWORD'` en la propia columna es importante: así ningún usuario
existente queda con un valor nulo/ambiguo tras la migración — todos arrancan en
`PASSWORD` (el comportamiento de hoy) hasta que activen OTP explícitamente.

### 2.3 Repositorio

`SqliteUserRepository` (o donde se mapee la fila `users` a `User`) necesita leer/
escribir la columna nueva igual que hace con `password_hash`, `email_verified`, etc.

## 3. Casos de uso

### 3.1 Nuevo: `SetAuthMethodUseCase`

Cambia la preferencia de un usuario. **Requiere confirmar la contraseña actual antes
de aplicar el cambio**, en ambas direcciones (activar OTP y volver a password) — es una
decisión de seguridad de la cuenta, no una preferencia cosmética: si una sesión
robada pudiera desactivar el password sin volver a pedirlo, estaría abriendo una
puerta trasera permanente sin que el dueño real se entere. Firma sugerida:

```java
public record Request(String userUuid, String currentPassword, AuthMethod newMethod) {}
```

Valida `currentPassword` contra `user.getPasswordHash()` con el `PasswordService` ya
existente (mismo que usa `LoginUseCase`/`ChangePasswordUseCase`) antes de guardar el
cambio. Si el usuario nunca tuvo contraseña (cuenta creada solo por OTP, caso futuro
hipotético — hoy todo usuario se registra con password) esto no aplica; no es un caso
real con el flujo de registro actual, no hace falta contemplarlo todavía.

### 3.2 `LoginUseCase` — rechazar password cuando la cuenta usa OTP

Agregar al inicio de `execute(...)`, después de encontrar al usuario:

```java
if (u.getAuthMethod() == AuthMethod.OTP_EMAIL) return Optional.empty(); // usar /auth/login/otp/*
```

Esto es la parte que hace que activar OTP sea una mejora real de seguridad y no solo
una alternativa cosmética: **una vez activado, el password deja de servir para entrar**
(igual sigue existiendo en la base — para poder volver a `PASSWORD` desde el perfil sin
tener que "recrear" la contraseña — pero `LoginUseCase` ya no lo acepta). El código de
error debe ser específico (no el genérico `credentials_invalid`) para que el frontend
pueda mostrar "Esta cuenta usa código de acceso, no contraseña" en vez de "contraseña
incorrecta" — agregar un motivo distinguible en el `Optional` o lanzar una excepción
tipada nueva (`OtpAuthRequiredException`), coherente con el patrón que ya usa
`PlatformDeviceBlockedException` un poco más abajo en el mismo método.

### 3.3 Nuevos: `RequestLoginOtpUseCase` y `VerifyLoginOtpUseCase`

**No reutilizar `SendOtpUseCase`/`VerifyOtpUseCase` tal cual para login.** Esos dos ya
se usan para verificar el correo/teléfono al registrarse, y ese flujo debe seguir
funcionando exactamente igual (cualquier identificador con formato válido puede pedir
un código ahí, es parte de probar que el dato es tuyo). El flujo de login OTP tiene una
regla adicional que el flujo de registro no debe tener: **solo debe emitir/aceptar
código si `user.getAuthMethod() == AuthMethod.OTP_EMAIL`** — si se agrega esa regla
directamente a `SendOtpUseCase`, se rompe la verificación de registro para cualquier
cuenta nueva (que todavía está en `PASSWORD` por default en ese momento).

Dos casos de uso nuevos, reutilizando `OtpCodeRepository`/`EmailPort` pero con su
propia validación:

```java
public class RequestLoginOtpUseCase {
    // valida: usuario existe, status ACTIVE, authMethod == OTP_EMAIL
    // genera y envía el código igual que SendOtpUseCase (mismo formato, mismo TTL)
    // SIEMPRE responde éxito aunque el identificador no exista o no use OTP
    // (ver 5.3 — prevenir enumeración de cuentas)
}

public class VerifyLoginOtpUseCase {
    // valida el código igual que VerifyOtpUseCase
    // exige ademas authMethod == OTP_EMAIL (no solo que el codigo sea valido)
    // emite el Token de sesion igual que VerifyOtpUseCase/LoginUseCase
}
```

### 3.4 Nuevo, opcional pero recomendado: `GetLoginMethodUseCase`

Para que `LoginPage.vue` sepa si mostrar el campo de contraseña o el flujo de código
sin que el usuario tenga que elegir a ciegas. Ver el tradeoff de enumeración de cuentas
en la sección 5.3 antes de implementarlo tal cual — la opción más simple seguro es NO
crear este endpoint y en cambio ofrecer ambos flujos como pestañas/opciones visibles en
`LoginPage.vue` desde el principio (el usuario elige "Contraseña" o "Código al correo"
sin que el sistema le confirme cuál corresponde a su cuenta) — más simple, y no filtra
información. Decisión de producto a confirmar antes de esta parte del frontend.

## 4. Endpoints nuevos (`AuthHandler.java`)

No reusar los paths `/otp/send` / `/otp/verify` existentes (esos siguen siendo del
flujo de registro). Agregar:

- `POST /auth/login/otp/request` — body `{ identifier }` → `RequestLoginOtpUseCase`.
  Responde siempre `200 { status: "sent" }` (ver 5.3).
- `POST /auth/login/otp/verify` — body `{ identifier, code, deviceFingerprint }` →
  `VerifyLoginOtpUseCase`. Mismo shape de respuesta que `handleLogin` (`token`,
  `userUuid`, `role`, `expiresAt`) para que el frontend pueda tratarlo igual que un
  login normal (mismo `authStore`, mismo guardado de sesión).
- `POST /auth/me/auth-method` (requiere `Authorization: Bearer`) — body
  `{ currentPassword, newMethod }` → `SetAuthMethodUseCase`. Vive en `AuthHandler.java`
  o en el handler de perfil existente (`UserProfileHandler` si ya administra otros
  cambios de cuenta) — usar el que ya maneje `ChangePasswordUseCase` como referencia de
  dónde encaja mejor.

Igual que `handleSendOtp`, propagar `IllegalStateException` (proveedor de email no
configurado) como `503`, y errores de validación como `400`.

## 5. Frontend

### 5.1 `LoginPage.vue`

Agregar una segunda vía de acceso junto al formulario de contraseña actual — dos
pestañas o un link "Usar código en vez de contraseña" (ver decisión pendiente de 3.4
sobre si el sistema detecta el método o el usuario elige):

- Paso 1: input de identificador (mismo campo `username` que ya existe, acepta
  username o email) + botón "Enviar código" → `POST /auth/login/otp/request`.
- Paso 2: input de 6 dígitos + botón "Verificar" → `POST /auth/login/otp/verify` →
  mismo manejo de éxito que `submitLogin` actual (guardar token en `authStore`,
  redirigir).
- Reusar el patrón de reenvío ya implementado en `RegisterPage.vue`
  (`resendOtp`/`sendOtpWithFallback`) como referencia de UX — no reinventar el
  cooldown/mensaje de "código reenviado".
- Si `LoginUseCase` devuelve el error específico de la sección 3.2 al intentar
  password en una cuenta OTP, mostrar un mensaje claro invitando a usar la otra
  pestaña, no un genérico "credenciales inválidas".

### 5.2 `ProfilePage.vue`

Nueva sección "Método de acceso" (junto a donde ya vive el cambio de contraseña, que
usa `changePassword` de `usersApi.ts` como referencia de patrón: modal o sección
inline que pide la contraseña actual):

- Mostrar el método activo (`Contraseña` / `Código por correo`).
- Botón para cambiar, que abre un campo para la contraseña actual (obligatorio en
  ambas direcciones, ver 3.1) y confirma el cambio.
- Mensaje de advertencia claro al activar OTP: "A partir de ahora vas a iniciar
  sesión con un código que te enviamos por correo, ya no con esta contraseña.
  Podés volver a contraseña en cualquier momento desde acá."

### 5.3 Prevención de enumeración de cuentas

`POST /auth/login/otp/request` **debe responder siempre `200 { status: "sent" }`**,
tanto si el identificador no existe como si existe pero usa `PASSWORD` — nunca debe
distinguir esos casos en la respuesta HTTP (si se distingue, cualquiera puede usar
este endpoint para descubrir qué correos/usuarios existen en la plataforma probando
identificadores al voleo). El envío real del email solo ocurre puertas adentro cuando
el usuario sí existe y sí usa `OTP_EMAIL`; en cualquier otro caso, no se manda nada
pero la respuesta es idéntica.

## 6. Plantilla de email

Reusar el patrón ya armado para el boleto (`TicketEmailTemplate.java`,
`TicketUseCase.sendEmail`/`EmailPort.sendHtml`, `ZohoEmailClient` en
`insightbloom-users`) en vez del `emailPort.send(...)` de texto plano que usa hoy
`SendOtpUseCase`. Crear `OtpEmailTemplate.java` con el mismo estilo visual (tabla +
estilos inline, fondo oscuro/acento morado) mostrando el código en tipografía grande y
un recordatorio de vencimiento a los 10 minutos. `RequestLoginOtpUseCase` usa
`emailPort.sendHtml(...)` con esta plantilla; el `SendOtpUseCase` de registro puede
migrarse a la misma plantilla (mismo estilo visual en toda la plataforma) como mejora
menor, no bloqueante para esta spec.

## 7. Hardening de seguridad (no opcional, hacer parte del mismo PR)

La infraestructura de `OtpCode` existente no tiene estas protecciones — agregarlas
al construir los casos de uso nuevos de login (y, si el tiempo alcanza, retrofitear
también al flujo de registro, aunque no es estrictamente parte de esta spec):

- **Límite de intentos de verificación**: hoy `OtpCode.isValid` se puede llamar sin
  límite hasta que expire (10 min) — un código de 6 dígitos son 1,000,000 de
  combinaciones, fuerza bruta acotada pero no despreciable en 10 minutos sin límite de
  intentos. Agregar un contador de intentos fallidos a `OtpCode` (columna
  `failed_attempts INTEGER DEFAULT 0`) y invalidar el código tras, por ejemplo, 5
  intentos fallidos.
- **Rate limit de envío**: `RequestLoginOtpUseCase` debe limitar cuántos códigos se
  pueden pedir por identificador en una ventana corta (por ejemplo, máximo 1 cada 60
  segundos, máximo 5 por hora) — si no, es un vector de email-bombing hacia cualquier
  usuario de la plataforma. Ver si ya existe un limitador reusable en el repo (memoria
  de sesiones previas menciona un rate limiter básico en un `AuthGateHandler` de otro
  servicio — confirmar si aplica acá o si hay que portar el patrón).
- **Comparación de el código en tiempo constante**: `OtpCode.isValid` usa
  `code.equals(candidateCode)`, que no es de tiempo constante — para un código de 6
  dígitos el riesgo real de timing attack es bajo, pero es una corrección barata
  (`MessageDigest.isEqual` sobre los bytes, o comparar dígito a dígito acumulando en un
  booleano) — hacerla si no representa complejidad extra relevante.
- **Auditoría**: loguear (sin exponer el código en texto plano en logs) cada solicitud
  y verificación de OTP de login, éxito y fallo, con timestamp e identificador — mismo
  nivel de trazabilidad que ya existe para login por password.

## 8. Testing

- Unit tests nuevos: `LoginUseCase` rechaza password cuando `authMethod=OTP_EMAIL`;
  `RequestLoginOtpUseCase`/`VerifyLoginOtpUseCase` con los casos de usuario
  inexistente, usuario en `PASSWORD`, código expirado, código ya consumido, límite de
  intentos alcanzado; `SetAuthMethodUseCase` rechaza sin contraseña actual correcta.
- `mvn -q -o test` en `insightbloom-users` debe seguir en verde.
- Frontend: si se agregan tests unitarios de `authApi.ts` para las funciones nuevas
  (`requestLoginOtp`, `verifyLoginOtp`, `setAuthMethod`), seguir el patrón ya usado en
  `src/services/api/__tests__/presentationsApi.test.js` u otros de esa misma carpeta.
- Verificación manual en navegador (ver `<preview_tools>` / flujo de este repo):
  1. Activar OTP desde el perfil de una cuenta de prueba.
  2. Confirmar que loguearse con la contraseña vieja ahora falla con el mensaje
     específico.
  3. Loguearse con el flujo de código completo (pedir código, revisar el correo real
     recibido, verificarlo).
  4. Volver a `PASSWORD` desde el perfil y confirmar que el login con contraseña
     vuelve a funcionar.

## 9. Backlog: TOTP (2FA con app autenticadora tipo Google Authenticator)

Fuera de alcance de esta spec — anotado acá para que quien lo tome después no tenga
que redescubrir el contexto ni pisar el trabajo de OTP por correo:

- Es un mecanismo **distinto** a OTP por correo: TOTP (RFC 6238) es un segundo factor
  que se combina CON la contraseña (algo que sabés + algo que tenés), no un reemplazo
  del login — a diferencia de `OTP_EMAIL` en esta spec, que si se activa reemplaza al
  password como único método.
- Requiere: generar y almacenar un secreto por usuario (columna nueva, cifrada en
  reposo — no guardar el secreto TOTP en texto plano), un endpoint para provisionarlo
  mostrando QR (formato `otpauth://totp/...`) para escanear con la app, un paso de
  confirmación (usuario ingresa un código válido antes de que el 2FA quede realmente
  activo, para no bloquearse a sí mismo por un secreto mal escaneado), códigos de
  respaldo de un solo uso para recuperación si se pierde el teléfono, y una vía para
  que un administrador de la plataforma pueda resetear el 2FA de un usuario que perdió
  el acceso (soporte).
- Puede convivir con `AuthMethod` de esta spec como un flag independiente
  (`totpEnabled: boolean`) en vez de un tercer valor del enum — un usuario podría en
  teoría tener `PASSWORD` + TOTP, o (más adelante) `OTP_EMAIL` + TOTP. Definir el
  modelo exacto cuando se tome esta iniciativa, no ahora.
- Librería sugerida a evaluar en su momento: cualquier implementación RFC 6238 estándar
  para Java (por ejemplo `com.warrenstrange:googleauth` o equivalente mantenido) — no
  implementar el algoritmo HMAC-based a mano.
