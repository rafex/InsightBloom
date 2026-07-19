# Hello World en Bash

La terminal en sí ya es Bash — no hace falta instalar nada para escribir y correr scripts.

## 1. Crear el archivo

```bash
nvim hola.sh
```

`i` para entrar en modo Insertar:

```bash
#!/bin/bash
echo "Hola mundo"
```

La primera línea (`#!/bin/bash`, el "shebang") le dice al sistema qué intérprete usar cuando el
script se ejecuta directamente.

## 2. Guardar y salir

`Esc`, después `:wq` y Enter.

## 3. Darle permiso de ejecución y correrlo

```bash
chmod +x hola.sh
./hola.sh
```

`chmod +x` marca el archivo como ejecutable — sin esto, `./hola.sh` da error de permiso denegado.

### Alternativa sin dar permisos

```bash
bash hola.sh
```

Esto le pasa el archivo directo al intérprete, sin necesitar que sea ejecutable.

## Tips

- `shellcheck hola.sh` (ya instalado) revisa el script y avisa de errores comunes antes de
  correrlo.
- Variables: `nombre="mundo"` y se usan como `echo "Hola $nombre"` (sin espacios alrededor del
  `=` al asignar).
- `fzf` (buscador difuso) y `ripgrep`/`rg` (búsqueda de texto rápida) ya están instalados y son
  mucho más cómodos que `find`/`grep` para uso diario en la terminal.
