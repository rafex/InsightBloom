# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Builder: compila todos los servicios con Maven Wrapper
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

# unzip es necesario para que el Maven Wrapper extraiga la distribución
RUN apk add --no-cache unzip

WORKDIR /workspace

# Maven Wrapper raíz (cacheado antes del código fuente)
COPY .mvn/   .mvn/
COPY mvnw    ./
RUN  chmod +x mvnw

# POMs de raíz y módulos (capa de caché de dependencias)
COPY pom.xml ./
COPY backend/ backend/

# Permisos de los wrappers de cada módulo
RUN chmod +x backend/contracts/mvnw \
             backend/services/mvnw \
             backend/cli/mvnw

# 1. Instala el parent POM raíz usando el wrapper raíz
RUN ./mvnw -f pom.xml install -N -DskipTests -q

# 2. Instala el módulo de contratos usando su propio wrapper
RUN backend/contracts/mvnw -f backend/contracts/pom.xml clean install -DskipTests -q

# 3. Empaqueta todos los servicios usando su propio wrapper
RUN backend/services/mvnw -f backend/services/pom.xml clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime: imagen mínima JRE para un servicio específico
#
# Uso:
#   docker build --build-arg SERVICE=insightbloom-users \
#                --build-arg SERVICE_PORT=8081 \
#                -f container/backend/java/Dockerfile .
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

ARG SERVICE=insightbloom-users
ARG SERVICE_PORT=8081
ARG VERSION=0.1.0-SNAPSHOT

WORKDIR /app

# Usuario no-root para seguridad
RUN addgroup -S insightbloom && \
    adduser  -S insightbloom -G insightbloom && \
    mkdir -p /data && \
    chown -R insightbloom:insightbloom /app /data

COPY --from=builder \
     /workspace/backend/services/${SERVICE}/target/${SERVICE}-${VERSION}.jar \
     app.jar

RUN chown insightbloom:insightbloom app.jar

USER insightbloom

# Variables de entorno con defaults del servicio
ENV PORT=${SERVICE_PORT}
ENV DB_PATH=/data/service.db

# Volumen para la base de datos SQLite
VOLUME ["/data"]

EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=4 \
  CMD wget -qO- http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
