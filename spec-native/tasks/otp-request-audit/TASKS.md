# Tareas: Trazabilidad de solicitudes OTP

- [x] Guardar eventos internos sin conservar identificador recibido ni código OTP.
- [x] Hacer que el endpoint público responda genéricamente ante cuenta no elegible y fallos SMTP.
- [x] No activar ni contar un OTP pendiente hasta que SMTP acepte el envío.
- [x] Registrar IP de cliente desde el último salto de X-Forwarded-For del Ingress y User-Agent.
- [x] Exponer consulta filtrada/paginada protegida por rol exacto `admin`.
- [x] Limpiar automáticamente los eventos con más de 30 días.
- [x] Registrar decisión y contratos en SpecNative.
- [ ] Ejecutar CI en `main` y verificar despliegue/promoción GitOps.
