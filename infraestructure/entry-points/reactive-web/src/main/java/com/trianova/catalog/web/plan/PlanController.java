package com.trianova.catalog.web.plan;

import com.trianova.catalog.usecase.plan.PlanUseCase;
import com.trianova.catalog.web.plan.dto.AgentPlansResponse;
import com.trianova.catalog.web.plan.dto.CreatePlanRequest;
import com.trianova.catalog.web.plan.dto.PlanResponse;
import com.trianova.catalog.web.plan.dto.PriceRequest;
import com.trianova.catalog.web.plan.dto.UpdatePlanRequest;
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
import reactor.core.publisher.Mono;

/**
 * Endpoints de planes. Igual que AgentController: solo traduce HTTP <-> caso de uso.
 * Los DTOs se convierten a dominio dentro de Mono.fromCallable para que un error de
 * mapeo (p. ej. INVALID_PRICE) viaje por el flujo reactivo como cualquier otro error.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlanController {

    private final PlanUseCase planUseCase;

    // ---- Públicos ----

    /** Acepta id o slug; la respuesta siempre lleva el agentId resuelto. */
    @GetMapping("/agents/{idOrSlug}/plans")
    public Mono<AgentPlansResponse> findPlansByAgent(@PathVariable String idOrSlug) {
        return planUseCase.findPublishedPlans(idOrSlug)
                .map(result -> new AgentPlansResponse(
                        result.agentId(),
                        result.plans().stream().map(PlanDtoMapper::toResponse).toList()));
    }

    // ---- Administración ----

    @PostMapping("/admin/agents/{agentId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PlanResponse> createPlan(@PathVariable String agentId, @Valid @RequestBody CreatePlanRequest request) {
        return Mono.fromCallable(() -> PlanDtoMapper.toDomain(agentId, request))
                .flatMap(planUseCase::createPlan)
                .map(PlanDtoMapper::toResponse);
    }

    @PutMapping("/admin/plans/{id}")
    public Mono<PlanResponse> updatePlan(@PathVariable String id, @Valid @RequestBody UpdatePlanRequest request) {
        return planUseCase.updatePlan(id, PlanDtoMapper.toDomain(request)).map(PlanDtoMapper::toResponse);
    }

    @PutMapping("/admin/plans/{id}/price")
    public Mono<PlanResponse> changePrice(@PathVariable String id, @Valid @RequestBody PriceRequest request) {
        return Mono.fromCallable(() -> PlanDtoMapper.toPrice(request))
                .flatMap(price -> planUseCase.changePrice(id, price))
                .map(PlanDtoMapper::toResponse);
    }

    @PostMapping("/admin/plans/{id}/publish")
    public Mono<PlanResponse> publishPlan(@PathVariable String id) {
        return planUseCase.publishPlan(id).map(PlanDtoMapper::toResponse);
    }

    @PostMapping("/admin/plans/{id}/archive")
    public Mono<PlanResponse> archivePlan(@PathVariable String id) {
        return planUseCase.archivePlan(id).map(PlanDtoMapper::toResponse);
    }

    @PostMapping("/admin/plans/{id}/restore")
    public Mono<PlanResponse> restorePlan(@PathVariable String id) {
        return planUseCase.restorePlan(id).map(PlanDtoMapper::toResponse);
    }

    /** Solo DRAFT; precios y features se borran con el plan. 204 sin cuerpo. */
    @DeleteMapping("/admin/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePlan(@PathVariable String id) {
        return planUseCase.deletePlan(id);
    }
}
