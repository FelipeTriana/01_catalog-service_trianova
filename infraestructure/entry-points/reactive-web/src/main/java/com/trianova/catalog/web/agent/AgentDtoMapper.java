package com.trianova.catalog.web.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.web.agent.dto.AgentResponse;
import com.trianova.catalog.web.agent.dto.CreateAgentRequest;
import com.trianova.catalog.web.agent.dto.UpdateAgentRequest;

/**
 * Traducción entre los DTOs HTTP y el agregado Agent. Vive en este módulo porque
 * los DTOs son propios del adaptador web; el dominio no los conoce.
 */
final class AgentDtoMapper {

    private AgentDtoMapper() {
    }

    static Agent toDomain(CreateAgentRequest request) {
        return Agent.builder()
                .slug(request.slug())
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .build();
    }

    static Agent toDomain(UpdateAgentRequest request) {
        return Agent.builder()
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .build();
    }

    static AgentResponse toResponse(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getSlug(),
                agent.getName(),
                agent.getDescription(),
                agent.getCategory(),
                agent.getStatus() == null ? null : agent.getStatus().name(),
                agent.getCreatedAt(),
                agent.getUpdatedAt());
    }
}
