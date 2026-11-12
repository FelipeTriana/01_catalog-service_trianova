package com.trianova.catalog.r2dbc;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Configuración mínima para anclar el contexto de @DataR2dbcTest.
 * Este módulo no tiene clase main propia (eso es del app-service); los tests de
 * slice necesitan una @SpringBootConfiguration en el classpath de test.
 *
 * Vive en el paquete raíz com.trianova.catalog.r2dbc para que la encuentren
 * TODOS los tests de slice de los sub-paquetes (agent, plan, ...), ya que
 * @DataR2dbcTest busca la @SpringBootConfiguration subiendo por el árbol de paquetes.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class TestR2dbcApplication {
}
