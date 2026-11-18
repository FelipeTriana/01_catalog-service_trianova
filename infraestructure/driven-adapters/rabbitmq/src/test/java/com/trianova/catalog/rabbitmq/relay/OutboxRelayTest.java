package com.trianova.catalog.rabbitmq.relay;

import com.trianova.catalog.rabbitmq.outbox.OutboxMessage;
import com.trianova.catalog.rabbitmq.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas del relay, sin broker ni base: qué se marca como publicado y cuándo. */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    OutboxRepository outbox;
    @Mock
    RabbitEventSender sender;

    private static OutboxMessage message(String id) {
        return new OutboxMessage(id, "catalog.plan.changed", "{}", Instant.now());
    }

    @Test
    void marksEachMessageAsPublishedOnlyAfterTheBrokerConfirmsIt() {
        OutboxMessage m1 = message("m1");
        OutboxMessage m2 = message("m2");
        when(outbox.findPending(10)).thenReturn(Flux.just(m1, m2));
        when(sender.send(m1)).thenReturn(Mono.empty());
        when(sender.send(m2)).thenReturn(Mono.empty());
        when(outbox.markPublished(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(new OutboxRelay(outbox, sender, 10).publishPending())
                .expectNext(2L)
                .verifyComplete();

        InOrder order = inOrder(sender, outbox);
        order.verify(sender).send(m1);
        order.verify(outbox).markPublished("m1");
        order.verify(sender).send(m2);
        order.verify(outbox).markPublished("m2");
    }

    @Test
    void stopsAtTheFirstFailureSoLaterEventsDoNotOvertakeIt() {
        OutboxMessage m1 = message("m1");
        OutboxMessage m2 = message("m2");
        when(outbox.findPending(10)).thenReturn(Flux.just(m1, m2));
        when(sender.send(m1)).thenReturn(Mono.error(new IllegalStateException("broker down")));

        StepVerifier.create(new OutboxRelay(outbox, sender, 10).publishPending())
                .verifyError(IllegalStateException.class);

        verify(outbox, never()).markPublished(anyString());
        verify(sender, never()).send(m2);
    }

    @Test
    void nothingPendingPublishesNothing() {
        when(outbox.findPending(10)).thenReturn(Flux.empty());

        StepVerifier.create(new OutboxRelay(outbox, sender, 10).publishPending())
                .expectNext(0L)
                .verifyComplete();
    }
}
