package com.trianova.catalog.r2dbc.agent;

import com.trianova.catalog.domain.agent.Agent;
import com.trianova.catalog.domain.agent.AgentStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Instant;

/**
 * Prueba de integración del adaptador contra un PostgreSQL real (Testcontainers).
 * Se SALTA automáticamente si Docker no está disponible (disabledWithoutDocker).
 */
@Testcontainers(disabledWithoutDocker = true)
@DataR2dbcTest
class AgentRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    AgentR2dbcRepository repository;
    @Autowired
    R2dbcEntityTemplate template;

    @DynamicPropertySource
    static void r2dbcProps(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort()
                + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private AgentRepositoryAdapter adapter() {
        return new AgentRepositoryAdapter(repository, template);
    }

    private Agent draft(String id, String slug) {
        return Agent.builder()
                .id(id).slug(slug).name("Asistente")
                .status(AgentStatus.DRAFT).createdAt(Instant.now())
                .build();
    }

    @Test
    void insertsThenFindsById() {
        AgentRepositoryAdapter adapter = adapter();

        StepVerifier.create(adapter.save(draft("a1", "slug-1")).then(adapter.findById("a1")))
                .expectNextMatches(a -> a.getId().equals("a1") && a.getStatus() == AgentStatus.DRAFT)
                .verifyComplete();
    }

    @Test
    void savesUpdateOverExistingRow() {
        AgentRepositoryAdapter adapter = adapter();

        Agent saved = adapter.save(draft("a2", "slug-2")).block();
        StepVerifier.create(adapter.save(saved.publish()).then(adapter.findById("a2")))
                .expectNextMatches(a -> a.getStatus() == AgentStatus.PUBLISHED)
                .verifyComplete();
    }

    @Test
    void findBySlugReturnsIt() {
        AgentRepositoryAdapter adapter = adapter();

        StepVerifier.create(adapter.save(draft("a3", "slug-3")).then(adapter.findBySlug("slug-3")))
                .expectNextMatches(a -> a.getId().equals("a3"))
                .verifyComplete();
    }

    @Test
    void findByStatusReturnsPublished() {
        AgentRepositoryAdapter adapter = adapter();

        StepVerifier.create(adapter.save(draft("a4", "slug-4").publish())
                        .thenMany(adapter.findByStatus(AgentStatus.PUBLISHED))
                        .filter(a -> a.getId().equals("a4"))
                        .next())
                .expectNextMatches(a -> a.getStatus() == AgentStatus.PUBLISHED)
                .verifyComplete();
    }
}
