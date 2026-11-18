package com.trianova.catalog.rabbitmq.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.event.DomainEvent;
import com.trianova.catalog.domain.plan.event.PlanChanged;
import com.trianova.catalog.domain.plan.event.PriceUpdated;

/**
 * Contrato JSON de los eventos publicados (spec §8, versión 1).
 *
 * El dominio define QUÉ pasó; esta clase define CÓMO viaja. Se usan records
 * propios (y no los eventos del dominio) para que un cambio interno en el dominio
 * no altere sin querer el JSON que leen otros servicios. Todo viaja como texto
 * simple: montos como "59900.00" y fechas en ISO-8601, sin depender de módulos de Jackson.
 */
public final class EventContract {

    public static final int VERSION = 1;

    private EventContract() {
    }

    public static String toJson(ObjectMapper mapper, DomainEvent event) {
        try {
            return mapper.writeValueAsString(payloadOf(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.eventId(), e);
        }
    }

    /** Un evento sin contrato no se envía: se rechaza en lugar de publicar un JSON improvisado. */
    static Object payloadOf(DomainEvent event) {
        return switch (event) {
            case PriceUpdated e -> new PriceUpdatedV1(
                    e.eventId(), VERSION, e.occurredAt().toString(),
                    e.agentId(), e.planId(), e.billingCycle().name(),
                    AmountV1.of(e.oldAmount()), AmountV1.of(e.newAmount()));
            case PlanChanged e -> new PlanChangedV1(
                    e.eventId(), VERSION, e.occurredAt().toString(),
                    e.agentId(), e.planId(), e.changeType().name(), e.name());
            default -> throw new IllegalArgumentException("No JSON contract for event type " + event.type());
        };
    }

    public record AmountV1(String amount, String currency) {
        static AmountV1 of(Money money) {
            return money == null ? null : new AmountV1(money.toPlainAmount(), money.currency().getCurrencyCode());
        }
    }

    public record PriceUpdatedV1(
            String eventId,
            int version,
            String occurredAt,
            String agentId,
            String planId,
            String billingCycle,
            AmountV1 oldAmount,
            AmountV1 newAmount) {
    }

    public record PlanChangedV1(
            String eventId,
            int version,
            String occurredAt,
            String agentId,
            String planId,
            String changeType,
            String name) {
    }
}
