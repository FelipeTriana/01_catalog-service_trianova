package com.trianova.catalog.r2dbc.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Feature;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.PlanStatus;
import com.trianova.catalog.domain.plan.Price;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Prueba de integración del adaptador del agregado Plan contra un PostgreSQL real
 * (Testcontainers). El foco es el caso DIFÍCIL: desarmar el agregado en tres
 * tablas al guardar y re-ensamblarlo al leer, incluido el "borrar e insertar"
 * de los hijos en un update. Se SALTA si Docker no está disponible.
 *
 * Los tests comparten el mismo contenedor (estático) y no hay rollback, así que
 * cada test usa ids y agentId propios para no interferir con los demás.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataR2dbcTest
class PlanRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PlanR2dbcRepository planRepository;
    @Autowired
    PriceR2dbcRepository priceRepository;
    @Autowired
    PlanFeatureR2dbcRepository featureRepository;
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

    private PlanRepositoryAdapter adapter() {
        return new PlanRepositoryAdapter(planRepository, priceRepository, featureRepository, template);
    }

    private Price price(String amount, String currency, BillingCycle cycle) {
        return new Price(Money.of(new BigDecimal(amount), currency), cycle);
    }

    private Plan plan(String id, String agentId, PlanStatus status, List<Price> prices) {
        return Plan.builder()
                .id(id).agentId(agentId).name("Plan " + id)
                .status(status)
                .prices(prices)
                .feature(new Feature("exec", "Ejecuciones", "límite mensual", "10000"))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void savesPlanWithPricesAndFeaturesThenReassembles() {
        PlanRepositoryAdapter adapter = adapter();
        Plan p = plan("p1", "agent-1", PlanStatus.DRAFT, List.of(
                price("29.00", "USD", BillingCycle.MONTHLY),
                price("290.00", "USD", BillingCycle.YEARLY)));

        StepVerifier.create(adapter.save(p).then(adapter.findById("p1")))
                .expectNextMatches(r -> r.getPrices().size() == 2
                        && r.getFeatures().size() == 1
                        && r.getStatus() == PlanStatus.DRAFT
                        && r.getPrices().stream().anyMatch(pr ->
                        pr.billingCycle() == BillingCycle.MONTHLY
                                && pr.amount().amount().compareTo(new BigDecimal("29.00")) == 0))
                .verifyComplete();
    }

    @Test
    void updateReplacesChildRowsWithoutLeavingOrphans() {
        PlanRepositoryAdapter adapter = adapter();
        Plan draft = plan("p2", "agent-2", PlanStatus.DRAFT,
                List.of(price("29.00", "USD", BillingCycle.MONTHLY)));

        Plan saved = adapter.save(draft).block();
        Plan changed = saved.changePrice(price("39.00", "USD", BillingCycle.MONTHLY));

        // Tras el update debe quedar UN solo precio (39.00), no dos: el borrar-e-insertar funcionó.
        StepVerifier.create(adapter.save(changed).then(adapter.findById("p2")))
                .expectNextMatches(r -> r.getPrices().size() == 1
                        && r.getPrices().get(0).amount().amount().compareTo(new BigDecimal("39.00")) == 0)
                .verifyComplete();

        // Sin filas huérfanas en la tabla prices.
        StepVerifier.create(priceRepository.findByPlanId("p2").count())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void findByAgentIdReturnsAllPlansOfAgent() {
        PlanRepositoryAdapter adapter = adapter();
        adapter.save(plan("p3a", "agent-3", PlanStatus.DRAFT,
                List.of(price("10.00", "USD", BillingCycle.MONTHLY)))).block();
        adapter.save(plan("p3b", "agent-3", PlanStatus.PUBLISHED,
                List.of(price("20.00", "USD", BillingCycle.MONTHLY)))).block();

        StepVerifier.create(adapter.findByAgentId("agent-3").collectList())
                .expectNextMatches(list -> list.size() == 2)
                .verifyComplete();
    }

    @Test
    void findPublishedByAgentIdReturnsOnlyPublished() {
        PlanRepositoryAdapter adapter = adapter();
        adapter.save(plan("p4pub", "agent-4", PlanStatus.PUBLISHED,
                List.of(price("49.00", "USD", BillingCycle.MONTHLY)))).block();
        adapter.save(plan("p4drf", "agent-4", PlanStatus.DRAFT,
                List.of(price("9.00", "USD", BillingCycle.MONTHLY)))).block();

        StepVerifier.create(adapter.findPublishedByAgentId("agent-4").collectList())
                .expectNextMatches(list -> list.size() == 1
                        && list.get(0).getId().equals("p4pub"))
                .verifyComplete();
    }
}
