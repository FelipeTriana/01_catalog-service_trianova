package com.trianova.catalog.r2dbc.plan;

import com.trianova.catalog.domain.common.Money;
import com.trianova.catalog.domain.common.PublicationStatus;
import com.trianova.catalog.domain.plan.BillingCycle;
import com.trianova.catalog.domain.plan.Feature;
import com.trianova.catalog.domain.plan.Plan;
import com.trianova.catalog.domain.plan.Price;
import com.trianova.catalog.domain.plan.gateway.PlanGateway;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Adaptador de SALIDA del agregado Plan. A diferencia de Agent (una sola tabla,
 * vía AdapterOperations), un Plan se reparte en TRES tablas: plans (raíz),
 * prices y plan_features. Este adaptador se encarga de:
 *   - al guardar: DESARMAR el agregado en sus filas y persistirlas de forma
 *     consistente (upsert de la raíz + "borrar e insertar" los hijos);
 *   - al leer: RE-ENSAMBLAR el Plan completo trayendo las tres partes.
 *
 * Nota de consistencia: la orquestación de guardado toca varias tablas y NO está
 * envuelta en una transacción todavía. Cuando el app-service aporte un
 * ReactiveTransactionManager, save() debe correr dentro de una transacción
 * (TransactionalOperator / @Transactional) para que el borrar-e-insertar sea atómico.
 */
@Repository
public class PlanRepositoryAdapter implements PlanGateway {

    private final PlanR2dbcRepository planRepository;
    private final PriceR2dbcRepository priceRepository;
    private final PlanFeatureR2dbcRepository featureRepository;
    private final R2dbcEntityTemplate template;

    public PlanRepositoryAdapter(PlanR2dbcRepository planRepository,
                                 PriceR2dbcRepository priceRepository,
                                 PlanFeatureR2dbcRepository featureRepository,
                                 R2dbcEntityTemplate template) {
        this.planRepository = planRepository;
        this.priceRepository = priceRepository;
        this.featureRepository = featureRepository;
        this.template = template;
    }

    @Override
    public Mono<Plan> findById(String id) {
        return planRepository.findById(id).flatMap(this::reassemble);
    }

    @Override
    public Flux<Plan> findByAgentId(String agentId) {
        return planRepository.findByAgentId(agentId).flatMap(this::reassemble);
    }

    @Override
    public Flux<Plan> findPublishedByAgentId(String agentId) {
        return planRepository.findByAgentIdAndStatus(agentId, PublicationStatus.PUBLISHED.name())
                .flatMap(this::reassemble);
    }

    /** Precios y features caen por ON DELETE CASCADE (V2), sin borrarlos a mano. */
    @Override
    public Mono<Void> deleteById(String id) {
        return planRepository.deleteById(id);
    }

    @Override
    public Mono<Void> deleteByAgentId(String agentId) {
        return planRepository.deleteByAgentId(agentId);
    }

    @Override
    public Mono<Plan> save(Plan plan) {
        return upsertRoot(dataFrom(plan))
                .then(replacePrices(plan))
                .then(replaceFeatures(plan))
                .thenReturn(plan);
    }

    // --- Orquestación de guardado (multi-tabla) ---

    /** Igual que AdapterOperations: @Id asignado por el dominio => existsById decide INSERT/UPDATE. */
    private Mono<PlanData> upsertRoot(PlanData data) {
        return planRepository.existsById(data.getId())
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? template.update(data)
                        : template.insert(data));
    }

    /** Borra los precios actuales del plan y vuelve a insertar el conjunto vigente. */
    private Mono<Void> replacePrices(Plan plan) {
        return priceRepository.deleteByPlanId(plan.getId())
                .thenMany(Flux.fromIterable(plan.getPrices())
                        .map(price -> priceRow(plan.getId(), price))
                        .flatMap(template::insert))
                .then();
    }

    /** Borra las features actuales del plan y vuelve a insertar el conjunto vigente. */
    private Mono<Void> replaceFeatures(Plan plan) {
        return featureRepository.deleteByPlanId(plan.getId())
                .thenMany(Flux.fromIterable(plan.getFeatures())
                        .map(feature -> featureRow(plan.getId(), feature))
                        .flatMap(template::insert))
                .then();
    }

    // --- Re-ensamblado al leer ---

    private Mono<Plan> reassemble(PlanData data) {
        Mono<List<Price>> prices = priceRepository.findByPlanId(data.getId())
                .map(PlanRepositoryAdapter::priceFrom)
                .collectList();
        Mono<List<Feature>> features = featureRepository.findByPlanId(data.getId())
                .map(PlanRepositoryAdapter::featureFrom)
                .collectList();
        return Mono.zip(prices, features)
                .map(parts -> domainFrom(data, parts.getT1(), parts.getT2()));
    }

    // --- Mapeos puros (estáticos, testeables sin base de datos) ---

    static PlanData dataFrom(Plan p) {
        return PlanData.builder()
                .id(p.getId())
                .agentId(p.getAgentId())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    static Plan domainFrom(PlanData d, List<Price> prices, List<Feature> features) {
        return Plan.builder()
                .id(d.getId())
                .agentId(d.getAgentId())
                .name(d.getName())
                .description(d.getDescription())
                .status(d.getStatus() != null ? PublicationStatus.valueOf(d.getStatus()) : null)
                .prices(prices)
                .features(features)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    static PriceData priceRow(String planId, Price price) {
        return PriceData.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .billingCycle(price.billingCycle().name())
                .amount(price.amount().amount())
                .currency(price.amount().currency().getCurrencyCode())
                .build();
    }

    static Price priceFrom(PriceData d) {
        return new Price(Money.of(d.getAmount(), d.getCurrency()),
                BillingCycle.valueOf(d.getBillingCycle()));
    }

    static PlanFeatureData featureRow(String planId, Feature feature) {
        return PlanFeatureData.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .code(feature.code())
                .name(feature.name())
                .description(feature.description())
                .limitValue(feature.limitValue())
                .build();
    }

    static Feature featureFrom(PlanFeatureData d) {
        return new Feature(d.getCode(), d.getName(), d.getDescription(), d.getLimitValue());
    }
}
