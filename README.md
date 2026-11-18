# Trianova — Catalog Service

Primer microservicio del backend SaaS de Trianova AI. Responde a una sola pregunta:
**¿qué vende Trianova y a qué precio?** (agentes, planes, precios y features).

Construido con **arquitectura hexagonal** (estructura del scaffold de SURA), **Spring WebFlux**
y **Java 21**. La especificación técnica completa está en
`../Catalog-Service-Especificacion-Tecnica.pdf`.

## Requisitos

- Un JDK para correr Gradle (vale JDK 17+).
- **No** necesitás instalar Java 21: Gradle lo descarga solo vía *toolchain* (foojay).

## Estructura (hexagonal, multi-módulo Gradle)

```
domain/
  model/      # Entidades, objetos de valor, enums y puertos (gateways). Java puro, sin Spring.
  usecase/    # Casos de uso: AgentUseCase y PlanUseCase.
infraestructure/
  entry-points/reactive-web/   # Controllers REST, DTOs y errores RFC 7807.
  driven-adapters/
    r2dbc-repository/          # Persistencia reactiva en PostgreSQL (migraciones V1 y V2).
    rabbitmq/                  # Eventos vía Transactional Outbox (migración V3) + relay a RabbitMQ.
    security/                  # Resource server que valida el JWT de Keycloak (próximo paso).
  helpers/r2dbc-commons/       # AdapterOperations reactivo.
applications/app-service/      # Arranque + wiring (próximo paso).
```

## Eventos publicados

Exchange topic `trianova.events`. Contrato JSON en `EventContract` (versión 1).

| Routing key | Cuándo |
|---|---|
| `catalog.plan.changed` | Plan creado, editado, publicado, archivado, restaurado o borrado (`changeType`). |
| `catalog.price.updated` | Cambio de precio de un ciclo; lleva `oldAmount` (null si el ciclo es nuevo) y `newAmount`. |

Entrega **al menos una vez**: los consumidores deben descartar duplicados por `eventId`
(también viaja como `messageId` del mensaje AMQP).

Configuración:

| Propiedad | Default | Uso |
|---|---|---|
| `spring.rabbitmq.publisher-confirm-type` | — | **Obligatoria:** `correlated`. Sin confirmaciones el servicio no arranca. |
| `app.events.exchange` | `trianova.events` | Exchange de destino. |
| `app.events.relay.interval` | `2s` | Espera entre lotes del relay. |
| `app.events.relay.batch-size` | `100` | Eventos por lote. |
| `app.events.relay.enabled` | `true` | Apaga el relay (p. ej. en una instancia solo HTTP). |

**Regla de oro:** las dependencias apuntan siempre hacia adentro (hacia el dominio).

## Comandos

```bash
# Compilar y correr las pruebas (la primera vez descarga Gradle 8.10 y el JDK 21)
./gradlew build

# Solo pruebas
./gradlew test

# Reporte de cobertura (JaCoCo)
./gradlew jacocoTestReport
```

## Estado actual

- [x] Esqueleto multi-módulo en Java 21 (Gradle 8.10).
- [x] Capa de dominio: `Agent`, `Plan`, `Price`, `Feature`, `Money` + puertos, con pruebas unitarias.
- [x] Casos de uso (`AgentUseCase`, `PlanUseCase`).
- [x] Adaptadores: REST, R2DBC, RabbitMQ (Outbox).
- [ ] Seguridad (JWT de Keycloak).
- [ ] app-service: arranque, wiring y transacciones (el Outbox necesita la transacción para ser atómico).
