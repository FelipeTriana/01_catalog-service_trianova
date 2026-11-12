package com.trianova.catalog.domain.agent;

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
    private final AgentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** DRAFT -> PUBLISHED. */
    public Agent publish() {
        if (status != AgentStatus.DRAFT) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        return toBuilder()
                .status(AgentStatus.PUBLISHED)
                .updatedAt(Instant.now())
                .build();
    }

    /** Cualquier estado -> ARCHIVED, salvo que ya esté archivado. */
    public Agent archive() {
        if (status == AgentStatus.ARCHIVED) {
            throw new BusinessException(BusinessException.Type.INVALID_STATE_TRANSITION);
        }
        return toBuilder()
                .status(AgentStatus.ARCHIVED)
                .updatedAt(Instant.now())
                .build();
    }

    public boolean isPublished() {
        return status == AgentStatus.PUBLISHED;
    }
}
