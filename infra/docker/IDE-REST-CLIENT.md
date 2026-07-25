# Probar APIs REST desde el IDE

## Web IDE

El Web IDE trae la extensión [**REST Client**](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)
(`humao.rest-client`). No necesitas instalar Postman ni levantar otro programa.

1. Crea `requests.http` o `requests.rest` en el workspace.
2. Escribe una solicitud HTTP.
3. Pulsa **Send Request** sobre la solicitud o usa `Ctrl+Alt+R`.
4. Revisa la respuesta en el panel del editor.

```http
GET https://example.com/health

###

POST https://example.com/api/items
Content-Type: application/json

{
  "name": "demo"
}
```

Puedes separar varias solicitudes con `###`. La extensión también admite variables y archivos
de entorno; no guardes tokens, contraseñas ni claves en el workspace.

## IDE CLI

El cliente de terminal **Posting** ya está instalado globalmente:

```bash
posting
```

Posting permite explorar y enviar solicitudes HTTP desde la terminal. Para pruebas rápidas
también están disponibles `http` (HTTPie) y `curl`.

## Red del sandbox

Los clientes REST no abren puertos ni cambian las reglas de red. Las solicitudes obedecen la
NetworkPolicy y el proxy de egress del evento: por defecto no hay salida directa a Internet y,
si el organizador habilita el acceso controlado, solo funcionan los hosts de la lista blanca
definida por la plataforma; la lista negra siempre tiene prioridad.
