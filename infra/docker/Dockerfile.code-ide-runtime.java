# Fase 4 del IDE: contenedor "runtime" — toolchain Java, sin code-server.
# Corre en el mismo Pod que Dockerfile.code-ide-server; expone un shell vía socat en
# 127.0.0.1:7681 (loopback intra-Pod) al que la terminal integrada de code-server se conecta.
FROM alpine:3.21

RUN apk add --no-cache \
    bash \
    socat \
    dumb-init \
    git \
    sqlite \
    curl \
    ca-certificates \
    openjdk21 \
    maven

# Mismo uid/gid 1000 que el contenedor 'ide' (Dockerfile.code-ide-server): el volumen
# 'workspace' se comparte entre ambos contenedores del Pod, necesitan el mismo dueño.
RUN addgroup -g 1000 coder && \
    adduser -D -u 1000 -G coder -h /home/coder coder && \
    chown -R coder:coder /home/coder

USER coder
WORKDIR /home/coder

RUN mkdir -p /home/coder/workspace /home/coder/db /home/coder/.m2

EXPOSE 7681

ENTRYPOINT ["dumb-init", "--"]
CMD ["socat", "TCP-LISTEN:7681,reuseaddr,fork,bind=127.0.0.1", "EXEC:/bin/bash -l,pty,stderr,setsid,sigint,sane"]
