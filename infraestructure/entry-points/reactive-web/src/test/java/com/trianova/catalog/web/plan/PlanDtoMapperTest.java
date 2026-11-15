package com.trianova.catalog.web.plan;

import com.trianova.catalog.domain.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Formato de montos: decimales de la moneda, nunca redondeo. Sin Spring. */
class PlanDtoMapperTest {

    private static String format(String amount, String currency) {
        return PlanDtoMapper.formatAmount(Money.of(new BigDecimal(amount), currency));
    }

    @Test
    void padsToCurrencyFractionDigits() {
        assertEquals("59900.00", format("59900.0000", "COP"));
        assertEquals("29.00", format("29", "USD"));
    }

    @Test
    void keepsExtraSignificantDecimalsInsteadOfRounding() {
        // 0.0050 USD no se redondea a "0.01": conserva sus decimales significativos.
        assertEquals("0.005", format("0.0050", "USD"));
        assertEquals("19.995", format("19.9950", "USD"));
    }

    @Test
    void currencyWithoutDecimalsHasNoFraction() {
        assertEquals("1500", format("1500.0000", "JPY"));
    }

    @Test
    void largeAmountsAreNotPrintedInScientificNotation() {
        assertEquals("100000000.00", format("1E+8", "COP"));
    }
}
