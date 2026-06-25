FROM node:20-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    fonts-liberation \
  && rm -rf /var/lib/apt/lists/*

ENV CHROME_PATH=/usr/bin/chromium
ENV PORT=8091
ENV DATA_DIR=/data

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

COPY server.js ./

RUN useradd -m -u 1000 presentations && mkdir -p /data && chown -R presentations:presentations /app /data
USER presentations

VOLUME ["/data"]
EXPOSE 8091

CMD ["node", "server.js"]
