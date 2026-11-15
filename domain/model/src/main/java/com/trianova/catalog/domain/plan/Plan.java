package com.trianova.catalog.domain.plan;

import com.trianova.catalog.domain.common.PublicationStatus;
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
    private final PublicationStatus status;

    @Singular
    private final List<Price> prices;

    @Singular
    private final List<Feature> features;

    private final Instant createdAt;
    private final Instant updatedAt;

    /** DRAFT -> PUBLISHED. No se publica sin al menos un precio válido. */
    public Plan publish() {
        if (status != PublicationStatus.DRAFT) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        if (prices == null || prices.isEmpty()) {
            throw new BusinessException(BusinessException.Type.PLAN_HAS_NO_VALID_PRICE);
        }
        return transitionTo(PublicationStatus.PUBLISHED, PublicationStatus.DRAFT);
    }

    /** PUBLISHED -> ARCHIVED. Retira el plan del catálogo sin borrarlo. */
    public Plan archive() {
        return transitionTo(PublicationStatus.ARCHIVED, PublicationStatus.PUBLISHED);
    }

    /** ARCHIVED -> PUBLISHED. Ya tuvo precio al publicarse y los precios no se pueden quitar. */
    public Plan restore() {
        return transitionTo(PublicationStatus.PUBLISHED, PublicationStatus.ARCHIVED);
    }

    public boolean isDeletable() {
        return status.isDeletable();
    }

    /** Mismo criterio que Agent: estado de origen esperado + tabla de PublicationStatus. */
    private Plan transitionTo(PublicationStatus target, PublicationStatus expectedSource) {
        if (status != expectedSource || !status.canTransitionTo(target)) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        return toBuilder()
                .status(target)
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
        return status == PublicationStatus.PUBLISHED;
    }
}
