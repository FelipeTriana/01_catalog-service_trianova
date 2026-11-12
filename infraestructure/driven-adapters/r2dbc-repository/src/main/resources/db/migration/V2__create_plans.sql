-- Agregado Plan: la raíz (plans) + sus value objects (prices, plan_features).
-- No hay FK de plans.agent_id a agents: son agregados distintos y se referencian
-- por identidad, no por integridad referencial (frontera de agregado en DDD).
CREATE TABLE plans (
    id          VARCHAR(36)  PRIMARY KEY,
    agent_id    VARCHAR(36)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_plans_agent_id ON plans (agent_id);
CREATE INDEX idx_plans_status   ON plans (status);

-- Precios: ON DELETE CASCADE porque un precio NO vive sin su plan (pieza interna
-- del agregado). UNIQUE (plan_id, billing_cycle): un plan tiene a lo sumo un
-- precio por ciclo (invariante que el dominio ya garantiza en Plan.changePrice).
CREATE TABLE prices (
    id            VARCHAR(36)   PRIMARY KEY,
    plan_id       VARCHAR(36)   NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    billing_cycle VARCHAR(20)   NOT NULL,
    amount        NUMERIC(19,4) NOT NULL,
    currency      VARCHAR(3)    NOT NULL,
    CONSTRAINT uq_prices_plan_cycle UNIQUE (plan_id, billing_cycle)
);

CREATE INDEX idx_prices_plan_id ON prices (plan_id);

-- Features: misma lógica. UNIQUE (plan_id, code): un plan no repite el mismo code.
CREATE TABLE plan_features (
    id          VARCHAR(36)  PRIMARY KEY,
    plan_id     VARCHAR(36)  NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    limit_value VARCHAR(255),
    CONSTRAINT uq_plan_features_plan_code UNIQUE (plan_id, code)
);

CREATE INDEX idx_plan_features_plan_id ON plan_features (plan_id);
