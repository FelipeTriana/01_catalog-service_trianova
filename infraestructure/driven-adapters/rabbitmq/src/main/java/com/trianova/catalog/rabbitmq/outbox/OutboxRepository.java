package com.trianova.catalog.rabbitmq.outbox;

import com.trianova.catalog.domain.common.event.DomainEvent;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Acceso a la tabla outbox con SQL explícito. Se usa DatabaseClient (y no un
 * repositorio de Spring Data) porque la columna payload es JSONB y hay que
 * convertirla con CAST al escribir y a texto al leer.
 */
@Repository
public class OutboxRepository {

    private final DatabaseClient db;

    public OutboxRepository(R2dbcEntityTemplate template) {
        this.db = template.getDatabaseClient();
    }

    public Mono<Void> insert(DomainEvent event, String payload) {
        return db.sql("""
                        INSERT INTO outbox (id, aggregate_type, aggregate_id, event_type, payload, created_at)
                        VALUES (:id, :aggregateType, :aggregateId, :eventType, CAST(:payload AS JSONB), :createdAt)
                        """)
                .bind("id", event.eventId())
                .bind("aggregateType", event.aggregateType())
                .bind("aggregateId", event.aggregateId())
                .bind("eventType", event.type())
                .bind("payload", payload)
                .bind("createdAt", event.occurredAt())
                .then();
    }

    /** Las más antiguas primero: el orden de envío respeta el orden en que ocurrieron. */
    public Flux<OutboxMessage> findPending(int limit) {
        return db.sql("""
                        SELECT id, event_type, payload::text AS payload, created_at
                        FROM outbox
                        WHERE published_at IS NULL
                        ORDER BY created_at
                        LIMIT :limit
                        """)
                .bind("limit", limit)
                .map((row, meta) -> new OutboxMessage(
                        row.get("id", String.class),
                        row.get("event_type", String.class),
                        row.get("payload", String.class),
                        row.get("created_at", Instant.class)))
                .all();
    }

    public Mono<Void> markPublished(String id) {
        return db.sql("UPDATE outbox SET published_at = :now WHERE id = :id")
                .bind("now", Instant.now())
                .bind("id", id)
                .then();
    }
}
