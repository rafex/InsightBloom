# Desplegar un portal web (sitio estático)

Guía paso a paso para llevar un sitio con varias páginas desde cero hasta una URL pública que
podés compartir. Si ya sabés lo que es `insightbloom publish` y solo buscás la referencia de
comandos, mirá la pestaña "🌐 Publicar página web".

## 1. Armar la estructura del portal

En `~/workspace`, creá al menos dos páginas enlazadas entre sí y una hoja de estilos:

```bash
nvim index.html
```

```html
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Mi portal</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <h1>Bienvenido</h1>
  <p>Este es mi portal de prueba.</p>
  <nav><a href="sobre.html">Sobre mí</a></nav>
</body>
</html>
```

`Esc`, `:wq`, Enter. Repetí con la segunda página:

```bash
nvim sobre.html
```

```html
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Sobre mí</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <h1>Sobre mí</h1>
  <p>Acá contás quién sos.</p>
  <nav><a href="index.html">← Volver</a></nav>
</body>
</html>
```

Y el CSS:

```bash
nvim style.css
```

```css
body { font-family: system-ui, sans-serif; max-width: 640px; margin: 40px auto; padding: 0 16px; }
nav { margin-top: 24px; }
```

**Punto clave**: `index.html` en la raíz del workspace (o de la carpeta que vayas a publicar) es
obligatorio — es la página que se sirve primero. Las demás páginas (`sobre.html`, etc.) y sus
enlaces relativos (`href="sobre.html"`, `href="style.css"`) funcionan igual que en cualquier
hosting estático.

## 2. Revisar el resultado antes de publicar

No hace falta levantar ningún servidor para chequear que los archivos estén bien: podés abrir
`index.html` en Neovim y repasar los enlaces, o usar `cat` para ver el contenido de cada archivo.
Publicar es rápido y revocable, así que también podés iterar publicando de nuevo si algo no
quedó como esperabas.

## 3. Publicar

Desde el Web IDE, el botón más simple:

**🌐 Publicar página temporal** (arriba en la pantalla del IDE).

Desde el terminal, si no iniciaste sesión todavía:

```bash
insightbloom login
```

Y publicá el workspace:

```bash
insightbloom publish
```

Si tu portal vive dentro de una subcarpeta (por ejemplo `mi-sitio/`), indicalo:

```bash
insightbloom publish --root mi-sitio --token-prompt
```

## 4. Verificar desde afuera

El resultado del comando (o del botón) incluye la URL pública. Abrila en una pestaña nueva del
navegador y navegá entre `index.html` y `sobre.html` con el link — si los enlaces relativos están
bien armados, la navegación funciona igual que en tu portal local.

## 5. Actualizar o revocar

Volver a publicar (`insightbloom publish`) reemplaza el contenido de la URL anterior con la
versión actual del workspace — no hace falta revocar antes de actualizar.

Para cortar el acceso público sin volver a publicar:

```bash
insightbloom revoke PUBLICATION_ID --token-prompt
```

## Limitaciones a tener en cuenta

Esta publicación es una **copia estática**: sirve HTML/CSS/JS/imágenes tal cual están en el
workspace en el momento de publicar. No ejecuta `package.json`, no corre un build de
Vite/Webpack/etc. y no admite backend, APIs ni WebSockets — para eso existe la publicación de
backend/API, ver la pestaña "🚀 Desplegar API REST".

Si tu portal necesita un paso de build (por ejemplo, un proyecto de React con Vite), corré el
build vos mismo dentro del sandbox antes de publicar (`npm run build`) y publicá la carpeta de
salida:

```bash
npm run build
insightbloom publish --root dist --token-prompt
```
