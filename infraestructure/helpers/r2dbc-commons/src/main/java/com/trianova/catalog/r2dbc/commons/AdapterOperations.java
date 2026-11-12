package com.trianova.catalog.r2dbc.commons;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Base reactiva reutilizable para los adaptadores R2DBC. Centraliza el mapeo
 * entidad de dominio &lt;-&gt; entidad de datos y las operaciones comunes, igual que
 * el AdapterOperations del scaffold de SURA, pero 100% no bloqueante (sin boundedElastic).
 *
 * @param <E> entidad de dominio
 * @param <D> entidad de datos (tabla)
 * @param <I> tipo del id
 * @param <R> repositorio reactivo de Spring Data
 */
public abstract class AdapterOperations<E, D, I, R extends ReactiveCrudRepository<D, I>> {

    protected final R repository;
    protected final R2dbcEntityTemplate template;
    private final Function<E, D> toData;
    private final Function<D, E> toEntity;
    private final Function<D, I> idOf;

    protected AdapterOperations(R repository,
                                R2dbcEntityTemplate template,
                                Function<E, D> toData,
                                Function<D, E> toEntity,
                                Function<D, I> idOf) {
        this.repository = repository;
        this.template = template;
        this.toData = toData;
        this.toEntity = toEntity;
        this.idOf = idOf;
    }

    /**
     * Guarda una entidad cuyo id lo asigna el dominio (no es autogenerado).
     * R2DBC no puede deducir si es INSERT o UPDATE cuando el @Id ya viene seteado,
     * así que lo resolvemos consultando existencia y usando insert/update explícitos.
     */
    public Mono<E> save(E entity) {
        D data = toData.apply(entity);
        return repository.existsById(idOf.apply(data))
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? template.update(data)
                        : template.insert(data))
                .map(toEntity);
    }

    public Mono<E> findById(I id) {
        return repository.findById(id).map(toEntity);
    }

    public Flux<E> findAll() {
        return repository.findAll().map(toEntity);
    }

    public Mono<Void> deleteById(I id) {
        return repository.deleteById(id);
    }

    /** Helpers para consultas propias de cada adaptador (p. ej. findBySlug). */
    protected Mono<E> mapOne(Mono<D> query) {
        return query.map(toEntity);
    }

    protected Flux<E> mapMany(Flux<D> query) {
        return query.map(toEntity);
    }
}
