package com.trianova.catalog.domain.common.ex;

/**
 * Raíz de las excepciones de la aplicación. Lleva un código opcional para que
 * los adaptadores (p. ej. REST) lo traduzcan a un formato de error estable.
 */
public class ApplicationException extends RuntimeException {

    private final String code;

    public ApplicationException(String message) {
        this(message, null);
    }

    public ApplicationException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
