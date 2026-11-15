package com.trianova.catalog.web.plan.dto;

import java.util.List;

/** Respuesta de GET /api/v1/agents/{agentId}/plans: los planes publicados de un agente. */
public record AgentPlansResponse(String agentId, List<PlanResponse> plans) {
}
