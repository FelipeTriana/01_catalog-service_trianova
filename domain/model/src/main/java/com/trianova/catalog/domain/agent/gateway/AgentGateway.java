package com.trianova.catalog.domain.agent.gateway;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.common.PublicationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para persistir y consultar agentes. El dominio solo conoce
 * esta interfaz; el adaptador R2DBC la implementará.
 */
public interface AgentGateway {

    Mono<Agent> findById(String id);

    Mono<Agent> findBySlug(String slug);

    Flux<Agent> findByStatus(PublicationStatus status);

    Flux<Agent> findAll();

    Mono<Agent> save(Agent agent);

    Mono<Void> deleteById(String id);

    /** Busca primero por id y, si no existe, por slug. Vacío si no hay ninguno. */
    default Mono<Agent> findByIdOrSlug(String idOrSlug) {
        return findById(idOrSlug).switchIfEmpty(Mono.defer(() -> findBySlug(idOrSlug)));
    }
}
