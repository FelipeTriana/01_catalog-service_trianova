package com.trianova.catalog.domain.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTest {

    private Price monthly(String amount) {
        return new Price(Money.of(new BigDecimal(amount), "COP"), BillingCycle.MONTHLY);
    }

    private Plan draftPlanWithPrice() {
        return Plan.builder()
                .id("p1")
                .agentId("a1")
                .name("Pro")
                .status(PublicationStatus.DRAFT)
                .price(monthly("59900"))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void publishMovesDraftWithPriceToPublished() {
        Plan published = draftPlanWithPrice().publish();

        assertEquals(PublicationStatus.PUBLISHED, published.getStatus());
    }

    @Test
    void publishFailsWithoutPrice() {
        Plan noPrice = Plan.builder()
                .id("p2")
                .agentId("a1")
                .name("Free")
                .status(PublicationStatus.DRAFT)
                .createdAt(Instant.now())
                .build();

        BusinessException ex = assertThrows(BusinessException.class, noPrice::publish);
        assertEquals(BusinessException.Type.PLAN_HAS_NO_VALID_PRICE, ex.getType());
    }

    @Test
    void publishFailsWhenNotDraft() {
        Plan published = draftPlanWithPrice().publish();

        BusinessException ex = assertThrows(BusinessException.class, published::publish);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void archiveMovesPublishedToArchived() {
        Plan archived = draftPlanWithPrice().publish().archive();

        assertEquals(PublicationStatus.ARCHIVED, archived.getStatus());
    }

    @Test
    void archiveFailsFromDraftBecauseItSkipsPublished() {
        BusinessException ex = assertThrows(BusinessException.class, draftPlanWithPrice()::archive);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void restoreMovesArchivedBackToPublished() {
        Plan restored = draftPlanWithPrice().publish().archive().restore();

        assertEquals(PublicationStatus.PUBLISHED, restored.getStatus());
    }

    @Test
    void restoreFailsWhenNotArchived() {
        Plan published = draftPlanWithPrice().publish();

        BusinessException ex = assertThrows(BusinessException.class, published::restore);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void onlyDraftIsDeletable() {
        assertEquals(true, draftPlanWithPrice().isDeletable());
        assertEquals(false, draftPlanWithPrice().publish().isDeletable());
    }

    @Test
    void changePriceReplacesSameCycleKeepingCount() {
        Plan plan = draftPlanWithPrice();
        assertEquals(1, plan.getPrices().size());

        Plan updated = plan.changePrice(monthly("69900"));

        assertEquals(1, updated.getPrices().size());
        assertEquals(new BigDecimal("69900"), updated.getPrices().get(0).amount().amount());
    }

    @Test
    void changePriceAddsNewCycle() {
        Price yearly = new Price(Money.of(new BigDecimal("599000"), "COP"), BillingCycle.YEARLY);

        Plan updated = draftPlanWithPrice().changePrice(yearly);

        assertEquals(2, updated.getPrices().size());
    }

    @Test
    void priceForReturnsThePriceOfThatCycleOrEmpty() {
        Plan plan = draftPlanWithPrice();

        assertEquals(new BigDecimal("59900"), plan.priceFor(BillingCycle.MONTHLY).orElseThrow().amount().amount());
        assertTrue(plan.priceFor(BillingCycle.YEARLY).isEmpty());
    }
}
