package com.trianova.catalog.domain.common.ex;

import java.util.function.Supplier;

/**
 * Errores de negocio del Catalog Service. Cada tipo define un mensaje y actúa
 * como código estable (getCode == name()). Los adaptadores lo mapean a HTTP
 * (RFC 7807) sin que el dominio conozca HTTP.
 */
public class BusinessException extends ApplicationException {

    public enum Type {

        AGENT_NOT_FOUND("The requested agent does not exist or is not published"),
        PLAN_NOT_FOUND("The requested plan does not exist"),
        SLUG_ALREADY_EXISTS("An agent with that slug already exists"),
        INVALID_PRICE("The price amount must be positive and use a supported currency"),
        PLAN_HAS_NO_VALID_PRICE("A plan cannot be published without at least one valid price"),
        AGENT_HAS_NO_PUBLISHED_PLAN("An agent cannot be published without at least one published plan"),
        INVALID_STATE_TRANSITION("The requested state transition is not allowed");

        private final String message;

        Type(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public BusinessException build() {
            return new BusinessException(this);
        }

        public Supplier<Throwable> defer() {
            return () -> new BusinessException(this);
        }
    }

    private final transient Type type;

    public BusinessException(Type type) {
        super(type.message);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String getCode() {
        return type.name();
    }
}
