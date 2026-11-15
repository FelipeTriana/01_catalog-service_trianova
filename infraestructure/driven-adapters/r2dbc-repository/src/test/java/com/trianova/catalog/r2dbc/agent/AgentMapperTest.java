package com.trianova.catalog.r2dbc.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.common.PublicationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Prueba de mapeo dominio &lt;-&gt; datos. No necesita base de datos ni Docker. */
class AgentMapperTest {

    @Test
    void roundTripPreservesFieldsAndMapsStatusAsText() {
        Agent agent = Agent.builder()
                .id("a1").slug("asistente-ventas").name("Asistente de Ventas")
                .description("desc").category("SALES").status(PublicationStatus.PUBLISHED)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        AgentData data = AgentRepositoryAdapter.dataFrom(agent);
        assertEquals("PUBLISHED", data.getStatus());

        Agent back = AgentRepositoryAdapter.domainFrom(data);
        assertEquals(agent.getId(), back.getId());
        assertEquals(agent.getSlug(), back.getSlug());
        assertEquals(PublicationStatus.PUBLISHED, back.getStatus());
        assertEquals(agent.getCreatedAt(), back.getCreatedAt());
    }

    @Test
    void handlesNullStatus() {
        Agent agent = Agent.builder().id("a2").slug("s").name("n").build();

        AgentData data = AgentRepositoryAdapter.dataFrom(agent);
        assertNull(data.getStatus());
        assertNull(AgentRepositoryAdapter.domainFrom(data).getStatus());
    }
}
