package com.trianova.catalog.r2dbc.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * Entidad de datos del value object Price, mapeada a la tabla prices.
 * El id es una llave subrogada: Price no tiene identidad en el dominio, así que
 * se genera al persistir y no se expone al dominio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("prices")
public class PriceData {

    @Id
    private String id;

    @Column("plan_id")
    private String planId;

    @Column("billing_cycle")
    private String billingCycle;

    private BigDecimal amount;
    private String currency;
}
