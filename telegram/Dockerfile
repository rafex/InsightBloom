FROM python:3.12-slim

WORKDIR /app

# Python deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Código
COPY *.py ./
COPY models/ ./models/
COPY services/ ./services/
COPY routers/ ./routers/

# Usuario no-root
RUN useradd -m -u 1000 telegram && chown -R telegram:telegram /app
USER telegram

ENV DB_PATH=/data/telegram.db
VOLUME ["/data"]

EXPOSE 8095

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8095", "--proxy-headers"]
