package com.trianova.catalog.domain.common;

/**
 * Ciclo de vida compartido por Agent y Plan. La tabla de transiciones vive aquí,
 * en un solo lugar:
 *
 * <pre>
 * DRAFT --publish--> PUBLISHED --archive--> ARCHIVED
 *                        ^                      |
 *                        +-------restore--------+
 * </pre>
 *
 * No existe PUBLISHED -> DRAFT: lo publicado ya lo vieron clientes; para
 * retirarlo se archiva. Solo un DRAFT se puede eliminar (ver isDeletable).
 */
public enum PublicationStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    /** Switch exhaustivo sin default: un estado nuevo no compila hasta definir sus transiciones. */
    public boolean canTransitionTo(PublicationStatus target) {
        return switch (this) {
            case DRAFT -> target == PUBLISHED;
            case PUBLISHED -> target == ARCHIVED;
            case ARCHIVED -> target == PUBLISHED;
        };
    }

    /** Solo lo que nunca se publicó se puede borrar: ningún cliente lo vio. */
    public boolean isDeletable() {
        return this == DRAFT;
    }
}
