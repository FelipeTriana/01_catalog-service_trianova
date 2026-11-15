package com.trianova.catalog.usecase.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Casos de uso de agentes. Orquesta el dominio y los puertos; no conoce HTTP ni
 * persistencia. Las reglas que cruzan agregados (publicar requiere un plan
 * publicado) viven aquí porque necesitan consultar el PlanGateway.
 */
@RequiredArgsConstructor
public class AgentUseCase {

    private final AgentGateway agentGateway;
    private final PlanGateway planGateway;

    public Flux<Agent> findPublishedAgents() {
        return agentGateway.findByStatus(PublicationStatus.PUBLISHED);
    }

    public Flux<Agent> findAllAgents() {
        return agentGateway.findAll();
    }

    /**
     * Solo agentes publicados, para el catálogo público. Un borrador o archivado
     * responde AGENT_NOT_FOUND para no revelar que existe.
     */
    public Mono<Agent> findPublishedAgent(String idOrSlug) {
        return agentGateway.findByIdOrSlug(idOrSlug)
                .filter(Agent::isPublished)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()));
    }

    public Mono<Agent> createAgent(Agent agent) {
        return agentGateway.findBySlug(agent.getSlug())
                .flatMap(existing -> Mono.<Agent>error(BusinessException.Type.SLUG_ALREADY_EXISTS.defer()))
                .switchIfEmpty(Mono.defer(() -> agentGateway.save(agent.toBuilder()
                        .id(UUID.randomUUID().toString())
                        .status(PublicationStatus.DRAFT)
                        .createdAt(Instant.now())
                        .build())));
    }

    public Mono<Agent> updateAgent(String id, Agent data) {
        return findExisting(id)
                .flatMap(existing -> agentGateway.save(existing.toBuilder()
                        .name(data.getName())
                        .description(data.getDescription())
                        .category(data.getCategory())
                        .updatedAt(Instant.now())
                        .build()));
    }

    /** DRAFT -> PUBLISHED. Exige al menos un plan publicado. */
    public Mono<Agent> publishAgent(String id) {
        return transitionRequiringPublishedPlan(id, Agent::publish);
    }

    /** ARCHIVED -> PUBLISHED. Misma invariante que publicar. */
    public Mono<Agent> restoreAgent(String id) {
        return transitionRequiringPublishedPlan(id, Agent::restore);
    }

    /** PUBLISHED -> ARCHIVED. Sus planes no cambian: quedan ocultos y vuelven con restore. */
    public Mono<Agent> archiveAgent(String id) {
        return findExisting(id)
                .map(Agent::archive)
                .flatMap(agentGateway::save);
    }

    /**
     * Solo un DRAFT se elimina, junto con todos sus planes: el agente nunca fue
     * público, así que ningún cliente pudo suscribirse a ellos. Primero los planes
     * y luego el agente: si algo falla a mitad, queda un DRAFT con menos planes
     * (estado válido) y no planes huérfanos. Reintentar completa la operación.
     */
    public Mono<Void> deleteAgent(String id) {
        return findExisting(id)
                .filter(Agent::isDeletable)
                .switchIfEmpty(Mono.error(BusinessException.Type.CANNOT_DELETE_NON_DRAFT.defer()))
                .flatMap(agent -> planGateway.deleteByAgentId(id)
                        .then(Mono.defer(() -> agentGateway.deleteById(id))));
    }

    /**
     * Valida primero el estado (la transición lanza INVALID_STATE_TRANSITION) y
     * después la invariante "un agente publicado tiene al menos un plan publicado".
     */
    private Mono<Agent> transitionRequiringPublishedPlan(String id, UnaryOperator<Agent> transition) {
        return findExisting(id)
                .map(transition)
                .flatMap(next -> planGateway.findPublishedByAgentId(id)
                        .hasElements()
                        .flatMap(hasPublishedPlan -> Boolean.TRUE.equals(hasPublishedPlan)
                                ? agentGateway.save(next)
                                : Mono.<Agent>error(BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN.defer())));
    }

    private Mono<Agent> findExisting(String id) {
        return agentGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()));
    }
}
