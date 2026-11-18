package com.trianova.catalog.rabbitmq.relay;

import com.trianova.catalog.rabbitmq.outbox.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Vacía el outbox hacia RabbitMQ: toma un lote de eventos pendientes y, uno por
 * uno y en orden, los envía y los marca como publicados.
 *
 * Garantía: AL MENOS UNA VEZ. Si el proceso cae después de que el broker confirmó
 * pero antes de marcar la fila, el evento se reenvía en la siguiente vuelta. Por
 * eso cada mensaje lleva su eventId: el consumidor descarta el que ya procesó.
 *
 * Con varias instancias del servicio, dos relays podrían enviar la misma fila.
 * Sigue siendo correcto (al menos una vez), pero duplica envíos; se resolverá con
 * SELECT ... FOR UPDATE SKIP LOCKED cuando el app-service aporte las transacciones.
 */
@Component
public class OutboxRelay {

    private final OutboxRepository outbox;
    private final RabbitEventSender sender;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox,
                       RabbitEventSender sender,
                       @Value("${app.events.relay.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.sender = sender;
        this.batchSize = batchSize;
    }

    /**
     * Publica un lote y devuelve cuántos eventos envió. concatMap (no flatMap)
     * mantiene el orden y se detiene en el primer fallo: así un evento posterior
     * nunca llega antes que uno anterior que todavía no salió.
     */
    public Mono<Long> publishPending() {
        return outbox.findPending(batchSize)
                .concatMap(message -> sender.send(message)
                        .then(Mono.defer(() -> outbox.markPublished(message.id())))
                        .thenReturn(message))
                .count();
    }
}
