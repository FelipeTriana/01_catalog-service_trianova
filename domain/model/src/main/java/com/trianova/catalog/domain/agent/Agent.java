package com.trianova.catalog.domain.agent;

import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;

/**
 * Raíz de agregado: el agente vendible. Inmutable; las transiciones de estado
 * devuelven una nueva instancia. Las reglas que cruzan agregados (p. ej. "un
 * agente no se publica sin un plan publicado") viven en el caso de uso, no aquí.
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public class Agent {

    private final String id;
    private final String slug;
    private final String name;
    private final String description;
    private final String category;
    private final PublicationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** DRAFT -> PUBLISHED. */
    public Agent publish() {
        return transitionTo(PublicationStatus.PUBLISHED, PublicationStatus.DRAFT);
    }

    /** PUBLISHED -> ARCHIVED. Un borrador no se archiva: se elimina. */
    public Agent archive() {
        return transitionTo(PublicationStatus.ARCHIVED, PublicationStatus.PUBLISHED);
    }

    /** ARCHIVED -> PUBLISHED. Recupera el agente y conserva su slug. */
    public Agent restore() {
        return transitionTo(PublicationStatus.PUBLISHED, PublicationStatus.ARCHIVED);
    }

    public boolean isPublished() {
        return status == PublicationStatus.PUBLISHED;
    }

    public boolean isDeletable() {
        return status.isDeletable();
    }

    /**
     * Valida el estado de origen esperado (publish y restore llegan ambos a
     * PUBLISHED, pero desde estados distintos) y la tabla de PublicationStatus.
     */
    private Agent transitionTo(PublicationStatus target, PublicationStatus expectedSource) {
        if (status != expectedSource || !status.canTransitionTo(target)) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        return toBuilder()
                .status(target)
                .updatedAt(Instant.now())
                .build();
    }
}
