package com.trianova.catalog.usecase.plan;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.event.EventPublisher;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;
import com.trianova.catalog.domain.plan.event.PlanChanged;
import com.trianova.catalog.domain.plan.event.PlanChanged.ChangeType;
import com.trianova.catalog.domain.plan.event.PriceUpdated;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Casos de uso de planes. Cada operación que cambia un plan lo persiste y luego
 * emite su evento (catalog.plan.changed o catalog.price.updated) por el EventPublisher.
 *
 * Nota de consistencia: guardar y emitir son dos escrituras. Hasta que el app-service
 * aporte la transacción, un fallo entre ambas deja el cambio sin evento.
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
                .filter(Agent::isPublished)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .flatMap(agent -> planGateway.findPublishedByAgentId(agent.getId())
                        .collectList()
                        .map(plans -> new PublishedPlans(agent.getId(), plans)));
    }

    public Mono<Plan> createPlan(Plan plan) {
        return agentGateway.findById(plan.getAgentId())
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .flatMap(agent -> saveAndEmit(plan.toBuilder()
                        .id(UUID.randomUUID().toString())
                        .status(PublicationStatus.DRAFT)
                        .createdAt(Instant.now())
                        .build(), ChangeType.CREATED));
    }

    public Mono<Plan> updatePlan(String id, Plan data) {
        return findExisting(id)
                .flatMap(existing -> saveAndEmit(existing.toBuilder()
                        .name(data.getName())
                        .description(data.getDescription())
                        // Semántica PUT: la lista enviada reemplaza a la anterior. Los precios no se tocan (van por /price).
                        .clearFeatures()
                        .features(data.getFeatures())
                        .updatedAt(Instant.now())
                        .build(), ChangeType.UPDATED));
    }

    /** Cambia el precio del ciclo indicado y emite catalog.price.updated con el monto anterior y el nuevo. */
    public Mono<Plan> changePrice(String planId, Price newPrice) {
        return findExisting(planId)
                .flatMap(before -> planGateway.save(before.changePrice(newPrice))
                        .flatMap(saved -> eventPublisher.publish(PriceUpdated.of(before, newPrice))
                                .thenReturn(saved)));
    }

    public Mono<Plan> publishPlan(String id) {
        return findExisting(id)
                .map(Plan::publish)
                .flatMap(plan -> saveAndEmit(plan, ChangeType.PUBLISHED));
    }

    /**
     * PUBLISHED -> ARCHIVED. Si el agente está publicado, el plan no puede ser su
     * último plan publicado: el agente quedaría en el catálogo sin nada que vender.
     * Primero se valida la transición del plan y después la invariante.
     */
    public Mono<Plan> archivePlan(String id) {
        return findExisting(id)
                .flatMap(plan -> {
                    Plan archived = plan.archive();
                    return agentKeepsAPublishedPlanWithout(plan)
                            .flatMap(keeps -> Boolean.TRUE.equals(keeps)
                                    ? saveAndEmit(archived, ChangeType.ARCHIVED)
                                    : Mono.<Plan>error(BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN.defer()));
                });
    }

    /** ARCHIVED -> PUBLISHED. Suma un plan publicado, así que no puede romper la invariante. */
    public Mono<Plan> restorePlan(String id) {
        return findExisting(id)
                .map(Plan::restore)
                .flatMap(plan -> saveAndEmit(plan, ChangeType.RESTORED));
    }

    /**
     * Solo un DRAFT se elimina. Un plan en DRAFT no cuenta para la invariante del
     * agente, así que borrarlo nunca la rompe. Precios y features caen en cascada.
     */
    public Mono<Void> deletePlan(String id) {
        return findExisting(id)
                .filter(Plan::isDeletable)
                .switchIfEmpty(Mono.error(BusinessException.Type.CANNOT_DELETE_NON_DRAFT.defer()))
                .flatMap(plan -> planGateway.deleteById(id)
                        .then(Mono.defer(() -> eventPublisher.publish(PlanChanged.of(plan, ChangeType.DELETED)))));
    }

    /** Persiste el plan y, solo si se guardó, emite catalog.plan.changed. */
    private Mono<Plan> saveAndEmit(Plan plan, ChangeType changeType) {
        return planGateway.save(plan)
                .flatMap(saved -> eventPublisher.publish(PlanChanged.of(saved, changeType))
                        .thenReturn(saved));
    }

    /**
     * true si archivar el plan deja la invariante intacta: el agente no está
     * publicado (o no existe), o tiene otro plan publicado además de este.
     */
    private Mono<Boolean> agentKeepsAPublishedPlanWithout(Plan plan) {
        return agentGateway.findById(plan.getAgentId())
                .filter(Agent::isPublished)
                .flatMap(agent -> planGateway.findPublishedByAgentId(agent.getId())
                        .any(other -> !other.getId().equals(plan.getId())))
                .defaultIfEmpty(true);
    }

    private Mono<Plan> findExisting(String id) {
        return planGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.PLAN_NOT_FOUND.defer()));
    }
}
