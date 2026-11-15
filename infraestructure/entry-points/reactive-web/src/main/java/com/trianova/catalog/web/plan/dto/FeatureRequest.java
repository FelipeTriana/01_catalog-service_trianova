package com.trianova.catalog.web.plan.dto;

import jakarta.validation.constraints.NotBlank;

/** Feature recibida por HTTP. */
public record FeatureRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String limitValue) {
}
