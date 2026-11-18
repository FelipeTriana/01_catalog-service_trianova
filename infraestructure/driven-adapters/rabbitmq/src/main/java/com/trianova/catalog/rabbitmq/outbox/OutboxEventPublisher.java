package com.trianova.catalog.rabbitmq.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trianova.catalog.domain.common.event.DomainEvent;
import com.trianova.catalog.domain.common.event.EventPublisher;
import com.trianova.catalog.rabbitmq.contract.EventContract;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Implementación del puerto EventPublisher con Transactional Outbox.
 *
 * "Publicar" aquí NO habla con RabbitMQ: solo guarda el evento en la tabla outbox,
 * en la misma base que el cambio de negocio. Así, cuando el caso de uso corra en
 * una transacción, el cambio y su evento se confirman o se descartan juntos.
 * El envío real al broker lo hace OutboxRelay, en segundo plano.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public OutboxEventPublisher(OutboxRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> publish(DomainEvent event) {
        return Mono.fromCallable(() -> EventContract.toJson(mapper, event))
                .flatMap(json -> outbox.insert(event, json));
    }
}
