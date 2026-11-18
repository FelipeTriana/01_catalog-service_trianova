package com.trianova.catalog.rabbitmq.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.event.DomainEvent;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.event.PlanChanged;
import com.trianova.catalog.domain.plan.event.PriceUpdated;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato JSON de los eventos (spec §8). Si este test cambia, cambia lo que
 * reciben los consumidores: hay que subir la versión del evento.
 */
class EventContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Instant AT = Instant.parse("2026-10-30T15:00:00Z");

    private JsonNode json(DomainEvent event) throws Exception {
        return mapper.readTree(EventContract.toJson(mapper, event));
    }

    @Test
    void priceUpdatedMatchesTheSpecShape() throws Exception {
        PriceUpdated event = new PriceUpdated("e1", AT, "a1", "p1", BillingCycle.MONTHLY,
                Money.of(new BigDecimal("49900.0000"), "COP"),
                Money.of(new BigDecimal("59900"), "COP"));

        JsonNode expected = mapper.readTree("""
                {
                  "eventId": "e1",
                  "version": 1,
                  "occurredAt": "2026-10-30T15:00:00Z",
                  "agentId": "a1",
                  "planId": "p1",
                  "billingCycle": "MONTHLY",
                  "oldAmount": { "amount": "49900.00", "currency": "COP" },
                  "newAmount": { "amount": "59900.00", "currency": "COP" }
                }
                """);
        assertEquals(expected, json(event));
    }

    @Test
    void priceUpdatedForANewCycleSendsNullOldAmount() throws Exception {
        PriceUpdated event = new PriceUpdated("e1", AT, "a1", "p1", BillingCycle.YEARLY,
                null, Money.of(new BigDecimal("599000"), "COP"));

        JsonNode node = json(event);
        assertTrue(node.has("oldAmount"));
        assertTrue(node.get("oldAmount").isNull());
    }

    @Test
    void planChangedMatchesTheSpecShape() throws Exception {
        PlanChanged event = new PlanChanged("e2", AT, "a1", "p1", PlanChanged.ChangeType.PUBLISHED, "Pro");

        JsonNode expected = mapper.readTree("""
                {
                  "eventId": "e2",
                  "version": 1,
                  "occurredAt": "2026-10-30T15:00:00Z",
                  "agentId": "a1",
                  "planId": "p1",
                  "changeType": "PUBLISHED",
                  "name": "Pro"
                }
                """);
        assertEquals(expected, json(event));
    }

    @Test
    void unknownEventTypeIsRejectedInsteadOfSentWithoutContract() {
        DomainEvent unknown = new DomainEvent() {
            public String eventId() { return "x"; }
            public String type() { return "catalog.unknown"; }
            public Instant occurredAt() { return AT; }
            public String aggregateType() { return "X"; }
            public String aggregateId() { return "x"; }
        };

        assertThrows(IllegalArgumentException.class, () -> EventContract.toJson(mapper, unknown));
    }
}
