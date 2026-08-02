# Cómo usar LazyVim en el IDE CLI

**LazyVim** es una configuración de Neovim orientada a trabajar como IDE: agrega un explorador
de archivos, buscador, autocompletado, navegación de código, diagnósticos y atajos consistentes.
No reemplaza los modos de Neovim: primero se aplican las reglas de **Normal**, **Insertar** y
**Visual** de la ayuda de Neovim básico.

## La tecla líder

En esta imagen la tecla líder es **Espacio** (`<Space>`). Muchos atajos se escriben como una
secuencia: por ejemplo, `<Space>ff` significa pulsar Espacio y después `f` dos veces.

Si no recuerdas un atajo, pulsa **Espacio** y espera un instante. LazyVim muestra el menú de
atajos disponibles para el modo y el contexto actual. También puedes pulsar `Esc` para volver a
modo Normal.

## Flujo recomendado

1. Abre el explorador con `<Space>e`.
2. Navega hasta un archivo y pulsa `Enter` para abrirlo.
3. Usa `i` para escribir y `Esc` para volver a Normal.
4. Guarda con `:w` o `Ctrl-s`.
5. Busca otro archivo con `<Space>ff`.

| Atajo | Acción |
|---|---|
| `<Space>e` | Abre o enfoca el explorador **neo-tree** |
| `<Space>ff` | Busca archivos del workspace |
| `<Space>sg` | Busca texto dentro del workspace |
| `<Space>fr` | Muestra archivos recientes |
| `<Space>gg` | Abre `lazygit` en el workspace |
| `<Space>,` | Cambia entre buffers abiertos |
| `<Space>bd` | Cierra el buffer actual |
| `:w` | Guarda el archivo |
| `:q` | Cierra la ventana actual |
| `:qa` | Cierra Neovim |

## Explorador de archivos

LazyVim usa **neo-tree** para navegar el workspace. Con el foco en el explorador:

| Tecla | Acción |
|---|---|
| `j` / `k` | Baja o sube |
| `Enter` | Abre el archivo o expande la carpeta |
| `a` | Crea un archivo o carpeta |
| `d` | Elimina el elemento seleccionado |
| `r` | Renombra |
| `R` | Actualiza el árbol |
| `h` | Cierra una carpeta expandida |
| `l` | Abre o expande |

Para crear una carpeta con `a`, termina el nombre con `/`. Revisa el nombre antes de confirmar
una eliminación: el cambio afecta directamente al workspace del evento.

## Ventanas, buffers y navegación de código

| Atajo | Acción |
|---|---|
| `Ctrl-w` `v` | Divide la ventana verticalmente |
| `Ctrl-w` `s` | Divide la ventana horizontalmente |
| `Ctrl-w` `h/j/k/l` | Mueve el foco entre ventanas |
| `Ctrl-w` `q` | Cierra la ventana actual |
| `Shift-h` / `Shift-l` | Buffer anterior o siguiente |
| `gd` | Va a la definición bajo el cursor |
| `gr` | Muestra referencias |
| `K` | Muestra la documentación del símbolo |
| `<Space>ca` | Acciones disponibles para el código |
| `<Space>cr` | Renombra el símbolo |
| `[d` / `]d` | Diagnóstico anterior o siguiente |

Los atajos de LSP funcionan cuando el servidor del lenguaje reconoce el archivo. Si todavía está
iniciando, espera unos segundos y vuelve a intentarlo.

## Terminal y comandos del proyecto

Puedes abrir una terminal desde Neovim con `:terminal`. Para trabajar en ella:

- Pulsa `i` para escribir comandos.
- Pulsa `Ctrl-\\` y después `Ctrl-n` para volver al modo Normal de la terminal.
- Usa `Ctrl-w` para cambiar entre la terminal y otras ventanas.
- Ejecuta `exit` para cerrar esa terminal.

El workspace ya incluye Git, Node.js, Python, Java, Make, Just y el comando `insightbloom`. Para
ver las operaciones de publicación usa:

```bash
insightbloom --help
```

Para publicar una página o una API revisa las ayudas **🌐 Publicar página web**, **🖥️ Desplegar
portal web** y **🚀 Desplegar API REST** de este mismo panel.

## Copiar texto y uso offline

La terminal web conserva la selección para que puedas copiarla manualmente desde el navegador.
Selecciona el texto con el ratón y usa el comando de copiar del navegador; no dependas de que
tmux envíe automáticamente la selección al portapapeles.

La imagen trae los plugins de LazyVim preparados para el sandbox. No ejecutes `:Lazy sync` ni
intentes instalar plugins desde Internet: el entorno puede estar sin salida de red y la
configuración está controlada por la imagen del evento.

## Si algo no responde

1. Pulsa `Esc` para volver a modo Normal.
2. Pulsa `Space` y espera el menú de atajos.
3. Ejecuta `:checkhealth` para revisar Neovim.
4. Si cerraste una ventana por accidente, vuelve a abrir el archivo con `<Space>ff`.
5. Si la terminal quedó en una pantalla extraña, pulsa `Ctrl-l` o abre otra con `:terminal`.

Los cambios se guardan en el workspace del evento. Antes de cerrar la pestaña, verifica que no
queden archivos modificados sin guardar.
