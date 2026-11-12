package com.trianova.catalog.domain.plan.gateway;

import com.trianova.catalog.domain.plan.Plan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para persistir y consultar planes. El adaptador R2DBC lo
 * implementará; el dominio solo conoce esta interfaz.
 */
public interface PlanGateway {

    Mono<Plan> findById(String id);

    Flux<Plan> findByAgentId(String agentId);

    Flux<Plan> findPublishedByAgentId(String agentId);

    Mono<Plan> save(Plan plan);
}
