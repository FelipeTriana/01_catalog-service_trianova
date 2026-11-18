package com.trianova.catalog.domain.plan.event;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.event.DomainEvent;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento emitido cuando cambia el precio de un ciclo de un plan.
 * {@code oldAmount} es null cuando el ciclo no tenía precio (se agregó uno nuevo).
 */
public record PriceUpdated(
        String eventId,
        Instant occurredAt,
        String agentId,
        String planId,
        BillingCycle billingCycle,
        Money oldAmount,
        Money newAmount) implements DomainEvent {

    public static final String TYPE = "catalog.price.updated";

    /** Construye el evento a partir del plan ANTES del cambio y del precio nuevo. */
    public static PriceUpdated of(Plan before, Price newPrice) {
        Money oldAmount = before.priceFor(newPrice.billingCycle())
                .map(Price::amount)
                .orElse(null);
        return new PriceUpdated(
                UUID.randomUUID().toString(),
                Instant.now(),
                before.getAgentId(),
                before.getId(),
                newPrice.billingCycle(),
                oldAmount,
                newPrice.amount());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateType() {
        return PlanChanged.AGGREGATE_TYPE;
    }

    @Override
    public String aggregateId() {
        return planId;
    }
}
