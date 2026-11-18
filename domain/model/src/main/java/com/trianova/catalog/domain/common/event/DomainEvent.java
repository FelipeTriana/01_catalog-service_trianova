package com.trianova.catalog.domain.common.event;

import java.time.Instant;

/**
 * Evento de dominio: algo que ya ocurrió en el negocio. Vive en el dominio porque
 * el hecho es parte del negocio; el formato JSON con que viaja es asunto del
 * adaptador de mensajería.
 */
public interface DomainEvent {

    /** Identificador único del evento. Los consumidores lo usan para descartar duplicados. */
    String eventId();

    /** Routing key / tipo del evento, p. ej. "catalog.price.updated". */
    String type();

    Instant occurredAt();

    /** Tipo de agregado que originó el evento, p. ej. "PLAN". */
    String aggregateType();

    /** Id del agregado que originó el evento. */
    String aggregateId();
}
