package com.trianova.catalog.domain.agent;

import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.common.ex.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

    private Agent draftAgent() {
        return Agent.builder()
                .id("a1")
                .slug("asistente-ventas")
                .name("Asistente de Ventas")
                .status(PublicationStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void publishMovesDraftToPublished() {
        Agent published = draftAgent().publish();

        assertEquals(PublicationStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isPublished());
    }

    @Test
    void publishFailsWhenNotDraft() {
        Agent published = draftAgent().publish();

        BusinessException ex = assertThrows(BusinessException.class, published::publish);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void archiveMovesPublishedToArchived() {
        Agent archived = draftAgent().publish().archive();

        assertEquals(PublicationStatus.ARCHIVED, archived.getStatus());
        assertNotNull(archived.getUpdatedAt());
    }

    @Test
    void archiveFailsFromDraftBecauseItSkipsPublished() {
        BusinessException ex = assertThrows(BusinessException.class, draftAgent()::archive);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void archiveFailsWhenAlreadyArchived() {
        Agent archived = draftAgent().publish().archive();

        BusinessException ex = assertThrows(BusinessException.class, archived::archive);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void restoreMovesArchivedBackToPublished() {
        Agent restored = draftAgent().publish().archive().restore();

        assertEquals(PublicationStatus.PUBLISHED, restored.getStatus());
    }

    @Test
    void restoreFailsWhenNotArchived() {
        BusinessException ex = assertThrows(BusinessException.class, draftAgent()::restore);
        assertEquals(BusinessException.Type.INVALID_STATE_TRANSITION, ex.getType());
    }

    @Test
    void onlyDraftIsDeletable() {
        assertTrue(draftAgent().isDeletable());
        assertFalse(draftAgent().publish().isDeletable());
    }

    @Test
    void transitionsDoNotMutateTheOriginal() {
        Agent draft = draftAgent();

        draft.publish();

        assertEquals(PublicationStatus.DRAFT, draft.getStatus());
    }
}
