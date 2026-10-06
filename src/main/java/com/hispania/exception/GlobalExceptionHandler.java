package com.hispania.exception;

import com.hispania.presentation.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Traduce excepciones a respuestas HTTP con un cuerpo {@link ApiError} comun.
 *
 * <p>Sin esta clase, una violacion de unicidad de Postgres llegaria al cliente como
 * un 500 con una traza en el cuerpo (o vacio), y un fallo de validacion como un 400
 * con una forma distinta a la de un 404. El cliente no tendria un unico formato que
 * parsear.
 *
 * <p>El orden de los metodos va de mas especifico a mas general a proposito: el
 * {@code @ExceptionHandler(Exception.class)} del final tambien capturaria las
 * anteriores, asi que si se reordenara, estas dejarían de ejecutarse.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 404: el recurso no existe.
     *
     * <p>Devuelve un cuerpo con el mismo formato que el 404 que genera Spring para las
     * rutas inexistentes, para que el cliente no tenga que distinguir uno de otro.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                    HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    /** 409: el recurso ya existe. */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    /**
     * 401: el login no ha sido valido.
     *
     * <p>Se registra aparte y no como parte del 500 generico porque un fallo de
     * credenciales es un evento esperado, no un error del servidor: si acabase
     * en el log de errores aparecerian volumenes de trazas por intentos fallidos.
     */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiError> handleCredenciales(CredencialesInvalidasException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, List.of());
    }

    /**
     * 400: el peticion es incoherente, pero no por el formato del JSON.
     *
     * <p>Cubre los argumentos que el servicio rechaza tras validar el cuerpo: por
     * ejemplo, un rechazo de propuesta sin motivo. Sin este handler, esos casos
     * caerian en el {@code Exception} generico de abajo y responderian 500, que
     * dice "fallo del servidor" cuando en realidad el cliente tiene que corregir
     * un campo. Es el error mas desconcertante que se puede devolver.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    /**
     * 403: el rol no da para esta operacion.
     *
     * <p>Spring Security lanza su propio {@code AccessDeniedException} cuando es
     * la cadena de filtros la que rechaza, y {@code @PreAuthorize} produce otra
     * excepcion distinta. Se mapean las tres al mismo cuerpo, para que el cliente
     * solo tenga que parsear un formato de error.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, List.of());
    }

    /** 403: variante que lanza Spring Security. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN,
                "No tienes permisos para esta operacion", request, List.of());
    }

    /**
     * 400: el cuerpo no cumple las reglas del DTO.
     *
     * <p>Se listan todos los campos invalidos, no solo el primero: si un alta viene
     * sin nombre y con una latitud fuera de rango, el cliente necesita las dos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        String message = details.stream()
                .map(d -> d.field() + ": " + d.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Peticion invalida");

        return build(HttpStatus.BAD_REQUEST, message, request, details);
    }

    /**
     * 400: el JSON no se pudo convertir a un DTO.
     *
     * <p>Ocurre al mandar un enum inexistente ({@code "category": "DANZAA"}), un tipo
     * numerico donde va texto, o JSON sintacticamente invalido. Spring lo envuelve en
     * {@code HttpMessageNotReadableException} y sin este handler el cliente recibiria
     * un 500 por un error que es suyo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la peticion no se pudo interpretar: " + ex.getMostSpecificCause().getMessage(),
                request, List.of());
    }

    /** 400: un parametro de ruta con tipo equivocado, por ejemplo un id vacio. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "El parametro '" + ex.getName() + "' tiene un valor invalido",
                request, List.of());
    }

    /**
     * 404: la ruta no existe.
     *
     * <p>Sin este handler, una ruta mal escrita devuelve el 404 por defecto de Spring,
     * que es un JSON con {@code "error"} y nada mas. Aqui se le da la misma forma que
     * al resto de errores de la API.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoRoute(NoResourceFoundException ex,
                                                  HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No existe el recurso " + request.getRequestURI(), request, List.of());
    }

    /**
     * 409: la base de datos rechazo la operacion por integridad.
     *
     * <p>Es la red de seguridad para lo que las validaciones del servicio no
     * interceptan: un CHECK de la base, un NOT NULL o una clave foranea. Se traduce a
     * 409 y no a 500 porque la causa es una violacion de la relacion entre datos, no
     * un fallo del servidor. El mensaje original de Postgres se registra en el log
     * pero no se devuelve al cliente, porque revela nombres de tabla y de columna.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest request) {
        log.warn("Violacion de integridad en {}: {}",
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "La operacion infringe una restriccion de integridad de los datos",
                request, List.of());
    }

    /**
     * 500: cualquier otro fallo.
     *
     * <p>Se registra la traza completa en el servidor y se devuelve un mensaje generico.
     * Devolver {@code ex.getMessage()} al cliente en un 500 filtraria el nombre de las
     * tablas implicadas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex,
                                                      HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Se produjo un error interno", request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest request,
                                           List<ApiError.FieldViolation> details) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(body);
    }
}
