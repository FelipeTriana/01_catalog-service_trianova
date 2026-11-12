package com.trianova.catalog.usecase.plan;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.AgentStatus;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.event.EventPublisher;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.PlanStatus;
import com.trianova.catalog.domain.plan.Price;
import com.trianova.catalog.domain.plan.event.PriceUpdated;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanUseCaseTest {

    @Mock
    PlanGateway planGateway;
    @Mock
    AgentGateway agentGateway;
    @Mock
    EventPublisher eventPublisher;

    PlanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlanUseCase(planGateway, agentGateway, eventPublisher);
    }

    private Price monthly(String amount) {
        return new Price(Money.of(new BigDecimal(amount), "COP"), BillingCycle.MONTHLY);
    }

    private Plan draftPlanWithPrice() {
        return Plan.builder()
                .id("p1")
                .agentId("a1")
                .name("Pro")
                .status(PlanStatus.DRAFT)
                .price(monthly("59900"))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createPlanSavesAsDraftWhenAgentExists() {
        Agent agent = Agent.builder().id("a1").slug("s").name("n").status(AgentStatus.DRAFT).build();
        when(agentGateway.findById("a1")).thenReturn(Mono.just(agent));
        when(planGateway.save(any(Plan.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.createPlan(draftPlanWithPrice()))
                .expectNextMatches(p -> p.getStatus() == PlanStatus.DRAFT && p.getId() != null)
                .verifyComplete();
    }

    @Test
    void createPlanFailsWhenAgentMissing() {
        when(agentGateway.findById("a1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.createPlan(draftPlanWithPrice()))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.AGENT_NOT_FOUND)
                .verify();
    }

    @Test
    void changePricePersistsAndEmitsEvent() {
        when(planGateway.findById("p1")).thenReturn(Mono.just(draftPlanWithPrice()));
        when(planGateway.save(any(Plan.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any(PriceUpdated.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.changePrice("p1", monthly("69900")))
                .expectNextMatches(p -> p.getPrices().get(0).amount().amount().equals(new BigDecimal("69900")))
                .verifyComplete();

        verify(eventPublisher, times(1)).publish(any(PriceUpdated.class));
    }

    @Test
    void changePriceFailsWhenPlanMissing() {
        when(planGateway.findById("p1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.changePrice("p1", monthly("69900")))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.PLAN_NOT_FOUND)
                .verify();

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void publishPlanFailsWithoutPrice() {
        Plan noPrice = Plan.builder()
                .id("p2").agentId("a1").name("Free")
                .status(PlanStatus.DRAFT).createdAt(Instant.now()).build();
        when(planGateway.findById("p2")).thenReturn(Mono.just(noPrice));

        StepVerifier.create(useCase.publishPlan("p2"))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.PLAN_HAS_NO_VALID_PRICE)
                .verify();
    }

    @Test
    void publishPlanSucceedsWithPrice() {
        when(planGateway.findById("p1")).thenReturn(Mono.just(draftPlanWithPrice()));
        when(planGateway.save(any(Plan.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.publishPlan("p1"))
                .expectNextMatches(p -> p.getStatus() == PlanStatus.PUBLISHED)
                .verifyComplete();
    }
}
