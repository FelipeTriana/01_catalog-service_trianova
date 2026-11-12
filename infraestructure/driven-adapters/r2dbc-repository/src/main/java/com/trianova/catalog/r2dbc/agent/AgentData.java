package com.trianova.catalog.r2dbc.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entidad de datos mapeada a la tabla agents. Es un detalle de infraestructura:
 * el dominio (Agent) no la conoce. El status se persiste como texto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("agents")
public class AgentData {

    @Id
    private String id;

    private String slug;
    private String name;
    private String description;
    private String category;
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
