-- Transactional Outbox: cada evento de dominio se guarda aquí en la misma base
-- (y, con el app-service, en la misma transacción) que el cambio que lo origina.
-- Un relay lee las filas pendientes, las envía a RabbitMQ y las marca como publicadas.
--
-- La migración vive en este módulo porque la tabla es suya: nadie más la lee ni la escribe.
-- published_at en lugar del BOOLEAN de la spec: además de "si se publicó" dice "cuándo",
-- lo que ayuda a diagnosticar retrasos del relay.
CREATE TABLE outbox (
    id             VARCHAR(36)  PRIMARY KEY,   -- = eventId del evento
    aggregate_type VARCHAR(50)  NOT NULL,      -- PLAN
    aggregate_id   VARCHAR(36)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,      -- catalog.plan.changed | catalog.price.updated (routing key)
    payload        JSONB        NOT NULL,      -- contrato JSON exacto que recibe el consumidor
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ                 -- NULL = pendiente de enviar
);

-- Índice parcial: el relay solo consulta las pendientes, que son pocas frente al histórico.
CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE published_at IS NULL;
