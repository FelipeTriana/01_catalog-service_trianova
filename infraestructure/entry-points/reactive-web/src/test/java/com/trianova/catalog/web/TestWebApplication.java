package com.trianova.catalog.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Configuración mínima para anclar el contexto de @WebFluxTest (este módulo no
 * tiene main propio). A diferencia del test de R2DBC, aquí hace falta
 * @ComponentScan (incluido en @SpringBootApplication) para que el slice encuentre
 * los controllers y el @RestControllerAdvice; el filtro de @WebFluxTest deja
 * fuera todo lo que no sea de la capa web.
 */
@SpringBootApplication
class TestWebApplication {
}
