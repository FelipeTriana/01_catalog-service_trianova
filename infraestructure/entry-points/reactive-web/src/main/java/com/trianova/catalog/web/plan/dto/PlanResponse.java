package com.trianova.catalog.web.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Representación HTTP de un plan con sus precios y features. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanResponse(
        String id,
        String agentId,
        String name,
        String description,
        String status,
        List<PriceResponse> prices,
        List<FeatureResponse> features,
        Instant createdAt,
        Instant updatedAt) {

    /** Monto como texto para no perder precisión en clientes que leen números como double. */
    public record PriceResponse(String billingCycle, String amount, String currency) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeatureResponse(String code, String name, String description, String limitValue) {
    }
}
