package com.trianova.catalog.domain.plan.event;

import com.trianova.catalog.domain.common.event.DomainEvent;
import com.trianova.catalog.domain.plan.Plan;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento emitido en cada cambio del ciclo de vida de un plan. Los cambios de
 * precio no viajan aquí sino en {@link PriceUpdated}.
 */
public record PlanChanged(
        String eventId,
        Instant occurredAt,
        String agentId,
        String planId,
        ChangeType changeType,
        String name) implements DomainEvent {

    public static final String TYPE = "catalog.plan.changed";
    static final String AGGREGATE_TYPE = "PLAN";

    /**
     * RESTORED y DELETED no estaban en la spec original; se agregan porque el
     * ciclo de vida actual permite restaurar y borrar planes, y un consumidor que
     * recibió CREATED necesita enterarse de que el plan dejó de existir.
     */
    public enum ChangeType { CREATED, UPDATED, PUBLISHED, ARCHIVED, RESTORED, DELETED }

    public static PlanChanged of(Plan plan, ChangeType changeType) {
        return new PlanChanged(
                UUID.randomUUID().toString(),
                Instant.now(),
                plan.getAgentId(),
                plan.getId(),
                changeType,
                plan.getName());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateType() {
        return AGGREGATE_TYPE;
    }

    @Override
    public String aggregateId() {
        return planId;
    }
}
