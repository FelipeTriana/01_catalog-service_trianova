package com.trianova.catalog.r2dbc.plan;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la tabla plan_features. deleteByPlanId permite la
 * estrategia "borrar e insertar" al guardar el agregado completo.
 */
public interface PlanFeatureR2dbcRepository extends ReactiveCrudRepository<PlanFeatureData, String> {

    Flux<PlanFeatureData> findByPlanId(String planId);

    Mono<Void> deleteByPlanId(String planId);
}
