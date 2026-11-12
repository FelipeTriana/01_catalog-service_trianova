package com.trianova.catalog.domain.common.event;

import reactor.core.publisher.Mono;

/**
 * Puerto de SALIDA para publicar eventos de dominio. El dominio solo conoce
 * esta interfaz; el adaptador de RabbitMQ (vía Transactional Outbox) la
 * implementará más adelante.
 */
public interface EventPublisher {

    Mono<Void> publish(DomainEvent event);
}
