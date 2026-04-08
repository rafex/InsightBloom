# Services

Microservicios backend de InsightBloom.

Cada servicio sigue arquitectura hexagonal con dominio, puertos y adaptadores.

## Servicios

- `insightbloom-users`: autenticacion, usuarios, conferencias.
- `insightbloom-ingest`: recepcion y normalizacion de mensajes.
- `insightbloom-query`: nubes de palabras y timelines.
- `insightbloom-moderation`: censura manual y revision.
- `insightbloom-stats`: agregados y relevancia.

## Puertos locales

| Servicio | Puerto |
|----------|--------|
| users | 8081 |
| ingest | 8082 |
| query | 8083 |
| moderation | 8084 |
| stats | 8085 |

## Comandos

```bash
# Build todos
./helpers-build/build-services.sh

# Correr uno
./helpers-run/run-service.sh insightbloom-users

# Test todos
./mvnw -f services/pom.xml test
```
