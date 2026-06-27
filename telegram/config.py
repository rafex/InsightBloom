import os

# URLs internas de los microservicios dentro del cluster K3s.
INGEST_URL = os.getenv("INGEST_URL", "http://localhost:8082")
USERS_URL = os.getenv("USERS_URL", "http://localhost:8081")
DB_PATH = os.getenv("DB_PATH", "telegram.db")

# Credenciales del bot de Telegram.
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_WEBHOOK_SECRET = os.getenv("TELEGRAM_WEBHOOK_SECRET", "")

# Clave compartida para llamadas service-to-service entrantes (/internal/*).
# Mismo comportamiento fail-open que BaseResourceHandler.validInternalAuth en Java:
# si está vacía, el endpoint no exige el header (modo desarrollo).
INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY", "")

TELEGRAM_API_BASE = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}"
