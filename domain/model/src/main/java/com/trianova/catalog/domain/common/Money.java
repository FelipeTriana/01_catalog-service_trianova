package com.trianova.catalog.domain.common;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Objeto de valor de dinero. Siempre {@link BigDecimal} + moneda ISO-4217;
 * nunca double. Inmutable (record). No admite montos negativos.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    /** Crea un Money validando el código contra ISO-4217 (p. ej. "COP", "USD"). */
    public static Money of(BigDecimal amount, String currencyCode) {
        if (currencyCode == null) {
            throw new IllegalArgumentException("currencyCode must not be null");
        }
        // Currency.getInstance lanza IllegalArgumentException si el código no es ISO-4217.
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /**
     * Monto como texto plano con los decimales propios de la moneda (COP/USD: 2),
     * sin redondear jamás: si el valor trae más decimales significativos, se conservan.
     * Es la representación canónica que comparten la API HTTP y los eventos.
     */
    public String toPlainAmount() {
        int currencyDigits = Math.max(currency.getDefaultFractionDigits(), 0);
        int significantDigits = amount.stripTrailingZeros().scale();
        return amount.setScale(Math.max(currencyDigits, significantDigits)).toPlainString();
    }
}
