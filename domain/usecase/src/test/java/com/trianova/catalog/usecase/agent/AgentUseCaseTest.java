package com.trianova.catalog.usecase.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.gateway.AgentGateway;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        // Los mocks no ejecutan métodos default: se delega en la implementación real del puerto.
        lenient().when(agentGateway.findByIdOrSlug(anyString())).thenCallRealMethod();
        useCase = new AgentUseCase(agentGateway, planGateway);
    }

    private Agent draftAgent() {
        return Agent.builder()
                .id("a1")
                .slug("asistente-ventas")
                .name("Asistente de Ventas")
                .status(PublicationStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
    }

    private Agent publishedAgent() {
        return draftAgent().toBuilder().status(PublicationStatus.PUBLISHED).build();
    }

    private Agent archivedAgent() {
        return draftAgent().toBuilder().status(PublicationStatus.ARCHIVED).build();
    }

    private static boolean isError(Throwable e, BusinessException.Type type) {
        return e instanceof BusinessException be && be.getType() == type;
    }

    private Plan publishedPlan() {
        return Plan.builder().id("p1").agentId("a1").name("Pro").status(PublicationStatus.PUBLISHED).build();
    }

    @Test
    void findPublishedAgentByIdReturnsIt() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(publishedAgent()));

        StepVerifier.create(useCase.findPublishedAgent("a1"))
                .expectNextMatches(a -> a.getId().equals("a1"))
                .verifyComplete();
    }

    @Test
    void findPublishedAgentReturnsPublishedAgentBySlug() {
        when(agentGateway.findById("asistente-ventas")).thenReturn(Mono.empty());
        when(agentGateway.findBySlug("asistente-ventas"))
                .thenReturn(Mono.just(draftAgent().toBuilder().status(PublicationStatus.PUBLISHED).build()));

        StepVerifier.create(useCase.findPublishedAgent("asistente-ventas"))
                .expectNextMatches(a -> a.getStatus() == PublicationStatus.PUBLISHED)
                .verifyComplete();
    }

    @Test
    void findPublishedAgentHidesDraftAsNotFound() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(draftAgent()));

        StepVerifier.create(useCase.findPublishedAgent("a1"))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.AGENT_NOT_FOUND)
                .verify();
    }

    @Test
    void findPublishedAgentNotFoundFails() {
        when(agentGateway.findById("x")).thenReturn(Mono.empty());
        when(agentGateway.findBySlug("x")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findPublishedAgent("x"))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getType() == BusinessException.Type.AGENT_NOT_FOUND)
                .verify();
    }

    @Test
    void createAgentSavesAsDraftWhenSlugIsFree() {
        when(agentGateway.findBySlug("asistente-ventas")).thenReturn(Mono.empty());
        when(agentGateway.save(any(Agent.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.createAgent(draftAgent()))
                .expectNextMatches(a -> a.getStatus() == PublicationStatus.DRAFT && a.getId() != null)
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
                .expectNextMatches(a -> a.getStatus() == PublicationStatus.PUBLISHED)
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

    @Test
    void publishAgentValidatesStateBeforeLookingAtPlans() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(publishedAgent()));

        StepVerifier.create(useCase.publishAgent("a1"))
                .expectErrorMatches(e -> isError(e, BusinessException.Type.INVALID_STATE_TRANSITION))
                .verify();

        verify(planGateway, never()).findPublishedByAgentId(anyString());
    }

    @Test
    void archiveAgentMovesPublishedToArchivedWithoutTouchingPlans() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(publishedAgent()));
        when(agentGateway.save(any(Agent.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.archiveAgent("a1"))
                .expectNextMatches(a -> a.getStatus() == PublicationStatus.ARCHIVED)
                .verifyComplete();

        verifyNoInteractions(planGateway);
    }

    @Test
    void archiveAgentFailsFromDraft() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(draftAgent()));

        StepVerifier.create(useCase.archiveAgent("a1"))
                .expectErrorMatches(e -> isError(e, BusinessException.Type.INVALID_STATE_TRANSITION))
                .verify();

        verify(agentGateway, never()).save(any());
    }

    @Test
    void restoreAgentPublishesArchivedAgentWithPublishedPlan() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(archivedAgent()));
        when(planGateway.findPublishedByAgentId("a1")).thenReturn(Flux.just(publishedPlan()));
        when(agentGateway.save(any(Agent.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.restoreAgent("a1"))
                .expectNextMatches(a -> a.getStatus() == PublicationStatus.PUBLISHED)
                .verifyComplete();
    }

    @Test
    void restoreAgentFailsWithoutPublishedPlan() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(archivedAgent()));
        when(planGateway.findPublishedByAgentId("a1")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.restoreAgent("a1"))
                .expectErrorMatches(e -> isError(e, BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN))
                .verify();

        verify(agentGateway, never()).save(any());
    }

    @Test
    void restoreAgentFailsWhenNotArchived() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(publishedAgent()));

        StepVerifier.create(useCase.restoreAgent("a1"))
                .expectErrorMatches(e -> isError(e, BusinessException.Type.INVALID_STATE_TRANSITION))
                .verify();
    }

    @Test
    void deleteDraftAgentDeletesItsPlansFirstAndThenTheAgent() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(draftAgent()));
        when(planGateway.deleteByAgentId("a1")).thenReturn(Mono.empty());
        when(agentGateway.deleteById("a1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.deleteAgent("a1")).verifyComplete();

        InOrder order = inOrder(planGateway, agentGateway);
        order.verify(planGateway).deleteByAgentId("a1");
        order.verify(agentGateway).deleteById("a1");
    }

    @Test
    void deleteAgentFailsWhenNotDraft() {
        when(agentGateway.findById("a1")).thenReturn(Mono.just(publishedAgent()));

        StepVerifier.create(useCase.deleteAgent("a1"))
                .expectErrorMatches(e -> isError(e, BusinessException.Type.CANNOT_DELETE_NON_DRAFT))
                .verify();

        verify(planGateway, never()).deleteByAgentId(anyString());
        verify(agentGateway, never()).deleteById(anyString());
    }

    @Test
    void deleteUnknownAgentFailsWithNotFound() {
        when(agentGateway.findById("x")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.deleteAgent("x"))
                .expectErrorMatches(e -> isError(e, BusinessException.Type.AGENT_NOT_FOUND))
                .verify();
    }
}
