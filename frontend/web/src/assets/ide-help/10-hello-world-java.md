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
