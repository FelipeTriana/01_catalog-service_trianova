package com.trianova.catalog.r2dbc.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entidad de datos de la RAÍZ del agregado, mapeada a la tabla plans.
 * Los value objects (precios, features) viven en sus propias tablas y entidades.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("plans")
public class PlanData {

    @Id
    private String id;

    @Column("agent_id")
    private String agentId;

    private String name;
    private String description;
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
