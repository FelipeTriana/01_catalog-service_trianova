package com.trianova.catalog.web.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Representación HTTP de un agente. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResponse(
        String id,
        String slug,
        String name,
        String description,
        String category,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
