package com.trianova.catalog.domain.agent.gateway;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.AgentStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para persistir y consultar agentes. El dominio solo conoce
 * esta interfaz; el adaptador R2DBC la implementará.
 */
public interface AgentGateway {

    Mono<Agent> findById(String id);

    Mono<Agent> findBySlug(String slug);

    Flux<Agent> findByStatus(AgentStatus status);

    Flux<Agent> findAll();

    Mono<Agent> save(Agent agent);
}
