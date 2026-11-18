package com.trianova.catalog.domain.plan.event;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanEventsTest {

    private static Price price(String amount, BillingCycle cycle) {
        return new Price(Money.of(new BigDecimal(amount), "COP"), cycle);
    }

    private static Plan plan() {
        return Plan.builder()
                .id("p1").agentId("a1").name("Pro")
                .status(PublicationStatus.PUBLISHED)
                .price(price("49900", BillingCycle.MONTHLY))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void priceUpdatedCarriesOldAndNewAmountOfTheSameCycle() {
        PriceUpdated event = PriceUpdated.of(plan(), price("59900", BillingCycle.MONTHLY));

        assertEquals("a1", event.agentId());
        assertEquals("p1", event.planId());
        assertEquals(BillingCycle.MONTHLY, event.billingCycle());
        assertEquals(new BigDecimal("49900"), event.oldAmount().amount());
        assertEquals(new BigDecimal("59900"), event.newAmount().amount());
        assertEquals("catalog.price.updated", event.type());
        assertEquals("PLAN", event.aggregateType());
        assertEquals("p1", event.aggregateId());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
    }

    @Test
    void priceUpdatedHasNoOldAmountWhenTheCycleIsNew() {
        PriceUpdated event = PriceUpdated.of(plan(), price("499000", BillingCycle.YEARLY));

        assertNull(event.oldAmount());
        assertEquals(new BigDecimal("499000"), event.newAmount().amount());
    }

    @Test
    void everyEventGetsItsOwnId() {
        Plan plan = plan();

        assertNotEquals(PlanChanged.of(plan, PlanChanged.ChangeType.UPDATED).eventId(),
                PlanChanged.of(plan, PlanChanged.ChangeType.UPDATED).eventId());
    }

    @Test
    void planChangedDescribesThePlanAndTheChange() {
        PlanChanged event = PlanChanged.of(plan(), PlanChanged.ChangeType.ARCHIVED);

        assertEquals("a1", event.agentId());
        assertEquals("p1", event.planId());
        assertEquals("Pro", event.name());
        assertEquals(PlanChanged.ChangeType.ARCHIVED, event.changeType());
        assertEquals("catalog.plan.changed", event.type());
        assertEquals("PLAN", event.aggregateType());
        assertEquals("p1", event.aggregateId());
    }
}
