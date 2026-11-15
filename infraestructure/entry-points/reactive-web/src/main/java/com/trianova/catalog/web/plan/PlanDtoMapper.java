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

import java.math.BigDecimal;
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

    /**
     * Muestra el monto con los decimales propios de la moneda (COP/USD: 2) sin
     * redondear jamás: si el valor trae más decimales significativos, se conservan.
     * La base guarda NUMERIC(19,4), así que 59900 llega como 59900.0000 y sale "59900.00".
     */
    static String formatAmount(Money money) {
        BigDecimal amount = money.amount();
        int currencyDigits = Math.max(money.currency().getDefaultFractionDigits(), 0);
        int significantDigits = amount.stripTrailingZeros().scale();
        return amount.setScale(Math.max(currencyDigits, significantDigits)).toPlainString();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
