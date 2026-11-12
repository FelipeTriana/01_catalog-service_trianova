package com.trianova.catalog.domain.plan;

import com.trianova.catalog.domain.common.ex.BusinessException;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Raíz de agregado: un nivel de precio de un agente (p. ej. "Starter", "Pro").
 * Referencia al agente por {@code agentId}. Inmutable; las transiciones devuelven
 * una nueva instancia. {@code @Singular} mantiene las listas inmutables.
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public class Plan {

    private final String id;
    private final String agentId;
    private final String name;
    private final String description;
    private final PlanStatus status;

    @Singular
    private final List<Price> prices;

    @Singular
    private final List<Feature> features;

    private final Instant createdAt;
    private final Instant updatedAt;

    /** DRAFT -> PUBLISHED. No se publica sin al menos un precio válido. */
    public Plan publish() {
        if (status != PlanStatus.DRAFT) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        if (prices == null || prices.isEmpty()) {
            throw new BusinessException(BusinessException.Type.PLAN_HAS_NO_VALID_PRICE);
        }
        return toBuilder()
                .status(PlanStatus.PUBLISHED)
                .updatedAt(Instant.now())
                .build();
    }

    public Plan archive() {
        if (status == PlanStatus.ARCHIVED) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        return toBuilder()
                .status(PlanStatus.ARCHIVED)
                .updatedAt(Instant.now())
                .build();
    }

    /** Reemplaza el precio del mismo ciclo (o lo agrega) y devuelve un nuevo Plan. */
    public Plan changePrice(Price newPrice) {
        if (newPrice == null) {
            throw new IllegalArgumentException("newPrice must not be null");
        }
        List<Price> updated = new ArrayList<>();
        for (Price p : prices) {
            if (p.billingCycle() != newPrice.billingCycle()) {
                updated.add(p);
            }
        }
        updated.add(newPrice);
        return toBuilder()
                .clearPrices()
                .prices(updated)
                .updatedAt(Instant.now())
                .build();
    }

    public boolean isPublished() {
        return status == PlanStatus.PUBLISHED;
    }
}
