# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Builder: compila la app Vue con Vite
# ─────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

# Instala dependencias (capa cacheada separada del código fuente)
COPY frontend/web/package*.json ./
RUN npm ci --prefer-offline

# Copia fuentes y construye
COPY frontend/web/ ./
ARG VITE_APP_VERSION=dev
ARG VITE_GIT_SHA=unknown
ARG VITE_OFFLINE_MANIFEST_PUBLIC_KEY=
ENV VITE_APP_VERSION=${VITE_APP_VERSION}
ENV VITE_GIT_SHA=${VITE_GIT_SHA}
ENV VITE_OFFLINE_MANIFEST_PUBLIC_KEY=${VITE_OFFLINE_MANIFEST_PUBLIC_KEY}
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime: nginx sirviendo el SPA + proxy a backends
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Elimina la configuración default de nginx
RUN rm /etc/nginx/conf.d/default.conf

# Copia configuración personalizada
COPY container/frontend/nginx.conf /etc/nginx/conf.d/default.conf

# Copia el build de Vite
COPY --from=builder /app/dist /usr/share/nginx/html

# Cambia permisos para usuario no-root nginx (UID 101)
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx /var/run

USER 101

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1
