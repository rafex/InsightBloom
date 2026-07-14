# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Builder: compila todos los servicios con Maven
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

# Instalar Maven directamente (evita que mvnw descargue distribuciones externas)
ENV MAVEN_VERSION=3.9.9
RUN apk add --no-cache unzip wget && \
    wget -q "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -O /tmp/maven.tar.gz && \
    tar xzf /tmp/maven.tar.gz -C /opt && \
    ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn && \
    rm -f /tmp/maven.tar.gz

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
    adduser  -S insightbloom -G insightbloom -u 1000 && \
    mkdir -p /data && \
    chown -R insightbloom:insightbloom /app /data

COPY --from=builder \
     /workspace/backend/services/${SERVICE}/target/${SERVICE}-${VERSION}.jar \
     app.jar

RUN chown insightbloom:insightbloom app.jar

USER 1000

# Variables de entorno con defaults del servicio
ENV PORT=${SERVICE_PORT}
ENV DB_PATH=/data/service.db

# Volumen para la base de datos SQLite
VOLUME ["/data"]

EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=4 \
  CMD wget -qO- http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", \
            "-Djdk.httpclient.keepalive.timeout=5", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
