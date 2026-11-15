package com.trianova.catalog.r2dbc.plan;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la tabla plans (la raíz del agregado). Las consultas
 * por agente las deriva Spring Data a partir del nombre del método.
 */
public interface PlanR2dbcRepository extends ReactiveCrudRepository<PlanData, String> {

    Flux<PlanData> findByAgentId(String agentId);

    Flux<PlanData> findByAgentIdAndStatus(String agentId, String status);

    Mono<Void> deleteByAgentId(String agentId);
}
