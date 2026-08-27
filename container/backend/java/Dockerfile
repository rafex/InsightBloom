# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Builder: compila todos los servicios con Maven
#
# IBM Semeru (OpenJ9) + Maven ya vienen instalados en esta imagen (ver
# infra/docker/Dockerfile.java-builder / .github/workflows/publish-java-base-images.yml) --
# reemplaza la instalación manual que antes vivía acá sobre eclipse-temurin:25-jdk-alpine.
# ─────────────────────────────────────────────────────────────────────────────
FROM ghcr.io/rafex/insightbloom-java-builder:25.0.4.0 AS builder

WORKDIR /workspace

# POMs de raíz y módulos (capa de caché de dependencias)
COPY pom.xml ./
COPY backend/ backend/

# 1. Instala el parent POM raíz
RUN mvn -f pom.xml install -N -DskipTests -q

# 2. Instala el módulo de contratos
RUN mvn -f backend/contracts/pom.xml clean install -DskipTests -q

# 3. Instala el módulo común (depende de contracts)
RUN mvn -f backend/common/pom.xml clean install -DskipTests -q

# 4. Empaqueta todos los servicios
RUN mvn -f backend/services/pom.xml clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime: imagen mínima JRE para un servicio específico
#
# El usuario no-root insightbloom (uid 1000) ya viene creado en esta imagen (ver
# infra/docker/Dockerfile.java-runtime) -- reemplaza la creación manual que antes vivía acá
# sobre eclipse-temurin:25-jre-alpine.
#
# Uso:
#   docker build --build-arg SERVICE=insightbloom-users \
#                --build-arg SERVICE_PORT=8081 \
#                -f container/backend/java/Dockerfile .
# ─────────────────────────────────────────────────────────────────────────────
FROM ghcr.io/rafex/insightbloom-java-runtime:25.0.4.0 AS runtime

ARG SERVICE=insightbloom-users
ARG SERVICE_PORT=8081
ARG VERSION=0.1.0-SNAPSHOT
ARG APP_VERSION=dev
ARG GIT_SHA=unknown

WORKDIR /app
RUN mkdir -p /data && chown -R insightbloom:insightbloom /app /data

COPY --from=builder \
     /workspace/backend/services/${SERVICE}/target/${SERVICE}-${VERSION}.jar \
     app.jar

RUN chown insightbloom:insightbloom app.jar

USER 1000

# Variables de entorno con defaults del servicio
ENV PORT=${SERVICE_PORT}
ENV DB_PATH=/data/service.db
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}

# Volumen para la base de datos SQLite
VOLUME ["/data"]

EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=4 \
  CMD curl -fsS http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", \
            "-Djdk.httpclient.keepalive.timeout=5", \
            "--enable-native-access=ALL-UNNAMED", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
