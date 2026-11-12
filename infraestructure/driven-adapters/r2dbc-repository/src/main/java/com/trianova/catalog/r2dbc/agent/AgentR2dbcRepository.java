package com.trianova.catalog.r2dbc.agent;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de Spring Data para la tabla agents. Las consultas
 * derivadas (findBySlug, findByStatus) las genera Spring a partir del nombre.
 */
public interface AgentR2dbcRepository extends ReactiveCrudRepository<AgentData, String> {

    Mono<AgentData> findBySlug(String slug);

    Flux<AgentData> findByStatus(String status);
}
