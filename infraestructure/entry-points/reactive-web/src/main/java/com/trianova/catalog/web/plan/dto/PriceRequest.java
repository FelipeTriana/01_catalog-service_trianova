package com.trianova.catalog.web.plan.dto;

import com.trianova.catalog.domain.plan.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Precio recibido por HTTP. Acepta el monto como número o como texto ("59900.00");
 * Jackson lo convierte a BigDecimal sin pasar por double.
 * Que el monto sea positivo y la moneda ISO-4217 lo valida el dominio (INVALID_PRICE).
 */
public record PriceRequest(
        @NotNull BillingCycle billingCycle,
        @NotNull BigDecimal amount,
        @NotBlank String currency) {
}
