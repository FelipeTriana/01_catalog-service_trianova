package com.trianova.catalog.usecase.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.AgentStatus;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.PlanStatus;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentUseCaseTest {

    @Mock
    AgentGateway agentGateway;
    @Mock
    PlanGateway planGateway;

    AgentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AgentUseCase(agentGateway, planGateway);
    }

    private Agent draftAgent() {
        return Agent.builder()
                .id("a1")
                .slug("asistente-ventas")
                .name("Asistente de Ventas")
                .status(AgentStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
    }

    private Plan publishedPlan() {
        return Plan.builder().id("p1").agentId("a1").name("Pro").status(PlanStatus.PUBLISHED).build();
    }

    @Test
    void findAgentByIdReturnsIt() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(draftAgent()));

        StepVerifier.create(useCase.findAgent("a1"))
                .expectNextMatches(a -> a.getId().equals("a1"))
                .verifyComplete();
    }

    @Test
    void findAgentFallsBackToSlug() {
        when(agentGateway.findById("asistente-ventas")).thenReturn(Mono.empty());
        when(agentGateway.findBySlug("asistente-ventas")).thenReturn(Mono.just(draftAgent()));

        StepVerifier.create(useCase.findAgent("asistente-ventas"))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void findAgentNotFoundFails() {
        when(agentGateway.findById("x")).thenReturn(Mono.empty());
        when(agentGateway.findBySlug("x")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findAgent("x"))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.AGENT_NOT_FOUND)
                .verify();
    }

    @Test
    void createAgentSavesAsDraftWhenSlugIsFree() {
        when(agentGateway.findBySlug("asistente-ventas")).thenReturn(Mono.empty());
        when(agentGateway.save(any(Agent.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.createAgent(draftAgent()))
                .expectNextMatches(a -> a.getStatus() == AgentStatus.DRAFT && a.getId() != null)
                .verifyComplete();
    }

    @Test
    void createAgentFailsWhenSlugTaken() {
        when(agentGateway.findBySlug("asistente-ventas")).thenReturn(Mono.just(draftAgent()));

        StepVerifier.create(useCase.createAgent(draftAgent()))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.SLUG_ALREADY_EXISTS)
                .verify();
    }

    @Test
    void publishAgentSucceedsWhenItHasPublishedPlan() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(draftAgent()));
        when(planGateway.findPublishedByAgentId("a1")).thenReturn(Flux.just(publishedPlan()));
        when(agentGateway.save(any(Agent.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.publishAgent("a1"))
                .expectNextMatches(a -> a.getStatus() == AgentStatus.PUBLISHED)
                .verifyComplete();
    }

    @Test
    void publishAgentFailsWithoutPublishedPlan() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(draftAgent()));
        when(planGateway.findPublishedByAgentId("a1")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.publishAgent("a1"))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN)
                .verify();
    }
}
