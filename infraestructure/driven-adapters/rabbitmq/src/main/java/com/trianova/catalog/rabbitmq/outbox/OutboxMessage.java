package com.trianova.catalog.rabbitmq.outbox;

import java.time.Instant;

/**
 * Fila pendiente del outbox, lista para enviarse.
 *
 * @param id        id del evento (eventId); viaja como messageId para que el consumidor deduplique
 * @param eventType routing key, p. ej. "catalog.price.updated"
 * @param payload   JSON del contrato, tal como se guardó
 */
public record OutboxMessage(String id, String eventType, String payload, Instant createdAt) {
}
