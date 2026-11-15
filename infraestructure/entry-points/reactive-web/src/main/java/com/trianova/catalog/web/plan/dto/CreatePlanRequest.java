package com.trianova.catalog.web.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Cuerpo de POST /api/v1/admin/agents/{agentId}/plans. El agentId viene en la
 * ruta, no en el cuerpo. Las listas son opcionales: un plan en DRAFT puede nacer
 * sin precios (solo se exige al menos uno para publicarlo).
 */
public record CreatePlanRequest(
        @NotBlank String name,
        String description,
        List<@Valid PriceRequest> prices,
        List<@Valid FeatureRequest> features) {
}
