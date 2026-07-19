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
