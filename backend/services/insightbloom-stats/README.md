# insightbloom-stats
Puerto: 8085
Calcula agregados y relevanceScore para la nube de palabras.
## Formula
relevanceScore = visibleCount * typeWeight * averageIntentWeight
## Endpoint interno
- POST /internal/recalc
## Endpoints públicos
- GET /api/v1/conferences/{id}/stats/overview
- GET /api/v1/conferences/{id}/stats/relevance
