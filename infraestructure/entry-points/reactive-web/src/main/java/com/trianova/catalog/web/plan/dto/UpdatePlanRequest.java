package com.trianova.catalog.web.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Cuerpo de PUT /api/v1/admin/plans/{id}: nombre, descripción y features.
 * Semántica PUT: las features enviadas reemplazan a las actuales; si se omiten,
 * el plan queda sin features. Los precios cambian por /price.
 */
public record UpdatePlanRequest(
        @NotBlank String name,
        String description,
        List<@Valid FeatureRequest> features) {
}
