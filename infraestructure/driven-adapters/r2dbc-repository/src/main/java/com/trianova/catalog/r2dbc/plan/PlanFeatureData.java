package com.trianova.catalog.r2dbc.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad de datos del value object Feature, mapeada a la tabla plan_features.
 * El id es una llave subrogada (Feature no tiene identidad propia en el dominio).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("plan_features")
public class PlanFeatureData {

    @Id
    private String id;

    @Column("plan_id")
    private String planId;

    private String code;
    private String name;
    private String description;

    @Column("limit_value")
    private String limitValue;
}
