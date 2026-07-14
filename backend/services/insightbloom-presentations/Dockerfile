FROM node:20-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    fonts-liberation \
  && rm -rf /var/lib/apt/lists/*

ENV CHROME_PATH=/usr/bin/chromium
ENV CHROME_NO_SANDBOX=true
ENV PORT=8091
ENV DATA_DIR=/data

ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

COPY server.js live.js ./

RUN mkdir -p /data && chown -R node:node /app /data
USER node

VOLUME ["/data"]
EXPOSE 8091

CMD ["node", "server.js"]
