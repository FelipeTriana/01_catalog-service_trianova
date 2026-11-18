package com.trianova.catalog.web.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.Feature;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;
import com.trianova.catalog.web.plan.dto.CreatePlanRequest;
import com.trianova.catalog.web.plan.dto.FeatureRequest;
import com.trianova.catalog.web.plan.dto.PlanResponse;
import com.trianova.catalog.web.plan.dto.PlanResponse.FeatureResponse;
import com.trianova.catalog.web.plan.dto.PlanResponse.PriceResponse;
import com.trianova.catalog.web.plan.dto.PriceRequest;
import com.trianova.catalog.web.plan.dto.UpdatePlanRequest;

import java.util.List;

/** Traducción entre los DTOs HTTP y el agregado Plan. */
final class PlanDtoMapper {

    private PlanDtoMapper() {
    }

    static Plan toDomain(String agentId, CreatePlanRequest request) {
        return Plan.builder()
                .agentId(agentId)
                .name(request.name())
                .description(request.description())
                .prices(nullToEmpty(request.prices()).stream().map(PlanDtoMapper::toPrice).toList())
                .features(nullToEmpty(request.features()).stream().map(PlanDtoMapper::toFeature).toList())
                .build();
    }

    static Plan toDomain(UpdatePlanRequest request) {
        return Plan.builder()
                .name(request.name())
                .description(request.description())
                .features(nullToEmpty(request.features()).stream().map(PlanDtoMapper::toFeature).toList())
                .build();
    }

    /**
     * Money.of rechaza monedas que no son ISO-4217 y montos negativos con
     * IllegalArgumentException. Para el cliente ambos casos son el mismo error de
     * negocio que un monto en cero: INVALID_PRICE (422), tal como define la spec.
     */
    static Price toPrice(PriceRequest request) {
        Money money;
        try {
            money = Money.of(request.amount(), request.currency());
        } catch (IllegalArgumentException e) {
            throw BusinessException.Type.INVALID_PRICE.build();
        }
        return new Price(money, request.billingCycle());
    }

    static Feature toFeature(FeatureRequest request) {
        return new Feature(request.code(), request.name(), request.description(), request.limitValue());
    }

    static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getAgentId(),
                plan.getName(),
                plan.getDescription(),
                plan.getStatus() == null ? null : plan.getStatus().name(),
                plan.getPrices().stream().map(PlanDtoMapper::toPriceResponse).toList(),
                plan.getFeatures().stream().map(PlanDtoMapper::toFeatureResponse).toList(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    static PriceResponse toPriceResponse(Price price) {
        return new PriceResponse(
                price.billingCycle().name(),
                formatAmount(price.amount()),
                price.amount().currency().getCurrencyCode());
    }

    static FeatureResponse toFeatureResponse(Feature feature) {
        return new FeatureResponse(feature.code(), feature.name(), feature.description(), feature.limitValue());
    }

    /** Formato canónico del dominio: el mismo que viaja en los eventos. */
    static String formatAmount(Money money) {
        return money.toPlainAmount();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
