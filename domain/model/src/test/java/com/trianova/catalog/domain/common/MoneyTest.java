package com.trianova.catalog.domain.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void createsValidMoneyWithIsoCurrency() {
        Money money = Money.of(new BigDecimal("59900.00"), "COP");

        assertEquals(new BigDecimal("59900.00"), money.amount());
        assertEquals("COP", money.currency().getCurrencyCode());
        assertTrue(money.isPositive());
    }

    @Test
    void zeroIsAllowedButNotPositive() {
        Money money = Money.of(BigDecimal.ZERO, "USD");

        assertFalse(money.isPositive());
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("-1"), "COP"));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(null, "COP"));
    }

    @Test
    void rejectsUnknownCurrencyCode() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.TEN, "XYZ"));
    }

    @Test
    void plainAmountUsesCurrencyDigitsWithoutRounding() {
        assertEquals("59900.00", Money.of(new BigDecimal("59900.0000"), "COP").toPlainAmount());
        assertEquals("19.995", Money.of(new BigDecimal("19.9950"), "USD").toPlainAmount());
        assertEquals("100000000.00", Money.of(new BigDecimal("1E+8"), "COP").toPlainAmount());
    }
}
