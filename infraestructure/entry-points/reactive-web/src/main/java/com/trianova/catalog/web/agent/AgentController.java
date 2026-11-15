package com.trianova.catalog.web.agent;

import com.trianova.catalog.usecase.agent.AgentUseCase;
import com.trianova.catalog.web.agent.dto.AgentResponse;
import com.trianova.catalog.web.agent.dto.CreateAgentRequest;
import com.trianova.catalog.web.agent.dto.UpdateAgentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Endpoints de agentes. El controller solo traduce HTTP <-> caso de uso: las
 * reglas de negocio viven en el dominio y en AgentUseCase, nunca aquí.
 * La autorización por rol (/admin/**) la aplica el adaptador de seguridad.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AgentController {

    private final AgentUseCase agentUseCase;

    // ---- Públicos ----

    @GetMapping("/agents")
    public Flux<AgentResponse> findPublishedAgents() {
        return agentUseCase.findPublishedAgents().map(AgentDtoMapper::toResponse);
    }

    @GetMapping("/agents/{idOrSlug}")
    public Mono<AgentResponse> findAgent(@PathVariable String idOrSlug) {
        return agentUseCase.findPublishedAgent(idOrSlug).map(AgentDtoMapper::toResponse);
    }

    // ---- Administración ----

    @GetMapping("/admin/agents")
    public Flux<AgentResponse> findAllAgents() {
        return agentUseCase.findAllAgents().map(AgentDtoMapper::toResponse);
    }

    @PostMapping("/admin/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return agentUseCase.createAgent(AgentDtoMapper.toDomain(request)).map(AgentDtoMapper::toResponse);
    }

    @PutMapping("/admin/agents/{id}")
    public Mono<AgentResponse> updateAgent(@PathVariable String id, @Valid @RequestBody UpdateAgentRequest request) {
        return agentUseCase.updateAgent(id, AgentDtoMapper.toDomain(request)).map(AgentDtoMapper::toResponse);
    }

    @PostMapping("/admin/agents/{id}/publish")
    public Mono<AgentResponse> publishAgent(@PathVariable String id) {
        return agentUseCase.publishAgent(id).map(AgentDtoMapper::toResponse);
    }

    @PostMapping("/admin/agents/{id}/archive")
    public Mono<AgentResponse> archiveAgent(@PathVariable String id) {
        return agentUseCase.archiveAgent(id).map(AgentDtoMapper::toResponse);
    }

    @PostMapping("/admin/agents/{id}/restore")
    public Mono<AgentResponse> restoreAgent(@PathVariable String id) {
        return agentUseCase.restoreAgent(id).map(AgentDtoMapper::toResponse);
    }

    /** Solo DRAFT; borra también sus planes. 204 sin cuerpo. */
    @DeleteMapping("/admin/agents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAgent(@PathVariable String id) {
        return agentUseCase.deleteAgent(id);
    }
}
