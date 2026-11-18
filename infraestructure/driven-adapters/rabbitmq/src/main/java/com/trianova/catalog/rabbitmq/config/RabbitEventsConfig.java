package com.trianova.catalog.rabbitmq.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara el exchange de eventos de dominio. RabbitAdmin (autoconfigurado por
 * Spring Boot) lo crea en el broker al conectarse si no existe.
 *
 * Es TOPIC para que cada consumidor se suscriba por patrón de routing key,
 * p. ej. "catalog.price.*" o "catalog.#". Las colas son de los consumidores:
 * este servicio solo publica y no declara ninguna.
 */
@Configuration
public class RabbitEventsConfig {

    @Bean
    public TopicExchange eventsExchange(@Value("${app.events.exchange:trianova.events}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }
}
