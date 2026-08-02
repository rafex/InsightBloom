# Neovim para quien nunca lo usó

Neovim tiene **modos**: en cada momento estás en uno solo, y las mismas teclas hacen cosas
distintas según el modo. Al entrar siempre estás en modo **Normal**.

Este capítulo explica los controles comunes de Vim/Neovim. Si elegiste **CLI · LazyVim**, revisa
también **✨ Cómo usar LazyVim**: agrega un explorador, buscador, atajos y ayudas de código sobre
estas mismas bases.

| Modo | Para qué sirve | Cómo entrar |
|---|---|---|
| Normal | Moverte, borrar, copiar, pegar, ejecutar comandos | `Esc` (desde cualquier otro modo) |
| Insertar | Escribir texto, como un editor normal | `i` (antes del cursor) o `a` (después) |
| Visual | Seleccionar texto | `v` (carácter), `V` (línea), `Ctrl-v` (bloque) |
| Comando | Guardar, salir, buscar/reemplazar | `:` desde modo Normal |

**La regla de oro**: si no sabés en qué modo estás, apretá `Esc`. Siempre te devuelve a Normal.

## Guardar y salir

Todo esto se escribe en modo Normal, empezando con `:`, y termina con Enter.

| Comando | Qué hace |
|---|---|
| `:w` | Guardar |
| `:q` | Salir (falla si hay cambios sin guardar) |
| `:wq` o `:x` | Guardar y salir |
| `:q!` | Salir SIN guardar, descartando cambios |

## Moverte (modo Normal)

| Tecla | Mueve el cursor |
|---|---|
| `h` `j` `k` `l` | izquierda / abajo / arriba / derecha |
| `w` | al inicio de la próxima palabra |
| `b` | al inicio de la palabra anterior |
| `0` | al inicio de la línea |
| `$` | al final de la línea |
| `gg` | al inicio del archivo |
| `G` | al final del archivo |
| `Ctrl-d` / `Ctrl-u` | media pantalla abajo / arriba |

## Editar (modo Normal)

| Tecla | Qué hace |
|---|---|
| `x` | borra el carácter bajo el cursor |
| `dd` | borra la línea completa |
| `yy` | copia ("yank") la línea completa |
| `p` | pega después del cursor/línea |
| `u` | deshacer |
| `Ctrl-r` | rehacer |
| `.` | repite el último cambio |

## Buscar y reemplazar

| Comando | Qué hace |
|---|---|
| `/palabra` + Enter | busca hacia adelante |
| `n` / `N` | siguiente / anterior resultado |
| `:%s/vieja/nueva/g` | reemplaza todas las ocurrencias en el archivo |
| `:%s/vieja/nueva/gc` | igual, pero pide confirmar cada una |

## El explorador de archivos (Neovim estable)

La imagen CLI de Neovim estable trae **nvim-tree** instalado y configurado. En LazyVim el
explorador es **neo-tree** y sus atajos están documentados en la sección específica de LazyVim.

| Tecla | Qué hace |
|---|---|
| `Ctrl-n` | abre/cierra el árbol de archivos |
| `j` / `k` | moverse abajo / arriba en el árbol |
| `Enter` o `<CR>` (sobre un directorio) | lo abre/expande |
| `Enter` o `<CR>` (sobre un archivo) | lo abre |
| `o` | igual que Enter: abre archivo o expande directorio |
| `l` | igual que Enter/o: abre el directorio |
| `h` | cierra el directorio (vuelve al padre) |
| `-` | sube al directorio padre |
| `r` | refresca la vista |
| `a` | crea un archivo/carpeta nuevo (te pregunta el nombre) |
| `d` | borra el archivo/carpeta bajo el cursor |
| `R` | renombra |

Para crear un directorio en vez de un archivo, en el prompt de `a` terminá el nombre con `/`
(por ejemplo `carpeta/`).

Comandos equivalentes desde modo Comando (`:`), por si preferís no usar el atajo:

| Comando | Qué hace |
|---|---|
| `:NvimTreeToggle` | abre/cierra el explorador |
| `:NvimTreeFocus` | pone el foco en el explorador |
| `:NvimTreeRefresh` | refresca la vista |

También podés abrir cualquier archivo por su ruta sin pasar por el árbol: `:e ruta/al/archivo`.

## Ventanas divididas (splits)

