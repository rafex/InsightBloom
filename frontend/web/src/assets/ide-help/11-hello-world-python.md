# Hello World en Python

Esta imagen trae **Python 3.12** ya instalado, con `pip` listo para instalar librerías
(`numpy`, `pandas`, `flask`, `fastapi`, `pytest` ya vienen preinstalados).

## 1. Crear el archivo

```bash
nvim hola.py
```

Apretá `i` para entrar en modo Insertar y escribí:

```python
print("Hola mundo")
```

## 2. Guardar y salir

`Esc` para modo Normal, después `:wq` y Enter.

## 3. Ejecutar

```bash
python3 hola.py
```

Deberías ver:

```
Hola mundo
```

## Tips

- No hace falta compilar nada — Python se ejecuta directo.
- Si tu script necesita una librería que no está instalada: `pip install nombre-libreria`.
- Para proyectos más grandes, `virtualenv` ya está instalado si querés aislar dependencias por
  proyecto (`python3 -m venv .venv && source .venv/bin/activate`).
- `black` (formateador) y `pylint` (linter) ya están instalados si querés mantener el código
  prolijo.
