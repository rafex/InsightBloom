# OTP Login (autenticación por código al correo)

Spec dedicada al login opcional por código de un solo uso (OTP) enviado al correo
registrado, activable por cada usuario desde su perfil, con backlog de 2FA con app
autenticadora (TOTP).

## Contenido

- `SPEC.md`: estado actual real del código (hay más infraestructura de OTP ya
  construida de lo que parece a simple vista), diseño de la preferencia por usuario,
  casos de uso y endpoints nuevos, cambios de frontend (login + perfil), plantilla de
  correo, checklist de hardening de seguridad, y el backlog de TOTP.

## Cuando leer esta spec

- Si vas a implementar el toggle de método de acceso en el perfil.
- Si vas a tocar `LoginUseCase`, `AuthHandler`, `SendOtpUseCase` o `VerifyOtpUseCase`.
- Si vas a evaluar agregar 2FA con app autenticadora (TOTP) — leer primero la sección
  "Backlog: TOTP" antes de empezar, para no duplicar lo que ya deja preparado esta spec.

## Cómo trabajar esta spec

Igual que en `docs/specs/*` en general: es un cambio que toca backend y frontend, así
que amerita su propia rama y worktree dedicados en vez de trabajarse en el checkout
principal. Ver la sección 0 de `SPEC.md`.
