package com.trianova.catalog.domain.plan.event;

import com.trianova.catalog.domain.common.event.DomainEvent;
import com.trianova.catalog.domain.plan.BillingCycle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento emitido cuando cambia el precio de un plan.
 * Se publica en el exchange trianova.events con routing key {@value #TYPE}.
 */
public record PriceUpdated(
        String agentId,
        String planId,
        BillingCycle billingCycle,
        BigDecimal amount,
        String currency,
        Instant occurredAt) implements DomainEvent {

    public static final String TYPE = "catalog.price.updated";

    @Override
    public String type() {
        return TYPE;
    }
}
