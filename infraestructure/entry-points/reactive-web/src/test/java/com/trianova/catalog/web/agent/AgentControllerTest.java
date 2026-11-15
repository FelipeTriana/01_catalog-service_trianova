package com.trianova.catalog.web.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import com.trianova.catalog.usecase.agent.AgentUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contrato HTTP de los endpoints de agentes. El caso de uso está simulado. */
@WebFluxTest(controllers = AgentController.class)
class AgentControllerTest {

    @Autowired
    WebTestClient client;

    @MockBean
    AgentUseCase agentUseCase;

    private static Agent agent(String id, PublicationStatus status) {
        return Agent.builder()
                .id(id).slug("asistente-ventas").name("Asistente de Ventas")
                .description("Responde leads").category("SALES")
                .status(status)
                .createdAt(Instant.parse("2026-10-30T15:00:00Z"))
                .build();
    }

    @Test
    void listsPublishedAgents() {
        when(agentUseCase.findPublishedAgents()).thenReturn(Flux.just(agent("a1", PublicationStatus.PUBLISHED)));

        client.get().uri("/api/v1/agents").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].slug").isEqualTo("asistente-ventas")
                .jsonPath("$[0].status").isEqualTo("PUBLISHED")
                .jsonPath("$[0].createdAt").isEqualTo("2026-10-30T15:00:00Z")
                .jsonPath("$[0].updatedAt").doesNotExist();
    }

    @Test
    void publicDetailUsesPublishedOnlyLookup() {
        when(agentUseCase.findPublishedAgent("asistente-ventas"))
                .thenReturn(Mono.just(agent("a1", PublicationStatus.PUBLISHED)));

        client.get().uri("/api/v1/agents/asistente-ventas").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo("a1");

        verify(agentUseCase).findPublishedAgent("asistente-ventas");
    }

    @Test
    void findAgentNotFoundReturns404ProblemDetail() {
        when(agentUseCase.findPublishedAgent("nope")).thenReturn(Mono.error(BusinessException.Type.AGENT_NOT_FOUND.build()));

        client.get().uri("/api/v1/agents/nope").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.code").isEqualTo("AGENT_NOT_FOUND")
                .jsonPath("$.detail").isEqualTo(BusinessException.Type.AGENT_NOT_FOUND.getMessage());
    }

    @Test
    void adminListsAllAgentsIncludingDrafts() {
        when(agentUseCase.findAllAgents()).thenReturn(Flux.just(
                agent("a1", PublicationStatus.PUBLISHED), agent("a2", PublicationStatus.DRAFT)));

        client.get().uri("/api/v1/admin/agents").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[1].status").isEqualTo("DRAFT");
    }

    @Test
    void createAgentReturns201AndPassesBodyToUseCase() {
        when(agentUseCase.createAgent(any())).thenReturn(Mono.just(agent("a1", PublicationStatus.DRAFT)));

        client.post().uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"slug":"asistente-ventas","name":"Asistente de Ventas",
                         "description":"Responde leads","category":"SALES"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("a1")
                .jsonPath("$.status").isEqualTo("DRAFT");

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentUseCase).createAgent(captor.capture());
        assertEquals("asistente-ventas", captor.getValue().getSlug());
        assertEquals("Asistente de Ventas", captor.getValue().getName());
        assertEquals("SALES", captor.getValue().getCategory());
    }

    @Test
    void createAgentWithDuplicateSlugReturns409() {
        when(agentUseCase.createAgent(any())).thenReturn(Mono.error(BusinessException.Type.SLUG_ALREADY_EXISTS.build()));

        client.post().uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"slug\":\"dup\",\"name\":\"Dup\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("SLUG_ALREADY_EXISTS");
    }

    @Test
    void createAgentWithBlankSlugReturns400WithFieldErrorsAndNeverCallsUseCase() {
        client.post().uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"slug\":\"\",\"name\":\"Sin slug\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors.slug").exists();

        verify(agentUseCase, never()).createAgent(any());
    }

    @Test
    void malformedJsonReturns400ProblemDetail() {
        client.post().uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        verify(agentUseCase, never()).createAgent(any());
    }

    @Test
    void updateAgentPassesPathIdAndBody() {
        when(agentUseCase.updateAgent(eq("a1"), any())).thenReturn(Mono.just(agent("a1", PublicationStatus.DRAFT)));

        client.put().uri("/api/v1/admin/agents/a1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Nuevo nombre\",\"category\":\"SUPPORT\"}")
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentUseCase).updateAgent(eq("a1"), captor.capture());
        assertEquals("Nuevo nombre", captor.getValue().getName());
        assertEquals("SUPPORT", captor.getValue().getCategory());
    }

    @Test
    void publishAgentWithoutPublishedPlanReturns409() {
        when(agentUseCase.publishAgent("a1"))
                .thenReturn(Mono.error(BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN.build()));

        client.post().uri("/api/v1/admin/agents/a1/publish").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("AGENT_HAS_NO_PUBLISHED_PLAN");
    }

    @Test
    void archiveAgentReturnsArchived() {
        when(agentUseCase.archiveAgent("a1")).thenReturn(Mono.just(agent("a1", PublicationStatus.ARCHIVED)));

        client.post().uri("/api/v1/admin/agents/a1/archive").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void invalidStateTransitionReturns409() {
        when(agentUseCase.archiveAgent("a1"))
                .thenReturn(Mono.error(BusinessException.Type.INVALID_STATE_TRANSITION.build()));

        client.post().uri("/api/v1/admin/agents/a1/archive").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingInternalMessage() {
        when(agentUseCase.findPublishedAgents()).thenReturn(Flux.error(new RuntimeException("db password=secret")));

        client.get().uri("/api/v1/agents").exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.detail").isEqualTo("An unexpected error occurred");
    }

    @Test
    void restoreAgentReturnsPublished() {
        when(agentUseCase.restoreAgent("a1")).thenReturn(Mono.just(agent("a1", PublicationStatus.PUBLISHED)));

        client.post().uri("/api/v1/admin/agents/a1/restore").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    @Test
    void restoreAgentWithoutPublishedPlanReturns409() {
        when(agentUseCase.restoreAgent("a1"))
                .thenReturn(Mono.error(BusinessException.Type.AGENT_HAS_NO_PUBLISHED_PLAN.build()));

        client.post().uri("/api/v1/admin/agents/a1/restore").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("AGENT_HAS_NO_PUBLISHED_PLAN");
    }

    @Test
    void deleteDraftAgentReturns204WithoutBody() {
        when(agentUseCase.deleteAgent("a1")).thenReturn(Mono.empty());

        client.delete().uri("/api/v1/admin/agents/a1").exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(agentUseCase).deleteAgent("a1");
    }

    @Test
    void deleteNonDraftAgentReturns409() {
        when(agentUseCase.deleteAgent("a1"))
                .thenReturn(Mono.error(BusinessException.Type.CANNOT_DELETE_NON_DRAFT.build()));

        client.delete().uri("/api/v1/admin/agents/a1").exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("CANNOT_DELETE_NON_DRAFT");
    }
}
