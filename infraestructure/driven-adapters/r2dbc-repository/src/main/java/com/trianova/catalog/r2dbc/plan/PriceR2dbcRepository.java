package com.trianova.catalog.r2dbc.plan;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la tabla prices. deleteByPlanId permite la estrategia
 * "borrar e insertar" al guardar el agregado completo.
 */
public interface PriceR2dbcRepository extends ReactiveCrudRepository<PriceData, String> {

    Flux<PriceData> findByPlanId(String planId);

    Mono<Void> deleteByPlanId(String planId);
}
