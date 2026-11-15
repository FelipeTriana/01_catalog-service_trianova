package com.trianova.catalog.web.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Feature;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;
import com.trianova.catalog.usecase.plan.PlanUseCase;
import com.trianova.catalog.usecase.plan.PublishedPlans;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contrato HTTP de los endpoints de planes. El caso de uso está simulado. */
@WebFluxTest(controllers = PlanController.class)
class PlanControllerTest {

    @Autowired
    WebTestClient client;

    @MockBean
    PlanUseCase planUseCase;

    /** Montos con 4 decimales, como llegan desde NUMERIC(19,4). */
    private static Plan proPlan(PublicationStatus status) {
        return Plan.builder()
                .id("p1").agentId("a1").name("Pro").status(status)
                .price(new Price(Money.of(new BigDecimal("59900.0000"), "COP"), BillingCycle.MONTHLY))
                .price(new Price(Money.of(new BigDecimal("599000.0000"), "COP"), BillingCycle.YEARLY))
                .feature(new Feature("MAX_RUNS", "Ejecuciones/mes", null, "10000"))
                .createdAt(Instant.parse("2026-10-30T15:00:00Z"))
                .build();
    }

    @Test
    void listsPublishedPlansBySlugWithResolvedAgentId() {
        when(planUseCase.findPublishedPlans("asistente-ventas"))
                .thenReturn(Mono.just(new PublishedPlans("a1", List.of(proPlan(PublicationStatus.PUBLISHED)))));

        client.get().uri("/api/v1/agents/asistente-ventas/plans").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo("a1")
                .jsonPath("$.plans.length()").isEqualTo(1)
                .jsonPath("$.plans[0].status").isEqualTo("PUBLISHED")
                .jsonPath("$.plans[0].prices[0].billingCycle").isEqualTo("MONTHLY")
                .jsonPath("$.plans[0].prices[0].amount").isEqualTo("59900.00")
                .jsonPath("$.plans[0].prices[0].currency").isEqualTo("COP")
                .jsonPath("$.plans[0].features[0].code").isEqualTo("MAX_RUNS")
                .jsonPath("$.plans[0].features[0].limitValue").isEqualTo("10000")
                .jsonPath("$.plans[0].features[0].description").doesNotExist();
    }

    @Test
    void agentWithoutPublishedPlansReturnsEmptyList() {
        when(planUseCase.findPublishedPlans("a1")).thenReturn(Mono.just(new PublishedPlans("a1", List.of())));

        client.get().uri("/api/v1/agents/a1/plans").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo("a1")
                .jsonPath("$.plans.length()").isEqualTo(0);
    }

    @Test
    void plansOfUnpublishedOrUnknownAgentReturn404() {
        when(planUseCase.findPublishedPlans("draft-agent"))
                .thenReturn(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.build()));

