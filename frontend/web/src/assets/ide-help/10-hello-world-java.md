# Hello World en Java

Esta imagen trae **Java 25 (Temurin)** ya instalado — `java` y `javac` funcionan directo, sin
instalar nada.

## 1. Crear el archivo

En la terminal, dentro de `~/workspace`:

```bash
nvim Hello.java
```

Esto abre Neovim con un archivo nuevo. Apretá `i` para entrar en modo Insertar y escribí:

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hola mundo");
    }
}
```

**Importante**: el nombre de la clase (`Hello`) tiene que coincidir con el nombre del archivo
(`Hello.java`) — es una regla del lenguaje, no de esta imagen.

## 2. Guardar y salir de Neovim

Apretá `Esc` para volver a modo Normal, después escribí `:wq` y Enter.

## 3. Compilar y ejecutar

De vuelta en la terminal:

```bash
javac Hello.java
java Hello
```

`javac` genera `Hello.class` (el bytecode compilado); `java` lo ejecuta. Deberías ver:

```
Hola mundo
```

## Tips

- `javac *.java` compila todos los `.java` del directorio actual de una vez.
- Si tu proyecto crece, esta imagen también trae **Maven** (`mvn`) para manejar dependencias y
  builds más grandes.
- El autocompletado de Neovim (`nvim-cmp` + LSP de Java) sugiere métodos y clases mientras
  escribís — no hace falta memorizar toda la API.

## Hello World de API REST (con la librería estándar)

No hace falta ningún framework para levantar un servidor HTTP en Java: el JDK trae
`com.sun.net.httpserver.HttpServer` incluido.

```bash
nvim ApiHello.java
```

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class ApiHello {
    public static void main(String[] args) throws Exception {
        // El puerto NO se elige a mano: cuando publicás tu API con "insightbloom app-publish"
        // (ver la sección "Publicar página web" -> backend/API), el sandbox ya te asignó un
        // puerto y te lo pasa en la variable de entorno APP_PORT. Si no está definida (por
        // ejemplo, mientras probás localmente antes de publicar), 8000 es un valor de respaldo.
        int port = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8000"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/hello", exchange -> {
            byte[] body = "{\"mensaje\":\"Hola mundo\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.out.println("Escuchando en el puerto " + port);
    }
}
```

```bash
javac ApiHello.java
java ApiHello
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
