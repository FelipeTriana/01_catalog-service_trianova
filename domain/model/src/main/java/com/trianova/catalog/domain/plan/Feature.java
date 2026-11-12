package com.trianova.catalog.domain.plan;

/**
 * Feature incluida en un plan. Objeto de valor inmutable.
 * {@code limitValue} es opcional (p. ej. "10000" ejecuciones/mes, o "web,whatsapp").
 */
public record Feature(String code, String name, String description, String limitValue) {

    public Feature {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("feature code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("feature name must not be blank");
        }
    }
}
