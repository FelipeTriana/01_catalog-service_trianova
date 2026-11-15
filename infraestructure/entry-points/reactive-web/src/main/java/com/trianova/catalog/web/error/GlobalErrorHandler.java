package com.trianova.catalog.web.error;

import com.trianova.catalog.domain.common.ex.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce excepciones a respuestas RFC 7807 (application/problem+json) con los
 * campos type, title, status, detail y code.
 *
 * Extiende ResponseEntityExceptionHandler para que los errores propios de Spring
 * (JSON mal formado, método no permitido, validación) también salgan como
 * ProblemDetail, sin reimplementarlos.
 */
@RestControllerAdvice
public class GlobalErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalErrorHandler.class);

    static final String CODE = "code";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        HttpStatus status = statusOf(ex.getType());
        LOG.warn("Business rule rejected the request: {}", ex.getCode());
        return problem(status, ex.getMessage(), ex.getCode());
    }

    /** Invariantes del dominio violadas por datos de entrada (constructores de objetos de valor). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        LOG.warn("Invalid input rejected by the domain: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_INPUT");
    }

    /** Último recurso: nunca se expone el mensaje interno, solo se registra. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_ERROR");
    }

    /** Validación de @Valid: agrega el detalle por campo para que el cliente sepa qué corregir. */
    @Override
    protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
            WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = ex.getBody();
        body.setProperty(CODE, "VALIDATION_FAILED");
        body.setProperty("errors", errors);
        return super.handleWebExchangeBindException(ex, headers, status, exchange);
    }

    /**
     * Switch exhaustivo y sin default: si se agrega un tipo nuevo al enum, esto deja
     * de compilar hasta asignarle un código HTTP. INVALID_STATE_TRANSITION no está en
     * la tabla de la spec; se responde 409 porque choca con el estado actual del recurso.
     */
    static HttpStatus statusOf(BusinessException.Type type) {
        return switch (type) {
            case AGENT_NOT_FOUND, PLAN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SLUG_ALREADY_EXISTS,
                 PLAN_HAS_NO_VALID_PRICE,
                 AGENT_HAS_NO_PUBLISHED_PLAN,
                 INVALID_STATE_TRANSITION -> HttpStatus.CONFLICT;
            case INVALID_PRICE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty(CODE, code);
        return ResponseEntity.status(status).body(body);
    }
}
