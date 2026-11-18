package com.trianova.catalog.rabbitmq.relay;

import com.trianova.catalog.rabbitmq.outbox.OutboxMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Sin broker: CachingConnectionFactory no se conecta hasta el primer envío. */
class RabbitEventSenderTest {

    @Test
    void refusesToStartWithoutPublisherConfirms() {
        RabbitTemplate template = new RabbitTemplate(new CachingConnectionFactory("localhost"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new RabbitEventSender(template, "trianova.events"));
        assertEquals("Publisher confirms are disabled; set spring.rabbitmq.publisher-confirm-type=correlated",
                error.getMessage());
    }

    @Test
    void amqpMessageCarriesEventIdTypeAndJsonBody() {
        OutboxMessage outbox = new OutboxMessage("e1", "catalog.plan.changed", "{\"eventId\":\"e1\"}",
                Instant.parse("2026-10-30T15:00:00Z"));

        Message message = RabbitEventSender.toAmqp(outbox);

        assertEquals("e1", message.getMessageProperties().getMessageId());
        assertEquals("catalog.plan.changed", message.getMessageProperties().getType());
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
        assertEquals("{\"eventId\":\"e1\"}", new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
