package com.trianova.catalog.usecase.plan;

import com.trianova.catalog.domain.plan.Plan;

import java.util.List;

/**
 * Planes publicados de un agente publicado. Lleva el agentId resuelto porque la
 * consulta puede llegar por slug y la lista puede venir vacía.
 */
public record PublishedPlans(String agentId, List<Plan> plans) {

    public PublishedPlans {
        plans = List.copyOf(plans);
    }
}
