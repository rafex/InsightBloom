FROM python:3.12-slim

WORKDIR /app

# Dependencias del sistema (solo las necesarias para cryptography)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libssl-dev \
  && rm -rf /var/lib/apt/lists/*

# Python deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Código
COPY *.py ./
COPY static/ ./static/

# Usuario no-root
RUN useradd -m -u 1000 chat && chown -R chat:chat /app
USER chat

ENV DB_PATH=/data/chat.db
VOLUME ["/data"]

EXPOSE 8090

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090", "--proxy-headers"]