| Comando | Qué hace |
|---|---|
| `:vsplit` o `Ctrl-w` `v` | divide la ventana verticalmente (lado a lado) |
| `:split` o `Ctrl-w` `s` | divide la ventana horizontalmente (una encima de otra) |
| `Ctrl-w` + flecha | mueve el foco entre ventanas |
| `Ctrl-w` `q` | cierra la ventana actual |

Moverte entre ventanas con `hjkl` (sin flechas):

| Tecla | Qué hace |
|---|---|
| `Ctrl-w` `h` | mover el foco a la ventana de la izquierda |
| `Ctrl-w` `j` | mover el foco a la ventana de abajo |
| `Ctrl-w` `k` | mover el foco a la ventana de arriba |
| `Ctrl-w` `l` | mover el foco a la ventana de la derecha |

Ejemplo visual de cada tipo de split:

```
Vertical (Ctrl-w v):          Horizontal (Ctrl-w s):
┌──────────┬──────────┐       ┌────────────────────┐
│ Ventana1 │ Ventana2 │       │      Ventana 1      │
│          │          │       ├────────────────────┤
└──────────┴──────────┘       │      Ventana 2      │
                               └────────────────────┘
```

### Abrir una terminal dentro de un split

```
Ctrl-w s        " abre un split horizontal
:terminal       " y adentro, una terminal

" o en un solo paso:
:split term://bash    " terminal en split horizontal
:vsplit term://bash   " terminal en split vertical
```

### Cerrar ventanas

| Comando | Qué hace |
|---|---|
| `:q` o `Ctrl-w` `q` | cierra la ventana actual |
| `Ctrl-w` `c` | cierra la ventana actual (sin confirmar) |
| `Ctrl-w` `o` o `:only` | cierra todas las ventanas menos la actual |

### Mover una ventana de lugar (mayúsculas)

Con el foco en la ventana que querés mover:

| Comando | Qué hace |
|---|---|
| `Ctrl-w` `J` | mueve la ventana actual hacia abajo |
| `Ctrl-w` `K` | mueve la ventana actual hacia arriba |
| `Ctrl-w` `H` | mueve la ventana actual a la izquierda |
| `Ctrl-w` `L` | mueve la ventana actual a la derecha |
| `Ctrl-w` `r` | rota (intercambia) las ventanas |
| `Ctrl-w` `T` | mueve la ventana actual a una pestaña nueva |

Ejemplo: tenés un archivo arriba y una terminal abajo (`Ctrl-w s` + `:terminal`), y querés que la
terminal quede arriba:

```
Ctrl-w k    " mové el cursor a la ventana de arriba... no, mejor:
Ctrl-w j    " mové el cursor A la terminal (que está abajo)
Ctrl-w K    " y ahora sí, mové ESA ventana (mayúscula K) hacia arriba
```

### Cambiar el tamaño de las ventanas

| Comando | Qué hace |
|---|---|
| `Ctrl-w` `+` | aumenta el tamaño de la ventana actual |
| `Ctrl-w` `-` | disminuye el tamaño de la ventana actual |
| `Ctrl-w` `=` | iguala el tamaño de todas las ventanas |
| `Ctrl-w` `>` | aumenta el ancho (en un split vertical) |
| `Ctrl-w` `<` | disminuye el ancho (en un split vertical) |

## Autocompletado y LSP (nvim-cmp)

Mientras escribís código, aparecen sugerencias automáticamente.

| Tecla | Qué hace |
|---|---|
| `Ctrl-n` / `Ctrl-p` (en modo Insertar, con el menú abierto) | siguiente / anterior sugerencia |
| `Enter` o `Tab` | acepta la sugerencia seleccionada |
| `Ctrl-e` | cierra el menú sin aceptar nada |

## La terminal en sí (tmux)

Esta sesión corre dentro de **tmux**, lo que significa que si se corta la conexión (WiFi, se
cierra la pestaña, se suspende la laptop) y volvés a entrar, **retomás exactamente donde
quedaste** — no perdés nada de lo que tenías abierto.

| Atajo (con `Ctrl-b` como prefijo) | Qué hace |
|---|---|
| `Ctrl-b` luego `d` | te "despegás" (detach) de la sesión sin cerrarla |
| `Ctrl-b` luego `c` | crea una ventana nueva dentro de la misma sesión |
| `Ctrl-b` luego `n` / `p` | siguiente / anterior ventana |

No hace falta usar esto activamente — pasa solo. Es lo que hace que tu terminal "sobreviva" a
los cortes de red.
