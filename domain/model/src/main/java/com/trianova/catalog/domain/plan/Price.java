package com.trianova.catalog.domain.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.ex.BusinessException;

/**
 * Precio de un plan para un ciclo de facturación. Objeto de valor inmutable.
 * El monto debe ser estrictamente positivo: {@link Money} ya impide negativos,
 * y aquí además rechazamos cero (regla INVALID_PRICE).
 */
public record Price(Money amount, BillingCycle billingCycle) {

    public Price {
        if (amount == null || !amount.isPositive()) {
            throw new BusinessException(BusinessException.Type.INVALID_PRICE);
        }
        if (billingCycle == null) {
            throw new IllegalArgumentException("billingCycle must not be null");
        }
    }
}
