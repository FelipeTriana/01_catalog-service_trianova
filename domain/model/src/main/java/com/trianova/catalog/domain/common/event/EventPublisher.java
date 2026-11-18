package com.trianova.catalog.domain.common.event;

import reactor.core.publisher.Mono;

/**
 * Puerto de SALIDA para publicar eventos de dominio. El dominio solo conoce esta
 * interfaz. La implementación (Transactional Outbox) guarda el evento en la base;
 * un proceso aparte lo entrega a RabbitMQ.
 */
public interface EventPublisher {

    Mono<Void> publish(DomainEvent event);
}
