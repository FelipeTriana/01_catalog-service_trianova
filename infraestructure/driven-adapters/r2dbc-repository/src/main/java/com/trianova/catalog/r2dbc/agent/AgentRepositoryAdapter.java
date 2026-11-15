package com.trianova.catalog.r2dbc.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.r2dbc.commons.AdapterOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adaptador de SALIDA: implementa el puerto AgentGateway sobre R2DBC, reutilizando
 * AdapterOperations. Traduce entre el dominio (Agent) y la tabla (AgentData).
 */
@Repository
public class AgentRepositoryAdapter
        extends AdapterOperations<Agent, AgentData, String, AgentR2dbcRepository>
        implements AgentGateway {

    public AgentRepositoryAdapter(AgentR2dbcRepository repository, R2dbcEntityTemplate template) {
        super(repository, template,
                AgentRepositoryAdapter::dataFrom,
                AgentRepositoryAdapter::domainFrom,
                AgentData::getId);
    }

    @Override
    public Mono<Agent> findById(String id) {
        return super.findById(id);
    }

    @Override
    public Mono<Agent> findBySlug(String slug) {
        return mapOne(repository.findBySlug(slug));
    }

    @Override
    public Flux<Agent> findByStatus(PublicationStatus status) {
        return mapMany(repository.findByStatus(status.name()));
    }

    @Override
    public Flux<Agent> findAll() {
        return super.findAll();
    }

    @Override
    public Mono<Agent> save(Agent agent) {
        return super.save(agent);
    }

    static AgentData dataFrom(Agent a) {
        return AgentData.builder()
                .id(a.getId())
                .slug(a.getSlug())
                .name(a.getName())
                .description(a.getDescription())
                .category(a.getCategory())
                .status(a.getStatus() != null ? a.getStatus().name() : null)
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    static Agent domainFrom(AgentData d) {
        return Agent.builder()
                .id(d.getId())
                .slug(d.getSlug())
                .name(d.getName())
                .description(d.getDescription())
                .category(d.getCategory())
                .status(d.getStatus() != null ? PublicationStatus.valueOf(d.getStatus()) : null)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
