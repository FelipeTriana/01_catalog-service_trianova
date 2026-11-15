package com.trianova.catalog.usecase.plan;

import com.trianova.catalog.domain.agent.AgentStatus;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.event.EventPublisher;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.PlanStatus;
import com.trianova.catalog.domain.plan.Price;
import com.trianova.catalog.domain.plan.event.PriceUpdated;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Casos de uso de planes. changePrice y publishPlan apoyan las transiciones del
 * dominio y, cuando corresponde, emiten eventos a través del EventPublisher.
 */
@RequiredArgsConstructor
public class PlanUseCase {

    private final PlanGateway planGateway;
    private final AgentGateway agentGateway;
    private final EventPublisher eventPublisher;

    /**
     * Catálogo público: acepta id o slug y solo responde si el agente está publicado
     * (si no, AGENT_NOT_FOUND, igual que el detalle del agente).
     */
    public Mono<PublishedPlans> findPublishedPlans(String agentIdOrSlug) {
        return agentGateway.findByIdOrSlug(agentIdOrSlug)
                .filter(agent -> agent.getStatus() == AgentStatus.PUBLISHED)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .flatMap(agent -> planGateway.findPublishedByAgentId(agent.getId())
                        .collectList()
                        .map(plans -> new PublishedPlans(agent.getId(), plans)));
    }

    public Mono<Plan> createPlan(Plan plan) {
        return agentGateway.findById(plan.getAgentId())
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .flatMap(agent -> planGateway.save(plan.toBuilder()
                        .id(UUID.randomUUID().toString())
                        .status(PlanStatus.DRAFT)
                        .createdAt(Instant.now())
                        .build()));
    }

    public Mono<Plan> updatePlan(String id, Plan data) {
        return planGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.PLAN_NOT_FOUND.defer()))
                .flatMap(existing -> planGateway.save(existing.toBuilder()
                        .name(data.getName())
                        .description(data.getDescription())
                        // Semántica PUT: la lista enviada reemplaza a la anterior. Los precios no se tocan (van por /price).
                        .clearFeatures()
                        .features(data.getFeatures())
                        .updatedAt(Instant.now())
                        .build()));
    }

    /** Cambia el precio del ciclo indicado, persiste y emite catalog.price.updated. */
    public Mono<Plan> changePrice(String planId, Price newPrice) {
        return planGateway.findById(planId)
                .switchIfEmpty(Mono.error(BusinessException.Type.PLAN_NOT_FOUND.defer()))
                .map(plan -> plan.changePrice(newPrice))
                .flatMap(planGateway::save)
                .flatMap(saved -> eventPublisher.publish(new PriceUpdated(
                                saved.getAgentId(),
                                saved.getId(),
                                newPrice.billingCycle(),
                                newPrice.amount().amount(),
                                newPrice.amount().currency().getCurrencyCode(),
                                Instant.now()))
                        .thenReturn(saved));
    }

    public Mono<Plan> publishPlan(String id) {
        return planGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.PLAN_NOT_FOUND.defer()))
                .map(Plan::publish)
                .flatMap(planGateway::save);
    }
}
