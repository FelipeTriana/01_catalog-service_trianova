package com.trianova.catalog.domain.common.event;

import java.time.Instant;

/**
 * Marcador de evento de dominio. Los eventos concretos (p. ej. PriceUpdated)
 * lo implementan. Vive en el dominio porque el contrato de lo que ocurre es
 * parte del negocio, no de la infraestructura.
 */
public interface DomainEvent {

    /** Routing key / tipo del evento, p. ej. "catalog.price.updated". */
    String type();

    Instant occurredAt();
}
