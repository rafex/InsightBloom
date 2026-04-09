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
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime: nginx sirviendo el SPA + proxy a backends
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Elimina la configuración default de nginx
RUN rm /etc/nginx/conf.d/default.conf

# Copia configuración personalizada
COPY container/frontend/proxy_params.conf /etc/nginx/conf.d/proxy_params.conf
COPY container/frontend/nginx.conf        /etc/nginx/conf.d/default.conf

# Copia el build de Vite
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1
