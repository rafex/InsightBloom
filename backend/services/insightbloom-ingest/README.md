# insightbloom-ingest
Puerto: 8082
Recibe mensajes de /duda y /tema, normaliza, clasifica, evalúa censura y notifica stats+query.
## Endpoints
- POST /api/v1/messages
- POST /api/v1/webhooks/messages
- GET  /api/v1/messages/{id}
## Variables de entorno
- PORT (default 8082)
- DB_PATH (default ingest.db)
- USERS_URL (default http://localhost:8081)
- MODERATION_URL (default http://localhost:8084)
- STATS_URL (default http://localhost:8085)
- QUERY_URL (default http://localhost:8083)