        client.get().uri("/api/v1/agents/draft-agent/plans").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("AGENT_NOT_FOUND");
    }

    @Test
    void createPlanTakesAgentIdFromPathAndMapsPricesAndFeatures() {
        when(planUseCase.createPlan(any())).thenReturn(Mono.just(proPlan(PublicationStatus.DRAFT)));

        client.post().uri("/api/v1/admin/agents/a1/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Pro","description":"Plan profesional",
                         "prices":[{"billingCycle":"MONTHLY","amount":"59900.00","currency":"COP"}],
                         "features":[{"code":"MAX_RUNS","name":"Ejecuciones/mes","limitValue":"10000"}]}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("DRAFT");

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planUseCase).createPlan(captor.capture());
        Plan sent = captor.getValue();
        assertEquals("a1", sent.getAgentId());
        assertEquals("Pro", sent.getName());
        assertEquals(1, sent.getPrices().size());
        assertEquals(0, new BigDecimal("59900.00").compareTo(sent.getPrices().get(0).amount().amount()));
        assertEquals("COP", sent.getPrices().get(0).amount().currency().getCurrencyCode());
        assertEquals(1, sent.getFeatures().size());
    }

    @Test
    void createPlanWithoutPricesIsAllowedForDraft() {
        when(planUseCase.createPlan(any())).thenReturn(Mono.just(proPlan(PublicationStatus.DRAFT)));

        client.post().uri("/api/v1/admin/agents/a1/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Starter\"}")
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planUseCase).createPlan(captor.capture());
        assertEquals(0, captor.getValue().getPrices().size());
    }

    @Test
    void createPlanForUnknownAgentReturns404() {
        when(planUseCase.createPlan(any())).thenReturn(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.build()));

        client.post().uri("/api/v1/admin/agents/ghost/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Pro\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("AGENT_NOT_FOUND");
    }

    @Test
    void createPlanWithInvalidNestedPriceReturns400() {
        client.post().uri("/api/v1/admin/agents/a1/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Pro\",\"prices\":[{\"billingCycle\":\"MONTHLY\",\"currency\":\"COP\"}]}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        verify(planUseCase, never()).createPlan(any());
    }

    @Test
    void updatePlanPassesPathIdAndBody() {
        when(planUseCase.updatePlan(eq("p1"), any())).thenReturn(Mono.just(proPlan(PublicationStatus.DRAFT)));

        client.put().uri("/api/v1/admin/plans/p1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Pro Plus","description":"Nueva",
                         "features":[{"code":"MAX_RUNS","name":"Ejecuciones/mes","limitValue":"20000"}]}
                        """)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planUseCase).updatePlan(eq("p1"), captor.capture());
        assertEquals("Pro Plus", captor.getValue().getName());
        assertEquals("Nueva", captor.getValue().getDescription());
        assertEquals(1, captor.getValue().getFeatures().size());
        assertEquals("20000", captor.getValue().getFeatures().get(0).limitValue());
    }

    @Test
    void updatePlanWithInvalidFeatureReturns400() {
        client.put().uri("/api/v1/admin/plans/p1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Pro\",\"features\":[{\"name\":\"Sin código\"}]}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        verify(planUseCase, never()).updatePlan(anyString(), any());
    }

    @Test
    void changePriceAcceptsNumericAmountAndCallsUseCase() {
        when(planUseCase.changePrice(eq("p1"), any())).thenReturn(Mono.just(proPlan(PublicationStatus.PUBLISHED)));

        client.put().uri("/api/v1/admin/plans/p1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"billingCycle\":\"YEARLY\",\"amount\":650000.50,\"currency\":\"COP\"}")
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<Price> captor = ArgumentCaptor.forClass(Price.class);
        verify(planUseCase).changePrice(eq("p1"), captor.capture());
        assertEquals(BillingCycle.YEARLY, captor.getValue().billingCycle());
        assertEquals(0, new BigDecimal("650000.50").compareTo(captor.getValue().amount().amount()));
    }

    @Test
    void changePriceWithZeroAmountReturns422AndNeverCallsUseCase() {
        client.put().uri("/api/v1/admin/plans/p1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"billingCycle\":\"MONTHLY\",\"amount\":\"0\",\"currency\":\"COP\"}")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PRICE");

        verify(planUseCase, never()).changePrice(anyString(), any());
    }

    @Test
    void changePriceWithNegativeAmountReturns422() {
        client.put().uri("/api/v1/admin/plans/p1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"billingCycle\":\"MONTHLY\",\"amount\":\"-10\",\"currency\":\"COP\"}")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PRICE");

        verify(planUseCase, never()).changePrice(anyString(), any());
    }

    @Test
    void changePriceWithUnsupportedCurrencyReturns422() {
        client.put().uri("/api/v1/admin/plans/p1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"billingCycle\":\"MONTHLY\",\"amount\":\"100\",\"currency\":\"ABC\"}")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PRICE");

        verify(planUseCase, never()).changePrice(anyString(), any());
    }

    @Test
    void changePriceWithUnknownBillingCycleReturns400() {
        client.put().uri("/api/v1/admin/plans/p1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"billingCycle\":\"WEEKLY\",\"amount\":\"100\",\"currency\":\"COP\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        verify(planUseCase, never()).changePrice(anyString(), any());
    }

    @Test
    void publishPlanWithoutValidPriceReturns409() {
        when(planUseCase.publishPlan("p1"))
                .thenReturn(Mono.error(BusinessException.Type.PLAN_HAS_NO_VALID_PRICE.build()));

        client.post().uri("/api/v1/admin/plans/p1/publish").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("PLAN_HAS_NO_VALID_PRICE");
    }

    @Test
    void publishUnknownPlanReturns404() {
        when(planUseCase.publishPlan("ghost")).thenReturn(Mono.error(BusinessException.Type.PLAN_NOT_FOUND.build()));

        client.post().uri("/api/v1/admin/plans/ghost/publish").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("PLAN_NOT_FOUND");
    }

    @Test
    void archivePlanReturnsArchived() {
        when(planUseCase.archivePlan("p1")).thenReturn(Mono.just(proPlan(PublicationStatus.ARCHIVED)));

        client.post().uri("/api/v1/admin/plans/p1/archive").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void archiveLastPublishedPlanReturns409() {
        when(planUseCase.archivePlan("p1"))
                .thenReturn(Mono.error(BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN.build()));

        client.post().uri("/api/v1/admin/plans/p1/archive").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("AGENT_HAS_NO_PUBLISHED_PLAN");
    }

    @Test
    void restorePlanReturnsPublished() {
        when(planUseCase.restorePlan("p1")).thenReturn(Mono.just(proPlan(PublicationStatus.PUBLISHED)));

        client.post().uri("/api/v1/admin/plans/p1/restore").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    @Test
    void deleteDraftPlanReturns204WithoutBody() {
        when(planUseCase.deletePlan("p1")).thenReturn(Mono.empty());

        client.delete().uri("/api/v1/admin/plans/p1").exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(planUseCase).deletePlan("p1");
    }

    @Test
    void deleteNonDraftPlanReturns409() {
        when(planUseCase.deletePlan("p1"))
                .thenReturn(Mono.error(BusinessException.Type.CANNOT_DELETE_NON_DRAFT.build()));

        client.delete().uri("/api/v1/admin/plans/p1").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("CANNOT_DELETE_NON_DRAFT");
    }
}
