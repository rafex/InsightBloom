# insightbloom-moderation
Puerto: 8084
Gestiona censura manual, restauracion y evaluacion automatica de terminos bloqueados.
## Endpoints publicos
- GET /api/v1/conferences/{id}/moderation/words?page=1&pageSize=50
- GET /api/v1/conferences/{id}/moderation/messages?page=1&pageSize=50
- POST /api/v1/moderation/words/{wordId}/censor
- POST /api/v1/moderation/words/{wordId}/restore
- POST /api/v1/moderation/messages/{messageId}/censor
- POST /api/v1/moderation/messages/{messageId}/restore
## Endpoint interno
- POST /internal/evaluate
