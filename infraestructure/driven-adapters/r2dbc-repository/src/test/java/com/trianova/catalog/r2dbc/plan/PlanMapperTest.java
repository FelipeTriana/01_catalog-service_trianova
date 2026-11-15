package com.trianova.catalog.r2dbc.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Feature;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mapeo dominio &lt;-&gt; datos del agregado Plan. No necesita base ni Docker. */
class PlanMapperTest {

    @Test
    void rootMapsStatusAsTextAndReassembles() {
        Plan plan = Plan.builder()
                .id("p1").agentId("ag1").name("Pro").description("desc")
                .status(PublicationStatus.PUBLISHED)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        PlanData data = PlanRepositoryAdapter.dataFrom(plan);
        assertEquals("PUBLISHED", data.getStatus());
        assertEquals("ag1", data.getAgentId());

        Plan back = PlanRepositoryAdapter.domainFrom(data, List.of(), List.of());
        assertEquals("p1", back.getId());
        assertEquals(PublicationStatus.PUBLISHED, back.getStatus());
        assertTrue(back.getPrices().isEmpty());
        assertTrue(back.getFeatures().isEmpty());
    }

    @Test
    void priceRoundTripGeneratesIdAndPreservesValue() {
        Price price = new Price(Money.of(new BigDecimal("29.90"), "USD"), BillingCycle.MONTHLY);

        PriceData row = PlanRepositoryAdapter.priceRow("p1", price);
        assertNotNull(row.getId());
        assertEquals("p1", row.getPlanId());
        assertEquals("MONTHLY", row.getBillingCycle());
        assertEquals("USD", row.getCurrency());

        Price back = PlanRepositoryAdapter.priceFrom(row);
        assertEquals(0, back.amount().amount().compareTo(new BigDecimal("29.90")));
        assertEquals(BillingCycle.MONTHLY, back.billingCycle());
    }

    @Test
    void featureRoundTripPreservesAllFields() {
        Feature feature = new Feature("exec", "Ejecuciones", "límite mensual", "10000");

        PlanFeatureData row = PlanRepositoryAdapter.featureRow("p1", feature);
        assertNotNull(row.getId());
        assertEquals("p1", row.getPlanId());
        assertEquals("exec", row.getCode());

        Feature back = PlanRepositoryAdapter.featureFrom(row);
        assertEquals(feature, back);
    }
}
