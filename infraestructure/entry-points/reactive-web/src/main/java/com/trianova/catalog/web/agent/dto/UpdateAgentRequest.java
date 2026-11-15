package com.trianova.catalog.web.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de PUT /api/v1/admin/agents/{id}. No incluye el slug: el caso de uso
 * no lo modifica, así que aceptarlo daría a entender algo que no ocurre.
 */
public record UpdateAgentRequest(
        @NotBlank String name,
        String description,
        String category) {
}
