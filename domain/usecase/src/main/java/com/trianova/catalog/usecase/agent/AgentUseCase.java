package com.trianova.catalog.usecase.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.AgentStatus;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

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
        return agentGateway.findByStatus(AgentStatus.PUBLISHED);
    }

    public Flux<Agent> findAllAgents() {
        return agentGateway.findAll();
    }

    public Mono<Agent> findAgent(String idOrSlug) {
        return agentGateway.findById(idOrSlug)
                .switchIfEmpty(Mono.defer(() -> agentGateway.findBySlug(idOrSlug)))
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()));
    }

    public Mono<Agent> createAgent(Agent agent) {
        return agentGateway.findBySlug(agent.getSlug())
                .flatMap(existing -> Mono.<Agent>error(BusinessException.Type.SLUG_ALREADY_EXISTS.defer()))
                .switchIfEmpty(Mono.defer(() -> agentGateway.save(agent.toBuilder()
                        .id(UUID.randomUUID().toString())
                        .status(AgentStatus.DRAFT)
                        .createdAt(Instant.now())
                        .build())));
    }

    public Mono<Agent> updateAgent(String id, Agent data) {
        return agentGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .flatMap(existing -> agentGateway.save(existing.toBuilder()
                        .name(data.getName())
                        .description(data.getDescription())
                        .category(data.getCategory())
                        .updatedAt(Instant.now())
                        .build()));
    }

    public Mono<Agent> publishAgent(String id) {
        return agentGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .flatMap(agent -> planGateway.findPublishedByAgentId(id)
                        .hasElements()
                        .flatMap(hasPublishedPlan -> Boolean.TRUE.equals(hasPublishedPlan)
                                ? agentGateway.save(agent.publish())
                                : Mono.<Agent>error(BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN.defer())));
    }

    public Mono<Agent> archiveAgent(String id) {
        return agentGateway.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.defer()))
                .map(Agent::archive)
                .flatMap(agentGateway::save);
    }
}
