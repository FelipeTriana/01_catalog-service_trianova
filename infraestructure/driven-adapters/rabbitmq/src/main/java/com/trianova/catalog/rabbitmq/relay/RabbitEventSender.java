package com.trianova.catalog.rabbitmq.relay;

import com.trianova.catalog.rabbitmq.outbox.OutboxMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Envía un mensaje del outbox al exchange y espera la CONFIRMACIÓN del broker
 * (publisher confirm). Sin esa confirmación no sabríamos si RabbitMQ recibió el
 * mensaje, y marcarlo como publicado podría perderlo.
 *
 * RabbitTemplate es bloqueante (Reactor RabbitMQ dejó de mantenerse en 2023), así
 * que el envío corre en boundedElastic. Es aceptable: lo usa el relay en segundo
 * plano, nunca una petición HTTP.
 */
@Component
public class RabbitEventSender {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public RabbitEventSender(RabbitTemplate rabbitTemplate,
                             @Value("${app.events.exchange:trianova.events}") String exchange) {
        // Falla al arrancar si faltan las confirmaciones: sin ellas cada envío esperaría hasta el timeout.
        if (!rabbitTemplate.getConnectionFactory().isPublisherConfirms()) {
            throw new IllegalStateException(
                    "Publisher confirms are disabled; set spring.rabbitmq.publisher-confirm-type=correlated");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    public Mono<Void> send(OutboxMessage message) {
        return Mono.fromCallable(() -> {
                    CorrelationData correlation = new CorrelationData(message.id());
                    rabbitTemplate.send(exchange, message.eventType(), toAmqp(message), correlation);
                    return correlation;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(correlation -> Mono.fromFuture(correlation.getFuture()))
                .timeout(CONFIRM_TIMEOUT)
                .flatMap(confirm -> confirm.isAck()
                        ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException(
                                "Broker rejected event " + message.id() + ": " + confirm.getReason())));
    }

    /** messageId = eventId para que el consumidor descarte duplicados; persistente para sobrevivir reinicios. */
    static Message toAmqp(OutboxMessage message) {
        return MessageBuilder.withBody(message.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(message.id())
                .setType(message.eventType())
                .setTimestamp(Date.from(message.createdAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
