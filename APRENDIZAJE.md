# Bitácora de Aprendizaje — Catalog Service

> Registro técnico de los conceptos que vamos trabajando mientras construimos el
> microservicio. Cada entrada nace de una pregunta real y queda anclada al código
> del proyecto. Es material de estudio, no documentación de usuario.

Índice:

1. [Arquitectura hexagonal (puertos y adaptadores)](#1-arquitectura-hexagonal-puertos-y-adaptadores)
2. [Agregado (DDD)](#2-agregado-ddd)
3. [Inmutabilidad del dominio](#3-inmutabilidad-del-dominio)
4. [`@Singular` (Lombok)](#4-singular-lombok)
5. [Eventos de dominio: `PriceUpdated`](#5-eventos-de-dominio-priceupdated)
6. [Referencias a métodos (`planGateway::save`)](#6-referencias-a-métodos-plangatewaysave)
7. [Pruebas: dónde viven y cómo se ejecutan](#7-pruebas-dónde-viven-y-cómo-se-ejecutan)
8. [Testcontainers: ciclo de vida del contenedor](#8-testcontainers-ciclo-de-vida-del-contenedor)
9. [Flyway: migraciones de base de datos](#9-flyway-migraciones-de-base-de-datos)
10. [Persistir un agregado en varias tablas (desarmar/re-ensamblar)](#10-persistir-un-agregado-en-varias-tablas-desarmarre-ensamblar)
11. [Sin FK entre agregados (frontera DDD)](#11-sin-fk-entre-agregados-frontera-ddd)
12. [`Mono.zip`: combinar flujos reactivos](#12-monozip-combinar-flujos-reactivos)
13. [Encoding UTF-8 en el build (trampa de Windows)](#13-encoding-utf-8-en-el-build-trampa-de-windows)
14. [`@SpringBootConfiguration` y la búsqueda por paquetes](#14-springbootconfiguration-y-la-búsqueda-por-paquetes)

---

## 1. Arquitectura hexagonal (puertos y adaptadores)

**Idea central:** las dependencias apuntan hacia adentro. El dominio no conoce la
infraestructura; la infraestructura conoce al dominio.

- **Dominio** (`domain/model`, `domain/usecase`): reglas de negocio puras. Java sin
  Spring, sin base de datos, sin frameworks.
- **Puertos**: interfaces que el dominio define (ej. `AgentGateway`, `PlanGateway`,
  `EventPublisher`). El dominio dice *qué* necesita, no *cómo*.
- **Adaptadores**: implementaciones concretas de esos puertos en la capa de
  infraestructura (ej. `AgentRepositoryAdapter` sobre R2DBC).

**Consecuencia práctica:** cada capa se prueba con lo mínimo que necesita. El
dominio se testea sin Docker; solo el adaptador de persistencia necesita una base
real. Por eso la mayoría de los tests son rapidísimos.

---

## 2. Agregado (DDD)

**Pregunta que lo originó:** *"¿A qué te referís con agregado?"*

Un **agregado** es un grupo de objetos del dominio que se tratan como **una sola
unidad** para los cambios. Tiene una **raíz** (*aggregate root*) que es la única
puerta de entrada: nadie toca las piezas internas sin pasar por la raíz.

En el proyecto:

- `Plan` es la **raíz del agregado**.
- `Price` y `Feature` son piezas **internas**. No tienen vida propia: un precio
  existe *porque pertenece a un plan*.

**Regla de oro:** la raíz protege las invariantes del grupo. No se hace
`price.setAmount(...)` por fuera; se le pide al `Plan` con `plan.changePrice(...)`,
y el `Plan` valida (no dos precios para el mismo ciclo, estado válido, etc.).

> Analogía: un `Pedido` (raíz) y sus `ItemsDelPedido` (piezas internas). No agregás
> un ítem por abajo; se lo pedís al pedido, que valida. El pedido entra y sale completo.

**Impacto en persistencia:** al guardar un agregado, se guarda el todo. `Agent` fue
el caso fácil (una sola tabla). `Plan` es el difícil: raíz + precios + features en
tres tablas (`plans`, `prices`, `plan_features`) que hay que desarmar al guardar y
re-ensamblar al leer.

---

## 3. Inmutabilidad del dominio

**Pregunta que lo originó:** *"¿Qué sentido tiene retornar un objeto nuevo si igual
se puede crear otro con el método de actualizar?"*

El `Plan` es inmutable: todos sus campos son `final` y las transiciones
(`publish`, `changePrice`, `archive`) **no modifican** el objeto, devuelven **uno
nuevo** vía `toBuilder()`.

**El punto NO es impedir el cambio** — el plan sí cambia de precio. El punto es
**prohibir la puerta de atrás** y obligar a que todo cambio pase por la raíz
(el guardián de las reglas).

```java
// MUTABLE (puerta de atrás): cualquiera deja el objeto en estado inválido
plan.setPrecio(-5000);        // nadie lo frena
plan.getPrices().clear();     // borró precios de un plan publicado

// INMUTABLE (puerta de adelante): el cambio pasa SÍ o SÍ por la validación
Plan nuevo = plan.changePrice(nuevoPrecio);
Plan pub   = plan.publish();  // falla si no hay precio o no está en DRAFT
```

**Beneficios concretos:**

- **Thread-safety gratis.** En un entorno reactivo/concurrente (WebFlux), un objeto
  inmutable no necesita locks: nadie lo cambia por debajo.
- **Sin efectos colaterales ocultos.** `changePrice` no ensucia el original; devuelve
  otro. Código predecible, fácil de testear y debuggear.
- **El historial no se pisa.** Tenés el objeto viejo y el nuevo para comparar.

> Conclusión: la inmutabilidad no impide crear una versión nueva (eso es lo deseado);
> impide modificar la existente salteándose las reglas.

---

## 4. `@Singular` (Lombok)

**Pregunta que lo originó:** *"¿Qué significa `@Singular` en `Plan`?"*

Anotación de Lombok que trabaja sobre el `@Builder`. Hace dos cosas:

**(a) Permite agregar elementos de a uno** con un método en singular:

```java
Plan p = Plan.builder()
        .name("Pro")
        .price(precioMensual)   // método singular generado por Lombok
        .price(precioAnual)
        .feature(featureSoporte)
        .build();
```

**(b) Hace la colección inmutable.** La lista guardada dentro del `Plan` es una copia
blindada: `plan.getPrices().add(...)` lanza excepción. Coherente con la inmutabilidad
del agregado.

También genera `clearPrices()`, usado en `changePrice` para reconstruir la lista:

```java
return toBuilder().clearPrices().prices(updated).build();
```

---

## 5. Eventos de dominio: `PriceUpdated`

**Pregunta que lo originó:** *"¿Por qué existe el evento `PriceUpdated`?"*

**Problema:** cuando cambia un precio, a varios servicios les puede importar
(Suscripciones, Analytics, Notificaciones... y otros que aún no existen). Si Catalog
tuviera que **llamar** a cada uno, quedaría acoplado a todos.

**Solución (arquitectura orientada a eventos):** Catalog no llama a nadie. **Anuncia
un hecho** (`PriceUpdated`) a RabbitMQ y se olvida. Quien le interese, que escuche.

Detalles importantes:

- El nombre está en **pasado** (`PriceUpdated`, no `UpdatePrice`): es un hecho
  consumado, no una orden.
- Lleva toda la info para actuar sin volver a preguntar: `planId`, `agentId`,
  `amount`, `currency`, `billingCycle`, `occurredAt`.
- Routing key: `catalog.price.updated`, exchange `trianova.events`.

**Orden en el caso de uso (`PlanUseCase.changePrice`):** primero persistir, después
anunciar. No se grita "el precio cambió" si todavía no se guardó.

```java
.map(plan -> plan.changePrice(newPrice))  // el dominio cambia el precio
.flatMap(planGateway::save)               // 1° PERSISTO
.flatMap(saved -> eventPublisher.publish( // 2° ANUNCIO
        new PriceUpdated(...)).thenReturn(saved));
```

**Premio:** mañana nace un servicio nuevo interesado en precios; se suscribe a
`catalog.price.updated` y listo. Líneas tocadas en Catalog: **cero**.

**Pendientes anotados:**

- Hoy `EventPublisher` es solo un puerto (interfaz). El adaptador real a RabbitMQ
  (`driven-adapters/rabbitmq`) todavía no existe; en los tests se mockea.
- Publicar justo después de guardar tiene una trampa (¿y si RabbitMQ se cae ahí?).
  Se resuelve con el patrón **Transactional Outbox** (pendiente, con el adaptador de
  RabbitMQ).

---

## 6. Referencias a métodos (`planGateway::save`)

**Pregunta que lo originó:** *"¿Ahí no deberías referirte al plan?"*

`planGateway::save` es una **referencia a método**: azúcar sintáctico para una lambda.
Son equivalentes:

```java
.flatMap(planGateway::save)               // referencia a método
.flatMap(plan -> planGateway.save(plan))  // lambda — exactamente lo mismo
```

El `plan` llega del operador anterior de la cadena reactiva (el `map` que emitió el
`Plan` modificado). `objeto::metodo` significa *"tomá el valor que llega y llamá a
`objeto.metodo(valor)`"*. El argumento es implícito pero está.

Se prefiere la referencia cuando solo se pasa el valor tal cual. Si hay que
transformarlo antes, se usa la lambda.

---

## 7. Pruebas: dónde viven y cómo se ejecutan

**Ubicación:** cada módulo lleva sus tests en `src/test` (al lado de `src/main`).

Convención de nombres:

- `*Test.java` → prueba unitaria (rápida, sin dependencias externas).
- `*IT.java` → prueba de **integración** (necesita infraestructura real, ej. Docker).

**Ejecución (desde `catalog-service`):**

```bash
./gradlew test                                              # todo el proyecto
./gradlew :driven-adapters-r2dbc-repository:test            # un módulo
./gradlew :driven-adapters-r2dbc-repository:test --tests "*AgentRepositoryAdapterIT"
```

- Gradle cachea: si no cambió nada, muestra `UP-TO-DATE`. Para forzar: `--rerun-tasks`.

**Ver resultados — reporte HTML** (siempre se genera):

```
<módulo>/build/reports/tests/test/index.html
```

Muestra % de éxito, duración, cada test y, si falla, el stack trace completo.

---

## 8. Testcontainers: ciclo de vida del contenedor

**Pregunta que lo originó:** *"¿Para qué prender Docker si no creaste un contenedor?"*

Docker tiene dos partes: el **daemon** (motor) y los **contenedores** (cajas
concretas). Los tests de integración necesitan el **motor** prendido, no un
contenedor tuyo.

La librería **Testcontainers** levanta su **propio** PostgreSQL efímero para el test
y lo destruye al terminar. No usa `postgres_db_container` ni ningún contenedor
preexistente. Por eso el test es reproducible en cualquier máquina (tu PC, otra, un
pipeline): siempre se trae una base limpia desde cero.

Ciclo de vida (declarativo, en `AgentRepositoryAdapterIT.java`):

```java
@Testcontainers(disabledWithoutDocker = true)   // activa la gestión; si no hay Docker, SALTA el test
@DataR2dbcTest
class AgentRepositoryAdapterIT {

    @Container                                   // marca el contenedor gestionado
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
```

- `static` → el contenedor se crea **una vez antes de toda la clase** y se destruye
  una vez al final. (No-`static` sería por cada `@Test`.)
- Nunca se llama a `.start()` ni `.stop()` a mano: las anotaciones lo gobiernan.

Conexión dinámica (el contenedor usa un puerto al azar):

```java
@DynamicPropertySource
static void r2dbcProps(DynamicPropertyRegistry registry) {
    registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
            + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort()
            + "/" + POSTGRES.getDatabaseName());
    // ...
}
```

**Orden de arranque del test:**

```
1. @Container (static)    → Docker levanta postgres:16-alpine
2. @DynamicPropertySource → Spring apunta a ese contenedor
3. @BeforeAll migrate()   → Flyway crea las tablas
4. @Test x4               → corren las pruebas
5. (fin de la clase)      → Testcontainers destruye el contenedor
```

Hoy, esto se usa **solo** en `AgentRepositoryAdapterIT.java`. Cada adaptador futuro
tendrá su propio `IT` con el contenedor que necesite.

---

## 9. Flyway: migraciones de base de datos

**Pregunta que lo originó:** *"¿Qué es Flyway?"*

Herramienta de **migraciones**: mantiene el esquema de la base versionado y
sincronizado con el código, como Git pero para la estructura de la base.

- Los cambios se escriben en archivos SQL numerados: `V1__create_agents.sql`,
  `V2__...`, etc.
- Flyway lleva una tabla de control (`flyway_schema_history`) donde anota qué
  versiones ya aplicó. Aplica solo las que faltan, en orden, una sola vez cada una.

Invocación en el test (`AgentRepositoryAdapterIT.migrate()`):

```java
@BeforeAll
static void migrate() {
    Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")   // carpeta de los V#__*.sql
            .load()
            .migrate();                             // ejecuta las migraciones pendientes
}
```

- `.locations("classpath:db/migration")` apunta a
  `src/main/resources/db/migration/`, donde vive `V1__create_agents.sql`.
- Corre **después** de levantar el contenedor y **antes** de los `@Test`, para que
  las tablas existan cuando se prueba el adaptador.

## 10. Persistir un agregado en varias tablas (desarmar/re-ensamblar)

**Contexto:** `Agent` era una sola tabla, así que su adaptador reutilizó
`AdapterOperations` (entidad ↔ tabla, 1 a 1). `Plan` **no**: es un agregado que
se reparte en TRES tablas (`plans` raíz, `prices`, `plan_features`). Por eso
`PlanRepositoryAdapter` **no extiende** `AdapterOperations` — el caso es
multi-tabla y necesita orquestación propia.

**Al guardar (`save`):** se DESARMA el agregado y se persiste pieza por pieza:

1. *Upsert* de la raíz (mismo truco que Agent: `existsById` → `insert`/`update`).
2. Los hijos (precios, features) se guardan con estrategia **"borrar e insertar"**:
   `deleteByPlanId(...)` y luego insertar el conjunto vigente.

```java
public Mono<Plan> save(Plan plan) {
    return upsertRoot(dataFrom(plan))   // 1. raíz
            .then(replacePrices(plan))  // 2. borrar e insertar precios
            .then(replaceFeatures(plan))// 3. borrar e insertar features
            .thenReturn(plan);
}
```

**¿Por qué "borrar e insertar" y no actualizar fila por fila?** Porque `Price` y
`Feature` son **value objects sin identidad propia** en el dominio. No nos importa
*qué* fila es *cuál*; nos importa que el conjunto guardado sea exactamente el del
agregado. Su `id` en la tabla es una **llave subrogada**, generada al persistir y
nunca expuesta al dominio.

**Al leer (`findById`):** se RE-ENSAMBLA el agregado trayendo las tres partes y
reconstruyendo el `Plan` con sus listas (vía `@Singular`):

```java
private Mono<Plan> reassemble(PlanData data) {
    Mono<List<Price>>   prices   = priceRepository.findByPlanId(data.getId())...collectList();
    Mono<List<Feature>> features = featureRepository.findByPlanId(data.getId())...collectList();
    return Mono.zip(prices, features)
            .map(parts -> domainFrom(data, parts.getT1(), parts.getT2()));
}
```

**Lección clave:** el adaptador **protege al dominio de la forma de la base**. El
`Plan` del dominio no sabe que son tres tablas; recibe y entrega un agregado
completo. Toda la fealdad del SQL multi-tabla queda encerrada acá.

**Pendiente anotado (consistencia):** el `save` toca varias tablas y hoy NO está
dentro de una transacción. Si falla a mitad de camino, puede quedar inconsistente.
Cuando el `app-service` aporte un `ReactiveTransactionManager`, `save` debe correr
en una transacción (`TransactionalOperator` / `@Transactional`) para que el
borrar-e-insertar sea atómico. (Verificado en `PlanRepositoryAdapterIT`.)

---

## 11. Sin FK entre agregados (frontera DDD)

**Decisión de diseño:** la tabla `plans` tiene `agent_id`, pero **no** hay una
llave foránea (`FOREIGN KEY`) de `plans.agent_id` hacia `agents.id`.

**¿Por qué?** `Agent` y `Plan` son **agregados distintos**. En DDD, un agregado se
referencia a otro **por identidad** (guardo su id), no por integridad referencial.

- Las FK **sí** se usan DENTRO de un agregado: `prices.plan_id` → `plans.id` con
  `ON DELETE CASCADE`, porque un precio no vive sin su plan.
- Las FK **no** se usan ENTRE agregados: acoplarían dos cosas que deben poder
  evolucionar (y hasta vivir en bases distintas) por separado.

> Regla práctica: FK hacia adentro del agregado, referencia por id hacia afuera.

---

## 12. `Mono.zip`: combinar flujos reactivos

`Mono.zip(a, b)` espera a que **ambos** `Mono` emitan y combina sus resultados en
una `Tuple`. Se usó en el re-ensamblado del `Plan` para traer precios y features
**en paralelo** y unirlos:

```java
return Mono.zip(prices, features)              // espera a los dos
        .map(parts -> domainFrom(data, parts.getT1(), parts.getT2()));
```

- `parts.getT1()` → resultado del primer `Mono` (lista de precios).
- `parts.getT2()` → resultado del segundo (lista de features).

Si cualquiera falla, el `zip` falla. Si uno estuviera vacío (no es el caso acá,
`collectList` siempre emite una lista aunque sea vacía), el `zip` no emitiría.

---

## 13. Encoding UTF-8 en el build (trampa de Windows)

**Síntoma:** al compilar aparecieron warnings `unmappable character (0x8D) for
encoding windows-1252` y los acentos se veían rotos (`RA�?Z`).

**Causa:** los archivos `.java` están en UTF-8, pero `javac`, si no se le dice nada,
usa el encoding **por defecto del sistema operativo**. En Windows es `windows-1252`,
que no entiende caracteres no-ASCII (acentos, ñ).

**Fix (en `main.gradle`, para todos los módulos):**

```groovy
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

test {
    systemProperty 'file.encoding', 'UTF-8'
}
```

**Lección:** nunca dependas del encoding por defecto del SO. Fijá UTF-8 explícito en
el build; así compila igual en tu Windows, en el Linux de un compañero y en el
pipeline.

---

## 14. `@SpringBootConfiguration` y la búsqueda por paquetes

**Contexto:** `@DataR2dbcTest` es un *slice test* — no levanta toda la app, solo la
capa de datos. Para armar ese contexto necesita una `@SpringBootConfiguration` en el
classpath de test. En este módulo esa clase es `TestR2dbcApplication` (no hay `main`
propio; el `main` real es del `app-service`).

**La trampa:** Spring busca esa `@SpringBootConfiguration` **subiendo** por el árbol
de paquetes desde el test, nunca de costado. La clase estaba en
`com.trianova.catalog.r2dbc.agent`; el nuevo test de `Plan` vive en
`com.trianova.catalog.r2dbc.plan` — un paquete **hermano**, no ancestro. No la
habría encontrado.

**Fix:** mover `TestR2dbcApplication` al paquete padre común
`com.trianova.catalog.r2dbc`. Desde ahí es ancestro de `.agent` y de `.plan`, así
que todos los slice tests de los sub-paquetes la encuentran.

**Lección:** la clase ancla de los slice tests va en el **paquete raíz** del módulo
de test, para que la vean todos los sub-paquetes.
