# TASK-0011: Variante Java
# Extiende base code-ide con JDK 21, Maven, y herramientas Java

FROM ghcr.io/rafex/insightbloom-code-ide:base

USER root

# Instalar JDK (temurin es lightweight, usado en otros Dockerfiles del proyecto)
# Alpine tiene eclipse-temurin disponible
RUN apk add --no-cache \
    openjdk21 \
    maven

# Volver a 'coder' apenas termina lo que requiere root (apk): extensiones y directorios
# se instalan/crean como usuario no-root, igual que code-server en la imagen base.
USER coder

# Instalar extensiones Java para code-server
RUN code-server --install-extension vscjava.extension-pack-for-java \
    --install-extension vscjava.vscode-maven

# Crear directorio para proyectos Maven
RUN mkdir -p /home/coder/.m2

# Workspace, db, y Maven cache directorios
EXPOSE 8080

ENTRYPOINT ["dumb-init", "--"]
CMD ["code-server", "--bind-addr", "0.0.0.0:8080", "/home/coder/workspace", "--disable-auth"]
