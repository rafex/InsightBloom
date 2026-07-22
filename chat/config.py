import os

# URL interna del microservicio insightbloom-ingest dentro del cluster K3s.
INGEST_URL = os.getenv("INGEST_URL", "http://localhost:8085")
USERS_URL = os.getenv("USERS_URL", "http://localhost:8081")
PRESENTATIONS_URL = os.getenv("PRESENTATIONS_URL", "http://localhost:8091")
DB_PATH = os.getenv("DB_PATH", "chat.db")
CHAT_SECRET_KEY = os.getenv("CHAT_SECRET_KEY", "")
# El webhook se firma con HMAC. Se permite reutilizar la clave Fernet existente
# durante la migración para no exigir una rotación coordinada inmediata.
INSIGHTBLOOM_WEBHOOK_SECRET = os.getenv("INSIGHTBLOOM_WEBHOOK_SECRET", "") or CHAT_SECRET_KEY
NATS_URL = os.getenv("NATS_URL", "")
NATS_AUTH_TOKEN = os.getenv("NATS_AUTH_TOKEN", "")
CORS_ORIGINS = [origin.strip() for origin in os.getenv(
    "CORS_ORIGINS", "https://insightbloom.v1.rafex.cloud"
).split(",") if origin.strip()]
