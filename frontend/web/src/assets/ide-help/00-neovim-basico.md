# Neovim para quien nunca lo usó

Neovim tiene **modos**: en cada momento estás en uno solo, y las mismas teclas hacen cosas
distintas según el modo. Al entrar siempre estás en modo **Normal**.

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

## El explorador de archivos (nvim-tree)

Esta imagen ya trae **nvim-tree** instalado y configurado.

| Tecla | Qué hace |
|---|---|
| `Ctrl-n` | abre/cierra el árbol de archivos |
| `Enter` (sobre un archivo) | lo abre |
| `a` | crea un archivo/carpeta nuevo |
| `d` | borra el archivo bajo el cursor |
| `r` | renombra |

## Ventanas divididas (splits)

| Comando | Qué hace |
|---|---|
| `:vsplit` | divide la ventana verticalmente |
| `:split` | divide la ventana horizontalmente |
| `Ctrl-w` + flecha | mueve el foco entre ventanas |
| `Ctrl-w` `q` | cierra la ventana actual |

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
