package com.trianova.catalog.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.event.PlanChanged;
import com.trianova.catalog.domain.plan.event.PriceUpdated;
import com.trianova.catalog.rabbitmq.outbox.OutboxEventPublisher;
import com.trianova.catalog.rabbitmq.outbox.OutboxRepository;
import com.trianova.catalog.rabbitmq.relay.OutboxRelay;
import com.trianova.catalog.rabbitmq.relay.RabbitEventSender;
import io.r2dbc.spi.ConnectionFactories;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Recorrido completo del outbox contra PostgreSQL y RabbitMQ reales:
 * publicar (fila pendiente) -> relay -> mensaje en el exchange -> fila marcada.
 * Una cola de prueba enlazada a "catalog.#" hace de consumidor. Se SALTA sin Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxIT {

    private static final String EXCHANGE = "trianova.events";
    private static final String QUEUE = "test.catalog.events";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    static R2dbcEntityTemplate template;
    static CachingConnectionFactory rabbitConnections;
    static RabbitTemplate rabbitTemplate;
    static RabbitAdmin admin;

    final ObjectMapper mapper = new ObjectMapper();
    OutboxRepository outbox;
    OutboxEventPublisher publisher;
    OutboxRelay relay;

    @BeforeAll
    static void setUpInfrastructure() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        template = new R2dbcEntityTemplate(ConnectionFactories.get("r2dbc:postgresql://"
                + POSTGRES.getUsername() + ":" + POSTGRES.getPassword() + "@"
                + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName()));

        rabbitConnections = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        rabbitConnections.setUsername(RABBIT.getAdminUsername());
        rabbitConnections.setPassword(RABBIT.getAdminPassword());
        rabbitConnections.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        rabbitTemplate = new RabbitTemplate(rabbitConnections);
        admin = new RabbitAdmin(rabbitConnections);
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        Queue queue = new Queue(QUEUE, false);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("catalog.#"));
    }

    @AfterAll
    static void tearDown() {
        rabbitConnections.destroy();
    }

    @BeforeEach
    void cleanState() {
        template.getDatabaseClient().sql("DELETE FROM outbox").then().block();
        admin.purgeQueue(QUEUE, false);
        outbox = new OutboxRepository(template);
        publisher = new OutboxEventPublisher(outbox, mapper);
        relay = new OutboxRelay(outbox, new RabbitEventSender(rabbitTemplate, EXCHANGE), 100);
    }

    private static PlanChanged planChanged(String eventId, Instant at, PlanChanged.ChangeType type) {
        return new PlanChanged(eventId, at, "a1", "p1", type, "Pro");
    }

    @Test
    void publishOnlyStoresThePendingEventWithoutTouchingTheBroker() {
        StepVerifier.create(publisher.publish(planChanged("e-store", Instant.now(), PlanChanged.ChangeType.CREATED)))
                .verifyComplete();

        StepVerifier.create(outbox.findPending(10))
                .expectNextMatches(m -> m.id().equals("e-store") && m.eventType().equals("catalog.plan.changed"))
                .verifyComplete();
        assertEquals(null, rabbitTemplate.receive(QUEUE, 500));
    }

    @Test
    void relayDeliversTheContractJsonAndMarksTheRowAsPublished() throws Exception {
        PriceUpdated event = new PriceUpdated("e-price", Instant.parse("2026-10-30T15:00:00Z"), "a1", "p1",
                BillingCycle.MONTHLY, Money.of(new BigDecimal("49900"), "COP"), Money.of(new BigDecimal("59900"), "COP"));
        publisher.publish(event).block();

        StepVerifier.create(relay.publishPending()).expectNext(1L).verifyComplete();

        Message received = rabbitTemplate.receive(QUEUE, 5000);
        assertNotNull(received, "the event never reached the queue");
        assertEquals("e-price", received.getMessageProperties().getMessageId());
        assertEquals("catalog.price.updated", received.getMessageProperties().getReceivedRoutingKey());
        assertEquals(mapper.readTree("""
                {
                  "eventId": "e-price",
                  "version": 1,
                  "occurredAt": "2026-10-30T15:00:00Z",
                  "agentId": "a1",
                  "planId": "p1",
                  "billingCycle": "MONTHLY",
                  "oldAmount": { "amount": "49900.00", "currency": "COP" },
                  "newAmount": { "amount": "59900.00", "currency": "COP" }
                }
                """), mapper.readTree(new String(received.getBody(), StandardCharsets.UTF_8)));

        StepVerifier.create(outbox.findPending(10)).verifyComplete();
        StepVerifier.create(relay.publishPending()).expectNext(0L).verifyComplete();
    }

    @Test
    void eventsArriveInTheOrderTheyOccurred() {
        Instant t0 = Instant.parse("2026-10-30T15:00:00Z");
        // Se guardan desordenados a propósito: manda occurredAt, no el orden de inserción.
        publisher.publish(planChanged("e-3", t0.plusSeconds(2), PlanChanged.ChangeType.ARCHIVED)).block();
        publisher.publish(planChanged("e-1", t0, PlanChanged.ChangeType.CREATED)).block();
        publisher.publish(planChanged("e-2", t0.plusSeconds(1), PlanChanged.ChangeType.PUBLISHED)).block();

        StepVerifier.create(relay.publishPending()).expectNext(3L).verifyComplete();

        List<String> received = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Message message = rabbitTemplate.receive(QUEUE, 5000);
            assertNotNull(message);
            received.add(message.getMessageProperties().getMessageId());
        }
        assertEquals(List.of("e-1", "e-2", "e-3"), received);
    }
}
