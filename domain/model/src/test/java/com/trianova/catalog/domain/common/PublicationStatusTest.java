package com.trianova.catalog.domain.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tabla completa de transiciones: las 9 combinaciones origen -> destino. */
class PublicationStatusTest {

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "DRAFT,     DRAFT,     false",
            "DRAFT,     PUBLISHED, true",
            "DRAFT,     ARCHIVED,  false",
            "PUBLISHED, DRAFT,     false",
            "PUBLISHED, PUBLISHED, false",
            "PUBLISHED, ARCHIVED,  true",
            "ARCHIVED,  DRAFT,     false",
            "ARCHIVED,  PUBLISHED, true",
            "ARCHIVED,  ARCHIVED,  false"
    })
    void transitionTable(PublicationStatus from, PublicationStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @ParameterizedTest(name = "{0} deletable = {1}")
    @CsvSource({"DRAFT, true", "PUBLISHED, false", "ARCHIVED, false"})
    void onlyDraftIsDeletable(PublicationStatus status, boolean deletable) {
        assertEquals(deletable, status.isDeletable());
    }
}
