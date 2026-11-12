package com.trianova.catalog.domain.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.ex.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceTest {

    @Test
    void createsValidPrice() {
        Price price = new Price(Money.of(new BigDecimal("49900"), "COP"), BillingCycle.MONTHLY);

        assertEquals(BillingCycle.MONTHLY, price.billingCycle());
    }

    @Test
    void rejectsZeroAmountAsInvalidPrice() {
        Money zero = Money.of(BigDecimal.ZERO, "COP");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> new Price(zero, BillingCycle.MONTHLY));
        assertEquals(BusinessException.Type.INVALID_PRICE, ex.getType());
    }

    @Test
    void rejectsNullAmount() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new Price(null, BillingCycle.YEARLY));
        assertEquals(BusinessException.Type.INVALID_PRICE, ex.getType());
    }
}
