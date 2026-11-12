package com.trianova.catalog.domain.agent;

import com.trianova.catalog.domain.common.ex.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

    private Agent draftAgent() {
        return Agent.builder()
                .id("a1")
                .slug("asistente-ventas")
                .name("Asistente de Ventas")
                .status(AgentStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void publishMovesDraftToPublished() {
        Agent published = draftAgent().publish();

        assertEquals(AgentStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isPublished());
    }

    @Test
    void publishFailsWhenNotDraft() {
        Agent published = draftAgent().publish();

        BusinessException ex = assertThrows(BusinessException.class, published::publish);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void archiveMovesToArchived() {
        Agent archived = draftAgent().archive();

        assertEquals(AgentStatus.ARCHIVED, archived.getStatus());
    }

    @Test
    void archiveFailsWhenAlreadyArchived() {
        Agent archived = draftAgent().archive();

        BusinessException ex = assertThrows(BusinessException.class, archived::archive);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void transitionsDoNotMutateTheOriginal() {
        Agent draft = draftAgent();

        draft.publish();

        assertEquals(AgentStatus.DRAFT, draft.getStatus());
    }
}
