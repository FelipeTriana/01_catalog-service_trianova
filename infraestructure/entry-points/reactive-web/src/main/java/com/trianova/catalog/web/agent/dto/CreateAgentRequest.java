package com.trianova.catalog.web.agent.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de POST /api/v1/admin/agents. */
public record CreateAgentRequest(
        @NotBlank String slug,
        @NotBlank String name,
        String description,
        String category) {
}
