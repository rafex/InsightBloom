# Hello World en JavaScript y TypeScript

Esta imagen trae **Node.js 24 LTS** (con `npm`) ya instalado. TypeScript (`tsc`, `ts-node`) y
herramientas de frontend (`vite`, `webpack`, `eslint`, `prettier`) también vienen preinstaladas
de forma global.

## JavaScript

### 1. Crear el archivo

```bash
nvim hola.js
```

`i` para entrar en modo Insertar:

```javascript
console.log("Hola mundo")
```

### 2. Guardar y salir

`Esc`, después `:wq` y Enter.

### 3. Ejecutar

```bash
node hola.js
```

## TypeScript

### 1. Crear el archivo

```bash
nvim hola.ts
```

```typescript
const mensaje: string = "Hola mundo"
console.log(mensaje)
```

`Esc`, `:wq`, Enter.

### 2. Ejecutar directo (sin compilar)

```bash
npx ts-node hola.ts
```

### 3. O compilar primero a JavaScript

```bash
tsc hola.ts
node hola.js
```

`tsc` genera `hola.js` a partir de `hola.ts` — útil cuando querés distribuir el código compilado
sin depender de `ts-node`.

## Tips

- `npm init -y` crea un `package.json` vacío si querés armar un proyecto con dependencias.
- `create-vite` y `create-react-app` ya están instalados si vas a arrancar un proyecto de
  frontend desde cero.

## Hello World de API REST (con el módulo `http` de Node)

No hace falta Express ni ningún framework para lo mínimo: el módulo `http`, incluido en Node,
alcanza.

```bash
nvim api-hola.js
```

```javascript
const http = require("http")

// El puerto NO se elige a mano: cuando publicás tu API con "insightbloom app-publish" (ver la
// sección "Publicar página web" -> backend/API), el sandbox ya te asignó un puerto y te lo pasa
// en la variable de entorno APP_PORT. Si no está definida (por ejemplo, mientras probás
// localmente antes de publicar), 8000 es un valor de respaldo.
const port = process.env.APP_PORT || 8000

const server = http.createServer((req, res) => {
  if (req.url === "/hello") {
    res.writeHead(200, { "Content-Type": "application/json" })
    res.end(JSON.stringify({ mensaje: "Hola mundo" }))
  } else {
    res.writeHead(404)
    res.end()
  }
})

server.listen(port, () => console.log(`Escuchando en el puerto ${port}`))
```

```bash
node api-hola.js
```

Probalo desde otra pestaña de terminal (`Ctrl+B` `"` en tmux para dividir, o `Ctrl+B` `%`):

```bash
curl "http://localhost:$APP_PORT/hello"
```

Para probarlo desde AFUERA del sandbox, primero publicalo (ver "Publicar página web" en este
mismo panel de ayuda, sección backend/API): `insightbloom app-publish` te da una URL pública y un
token. Con esos dos datos:

```bash
curl -H "X-Preview-Token: TU_TOKEN" "https://app-insightbloom.v1.rafex.cloud/p/TU_PUBLICATION_ID/hello"
```
