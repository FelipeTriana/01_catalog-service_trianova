package com.trianova.catalog.rabbitmq.relay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Ejecuta el relay en bucle mientras la aplicación está viva: publica un lote,
 * espera el intervalo y repite. Un error (p. ej. RabbitMQ caído) solo se registra;
 * los eventos siguen en el outbox y se reintentan en la siguiente vuelta.
 *
 * Se apaga con app.events.relay.enabled=false (útil en tests o en una instancia
 * dedicada solo a atender HTTP).
 */
@Component
public class OutboxRelayScheduler implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;
    private final Duration interval;
    private final boolean enabled;
    private volatile Disposable loop;

    public OutboxRelayScheduler(OutboxRelay relay,
                                @Value("${app.events.relay.interval:2s}") Duration interval,
                                @Value("${app.events.relay.enabled:true}") boolean enabled) {
        this.relay = relay;
        this.interval = interval;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        loop = Mono.defer(relay::publishPending)
                .doOnNext(sent -> {
                    if (sent > 0) {
                        LOG.debug("Outbox relay sent {} events", sent);
                    }
                })
                .onErrorResume(error -> {
                    LOG.warn("Outbox relay failed; pending events will be retried", error);
                    return Mono.empty();
                })
                .then(Mono.delay(interval))
                .repeat()
                .subscribe();
    }

    @Override
    public void stop() {
        Disposable current = loop;
        if (current != null) {
            current.dispose();
        }
        loop = null;
    }

    @Override
    public boolean isRunning() {
        return loop != null && !loop.isDisposed();
    }

    @Override
    public boolean isAutoStartup() {
        return enabled;
    }
}
