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
  usecase/    # Casos de uso (próximo paso).
infraestructure/
  entry-points/reactive-web/   # Controllers REST (próximo paso).
  driven-adapters/
    r2dbc-repository/          # Persistencia reactiva (próximo paso).
    rabbitmq/                  # Publicación de eventos vía Outbox (próximo paso).
    security/                  # Resource server que valida el JWT de Keycloak (próximo paso).
  helpers/                     # AdapterOperations reactivo (próximo paso).
applications/app-service/      # Arranque + wiring (próximo paso).
```

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
- [ ] Casos de uso (`AgentUseCase`, `PlanUseCase`).
- [ ] Adaptadores: REST, R2DBC, RabbitMQ, seguridad.
